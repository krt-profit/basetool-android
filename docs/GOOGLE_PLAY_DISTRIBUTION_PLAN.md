# Google Play distribution — implementation plan and operator runbook

> **Doc type:** Plan · **Audience:** @greluc, and whoever implements the repo half
> **Status:** **proposal, not a decision.** Plan [Q1](ANDROID_APP_PLAN.md) still reads
> "GitHub Releases APK (+ Obtainium); no Play". Nothing here may be executed before that decision
> is reopened and an ADR records the reversal.
> **Related:** [`ANDROID_APP_DEV_CI.md`](ANDROID_APP_DEV_CI.md) § 4 (signing, release workflow) ·
> [`ANDROID_APP_PRIVACY_GDPR.md`](ANDROID_APP_PRIVACY_GDPR.md) § 6 (Play obligations) ·
> [`OWNER_RUNBOOK.md`](OWNER_RUNBOOK.md) · [`fankit/README.md`](../core/designsystem/fankit/README.md)
> **Google documentation verified:** 2026-08-27 — every requirement below was read from the
> official page on that date; § 8 lists them. Play Console navigation labels change; search the
> Console for the **section name**, not for a click path.

---

## 0. What this plan settles, and what it does not

It settles the engineering work, the manual work, the order, and the traps this particular app
walks into. It does not settle whether to go — that is § 1, and it is three decisions, not one.

Two facts frame everything below.

**Four public releases already exist.** `v0.1.0` … `v0.1.3` shipped on 2026-08-25, signed with the
release key whose certificate fingerprint the README publishes. Every install in the field is bound
to that certificate. A Play listing signed with a different key is a *different app* to Android:
those installs cannot update from it and the channels fork permanently. § 1.2 is therefore a
migration, not a preference.

**The app contains no Google code and does not have to gain any.** An App Bundle is a packaging
format, not an SDK. Play pushes back in exactly one place — the in-app language switch — and § 2.1
answers it with a build flag rather than with Play Core. That property is worth defending in
writing, because it is the kind that erodes quietly.

---

## 1. Three decisions before any work starts

### 1.1 Account type — personal or organization

| | Personal | Organization |
|---|---|---|
| Fee | US$25 one-time | US$25 one-time |
| Identity proof | valid government ID **and** a credit card, both in the legal name | **D-U-N-S number** — free, but issuance takes **up to 30 days**, and it presupposes a real legal entity |
| Public on the listing | legal name, country, developer email. **Full address only if the app monetizes** | legal name, **legal address**, developer email, developer phone |
| Device check | must confirm access to an Android device via the **Play Console mobile app** before the app can go live | not required |
| **Closed-test gate** | **12 testers, opted in continuously for 14 days**, before production access can even be applied for (personal accounts created after 2023-11-13) | **exempt** |

The app must never monetize — Fankit Agreement 2(i) forbids it permanently — so on a personal
account the owner's **postal address stays private**; only legal name, country and the developer
email become public. That is a materially smaller disclosure than it first appears.

The trade: personal costs a **14-day runway with 12 real testers**; organization costs a legal
entity, up to a month of D-U-N-S waiting, and publishes a **postal address**. "DAS KARTELL" is not
a registered entity, so organization is reachable only by founding one.

**Recommendation: personal.** The 12/14 gate is satisfiable inside the squadron and is genuinely
useful — this app has never had a supervised beta.

### 1.2 Signing architecture

Play App Signing is **not optional for new apps**; new apps are enrolled automatically with a
Google-generated key. You may instead **provide your own app signing key**, but only **before any
release reaches Open testing or Production**. The window closes; it must be used at app creation.

**Option A — recommended. Existing key becomes the app signing key; a new upload key is created.**

- The current release key (certificate `E8:40:20:5E:…:50:64`, published in the README) is exported
  with Google's **PEPK tool** and uploaded as the **app signing key**.
