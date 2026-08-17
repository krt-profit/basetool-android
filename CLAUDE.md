# CLAUDE.md

## Project

**Basetool Android** — the native Android companion app of the Profit Basetool, the
squadron-management web app of the "DAS KARTELL" / IRIDIUM organization. Kotlin (K2) +
Jetpack Compose (Material 3 + material3-adaptive), minSdk 29 (Android 10), targetSdk 37,
phones portrait-first, tablets landscape-first. The app consumes the **existing Basetool
backend REST API** (`/api/v1`, Bearer JWT against Keycloak) — it contains **no business logic
of its own** and never talks to PostgreSQL, Redis, or the Keycloak Admin API. All server-side
work (API exposure, Keycloak client, monitoring, specs) lives in the main `basetool` repo,
never here.

This repo is **public**. Treat every file as world-readable at all times: client id, API
hosts, and certificate pins are public by design; secrets of any kind never land here (see
Security below).

Binding concept documents (until superseded by `docs/specs/`): [`docs/ANDROID_APP_PLAN.md`](docs/ANDROID_APP_PLAN.md)
(master plan incl. the resolved decisions Q1–Q7), [`docs/ANDROID_APP_SECURITY.md`](docs/ANDROID_APP_SECURITY.md),
[`docs/ANDROID_APP_PRIVACY_GDPR.md`](docs/ANDROID_APP_PRIVACY_GDPR.md),
[`docs/ANDROID_APP_DEV_CI.md`](docs/ANDROID_APP_DEV_CI.md),
[`docs/ANDROID_APP_DESIGN_PROMPT.md`](docs/ANDROID_APP_DESIGN_PROMPT.md).

## Resolved project decisions (owner-approved 2026-08-17 — do not silently reopen)

- Distribution: **GitHub Releases APK (+ Obtainium)**. No Google Play, therefore no Play
  Integrity and no Play App Signing; release key is self-managed (offline backup, v3.1
  rotation lineage).
- **No push channel.** Live data = backend SSE while foregrounded + unread badge.
- **Crash reporting local-only** (on-device buffer, user-initiated export). No automatic
  reporting backend of any kind.
- New public repo (this one); MVP = **full member feature set** before the first release;
  the **admin area stays web-only permanently**.

## Requirements, specs & decisions (binding)

Same docs-as-code discipline as the main repo: durable requirements live in
[`docs/specs/`](docs/specs/INDEX.md) (`REQ-APP-<AREA>-NNN`, registry in its `INDEX.md`),
architecture decisions in [`docs/adr/`](docs/adr/README.md).

- **Every change updates the requirements in the same PR.** A behaviour change with no
  matching spec change is incomplete.
- **Every architecturally significant decision is recorded as an ADR** before or with the
  change that implements it.
- **README and CHANGELOG move with the change.** The German end-user wiki page for the app
  (in the `basetool.wiki` repo) moves with every user-visible change too.
- **Requirements must always be honoured.** If a change must violate one, it needs prior
  approval by the repository owner (@greluc) AND the requirement amended first. When in
  doubt, stop and ask.
- Changes that alter the API contract consumed from the backend (endpoints used, headers,
  error-code handling) must stay in sync with the main repo's external-contract spec — flag
  any mismatch instead of coding around it.

## Privacy gate (HARD RULE — this is the project's identity)

**No dependency, service, or code path may store, use, or send user data outside the device
and the Basetool infrastructure without explicit prior approval by @greluc.** The approved
baseline inventory lives in `docs/ANDROID_APP_PLAN.md` §7. Adding any dependency means:
re-check its data flows, extend the inventory table in the same PR, and re-run the
§ 25 TDDDG storage analysis in `docs/ANDROID_APP_PRIVACY_GDPR.md` §2 if it stores anything
on-device. Analytics, tracking, ad SDKs, and Firebase are **not** approvable-by-default —
they are design violations unless the owner decides otherwise. The app must remain
consent-banner-free; guard that property in review.

Permissions stay minimal: `INTERNET`, `USE_BIOMETRIC` (optional app-lock),
`DETECT_SCREEN_RECORDING` (API 35+); `ACCESS_LOCAL_NETWORK` in the `dev` flavor only.

## Frontend / UI & design system

- **The DAS KARTELL design system is binding** (dark-only, square-first, Lato, orange
  #E77E23 — full token set and component canon in `docs/ANDROID_APP_DESIGN_PROMPT.md`; the
  binding upstream contract is `docs/specs/ui-design-system.md` in the main repo). Dynamic
  color / Material You theming of brand hues is deliberately disabled. No emoji in UI, no
  native-styled dialogs, no icon libraries — the in-house stroke icon set only.
