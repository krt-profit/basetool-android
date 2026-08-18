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
- [x] The proof is attached to the token request and to nothing else — `TokenClient` is the only
  caller of the factory, and it builds its own HTTP client precisely so no interceptor can add one
  (`REQ-APP-AUTH-006`).
- [x] A server-issued `DPoP-Nonce` is echoed back, and a `use_dpop_nonce` refusal is retried exactly
  once (RFC 9449 §8.3, `TokenClientTest`). The realm demands no nonce today; the handling exists so
  that enabling one is not a client-breaking change.
- [x] The authorization request carries `dpop_jkt`, the thumbprint of the same key
  (`AuthorizationRequestTest`) — RFC 9449 §10 defence in depth, so an intercepted code cannot be
  redeemed against a different key.
- [ ] The proof is accepted by the live realm under the refresh-only policy. **Open** — verified
  against a throwaway Keycloak in the concept (security concept §4), not yet against the app's own
  request.

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
- [x] The revocation call and the RP-initiated logout URL exist and are tested
  (`TokenClient.revokeRefreshToken`, `TokenClient.endSessionUri`). Revocation cannot fail a logout:
  it answers `false` and logs, because what protects the device is the local wipe and a phone with
  no connectivity must still be able to log out.
- [x] The three steps run in one defined order (`AuthSession.logout`, `AuthSessionTest`): the
  in-memory session is dropped **first**, so a logout is instant and cannot be undone by a slow
  network; the revocation follows while the token is still known; the local wipe of blob **and** key
  comes last. A refused revocation does not stop any of it.
- [x] `deleteKey()` is part of the `SecretCipher` contract rather than an implementation detail — a
  cipher that cannot be wiped cannot back a logout.
- [ ] The end-session URL is actually opened. **Open** — `logout()` returns it; opening a browser is
  the UI's half and lands with the chapter-04 screens.

### REQ-APP-AUTH-006 — Token requests use their own HTTP client, and every answer is a named state

**The API client must not be reused for token requests.** It carries
`MandatoryHeadersInterceptor`, which attaches `Authorization: Bearer <access token>` to everything
it sees; Keycloak reads an `Authorization` header on its token endpoint as an attempt at client
authentication and answers `invalid_client`. A refresh would therefore succeed exactly once — on
the first login, when no access token exists yet — and fail for the rest of the install's life.
`KrtHttpClient.createTokenClient` derives a client that keeps the connection pool, the dispatcher
and the timeouts and drops the interceptors, re-adding only `ServerTimeInterceptor`: this traffic is
the only kind that observes **Keycloak's** clock, and Keycloak is the party that judges a proof's
`iat`.

**The endpoints are derived from the issuer, not discovered.** Keycloak's URL layout is fixed, so a
`/.well-known/openid-configuration` fetch would only add a round trip to the login path and another
way for it to fail. Deriving them also makes an omission enforceable: `OidcConfiguration` has no
`userinfo` property, and under the refresh-only DPoP policy Keycloak answers **HTTP 500** there
(security concept §4, constraint 1). Profile claims come from the ID token.

**Every outcome is a state, and the ones that look alike are kept apart.** `invalid_grant` and a
realm misconfiguration both arrive as HTTP 400: the first means "show the login screen", the second
means a login that cannot succeed, and collapsing them produces either a dead end or an infinite
loop. A 2xx carrying `token_type` other than `Bearer` is a *third* case — the per-client "Require
DPoP bound tokens" switch overriding the client policy — whose natural symptom is that every
subsequent API call 401s, pointing at the wrong component entirely.

**Acceptance**

- [x] The token client sends no `Authorization`, correlation-id or org-unit header, and the API
  client still sends all of them (`KrtHttpClientTest`).
- [x] The token client updates the `ServerClock` from the realm's `Date` header.
- [x] `invalid_grant` maps to a session that ended; any other OAuth error stays a rejection; a
  refusal with no OAuth body keeps its status (`TokenClientTest`).
- [x] `token_type` other than `Bearer` is reported as such instead of being handed on.
- [x] A 2xx that is not a grant — the captive-portal case — is malformed, not an empty session.
- [x] A transport failure is distinguishable from a refusal, so only it can read as "offline".
- [ ] The states are rendered by the screens that own them. **Open** — lands with the login flow.

### REQ-APP-AUTH-007 — One login attempt, three per-attempt secrets, and a session that survives a tunnel

Login is Authorization Code + PKCE **S256** in a browser (RFC 8252), never a WebView. Each attempt
mints three values that must not outlive it or be reused: the PKCE verifier redeems the code, the
`state` ties the redirect to this attempt, and the `nonce` ties the ID token to it. They live
together in one `AuthorizationRequest`, so losing one of them is a compile error rather than a
quietly weakened login.

**`state` is checked before anything else in a redirect is read.** A redirect the app did not start
must not be able to steer the flow — not even into an error screen of its choosing.

**The `nonce` is verified against the ID token, or it is decoration.** A token whose `nonce` does not
match was not minted for this login; nothing is stored and no session starts.

**The redirect is parsed at string level, not with a URL parser.** Production redirects to a verified
App Link, but the dev realm registers the custom scheme `de.kartell.basetool:/oauth2redirect`, which
an HTTP URL parser refuses outright — parsing with one would break every login on the build the flow
is developed against.

