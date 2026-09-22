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
- **AVDs**: phone at **API 31** (the minSdk floor since ADR-0015) *and* at the current API — the
  two behavioral extremes — plus a tablet (landscape, current API). Testing only the newest image
  is how a lock that could not be armed at all on the floor reached main (ADR-0006), so the
  floor is not optional. The floor moved from 30 to 31 for this very reason: an API-30 emulator
  cannot complete the sign-in, so a floor of 30 was a floor nothing could exercise. Compose `@Preview` variants for
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
- Optional: Gradle Managed Devices locally for the instrumented suite (same definition as CI):
  `./gradlew :app:atdApi31DevDebugAndroidTest` boots the `atdApi31` device declared in
  `app/build.gradle.kts` — `aosp-atd`, API 31 — runs the suite and shuts it down again.

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

> **Status, checked against the build on 2026-09-22 — the table above is the plan, not the build.**
> Built and gated in `./gradlew check`: the unit layer (JUnit **4**, not 5, with
> kotlinx-coroutines-test; Turbine is not a dependency), Robolectric, and the MockWebServer contract
> layer. Built and run by CI **outside the gate**: three instrumented tests in `app/src/androidTest`
> (`AppLockKeystoreContractTest`, `ApiReaderMainThreadTest`, `TestStackTlsHandshakeTest`), on one
> Gradle Managed Device (`atdApi31`: `aosp-atd`, API 31 — the floor, not the plan's API 30 + 37
> pair) by `instrumented.yml`, nightly and on pull requests that touch them; not a required check.
> `TestStackTlsHandshakeTest` reports itself skipped there, because it needs the main repository's
> test stack; it stays a by-hand test. The app-lock test gives a lock-less CI emulator a throwaway
> PIN for its duration (`SecureLockScreenRule`), because an auth-bound key cannot be created
> without one. (Changed 2026-09-22 — until then no workflow ran the suite.) **Not built at all:**
> the Keycloak Testcontainer auth flow, screenshot tests, the nightly E2E workflow, and Kover — no
> coverage is measured and nothing gates on it. `feature/` stayed empty, so the `feature:*` half of
> the coverage line has no modules to apply to.

## 4. GitHub Actions — hardened for a public repo

