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
(required status checks = the `ci.yml` gates, required review, no force-push, linear history) —
**still to be set in repository settings, and only possible once each check has run on `main`
so GitHub knows its name**; **tag protection for `v*`** (only the owner can push release tags —
the release workflow triggers on them); `CODEOWNERS` = @greluc (**built**); a `SECURITY.md` with a private disclosure channel (GitHub
private vulnerability reporting enabled) and the supported-versions statement; issue/PR templates
mirroring this repo's conventions.

## 2. Local development environment

- **Android Studio** (latest stable) + JDK 17+ toolchain; the Gradle wrapper is the only
  sanctioned build/test path (`./gradlew …`), mirroring this repo's rule.
- **AVDs**: phone at **API 30** (the minSdk floor) *and* at the current API — the two
  behavioral extremes — plus a tablet (landscape, current API). Testing only the newest image
  is how a lock that could not be armed at all on the floor reached main (ADR-0006), so the
  floor is not optional. Compose `@Preview` variants for
  compact/expanded window size classes cover most iteration without an emulator.

  **Split the two kinds of check between them, because one image cannot do both.**

  *Platform behaviour at the floor* — Keystore contracts, the app lock, TLS trust — belongs in
  `connectedDevDebugAndroidTest` and runs on the API 30 image. It needs no browser.

  *Interactive end-to-end* (a real login through Keycloak) needs an emulator whose **Chrome is 89
  or newer**, and that is a property of the image's build date rather than of its API level:
  the API 30 image ships Chrome 83, the API 29 one Chrome 74, the API 37 one Chrome 149.
  Keycloak marks its auth session cookies `Secure; SameSite=None`, and Chrome only sends those
  over `http://127.0.0.1` from version 89 (2021), which treats loopback as a secure context.
  Below that the login POST arrives without them and Keycloak answers `cookie_not_found`. So run
  the interactive flow on a current image, and read the floor's coverage from the instrumented
  tests.

  **A fresh emulator has a second, earlier blocker: Chrome's first run.** Before Chrome has been
  opened once, a `VIEW` intent lands in `org.chromium.chrome.browser.firstrun.FirstRunActivity`
  — the welcome screen — and a Custom Tab launched into it never comes back, so the app sits on
  „Anmeldung läuft …" with its login button disabled and no error anywhere. It reads exactly like
  a broken login. Skip the onboarding instead of clicking through it, so nothing is accepted on
  the tester's behalf:

  ```bash
  adb shell "echo 'chrome --disable-fre --no-first-run' > /data/local/tmp/chrome-command-line"
  adb shell am set-debug-app --persistent com.android.chrome
  ```

  Confirm it took: a `VIEW` intent must land in `ChromeTabbedActivity`, not `FirstRunActivity`.
  The two blockers stack — clearing the first run on the API 30 image gets the Custom Tab to open
  and then reveals the cookie error below, which is the version limit and not something setup can
  fix.

  **Serving the test stack's Keycloak over TLS does not fix this** — measured, not assumed. It
  works on the server side (Keycloak starts with both connectors, presents the shared test
  certificate, and the chain verifies), but the login runs in **Chrome**, and Chrome does not use
  the app's `<debug-overrides>` trust anchor: it answers `NET::ERR_CERT_AUTHORITY_INVALID`. Making
  Chrome trust the test CA means installing it into the *device's* store, which needs either the
  unautomatable Settings flow or `adb root` plus `-writable-system` — and that combination left
  the API 30 AVD permanently offline. TLS would therefore trade a working cleartext flow on
  current images for a certificate error on all of them.
