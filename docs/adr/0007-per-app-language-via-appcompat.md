# ADR-0007 — The in-app language is the platform's per-app language, not a preference of ours

- **Status:** Accepted
- **Date:** 2026-08-20
- **Deciders:** @greluc
- **Related:** ADR-0006 (minSdk 30), `REQ-APP-SET-002`, design chapter 13

## Context

Design chapter 13 gives Einstellungen a two-segment DE/EN control. The app therefore needs to
switch its own language independently of the device's, and to remember that choice across
restarts.

Android has an API for exactly this — `LocaleManager`, per-app language — but only from
**API 33**. The minSdk floor is 30 (ADR-0006), so on Android 11 and 12 there is no platform
mechanism at all. Three ways to bridge that gap were considered.

**Storing the tag ourselves and wrapping the activity's base context.** About sixty lines:
persist a tag, override `attachBaseContext`, build a `Configuration` with the chosen locale, and
call `recreate()` on change. It works, and it has a defect that only shows up later — on API 33+
the *platform* also stores a per-app language, shown in Android's own "App languages" settings
screen. A member who changes the language there would be changing a value this app ignores, and
the app would keep rendering the tag from its own store. Two sources of truth that disagree,
with no error anywhere.

**AndroidX AppCompat's backport.** `AppCompatDelegate.setApplicationLocales` forwards to
`LocaleManager` on API 33+ and emulates it below, persisting into SharedPreferences when the app
declares `autoStoreLocales`. One store, one API, the system settings screen included. The cost
is that the backport applies only to AppCompat components: below API 33 it recreates *active
AppCompat delegates*, so the activity must be an `AppCompatActivity` rather than the
`FragmentActivity` that `BiometricPrompt` requires. `AppCompatActivity` extends
`FragmentActivity`, so this is an addition rather than a swap — but it does mean the activity
theme has to descend from `Theme.AppCompat`, or the activity refuses to start.

**Waiting for minSdk 33.** Not a real option: the floor was raised to 30 four days ago for a
security reason, and raising it again for a convenience would cost devices the app for a feature
that has a working backport.

The plan document (`ANDROID_APP_PLAN` §2) already named the AppCompat backport. This ADR records
why, and what it costs, because the two consequences below are not obvious from the one line.

## Decision

**The per-app language is stored by the platform, read and written through
`AppCompatDelegate`, and the app keeps no copy of it.**

- `LanguageSetting` is a thin façade over `AppCompatDelegate.getApplicationLocales()` /
  `setApplicationLocales()`. There is no DataStore entry, no `AppLanguage` on disk.
- `MainActivity` becomes an `AppCompatActivity`, and `Theme.Basetool` reparents from
  `android:Theme.Material.NoActionBar` to `Theme.AppCompat.NoActionBar`.
- `AndroidManifest.xml` declares `AppLocalesMetadataHolderService` with
  `autoStoreLocales=true`, which is what makes the choice survive a cold start on API 30–32.
- `androidx.appcompat` becomes a declared dependency. It was already present transitively — it
  arrives with `androidx.biometric` — so this pins the version rather than adding a library.
- What the control **shows** is the language on screen, not the stored tag: with nothing pinned
  it resolves the device's locales against the two bundles, falling back to German because that
  is the default bundle Android itself falls back to (`AppLanguage.resolve`).

## Consequences

**One source of truth, including the one outside the app.** Changing the language in Android's
settings and changing it in Einstellungen now write the same value, and each screen shows what
the other did. That is the whole reason for the dependency.

**An AppCompat theme sits under a Compose app.** Nothing above the window background is drawn by
AppCompat — every pixel is Compose — but `Theme.Basetool` now has an AppCompat ancestor, and
changing it back would break the activity at runtime rather than at build time. The theme file
says so.

**The activity is recreated on every language change.** Deliberate, and the reason the screen
needs no refresh logic: the recreated activity reads the new bundle. The segmented control keeps
its own state for the frame in between so the tap feels immediate.

**Below API 33 the choice is invisible to the system.** Android's "App languages" screen does not
list apps that only use the backport, so on Android 11 and 12 Einstellungen is the only place to
change it. That is a platform limit, not one this decision adds.

**Rejected: a DataStore preference of our own** (two stores that disagree on API 33+),
**a hand-rolled `attachBaseContext` wrapper** (same defect, plus reimplementing what AndroidX
maintains), **raising minSdk to 33** (costs devices for a convenience).
