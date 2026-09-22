# Apple platforms (iPhone / iPad) — feasibility assessment

> **Doc type:** Assessment · **Audience:** @greluc
> **Status:** **analysis, not a decision, and not a plan to execute.** Nothing here reopens a
> resolved decision. Q1 („GitHub Releases APK (+ Obtainium); no Play") and the Android-only scope
> of [`ANDROID_APP_PLAN.md`](ANDROID_APP_PLAN.md) stand until an ADR says otherwise.
> **The question:** Kotlin and Compose run on Apple devices — can this app be brought to iPhone
> and iPad *with simple means*?
> **The answer:** the **code** is portable at a real but bounded cost. The **channel** is the wall,
> and it is not one engineering can climb. There is no „simple means" on this platform for this
> project. → § 1, § 7.
> **Repository measured 2026-09-13** at `7c37002`. **Apple and JetBrains documentation read
> 2026-09-13**; § 9 lists every source and what was *not* verifiable.
> **Related:** [`GOOGLE_PLAY_DISTRIBUTION_PLAN.md`](GOOGLE_PLAY_DISTRIBUTION_PLAN.md) (the same
> shape of question for the other store) · [`ANDROID_APP_SECURITY.md`](ANDROID_APP_SECURITY.md)
> (§ 4 DPoP, § 5.1 pinning) · [`ANDROID_APP_PRIVACY_GDPR.md`](ANDROID_APP_PRIVACY_GDPR.md) ·
> [`core/designsystem/fankit/README.md`](../core/designsystem/fankit/README.md)

---

## 0. What this assessment settles, and what it does not

It settles **what the work is, what it costs, what cannot be ported at all, and which route to
members' devices exists** — with numbers measured in this repository rather than estimated. It does
not settle whether to go. That is § 7, and it is one decision with three honest outcomes.

Two facts frame everything below, and they pull in opposite directions.

**The premise of the question is correct.** Compose Multiplatform for iOS has been Stable since
1.8.0 (May 2025) and is at 1.10.3; Compose is genuinely a cross-platform UI toolkit now, and the
organisation already ships Compose outside Android — the SC Extractor is Compose for Desktop on
CMP 1.11. This is not exotic technology.

**The premise stops at the device boundary.** Android accepted this project's distribution decision
because Android lets anyone install a signed APK from a URL. Apple does not, and the route that
looks equivalent — Web Distribution, an app served from our own site — carries an eligibility bar
(§ 2.2) that this project cannot clear and will not clear by working harder. Every remaining route
either expires, counts devices, or goes through App Review.

---

## 1. Two questions, and only one of them is engineering

| | Question | Answer | Where |
|---|---|---|---|
| A | Can the codebase run on iPhone and iPad? | **Yes.** Roughly 100k lines of Kotlin, of which the great majority is portable; four platform clusters must be re-implemented, and the module layout must be rebuilt. Weeks-to-months of work, not days. | § 3, § 4 |
| B | Can the result reach a member's iPhone the way the APK reaches their phone? | **No.** No Apple channel is both durable, open to a hobby project, and free of App Review. | § 2 |

Question B is settled first on purpose. A perfect port that cannot be installed is not a product,
and the port is the expensive half.

---

## 2. The channel — every Apple route, measured against Q1

Q1 chose GitHub Releases plus Obtainium for stated reasons: no gatekeeper between a release and a
member, no store review of a Star Citizen fan work, no Google account in the loop, no Play
Integrity, self-managed signing. Here is what Apple offers instead.

| Route | Reach | What it costs | Why it fails Q1's intent |
|---|---|---|---|
| **App Store** | everyone | US$99/year Apple Developer Program; **App Review per release**; a working demo account; Fan Kit artwork and Star Citizen IP in front of a reviewer | Introduces exactly the gatekeeper Q1 removed — and adds a *production* account handed to a third party (§ 2.4) |
| **TestFlight** | 10 000 external testers by link | same US$99/year; **every build expires after 90 days**; Beta App Review for the first external build of each version | The app dies three months after each release unless re-cut. A permanent beta is not a release channel |
| **Ad Hoc** | **100 devices per device type per year** | UDID of every device collected and registered; annual cert rotation; the count does not free up when a member leaves | Caps the squadron, and collecting device identifiers from members is a privacy-gate question we do not want to answer |
| **Web Distribution (EU)** | EU users, from our own site | the closest analogue to Obtainium — and unreachable (§ 2.2) | Eligibility bar cannot be met |
| **Alternative marketplace (EU)** | EU users with iOS 17.4+ | Developer Program membership plus notarization; the marketplace's own terms | EU-only, third-party dependency, excludes every non-EU member; still notarized by Apple |
| **Apple Business Manager custom app** | named organisations | recipients must be ABM organisations | „DAS KARTELL" is not a company with managed Apple IDs |
| **Free personal provisioning** | one device, 7 days | each member needs a Mac and Xcode | Not a channel |

### 2.1 The two that are actually on the table

Only the **App Store** is durable. **TestFlight** works technically and buys time without a public
listing, but its 90-day build expiry converts „ship a release" into „ship a release every quarter
forever", and an expired build is a member locked out with no push channel to tell them why (Q2
removed push deliberately).