- **Backend**: the existing **isolated test stack** from this repo
  (`docker-compose.test.yml`, `--env-file .env.test`, throwaway credentials — the hard rule
  "never production credentials in tests or local stacks" applies unchanged; teardown with
  `down --volumes`). The emulator reaches the host via `https://10.0.2.2:<port>`; the stack's
  self-signed certificate is trusted **only** via Network Security Config
  `<debug-overrides>` (active only in debuggable builds — release builds structurally cannot
  trust it). The override trusts the **user certificate store**, not a certificate committed to
  the repo: every developer generates their own throwaway keystore, so a bundled one would be one
  person's and would rot the first time anyone regenerated theirs. One-time setup per emulator,
  after starting the test stack:

  Both local services are reached through **`adb reverse`**, not through `10.0.2.2`. A connection to
  `10.0.2.2` times out on this setup even with the port published on all interfaces — measured twice,
  once per service: ICMP answers, the host's own browser loads the URL, and OkHttp still reports
  `SocketTimeoutException` after 10 s, which the app can only classify as "offline". The root cause
  is not established; the tunnel routes around it reliably, so the `dev` flavour targets the device's
  own loopback for both:

  ```bash
  adb reverse tcp:18080 tcp:18080   # Keycloak
  adb reverse tcp:11261 tcp:11261   # backend
  ```

  The throwaway keystore must use the alias **`basetool`** — `application.yml` pins it and does not
  read it from the environment, so any other alias fails start-up with "Alias name [basetool] does
  not identify a key entry", which reads like a code fault and is not one. Give it a SAN covering
  `127.0.0.1` as well, since that is the address the app now connects to.

  ```bash
  keytool -exportcert -rfc -alias basetool -keystore keystore.p12 -storetype PKCS12 -storepass <throwaway> -file basetool-dev-ca.crt
  ```

  Then push the file to the device and install it under *Settings → Security → Encryption &
  credentials → Install a certificate → CA certificate*. Android warns that a third party may
  monitor traffic; on a throwaway emulator against a local stack that is exactly what is being
  asked for. Skipping this step does not produce a certificate error in the app — the failure
  surfaces as `ApiError.Network`, i.e. as "you are offline" while the server runs on the same
  machine, which is why the step is written down rather than left to be rediscovered. A `dev` build flavor pins base URLs to the test stack; `prod` flavor pins the real
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
- **Robolectric's `android-all` runtime is a declared dependency, not a runtime download.**
  Left alone, Robolectric fetches the Android framework jar for the SDK under test from Maven
  Central the first time a test class runs — at test *execution* time, on its own HTTP client,
  outside Gradle's dependency resolution and therefore outside the cache above (which branch
  builds only read anyway, so caching alone would not have fixed it). When that one request
  fails, **every** Robolectric class in the run dies at `classMethod` with a bare
  `MavenArtifactFetcher` AssertionError, in modules the pull request never touched — a network
  flake that presents as a red "Build, Test & Lint" with failing tests, which is exactly the shape
  of a real regression and costs a diagnosis every time (PR #40, run 32477195019: 4 of 44 tests
  in `:app:testDevDebugUnitTest`, green on a plain re-run). The jar is therefore pinned in
  `gradle/libs.versions.toml` as `robolectricAndroidAll`, resolved through the root project's
  `robolectricSdks` configuration, staged into `build/robolectric-sdks` by
  `:stageRobolectricSdks`, and consumed with `robolectric.offline=true` +
  `robolectric.dependency.dir`, which the root build script sets on every `Test` task. Gradle
  resolves and caches it like every other artifact, and a fetch that fails now fails at
  resolution time with a name attached — the same thing SHA-pinned actions and dependency review
  already assume of everything else this build consumes.
  **A `robolectric` bump is a `robolectricAndroidAll` bump in the same commit.** The coordinate
  is `<androidVersion>-robolectric-<buildId>-i<preinstrumentedVersion>`, all three hardcoded in
  Robolectric's `DefaultSdkProvider` per release; Dependabot cannot know that. Drift fails
  loudly rather than randomly — `Unable to locate dependency: '<file>'`, and that file name is
  the value to put in the catalog. A new `@Config(sdk = …)` level needs its own artifact
  declared next to the current one; today every Robolectric test in the repo pins API 34.

  **Moving to API 37 was attempted on 2026-08-21 and is blocked upstream, not by us.** The Android
  17 runtime exists on Maven, and Dependabot duly proposed it (#45, closed). Two walls behind it:
  Robolectric 4.16.1 answers `IllegalArgumentException: API level 37 is not available` — the
  coordinate is only half the story, `DefaultSdkProvider` has to know the level — and 4.17, the
  release that does, exists solely as a beta which cannot run on this toolchain at all: all 350
  tests failed with `RuntimeException: Failed to interact with raw FileDescriptor internals;
  perhaps JRE has changed?`. So the pin at 34 is not inertia; it is the only level that works
  today. Revisit when 4.17 is stable. `android-all-instrumented` is on Dependabot's ignore list
  until then, because an automated bump of it is always wrong: the runtime, the `robolectric`
  version and every `@Config` pin have to move in one commit.
- Dependency graph via the separate `gradle/actions/dependency-submission` workflow; for fork PRs
  the documented two-workflow pattern (`pull_request` generates, `workflow_run` submits).

### Pipelines

| Workflow | Trigger | Jobs | State |
|---|---|---|---|
| `ci.yml` | PR + push to main | `./gradlew build` (assemble all four variants, unit + Robolectric tests, Android Lint with SARIF → code scanning, detekt, Spotless/ktlint), wrapper validation; second job: actionlint + zizmor | **built** |
| `codeql.yml` | PR + push + weekly | CodeQL `security-and-quality` on `java-kotlin` (`build-mode: manual` — a real uncached `assembleDevDebug`; see the file header for why `none` was abandoned) and on `actions` (`build-mode: none`), wrapper validation | **built** |
| `dco.yml` | PR | Signed-off-by trailer matching the author on every commit the PR adds | **built** |
| `gitleaks.yml` | PR + dispatch | pinned gitleaks binary, range-scoped to `base..head` on a PR | **built** |
| `supply-chain.yml` | PR + push + weekly | dependency-review-action (fails on moderate+ and on incompatible licences), OpenSSF Scorecard → code scanning | **built** |
| `dependabot.yml` | daily / weekly | `gradle` daily (see the note below), `github-actions` weekly | **built** |
| `instrumented.yml` | PR label or nightly | GMD emulator suite on `ubuntu-latest` with the KVM udev step | planned — waits for the first instrumented test |
| `release-dry-run.yml` | PR + push to main + dispatch | generate a throwaway key → base64 round trip → `assembleProdRelease` → `apksigner verify` (v3 present, v1 absent, one signer, certificate is the generated one) → shred; no secrets, no cache, nothing published | **built** |
| `release.yml` | tag `v*` | wrapper validation, build APK, sign, `apksigner verify` against the configured key, provenance attestation, dependency SBOM, **draft** release — **environment `release`** | **built** — cannot run until the owner runbook's §§ 2-3 provide the key and the environment |

**Why Dependabot runs the Gradle ecosystem daily.** Android Lint runs with
`warningsAsErrors = true` and its dependency checks treat an available newer version as a
finding, so a release upstream can turn `main` red without anyone touching this repository.
Dependabot is therefore not hygiene here — it is what keeps the build green. The alternative,
should the noise outweigh it, is to demote that one lint check and let Dependabot own freshness
on its own schedule; that is an owner decision, not a CI one.

**Not yet built, and deliberately so:** Gradle dependency verification
(`gradle/verification-metadata.xml`). The metadata has to be generated on every platform whose
resolved artifacts differ — a file written on Windows omits the Linux-only artifacts CI resolves,
and the failure is a red build that looks like tampering. It needs one generation run per
platform and a documented refresh flow before it can be turned on; see the open item in § 5.

### Release signing (no key leakage)

- **The signing path is rehearsed on every pull request** (`release-dry-run.yml`), with a key
  generated inside the run and shredded with it. The reason is the shape of the risk rather than
  the difficulty of the code: signing runs *once per release*, on the day of the release, with a
  key that cannot be regenerated — and every way it can be wrong produces an APK that looks
  finished. A signing config that never takes effect leaves AGP writing
  `app-prod-release-unsigned.apk` while the build stays green; a debug-signed "release" verifies
  perfectly and installs on the wrong lineage; a missing v3 block means a future key rotation can
  never be proven to Android, and *that* cannot be repaired in APKs already installed. The dry run
  therefore asserts the certificate in the APK is the one the run generated, not merely that some
  signature verifies.
  - The **base64 round trip is part of the rehearsal**, not scaffolding: it is the exact transport
    the real secret will use, and a truncated secret otherwise surfaces as a keystore-format error
    that reads like a corrupt key rather than like a broken transport.
  - It uses **no secrets**, so it runs on fork PRs too, and **no Gradle cache**, because the real
    release job restores none either — a rehearsal that runs warm would not rehearse the thing it
    exists for.
  - Nothing it builds is published. An APK signed with a throwaway key is good for these checks
    and for nothing else.
- The signing material reaches Gradle through four environment variables —
  `KRT_SIGNING_KEYSTORE`, `KRT_SIGNING_STORE_PASSWORD`, `KRT_SIGNING_KEY_ALIAS`,
  `KRT_SIGNING_KEY_PASSWORD` — and never through a file in the repository or a Gradle property.
  **All four or none**: a partial configuration fails the build rather than falling back to an
  unsigned APK, because three of four set is exactly how a release day ships something nobody can
  install as an update. With none set the release build is unsigned, which is what a contributor's
  `./gradlew build` and the ordinary CI gate produce.
- **Signature schemes: v1 off, v2 on, v3 on.** v1 is JAR signing, unreachable below API 24 (the
  floor is 30, ADR-0006) and the scheme Janus attacks. v3 carries the rotation lineage the key
  strategy below depends on and has to be present from the *first* signed build. v2 is inert on
  API 30+, where Android always uses v3; it stays enabled because it costs a few kilobytes and is
  what most APK-inspection tooling reports.
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
lint-gate discipline of this repo carried over). CI runs `./gradlew build` — `assemble` + `check`
— rather than a hand-picked task list, so the command that gates a PR is the command a
contributor runs locally; a CI-only task list is how the two drift apart.

**Open gate: Gradle dependency verification.** `gradle/verification-metadata.xml` is the
Android-toolchain counterpart of SHA-pinned actions and is not in place yet. The obstacle is
platform-dependent resolution: metadata generated on one OS omits artifacts another OS resolves,
and the resulting failure reads like tampering rather than like a missing checksum. It needs a
generation run per platform plus a documented `--write-verification-metadata` refresh flow in
CONTRIBUTING.md before it can gate anything. KDoc on every public API of `core:*` modules
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