- All UI is Compose; layouts are `WindowSizeClass`-driven (compact width = phone portrait,
  expanded+ = tablet landscape with list-detail). Orientation may be locked only on
  < sw600dp screens; edge-to-edge and predictive back are on from the start.
- The design specification produced from the Claude Design prompt is the binding UI
  reference once it exists; deviations need an ADR.

## Star Citizen Fan Kit compliance (binding)

The app is a Star Citizen fan project and uses Fan Kit assets, so the Fan Kit Guidelines
(sections 2, 2b, 3) bind it exactly as they bind the web app (REQ-UI-018 there). Asset and
detailed rules: [`core/designsystem/fankit/`](core/designsystem/fankit/README.md).

- The **"Made By The Community" logo and the CIG trademark notice are ONE coupled unit** —
  a single composable; neither may render, move, or be removed alone.
- The notice is **prescribed legal wording, byte-exact, verbatim English in every locale**
  (`translatable="false"`): `Star Citizen®, Roberts Space Industries® and Cloud Imperium ®
  are registered trademarks of Cloud Imperium Rights LLC` — including the space before the
  third ®. Never tidy it up, never translate it — a "fixed" string breaks compliance while
  passing every key-parity check.
- **Placement:** the login/entry screen (the app's home-page analog under section 2b,
  visible without a login) carries the band; the settings "About" screen may repeat it as
  an addition, never as a substitute. A legal subpage alone is not a sanctioned surface.
- **Artwork unmodified** (section 3): white variant, no recolor/tint/flip/distortion/
  outline/shadow/effect; notice ≥ 14 sp in `#D2D2D2`-grade contrast.
- A UI test pins logo + notice + the byte-exact string per locale (mirror of the web app's
  `FanKitComplianceMvcTest`); it ships together with the Phase-1 login screen and may never
  be split into independently disableable halves.

## Build, run, test

Always use the Gradle wrapper; it is the only sanctioned build/test path (never the IDE test
runner). The Gradle scaffold lands in Phase 1 — until then this section is forward-looking:

```bash
./gradlew check                       # unit tests + Android Lint + detekt + Spotless verify
./gradlew :app:assembleDevDebug       # dev flavor (test-stack endpoints, debug trust)
./gradlew :app:assembleProdRelease    # prod flavor (real hosts; release signing in CI only)
./gradlew testDebugUnitTest           # JVM tests incl. Robolectric
./gradlew :app:gmdCheck               # Gradle Managed Devices instrumented suite
```

Local backend = the main repo's isolated test stack (`docker-compose.test.yml`,
`--env-file .env.test`). The emulator reaches it via `https://10.0.2.2:<port>`; its
self-signed certificate is trusted **only** via Network Security Config `<debug-overrides>`.
The `prod` flavor never trusts custom anchors and has no runtime-switchable endpoints.

## Linting / static analysis

Android Lint (`warningsAsErrors`; baseline files are forbidden without an owner decision),
**detekt** and **Spotless (ktlint)** run in `check`. **Every new or modified piece of code is
linted before the task is done, and all findings introduced by your change are fixed — never
silenced** with `@Suppress` unless the rule is genuinely wrong at that call site (then leave
a one-line comment saying why). Run `./gradlew spotlessApply` before **every** push; all lint
gates must be green before every push. Pre-existing findings you did not touch are out of
scope — but never add a new one on top.

## Concurrency & API contract (agent-critical)

- **Optimistic locking version echo:** every write DTO carries a `version` the client must
  echo; HTTP 409 `OPTIMISTIC_LOCK` → reload-and-retry UX, never a silent retry loop. After a
  successful mutation, propagate the response's new version into every local holder.
  `Mission` uses per-section version counters — PATCH the section you edited, never re-save
  the whole aggregate.
- RFC 7807 `problem+json` codes are first-class states: `UNAUTHENTICATED`,
  `PENDING_APPROVAL`, the terms-gate 403, `RATE_LIMIT_EXCEEDED` (429 + `Retry-After`),
  `SERVICE_UNAVAILABLE` (503, retryable), `OPTIMISTIC_LOCK` (409).
- Send `Authorization`, `X-Active-Org-Unit-Id` (org pin), `Accept-Language`, and
  `X-Correlation-Id` on every call; honor `Deprecation`/`Sunset` response headers; UTC on the
  wire, device zone for display; page-walk paginated catalogs until `totalElements`.
- DTOs are **generated from the committed backend `openapi.json`** — never hand-write a DTO
  the generator can produce; contract drift must fail compilation, not runtime.
- Tokens: access token in memory only; refresh token AES-256-GCM-encrypted via Android
  Keystore, ciphertext in DataStore, excluded from cloud backup **and** device-to-device
  transfer in all three rule sets. Never log tokens, names, or emails.

