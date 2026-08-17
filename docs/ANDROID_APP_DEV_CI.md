# Android App — Development & Test Environment, CI/CD (public repo)

Doc type: **living plan** (draft, pending approval by @greluc). Tooling facts verified live on
**2026-08-17**. Master plan: [`ANDROID_APP_PLAN.md`](ANDROID_APP_PLAN.md).

## 1. Repository

**Recommendation (open decision Q5): new public repo `basetool-android`** beside this one.
Rationale: independent release cadence and toolchain (AGP/Gradle vs the Spring stack), Dependabot
needs the standard `gradle/libs.versions.toml` location per ecosystem, public-repo hardening can
be maximal without touching this repo's settings, and the server-side work stays here where the
specs/monitoring live. Carry-over conventions from this repo: English-only Git/GitHub prose,
Conventional Commits, DCO sign-off (`-s`) + GPG (`-S`) + `Co-Authored-By` model trailer on
AI-authored commits, CHANGELOG discipline, docs-as-code (`docs/adr/`, `docs/specs/` with
`REQ-APP-*`), assignee + labels on every PR.

Public-repo ground rules: **nothing secret ever lands in the repo** — client id, API hosts,
certificate pins are public by design and documented as such; signing keys exist only as CI
environment secrets and in an offline backup; local secrets pattern: `local.properties` /
`*.local.env` gitignored; **repository-level push protection enabled explicitly** (user-level
push protection is on by default for public repos, the repo-level switch is not), secret scanning
on, plus gitleaks in CI for token classes GitHub doesn't cover (memory: a leak "fixed on top" still
trips range scans — history rewrite is the only cure, so prevention is the cheap path).

Repo governance (set before the first external PR can arrive): branch protection on `main`
(required status checks = the `ci.yml` gates, required review, no force-push, linear history);
**tag protection for `v*`** (only the owner can push release tags — the release workflow triggers
on them); `CODEOWNERS` = @greluc; a `SECURITY.md` with a private disclosure channel (GitHub
private vulnerability reporting enabled) and the supported-versions statement; issue/PR templates
mirroring this repo's conventions.

## 2. Local development environment

- **Android Studio** (latest stable) + JDK 17+ toolchain; the Gradle wrapper is the only
  sanctioned build/test path (`./gradlew …`), mirroring this repo's rule.
- **AVDs**: phone (Pixel-class, portrait, API 29 *and* API 37 images — the two behavioral
  extremes) and tablet (e.g. Pixel Tablet, landscape, API 37). Compose `@Preview` variants for
  compact/expanded window size classes cover most iteration without an emulator.
- **Backend**: the existing **isolated test stack** from this repo
  (`docker-compose.test.yml`, `--env-file .env.test`, throwaway credentials — the hard rule
  "never production credentials in tests or local stacks" applies unchanged; teardown with
  `down --volumes`). The emulator reaches the host via `https://10.0.2.2:<port>`; the stack's
  self-signed certificate is trusted **only** via Network Security Config
  `<debug-overrides>` (active only in debuggable builds — release builds structurally cannot
  trust it). A `dev` build flavor pins base URLs to the test stack; `prod` flavor pins the real
  hosts. No app-side code branches on URLs at runtime.
- **Keycloak**: the test realm gets the `basetool-android` client (S256, exact redirect URIs,
  DPoP toggle) so the full login/refresh/DPoP path runs locally — this is also where the Phase-0
  DPoP verification task happens.
- Optional: Gradle Managed Devices locally for the instrumented suite (same definition as CI).

## 3. Test strategy

| Layer | Tool | Scope |
|---|---|---|
| Unit / ViewModel / repository | JUnit 5 + kotlinx-coroutines-test + Turbine | logic, flows, error mapping (RFC 7807 codes incl. 409/429/`PENDING_APPROVAL`) |
| JVM UI + Android framework | **Robolectric 4.16.1** | fast screen-level tests on every PR |
| API contract | MockWebServer against the **committed `openapi.json`** fixtures; generated DTOs make drift a compile error | pagination page-walk, version echo, problem+json parsing, SSE framing |
| Instrumented / emulator | **Gradle Managed Devices** (`aosp-atd` API 30 for speed + one API 37 image), `…emulator.gpu=swiftshader_indirect` on CI | navigation, both window size classes; auth flow against a **Keycloak Testcontainer seeded with the stripped test realm** — committed Phase-1 deliverable; fallback if container startup proves too flaky on CI: MockWebServer replaying recorded OIDC exchanges, with the Testcontainer variant kept as a nightly job |
| Screenshot/visual | Compose Preview screenshot testing on the design-system module | KRT component regressions |
| E2E against a live stack | scripted flows vs the local test stack — manual per release candidate from Phase 2, promoted to a nightly workflow once Phase 3 mutations land | login→feature round trips |

Coverage: **Kover** on `core:*` and `feature:*` modules, reported in CI on every PR; threshold
starts advisory in Phase 1 and becomes a failing gate (line coverage ≥ 80 % on `core:*`) from
Phase 2 — mirroring this repo's JaCoCo culture without starting at an unmeetable bar.

## 4. GitHub Actions — hardened for a public repo

