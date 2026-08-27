# CLAUDE.md

## Project

**Basetool Android** — the native Android companion app of the Profit Basetool, the
squadron-management web app of the "DAS KARTELL" / IRIDIUM organization. Kotlin (K2) +
Jetpack Compose (Material 3 + material3-adaptive), minSdk 30 (Android 11), targetSdk 37,
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
[`docs/ANDROID_APP_DEV_CI.md`](docs/ANDROID_APP_DEV_CI.md).
The **binding UI specification is the delivered design handoff at
[`docs/design/android/`](docs/design/android/README.md)** (chapters 00–14 + `artifacts/Theme.kt`;
see the UI section below). [`docs/ANDROID_APP_DESIGN_PROMPT.md`](docs/ANDROID_APP_DESIGN_PROMPT.md)
is the historical brief that produced it — do not design against the prompt anymore.

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
- **The app wears the Basetool mark, not the DAS KARTELL org mark.** The design system ships a
  dedicated logo family (`assets/basetool-*`); the adaptive launcher icon and the tablet
  navigation rail draw from it (`app/.../ic_launcher_foreground.xml`, `krt_basetool_logo.xml`).
  Design chapter 14 deferred the final geometry to exactly this family, so this is the spec being
  fulfilled, not a deviation. The org mark stays available in `core:designsystem` for surfaces
  where the organisation — not the tool — is the subject.
- **The delivered design specification at [`docs/design/android/`](docs/design/android/README.md)
  is the binding UI reference** (high-fidelity: colors, type, spacing, states, and copy are
  final; 1 mockup px = 1 dp). Deviations need an ADR. Read its README before any UI work.
  The `.dc.html` chapter files are **design references to open in a browser — never
  production code to port or ship**. Precedence: DAS KARTELL design system (mirrored in
  `docs/design/android/_ds/`) > this spec > web-app behavioral parity. Token source of
  truth for the Compose theme: `docs/design/android/artifacts/Theme.kt`; icon export
  contract: `docs/design/android/artifacts/icon-export.md`.