## i18n

- **Every** user-visible string comes from string resources — DE (primary) + EN, no
  hardcoded text in composables. Domain terms stay German in EN (Staffel, Spezialkommando,
  Auftrag, Lager, Raffinerie). Design for long German compounds.
- Android resource files are UTF-8: German umlauts are **literal characters** in
  `strings.xml` and Markdown. (The `\uXXXX` escaping rule of the main repo applies to Java
  `.properties` files only — none exist here.)

## Testing

- **Every new feature ships with tests.** No exceptions. Pyramid: JUnit/Turbine unit tests →
  Robolectric screen tests → MockWebServer contract tests against `openapi.json` fixtures →
  Gradle Managed Devices instrumented suite → screenshot tests for `core:designsystem`.
  Kover gates coverage on `core:*` (≥ 80 % line from Phase 2).
- **Never use production / real credentials in tests or local stacks.** Only the synthetic
  test-stack artifacts of the main repo (`.env.test`, stripped realm export, throwaway
  keystore). Anything that enters a public worktree, CI log, or screenshot must be assumed
  leaked.

## Kotlin conventions

- Kotlin 2.x (K2), coroutines + Flow, unidirectional data flow (ViewModel → Repository →
  API/cache). Constructor injection via **Hilt** only — no service locators, no globals.
- Immutable `data class`/`value class` for models; sealed interfaces for UI/domain states;
  no platform types leaking across module boundaries.
- **KDoc is mandatory on every public API of `core:*` modules** (classes, functions,
  properties) and on every `feature:*` screen entry point. It must describe actual behavior,
  parameters, error cases, and invariants — generic boilerplate ("Returns the value") is
  forbidden. If you cannot write a concrete sentence, read the implementation again.
- Logging via a single project logger facade; no `println`, no `Log.*` scattered in features.

## Documentation

- **Maintain `CHANGELOG.md`** for every user-visible change. Entries short, terse, one to
  three sentences — what changed and why it matters to the user. No design essays.
- README, the concept docs, and the German wiki page move with the change (see Requirements).

## Git

Identical rules to the main repo — the short form:

- No destructive Git commands without explicit user instruction (`reset --hard`,
  `push --force*`, `rebase` on shared branches, `clean -fd`, …). Read-only/additive is fine.
- Run `./gradlew spotlessApply` and the full lint gate before **every** push.
- **Every commit carries a DCO `Signed-off-by:` trailer** — always `git commit -s` (or
  `-S -s` when GPG-signing, which is the norm on PR branches).
- **Every commit Claude authors includes a `Co-Authored-By:` trailer naming the model**, e.g.
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` — transparency requirement,
  independent of the DCO sign-off. Substitute the actual model identifier of the session.
- **All Git/GitHub/in-code prose is English** — commit messages, branch names, PRs, issues,
  comments, KDoc — regardless of the conversation language. Sole carve-outs: verbatim quotes
  and the German `basetool.wiki`.
- **Every PR is assigned (`--assignee greluc`) and labelled** from the repo's existing label
  set (Conventional-Commit type → `enhancement`/`bug`/`documentation`, plus functional-area
  labels once created). Never invent labels inline.
- Branch protection on `main`, tag protection on `v*`, and the `release` environment are the
  repo governance baseline — do not bypass them.

## Security (public repo)

- **Nothing secret ever lands in the repo** — no keystores (`*.jks`, `*.keystore`), no
  `keystore.properties`, no `.env*` with real values, no tokens in code, tests, fixtures,
  logs, or screenshots. Release signing keys exist only as GitHub environment secrets
  (`release` environment: tag-restricted + required reviewer) and in the owner's offline
  backup.
- CI is hardened per `docs/ANDROID_APP_DEV_CI.md`: actions SHA-pinned, top-level
  `permissions: contents: read`, no `pull_request_target` on code, zizmor + actionlint +
  CodeQL + gitleaks + dependency review. Keep it that way when touching workflows.
- Server-side security work (vhost, rate limits, Keycloak config, monitoring) happens in the
  main `basetool` repo under its rules — including its **production-host approval gate**,
  which this repo inherits by reference: no prod access from work in this repo, ever.

## License

**GPL-3.0** ([`LICENSE.md`](LICENSE.md), owner decision 2026-08-17 — same as the main repo).
Every bundled third-party asset keeps its own license notice; in particular the Lato fonts
ship with the OFL 1.1 text next to them (missing upstream — add it when the fonts land).

## Open items (tracked, not yet decided)

- The DAS KARTELL design-system submodule may be added here in Phase 1 if UI work needs the
  raw assets; until then the design prompt doc carries the extracted tokens.