- A **separate upload key** is generated. Google's own guidance is that the two should differ, and
  the practical reason is decisive: an upload key can be reset by Google if it leaks; an app
  signing key you handed over cannot be un-handed.
- CI produces **two artifacts per tag** — an `.aab` signed with the **upload key** for Play, and
  the `.apk` signed with the **app signing key** for the GitHub release.
- Result: Play re-signs delivered APKs with the app signing key, so **the on-device signature is
  identical on both channels**. Existing `v0.1.x` installs update from either. The README
  fingerprint stays true. `assetlinks.json` needs no change, so the verified App Link carrying the
  OIDC redirect keeps working.
- **The price, stated plainly:** Google holds a copy of the app signing key. That is the direct
  opposite of DEV_CI § 4's posture ("self-managed, unrecoverable if lost, offline backup"). It
  needs an ADR, not a footnote.

**Option B — Google-generated key, Play-signed universal APK re-published on GitHub.** Google never
receives your key; Play Console and the Publisher API can hand back a signed universal APK, so both
channels again carry one signature — Google's. But it is a *different* signature from today's, so
**every existing install breaks**: no update path, uninstall and reinstall, local data gone. The
README fingerprint changes and `assetlinks.json` must carry the new digest *before* the first such
build ships. Only sensible if the four existing releases are written off deliberately.

**Option C — two keys, two channels, no bridge.** Cheapest to build, worst to live with: one
`applicationId`, two mutually non-updatable installations. Rejected; recorded so nobody
rediscovers it as an idea.

### 1.3 The Fan Kit disclaimer is a prerequisite

The Fankit Agreement's Ziffer 2(g) notice — the "not endorsed by or affiliated with" disclaimer —
is **missing from both the app and the web frontend**. On GitHub that is a formality. A Play listing
is judged against the **Impersonation policy**, which is about precisely that claim, and the listing
itself becomes a new surface carrying CIG trademarks (§ 5).

**Close that gap first.** It is independent of this decision and overdue on its own terms.

---

## 2. Engineering plan (repo `basetool-android`)

### 2.1 The App Bundle, and the language-split trap

Play requires an App Bundle for new apps. `bundleProdRelease` already exists in AGP; nothing has to
be invented. **One build-file change is mandatory, and it is the single easiest thing to get wrong:**

```kotlin
android {
    bundle {
        language {
            // ADR-0007: the app carries its own DE/EN switch. On API 33+ the platform asks Play
            // for a missing language split; on API 30-32 the AppCompat backport has no such
            // mechanism, and minSdk is 30. Google's bundle documentation is explicit that an app
            // with an in-app language picker must either disable language splits or fetch them on
            // demand through Play Core - and Play Core is a Google runtime dependency this app
            // will not take. So: disabled.
            enableSplit = false
        }
    }
}
```

Density and ABI splits stay at their defaults. The app ships no native code, so ABI splits are
inert either way.

Cost: every user downloads both language bundles. For two locales of a text-light app that is noise.

### 2.2 Signing — two keys, and one assertion that has to be added

[`app/build.gradle.kts`](../app/build.gradle.kts) reads four `KRT_SIGNING_*` environment variables
and **fails the build when three of four are set**. Keep that shape exactly and add a parallel set
— `KRT_UPLOAD_SIGNING_*` — with the same all-or-none check, feeding a second `signingConfig` used
only by the bundle.

**Verify before trusting: an `.aab` is JAR-signed, and the release signing config sets
`enableV1Signing = false`.** That flag governs APK signature schemes, but a bundle is a JAR and its
signature is a JAR signature. Whether AGP still signs the bundle under that config is a question to
answer in the dry run, not on release day — the failure mode is an unsigned `.aab` that builds
cleanly and is rejected at upload.

Extend `release-dry-run.yml`, which already rehearses the APK path with a throwaway key:

- build `bundleProdRelease` with a throwaway upload key;
- assert the file exists at `app/build/outputs/bundle/prodRelease/app-prod-release.aab`;
- assert it carries a signature (`jarsigner -verify`) and that the signer is the throwaway key;
- assert the bundle config disables language splits. An accidental revert of § 2.1 is invisible to
  every other check and surfaces only on a member's phone.

### 2.3 `release.yml`

The existing job already does the hard parts: decode keystore, read certificate fingerprint, build,
verify the signature *against that fingerprint*, shred the key, attest provenance, publish a draft.
Extend rather than fork:

1. Decode a **second** keystore (upload key) into the runner temp dir; shred both in the same
   `always()` step.
2. After the APK step, run `:app:bundleProdRelease` with the upload-key variables.
3. Verify the `.aab` the way the APK is verified — existence, signature, expected signer.
4. Keep the `.aab` as a **workflow artifact, not a release asset**. An `.aab` attached to a public
   release invites somebody to try to install it.
5. Attest the `.aab` alongside the APK.
6. **Do not automate the Play upload in the first iteration** (§ 2.5).

One README paragraph stops being true and must be rewritten: the published SHA-256 identifies **the
GitHub APK**. A Play install is assembled from splits on the device and has no matching digest.
What still holds for both channels is the **certificate fingerprint** — under Option A it is
identical on both, which is exactly why Option A is the recommendation.

### 2.4 What must not change

- **No Google runtime dependency.** No Play Core, no Play Services, no Firebase, no Play Integrity.
  The `licensee` allow-list (`Apache-2.0`, `BSD-3-Clause`) is the tripwire: each of those would
  fail the build, and that failure is a feature.
- **The `dev` flavour is never uploaded.** It carries cleartext permission, debug trust anchors and
  `ACCESS_LOCAL_NETWORK`. Only `prodRelease` goes to Play.
- **The `release` environment keeps its protections** — tag-restricted, required reviewer. A second
  key, and later a service-account credential, make that more important, not less.
- `versionCode` stays monotonic and identical between the `.apk` and the `.aab` of one tag.
- `targetSdk 37` already exceeds Play's floor (API 36 for new apps and updates from 2026-08-31).
  No change, and no extension to request.

### 2.5 Later, optionally: automated upload

The Google Play Android Publisher API can push a bundle to a track. It needs a Google Cloud
project, the API enabled, a **service account**, that account invited under Play Console's *Users
and permissions* with release rights, and a **JSON key** held as a secret.

That is a long-lived credential with publish rights on a store listing, living beside the signing
keys — against a channel where **every production release passes a human review anyway**. Do the
first three releases by hand. Automate only if the manual step proves to be the bottleneck, and
then pin the action by commit SHA like every other action in this repo.

### 2.6 Documentation that moves in the same PR

Non-negotiable under this repo's rules:

- **ADR** reversing plan Q1 and naming the signing trade of § 1.2 explicitly.
- `ANDROID_APP_PLAN.md` § 9 — Q1, and the Q3 sentence reading "only if Play distribution ever
  comes": Play Integrity stays **off**, but the condition it names has now occurred and the text
  has to say so.
- `ANDROID_APP_DEV_CI.md` § 4 — the key-strategy paragraph is written on the assumption that no
  Play channel exists.
- `ANDROID_APP_PRIVACY_GDPR.md` § 5, § 6 and the phase-5 rows of the § 9 checklist.
- `README.md` — distribution section and verification table.
- `CHANGELOG.md`, and the German wiki page.

---

## 3. Operator runbook — the manual steps, in order

Read each step to its end before starting it. Steps 3, 5 and 9 are hard or impossible to undo.

### Step 1 — Prepare before touching the Console

- [ ] A Google account that will own the developer account **for years**. Not a throwaway. It
      becomes a single point of failure for the listing; note it in the offline key backup.
- [ ] A credit card in the legal name (prepaid cards are refused).
- [ ] A government ID in the same legal name, ready to photograph.
- [ ] An Android device with the **Play Console app** installed and signed in with that account —
      personal accounts must confirm device access there before the app can go live.
