# ADR-0015 — minSdk 31, so the lowest supported version is one we can actually test

- **Status:** Accepted
- **Date:** 2026-08-27
- **Deciders:** @greluc
- **Related:** ADR-0006 (minSdk 29 → 30), `REQ-APP-AUTH-002`, `REQ-APP-AUTH-004`

## Context

ADR-0006 raised the floor from 29 to 30 because API 29 forced a second, weaker app-lock path. This
one raises it again for a different reason: **the floor was not testable.**

The `Pixel_5_x64` emulator (API 30, `google_apis`) could never complete a sign-in. Measured against
the local test stack:

| API | Image | Chrome | Signs in |
| --- | --- | --- | --- |
| 30 | `google_apis` | 83 | ❌ |
| 30 | `google_apis_playstore` | 83 | ❌ |
| **31** | `google_apis` | **91** | ✅ |
| 37.1 | `google_apis_playstore` | 149 | ✅ |

**Why 83 fails.** Every cookie Keycloak sets on the auth endpoint is `Secure; HttpOnly;
SameSite=None`, and the emulator reaches it over plain HTTP on `127.0.0.1`. Chrome accepts a
`Secure` cookie over HTTP only on a trustworthy origin, and that localhost exception arrived in
**Chrome 89**. Chrome 83 drops it, Keycloak never receives `AUTH_SESSION_ID` on the form POST, and
answers `cookie_not_found`.

**Why the Play Store image did not rescue it.** It ships the same Chrome 83; it only makes an
update *possible*, and that needs a Google account signed in on the emulator. It also takes away
`adb root` — and with it the scripted locale — and its stock Chrome ignores
`/data/local/tmp/chrome-command-line`, so even skipping the first-run screen becomes a human step.
Google publishes no mapping of Chrome version to system image, so all of this had to be measured.

The consequence was not theoretical. The minSdk class could verify rendering and nothing behind a
session, which is most of the app. **The first authenticated run at API 31 immediately found a
crash that had been shipping**: `setUnlockedDeviceRequired(true)` cannot create its key on a device
with no secure lock screen, `KeyGenerator.generateKey` throws `java.security.ProviderException`, and
that type extends `RuntimeException` rather than `GeneralSecurityException` — so it walked past a
handler that had already been written for exactly that situation (`REQ-APP-AUTH-002`).

## Decision

**`minSdk = 31`** (Android 12).

The reach argument does not motivate this and is not claimed: a real Android 11 phone has an
auto-updating Chrome — Chrome has required Android 10 or newer since version 139 — and signs in
without trouble. Android 11 users are dropped, and nothing they experienced was broken.

What is bought is that **every supported API level can run the full test suite against a real
session**. A floor that cannot be exercised is a compatibility claim nobody has checked, and the
defect above is what that costs.

## Consequences

- The three device classes become **API 31 (floor)**, **API 37 (current phone)**, **API 37
  (tablet)**. The API-30 AVDs and the Play Store variant are removed.
- The floor emulator is a `google_apis` image again, so `adb root` works: the German locale and the
  Chrome first-run flag are scriptable, as they were before the Play Store detour.
- **`backup_rules.xml` is now inert** — `android:fullBackupContent` is read only by API ≤ 30. It is
  **deliberately kept** rather than deleted: it costs nothing, and it is the belt beside the braces
  on the one artefact that must never leave the device (`REQ-APP-AUTH-004`). Deleting it would make
  a future lowering of the floor silently unprotected.
- Two comments narrow from "API 30–32" to "API 31–32": the `AppCompatDelegate` locale backport is
  still needed, because the platform per-app locale service arrives at API 33.
- The delivered design specification still states "minSdk 30, never 29" as a binding correction
  (`docs/design/android/README.md`, item 1). That text is the design side's and is regenerated with
  each bundle, so it is raised with them rather than edited here.

## Alternatives considered

- **Stay at 30 and accept an untestable floor.** Rejected: it is the status quo that hid the crash,
  and the next such defect would be found the same way — by a member.
- **Stay at 30 and update Chrome on a Play Store AVD.** Possible, but it needs a Google account on
  the emulator, cannot be scripted, breaks on every fresh AVD, and still leaves `adb root` gone.
  A per-machine manual step is not a test floor.
- **Raise to 33**, where `cmd locale set-app-locales` returns and the locale is scriptable without
  root. Rejected: that is tooling convenience, and it drops two more Android versions for it.
