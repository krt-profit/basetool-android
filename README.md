# Basetool Android

Native Android companion app for the **Profit Basetool** — the squadron-management tool of
the "DAS KARTELL" Star Citizen organization. Kotlin + Jetpack Compose, phones
portrait-first, tablets landscape-first, minSdk 30 (Android 11), dark-only DAS KARTELL
design.

**Released: [v0.1.3](https://github.com/krt-profit/basetool-android/releases/latest).** Install it
through Obtainium or straight from the release page — and check what you installed before you do
(see [Installing, and checking what you installed](#installing-and-checking-what-you-installed)).

A member can do everything the plan set as the bar for a first release: read Übersicht, Einsätze,
Operationen, Aufträge, Lager, Bank, Hangar, Mein Inventar, Materialbörse, Raffinerie and the
Posteingang — and write to them. Sign up for an Einsatz and book its money, book stock in and out,
take an Auftrag and change its status, keep your own ships and blueprints, offer and request on the
Materialbörse, book a refining yield into the Lager, and clear the inbox. Data updates live while
the app is in front; on a tablet the list sits beside its detail.

**What it deliberately does not do.** The administration stays in the browser, permanently and by
decision — roles, members, catalogues, mission planning — as does the bank-employee view. There is
no push channel, so notifications reach you only while the app runs; that is the cost of not routing
them through Google. Beförderung is absent for a different reason: it is built and tested but has no
design chapter, so it is withheld ([#66](https://github.com/krt-profit/basetool-android/issues/66),
[ADR-0009](docs/adr/0009-tablet-settings-ships-without-its-befoerderung-column.md)). The file
imports of the Desktop-Extractor come later.

The app needs the server side of `basetool` **v1.6.0 or newer**.

The owner-approved concept lives in [`docs/`](docs/):

| Document | Content |
|---|---|
| [`ANDROID_APP_PLAN.md`](docs/ANDROID_APP_PLAN.md) | Master plan: goals, verified toolchain baseline, architecture, feature map, resolved decisions Q1–Q7. Its **roadmap is spent** — the phases it lays out shipped in v0.1.0 — but Q1–Q7 stay binding and are not to be reopened silently |
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
app/                  every screen, the auth flow, the navigation shell, settings
core/common/          logging facade
core/designsystem/    KRT Compose theme, components, icon set, Lato fonts
core/network/         OkHttp client, mandatory headers, problem+json → app states, SSE
                      (DTOs live in core/contract, no Retrofit — ADR-0008)
core/contract/        the backend's committed openapi.json + the models generated from it
core/auth/            Keystore token store, DPoP, token client, PKCE login + session
core/data/            repositories and the org-unit context
docs/                 concept docs, binding design spec, ADRs, specs
```

`feature/` was planned as one module per area and stayed empty: the screens live in `app/`. The
split was never worth its cost at this size, and an empty directory promising otherwise is worse
than none — it is kept only so the intent is findable if the app ever outgrows one module.

## Build

Requires JDK 25 and the Android SDK (platform 37). `./gradlew check` runs tests, Android Lint,
detekt and Spotless; `./gradlew :app:installDevDebug` puts the dev flavour on a device — it carries a second launcher entry with the component showcase, which the release build does not.
The toolchain has a few non-obvious constraints — see the *Toolchain landmines* section in
[`CLAUDE.md`](CLAUDE.md) before changing build files.

Contributor ground rules: [`CLAUDE.md`](CLAUDE.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).
Server-side counterpart work lives in the main `basetool` repository.

### What CI checks

Every pull request runs `./gradlew build` — the same command as above plus `assemble`, so the
gate and the working copy cannot drift apart — together with CodeQL (`java-kotlin` and
`actions`), a range-scoped secret scan, dependency review, and a DCO sign-off check on every
commit the PR adds. A **release-signing dry run** builds and verifies a signed release APK with a
key generated inside the run and shredded with it, so the signing path is exercised continuously
rather than once per release with the one key that cannot be regenerated. The workflows themselves are linted by actionlint and zizmor, and every
third-party action is pinned to a full commit SHA. Details and the deliberately-still-open gates:
[`ANDROID_APP_DEV_CI.md`](docs/ANDROID_APP_DEV_CI.md) § 4.

## Installing, and checking what you installed

Distribution is **GitHub Releases plus [Obtainium](https://github.com/ImranR98/Obtainium)**. There
is no Play Store listing, which means none of the checks a store would perform happen — so the
release publishes everything needed to make them yourself.

**Three things travel with every release**, and each answers a different question:

| What ships with the release | Answers |
|---|---|
| the APK's **SHA-256**, in the release notes | is this the file that release built? |
| the **signing certificate's SHA-256**, below and in the notes | did it come from us? |
| a GitHub **build attestation** | did *this* workflow, on *this* commit, produce it? |

The certificate fingerprint is the one that matters most and the one to bookmark. Android refuses an
update signed by a different key, so a changed fingerprint is either a key rotation announced here
first, or an APK that is not ours.

```
Signaturzertifikat SHA-256:
E8:40:20:5E:EC:16:F5:FD:CD:BA:8B:44:81:18:06:3C:4A:37:E6:16:20:99:CC:49:00:DF:23:80:C1:AF:50:64
```

Recorded when the release key was generated, before it signed anything — so it is the value a
release is checked *against*, not one copied out of a release. Compare it with the fingerprint in
any release's notes; they must be identical.

The attestation is checkable offline with the GitHub CLI:

```bash
gh attestation verify basetool-<version>.apk --repo krt-profit/basetool-android
```

**The app pins its TLS.** All three production hosts are pinned to the two Let's Encrypt roots
(`app/src/main/res/xml/network_security_config.xml`); what that does and does not protect against,
and how to rotate it, is in [`docs/ANDROID_APP_SECURITY.md`](docs/ANDROID_APP_SECURITY.md) § 5.1.

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