- [ ] A **developer email address** that is monitored and can stay public for years. Not a personal
      main address if that matters to you; it is displayed on the listing.

### Step 2 — Create the developer account

1. Sign up at the Play Console with the chosen Google account.
2. Accept the **Developer Distribution Agreement**. Read § 7 of this document first — it lists the
   standing constraints the DDA and the Fankit Agreement jointly impose.
3. Pay the **US$25 one-time fee**.
4. Choose **Personal** (§ 1.1).
5. Complete identity verification: government ID + credit card. Budget **1–5 business days**.
6. Verify contact email, phone and developer email by one-time password. All three must stay
   operational for the life of the account.
7. Complete the device check in the Play Console mobile app.

**Do not create the app yet.** Step 3 has to be prepared first, and one of its options expires.

### Step 3 — Generate the upload key *(irreversible if lost)*

Offline, on the same machine and with the same discipline as the release key.

```bash
keytool -genkeypair -v -keystore upload.p12 -storetype PKCS12 \
  -alias basetool-upload -keyalg RSA -keysize 4096 -validity 10000
```

- [ ] Store it in the offline backup **next to, and clearly distinguished from, the release key**.
      Confusing the two produces an upload Play rejects with a message about the wrong certificate.
- [ ] Record its SHA-256 fingerprint in the offline notes, before it signs anything.
- [ ] Add it to the `release` GitHub environment as `KRT_UPLOAD_SIGNING_*` secrets (base64 keystore,
      store password, alias, key password), mirroring the existing four.

### Step 4 — Export the existing release key for Play *(handle once, carefully)*

- [ ] Download the **PEPK tool** from the Play Console page that offers it (Play App Signing setup;
      it is served from the Console, not from a public mirror).
- [ ] Run it against the **release** keystore — the one that signed `v0.1.0`–`v0.1.3` — to produce
      the encrypted key blob Play expects.
- [ ] Run it on a machine you trust, delete the blob after upload, and never let the plaintext
      keystore leave the offline medium.
- [ ] This is the point of no return for § 1.2 Option A. After this, Google has the key.

### Step 5 — Create the app in Play Console *(the expiring choice)*

1. **Create app.** Default language German; app name `Basetool` (≤ 30 characters). App, not game.
   Free, not paid.
2. Go to the Play App Signing setup **before uploading anything**.
3. Choose **provide your own app signing key** and upload the PEPK blob from Step 4.
   *This option disappears once a release reaches Open testing or Production.*
4. Register the **upload key** certificate from Step 3.
5. Verify in the Console that the app signing certificate's SHA-256 matches the fingerprint in the
   README, character for character. If it does not, stop — the wrong key went up.

### Step 6 — App content declarations

Answers are in § 4; this is the click order.

- [ ] Privacy policy URL → `https://profit-base.online/privacy`
- [ ] App access → restricted, with the review account (§ 4.1)
- [ ] Ads → **no ads**
- [ ] Content rating → IARC questionnaire (§ 4.4)
- [ ] Target audience → 18+ (§ 4.4)
- [ ] Data safety → § 4.2
- [ ] Advertising ID → **not used** (§ 4.3)
- [ ] Data deletion → § 4.5
- [ ] Government apps, financial features, health: **no** to all

### Step 7 — Store listing

Assets and their specifications are § 5. The Fan Kit obligations there are not optional.

- [ ] German listing (primary) and English listing
- [ ] App icon, feature graphic, phone screenshots, 7″ and 10″ tablet screenshots
- [ ] Full description carrying the Fankit 2(g) disclaimer and the § 2b trademark notice **near the
      top**, above the "read more" fold

### Step 8 — Internal testing, on yourself first

1. Upload the `.aab` from the first tagged CI run to the **Internal testing** track (up to 100
   testers, changes live within minutes).
2. Install it on a device **that already has the GitHub build installed**. It must update in place,
   without uninstalling. If Android refuses, the signing setup is wrong — go back to Step 5 before
   anything else happens.
