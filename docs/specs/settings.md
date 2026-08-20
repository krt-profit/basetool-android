> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-08-20.
> **Owner area:** SET · **Related:** design chapter 13 (`docs/design/android/13 Einstellungen.dc.html`),
> [`auth.md`](auth.md) (`REQ-APP-AUTH-010`, the app lock this screen switches on),
> ADR-0007 (per-app language), main repo `REQ-UI-018` (Fan Kit placement)

# Einstellungen — language, legal texts and the app's own switches

Design chapter 13 draws two screens: Beförderung and Einstellungen. Only the second is specified
here; Beförderung reads evaluations from the backend and lands with the live-parity phase.

## Requirements

### REQ-APP-SET-001 — The screen shows only what the app itself decides

Einstellungen carries the settings the **app** owns: the language, the app lock, the legal texts,
the open-source notice, the version, and sign-out. The chapter also draws the member's rank and
squadron, the active org unit, the payout preference and a blueprint-sharing switch — every one of
those is a value the **backend** owns, and none of those endpoints is consumed yet.

They are left out rather than drawn from placeholder data. A settings screen showing a rank nobody
set is not an unfinished feature, it is a wrong statement about the member, and the one place a
member checks when they think something is wrong is exactly this screen.

The same reasoning removes the chapter's **"Lokale Daten löschen"** for now: there is no offline
cache, so the button would delete nothing while its own modal promises otherwise. A destructive
control that does nothing teaches members to distrust the ones that do. It lands with the first
read cache (`offline.md`, planned), together with the wipe semantics it belongs to.

The account row shows the **username from the ID token** and nothing else, because that is the one
identity fact the app holds without asking the backend, and on a shared device "who is signed in
here" is worth answering.

**Acceptance**

- [x] Konto (username), App (Sprache, App-Sperre), Rechtliches & Daten (Datenschutzerklärung,
  Impressum, Nutzungsbedingungen, Open-Source-Lizenzen), the Fan Kit band, sign-out and the version
  are rendered; nothing else is.
- [x] No value on the screen comes from a placeholder constant.
- [ ] Rank, active org unit, payout preference and blueprint sharing. **Open** — they arrive with
  the read-only member core, each with the endpoint that feeds it.
- [ ] "Lokale Daten löschen". **Open, and deliberately so** — it needs something to delete first.
- [ ] The version footer's **server-status dot and API version**. **Open** — both describe the link
  to the backend, and the app has no health signal to read them from. An always-green dot would be
  decoration that reads as a diagnosis.

### REQ-APP-SET-002 — The in-app language is the platform's per-app language

The DE/EN control writes through `AppCompatDelegate.setApplicationLocales`, which is the platform
`LocaleManager` on API 33+ and AppCompat's backport below (ADR-0007). **The app keeps no copy of
the choice.** A second store would disagree with Android's own "App languages" screen the moment a
member changes the language there, and neither side would report an error.

What the control **highlights is the language on screen, not the stored tag**, and those are
different facts until the first tap: with nothing pinned, the device's locales decide, and anything
the app has no bundle for resolves to German because that is the bundle Android itself falls back
to. A control that showed "nothing selected" while the member reads German would be accurate about
the store and useless about the app.

Applying a language recreates the activity — the platform does it above API 33, AppCompat below —
which is what re-reads every string without the app restarting itself.

**Acceptance**

- [x] A pinned language wins over the device's; with nothing pinned the device decides
  (`AppLanguageTest`).
- [x] `de-AT`, `de-CH` and `de` all resolve to German, matching the bundle Android loads.
- [x] An unsupported device language resolves to German rather than to "no selection".
- [x] The segment order is pinned by a test, because the screen maps the control's index straight
  onto `AppLanguage.entries` and a reordering would silently swap both segments' meaning.
- [x] The choice survives a cold start on API 30–32 (`autoStoreLocales` in the manifest).
- [ ] Verified on a device at the minSdk floor **and** on API 33+, since the two use different
  mechanisms and only the second appears in Android's system settings. **Open.**

### REQ-APP-SET-003 — Every user-visible string is a resource, navigation titles included

The i18n rule is not new; what the language switch changes is that breaking it is now **visible**.
`KrtDestination` carried its title as a Kotlin literal, so the bottom bar, the rail, the top bar and
the "Mehr" list would have stayed German for a member who switched the app to English — the largest
and most obvious surface in the app.

Titles are `@StringRes` ids. The same move covers the org-switcher sheet title and the placeholder
screen's message.

