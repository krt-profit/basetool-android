# Basetool Android

Native Android companion app for the **Profit Basetool** — the squadron-management tool of
the "DAS KARTELL" / IRIDIUM Star Citizen organization. Kotlin + Jetpack Compose, phones
portrait-first, tablets landscape-first, minSdk 29 (Android 10), dark-only DAS KARTELL
design.

**Status: Phase 1 in progress.** The theme and the component library from chapter 02 of the
design specification are implemented and building (`./gradlew check` is green); the navigation
shell, the auth flow and the feature screens follow. The owner-approved concept lives in
[`docs/`](docs/):

| Document | Content |
|---|---|
| [`ANDROID_APP_PLAN.md`](docs/ANDROID_APP_PLAN.md) | Master plan: goals, verified toolchain baseline, architecture, feature map, roadmap, resolved decisions Q1–Q7 |
| [`ANDROID_APP_SECURITY.md`](docs/ANDROID_APP_SECURITY.md) | Threat model, API exposure, Keycloak client + DPoP posture, layered abuse prevention, release gate |
| [`ANDROID_APP_PRIVACY_GDPR.md`](docs/ANDROID_APP_PRIVACY_GDPR.md) | GDPR / TDDDG / German-law analysis and the compliance checklist |
| [`ANDROID_APP_DEV_CI.md`](docs/ANDROID_APP_DEV_CI.md) | Local dev/test environment, hardened GitHub CI, release signing |
| [`docs/design/android/`](docs/design/android/README.md) | **Binding UI specification** (design handoff, 2026-08-17): chapters 00–14, `artifacts/Theme.kt`, icon export list, fonts. Open `00 Index.dc.html` in a browser. |
| [`ANDROID_APP_DESIGN_PROMPT.md`](docs/ANDROID_APP_DESIGN_PROMPT.md) | Historical: the Claude Design brief that produced the specification above |

Key properties, decided up front: consumes the existing Basetool backend API only (no own
business logic) · distribution via **GitHub Releases** (no Google Play) · **zero third-party
data flows** — no analytics, no tracking, no Firebase, crash logs stay on-device ·
consent-banner-free by design · admin area stays web-only.

## Module layout

```
app/                  manifest, flavors, component showcase (navigation shell follows)   [built]
core/common/          logging facade                                                     [built]
core/designsystem/    KRT Compose theme, components, icon set, Lato fonts                [built]
core/network/         OkHttp client, mandatory headers, problem+json → app states        [built]
                      (OpenAPI-generated DTOs, Retrofit services, SSE still planned)
core/auth/            AppAuth flow, Keystore token store, DPoP, session state            [planned]
core/data/            repositories, Room cache, org-unit context                         [planned]
feature/…             one module per area (missions, orders, inventory, bank, …)         [planned]
docs/                 concept docs, binding design spec, ADRs, specs
```

## Build

Requires JDK 25 and the Android SDK (platform 37). `./gradlew check` runs tests, Android Lint,
detekt and Spotless; `./gradlew :app:installDevDebug` puts the component showcase on a device.
The toolchain has a few non-obvious constraints — see the *Toolchain landmines* section in
[`CLAUDE.md`](CLAUDE.md) before changing build files.

Contributor ground rules: [`CLAUDE.md`](CLAUDE.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).
Server-side counterpart work lives in the main `basetool` repository.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) — one-time [CLA](CLA.md) signature
(roster: [`docs/cla-signatures.md`](docs/cla-signatures.md)), per-commit DCO sign-off
(`git commit -s`), and the [Contributor Covenant 3.0](CODE_OF_CONDUCT.md).

## Star Citizen

This is an unofficial fan project ("Made By The Community") and is not affiliated with or
endorsed by Cloud Imperium. Star Citizen®, Roberts Space Industries® and Cloud Imperium ®
are registered trademarks of Cloud Imperium Rights LLC.

## License

[GPL-3.0](LICENSE.md), like the main Basetool repository.