3. Verify on that device: the DE/EN switch renders both languages fully (this is the language-split
   check, and Internal testing is the first place it can be observed), login via the App Link
   redirect completes, and the version footer shows the expected build.

### Step 9 — Closed testing: 12 testers, 14 continuous days

1. Create a **Closed testing** track. Add testers by email list or Google Group (up to 200 lists,
   2 000 addresses each; up to 50 lists per track). Every tester needs a Google account.
2. Publish, then share the opt-in link. **The link can take several hours to work** after first
   publication, and so can later changes — do not debug that in the first hour.
3. **The 14 days start when the twelfth tester has opted in**, not when the track is created. Opting
   out and back in restarts the count for that person. Track opt-ins, not installs.
4. Ask testers to actually use the app. Google evaluates engagement, not just enrolment.
5. Collect what changed as a result — you have to describe it in Step 10.

### Step 10 — Apply for production access

Available on the Console dashboard once Step 9's criteria are met. Three sections, and they are
read by a human:

- **Closed test details** — how testers were recruited, which features they used, whether behaviour
  matched production expectations, what feedback came back.
- **App information** — target audience, value proposition, estimated first-year installs. Say
  plainly that this is a companion app for a closed community; do not inflate the numbers.
- **Production readiness** — what changed because of the test, and how you concluded it is ready.

Review typically takes **seven days or less**. Rejection usually means too few testers, weak
engagement, or a policy problem; you keep testing and reapply.

### Step 11 — Production, staged

- [ ] Promote to Production with a **staged rollout**, not 100 %.
- [ ] Watch the first days: crash rate, and whether the App Link redirect works across the device
      mix. A Play rollout can be halted; a GitHub release cannot.
- [ ] Update the README: two channels, what each one guarantees, and the fingerprint that is common
      to both.

### Step 12 — Fan Kit reporting obligation

- [ ] Send the **Play listing URL** to `legal_notices@cloudimperiumgames.com`, together with the
      GitHub repository and release URLs. Fankit Agreement 2(k) requires the URL of the fan work and
      of everything redirecting to it, kept current.

---

## 4. Form answers, derived from this repository

These are drafts grounded in what the code actually does. Re-read them against the build being
submitted — **you alone are responsible for their accuracy**, and a misdeclaration is an
enforcement matter, not a correction.

### 4.1 App access — the review account

Play requires sign-in details that are **accessible at all times, reusable, and valid regardless of
location**, with **no expiring OTP or MFA**. For this app that means a dedicated Keycloak account in
the **production** realm, and four repo-specific traps that will otherwise get the app rejected as
broken:

1. **It must be approved.** An account still carrying `ROLE_PENDING_APPROVAL` lands the reviewer on
   the approval gate, which correctly refuses to show anything. The reviewer will report a
   non-functional app.
2. **The Terms of Use must already be accepted for it**, or the terms gate returns 403 before any
   screen renders.
3. **It needs an org unit and the member roles** for the screens under review — the app sends
   `X-Active-Org-Unit-Id` on every call and refuses in place where permissions are missing.
4. **It must live in its own org unit with synthetic content.** No real member data, no real
   amounts. Google's reviewers are third parties, and this repo's rule is that anything reaching a
   screenshot or a log is to be assumed leaked.

Add to the instructions field: the UI is **German by default**, the language switch is in
*Einstellungen*, and the app is a companion for an existing closed community — reviewers who
understand that stop looking for a sign-up flow.

### 4.2 Data safety

Google's definition: **collection = transmitting data off the device**, including to *your own*
server. Data processed only on-device is **not** declared.

