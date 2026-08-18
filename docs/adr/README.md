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

| [0003](0003-token-endpoint-client.md) | Token traffic runs on its own `OkHttpClient`, derived from the API client but stripped of its interceptors: `MandatoryHeadersInterceptor` would put an `Authorization` header on Keycloak's token endpoint, which answers `invalid_client` — an app that logs in once and can never refresh. `ServerTimeInterceptor` is re-added alone, because this is the only traffic that observes the clock a DPoP proof is judged against. Endpoints are derived from the issuer rather than discovered, which also makes the `/userinfo` ban structural (Keycloak answers 500 there under the refresh-only policy). Outcomes are states: `invalid_grant` and a misconfigured realm both arrive as HTTP 400 and only the first means "log in again", and a 2xx with `token_type` other than `Bearer` is named rather than handed on — its symptom is a 401 storm that accuses the backend. The RFC 9449 nonce retry is implemented although the realm demands no nonce, so enabling one is not a client-breaking change. Rejected: one client with per-request overrides, AppAuth (no DPoP), throwing on refusal, startup discovery. | Proposed |

Still to come in Phase 1: the DTO generation pipeline, the login flow (Custom Tab,
session orchestration) and the chapter-04 screens.