Baseline posture (all from GitHub's current security docs):

- Workflows trigger on `pull_request` (never `pull_request_target` for anything that builds PR
  code); fork PRs get no secrets and a read-only `GITHUB_TOKEN` by design; first-time-contributor
  runs require approval (default).
- Top-level `permissions: contents: read` in every workflow; scopes widened per job only.
- **Every third-party action pinned to a full commit SHA** (the only immutable reference —
  tj-actions/changed-files, CVE-2025-30066, is the case study; >23 000 repos hit by tag rewrite).
- **Gradle dependency verification** (`gradle/verification-metadata.xml`: checksums + PGP where
  published) is the Android-toolchain counterpart of SHA-pinned actions — **planned, not in
  place**; it is the open gate in § 5, and this bullet claimed it was live until 2026-09-04.
- **Executables the workflows fetch themselves are pinned by content, not by version**: actionlint
  by a `sha256sum -c` against a hardcoded digest, zizmor by `pip --require-hashes` against
  `.github/requirements/zizmor.txt`. The two differ because zizmor's GitHub release publishes no
  checksums file while PyPI publishes sha256 as first-class metadata. gitleaks takes the same
  route as actionlint, with its digest read from the release's own `gitleaks_<ver>_checksums.txt`.
  None of the three is covered by a Dependabot ecosystem: version and digest are bumped by hand,
  together, and a mismatch fails loudly rather than silently installing something else.
- **zizmor** (v1.30.0) + **actionlint** (v1.7.12) lint the workflows in CI; zizmor's
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
| `ci.yml` | PR + push to main | `./gradlew build` (assemble the three enabled variants — `devRelease` is disabled since 2026-09-22 because nothing ships it — unit + Robolectric tests, Android Lint with SARIF → code scanning, detekt, Spotless/ktlint), wrapper validation; second job: actionlint + zizmor over `.github/workflows` **and** `.github/actions` | **built** |
| `codeql.yml` | PR + push + weekly | CodeQL `security-and-quality` on `java-kotlin` (`build-mode: manual` — a real `assembleDevDebug --no-build-cache` over a **read-only** dependency cache, then an assertion that fails the job, before anything is uploaded, when the database holds fewer Kotlin files than the build compiles; see the file header for why `none` was abandoned and why the cache is safe) and on `actions` (`build-mode: none`), wrapper validation. One query, `java/local-variable-is-never-read`, is excluded via `.github/codeql/codeql-config.yml` — ADR-0020. **The extractor also caps the repo's Kotlin version**: it hooks the compiler, so a Kotlin above its `versions.bzl` list fails this job at the Gradle step rather than degrading the scan — the catalog holds `kotlin` one release back and Dependabot ignores the bump, ADR-0022 | **built** |
| `dco.yml` | PR | Signed-off-by trailer matching the author on every commit the PR adds | **built** |
| `gitleaks.yml` | PR + dispatch | checksum-verified gitleaks binary, range-scoped to `base..head` on a PR | **built** |
| `supply-chain.yml` | dependency review on PR; Scorecard daily + dispatch | dependency-review-action (fails on moderate+ and on incompatible licences), OpenSSF Scorecard → code scanning. Scorecard deliberately does **not** run on push: its Binary-Artifacts check excludes the wrapper jar only once `ci.yml` has succeeded for that commit, and a 40 s scan racing a 15 min build never sees that. Daily rather than weekly because the exclusion only looks at the 30 newest `ci.yml` runs | **built** |
| `dependabot.yml` | daily / weekly | `gradle` daily (see the note below), `github-actions` weekly | **built** |
| `instrumented.yml` | nightly + dispatch + PRs touching `app/src/androidTest`, the workflow or the build-environment action | the three instrumented tests on the `atdApi31` Gradle Managed Device (`aosp-atd`, API 31, `swiftshader_indirect`) on `ubuntu-latest` with the KVM udev step; **not a required check**. The owner approved a label trigger; no fitting label exists, so a path filter stands in until one does | **built** (2026-09-22) |
| `release-dry-run.yml` | PR + push to main + dispatch | generate a throwaway key → base64 round trip, decoded from `env` exactly as the real secret is → `assembleProdRelease` → `apksigner verify` at minSdk **31** (v3 present, v1 absent, one signer, certificate is the generated one) → shred; keytool reads its password with `-storepass:env`; no secrets, no cache, nothing published | **built** |
| `release.yml` | tag `v*`; dispatch **on the tag** (`--ref vX.Y.Z`), no free-text input | refuses a ref that is not a `vMAJOR.MINOR.PATCH` tag, wrapper validation, build APK, sign, `apksigner verify` at minSdk **31** against the configured key, provenance attestation, dependency SBOM (attached as a release asset and attested), **draft** release via `gh release create --verify-tag` — **environment `release`**; every secret reaches its step through `env:`, keytool via `-storepass:env` | **built** |

**One build environment for six jobs.** JDK 25, the SDK licences with an empty package list, wrapper
validation and `setup-gradle` sit in one local composite action, `.github/actions/android-build-env`,
used by `ci.yml` (×2), `codeql.yml`, `release.yml`, `release-dry-run.yml` and `instrumented.yml`. Its
`cache-mode` input is **required** — `write-on-main`, `read-only`, `disabled` or `skip` — because a
default would make the release job's no-cache rule a decision taken by omission. Callers use
GitHub's self-repository form, `uses: $/.github/actions/android-build-env`, which is pinned to the
workflow's own commit and cannot pick up an action a previous step wrote into the workspace — the
`./` form could, and zizmor 1.30's `self-repository` audit turned the workflow lint red on it the
day the composite landed (#178). The checkout stays in each workflow, because every job builds from
the workspace. Dependabot watches the action's directory (`directories: [/, /.github/actions/*]`),
and zizmor scans it (added 2026-09-22, audit SIB-SIMP-04).

**The first `instrumented.yml` run (#178) was red for two reasons neither of which was the app.**
`TestStackTlsHandshakeTest` skips itself without the test stack, and the managed-device runner
counts that assumption failure as a failure — so the job now excludes it by name
(`notClass`). And `SecureLockScreenRule`'s `locksettings set-pin` did not give the ATD emulator a
screen lock, so the app-lock contract tests failed in the rule rather than in the Keystore; the
rule now reports the command's own answer and whether the image has the secure-lock-screen feature,
which is what decides between fixing the command and changing the image.

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
  floor is 31, ADR-0015) and the scheme Janus attacks. v3 carries the rotation lineage the key
  strategy below depends on and has to be present from the *first* signed build. v2 is inert on
  API 30+, where Android always uses v3; it stays enabled because it costs a few kilobytes and is
  what most APK-inspection tooling reports.
- **No secret is expanded into a script, and no password is an argument.** Each secret reaches
  its step through `env:` and is read as a shell variable; keytool gets the store password as
  `-storepass:env KRT_SIGNING_STORE_PASSWORD`, because a plain `-storepass <value>` sits in the
  process's argv, which every process on the runner can read. The dry run takes the same shapes
  with its throwaway key — the encoded keystore arrives through an `env:` expression and passes the
  same PKCS#12 check — so a regression here turns a pull request red first (audit SIB-SEC-07,
  2026-09-22).
- **The release tag is the run's own ref.** `release.yml` has no free-text `tag` input any more; a
  manual re-run is dispatched on the tag (`gh workflow run release.yml --ref v0.3.1`). Its first
  step refuses anything that is not a `vMAJOR.MINOR.PATCH` tag, and `gh release create
  --verify-tag` refuses to create a tag that is not on the remote (audit SIB-SEC-06, 2026-09-22).
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
- SBOM per release: **the fallback was taken.** The CycloneDX Gradle plugin's AGP compatibility was
  never verified (its README is silent on Android), so `release.yml` exports GitHub's own
  dependency-graph SBOM — no plugin, no way to break the build, and the same resolved graph the
  `dependency-submission` job on `main` keeps current. It is **SPDX 2.3**, not CycloneDX, which is
  why the asset is named `basetool-android-sbom.spdx.json` rather than `*-bom.json`: a consumer
  should not have to sniff the file to learn which parser opens it.

  Two properties are load-bearing and were both missing until 2026-09-14:

  - **It is attached to the release.** The export step existed and wrote the file into the
    workspace, where the job then discarded it — `gh release create` uploaded the APK alone. An
    SBOM nobody can download is not evidence, it is a log line.
  - **It is the document, not the API response.** The endpoint answers `{"sbom": {…}}`, so the raw
    body is a GitHub envelope that happens to contain an SBOM; `--jq .sbom` unwraps it to a file an
    SPDX tool can actually open.

  It carries its own `actions/attest-build-provenance` attestation, for the reason the main repo
  states in ADR-0145 / REQ-OPS-023: published without provenance an SBOM is a bare file behind a
  URL, indistinguishable from a flattering one. A repository whose dependency graph has never been
  submitted answers with nothing rather than an error, and the job warns and ships without one
  instead of failing — an SBOM is evidence to publish, not a gate on a build that is already
  signed and attested.
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
by owner decision) + detekt + Spotless(ktlint) verify.

