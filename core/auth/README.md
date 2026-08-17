# core:auth

Authentication: AppAuth Authorization Code + PKCE S256 flow (Custom Tab), token store
(Android Keystore AES-256-GCM → DataStore, backup/D2D-excluded), DPoP proof signer for the
token endpoint (refresh-token-only binding via Keycloak Client Policy), session state
machine (login, silent refresh, `PENDING_APPROVAL`, terms gate, logout + revocation),
optional biometric app-lock. Security contract: `docs/ANDROID_APP_SECURITY.md` §4.
