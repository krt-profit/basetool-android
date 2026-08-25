# Basetool Android

Native Android companion app for the **Profit Basetool** — the squadron-management tool of
the "DAS KARTELL" Star Citizen organization. Kotlin + Jetpack Compose, phones
portrait-first, tablets landscape-first, minSdk 30 (Android 11), dark-only DAS KARTELL
design.

**Status: phase 3 complete (2026-08-23).** The theme, the component library, the navigation shell,
the auth flow (login, approval gate, terms, app lock) and the settings screen ship from phase 1; the
member's read surface — Übersicht, Einsätze, Operationen, Aufträge, Lager, Bank, Hangar, Posteingang
— from phase 2; and the app now **writes**: Mein Inventar and the Blueprints, the Hangar's own
ships, the Lager's bookings, an Auftrag's assignment and status, an Einsatz's participation and
money, and a bank account's settings. `./gradlew check` is green. Live parity, the Materialbörse,
the Raffinerie and the file imports are phase 4. The owner-approved concept lives in
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
app/                  manifest, flavors, auth flow, navigation shell, settings          [built]
core/common/          logging facade                                                     [built]
core/designsystem/    KRT Compose theme, components, icon set, Lato fonts                [built]
core/network/         OkHttp client, mandatory headers, problem+json → app states        [built]
                      (SSE still planned; DTOs live in core/contract, no Retrofit — ADR-0008)
core/contract/        the backend's committed openapi.json + the models generated from it  [built]
core/auth/            Keystore token store, DPoP, token client, PKCE login + session     [built]
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

| | Answers |
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
