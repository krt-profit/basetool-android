# Android App — Security Concept

Doc type: **living plan** (draft, pending approval by @greluc).
All external claims verified against live documentation on **2026-08-17**; repo claims verified
against the current codebase. Master plan: [`ANDROID_APP_PLAN.md`](ANDROID_APP_PLAN.md).

## 1. Threat model

Assets: member accounts/tokens, member PII (usernames, roles, org data), org-internal operational
data (missions, bank, inventory), server availability. Adversaries: (a) internet-wide scanners and
credential stuffers hitting a newly public API host; (b) authors of unofficial clients scraping or
automating the API; (c) a thief/finder of an unlocked or locked device; (d) malicious apps on the
same device; (e) supply-chain attackers targeting the public app repo/CI. Explicitly accepted
residual risk: **a determined adversary can build a working third-party client** — the app is open
source, the client is public (no secret), and attested traffic can be proxied through a genuine
app on a genuine device. Sources for this honesty bar: OWASP MASVS-RESILIENCE ("the absence of
these measures does not in itself constitute a vulnerability"), Google Play Integrity overview
("part of a broader anti-abuse strategy, not sole mechanism"). Our bar: raise cost, detect,
throttle, revoke — and never weaken server-side authorization in exchange for client-side trust.

## 2. Exposing `/api/v1` (server-side delta in this repo)

Today the backend container is on **no** `net-proxy-*` network — `/api/v1` is not internet-
reachable. The exposure package (one PR series, each with specs/monitoring per root `CLAUDE.md`):

1. **New NPM vhost** `api.profit-base.online` → `https://backend:11261` (re-encrypt like the
   other hosts); backend joins a new dual-stack proxy network (ADR-0112 pattern so real IPv4/IPv6
   client addresses reach nginx). The version-controlled edge snippets (`limit_req` 20 r/s,
   burst 80, `limit_conn` 500, 429 responses, keyed full-IPv4 / IPv6-/64) apply to every proxy
   host automatically — verify with the existing `EdgeRateLimitSpike` alert.
2. **`/actuator` edge deny** on the new vhost (404), asserted by `blackbox-edge-deny` for both
   `/actuator/prometheus` and `/actuator/health`, plus the external
   `.github/workflows/edge-deny-probe.yml` — same posture as frontend/ingest (REQ-OBS-005/-012).
3. **Rate-limit attribution**: the backend honors `X-Forwarded-For` only from
   `app.rate-limit.trusted-proxies` (today: the frontend). NPM's network gateway must be added,
   keeping the right-to-left chain-walk semantics (REQ-SEC-011/SEC-02) — otherwise every app user
   collapses into a single Bucket4j bucket and 429s storm. Note CGNAT: many legitimate users can
   share one IPv4; per-subject (JWT `sub`) limits complement per-IP limits (ingest precedent).
4. **Audience enforcement rollout** (REQ-INGEST-008 sequencing): the Android client gets the
   `aud=basetool-backend` mapper at creation; once *all* issuing clients stamp the audience,
   `IRI_BACKEND_EXPECTED_AUDIENCES=basetool-backend` is set in prod. Enforcement before mapping
   would lock out the app the moment the flag flips.
5. **Monitoring package** (binding, same PRs): blackbox `http_2xx_or_401` liveness probe on the
   API root + IPv6 twin + DNS A/AAAA probes + force-SSL + HSTS probes; keep new `/probe` jobs out
   of `TargetDown`'s scope regex deliberately; auth-failure and rate-limit-rejection counters for
   the new surface (model: `basetool_ingest_auth_failures_total`); alert rules staged per
   REQ-OBS-014 until proven live on the test stack; dashboard updates (03/07/08); Alloy pipeline +
   31-day IP-retention entry for the new vhost's access log **including the privacy-policy
   extension that conditions it** (see privacy doc §7).
6. **CORS stays closed** (`allowed-origin-patterns` empty). The native app sends no Origin; a
   browser-based client remains deliberately impossible.
7. Edge software watch: NPM 2.15.1 carries an unpatched CVE watch item (CVE-2026-40519, memory).
   The new vhost raises the value of fast NPM bumps — keep the Dependabot merge cadence.
8. **Anonymous-surface stance (explicit, Phase 0).** The backend deliberately permits anonymous
   endpoints (master-data reads, redacted mission browsing, guest participant editing, anonymous
   order creation). The new vhost would make these internet-reachable on day one — long before
   the app's guest mode (post-MVP). Default stance: the API vhost **denies the anonymous-write
   and guest paths** (`POST /api/v1/orders`, `/orders/items`, the guest participant mutations)
   via edge location rules until guest mode ships, and the anonymous read surface stays open
   only where the app needs it pre-login (master data). Each opened anonymous path gets its own
   rate budget and an abuse counter/alert. Revisit when Q6 activates guest mode.
9. **Per-subject quotas (Phase 0 work item, not just an aspiration).** Extend the backend
   Bucket4j configuration with per-`sub` budgets on the write endpoints the app uses (the ingest
   module's enforceable per-subject limiter is the model), so CGNAT users don't share one IP
   bucket and a single hijacked account cannot exhaust an endpoint. Ships with the exposure PRs
   including metrics + staged alerts.
10. **App-Links prerequisite.** The preferred redirect URI requires
    `/.well-known/assetlinks.json` on `profit-base.online` (served by the frontend or an NPM
    static location; carries the app's signing-cert SHA-256). Small change, own PR with a
    blackbox probe asserting availability + content type — scheduled in Phase 0.

## 3. Keycloak client `basetool-android` (spec)

Modeled on `basetool-sc-extractor` (the existing native-app precedent), tightened:

| Setting | Value | Rationale |
|---|---|---|
| Type | public (no secret) | RFC 8252; a secret in an open-source APK is theater |
| Flows | Standard (Auth Code) only; direct grants OFF; device grant OFF | one login path via Custom Tab |
| PKCE | **S256 enforced** (client attribute `pkce.code.challenge.method=S256`) | RFC 9700 §2.1.1 |
| Redirect URIs | exact, wildcard-free. Preferred: **verified App Link** `https://profit-base.online/app/callback` (assetlinks.json on the domain we control); fallback custom scheme `de.kartell.basetool:/oauth2redirect` for dev | RFC 9700 §4.1.3; App Links are non-claimable by other apps |
| Scopes | `openid profile email roles` + default scope with `aud=basetool-backend` mapper; `fullScopeAllowed=false`; **no `offline_access`** initially (mirrors the frontend audit L-4 decision; revisit only with owner sign-off) | least privilege |
| Access-token lifespan | per-client override **300 s** (matches realm) | short blast radius |
| Session bounds | per-client Client Session Idle/Max tuned for mobile (proposal: idle 30 d / max 180 d, matching realm SSO) | usable without weekly logins |
| Sender-constraining | **DPoP with refresh-token-only binding via Client Policies — target posture** (see §4) | RFC 9700 §2.2.2 |
| Consent | not required (first-party) | — |

Registration of the client id in the Terms-of-Use approved-client-software list (REQ-SEC-027) is
part of Phase 0.

## 4. Token security

**The rotation option is off the table realm-wide**: `revokeRefreshToken=false` is a deliberate
REQ-SEC-012 amendment (rotation broke BFF sessions under concurrent refresh), and the toggle is a
*realm* setting — the mobile client cannot get rotation without re-imposing it on the frontend.
That makes **DPoP the RFC 9700-compliant path for the public mobile client** ("refresh tokens for
public clients MUST be sender-constrained or use refresh token rotation"):

- Keycloak ≥ 26.4 **officially supports DPoP** (release notes; blog 2025-10-09). Precision on
  the mechanism (per the live Keycloak DPoP doc): the per-client **"Require DPoP bound tokens"**
  toggle binds **both** access and refresh token for a public client — that alone would break us,
  because the backend's Spring Security bearer filter **rejects any access token carrying
  `cnf.jkt`** presented as Bearer (verified in `BearerTokenAuthenticationFilter` source). The
  target posture — **refresh token DPoP-bound, access token plain Bearer** — is delivered by the
  dedicated **Client Policies executor** ("enforce DPoP binding only for the refresh token",
  added with the 26.4 DPoP GA). The DPoP proof is then verified at the **token endpoint**
  (Keycloak), never at the resource server. Note: a *voluntarily* sent proof also causes Keycloak
  to bind both tokens — the app must therefore send DPoP proofs **only** on token/refresh
  requests under the refresh-only policy, never ad hoc.
- App side: per-install **P-256 key in Android Keystore** (non-exportable, StrongBox where
  available); DPoP proof JWTs (`htm`/`htu`/`iat`/`jti`) built with Nimbus JOSE and attached to
  token/refresh requests. AppAuth has no built-in DPoP — the proof header is added in our token
  request layer (small, testable surface).
- **Phase-0 verification task (test stack, before committing):** configure the refresh-only
  Client Policy for `basetool-android` on Keycloak 26.7 and confirm: access token issued without
  `cnf` (backend accepts it as Bearer), refresh token bound, refresh replay from a different key
  fails. Fallback ladder if the policy is unavailable or misbehaves: (a) bind **both** tokens
  ("Require DPoP bound tokens") and add `.dPoP()` support to the backend resource server (Spring
  Security ≥ 6.5 supports it; the ingest module already runs Bearer+DPoP side by side — proven
  pattern in this codebase, at the cost of a backend change + proofs on every API call);
  (b) plain PKCE + short sessions + revocation levers, documented as a REQ-SEC deviation
  needing owner approval.

**On-device storage** (verified guidance):

- `androidx.security:security-crypto` is **deprecated, final release 1.1.0, no successors** — not
  used. Instead: AES-256-GCM key in **AndroidKeyStore** (`PURPOSE_ENCRYPT|DECRYPT`,
  GCM/NoPadding, StrongBox attempt with fallback); refresh token encrypted with it; ciphertext in
  Preferences DataStore. Key material never enters the app process; ciphertext restored to
  another device is undecryptable.
- **Backup exclusion in all three rule sets** (minSdk 29 spans both worlds): legacy
  `fullBackupContent` (API ≤ 30 devices) *and* `dataExtractionRules` with explicit excludes in
  **both** `<cloud-backup>` and `<device-transfer>` (API 31+; `allowBackup=false` alone does not
  reliably stop D2D transfers — verified Android 12 behavior-change doc).
- Access token lives in memory only. Logout = Keycloak end-session + local wipe + best-effort
  refresh-token revocation call.
- **Optional app-lock** (user setting): BiometricPrompt `BIOMETRIC_STRONG` + `CryptoObject`
  gating a second, auth-bound Keystore key that wraps the token key. API-29 caveats honored:
  `DEVICE_CREDENTIAL` combos only on API 30+ (`setUserAuthenticationParameters`), API 29 uses
  `setUserAuthenticationValidityDurationSeconds` + `KeyguardManager.isDeviceSecure()`. Keys with
  `setInvalidatedByBiometricEnrollment` die on new enrollment → re-login path required.
- **`FLAG_SECURE`** on all authenticated screens (blocks screenshots/cast; ~70 % effective ≤ API
  30 per Google's own numbers — treat as hardening, not guarantee); `setHideOverlayWindows` where
  available; API 35+ `addScreenRecordingCallback` (`DETECT_SCREEN_RECORDING` normal permission)
  to warn during capture. A user-facing toggle may relax FLAG_SECURE for screenshots if the org
  wants it — default strict.
- Cached member data: app-private storage under platform FBE (mandatory on devices launched with
  Android 10+) — the documented baseline; backup-excluded. SQLCipher (`sqlcipher-android` 4.17.0,
  active) only if the org classifies the cache as sensitive — it buys forensic-extraction
  resistance at the cost of key-management failure modes; default: not used, revisit at Phase 5.

## 5. Keeping foreign clients out — layered, with honest limits

| Layer | What it stops | What it does NOT stop |
|---|---|---|
| OAuth-only member surface + Keycloak brute-force protection + no self-registration; anonymous paths blocked at the vhost until guest mode (§2.8) | anonymous scraping of member data; bot signups; day-one abuse of the anonymous write endpoints | a member's own token in a foreign client |
| PKCE S256 + exact redirect URIs + App Links | code interception, redirect hijack, app impersonation at login | — |
| DPoP-bound refresh token (per-install Keystore key) | **token exfiltration**: a stolen refresh token is useless off-device; the long-lived credential is sender-constrained | a foreign client doing its own full login as a real member |
| Short (300 s) access tokens + per-client session ceilings | long replay windows | — |
| `azp` visibility: backend logs/metrics track the issuing client id | *detection* of tokens minted via unexpected clients; input for revocation | spoofing — a foreign app can impersonate the public client id (no secret exists; an `azp` allowlist raises effort only marginally — stated honestly) |
| Kill switch: disable the `basetool-android` Keycloak client | instantly invalidates the whole app population's logins/refresh (5-min max token tail) | — |
| Edge `limit_req`/`limit_conn` + backend Bucket4j per-endpoint budgets + per-`sub` quotas | volumetric abuse, scraping speed, credential stuffing | slow-and-low abuse |
| Abuse analytics on existing logs/metrics (endpoint-mix, velocity, correlationId trails; optional CrowdSec on NPM logs — firewall bouncer needs no NPM image change) | detection → targeted revocation | — |
| Terms of Use: REQ-SEC-027 "approved client software" + the app added to the approved list | gives a *policy/legal* lever against unofficial clients | technical enforcement |
| **Optional Phase 5** — server-verified **hardware Key Attestation** at enrollment (challenge → TEE/StrongBox cert chain → verify chain + CRL server-side with Google's `android/keyattestation` library → bind the DPoP key to the account/install) | repackaged clients without hardware-backed keys; emulator farms; raises the proxying bar substantially — and **without Play services or per-user data flows to Google** (the server fetches Google's CRL; the device talks to no one new) | a genuine app on a genuine device used as a proxy; devices with OEM-root or broken attestation need a fallback tier |
| **Optional, needs approval (Q3)** — Play Integrity as a *tiering* signal (never a hard gate: enforcing `PLAY_RECOGNIZED` would lock out our own GitHub-distributed installs; sideloaded builds report `UNRECOGNIZED_VERSION`) | adds Google's device/app verdict for risk tiering | same proxying limit; requires Play services; sends device/app telemetry to Google |

**Certificate pinning** (channel hardening — explicitly *not* a client-exclusion tool; a foreign
client simply doesn't pin): Network Security Config `<pin-set>` with **our SPKI + a pre-generated
offline backup key's SPKI**, `expiration` ~12–18 months as fail-open insurance, `<debug-overrides>`
for the local test stack. Operational precondition: Let's Encrypt renewals rotate keys unless the
keypair/CSR is reused — so pinning requires either keypair-reuse on the pinned host or accepting
leaf-pin churn tied to app updates. Rollout in Phase 5 only, with a written rotation runbook
(ship the *next* key's pin in an update before activating it server-side); a bricked-pin incident
is worse than the MITM risk in year one. OkHttp's own docs: "Certificate Pinning is Dangerous!" —
we do it with the documented backup-pin pattern or not at all.

## 6. App/API behavior contracts (security-relevant)

- Handle RFC 7807 codes as first-class states: `UNAUTHENTICATED` (401 → silent refresh → login),
  `PENDING_APPROVAL` (dedicated screen), terms-gate 403 (in-app acceptance via `/api/v1/terms/*`),
  `ACCESS_DENIED` (KRT-toned error), `RATE_LIMIT_EXCEEDED` (429 + `Retry-After` backoff),
  `SERVICE_UNAVAILABLE` (503 retryable), `OPTIMISTIC_LOCK` (409 reload-and-retry).
- SSE stream authenticates at connect; on token expiry mid-stream the stream survives until the
  30-min completion — reconnect with a fresh token; honor the terminal `replaced` event
  (do-not-reconnect); keep the unread-count poll as liveness fallback (watchdog ≈ 3× the 20 s
  heartbeat).
- No secrets in the repo or the APK: client id, endpoints, pins are public **by design** and
  documented as such. Release signing keys live only in the CI `release` environment (see
  [`ANDROID_APP_DEV_CI.md`](ANDROID_APP_DEV_CI.md)); `.env.test`-style local files stay gitignored
  and synthetic (hard repo rule: never production credentials in tests/local stacks).
- Logging: no names, emails, tokens in app logs (mirror REQ-OBS-004); correlation ids only.

## 7. Verification & release gate (Phase 5)

MASVS-based review (MASVS-STORAGE/CRYPTO/AUTH/NETWORK/PLATFORM/RESILIENCE) with MASTG test
procedures; dependency audit against the §7 inventory of the plan; a red-team pass against the
exposure package (rate-limit bypass, forwarded-header spoofing, audience/azp confusion, SSE
starvation); pin-rotation fire drill on the test stack; kill-switch drill (client disable +
observed lockout). Findings gate the release.