| Item | Declaration |
|---|---|
| Name / username, email (from Keycloak) | **Collected**, not shared. Purpose: app functionality, account management |
| App activity (in-app actions against the Basetool API) | **Collected**, not shared. Purpose: app functionality |
| Crash logs / diagnostics | **Not collected** — local ring buffer, export only on explicit user action, no reporting backend (plan Q4) |
| Device or other IDs | **Not collected** — no advertising ID, no device identifier, no push token (plan Q2: no push) |
| Location, contacts, photos, messages, financial data | **Not collected** |
| Refresh token, org-unit pin, read cache | **Not declared** — processed and stored on-device only |
| Shared with third parties | **No.** There is no third-party SDK, no analytics, no ads |
| Encrypted in transit | **Yes** — TLS with certificate pinning to both Let's Encrypt roots |
| Users can request deletion | **Yes** — see § 4.5 |

**The privacy policy must match this form**, and it currently does not cover Play at all. Extend it
before submission: Google as an independent recipient of installation and store-interaction data,
third-country transfer to the USA with DPF and SCC fallback, and the distribution channel itself.

### 4.3 Advertising ID

**Not used.** The app declares no `com.google.android.gms.permission.AD_ID` permission and pulls in
no SDK that would merge one — verifiable in the merged manifest, and structurally guaranteed by the
absence of any Google runtime dependency (§ 2.4). Declare "no".

### 4.4 Content rating and target audience

- Complete the **IARC questionnaire**; it is mandatory and the app cannot publish without it.
- Answer the user-interaction questions **truthfully**: the app displays content authored by other
  members (mission descriptions, orders, announcements). It is not a public social network, but it
  is not a purely single-user tool either.
- **Target audience: 18+.** Not directed at children; no Families policy involvement.
- Retake the questionnaire whenever a feature change would alter an answer.

### 4.5 Account deletion

The requirement attaches to apps that let users create an account **from within the app, including
by directing them to a sign-up flow elsewhere**. This app dropped guest mode (Q8) and creates no
accounts; registration happens in Discord/WoltLab.

- [ ] **Check first**: does the Keycloak login page presented to the mobile client show a
      *Register* link? If it does, the requirement attaches, and both an in-app path and a web URL
      are needed.
- Either way, **declare a deletion request URL**. That is the plan's own default (§ 9, secondary
  decisions), it is cheap, and it removes an argument you would otherwise have to win.

### 4.6 Privacy policy

`https://profit-base.online/privacy` — already `permitAll`, already linked from the login screen and
from *Einstellungen*, therefore reachable to a reviewer without an account. It must be extended per
§ 4.2 before submission.

---

## 5. Store listing assets — specifications and Fan Kit obligations

| Asset | Specification |
|---|---|
| App icon | 512 × 512 px, **32-bit PNG with alpha**, ≤ 1024 KB. No badges, no text suggesting ranking or price |
| Feature graphic | 1024 × 500 px, JPEG or **24-bit PNG without alpha**. Focal point centred; no icon-like branding; no award, ranking or price claims |
| Phone screenshots | at least 2; 320–3840 px per side, longest side ≤ 2× shortest; JPEG or 24-bit PNG without alpha; 1080 × 1920 recommended |
| Tablet screenshots | at least 4 each for 7″ and 10″; 1080–7680 px. **Provide them** — the app is landscape-first on tablets and Play otherwise presents it as phone-only |
| Video | optional; YouTube URL, public or unlisted, ads disabled, not age-restricted |
| Short description | ≤ 80 characters |

Rules that bite: **no device frames**, no store badges, no people interacting with a device,
taglines confined to about 20 % of the image, and nothing implying awards, rankings or promotions.

**Fan Kit obligations that apply to the listing itself**, because a store page is fan work carrying
CIG trademarks:

- The full description carries the Agreement 2(g) disclaimer **and** the Guidelines § 2b trademark
  notice, near the top, above the fold Play introduces.
- Guidelines § 2 requires the *Made By The Community* logo in the corner of images that use Fan Kit
  content or Star Citizen branding, at ≥ 50 % opacity and legible size — screenshots included.
  The § 2a alternative ("This is an unofficial Star Citizen Fan Site") **requires prior approval by
  CIG's legal department** via the Fankit site; that has a lead time and is not a self-declaration.
