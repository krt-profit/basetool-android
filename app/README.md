# app

Application module: manifest, flavors, window setup and — for now — the component showcase.

- **Flavors**: `dev` (local test stack, debug trust anchors, `.dev` application id) and `prod`
  (production hosts, system trust only). See `docs/ANDROID_APP_DEV_CI.md` section 6.
- **`ShowcaseActivity`**: renders the full `:core:designsystem` component library so it can be
  compared against `docs/design/android/02 Components.dc.html` on a real device, at real
  densities and font scales. It is a development surface, not a product screen, and is replaced
  by the navigation shell and the feature screens as they land (design chapters 03 onwards).
- Backup rules exclude the future token store from both cloud backup and device-to-device
  transfer in the API-30-and-below *and* the API-31-and-above rule sets.

Build: `./gradlew :app:assembleDevDebug` · install: `./gradlew :app:installDevDebug`.

The launcher icon is deliberately absent: the adaptive icon is chapter 14 of the design handoff,
and the KRT mark may not be improvised (logo rule — orange, white or black only).