Domain terms stay German in the English bundle (Einsätze, Aufträge, Lager, Raffinerie,
Materialbörse, Beförderung): they are the squadron's vocabulary, not words to translate.

**Acceptance**

- [x] No `@Composable` in `:app` renders a string literal as user-visible text.
- [x] Both bundles carry the same keys and the same format placeholders (`StringsParityTest`).
- [x] Domain terms are identical in both bundles.

### REQ-APP-SET-004 — The app lock is switched on here

`REQ-APP-AUTH-010` owns the lock's behaviour; this requirement owns where it is switched. The row
sits under "App", shows in words what the current state means rather than repeating the value, and
is **disabled rather than hidden** on a device with no screen lock — with the subtitle naming the
one thing the member can do about it.

It lived under "Mehr" until this screen existed, which was a stopgap and is recorded as one.

**Acceptance**

- [x] The toggle is on the settings screen and no longer in "Mehr".
- [x] Arming raises the biometric prompt immediately (design handoff, ch. 13).
- [x] Without a device lock the row is present, disabled, and says why.
- [x] The subtitle states the effect ("beim Start und nach 5 Minuten im Hintergrund"), not the
  boolean.

### REQ-APP-SET-005 — The legal texts are the web app's, opened in a browser

Datenschutzerklärung, Impressum and Nutzungsbedingungen open the web frontend's `/privacy`,
`/impressum` and `/terms` in a **Custom Tab** — never a `WebView` (`REQ-APP-AUTH-005`), and never a
second copy maintained in the app. One document per text, for web and app alike: two copies drift,
and the one that drifts is the one nobody is reading while the lawyer reads the other.

All three are `permitAll` on the frontend, which is what lets the **login screen** link to them
before anyone has signed in. That placement is not cosmetic: the privacy notice has to be available
before processing begins, and processing begins with the sign-in tap. Both buttons were empty stubs
until this change.

**Acceptance**

- [x] The three rows open the web pages of the current flavour (`BuildConfig.WEB_BASE_URL`).
- [x] The login screen's Datenschutz and Impressum buttons open the same two documents.
- [x] External rows carry the external-link glyph, not a chevron — the member is told the browser
  is about to open.
- [ ] A device with no browser at all. **Open** — `CustomTabLauncher` already reports it, but
  nothing surfaces the report on this screen yet.

### REQ-APP-SET-006 — The open-source notice is generated, and the build refuses what it cannot name

The notice is produced from the **dependency graph of the exact variant being built**: Licensee
resolves the runtime classpath, and `app/build.gradle.kts` copies its report into
`res/raw/oss_licenses.json` as a generated resource. A hand-written attribution list is wrong the
first time a transitive dependency changes, and wrong silently.

Two gates keep it honest, and they are two halves of one decision:

- **`licensee { allow(…) }` fails the build** on an artifact whose licence is not allowed. It runs
  as part of `check`, so a transitive dependency arriving under a copyleft or an unknown licence is
  a red build rather than a shipping decision nobody made.
- **`OssLicense` must name and address every allowed identifier**, or the screen would print a bare
  SPDX string. `OssLicensesTest` fails when the generated report contains one the enum does not
  know, and when any artifact falls out of the grouping entirely — the failure mode that looks
  exactly like success.

Each artifact is listed with its **exact version**, because the terms apply to the code that
shipped. Licence texts are opened at their canonical addresses rather than bundled, which keeps a
legal text from drifting out of date inside an APK.

**Acceptance**

- [x] The notice is generated into the APK and is not empty (`OssLicensesTest`).
- [x] Every identifier in it has a name and a canonical URL.
- [x] No artifact drops out of the grouping; one offered under two licences is listed under both.
- [x] An unlisted licence fails `check` (verified: `BSD-3-Clause` failed the build before it was
  allowed).
- [ ] Whether GPL-3.0-only distribution requires the full text **bundled** rather than linked has
  not been reviewed by anyone qualified. **Open** — the current form matches common practice; it is
  recorded here rather than assumed settled.

### REQ-APP-SET-007 — The Fan Kit band appears here, and here is one of exactly two places

The "Made By The Community" artwork and the CIG trademark notice render as one coupled unit
(`KrtFanKitBand`). The Fan Kit Guidelines fix the placement at the login screen and Einstellungen —
nowhere else — and neither half of the unit may be moved or removed on its own.

**Acceptance**

- [x] The band is rendered above sign-out on this screen.
- [x] It is not added to any further screen by this change.