**detekt runs twice, and the difference matters.** The plain `detekt` task analyses without type
resolution; `:app:detektMain` analyses every enabled production variant *with* it — three since
`devRelease` was disabled on 2026-09-22, `src/dev` still covered through `devDebug` — and only the second
sees the rules that need a resolved type — `UnusedPrivateFunction`, `UnusedPrivateProperty`,
`InjectDispatcher` and their family. Until ADR-0017 only the first was gated, and five unused
private declarations accumulated unreported. `:app:check` now depends on `detektMain`;
`:core:auth`, `:core:network` and `detektTest` deliberately do not yet — see the ADR for the
kotlinx.serialization limitation that blocks the core modules and the test-builder policy call that
blocks the test sources. All gates green before every push (the
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
| Keycloak issuer | test-stack realm | `https://profit-base.online/auth/realms/iri` |
| Network security config | debug-overrides trust for test CA | system trust (+ pins in Phase 5) |
| Logging | verbose, local | warn+, local ring buffer only |

No runtime-switchable endpoints in release builds (an attacker-visible server switcher is an
unnecessary gift); QA against staging happens via the dev flavor.

## 7. Version sources (all fetched live 2026-08-17)

gradle/actions v6.3.0, android-emulator-runner v2.38.0, Robolectric 4.16.1, detekt 2.0.0-alpha.6
(the 1.23 line's embedded IntelliJ library cannot parse the JDK 25 version string and dies with a
bare `> 25`, so the alpha is the only usable line — this entry said "pinned to 1.23.x" until
2026-09-04), ktlint 1.8.0, zizmor v1.30.0, actionlint v1.7.12,
gitleaks v8.30.1, CycloneDX Gradle plugin 3.4.1, dependency-review-action v5, Scorecard action
v2 — each from the project's GitHub releases API / official docs; CodeQL Kotlin GA status from
codeql.github.com supported-languages; KVM-on-hosted-runners from the GitHub changelog
(2024-04-02); cache/fork-PR/environment semantics from docs.github.com (Actions security
references); Play App Signing / apksigner rotation from developer.android.com; reproducible-
builds status from f-droid.org/docs/Reproducible_Builds. Re-verify on adoption — versions in
this table are planning anchors, not pins.
