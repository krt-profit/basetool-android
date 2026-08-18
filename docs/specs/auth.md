> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-08-18.
> **Owner area:** AUTH · **Related:** [`../ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §3–4,
> [`api-contract.md`](api-contract.md), ADR-0001 / ADR-0002 (this repo),
> main repo ADR-0131 (refresh-only DPoP), `REQ-SEC-030`

# Authentication — tokens, keys and proofs

The app is a public OAuth client on a member's phone. Everything here follows from two facts: it
holds no client secret, and the device it runs on can be lost while unlocked, locked, or restored
onto someone else's hardware.

## Requirements

### REQ-APP-AUTH-001 — The access token never touches disk

It lives in memory for its five-minute lifetime and is gone when the process dies. Persisting it
would add a second secret at rest to save at most one refresh, on a token that is short-lived by
design (main repo `REQ-SEC-030`).

**Acceptance**

- [x] No component writes the access token anywhere: the only store in `core:auth` is
  `RefreshTokenStore`, and its API takes a refresh token.
- [ ] A lint or detekt gate makes writing it impossible rather than merely absent. **Open.**

### REQ-APP-AUTH-002 — The refresh token is encrypted by a non-exportable device key

AES-256-GCM with a key generated in the Android Keystore; the ciphertext (IV ‖ ciphertext, Base64)
lives in a Preferences DataStore. Three key properties carry the protection:

- **Non-exportable** — the key never enters the app process, so a ciphertext that reaches another
  device cannot be decrypted there.
- **`setUnlockedDeviceRequired(true)`** — while the device is locked the token is cryptographically
  unusable. It costs nothing: the app refreshes only in the foreground, because there is no push
  channel (decision Q2).
- **StrongBox where available**, falling back to a TEE-backed key when the device has no secure
  element. Requesting it unconditionally and catching `StrongBoxUnavailableException` is the check —
  an SDK-level guard would be dead code at minSdk 29.

`androidx.security:security-crypto` is deliberately not used: deprecated, final release 1.1.0, no
successor.

**Reading is allowed to fail, and failure is not an error.** A key invalidated by a new biometric
enrolment, a locked device, a blob restored from elsewhere — all three mean "no usable session".
`RefreshTokenStore.read()` answers `null` and clears the unusable blob rather than throwing, because
the alternative is a crash loop on start-up where a login prompt belongs.

**Acceptance**

- [x] Round-trip, overwrite and clear behave as specified (`RefreshTokenStoreTest`).
- [x] An undecryptable token reads as `null` **and** is cleared, so the failure is paid once.
- [ ] The Keystore implementation itself is exercised on a device. **Open** — `KeystoreSecretCipher`
  cannot run on a JVM; the seam is at `SecretCipher` so everything above it is tested, and the key's
  hardware binding needs the instrumented suite (Gradle Managed Devices).

### REQ-APP-AUTH-003 — DPoP proofs are sent on token requests only, and timed by server clock

The realm binds **only the refresh token** (main repo ADR-0131). A voluntarily sent proof makes
Keycloak bind the access token as well, and the backend's bearer filter rejects an access token
carrying `cnf.jkt` — so a proof on an ordinary API call breaks the next request. Proofs therefore
belong on `/token` and nowhere else, which is why the proof factory is used by the token client and
is not an interceptor.

`iat` comes from `ServerClock` (`REQ-APP-API-004`), never the device clock: Keycloak allows a 10 s
proof lifetime with 15 s of skew, and a phone a minute off would fail to log in with a symptom that
reads as "login is broken".

Each proof carries a fresh `jti`; Keycloak rejects a replay, so a reused id would make the second
refresh of a session fail intermittently and only in the field.

**Acceptance**

- [x] `htm`/`htu`/`iat`/`jti` and the `dopp+jwt` header shape verify against the embedded key
  (`DpopProofFactoryTest`).
- [x] `iat` follows an offset server clock.
- [x] Two proofs never share a `jti`.
- [x] The private half of the key is never serialised into the header.
- [ ] The proof is attached to the actual token request. **Open** — lands with the token client.

### REQ-APP-AUTH-004 — The token file is excluded from backup in both rule sets

minSdk 29 spans two backup worlds: `backup_rules.xml` governs API ≤ 30, `data_extraction_rules.xml`
governs API 31+, and the latter needs the exclusion in **both** its `cloud-backup` and
`device-transfer` sections — `allowBackup=false` alone does not reliably stop a device-to-device
transfer.

The excluded path must be the file DataStore actually writes: `datastore/krt_tokens.preferences_pb`,
not the bare store name. `AuthDataStore.RELATIVE_PATH` publishes it and `BackupExclusionTest` compares
the XML against it, because this is the failure with no symptom — a stale path breaks no build and
simply starts uploading a refresh token.

**Acceptance**

- [x] Both rule files exclude the path, and the extraction rules do so in both sections
  (`BackupExclusionTest`).
- [x] The path names the `datastore/` subdirectory and the `.preferences_pb` file.
- [ ] A restored backup is observed not to contain the file. **Open** — device-level verification,
  Phase 5.

### REQ-APP-AUTH-005 — Logout wipes the token and the key

Clearing the stored ciphertext is not enough on its own: a key left behind can decrypt any copy of
that ciphertext that escaped. Logout therefore deletes the DataStore entry **and** the Keystore
entry, in addition to the Keycloak end-session call and the best-effort refresh-token revocation.

**Acceptance**

- [x] `RefreshTokenStore.clear()` removes the entry; `KeystoreSecretCipher.deleteKey()` removes the
  key.
- [ ] The full logout sequence — end-session, revocation, local wipe — is orchestrated and tested.
  **Open** — lands with the token client and the login flow.