- **Copy rules (binding, from the product owner):** German-first, military-terse, UPPERCASE
  labels, no emoji. Fixed terms: „**Einsätze**" (never „Missionen") in all user-visible copy,
  „**Bereich Profit**" as org context, „**Administration**" (never „Führung"). Error states
  keep the English in-fiction canon (403 "Access Denied — …", 404 "Signal Lost — …",
  500 "System Malfunction…", CTA „Zurück zur Basis"). Reuse the web app's
  `messages*.properties` keys where they exist.

## Star Citizen Fan Kit compliance (binding)

The app is a Star Citizen fan project and uses Fan Kit assets, so **two CIG documents bind it
and they apply cumulatively** — the Fan Kit **Guidelines** (sections 2, 2b, 3) and the Fankit
**Agreement** (clause 2 g) — exactly as they bind the web app (REQ-UI-018 there). Asset and
detailed rules, plus the checked kit version: [`core/designsystem/fankit/`](core/designsystem/fankit/README.md).

- The **logo and BOTH notices are ONE coupled unit** — a single composable
  (`KrtFanKitBand`); none of the three may render, move, or be removed alone. The Guidelines'
  §2b trademark line and the Agreement's clause-2(g) notice are separate requirements and
  neither substitutes for the other.
- **Never harmonise the two notices.** The §2b line has a space before its third ®; clause 2(g)
  has none before any of its four, writes `Ltd..` with two full stops and takes an Oxford comma
  before "and Cloud Imperium®". Both are quoted, not written, and the tests pin the difference.
- The 2(g) paragraph is **never folded behind a disclosure**, and the band keeps **one type
  size** throughout (14 sp).
- Both notices are **prescribed legal wording, byte-exact, verbatim English in every locale**
  (`translatable="false"`). §2b: `Star Citizen®, Roberts Space Industries® and Cloud Imperium ®
  are registered trademarks of Cloud Imperium Rights LLC`. Clause 2(g): `This site is not
  endorsed by or affiliated with the Cloud Imperium or Roberts Space Industries group of
  companies. All game content and materials are copyright Cloud Imperium Rights LLC and Cloud
  Imperium Rights Ltd.. Star Citizen®, Squadron 42®, Roberts Space Industries®, and Cloud
  Imperium® are registered trademarks of Cloud Imperium Rights LLC. All rights reserved.`
  Never tidy either up, never translate them — a "fixed" string breaks compliance while
  passing every key-parity check.
- **Placement (fixed by the design spec, ch. 02 §9): Login (above the version footer) and
  Einstellungen — nowhere else.** The login screen is the mandatory home-page-analog
  placement (section 2b, visible without a login); Einstellungen is the second fixed
  placement. Do not add the band to further screens; a legal subpage alone would not be a
  sanctioned surface.
- **Artwork unmodified** (section 3): white variant, no recolor/tint/flip/distortion/
  outline/shadow/effect; notice ≥ 14 sp in `#D2D2D2`-grade contrast.
- UI tests pin logo + both notices + the byte-exact strings per locale and the spacing
  difference (`KrtFanKitBandTest`, `FanKitNoticeParityTest` — mirrors of the web app's
  `FanKitComplianceMvcTest`); they may never be split into independently disableable halves.

## Build, run, test

Always use the Gradle wrapper; it is the only sanctioned build/test path (never the IDE test
runner).

```bash
./gradlew check                                  # tests + Android Lint + detekt + Spotless verify
./gradlew spotlessApply                          # format — run before every push
./gradlew :app:assembleDevDebug                  # dev flavor (test-stack endpoints, debug trust)
./gradlew :app:assembleProdRelease               # prod flavor (real hosts; signing in CI only)
./gradlew :core:designsystem:testDebugUnitTest   # JVM tests incl. Robolectric
```

### Toolchain landmines (each cost a debugging round — do not "fix" them back)

- **AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android` fails the build
  outright; only `org.jetbrains.kotlin.plugin.compose` is applied per module, and `jvmTarget`
  is inherited from `android.compileOptions.targetCompatibility` rather than set by hand.
- **detekt must stay on 2.x.** detekt 1.23's embedded IntelliJ library cannot parse the JDK 25
  version string and dies with a bare `> 25`. The 2.0.0-alpha pin is deliberate, and its config
  schema differs from 1.x (no `build>maxIssues`, `TooManyFunctions>allowedFunctionsPer*`,
  `UnusedPrivateMember` split into `UnusedPrivateFunction`/`UnusedPrivateProperty`).
- **ktlint needs `.editorconfig`.** `ktlint_function_naming_ignore_when_annotated_with =
  Composable` keeps the standard naming rule from renaming every composable.
- **Kotlin warnings are errors** (`allWarningsAsErrors`), so a deprecated Compose API breaks the
  build instead of rotting quietly. Fix the call site; do not relax the flag.
- **Android Lint runs with `warningsAsErrors`.** Exactly one rule is disabled, with a written
  reason in the build file (design-system resources having no consumer yet). A second needs the
  same justification. `MissingApplicationIcon` was the other one and is enabled again since the
  adaptive launcher icon landed.
- Robolectric ships no runtime for API 37, so resource tests pin `@Config(sdk = [34])`. That
  runtime jar is a declared dependency (`robolectricAndroidAll` in the version catalog) and the
  tests run with `robolectric.offline=true`, so **bumping `robolectric` means bumping
  `robolectricAndroidAll` in the same commit** — otherwise every Robolectric class fails with
  `Unable to locate dependency`, and that message names the version to write. Why it is pinned at
  all: `docs/ANDROID_APP_DEV_CI.md` § 4.

Local backend = the main repo's isolated test stack — and for emulator work it needs **three**
compose files, not two:

```bash
docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml \
    -f docker-compose.android.yml --profile dev up -d
```

**Omitting `docker-compose.android.yml` is the single most expensive mistake in this repo's local
setup.** Without it Keycloak advertises `host.docker.internal`, which containers and the host
browser resolve and the emulator cannot — Play-store system images cannot be rooted to add a hosts
entry. The login then fails *after* the Keycloak form with `DNS_PROBE_FINISHED_NXDOMAIN`, which
reads as a broken realm rather than a topology mismatch. The override pins the issuer to
`127.0.0.1:18080`, which is exactly what the `dev` flavour's `OIDC_ISSUER` already expects, and
gives the backend a split-horizon JWKS URL so it can still validate those tokens. While it is in
effect the **web** frontend's own login does not work; it is an app-work override.

The device reaches the stack through `adb reverse` (`tcp:18080`, `tcp:11261`, `tcp:18081`), not
through `10.0.2.2` — a direct socket to that address was measured to time out on this machine even
though the browser loads the same URL. The tunnels do not survive an emulator restart. After a
`down --volumes`, `adb shell pm clear` the app as well: a refresh token from the previous stack
fails against the new realm, and `pm clear` also drops the per-app locale, so set German again.

The self-signed certificate is trusted **only** via Network Security Config `<debug-overrides>`.
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
  `-S -s` when GPG-signing, which is the norm on PR branches). **The signing identity is
  `Lucas Greuloch (greluc) <lucas.greuloch@gmail.com>` — that address, not any other, belongs
  in the trailer.** The owner's contact address (`lucas.greuloch@pm.me`) is a different address
  and is NOT the git identity; using it fails the DCO gate. Never hand-write the trailer:
  `-s` derives it from `git config user.name` / `user.email`, which is already correct —
  typing it out by hand is exactly how the wrong address gets in.
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

- The DAS KARTELL design-system submodule may still be added in Phase 1, but the raw
  sources the UI work needs (colors/type CSS, component CSS, Lato WOFF2/TTF) are already
  mirrored in-repo at `docs/design/android/_ds/`. **The mirror is refreshed by hand** — when the
  upstream `krt-profit/design-system` moves, copy the changed files across in the same PR and
  reconcile any token that has an Android counterpart (e.g. `--color-gray-2-text` ↔
  `KrtPalette.TextMuted`). Nothing in the build detects the drift.
- Manufacturer logos (Anvil/Drake/MISC …) exist upstream only as SVGs with embedded
  rasters — clean vectors must be re-exported; until then the design spec's lettermark
  placeholder IS the design (handoff README, Assets).
