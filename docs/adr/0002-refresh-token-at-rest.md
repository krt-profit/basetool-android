# ADR-0002 — Keeping the refresh token at rest, and testing that we do

- **Status:** Proposed
- **Date:** 2026-08-18
- **Related:** [ADR-0001](0001-core-module-split-and-network-layer.md) ·
  [`docs/specs/auth.md`](../specs/auth.md) (`REQ-APP-AUTH-001…005`) ·
  [`docs/ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §4 ·
  [`docs/ANDROID_APP_PRIVACY_GDPR.md`](../ANDROID_APP_PRIVACY_GDPR.md) §2 ·
  main repo ADR-0131

## Context

The app must survive being closed without asking the member to log in again, which means keeping a
refresh token. That is the one secret this app has at rest, and the threat is concrete rather than
theoretical: a phone is lost unlocked, lost locked, or its data is restored onto someone else's
device.

The security concept fixes the mechanism (AES-256-GCM, Keystore key, DataStore ciphertext, backup
exclusion in both rule sets). What it does not answer is how any of it gets **tested**, and an
untested wipe path is indistinguishable from a missing one.

## Decision

**A `SecretCipher` seam, with the Keystore implementation on one side and everything else on the
other.**

`RefreshTokenStore` — what is written, what happens when it cannot be read, what a wipe removes —
runs against a fake cipher in ordinary JVM tests. `KeystoreSecretCipher` holds every Android-only
concern: the non-exportable key, `setUnlockedDeviceRequired(true)`, the StrongBox request and its
fallback. The Android Keystore cannot be exercised on a JVM, so putting the seam anywhere higher
would have made the *behaviour* untestable, not just the crypto.

**An unreadable token is a state, not an exception.** A key invalidated by a new biometric
enrolment, a locked device under `setUnlockedDeviceRequired`, a blob restored from another phone —
all three mean "no usable session". `read()` answers `null` and clears the blob. Throwing would turn
each of them into a crash on start-up where the correct behaviour is a login prompt, and clearing
means the failure is paid once rather than on every read.

**The backup exclusion is pinned by a test that reads the XML.** `AuthDataStore.RELATIVE_PATH`
publishes the exact file DataStore writes, and `BackupExclusionTest` in `:app` asserts both rule
files exclude it — the extraction rules in both their `cloud-backup` and `device-transfer` sections.
This is the one failure in the whole design that produces **no symptom**: rename the store, or
exclude a path that does not exist, and nothing breaks, nothing warns, and a refresh token starts
travelling to Google Drive. It was also nearly the state this change inherited — the placeholder
rules excluded `krt_tokens`, while Preferences DataStore writes
`datastore/krt_tokens.preferences_pb`, so the exclusion would have matched nothing.

**StrongBox is requested unconditionally.** `StrongBoxUnavailableException` is the check; an SDK
guard would be dead code at minSdk 29, which Android Lint says out loud.

## Consequences

**Two acceptance items are honestly open.** The Keystore implementation itself is unverified until
the instrumented suite exists, and "a restored backup does not contain the file" is a device-level
observation. Both are `[ ]` in `REQ-APP-AUTH-002` and `-004` rather than implied by the green JVM
tests.

**`:app` now depends on `:core:auth`.** That was going to happen anyway, and it is what lets the
backup test compare the app's resources against the module's published constant. The alternative —
duplicating the path string in a test — would pin the copy instead of the contract.

**The § 25 TDDDG assessment does not change, but its subject now exists.** The privacy concept
already classified "Keycloak tokens after deliberate login" as strictly necessary and
consent-free; §2 now records what is concretely stored, in what form, and how it is wiped.

**Nothing here logs in yet.** The store, the cipher and the proof factory are the parts that must be
right before a token exists to put in them; the token client and the Custom Tab flow follow.

## Alternatives considered

*`androidx.security:security-crypto` (`EncryptedSharedPreferences`).* The obvious choice two years
ago and deprecated with no successor (final release 1.1.0). Building the one secret at rest on an
abandoned library buys convenience now and a migration later.

*Store the access token too, to skip a refresh on cold start.* Saves one round trip on a token that
expires in five minutes, and doubles the number of secrets at rest. The refresh it saves is the same
one that has to happen a few minutes later anyway.

*Encrypt with a key derived from a device credential instead of a Keystore key.* Ties the token to
the screen lock, which sounds stronger and is weaker in practice: the derivation material lives in
the app process, and the key does not die when the device is wiped or the credential changes.

*Skip the seam and test the Keystore through Robolectric shadows.* Would test the shadow, not the
Keystore — and would still leave `RefreshTokenStore`'s failure paths untested, because the shadow
does not fail the way a real invalidated key does.