### 2.2 Web Distribution is the route that looks right and is closed

This is the one worth stating precisely, because it is the one that reads like Obtainium for iOS:
notarized apps served directly from our own website to EU users, no store listing. Apple's
eligibility criteria — **effective 2026-10-01, eighteen days from this assessment** — require
meeting **at least one** of:

1. a moderate financial-stability score from Dun & Bradstreet;
2. being publicly traded, or owned by a public company;
3. venture funding from an established investment firm;
4. a completed audit by a licensed accountant;
5. being a government entity, educational institution, or a fee-waiver-approved nonprofit;
6. a stand-by letter of credit of **USD 1 000 000**;
7. **one million first annual installs worldwide**.

A GPL-3.0 fan companion for one Star Citizen organisation meets none of them, and none is reachable
by doing the engineering better. Criterion 5 is the only theoretically open door and it would mean
founding and getting approval for a nonprofit — a larger project than the port.

> [!important] This is the finding that decides the question
> Android's channel decision was free. Apple's equivalent costs either a legal entity of a kind
> this organisation is not, or acceptance of App Review. Everything in § 3 and § 4 is downstream of
> that.

### 2.3 What App Review specifically means for *this* app

Not generic risk — four concrete exposures, each already documented for Google and each worse or
equal here:

- **Guideline 4.2 (minimum functionality).** Low risk. 26 destinations, 39 ViewModels and 777
  composables is not a web wrapper. Worth naming only because reviewers reject on first
  impression, and this app shows a **login gate** before anything.
- **Guideline 5.1.1(v) (account deletion).** Same analysis as the Play plan § 4.5: the app creates
  no accounts (registration is Discord/WoltLab), so the requirement may not attach — but check
  whether the Keycloak login page shows a *Register* link, and declare a deletion request URL
  either way. It is cheap and removes an argument.
- **Guideline 5.2 (intellectual property).** The Fan Kit licence is the answer, and it is a real
  one — but it is now an argument to be *won with a reviewer*, per release, over Star Citizen
  artwork and trademarks. Google's plan already required the Fan Kit disclaimer as a prerequisite
  (§ 1.3 there); Apple additionally reviews the listing artwork. Fankit Agreement 2(k) then obliges
  us to report the App Store listing URL to `legal_notices@cloudimperiumgames.com`, exactly as
  Step 12 of the Play plan does.
- **Review account in production.** All four traps from the Play plan § 4.1 apply unchanged: the
  account must be approved (not `ROLE_PENDING_APPROVAL`), must have already accepted the Terms,
  must hold an org unit and member roles, and must live in **its own org unit with synthetic
  content** — because this repo's rule is that anything reaching a screenshot or a log is assumed
  leaked. That is a permanent standing credential into the production realm, held for Apple's
  benefit, in a tool that removed anonymous access on purpose (ADR-0159 / `REQ-SEC-052`).