**Refreshing is single-flight, and only `invalid_grant` ends a session.** Several screens loading at
once would each notice the expiry and each start a refresh; one mutex plus a re-check inside it means
they wait and then find it done. A refresh that fails because the phone is in a tunnel leaves the
stored token untouched and reports `SessionState.Stale` — the UI shows a retry, not a password
prompt. Only a refusal from the realm clears the stored token.

**A refresh response with no `refresh_token` keeps the stored one.** The realm does not rotate
(main repo REQ-SEC-012), so omission is legitimate, and overwriting the field with `null` would
discard the only way back into the session.

**Acceptance**

- [x] The verifier is 43 characters of unreserved entropy and the challenge is its base64url SHA-256,
  recomputed independently in `PkceChallengeTest`; the verifier never appears in `toString`.
- [x] The authorization request asks for `code` with `S256`, the registered redirect, the client's
  scopes **without** `offline_access`, and `dpop_jkt` (`AuthorizationRequestTest`).
- [x] `state`, `nonce` and the verifier differ between attempts.
- [x] A redirect with a foreign `state` yields `StateMismatch` whether it carries a code or an error.
- [x] A custom-scheme redirect is read exactly like an `https` one.
- [x] An ID token with a mismatched `nonce` is refused and stores nothing (`AuthSessionTest`).
- [x] Concurrent `refreshIfNeeded()` calls produce exactly one token request — asserted by a test
  verified to fail when the lock is removed.
- [x] An unreachable realm leaves the stored token in place and yields `Stale`; `invalid_grant`
  clears it and yields `SignedOut`.
- [x] A grant without a `refresh_token` keeps the stored one.
- [x] The Custom Tab launch and the redirect activity (`REQ-APP-AUTH-008`).
- [ ] The chapter-04 screens — login, approval-pending, terms, app-lock. **Open** — this
  requirement covers the flow's logic; the screens that drive it follow.
- [ ] The ID token's signature is not verified. **Accepted, not open** — OIDC Core §3.1.3.7 permits
  it when the token comes directly from the token endpoint over TLS, which is the only way this app
  obtains one (ADR-0004).

### REQ-APP-AUTH-008 — The browser round trip: a Custom Tab, a claimed redirect, and an attempt that outlives the process

Login happens in a **Custom Tab, never a WebView** (RFC 8252 §8.12). A WebView would put the
member's realm password inside a surface this app controls and can read, share no session with the
browser, and be indistinguishable from a phishing app. The toolbar is `#141414` so the realm's dark
login page does not sit inside light chrome (design spec ch. 04).

**The pending attempt is persisted, encrypted, and single-use.** While the Custom Tab is in front,
Android may kill this process — on a low-memory phone it will. The `state`, `nonce` and PKCE
verifier must therefore survive it, or the redirect cannot be recognised and the code cannot be
redeemed. They live in the same encrypted store as the refresh token, because the verifier is
exactly as sensitive as the code it unlocks for the length of the round trip. `take()` reads *and*
clears: a code is redeemable once, so a consumed attempt must not be actionable again.

**The redirect URI is per flavour and is claimed by a dedicated activity.** Production uses the
verified App Link `https://profit-base.online/app/callback`, which no other app can claim because
the domain publishes this app's signing-certificate digest; the custom scheme
`de.kartell.basetool:/oauth2redirect` is registered on the dev realm only, since a custom scheme is
claimable by any installed app (security concept §3). The design chapter's parenthetical
`(basetool://auth)` is illustrative — the registered URIs are the ones above, and they are pinned
on both ends: the realm refuses an unregistered redirect, and this end is asserted by a test.

**The post-logout return must be claimed too**, or the browser opens the website after a logout and
the member is left looking at it.

A **separate** activity holds the filter rather than `MainActivity`: while the Custom Tab is open it
sits on top of the task, so a `singleTop` `MainActivity` would be created a *second* time on top of
the browser instead of returning to the running one. `singleTask` on a translucent, UI-less activity
brings the task forward and re-launches `MainActivity` with `CLEAR_TOP`. Giving `MainActivity`
itself `singleTask` would change the launch semantics of every deep link and notification to fix one
flow.

Endpoints come from `BuildConfig` and nowhere else — no runtime switch, no debug menu. A release
build that can be pointed at another server is a gift to whoever gets hold of a device (DEV_CI §6).

**Acceptance**

- [x] The attempt survives a process restart, asserted by a second store instance reading what the
  first wrote (`PendingAuthorizationTest`).
- [x] `take()` consumes it; a second redirect finds nothing.
- [x] An unreadable attempt is discarded rather than thrown, like an unreadable refresh token.
- [x] The flavour's configured redirect resolves to exactly one activity of this app, and so does
  its post-logout redirect (`AuthRedirectFilterTest`, run once per flavour).
- [x] Login opens a Custom Tab; no `WebView` exists anywhere in the app.
- [ ] A CI gate forbids `WebView` outright rather than relying on its absence. **Open** — same gate
  as `REQ-APP-AUTH-001`'s access-token check.
- [ ] `/.well-known/assetlinks.json` is served, so the production App Link verifies. **Open** —
  server-side, main repo (exposure plan A7). Until then Android shows a disambiguation dialog
  instead of opening the app.
- [ ] The redirect URIs compiled into the flavours match the ones registered on the realm's
  `basetool-android` client. **Open** — the client exists, but this repo controls only one end of
  that contract; a mismatch surfaces as `invalid_redirect_uri` at the realm, before the app is ever
  reached. Note that `docs/keycloak/realm-config.reference.json` in the main repo does **not**
  list this client, so the committed reference is stale and cannot serve as the check.
