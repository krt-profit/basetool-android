# Architecture Decision Records

Every architecturally significant decision for the Android app is recorded here **before or
with** the change that implements it (same discipline as the main `basetool` repo).

Format: `NNNN-short-kebab-title.md` with sections *Status* (proposed / accepted / superseded
by NNNN), *Context*, *Decision*, *Consequences*. Numbering starts at 0001 and is
repo-local — main-repo ADRs (e.g. the API-exposure and mobile-auth ADRs from Phase 0) are
referenced by their repo-qualified id, never renumbered here.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-core-module-split-and-network-layer.md) | `core:common` (logging facade) and `core:network` (client, mandatory headers, problem→state mapping) as separate modules; `core:auth` will depend on `core:network` and implement its `AccessTokenProvider`, which is **synchronous** because it is read from an OkHttp interceptor. Errors are classified by the backend's stable `code`, not the HTTP status — 403 covers pending approval, the terms gate and a real authorisation failure, and a status-based client shows the wrong screen for two of three. No HTTP disk cache (it would be a second copy of member data outside every wipe path) and a `Date`-header-derived `ServerClock` for DPoP proof timing (Keycloak allows 10 s lifetime / 15 s skew, tighter than mobile clock drift). Hilt modules deliberately follow with `core:auth`. | Proposed |

| [0002](0002-refresh-token-at-rest.md) | The refresh token is kept AES-256-GCM encrypted under a non-exportable Keystore key bound to an unlocked device, ciphertext in a Preferences DataStore, excluded from backup in both rule sets. The decision that shapes the code is the **`SecretCipher` seam**: the Keystore cannot run on a JVM, so the cipher is the fake and everything above it — what is written, what happens when it cannot be read, what a wipe removes — is tested for real. An unreadable token is a state (`null` + clear), not an exception, because the three ways it happens all mean "log in again" and throwing would crash on start-up. The backup exclusion is pinned by a test reading the XML against a published path constant: it is the one failure here with no symptom, and the placeholder rules this change inherited would have matched nothing. Open and marked so: the Keystore implementation and the restored-backup check both need a device. Rejected: security-crypto (deprecated, no successor), persisting the access token, a credential-derived key, Robolectric shadows instead of the seam. | Proposed |

Still to come in Phase 1: the DTO generation pipeline, the token client and the login flow.