### 2.4 Build and signing infrastructure

Less dramatic than it is assumed to be, and worth stating so it is not used as an argument either
way. iOS linking requires Xcode, therefore a **macOS runner** — GitHub Actions provides them, at
roughly ten times the per-minute cost of Ubuntu, and the existing hardened release workflow pattern
(secrets in the `release` environment, tag-restricted, required reviewer) transfers. Signing
material can be produced and managed through the App Store Connect API without owning a Mac.
**Development** is the honest catch: writing and debugging the iOS half of § 4.2 without a Mac in
the room is not realistic.

---

## 3. The code as it stands today — measured, not estimated

All numbers from `7c37002`, main source sets only, tests counted separately.

| Module | main files | main lines | `android*` imports | `java*`/`javax*` imports | Portability |
|---|---:|---:|---:|---:|---|
| `:core:contract` | 2 | 145 | 0 | 1 | **Portable as-is** — generated models, kotlinx.serialization; only `KrtDecimal` blocks (§ 4.3) |
| `:core:common` | 2 | 194 | 1 file | 0 | Near-portable |
| `:core:network` | 15 | 2 056 | 1 file | 10 | Rewrite against Ktor — the real work is here, not in the repositories |
| `:core:data` | 40 | 18 582 | **0 files** | 27 | **Portable in shape**: 27 files name `OkHttpClient` only as a constructor parameter type; 4 build URLs or requests |
| `:core:auth` | 21 | 3 294 | 8 files | 38 | The hard cluster: Keystore, DPoP, app lock, browser round-trip (§ 4.2) |
| `:core:designsystem` | 29 | 8 877 | 27 files (10 non-Compose) | 8 | Compose ports; resources, fonts and drawables move to CMP resources |
| `:app` | 180 | 67 221 | most | 82 | Portable *content*, wrong *module type* — all of it must relocate (§ 4.1) |
| **Total** | **289** | **100 369** | | **166** | |

Three measured facts change the estimate materially, and all three are good news:

1. **ADR-0001's module split happens to be port-friendly.** `:core:data` — the largest core module,
   18 582 lines of repositories — has **zero** Android imports. The HTTP client enters through
   constructor parameters, so swapping OkHttp for Ktor there is a type substitution in 27 files,
   not 27 rewrites.
