# Android App — Master Plan

Doc type: **living plan** (draft, pending approval by @greluc — see [Open decisions](#9-open-decisions)).
Fact base: repo state on branch `claude/basetool-android-app-concept-6c6ef5` plus live-verified
external documentation, all checked on **2026-08-17**. Companion documents:

- [`ANDROID_APP_SECURITY.md`](ANDROID_APP_SECURITY.md) — API exposure, Keycloak client, token
  handling, layered abuse prevention, monitoring obligations.
- [`ANDROID_APP_PRIVACY_GDPR.md`](ANDROID_APP_PRIVACY_GDPR.md) — GDPR / TDDDG / German-law analysis
  and the compliance checklist.
- [`ANDROID_APP_DEV_CI.md`](ANDROID_APP_DEV_CI.md) — local dev/test environment and hardened
  GitHub CI for a public repository.
- [`ANDROID_APP_DESIGN_PROMPT.md`](ANDROID_APP_DESIGN_PROMPT.md) — ready-to-use prompt for Claude
  Design to produce the binding design specification (mockups, components, tokens).

## 1. Goal & scope

A **native Android app** (Kotlin) that makes the Profit Basetool usable on phones (portrait-first)
and tablets (landscape-first) without a browser. The app consumes the **existing backend REST API**
(`/api/v1`, OpenAPI-documented) — no parallel business logic, no new business API. German is the
primary language, English fully supported (same DE/EN posture as the web app). The visual language
is the **DAS KARTELL design system** (dark-only, binding — REQ-UI-001…013).

Non-goals (proposed, see open decisions): the **admin area stays web-only**; the app targets the
member/officer daily-driver surface. iOS is out of scope for this plan (the API/auth groundwork is
reusable later).

**Honest security framing up front:** a public API consumed by an open-source public client cannot
*cryptographically exclude* third-party clients — every client-side secret is public and every
client-side check runs on hardware the adversary owns (OWASP MASVS-RESILIENCE; Google positions
even Play Integrity as "part of a broader anti-abuse strategy, not a sole mechanism"). The design
goal is therefore **raise cost → detect → throttle → revoke**, layered as described in
[`ANDROID_APP_SECURITY.md`](ANDROID_APP_SECURITY.md). Claims beyond that would be snake oil.

## 2. Verified platform baseline (all sources fetched live 2026-08-17)

| Item | Value | Source |
|---|---|---|
| minSdk | **30** (Android 11) — raised from 29; the reach lost is a few percent, the second app-lock key path it removed had already shipped a total failure (ADR-0006) | apilevels.com (Statcounter 04/2026) |
| targetSdk | **37** (Android 17, owner decision 2026-08-17) — exceeds the Play floor (≥ 36 for new apps/updates by 2026-08-31); the API-37 behavior changes below apply from day one | developer.android.com/google/play/requirements/target-sdk |
| compileSdk | **37** (Android 17; stable-since-June-2026 date via secondary source, officially corroborated by the Android 17 QPR beta cycle) | developer.android.com/about/versions |
| Kotlin | **2.4.10** (K2 default) | kotlinlang.org/docs/releases.html |
| KSP | **2.3.11** (KSP2-only line, standalone versioning) | github.com/google/ksp/releases |
| AGP / Gradle / JDK | **9.3.0 / 9.5 / JDK 17+** | developer.android.com/build/releases/gradle-plugin |
| Compose BOM | **2026.08.00** → compose-ui 1.12.0, **Material 3 1.4.0**, **material3-adaptive 1.3.0** | developer.android.com/develop/ui/compose/bom/bom-mapping |
| Adaptive layout API | `currentWindowAdaptiveInfo().windowSizeClass` — compact width = phone portrait, expanded+ = tablet landscape | developer.android.com/develop/ui/compose/layouts/adaptive |
| OkHttp | **5.5.0** (2026-08-16) | github.com/square/okhttp CHANGELOG |
| AppAuth-Android | 0.11.1 (2021; Keycloak-endorsed, low-activity maintenance; alternative: kotlin-multiplatform-oidc 0.18.1, active) | github.com/openid/AppAuth-Android |
| Keycloak (server, exists) | 26.7 — **DPoP officially supported since 26.4** | keycloak.org release notes |

Behavior changes we design for from day one (verified): Android 16+ **ignores orientation/
resizability restrictions on sw ≥ 600 dp screens** — and at targetSdk 37 the temporary
compat opt-out no longer exists → tablets must be truly adaptive; orientation lock is only ever
applied on phone-sized screens. **Edge-to-edge is enforced** (no opt-out since target 36).
**Predictive back** is on by default (androidx.activity dispatcher from the start). The **16 KB
page-size** requirement affects any native `.so` we pull in transitively (AGP 9.3 aligns by
default; relevant if SQLCipher is ever adopted). Additional API-37 changes that now bind us:
**`ACCESS_LOCAL_NETWORK`** is enforced for local-network access (affects only the `dev` flavor
talking to a LAN test stack — declared there, not in release); **Certificate Transparency + ECH
are on by default** for the platform TLS stack (fine for the Let's Encrypt-served production
hosts; the local test stack stays reachable via the debug-only trust overrides); the
reflection/`MessageQueue`/`System.load()` hardenings don't touch this codebase.

## 3. System architecture

```
┌─────────────┐  OIDC Auth Code + PKCE (Custom Tab)   ┌──────────────────────┐
│ Android App │──────────────────────────────────────▶│ Keycloak 26.7        │
│  (Kotlin,   │◀──────── tokens (AT 300 s + RT) ──────│ keycloak.profit-…    │
│  Compose)   │                                       └──────────────────────┘
│             │  Bearer AT + X-Active-Org-Unit-Id     ┌──────────────────────┐
│             │──────────────────────────────────────▶│ NPM edge (new vhost) │
│             │◀── JSON / RFC 7807 / SSE stream ──────│ api.profit-base.online│
└─────────────┘                                       │   → backend:11261    │
                                                      └──────────────────────┘
```

Key decisions (each becomes an ADR in this repo when implemented — see §8):

1. **Direct-to-backend, no new business API.** The backend is already a complete, stateless JWT
   resource server with a committed OpenAPI contract (`backend/src/main/resources/api/openapi.json`,
   gate-enforced). The delta is *exposure*, not *construction*: a new NPM vhost
   (`api.profit-base.online` → `https://backend:11261`), backend joins a `net-proxy-*` network.
   The existing edge protections (per-IP `limit_req` 20 r/s / burst 80 / 429, force-SSL, HSTS)
   apply to every NPM proxy host automatically. The alternative (an ingest-style gateway,
   ADR-0129 pattern) is rejected for the app: it would duplicate the whole API surface for no
   authorization gain — the backend already enforces everything per-user via `@PreAuthorize`.
2. **New Keycloak public client `basetool-android`** modeled on the `basetool-sc-extractor`
   precedent: Authorization Code + PKCE **S256 enforced**, no client secret, no direct grants,
   `fullScopeAllowed=false`, audience mapper `aud=basetool-backend`, exact redirect URIs.
   Sender-constraining via **DPoP-bound refresh tokens** (Keycloak ≥ 26.4 officially supports
   DPoP; the refresh-token-only binding for public clients — access tokens stay plain Bearer,
   which keeps the backend's bearer filter happy since Spring Security rejects DPoP-bound tokens
   presented as Bearer — is delivered via a **Client Policies executor**, not the plain
   per-client toggle, which binds both token types). **The exact configuration is a Phase-0
   verification task on the test stack** — the security doc §4 carries the mechanism and the
   fallback ladder.
3. **Mobile session = pure Bearer.** No cookies, no Spring Session, no CSRF involvement (verified
   down to the Spring Security 7.1 bytecode: `OAuth2ResourceServerConfigurer#registerDefaultCsrfOverride`
   registers every Bearer request as a CSRF exemption; the backend's explicit ignore list only
   serves the anonymous write paths). The app sends
   `Authorization`, `X-Active-Org-Unit-Id` (org pin), `Accept-Language`, and a client-generated
   `X-Correlation-Id` on every call.
4. **Live data**: MVP uses the existing Bearer-capable **SSE stream**
   `GET /api/v1/notifications/stream` (heartbeat 20 s, reconnect on 30-min completion) plus
   focus-triggered refresh. Full live-sync parity later via a **backend-side bridge** fed from the
   existing Redis `basetool:livesync:changed` channel (the `/ws/sync` socket is frontend-session-
   bound and not reusable; the wire payload is only `{topic, sections}`, so a bridge is trivial
   and every re-fetch re-authorizes server-side).
5. **Offline = read cache only.** Cached lists/details for airplane-mode viewing; **no offline
   writes** (the optimistic-locking `version`-echo contract makes queued offline mutations a
   conflict factory). Cache lives in app-private storage, excluded from cloud backup and D2D
   transfer; the refresh token is Keystore-encrypted (see security doc).

## 4. App architecture

- **Language/UI**: Kotlin 2.4 (K2), Jetpack Compose + Material 3, dark-only KRT theme (dynamic
  color deliberately disabled — brand rule), edge-to-edge, predictive back.
- **Pattern**: MVVM + unidirectional data flow. `ViewModel` → `Repository` → (HTTP client |
  Room cache). Coroutines + Flow everywhere. Paging 3 for the paginated list endpoints
  (`PageResponse<T>` adapter).
- **Modules** (Gradle, own version catalog `gradle/libs.versions.toml`):
  - `app` — wiring, navigation, DI graph.
  - `core:designsystem` — KRT theme, tokens, the Compose component library (buttons ladder,
    hud-box, cards, chips/status pills, tables/tree lists, modals, toasts, combobox
    equivalents), seeded from `docs/design/android/artifacts/Theme.kt` and chapter 02 of the
    design spec.
  - `core:network` — OkHttp/Retrofit, kotlinx.serialization, RFC 7807 problem parser (stable
    `code` + `correlationId`), auth interceptor, SSE client, DTOs **generated from the committed
    `openapi.json`** (openapi-generator, same source-of-truth approach as the frontend's typed JS
    DTOs per ADR-0125). No OkHttp disk cache — the Room cache is the only persistence layer
    (security doc §4); 429/503/SSE reconnects use exponential backoff with full jitter;
    non-idempotent writes are never auto-retried; every call carries
    `User-Agent: basetool-android/<semver>` (security doc §6).
  - `core:auth` — AppAuth flow, token store (Keystore AES-GCM + DataStore), DPoP proof signer
    (proof `iat` from server-synced time, security doc §4), session state machine (incl.
    `PENDING_APPROVAL`, terms gate, logout/revocation, kill-switch/min-version error mapping).
  - `core:data` — repositories, Room cache, org-unit context holder.
  - `feature:*` — one module per area (missions, notifications, hangar, inventory, orders, bank,
    refinery, exchange, promotion, settings).
- **DI**: Hilt (KSP). **Images**: Coil (loads only from basetool hosts). **No** Firebase, no
  analytics, no ad/tracking SDK, no crash-reporting SDK unless explicitly approved (open
  decision Q4). Every dependency must pass the rule: *no user data leaves the device except to
  basetool infrastructure* — the third-party inventory in §7 is the gate.
- **Layouts**: `WindowSizeClass`-driven. Compact width (phone portrait) = single column + bottom
  navigation (5 top destinations). Expanded+ width (tablet landscape) = navigation rail +
  list-detail canonical layout (`NavigableListDetailPaneScaffold`). Phones may lock portrait
  (allowed < sw600dp); tablets are adaptive by platform mandate.
- **i18n**: DE + EN resource bundles; per-app language via the AppCompat backport
  (`setApplicationLocales`, works to API 21). Terminology mined from the existing frontend
  bundles (~3 356 keys) for parity — Staffel, Spezialkommando, Auftrag, Lager stay German in EN.
  Binding copy rules from the design handoff: „**Einsätze**" (never „Missionen") in all
  user-visible copy, „**Bereich Profit**" as org context, „**Administration**" (never
  „Führung"); UPPERCASE labels, no emoji; error states keep the English in-fiction canon.
  (Module/endpoint names like `feature:missions` are code, not copy — they stay.)
- **API contract handling**: honor `Deprecation`/`Sunset`/`Link` response headers; UTC
  everywhere, display-local via the device zone; send `X-User-Time-Zone` on report/PDF endpoints;
  page-walk complete catalogs until `totalElements` (ADR-0102/0103); echo every DTO `version`
  field and map HTTP 409 `OPTIMISTIC_LOCK` to a reload-and-retry UX; Mission edits use the
  per-section PATCH + section version counters, never the coarse PUT.

### Design specification — delivered 2026-08-17

The Claude Design handoff is in-repo at [`docs/design/android/`](design/android/README.md)
and is the **binding UI reference** (high-fidelity; 1 mockup px = 1 dp): chapters 00–14
(foundations, component sheet, navigation, auth, all feature screens, system states +
adaptive icon), `artifacts/Theme.kt` (drop-in M3 mapping: `secondaryContainer = #E77E23` /
`onSecondaryContainer = #000` for the selection rule, `surfaceTint = surface` + tonal
elevation 0 for flat surfaces, `error`/`errorContainer` split for the text-tint rule),
`artifacts/icon-export.md` (42-symbol product sprite + 20 mobile extensions →
VectorDrawables), the Lato fonts and DS CSS mirrored under `_ds/`, and the Fan Kit artwork.
The `.dc.html` chapters are browser-viewed references, never shipped code. System-wide
behavior it fixes (extract): `NavigationSuiteScaffold` with a SQUARE 56×32 dp orange
indicator, per-destination back stacks, deep links per ch. 03; 200 ms color/fade motion
only; KRT modal/sheet/toast overlays — never native dialogs; offline = cached banner +
disabled writes (0.45 opacity), never queued mutations; 409 conflict dialog preserving
input; 429/503 backoff 3/6/12/30 s honoring `Retry-After`; approval-pending polls 60 s;
Custom-Tab-only auth; screen state survives process death via `SavedStateHandle`.
`ANDROID_APP_DESIGN_PROMPT.md` is thereby historical.

### The "external contract" carve-out (must be settled in Phase 0)

`docs/specs/api-conventions.md` allows endpoints consumed *only* by the in-repo frontend to change
shape in place (atomic deploy). A shipped app breaks that assumption. Phase 0 therefore includes a
spec amendment (REQ-API) that designates the endpoint set the app consumes as **external
contract** — shape changes to those endpoints then require `/api/v2` + `@ApiDeprecation`, and the
app pins minimum-server-version handling (a `GET /api/v2/system/ping`-based capability check at
startup with a friendly "server too old/new" screen).

## 5. Feature mapping (from the verified endpoint inventory)

| Area | App surface (member view) | Backing endpoints (exist today) |
|---|---|---|
| Dashboard | next missions, unread count, org context, announcements | `/missions/next`, `/notifications/unread-count`, `/announcement`, `/me/*` |
| Missionen | list/search/detail (sections: overview, participants, units/crew, steps, objectives, frequencies, finance), signup, check-in/out, payout preference, finance entries | `/missions/**` incl. `/slim` mutation variants, `/finance-entries` |
| Operationen | list/search/detail, per-mission finances, finance summary, **payout status** (member daily-driver: "was I paid out?"); edit/payout toggles for MM+ | `/operations/**` |
| Benachrichtigungen | inbox (paged), mark read, delete, unread badge, live via SSE (the rule engine behind delivery is admin-only server config — no app surface) | `/notifications/**` |
| Hangar | my ships CRUD, squadron overview, imports (JSON paste/file) | `/hangar/**` |
| Lager/Inventar | aggregated + my inventory, book in/out, rebook, allocations | `/inventory/**` |
| Mein Inventar / Blueprints | personal inventory CRUD, blueprints + craftability | `/personal-inventory`, `/personal-blueprints/**` |
| Aufträge | queue (org-public SK / private Staffel), detail incl. items/material demand, status/priority (Logistician), assignees + notes, claims (Logistician) | `/orders/**` |
| Materialbörse | offers + requests, interest toggles | `/material-exchange/**`, `/material-requests/**` |
| Raffinerie | my orders, detail, store yield | `/refinery-orders/**` |
| Bank (member surface) | org-unit balances, account detail, booking requests, approvals | `/org-units/bank/**` |
| Beförderung | my evaluations + eligibility | `/promotion/**` |
| Profil/Einstellungen | language, active org unit, payout pref, blueprint sharing, app-lock, Impressum/Datenschutz/licenses, logout | `/users/me/**`, `/me/**`, `/terms/*` |
| Onboarding states | login (Custom Tab), `PENDING_APPROVAL` screen, terms-acceptance screen (in-app via `/api/v1/terms/*`), guest mode (optional) | — |

Anonymous/guest surface (browse redacted missions, guest signup, master data) exists server-side
and was proposed as a **post-MVP** app mode; Q8 dropped it. The anonymous *order create* is on its way out of that surface
entirely: the main repo is removing it (ADR-0149, owner decision 2026-08-28) because the app's own
create needed the verb through the API vhost, which would otherwise have meant admitting an
anonymous write there. Once it lands, the backend agrees with the stance Q8 already took for the
app.

## 6. Phased roadmap

**Phase 0 — approvals & server-side groundwork** (this repo; no app code yet)
Decisions Q1–Q7 resolved · ADRs written (API exposure, mobile auth, external-contract set) ·
Keycloak client `basetool-android` on the test stack; the DPoP **Client Policy created** (the
realm has none today) and refresh-binding behavior verified (security doc §4) · Keycloak realm
hardening: user event logging + alerts, realm-wide S256 policy for public clients,
`fullScopeAllowed` cleanup, `sslRequired: external`, versioned token-endpoint edge budget
(security doc §2.11) · NPM vhost as **default-deny allowlist** + compose network (security doc
§2.8) · backend **XFF right-to-left chain walk** + narrowed trusted proxies + forwarded-header
overwrite at the edge (security doc §2.3) · backend Actuator moved to a dedicated management
port (ADR-0090 pattern) + edge deny (security doc §2.2) · audience enforcement flipped as a
**release gate** of the exposure PRs (security doc §2.4) · per-`sub` quotas behind auth +
dedicated budgets for the app's endpoint families and SSE connects (security doc §2.9) ·
`no-store` on sensitive GET families (security doc §2.12) · min-app-version gate +
`User-Agent` convention, CAA record, edge body-size caps (security doc §2.13) ·
**anonymous-write and guest paths stay off the allowlist until guest mode ships** (security doc
§2.8) · **`/.well-known/assetlinks.json` served on `profit-base.online`** (App-Links
prerequisite; small frontend/NPM change with its own probe; rotation-coupled per security doc
§2.10) · REQ-SEC / REQ-API / REQ-OBS spec amendments incl. the two stale-doc fixes (security doc
§2.14) · full monitoring package for the new public surface (blackbox liveness + edge-deny +
force-SSL + HSTS + IPv6 twins + DNS, auth-failure + per-`azp` client counters, alert rules
staged per REQ-OBS-014, dashboards, Alloy pipeline, privacy-policy extension for the new vhost's
access log and — once enabled — the Keycloak event store) · app repo scaffolded (public,
hardened per DEV_CI doc incl. Gradle dependency verification).

**Phase 1 — walking skeleton (app)**
Follows the design spec's implementation order: (1) theme + tokens from
`docs/design/android/artifacts/Theme.kt`, icon VectorDrawables per `icon-export.md`, bundled
Lato fonts (+ OFL text); (2) the chapter-02 component library (buttons ladder, chips/pills,
rows, forms, modal/sheet/toast, empty/loading/offline states, **Fan Kit band** with the
per-locale byte-exact string test); (3) navigation shell per ch. 03 for both form factors;
(4) auth flow per ch. 04 end-to-end (login via Custom Tab, token store, refresh loop,
logout+revocation, PENDING/terms/409/429/503 problem handling, app-lock) · **settings
(language, Impressum, Datenschutz, licenses — ch. 13) — done, `docs/specs/settings.md`** ·
DE/EN bundles · CI green incl. release-signing dry run — **done, `release-dry-run.yml`**.

**Phase 2 — read-only member core — done (2026-08-22)**
Dashboard · missions list/detail · operations list/detail incl. payout status · notifications inbox
+ SSE + unread badge · hangar (my ships) · inventory reads · orders queue/detail reads · org-bank
balances/detail reads · org-unit switcher. Each area has a spec under
[`docs/specs/`](specs/INDEX.md) with its own "known gaps" list naming what Phase 3 owns.

Three **approved deviations** from the design were recorded rather than left silent, all of the same
kind — an aggregate the API does not serve, which a client could only fake by adding up a page:
the thin Operationen list row (owner decision, 2026-08-22), no income/expense split on an
Operation's roll-up, and no three-number band on the Hangar's org tab.

Server-side, the reads are frozen in the main repo's `REQ-API-009` contract set and on the API
vhost's allow-list; the one manual step that opens them in production is that repo's runbook
Phase H.

**Walked on a device** (Pixel 10a emulator, German, against the isolated test stack) rather than
declared done from a green suite — every screen, both list and detail. It found **eight** defects
the 798-test JVM suite could not: the app crashed at launch on any notification (a regular
expression the JVM accepts and Android's ICU engine rejects), the session died silently after one
access-token lifespan, pull-to-refresh did nothing on an empty screen, a running Einsatz was hidden
by the "Vergangene aus" filter, an Operation's roll-up showed a donating member's zero as the share
per participant, a missing quantity rendered as a gap, the Übersicht claimed "Nichts Ungelesenes"
before the inbox had answered, and the Einsätze tab carried a hard-coded badge of 2. Each is fixed,
pinned by a regression test, and written into the area's spec.

**Phase 3 — mutations** (version-echo + 409 UX everywhere) — **complete, 2026-08-23**
Mission signup/check-in/payout-preference/finance entries · hangar CRUD · inventory book-in/out ·
order assignee/note/status (role-gated) · bank account settings · personal inventory/blueprints.

**Two things this line promised and the phase did not ship, each for the same reason — the server
would have refused them:**

- **Inventory *rebook*.** Turning private stock into shared stock exists as an endpoint, and the
  Lager reads exclude private stock in both directions (`i.personal = false` in the grouped and the
  entry query alike). So no entry that could be rebooked ever reaches the app's screen, and material
  booked in as private would land where nothing in the app can show it again. Both the mode and the
  "Persönlich" switch were built, walked on a device and then removed; `personal-rebook` stays in
  the contract set for the `my-inventory` slice that would make it reachable
  ([`docs/specs/inventory.md`](specs/inventory.md), REQ-APP-INV-008).
- **Bank *booking requests + owner approvals*.** Every booking path — deposits, withdrawals,
  transfers, the request queue — is `hasRole(BANK_EMPLOYEE)`: that is the bank-employee surface, and
  REQ-APP-BANK-001 keeps the app on the member-facing one. What a member actually owns on the bank
  is their **account's settings**, and that is what slice 7 shipped
  ([`docs/specs/bank.md`](specs/bank.md), REQ-APP-BANK-006/007).

**Ordered by ascending risk** (owner decision, 2026-08-23), so the write plumbing — request verbs,
version echo, conflict dialog, offline rule — is built where a mistake reaches nobody but the member
making it: **1.** Mein Inventar · **2.** Blueprints · **3.** Hangar-CRUD · **4.** Lager · **5.**
Aufträge · **6.** Einsatz · **7.** Bank. The **imports** (Fleetview, P4K, blueprint files) move to
phase 4 with the other file flows, and each area ships as its own PR with tests, spec and a device
walk-through.

Two decisions the whole phase rests on. `ACCESS_NETWORK_STATE` joins the permission inventory, so a
write action can be **disabled** while the device is offline rather than queued — a held mutation
carries a `version` that ages while it waits, which is precisely the write the server must refuse.
And the production vhost opens the write paths in **one paste at the end of the phase** (runbook
Phase I), not once per slice; the nightly probe is extended in that same change, so it never reports
a state nobody intends to fix yet. Both landed with the phase; the paste itself is the owner's, and
until it is applied the app reaches its own write paths only from inside the network.

**What the phase cost in defects, all found by walking a device and none by a test:** an entry read
keyed on the wrong quality *and* sent in the wrong type, an omitted pool id the server reads as a
different question, a sale that could never list a terminal, a transfer the server refuses because
it changes nothing, a check-in offered before the Einsatz had started, an editor opening on a number
its own field could not hold, two different minus signs one line apart, and — the one that affects
every screen — a bottom sheet whose action row sat inside the system gesture bar. Each is written
down as the requirement it produced.

**Phase 4 — live parity & breadth** (still pre-release per Q6; guest mode dropped per Q8)
Backend live-sync bridge — **shipped as SSE, both directions** (owner decision, 2026-08-23;
main-repo ADR-0143 / REQ-FE-019, app `docs/specs/sync.md`) with ADR-0094's coalescing unchanged
(400 ms detail / 1500 ms global, full-jittered) · Materialbörse · Raffinerie · Beförderung ·
system states per design-spec ch. 14. No push channel (decided Q2).

Three decisions the phase rests on, all taken 2026-08-23:

- **The bridge carries both directions, not just receiving.** An app that mutated shared state
  without emitting a signal would not merely fail to receive live sync — it would *break* it for
  every browser, on surfaces where it worked. The app therefore announces its own writes the way a
  tab does, onto the frontend's own Redis channel with the frontend's own payload.
- **No file imports in phase 4** — not Fleetview, not blueprint files, and **not the Raffinerie's
  extractor JSON either**, although design chapter 11 puts a scan icon on that screen. The
  Raffinerie ships without it as a recorded deviation; all three move to phase 5 with the file
  picker and the permission and privacy work they share.
- **System states ships all three of its open items**: notification channels plus a system
  notification while the app runs (a partial benefit without a push channel, and the only one
  available without FCM), the 429/503 full-screen retry countdown, and the forced-update gate —
  which needs a *new backend contract*, since nothing today lets the server state a minimum app
  version.

**Adaptive icon and the 409/offline rules were already done** (#23, phase 1–3), so chapter 14's
remaining scope is exactly the three items above.

**Editor presence stays web-only, permanently.** It is the one part of the web socket that carries
cross-user identity data, and the app has no place to show it.

**Where the phase stands (2026-08-24).** The bridge shipped in both repos. Of the four slices that
followed it, three are done and one is deliberately withheld:

- **Materialboerse (#64)** — shipped. Both halves, the pledge toggle, Zurueckziehen and both create
  sheets. Item *creates* are not in it: they address a P4K `productKey` the app has no picker for.
- **Raffinerie (#65)** — shipped, without the extractor import as decided. Its two recorded
  deviations from chapter 11 are in `docs/specs/refinery.md`.
- **System states (#67)** — shipped. The notification channels' blocker resolved itself: the SSE
  fault (krt-profit/basetool#1653) turned out to be a `ShallowEtagHeaderFilter` registered on `/*`,
  buffering every stream and never writing the buffer back. **The notification push had been dead
  the whole time** and nothing said so. Fixed, and guarded by a test that reads a socket, because no
  server-side metric can tell a delivering stream from a silent one. The forced-update gate's new
  backend contract shipped as REQ-API-010.
- **Befoerderung (#66)** — **built and withheld**, and the phase closes without it. The screen has
  no chapter in the design handoff and a derived layout is not a followed one (owner decision,
  2026-08-23, reaffirmed 2026-08-24). Everything behind it stays wired: the repository, the tests,
  the contract freeze and the allow-list lines. The issue stays open until a chapter exists.

**Web-parity gaps still open (audited 2026-08-28, artboard beside screenshot).** Recorded here so a
new session finds them without re-deriving the audit; the per-chapter detail is in
[`DESIGN_PARITY_AUDIT.md`](DESIGN_PARITY_AUDIT.md).

| Gap | Where | Why it is not done |
| :-- | :-- | :-- |
| **Item orders** | Aufträge create | Needs a game-item picker, a blueprint picker *per item* and the derivation tree the web renders as nested lines (`/orders/item-catalog/{id}/blueprints`, `…/blueprints/{id}/derivation`). A screen of its own; asked for in design round 8 §1.3. |
| **Lager: the FAB glyph** | Lager | The artboard draws a download glyph (⤓); the app's is „+" labelled „Einbuchen". The drawn glyph reads as Ausbuchen. |
| **Lager: the holder subtotal level** | Lager | Artboard 1 is material → holder (with a subtotal) → entry; the app merges holder and location into one row, so a member holding one material in two places gets no subtotal. |
| **Lager + Materialbörse on a tablet** | both | The only two screens with no wide-window treatment: the phone layout stretched to 1200 dp. Needs a design ruling first — round 8 §5 offers three options. |

Two things that look like gaps and are not, so they are not re-opened: the Lager's **material and
location filter chips** (neither the web nor `/inventory/aggregated` has them — round 7 §2g), and a
**squadron filter on the Aufträge queue** (the app's equivalent is the active-org-unit pin, which
the interceptor sends as a header on every call).

**Verification coverage:** the 2026-08-28 pass ran on the phone and, from the Navigation chapter
on, the **tablet** class (`KrtTablet`, 1280×800 dp) — which immediately found a list-detail defect
the phone could not show (`REQ-APP-UI-009`). `Pixel_5_minSdk` still has to see it.

**The vhost paste is the one thing left, and it is the owner's** (runbook Phase J). Until it is
applied the nightly `edge-deny-probe` reports the phase-3 and phase-4 paths as `404`, which is
production being read correctly rather than a defect — see the runbook's note before investigating
that run again.

**Phase 5 — hardening & first release**
Certificate pinning rollout (**CA-pin evaluated first**, else leaf backup-pin + expiration;
documented rotation runbook — security doc §5) · MASVS-based security review + red-team pass per
security doc §7 (incl. spoofed-XFF attribution, page-size amplification, assetlinks/key-rotation
drill) · release provenance: artifact attestations + published APK SHA-256 + signing-cert
fingerprint in the README (DEV_CI doc) · Datenschutzerklärung finalized in-app · beta (internal
testers) → **first public release via GitHub Releases + Obtainium** (Q1). Play Integrity/store
work only if a Play channel is ever added (Q3).

**The German wiki page for the app ships here, not earlier** (owner decision, 2026-08-23). The
`basetool.wiki` handbook is written for members, and until there is an APK to install it would
describe something that does not exist for them. The page belongs with the release: how to install
it through Obtainium, what the app does, and what stays in the browser.

**Where phase 5 stands (2026-08-24).** Everything that can be done without a production key or
production access is done; what is left is in [`OWNER_RUNBOOK.md`](OWNER_RUNBOOK.md), one step per
section.

- **Certificate pinning — shipped.** The § 5 evaluation came out for the **CA pin**: all three
  production hosts are pinned to both Let's Encrypt roots, with expirations, and the rotation
  runbook is § 5.1 of the security doc. The leaf+backup variant was rejected on the property that
  actually decides it here — it cannot brick the app. The pins were computed from certificates in a
  trust store, never transcribed, and a test asserts both roots on all three hosts as real `<pin>`
  elements.
- **MASVS review — performed and recorded** (security doc § 7.1). Four low findings, all fixed:
  server prose reaching logcat, a wire string becoming an implicit intent, a login any co-installed
  app could end, and a backup guard three comments claimed existed. `allowBackup` went off outright.
- **Release provenance — shipped.** `release.yml` builds, signs, verifies the certificate against
  the configured key, attests the build, exports the dependency SBOM and creates a **draft**
  release carrying the APK's SHA-256 and the signing certificate's. The README documents both
  checks and the `gh attestation verify` command.
- **Datenschutzerklärung — finalised.** The app has its own section in the policy the app itself
  links to (main repo, `privacy.h2_3_9`), covering what it stores, that both files are excluded
  from backup and device transfer, and the absences: no analytics, no ads, no tracking, no
  automatic crash reporting, no push service.
- **The wiki page — drafted** at `docs/wiki/App.md`, ready to commit into `basetool.wiki` with the
  release.

**Still outstanding, and all of it the owner's:** the vhost paste, the signing key, the `release`
environment, the first tag. The four drills § 7 lists — red-team against the exposure package,
pin rotation, assetlinks/key rotation, kill-switch — need the production key and the paste; the
min-version half of the kill switch is built and device-verified.

Each phase lands with the binding repo obligations of §8. Phases 2–4 slice vertically (a feature
ships UI + repository + tests + i18n together), so the cut lines can shift after Q6.

## 7. Third-party inventory (privacy gate)

Rule: **anything that stores/sends user data outside the device and basetool needs explicit
prior approval by @greluc.** Baseline set (all local-only, none phones home with user data):

| Dependency | Purpose | Data leaves device? |
|---|---|---|
| AndroidX / Jetpack (Compose, Room, DataStore, Paging, Navigation, Lifecycle, Biometric, AppCompat, Core-SplashScreen) | UI/persistence/lifecycle | No | DataStore **adopted 2026-08-18** (`core:auth`, encrypted refresh token); AppCompat **adopted 2026-08-20** (per-app language, ADR-0007 — it already arrived transitively with Biometric, so this pins the version rather than adding a library) |
| OkHttp 5 + Retrofit + kotlinx.serialization | HTTP/SSE to basetool only | Only to basetool |
| AppAuth-Android (or kotlin-multiplatform-oidc) | OIDC flow against our Keycloak | Only to basetool Keycloak |
| Hilt, KSP | compile-time DI | No |
| Coil | images from basetool hosts | Only to basetool |
| Nimbus JOSE (or equivalent) | DPoP proof JWTs, local signing | No | **adopted 2026-08-18** (`core:auth`) |
| Lato font files (OFL 1.1, bundled + license text) | typography | No |

Build-time only, no runtime data flow and nothing shipped in the APK but their own output:
the Gradle plugins (AGP, Compose compiler, detekt, Spotless/ktlint) and **Licensee**
(**adopted 2026-08-20**), which resolves the dependency graph to generate the open-source notice
and fails the build on a licence that is not explicitly allowed.

**Requires explicit approval before adoption** (not in the baseline): Firebase Cloud Messaging,
Play Integrity API, any crash-reporting backend, any analytics of any kind, Google Play Billing
(n/a), embedded FCM UnifiedPush fallback. Open decisions Q2–Q4 cover exactly these. The Google
Maven repository serving AndroidX artifacts is a build-time source, not a runtime data flow.

## 8. Binding repo obligations (checklist per implementation PR)

Per root `CLAUDE.md`, every server-side change here ships in the same PR with: REQ updates
(`docs/specs/` — new `REQ-MOB-*` spec proposed, plus touched REQ-SEC/REQ-API/REQ-OBS) · ADRs ·
monitoring sync (metrics, alerts + promtool tests, dashboards, blackbox targets, Alloy, tracing) ·
README + `ROLES_AND_PERMISSIONS.md` (roles unchanged, but the client-software section moves) ·
German wiki page (new: *Android-App*) · CHANGELOG · audit logging untouched areas stay in sync ·
i18n DE/EN. The app repo mirrors the discipline: own CHANGELOG, spec-as-docs, English-only
Git/GitHub prose, DCO + `Co-Authored-By` trailers, Javadoc/KDoc gate.

## 9. Decisions (resolved by @greluc, 2026-08-17)

| # | Decision | **Decided** |
|---|---|---|
| Q1 | Distribution channel | **GitHub Releases APK (+ Obtainium)**; no Play for now — zero Google involvement |
| Q2 | Push notifications | **No push in the MVP** (SSE while foregrounded + unread badge); any later push channel is a new decision |
| Q3 | Play Integrity (data → Google) | **Only if Play distribution ever comes** — with Q1 = GitHub Releases it stays OFF; hardware Key Attestation remains the Phase-5 Google-lean option |
| Q4 | Crash reporting | **Local only** — on-device log/crash buffer, user-initiated export; no automatic reporting backend |
| Q5 | Repo layout | **New public repo `basetool-android`**; server-side changes stay in this repo |
| Q6 | MVP scope | **Full member feature set before first release** — Phases 2–4 (incl. Materialbörse, Raffinerie, Beförderung) are all pre-release; first public release ships after Phase 5 hardening |
| Q8 | Guest mode | **Dropped** (owner decision, 2026-08-18). Every user signs in; the app has no anonymous surface. Consequences: no "Als Gast fortfahren" entry on the login screen (design spec ch. 04), no guest signup or anonymous order create in Phase 4, and — server-side — the API vhost never has to open the anonymous read/write paths its allowlist stance held in reserve (security concept §2.8). The terms gate now covers every user of the app, since there is no one who reaches content without passing it. |
| Q7 | Admin area in app | **Web-only permanently** |

Consequences folded into this plan: the roadmap keeps its phase structure, but the **first
release gate moves behind Phase 4 + Phase 5** (Q6); the Phase-4 "push channel" item is dropped
(Q2); release signing uses a **self-managed key** (offline-generated, offline backup, APK
Signature Scheme v3.1 rotation lineage from day one — unrecoverable if lost, see DEV_CI doc)
because there is no Play App Signing without Play (Q1); the guest mode ships with the MVP, so
the anonymous-path block at the API vhost (security doc §2.8) lifts at release, not later.

**Secondary decisions** (lower stakes; default applies unless overridden, listed here so nothing
hides in the sub-documents): FLAG_SECURE user-relax toggle (default: strict, no toggle) ·
SQLCipher for the read cache (default: no — platform FBE + backup exclusion suffices) · Play
account-deletion URL (default: provide the web deletion-request URL if Play is chosen) · Android
Lint baseline file (default: forbidden) · DPoP fallback rung if the Phase-0 verification fails
(needs explicit owner approval, security doc §4) · CycloneDX-vs-AGP compatibility fallback
(default: GitHub dependency-graph SBOM export) · KDoc bar in the app repo (default: every public
API of `core:*` modules — weaker than this repo's every-member Javadoc rule; owner nod requested).

Also pending @greluc sign-off: REQ-SEC-027 ("approved client software") — the app must be added to
the approved-clients list as part of Phase 0, and the Terms of Use extended accordingly.
