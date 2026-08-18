# Android App — Security Concept

Doc type: **living plan** (draft, pending approval by @greluc).
All external claims verified against live documentation on **2026-08-17**; repo claims verified
against the current codebase. Amended **2026-08-17** after a code-level security audit of the
basetool backend/realm (verified findings are marked "code-verified" in §2/§4). Master plan:
[`ANDROID_APP_PLAN.md`](ANDROID_APP_PLAN.md).

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

## 2. Exposing `/api/v1` (server-side delta — all work in the main `basetool` repo)

Today the backend container is on **no** `net-proxy-*` network — `/api/v1` is not internet-
reachable. Everything in this section is server-side and therefore lands in the main `basetool`
repo under its rules (REQ updates + ADRs + monitoring in the same PR); nothing here is app code.
The exposure package (one PR series):

1. **New NPM vhost** `api.profit-base.online` → `https://backend:11261` (re-encrypt like the
   other hosts); backend joins a new dual-stack proxy network (ADR-0112 pattern so real IPv4/IPv6
   client addresses reach nginx). The version-controlled edge snippets (`limit_req` 20 r/s,
   burst 80, `limit_conn` 500, 429 responses, keyed full-IPv4 / IPv6-/64) apply to every proxy
   host automatically — verify with the existing `EdgeRateLimitSpike` alert.
2. **`/actuator` off the public connector — two layers (code-verified gap).** Unlike
   frontend/ingest (ADR-0090), the backend serves `/actuator/**` on its ordinary app connector
   (no `management.server.port`), `/actuator/health*` is `permitAll`, and the existing NPM
   actuator-deny rules live only in the unversioned NPM admin DB. The exposure package therefore
   (a) moves the backend Actuator to a dedicated management port on the monitoring network
   (ADR-0090 pattern) so the connector the new vhost proxies serves no Actuator at all, and
   (b) still adds the `/actuator` edge deny (404) on the new vhost, asserted by
   `blackbox-edge-deny` for both `/actuator/prometheus` and `/actuator/health`, plus the external
   `.github/workflows/edge-deny-probe.yml` — same posture as frontend/ingest (REQ-OBS-005/-012).
   An unversioned edge rule alone is one NPM misconfiguration away from public health output.
3. **Rate-limit attribution — the backend needs the SEC-02 walk first (code-verified gap).** The
   backend honors `X-Forwarded-For` only from `app.rate-limit.trusted-proxies`, but
   `RateLimitingFilter.resolveClientKey` takes the **leftmost** XFF element — the backend has no
   right-to-left chain walk today. That is safe behind its current single sanitizing hop (the
   SEC-02-hardened frontend) and becomes an exploitable hole behind NPM, which *appends* the real
   peer (`$proxy_add_x_forwarded_for`): a client-chosen leftmost entry mints a fresh per-IP
   bucket per request — a full rate-limit bypass plus foreign-IP framing. Phase 0 therefore
   (a) implements the right-to-left walk in the backend (model: the frontend's
   `ClientIpContextFilter`, REQ-SEC-011/SEC-02), (b) narrows the trusted-proxies range (prod
   today: `172.28.0.0/16`) to the API vhost's proxy network, and (c) has the vhost **overwrite —
   never append or pass through — the whole `X-Forwarded-*`/`Forwarded` family**: the backend
   runs `server.forward-headers-strategy: framework`, whose `ForwardedHeaderFilter` consumes
   forwarded headers from any peer, so an unfiltered client-supplied `X-Forwarded-Host` would
   poison every rebuilt URL (Location headers, problem `instance` URIs). Without correct
   attribution every app user collapses into a single Bucket4j bucket and 429s storm. Note
   CGNAT: many legitimate users can share one IPv4; per-subject (JWT `sub`) limits complement
   per-IP limits (ingest precedent).
4. **Audience enforcement rollout** (REQ-INGEST-008 sequencing): the Android client gets the
   `aud=basetool-backend` mapper at creation; once *all* issuing clients stamp the audience,
   `IRI_BACKEND_EXPECTED_AUDIENCES=basetool-backend` is set in prod. Enforcement before mapping
   would lock out the app the moment the flag flips. Code-verified state: the flag is unset in
   prod today (commented out in `.env.example`; only the E2E stack enforces it) — the flip is a
   **release gate of the exposure PRs**, not a later hardening step: the vhost does not go live
   against a backend that accepts audience-less tokens from arbitrary realm clients.
