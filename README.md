# Basetool Android

Native Android companion app for the **Profit Basetool** — the squadron-management tool of
the "DAS KARTELL" / IRIDIUM Star Citizen organization. Kotlin + Jetpack Compose, phones
portrait-first, tablets landscape-first, minSdk 29 (Android 10), dark-only DAS KARTELL
design.

**Status: concept phase — pre-implementation.** The owner-approved concept lives in
[`docs/`](docs/):

| Document | Content |
|---|---|
| [`ANDROID_APP_PLAN.md`](docs/ANDROID_APP_PLAN.md) | Master plan: goals, verified toolchain baseline, architecture, feature map, roadmap, resolved decisions Q1–Q7 |
| [`ANDROID_APP_SECURITY.md`](docs/ANDROID_APP_SECURITY.md) | Threat model, API exposure, Keycloak client + DPoP posture, layered abuse prevention, release gate |
| [`ANDROID_APP_PRIVACY_GDPR.md`](docs/ANDROID_APP_PRIVACY_GDPR.md) | GDPR / TDDDG / German-law analysis and the compliance checklist |
| [`ANDROID_APP_DEV_CI.md`](docs/ANDROID_APP_DEV_CI.md) | Local dev/test environment, hardened GitHub CI, release signing |
| [`ANDROID_APP_DESIGN_PROMPT.md`](docs/ANDROID_APP_DESIGN_PROMPT.md) | Self-contained Claude Design prompt (KRT tokens, components, screens) |

Key properties, decided up front: consumes the existing Basetool backend API only (no own
business logic) · distribution via **GitHub Releases** (no Google Play) · **zero third-party
data flows** — no analytics, no tracking, no Firebase, crash logs stay on-device ·
consent-banner-free by design · admin area stays web-only.

## Planned module layout

```
app/                  wiring, navigation, DI graph
core/designsystem/    KRT Compose theme, tokens, component library
core/network/         OkHttp/Retrofit, OpenAPI-generated DTOs, problem+json, SSE
core/auth/            AppAuth flow, Keystore token store, DPoP, session state
core/data/            repositories, Room cache, org-unit context
feature/…             one module per area (missions, orders, inventory, bank, …)
docs/                 concept docs, ADRs (docs/adr/), specs (docs/specs/)
```

The Gradle scaffold lands with Phase 1 of the [roadmap](docs/ANDROID_APP_PLAN.md#6-phased-roadmap).
Contributor ground rules: [`CLAUDE.md`](CLAUDE.md). Server-side counterpart work lives in the
main `basetool` repository.

## License

[GPL-3.0](LICENSE.md), like the main Basetool repository.
