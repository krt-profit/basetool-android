# core:auth

Authentication: AppAuth Authorization Code + PKCE S256 flow (Custom Tab), token store
(Android Keystore AES-256-GCM → DataStore, backup/D2D-excluded), DPoP proof signer for the
token endpoint (refresh-token-only binding via Keycloak Client Policy), session state
machine (login, silent refresh, `PENDING_APPROVAL`, terms gate, logout + revocation),
optional biometric app-lock. Security contract: `docs/ANDROID_APP_SECURITY.md` §4.

Three constraints that are easy to get subtly wrong:

- The token-wrapping Keystore key sets **`setUnlockedDeviceRequired(true)`** — while the device
  is locked the refresh token is cryptographically unusable. Affordable because the app only
  refreshes in the foreground (no push channel, Q2).
- DPoP proof `iat` is derived from **server time** (tracked from the `Date` response header),
  never the raw device clock: Keycloak allows a 10 s proof lifetime with 15 s skew, which is
  tighter than everyday mobile clock drift.
- Under the refresh-only Client Policy a **voluntarily** sent proof makes Keycloak bind the
  access token too — which the backend's bearer filter then rejects. Proofs go on
  token/refresh requests only, never on API calls.

Redirect URI: the prod client accepts the verified App Link **only**; the custom scheme exists
solely on the dev/test realm.