2. **There is no DI framework to migrate.** `dagger.hilt`, `javax.inject`, `@Inject` and
   `@HiltViewModel` appear **zero times** in 289 main source files. DI is constructor injection
   wired by hand — the `AuthContainer` graph, `BasetoolApplication` owning every DataStore, and
   ViewModels built in `MainActivity`'s `viewModelFactory`. ADR-0001 decided that deliberately
   („every class here is constructor-injectable with no framework"). Hilt is KMP-hostile and would
   have been one of the largest single items on this list; it is simply absent. (The measurement
   also closed a documentation drift and removed six dead catalog entries — § 9.2.)
3. **No local database.** Persistence is DataStore only, and DataStore has supported KMP including
   iOS since 1.1.0. No Room or SQLDelight migration exists to do.

---

## 4. The port surface

### 4.1 The structural change comes first, and it touches every module

With AGP 9 — this repo is on 9.4.0 — the Kotlin Multiplatform plugin **is no longer compatible
with `com.android.application` and `com.android.library`**. Shared modules must move to
`com.android.kotlin.multiplatform.library`. So:

- every `:core:*` module changes its Android plugin and grows a `commonMain` / `androidMain` /
  `iosMain` source layout;
- `:app` **stays** `com.android.application` and therefore **cannot hold shared code**. Its 180
  main files and 67 221 lines — every screen, all 39 ViewModels, the navigation graph — must
  relocate into new KMP modules, and `:app` shrinks to `MainActivity`, the manifest and the
  flavours. This is the single largest mechanical item in the port;
- a new `iosApp` Xcode project appears, plus the empty `:feature:*` tree finally earns its
  existence as the destination of that relocation.

### 4.2 Four platform clusters must be re-implemented, not ported

`expect`/`actual` boundaries, each with an iOS counterpart that exists but differs:

| Cluster | Android today | iOS counterpart | Note |
|---|---|---|---|
| Token crypto | per-install P-256 key in Android Keystore/StrongBox; AES-256-GCM refresh-token cipher; Nimbus JOSE builds the DPoP proof | Secure Enclave P-256 key (`kSecAttrTokenIDSecureEnclave`) signs ES256; Keychain with `…ThisDeviceOnly`; the proof JWT hand-built (Nimbus is JVM-only) | The DPoP posture survives — Secure Enclave keys are non-exportable and ES256 is exactly what the proof needs |
| Browser round-trip | Custom Tabs + App Links | `ASWebAuthenticationSession` + Universal Links | Universal Links need an `apple-app-site-association` file served by the main repo — **server-side work outside this repository** |
| App lock | `androidx.biometric` | `LocalAuthentication` | ADR-0013's one-button lock screen reasoning is Android-prompt-specific and needs re-deciding |
| DataStore location | `preferencesDataStoreFile` | an iOS documents path, backup-excluded via `NSURLIsExcludedFromBackupKey` | Library itself is multiplatform; only the path and the exclusion are per-platform |

### 4.3 JVM types that do not exist on Kotlin/Native

166 `java.*` / `javax.*` imports, in four groups:

- **`java.time` (87 imports).** `kotlinx-datetime` covers instants, arithmetic and ISO parsing.
  What it does not cover is **locale-aware display formatting** — which is what 17 of those imports
  (`java.time.format`) and `android.text.format.DateUtils` do, for German-first output. That becomes
  `expect`/`actual` over `NSDateFormatter`, and the vault already records one defect in exactly this
  area (*„`DateUtils` ignores the zone it was handed"*), so it is a place to be careful rather than
  quick.
- **`java.math.BigDecimal` (24 imports).** `KrtDecimal` exists precisely so call sites do not name
  the JVM type — but 24 files name it anyway. Kotlin/Native has no `BigDecimal`; the usual KMP
  replacement is `com.ionspin.kotlin:bignum`. This is the **bank's money type**: the class's own
  KDoc explains that a rounding error here is a wrong balance, not a display bug, so the
  replacement needs its own test pass rather than a swap.
- **`java.security` / `javax.crypto` (28 imports).** Covered by § 4.2; concentrated in `:core:auth`.
- **The remainder (~27).** `java.io`, `java.util`, `java.net`, `java.util.concurrent.atomic`,
  `java.text` — mechanical, mostly one-line substitutions (`kotlin.concurrent.Atomic*`, okio, kotlinx).

### 4.4 The HTTP layer

OkHttp is JVM-only, so `:core:network` is rewritten against Ktor: the client, the three interceptors
(`ServerTimeInterceptor`, `TokenRefreshInterceptor`, `MandatoryHeadersInterceptor`) as plugins with
their **documented order preserved**, and `SseStream` onto Ktor's SSE client — live sync is the
app's live-data channel and its five rules must hold identically. `:core:data` follows as a type
swap (§ 3). Ktor's Darwin engine and the SSE plugin both support this; the interceptor-order KDoc
in `KrtHttpClient` is the specification to port against.

### 4.5 Resources are the biggest mechanical item after § 4.1

| What | Count |
|---|---|
| `R.string` references | 1 874 |
| `stringResource(` call sites | 1 570 |
| `R.plurals` / `R.drawable` / `R.font` / `R.raw` references | 80 / 58 / 4 / 1 |
| `<string>` entries across DE and EN (`:app` 1 522 + 1 521, `:core:designsystem` 37 + 35) | 3 115 |
| `<plurals>` entries across DE and EN (`:app` 84 + 84) | 168 |

Android `res/` becomes CMP `composeResources/`, and `R.string.x` becomes `Res.string.x`. The moves
are scriptable; three things are not:

- **The Fan Kit strings.** The § 2b trademark line and the clause-2(g) notice are byte-exact,
  `translatable="false"`, prescribed legal wording — including the space before the third ® in one
  and `Ltd..` in the other. `KrtFanKitBandTest` and `FanKitNoticeParityTest` pin them per locale and
  may never be split into independently disableable halves. Moving these strings between resource
  systems is a **compliance operation**, done with the tests, not a find-and-replace.
- **Per-app language.** ADR-0007 implements the in-app language switch through AppCompat. iOS has
  per-app language, by a different mechanism, and the switch needs re-deciding.
- **A live toolchain bug sits on this exact path.** JetBrains issue **CMP-9547** reports that CMP
  resources are **not packaged into the Android APK** when AGP 9.0.0 is combined with
  `com.android.kotlin.multiplatform.library` — the app then crashes with `MissingResourceException`.
  Reported against CMP 1.10.0 / Kotlin 2.3.0 / AGP 9.0.0. I could not read the issue page itself
  (YouTrack renders client-side), so **its current state is unverified** — check it before
  committing to a date. The shape of the risk is familiar: it belongs in the same family as
  CLAUDE.md's „toolchain landmines".

### 4.6 Versions: the mirrors are behind, and some are alphas

This repository pins stable androidx: Compose BOM `2026.08.00`, `lifecycle 2.11.0`,
`material3-adaptive 1.3.0`, `navigation 2.10.0`. The multiplatform equivalents live under
`org.jetbrains.compose` / `org.jetbrains.androidx.*` and trail — recent published sets show
`lifecycle 2.10.0-alpha05` and `adaptive 1.3.0-alpha02`. The repo has a written position on exactly
this trade: the biometric pin comment rejects an alpha „in the authentication path of a shipped
app", and detekt's 2.0.0-alpha is an exception that had to be justified in writing. Going CMP means
either accepting alpha-versioned UI infrastructure across the whole app, or waiting for the mirrors.

### 4.7 Gates, tests and the privacy inventory

- **107 test files use Robolectric.** Robolectric is Android/JVM-only. Those tests can keep testing
  shared code from the Android target's test source set, but **iOS gets no equivalent coverage**
  from them. „Every new feature ships with tests" then needs an iOS leg — simulator UI tests —
  that does not exist today, on top of Kover's ≥ 80 % line gate on `core:*`. *(Corrected
  2026-09-22: that gate was planned in `ANDROID_APP_DEV_CI.md` § 3 and `CLAUDE.md` stated it as
  fact, but Kover is not in the build — no coverage is measured today, so there is no gate to
  re-baseline, only one to build.)*
- **`allWarningsAsErrors`, Lint `warningsAsErrors`, detekt, Spotless** all apply to every line
  moved. A port of 100k lines under warnings-as-errors is a large, noisy diff by construction.
- **The privacy gate makes each new dependency a formal step.** Ktor, kotlinx-datetime, bignum and
  the CMP runtime are four additions; each one means re-checking data flows, extending the
  inventory table in `ANDROID_APP_PLAN.md` § 7 **in the same PR**, and re-running the § 25 TDDDG
  storage analysis for anything that stores on-device. Not hard — but not skippable either.
- **`licensee`** produces the shipped `oss_licenses.json`; the iOS side has no equivalent wiring
  today, and the app has a Licenses screen that must keep telling the truth.

---

## 5. Five things that do not port — they have to be re-decided

These are the items where a requirement, not a library, is the obstacle. Each needs an owner
decision plus a spec amendment; none has an iOS implementation to find.

1. **Screenshot protection (ADR-0010, member-switchable).** Android has `FLAG_SECURE`. **iOS has no
   API that blocks screenshots** — none. Recording and mirroring can be *detected*
   (`UIScreen.isCaptured`, the analogue of `DETECT_SCREEN_RECORDING`), but the switch ADR-0010
   describes cannot exist on iOS. The requirement must either be declared Android-only or dropped
   for iOS, explicitly.
2. **Certificate pinning (§ 5.1 of the security concept).** Today: a declarative
   `network_security_config.xml` pinning three hosts to both Let's Encrypt roots, asserted by
   `NetworkSecurityConfigTest` reading the XML. iOS has no declarative equivalent — pinning becomes
   **code** in the Ktor Darwin engine's challenge handler, and the test that guards it must be
   rewritten to test behaviour instead of a resource file. The rotation runbook then has two
   procedures.
3. **Hardware key attestation (the optional Phase 5 tier).** Android's TEE/StrongBox chain
   verification has a real Apple analogue — **App Attest / DeviceCheck** — but it is a different
   protocol with a different server side, i.e. a second implementation in the main repo, not a
   port.
4. **App Links → Universal Links.** Needs a server-side `apple-app-site-association`, an
   Apple-specific Team-ID-bound artefact served by the main repo. Cross-repo work, under the main
   repo's rules.
5. **The distribution channel itself (Q1).** § 2. No analogue exists.

---

## 6. The option that *is* simple, and it is not Kotlin

The question was „with simple means". There is a simple means to put Basetool on iPhones and iPads
— it just is not this repository.

**Make the existing web frontend installable as a PWA.** Measured in `basetool` on 2026-09-13:
`fragments/head.html` already ships `<meta name="viewport" content="width=device-width,
initial-scale=1.0">`, and the stylesheets already carry media queries (`styles.css` 16,
`personal-inventory.css` 7, `bank.css` 6, `materialboerse.css` 3, …). What is missing is only a
**web app manifest**, icons, the `apple-mobile-web-app-*` meta tags and optionally a minimal service
worker — none of which exists yet (`find` returns no manifest and no service worker).

| | PWA on iOS | Native iOS port |
|---|---|---|
| Work | small, in `basetool`; layout verification per page is the actual cost | § 3–§ 5 |
| Apple account | **none** | US$99/year |
| App Review | **none** | per release |
| Expiry | **none** | TestFlight: 90 days |
| Reach | every iPhone and iPad, and every other browser | iOS only |
| Token at rest | **none** — the web app is a server-rendered BFF with its session in Redis, so no refresh token ever reaches the device | Secure Enclave + Keychain, re-implemented |
| Parity | whatever the web has, including the web-parity gaps the app is still missing | app parity, ported |
| What it is not | not a native app: no app lock, no biometrics, no offline story, no App Store presence, and Apple's 2024 EU wobble over home-screen web apps is a reminder that this rests on Apple's tolerance | — |

Honest framing: the PWA is not a substitute for the Android app's security posture. It is a way for
iPhone-holding members to reach the tool through an icon on their home screen — at a fraction of a
percent of the port's cost, with **better** token-at-rest properties, and with no dependency on
Apple's goodwill beyond the browser.

The third option is worth naming because it is the status quo and it is not absurd: **Safari**.
iPhone members use the web app as they do today.

---

## 7. Recommendation, and the decision to take

**Not with simple means.** Recommended, in order:

1. **Add the PWA layer to the web frontend** (main repo, small, reversible, helps every non-Android
   member immediately — including desktop). This is the answer to the question as asked.
2. **Do not start the iOS port now.** Not because the code resists — § 3 shows it resists less than
   expected — but because § 2 has no acceptable exit. The port's whole value is gated on a channel
   decision that costs either App Review plus a standing production credential for Apple, or a
   legal entity this organisation is not.
3. **Keep the port cheap to start later, for free.** Two habits cost nothing today and remove weeks
   later: keep `:core:data`'s zero-Android-import property (it is a real asset, currently
   unprotected by any gate), and keep new platform APIs behind interfaces in `:core:*` rather than
   calling them from features.