Baseline posture (all from GitHub's current security docs):

- Workflows trigger on `pull_request` (never `pull_request_target` for anything that builds PR
  code); fork PRs get no secrets and a read-only `GITHUB_TOKEN` by design; first-time-contributor
  runs require approval (default).
- Top-level `permissions: contents: read` in every workflow; scopes widened per job only.
- **Every third-party action pinned to a full commit SHA** (the only immutable reference —
  tj-actions/changed-files, CVE-2025-30066, is the case study; >23 000 repos hit by tag rewrite).
- **Gradle dependency verification** (`gradle/verification-metadata.xml`: checksums + PGP where
  published) gates every resolved build/plugin artifact — the Android-toolchain counterpart of
  SHA-pinned actions; the `--write-verification-metadata` refresh flow is documented in the
  contributor docs so a bumped dependency fails loudly instead of resolving silently.
- **zizmor** (v1.29.0) + **actionlint** (v1.7.12) lint the workflows in CI; zizmor's
  `cache-poisoning`, `template-injection`, `artipacked`, `unpinned-uses`, `excessive-permissions`
  audits are the checklist.
- Caches: `gradle/actions/setup-gradle` (v6.3.0) with default branch-scoped cache semantics
  (writes only on the default branch); **the release/signing job restores no caches at all**
  (poisoned-cache → poisoned-artifact vector).
- Dependency graph via the separate `gradle/actions/dependency-submission` workflow; for fork PRs
  the documented two-workflow pattern (`pull_request` generates, `workflow_run` submits).

### Pipelines

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | PR + push to main | assemble, unit + Robolectric tests, Android Lint (SARIF → code scanning), detekt (1.23.8), Spotless+ktlint check, zizmor/actionlint |
| `instrumented.yml` | PR label or nightly | GMD emulator suite on `ubuntu-latest` with the KVM udev step (KVM is available on standard GitHub-hosted Linux runners) |
| `codeql.yml` | PR + schedule | CodeQL **Kotlin (GA)** + the `actions` language pack for workflow scanning |
| `supply-chain.yml` | PR + schedule | dependency-review-action (v5, fails on vulns/licenses), OpenSSF Scorecard (schedule, `id-token: write`), gitleaks |
| `release.yml` | tag `v*` | build AAB/APK, SBOM, sign, attach to GitHub Release — **environment `release`** |
| `deps` | — | Dependabot (`gradle` ecosystem, standard catalog location) — or Renovate if grouped updates prove necessary |

### Release signing (no key leakage)

- Signing keys live **only** as environment secrets in a `release` environment protected by:
  required reviewer (@greluc), deployment restricted to `v*` tags. Fork PRs structurally cannot
  reach them (no secrets on fork runs + environment gating).
- Keystore transported as base64 secret → decoded to a runner-local file → shredded; documented
  GitHub-sanctioned pattern for small binary blobs.
- **Key strategy (decided Q1 = GitHub Releases, no Play)**: a self-managed signing key that is
  **unrecoverable if lost** — generated offline, offline backup kept by the owner, APK Signature
  Scheme v3.1 rotation lineage enabled from day one (`apksigner rotate`, rotated keys apply on
  Android 13+ by default; `--rotation-min-sdk-version` for older). Should a Play channel ever be
  added, Play App Signing with a resettable upload key comes on top; the release runbook then
  documents both paths.
- SBOM per release: CycloneDX Gradle plugin 3.4.1 (spec 1.6/1.7) — **verify AGP compatibility in
  Phase 1** (README is silent on Android); fallback: GitHub dependency-graph SBOM export. Matches
  this repo's `cyclonedxBom` habit.
- **Release provenance & user-side verification**: each release publishes build provenance
  (`actions/attest-build-provenance`) and the APK's SHA-256 next to the artifact; the README
  documents the release signing certificate's SHA-256 fingerprint (the same digest served in
  `assetlinks.json`), so Obtainium users can verify a download with
  `apksigner verify --print-certs`. The rotation runbook updates `assetlinks.json` with **both**
  the old and the new cert digest *before* a rotated APK ships (security doc §2.10).
- Reproducible builds: aspirational, not gated — AGP is close but apksigner ≥ 35.x verification
  quirks, baseline profiles and PNG crunching still break byte-identity (F-Droid docs). Pin exact
  SDK/build-tools versions and revisit if F-Droid distribution ever becomes a goal.

## 5. Quality gates (the app repo's `check`)

`./gradlew check` = unit tests + Android Lint (`warningsAsErrors`, baseline file forbidden except
by owner decision) + detekt + Spotless(ktlint) verify. All gates green before every push (the
lint-gate discipline of this repo carried over). KDoc on every public API of `core:*` modules
(the design-system module documents component contracts); CHANGELOG entry per user-visible
change; README + docs move with behavior changes — same "incomplete without docs" bar as here.

## 6. Environments & configuration

| Config | dev flavor | prod flavor |
|---|---|---|
| API base | `https://10.0.2.2:<test-port>` (or LAN host) | `https://api.profit-base.online` |
| Keycloak issuer | test-stack realm | `https://keycloak.profit-base.online/realms/iri` |
| Network security config | debug-overrides trust for test CA | system trust (+ pins in Phase 5) |
| Logging | verbose, local | warn+, local ring buffer only |

No runtime-switchable endpoints in release builds (an attacker-visible server switcher is an
unnecessary gift); QA against staging happens via the dev flavor.

## 7. Version sources (all fetched live 2026-08-17)

gradle/actions v6.3.0, android-emulator-runner v2.38.0, Robolectric 4.16.1, detekt 1.23.8
(2.0.0 in alpha — pinned to 1.23.x), ktlint 1.8.0, zizmor v1.29.0, actionlint v1.7.12,
gitleaks v8.30.1, CycloneDX Gradle plugin 3.4.1, dependency-review-action v5, Scorecard action
v2 — each from the project's GitHub releases API / official docs; CodeQL Kotlin GA status from
codeql.github.com supported-languages; KVM-on-hosted-runners from the GitHub changelog
(2024-04-02); cache/fork-PR/environment semantics from docs.github.com (Actions security
references); Play App Signing / apksigner rotation from developer.android.com; reproducible-
builds status from f-droid.org/docs/Reproducible_Builds. Re-verify on adoption — versions in
this table are planning anchors, not pins.