- The app name stays `Basetool`. "Star Citizen" in the title would breach Agreement 2(d)/2(e) and
  Play's Impersonation policy in the same stroke.
- Screenshots must be captured against the test stack or a synthetic org unit. **No real member
  names, no real amounts.**

---

## 6. Timeline and gates

| Phase | Duration | Gate to the next |
|---|---|---|
| Fan Kit disclaimer in app + web | days | merged and released |
| Decisions § 1.1–1.2, ADR written | — | owner sign-off |
| Repo work § 2.1–2.3 | ~2 days | dry run proves a signed `.aab` and the language-split assertion |
| Account creation + identity verification | 1–5 business days | verified |
| App creation + signing setup | hours | app signing certificate matches the README fingerprint |
| App content + store listing | days (screenshots dominate) | all declarations complete |
| Internal testing | days | **update-in-place over a GitHub install works** |
| Closed testing | **≥ 14 days after the 12th opt-in** | criteria met |
| Production access application | ≤ 7 days review | approved |
| Staged production rollout | days | crash rate and App Link behaviour hold |

Realistic total from decision to production: **six to eight weeks**, dominated by the closed test.
Do not compress the closed test; it is the one gate with no appeal.

---

## 7. Standing constraints once the listing exists

These outlive the project that created them.

- **Never monetize.** No in-app purchases, no ads, no subscriptions, no donation link, no paid
  tier. Fankit Agreement 2(i) forbids revenue "of any kind" in connection with CIG material; a
  breach terminates the licence immediately (clause 5).
- **Never add a Google SDK.** § 2.4 is a permanent rule, not a launch-time one.
- **Keep the Fan Kit surface at one asset.** The manufacturer logos still listed as an open item in
  the plan would become additional CIG trademarks in public screenshots.
- **Keep the target API current.** Play's floor moves annually; falling behind removes the app from
  new users on current devices.
- **Report URL changes to CIG** under Agreement 2(k).
- **Expect CIG to be able to withdraw permission at any time** (clauses 2(h), 6). On GitHub that
  means deleting a release. On Play a trademark complaint runs through Google's IP process and can
  escalate to a **policy strike against the developer account** — which is the risk this channel
  adds and no amount of preparation removes.

---

## 8. Sources

Read on 2026-08-27; each is the official page, not a summary of it.

- Get started with Play Console (fee, account types, age, ID) — `support.google.com/googleplay/android-developer/answer/6112435`
- Required information to create a developer account (public display, D-U-N-S) — `…/answer/13628312`
- App testing requirements for new personal developer accounts (12 testers / 14 days, production application) — `…/answer/14151465`
- Use Play App Signing (upload vs app signing key, PEPK, own key, key upgrade) — `…/answer/9842756`
- Set up an open, closed or internal test (tracks, tester limits, propagation) — `…/answer/9845334`
- Data safety section (collection vs sharing, off-device rule, enforcement) — `…/answer/10787469`
- Account deletion requirement — `…/answer/13327111`
- Sign-in details for review (App access) — `…/answer/15748846`
- Store listing graphic assets (icon, feature graphic, screenshots, video) — `…/answer/9866151`
- Content rating questionnaire (IARC) — `…/answer/9859655`
- Impersonation policy — `…/answer/9888374`
- Advertising ID — `…/answer/6048248`
- Target API level requirements (API 36 from 2026-08-31) — `developer.android.com/google/play/requirements/target-sdk`
- Configure the base module / bundle splits (`language.enableSplit`) — `developer.android.com/guide/app-bundle/configure-base`
- Google Play Android Publisher API getting started — `developers.google.com/android-publisher/getting_started`
- Android developer verification (Sept 2026 regional, 2027 global; affects the GitHub channel too) — `developer.android.com/developer-verification`
- Fankit Agreement 2025-11-19 and Fan Kit Guidelines, from the owner's Fan Kit archive