**If the owner wants iOS anyway**, the decision to take is not „port or not" but **„App Store, or
TestFlight forever"** — and that decision must precede any code, because it changes the release
workflow, the review-account posture, the Fan Kit reporting obligation and the answer to
`REQ-SEC` questions that are currently answered by „there is no store".

Either way, three ADRs would be needed before implementation: the platform decision itself, the
screenshot-protection delta (§ 5.1), and the pinning-in-code delta (§ 5.2).

---

## 8. If it goes ahead — order of work, and the tripwires

Sequenced so that each step is independently verifiable and the expensive step comes last.

| Phase | Work | Verifiable by |
|---|---|---|
| 0 | Channel decision + ADR; Apple Developer Program enrolment; `apple-app-site-association` designed in the main repo | the decision exists in writing |
| 1 | Module restructuring to `com.android.kotlin.multiplatform.library`, `:app` relocation into `:feature:*` KMP modules — **Android-only, iOS target not yet added** | `./gradlew check` stays green and the APK is byte-comparable in behaviour |
| 2 | `java.*` removal: kotlinx-datetime, the `KrtDecimal` replacement with its own money test pass, atomics, io | existing unit tests, unchanged |
| 3 | OkHttp → Ktor in `:core:network`, type swap in `:core:data`; MockWebServer contract tests re-pointed | the `openapi.json` fixture contract suite |
| 4 | Resources → CMP resources, Fan Kit strings **with** their compliance tests | `KrtFanKitBandTest`, `FanKitNoticeParityTest`, per locale |
| 5 | Add the iOS target: the four `expect`/`actual` clusters, pinning in code, `iosApp` shell | simulator run, then a device |
| 6 | iOS test leg, Kover re-baselined, macOS release workflow, privacy inventory and licence report extended | CI green on both platforms |