5. **Monitoring package** (binding, same PRs): blackbox `http_2xx_or_401` liveness probe on the
   API root + IPv6 twin + DNS A/AAAA probes + force-SSL + HSTS probes; keep new `/probe` jobs out
   of `TargetDown`'s scope regex deliberately; auth-failure and rate-limit-rejection counters for
   the new surface (model: `basetool_ingest_auth_failures_total`); alert rules staged per
   REQ-OBS-014 until proven live on the test stack; dashboard updates (03/07/08); Alloy pipeline +
   31-day IP-retention entry for the new vhost's access log **including the privacy-policy
   extension that conditions it** (see privacy doc §7); plus **client-id (`azp`) observability
   from day one** — a bounded per-client counter on the new surface and an unknown-`azp` alert
   (model: `basetool_ingest_client_total` / `IngestUnknownClient`), the detection half of the §5
   revocation lever.
6. **CORS stays closed** (`allowed-origin-patterns` empty). The native app sends no Origin; a
   browser-based client remains deliberately impossible.
7. Edge software watch: NPM 2.15.1 carries an unpatched CVE watch item (CVE-2026-40519, memory).
   The new vhost raises the value of fast NPM bumps — keep the Dependabot merge cadence.
8. **Anonymous-surface stance: default-deny allowlist (explicit, Phase 0).** The backend
   deliberately permits anonymous endpoints (master-data reads, redacted mission browsing, guest
   participant editing, anonymous order creation). The new vhost would make these
   internet-reachable on day one — before the app's guest mode ships (Q6: with the first
   release). Stance: the API vhost is a **default-deny allowlist** — it proxies only the
   endpoint families the app consumes and 404s everything else, rather than blocklisting
   known-anonymous paths. Two reasons: the anonymous surface is branchy (the `/slim` twins, the
   guest participant mutations across PUT/DELETE/check-in/out/payout, `POST /api/v1/orders/items`
   with its table-wide pessimistic lock — a DoS lever), so a blocklist misses paths; and any
   *future* `permitAll` endpoint added for the web app would otherwise become internet-reachable
   the day it merges. The anonymous read surface joins the allowlist only where the app needs it
   pre-login (master data); the anonymous-write and guest paths join when guest mode ships. The
   terms/consent endpoints (`/api/v1/terms/**`) and the registration-status read MUST be on the
   allowlist from day one — the app's terms gate and `PENDING_APPROVAL` handling depend on them.
   Each opened anonymous path gets its own rate budget and an abuse counter/alert.