Tripwires, in the spirit of CLAUDE.md's landmine list:

- **CMP-9547** (§ 4.5) sits on the Phase 1/4 path. Verify its state before planning dates.
- **`org.jetbrains.androidx` alphas** (§ 4.6) collide with this repo's no-alpha stance. Decide
  before Phase 1, not during.
- **`allWarningsAsErrors` plus a 67k-line relocation** produces a diff nobody can review as one
  change. Phase 1 must be split per module, or it will be merged unreviewed.
- **Two security implementations means two review passes.** The MASVS review of 2026-08-24 covered
  Android. iOS is a new review, not a re-read.
- **Fan Kit strings** are legal wording. They move with their tests or not at all.

---

## 9. Sources

Read or measured **2026-09-13**.

**This repository** (`7c37002`): module inventories, import counts, resource counts and test counts
as tabulated in § 3 and § 4, measured with `find` / `grep` over `src/main` and `src/test`;
`gradle/libs.versions.toml`; `core/network/.../KrtHttpClient.kt`;
`core/contract/.../KrtDecimal.kt`; `core/contract/build.gradle.kts`;
`docs/ANDROID_APP_SECURITY.md` §§ 4, 5, 5.1; `docs/GOOGLE_PLAY_DISTRIBUTION_PLAN.md` §§ 1.3, 4.1,
4.5, 12; `core/designsystem/fankit/README.md`; `docs/adr/0001`, `0007`, `0010`, `0013`.
**`basetool`** (main repo, § 6): `frontend/src/main/resources/templates/fragments/head.html`,
`frontend/src/main/resources/static/css/*.css`, and the absence of any manifest or service worker.
**Knowledge base:** `10 Systems/Android App.md`, `App Security.md`, `SC Extractor.md`,
`Live Sync.md`.