9. **Per-subject quotas (Phase 0 work item, not just an aspiration).** Extend the backend
   Bucket4j configuration with per-`sub` budgets on the write endpoints the app uses (the ingest
   module's enforceable per-subject limiter is the model), so CGNAT users don't share one IP
   bucket and a single hijacked account cannot exhaust an endpoint. Ships with the exposure PRs
   including metrics + staged alerts. Implementation note (code-verified): the backend's
   `RateLimitingFilter` runs before Spring Security (`HIGHEST_PRECEDENCE + 10`) and can only key
   on IP — the per-`sub` layer must sit *behind* authentication like the ingest
   `SubjectRateLimiter` (service-level, unforgeable key). Today `hangar/**`, `inventory/**`, the
   bank surface, `users/search` and the SSE connect ride only the loose global per-IP umbrella
   (5000/min) — the exposure package gives the app-consumed write families and
   `GET /notifications/stream` connects their own budgets (the stream already caps at 5
   concurrent emitters per `sub` server-side).
10. **App-Links prerequisite.** The preferred redirect URI requires
    `/.well-known/assetlinks.json` on `profit-base.online` (served by the frontend or an NPM
    static location; carries the app's signing-cert SHA-256). Small change, own PR with a
    blackbox probe asserting availability + content type — scheduled in Phase 0. Coupled to key
    rotation: on a v3.1 signing-key rotation the file must list **both** the old and the new
    cert digest *before* the rotated APK ships, or verified App Links — and with them the login
    redirect — break. This goes into the rotation runbook (DEV_CI doc) and the Phase-5 fire-drill
    list (§7).
11. **Keycloak realm hardening (same PR series, code-verified gaps).** The new public surface
    turns these from cosmetic into operational: (a) `eventsEnabled`/`adminEventsEnabled` are
    both `false` — no login-failure, token-error or client-disable event exists anywhere, i.e.
    the "detect" half of the §5 ladder is blind on the very token endpoint that goes public;
    enable user events with a bounded `eventsExpiration`, ship them into the Loki alert stack,
    and extend the VVT/privacy notes accordingly (privacy doc §9). (b) `basetool-frontend` — a
    public client — carries no `pkce.code.challenge.method` attribute, and both existing public
    clients run `fullScopeAllowed: true` (tracked as an open finding in
    `docs/keycloak/README.md`); the Client-Policies infrastructure Phase 0 introduces for DPoP
    (§4) doubles as the vehicle for a realm-wide **"S256 required for public clients"** policy
    and the `fullScopeAllowed` cleanup. (c) `sslRequired` is `"none"` — set `external`. (d) The
    token endpoint (`/realms/iri/protocol/openid-connect/token`) becomes the hottest public path
    (AT 300 s ⇒ each active app user refreshes ~12×/h): give it a stricter,
    **version-controlled** edge budget — REQ-SEC-023 currently declares per-endpoint edge limits
    unversioned host state; carve the token endpoint out of that rule.
12. **Response-cache hardening.** The backend's `ApiCacheControlFilter` emits only `no-cache,
    must-revalidate` (storage with revalidation allowed) and never `no-store`; the exposure
    package adds `Cache-Control: private, no-store` on the sensitive GET families (bank, member
    PII, notifications). The app-side mirror rule — no OkHttp disk cache at all — is in §4.
13. **Minimum-app-version gate (forced upgrade) + edge misc.** The app sends
    `User-Agent: basetool-android/<semver>` on every call; the backend (or vhost) can refuse
    versions below a configured minimum with a dedicated RFC 7807 code the app maps to an
    "update required" screen. This is the missing granularity between "do nothing" and the
    all-or-nothing client kill switch (§5): without it, an app version with a security defect
    can never be locked out without locking out everyone. Pairs with the client-side capability
    ping (plan §4). Also Phase 0, cheap: a **CAA record** for `profit-base.online` (Let's
    Encrypt only), and per-location `client_max_body_size` caps on the vhost (small default;
    larger only on the hangar-import endpoints — the backend's `RequestBodySizeLimitFilter`
    covers only its configured paths). Review the `PaginationUtil` clamp (`MAX_PAGE_SIZE`
    100 000) for the public ingress in the REQ-API amendment — a single anonymous-reachable
    list request returning 100 k rows is an amplification lever the global query timeout only
    bounds in time.
14. **Doc drift found while auditing — already fixed in the main repo (2026-08-17).** Two
    security docs contradicted the code this concept builds on: `docs/keycloak/README.md`
    documented `revokeRefreshToken/refreshTokenMaxReuse = true/5` although realm-wide rotation
    has been **off** since 2026-06-18 (REQ-SEC-012 / ADR-0019 amendment #4) — precisely the fact
    that makes DPoP the RFC 9700 path in §4; and `desktop-ingest.md` REQ-INGEST-012's acceptance
    list claimed "the gateway does not configure `dPoP(...)`" although the ingest `SecurityConfig`
    does (its own requirement body describes the mechanism correctly). Both corrected. Noted here
    because §4's fallback ladder and the DPoP precedent rest on them.

## 3. Keycloak client `basetool-android` (spec)

Modeled on `basetool-sc-extractor` (the existing native-app precedent), tightened:

| Setting | Value | Rationale |
|---|---|---|
| Type | public (no secret) | RFC 8252; a secret in an open-source APK is theater |
| Flows | Standard (Auth Code) only; direct grants OFF; device grant OFF | one login path via Custom Tab |
| PKCE | **S256 enforced** (client attribute `pkce.code.challenge.method=S256`) | RFC 9700 §2.1.1 |
| Redirect URIs | exact, wildcard-free. Prod client: **verified App Link** `https://profit-base.online/app/callback` **only** (assetlinks.json on the domain we control). The custom-scheme fallback `de.kartell.basetool:/oauth2redirect` is registered solely on the dev/test-stack realm's client — a custom scheme is claimable by any installed app (PKCE prevents code theft, but the phishing/confusion surface is free to close) | RFC 9700 §4.1.3; App Links are non-claimable by other apps |
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
  request layer (small, testable surface). Keycloak accepts a proof lifetime of **10 s** with
  **15 s** clock skew (`DPoPUtil`) — tighter than typical mobile clock drift — so the proof
  `iat` is computed from **server time** (tracked via the `Date` header of the latest
  token/API response), never from the raw device clock; the desktop extractor documents clock
  drift as its primary DPoP failure mode.
- **Phase 0 creates this policy for the first time — it is a first, not a repeat.** Verified
  against a fresh production export (2026-08-17): the realm carries **zero client profiles and
  zero policies**. An earlier draft of this document cited the desktop
  extractor as a validated precedent for refresh-only binding; **it is not one.** The extractor
  needs no policy and must not have one: since ADR-0129 its gateway validates the DPoP proof at
  the hop that consumes the token, so binding *both* tokens is the wanted state there
  (REQ-INGEST-012). The setup document that still described an `extractor-dpop` policy was
  corrected in the main repo.

  The app is the opposite case, which is why refresh-only remains right here: it talks to the
  backend directly, and Spring Security's bearer filter rejects a `cnf`-bound access token
  outright.

  Scoping note, verified against the Keycloak sources: there is **no** condition that names
  clients directly. The documented way to scope a policy is a marker **client role** plus the
  `client-roles` condition (provider id `client-roles`, config key `roles`); the executor is
  `dpop-bind-enforcer` with `allow-only-refresh-token-binding` (`DPoPBindEnforcerExecutorFactory`).

- **✅ Verified 2026-08-17 — the posture holds, and the fallback ladder stays unused.** Against a
  throwaway Keycloak 26.7, a profile carrying **only** `allow-only-refresh-token-binding: true`,
  scoped by that marker client role, produces exactly the target posture in the
  **authorization-code** flow: `token_type: Bearer`, access token **without `cnf`**, refresh token
  **bound**, and a refresh refused both without a proof and with a different key. The reproducible
  configuration and the full measurement table are in the main repo's
  `docs/ANDROID_API_EXPOSURE_PLAN.md` section 7.

  Four constraints this puts on the app, each measured rather than assumed:

  1. Profile claims come from the **ID token**. The app must never call `/userinfo`: for a client
     under this policy Keycloak answers **HTTP 500** there instead of a 401 (an
     `IllegalArgumentException` in `UserInfoEndpoint`). The backend is unaffected — it validates
     JWTs locally against the JWKS.
  2. The client keeps `directAccessGrantsEnabled = false`, and this result must never be re-checked
     through a direct grant: under ROPC the same realm binds the access token on the initial grant
     and only narrows it from the first refresh onward, which reads as a failure of the whole
     design when it is an artefact of the shortcut.
  3. The per-client **"Require DPoP bound tokens"** switch stays **off** — it overrides the profile
     and re-binds the access token even on refresh. It is also the prerequisite of
     `enforce-authorization-code-binding-to-dpop`, so a DPoP-bound authorization code and a plain
     Bearer access token cannot be had together. Sending the RFC 9449 §10 `dpop_jkt` parameter on
     the authorization request is accepted and worth doing as defence in depth.
  4. Operationally the client is **frozen while the policy is attached**: every admin edit is
     refused with `invalid_client_metadata` / "DPoP token is disabled", down to a description
     change. Provisioning configures the client first and attaches the policy last; later edits
     need detach → edit → re-attach.

  Fallback ladder, kept on the shelf: (a) bind **both** tokens ("Require DPoP bound tokens") and
  let the backend do DPoP — Spring Security has shipped servlet-side resource-server DPoP since
  **6.5**, auto-enabled whenever `oauth2-jose` is on the classpath, with no DSL to configure and no
  supported seam to customise the `htu` comparison (plain string equality against
  `getRequestURL()`, needing `ForwardedHeaderFilter` behind the proxy); the ingest module already
  runs Bearer+DPoP side by side. (b) plain PKCE + short sessions + revocation levers, documented as
  a REQ-SEC deviation needing owner approval.

**On-device storage** (verified guidance):

- `androidx.security:security-crypto` is **deprecated, final release 1.1.0, no successors** — not
  used. Instead: AES-256-GCM key in **AndroidKeyStore** (`PURPOSE_ENCRYPT|DECRYPT`,
  GCM/NoPadding, StrongBox attempt with fallback); refresh token encrypted with it; ciphertext in
  Preferences DataStore. Key material never enters the app process; ciphertext restored to
  another device is undecryptable. The key additionally sets `setUnlockedDeviceRequired(true)`
  (API 28+): while the device is locked the refresh token is cryptographically unusable —
  exactly threat (c) — and since the app only refreshes in the foreground (no push, Q2) the
  restriction costs nothing.
- **Backup exclusion in all three rule sets** (minSdk 29 spans both worlds): legacy
  `fullBackupContent` (API ≤ 30 devices) *and* `dataExtractionRules` with explicit excludes in
  **both** `<cloud-backup>` and `<device-transfer>` (API 31+; `allowBackup=false` alone does not
  reliably stop D2D transfers — verified Android 12 behavior-change doc).
- Access token lives in memory only. Logout = Keycloak end-session + local wipe + best-effort
  refresh-token revocation call.
- **No OkHttp disk cache.** The API client configures no HTTP cache: the Room read cache
  (backup-excluded, logout-wiped, settings-clearable) is the *only* persistence layer for member
  data — an OkHttp cache would be a second, uncontrolled copy outside every wipe path. The
  server mirrors this with `no-store` on sensitive reads (§2.12).
- **Static guardrails in the app repo (CI-enforced):** a lint/detekt gate forbids `WebView`
  (login runs only in the Custom Tab, RFC 8252), direct `android.util.Log` use (logger facade
  only) and cleartext traffic (explicit `cleartextTrafficPermitted="false"` base config in every
  flavor's Network Security Config); the manifest pins `android:taskAffinity=""` (StrandHogg),
  keeps `exported` surfaces minimal, and sensitive confirm actions set
  `filterTouchesWhenObscured` (tapjacking — complements `setHideOverlayWindows`).
- **Optional app-lock** (user setting): BiometricPrompt `BIOMETRIC_STRONG` + `CryptoObject`
  gating a second, auth-bound Keystore key that wraps the token key. API-29 caveats honored:
  `DEVICE_CREDENTIAL` combos only on API 30+ (`setUserAuthenticationParameters`), API 29 uses
  `setUserAuthenticationValidityDurationSeconds` + `KeyguardManager.isDeviceSecure()`. Keys with
  `setInvalidatedByBiometricEnrollment` die on new enrollment → re-login path required.
- **`FLAG_SECURE` app-wide** (fixed by the design spec, ch. 04 — not just authenticated
  screens; blocks screenshots/cast; ~70 % effective ≤ API 30 per Google's own numbers — treat
  as hardening, not guarantee); `setHideOverlayWindows` where available; API 35+
  `addScreenRecordingCallback` (`DETECT_SCREEN_RECORDING` normal permission) to warn during
  capture. The app-lock renders as the spec's custom KRT lock screen driven by
  BiometricPrompt. A user-facing toggle may relax FLAG_SECURE for screenshots if the org
  wants it — default strict (secondary-decision register in the plan).
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
we do it with the documented backup-pin pattern or not at all. Preferred variant to evaluate
first in Phase 5: pin the **CA key** (ISRG Root X1/X2 SPKI) instead of the leaf — the NSC
`<pin-set>` accepts any pin in the chain, a root pin survives every Let's Encrypt renewal (no
keypair-reuse requirement, no pin churn tied to app updates) and still excludes every other CA;
the leaf+backup pin remains the stricter, higher-maintenance option.

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
- Client politeness is server protection: exponential backoff with full jitter on 429
  (`Retry-After`-aware), 503 and SSE reconnects (mirroring the web client's jittered timer);
  non-idempotent writes are never auto-retried (the version-echo contract turns a blind replay
  into a 409 anyway).
- The app sends `User-Agent: basetool-android/<semver>` on every call (feeds the §2.13
  min-version gate and abuse forensics) and maps a disabled Keycloak client (kill switch, §5) to
  a dedicated "app blocked — check for updates / contact the org" screen rather than a generic
  login error.

## 7. Verification & release gate (Phase 5)

MASVS-based review (MASVS-STORAGE/CRYPTO/AUTH/NETWORK/PLATFORM/RESILIENCE) with MASTG test
procedures; dependency audit against the §7 inventory of the plan; a red-team pass against the
exposure package (rate-limit bypass incl. spoofed-XFF attribution, forwarded-header spoofing,
audience/azp confusion, SSE starvation, page-size amplification); pin-rotation fire drill on the
test stack; assetlinks/key-rotation drill (§2.10); kill-switch drill (client disable + observed
lockout + the §2.13 min-version gate). Findings gate the release.