**Apple:** [Changes for apps in the European Union](https://developer.apple.com/support/dma-and-apps-in-the-eu/)
— the seven Web Distribution eligibility criteria effective 2026-10-01, and notarization;
[About alternative app distribution](https://support.apple.com/en-us/118110).
**JetBrains / Google:** [Compose Multiplatform 1.8.0 — iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/);
[What's new in Compose Multiplatform 1.10.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html);
[Updating multiplatform projects with Android apps to use AGP 9](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html);
[Set up the Android Gradle library plugin for KMP](https://developer.android.com/kotlin/multiplatform/plugin);
[Set up DataStore for KMP](https://developer.android.com/kotlin/multiplatform/datastore);
[Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html);
[CMP-9547](https://youtrack.jetbrains.com/issue/CMP-9547);
[GitHub Actions CI for KMP](https://kotlinlang.org/docs/multiplatform/github-actions-for-kmp.html).
**Distribution limits** (TestFlight 90-day expiry and 10 000 external testers; Ad Hoc 100 devices
per device type per year): [iOS Distribution Guide 2026](https://foresightmobile.com/blog/ios-app-distribution-guide-2026),
[Appcircle — iOS App Distribution](https://appcircle.io/guides/ios/ios-app-distribution).

### 9.1 Not verified

Stated so no reader mistakes them for measured facts:

- **CMP-9547's current state.** The issue exists and its content was readable only through a search
  snippet; YouTrack's page did not render for a fetch. Whether it is fixed in a 1.10.x or 1.11
  release is **unknown** and must be checked first-hand.
- **US$99/year** for the Apple Developer Program is long-standing but was not re-read from Apple's
  enrolment page on this date. Confirm at enrolment.
- **TestFlight and Ad Hoc limits** come from secondary sources (above), not from Apple's own
  documentation pages. The numbers are consistent across them and match long-standing Apple policy;
  confirm against Apple's developer documentation before they inform a decision.
### 9.2 Two drifts this assessment found, and how they were closed

Both were fixed on 2026-09-13, in the same unit of work as this document.

- **CLAUDE.md claimed Hilt.** It read „Constructor injection via **Hilt** only", while the codebase
  contains no Hilt, no Dagger and no `javax.inject` — and never did. The absence is a *decision*,
  not an omission: ADR-0001 states „every class here is constructor-injectable with no framework",
  and `AuthContainer`'s KDoc says Hilt is „deliberately not here yet" because a hand-written graph
  is easier to read than a generated one. So the sentence described an intended end state as
  current practice. **Fixed:** CLAUDE.md's Kotlin-conventions section now describes what the app
  actually does — constructor injection, the `AuthContainer` graph, `BasetoolApplication` owning
  every DataStore (ADR-0014, guarded by `ProcessStoreOwnershipTest`), ViewModels built in
  `MainActivity`'s `viewModelFactory` — and says adopting a framework needs an ADR.
  **`libs.versions.toml` lost four dead entries** with it: the `hilt` version, `hilt-android`,
  `hilt-compiler`, the `hilt` plugin alias, plus the `ksp` version and plugin alias, which existed
  only for Hilt's annotation processor and are applied by no module. The repo's own convention
  settles this: the one deliberately unused entry, `pin-bcutil`, carries a written „do not delete
  it as unused" — neither Hilt nor KSP carried a reason.
- **The Play plan listed finished work as a prerequisite.** Its § 1.3 called the Fan Kit 2(g)
  disclaimer „missing from both the app and the web frontend" and said to close that gap first.
  Verified 2026-09-13: the app ships it coupled with the § 2b line as `KrtFanKitBand` on Login and
  Einstellungen, pinned per locale by tests, and the web frontend ships it in
  `templates/fragments/fankit.html` with the string in all three `messages*.properties`.
  **Fixed:** § 1.3 now states it is met and keeps the old wording as a dated correction, the § 6
  timeline row is struck, and § 0 and § 1 no longer say „three decisions" — two remain, § 1.1 and
  § 1.2. What is still genuinely open is the *listing* copy, which does not exist yet.

Why this mattered to *this* assessment rather than being tidy-up: Hilt is KMP-hostile, so a repo
that really used it would carry one of the largest items in § 4. It does not — and that is a
measured fact now, not an inference from a stale sentence.
