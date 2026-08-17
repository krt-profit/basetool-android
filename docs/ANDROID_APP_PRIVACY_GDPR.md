# Android App — Data Protection Analysis (EU + Germany)

Doc type: **living plan** (draft, pending approval by @greluc). Legal state verified against live
primary sources on **2026-08-17**. This is a structured compliance analysis, not legal advice; for
contested points a lawyer or the competent supervisory authority decides.

Profile: German community operator ("DAS KARTELL"/IRIDIUM), members-only app; personal data =
forum-sourced usernames/display names, Keycloak `sub`, org roles/memberships, in-game inventory,
finance and activity data, optional Discord link; no ads, no analytics, no tracking; self-hosted
backend in the EU (Hetzner); distribution per open decision Q1 (GitHub Releases and/or Google
Play).

## 1. Roles and legal bases

- **Controller (Verantwortlicher)**: the operator of the Basetool (the org / @greluc as operator)
  — same controller as for the web app; the Android app is a new *access channel*, not a new
  processing purpose. The existing Datenschutzerklärung structure extends rather than duplicates.
- **Legal bases (Art. 6(1) GDPR)**: membership administration and squadron operations →
  **(b) contract/membership** for members' own data, **(f) legitimate interest** for org
  administration surfaces (rosters, org charts) — identical to the web app. New app-specific
  processing (push tokens, crash reports) would each need their own basis and only exist if the
  corresponding open decision approves the feature; push endpoint registration should be
  **opt-in (consent, Art. 6(1)(a))** given it is not strictly necessary for the service.
- **Processors**: none new in the baseline design (self-hosted stack). Google becomes involved
  **only** if Q1/Q2/Q3 decisions adopt Play/FCM/Play Integrity — consequences in §5.

## 2. § 25 TDDDG (device storage) — the app can run consent-banner-free

Legal frame: § 25(1) TDDDG requires consent for storing/reading information on the device;
§ 25(2) Nr. 2 exempts what is **strictly necessary** ("unbedingt erforderlich") to deliver a
service the user explicitly requested. The DSK **Orientierungshilfe Digitale Dienste v1.2
(11/2024)** expressly covers native apps; EDPB **Guidelines 2/2023** (v2.0, 10/2024) add that
purely local information that **never leaves the device** is not even in scope of Art. 5(3) ePD.

| Stored on device | Assessment |
|---|---|
| Keycloak tokens after deliberate login | strictly necessary for the requested login service (OH: login areas incl. authentication are user-requested; security storage protecting login expressly listed as necessary) — **no consent** |
| Settings (language, theme-independent prefs) | necessary; store values, not unique IDs (OH para 81) — **no consent** |
| Offline read cache of content the member requested | necessary for the requested service; additionally local-only until display — **no consent** |
| App-lock keys (Keystore) | necessary for the user-requested lock feature — **no consent** |
| Anything for analytics/tracking/ads | **would require consent — not planned; keep it that way** |

Design consequence: **zero consent banners** as long as we add no analytics/tracking. This is a
feature; guard it in review (any SDK addition re-triggers this analysis).

## 3. Art. 13 GDPR — in-app privacy notice (Datenschutzerklärung)

Required at first collection, reachable from login screen and settings, DE (+ EN courtesy):
controller identity + contact; purposes + legal basis per purpose; recipients (Google LLC **only
if** FCM/Play Integrity approved); third-country transfers + safeguard (see §5); retention
periods/criteria (mirror server-side rules, e.g. read notifications swept after 90 days); data
subject rights incl. complaint right to the supervisory authority; whether provision is required;
no automated decision-making. The existing web `privacy.html` + DE/EN bundles are the template —
the app gets its own screen (offline-capable copy) linking the canonical version.

**Also triggered by the server-side exposure work**: the new `api.profit-base.online` vhost's
access log joins the 31-day client-IP retention set — the repo's monitoring rules already
condition that on the **privacy-policy extension**. That extension ships with the exposure PRs
(web `privacy.html` + bundles), independent of app-store questions.

## 4. Impressum, records, TOMs, DPIA, DPO

- **Impressum**: § 5 DDG full-Impressum duty attaches to "geschäftsmäßige, in der Regel gegen
  Entgelt" services — arguably not this app; **but § 18(1) MStV** requires name + ladungsfähige
  Anschrift for every service that is not purely personal/family. A members-only org app serves
  an association purpose → **ship an Impressum screen** (name, address, contact). Costs nothing,
  moots the classification argument, matches the web app.
- **Art. 30 Verzeichnis von Verarbeitungstätigkeiten**: required. The Art. 30(5) small-operator
  exemption fails because member-data processing is "nicht nur gelegentlich" (DSK Kurzpapier
  Nr. 1). Action: add/extend the VVT entry for the Basetool with the app channel (categories,
  purposes, recipients, deletion periods, TOM reference).
- **TOMs (Art. 32)**: document the app-specific measures — Keystore token encryption, backup/D2D
  exclusion, FLAG_SECURE, TLS + (Phase 5) pinning, short token lifetimes, DPoP sender-
  constraining, kill switch, no third-party SDKs — alongside the existing server TOMs.
- **DPIA (Art. 35)**: **not required.** Nothing in the profile matches the DSK Muss-Liste
  (no scoring, no large-scale tracking/geolocation, no special categories, small closed user
  group). Action: file a one-page negative threshold assessment with the VVT.
- **DPO (Datenschutzbeauftragter)**: not required below 20 persons constantly processing
  (§ 38 BDSG) — n/a for this operator.

## 5. Third-country transfers — only if Google services are approved

Baseline design transfers **nothing** outside the self-hosted EU stack. If open decisions adopt
Google services, the state of play (verified 2026-08-17):

- **EU-US Data Privacy Framework**: adequacy decision in force; General Court dismissed the
  Latombe challenge (T-553/23, 2025-09-03); **appeal C-703/25 P pending at the CJEU** — usable
  today, but keep **SCCs as documented fallback** (Firebase DPST §10 already incorporates them).
  Google LLC is DPF-certified (EU-US + Swiss + UK extension).
- **FCM** (only if Q2 chooses it, incl. the UnifiedPush embedded-FCM fallback): Google acts as
  processor under the **Firebase Data Processing and Security Terms** (accept them = the AVV);
  FCM uses Firebase installation IDs (deletable, 180-day purge) and offers **no EU data
  residency**; payloads transit Google. Mitigations if adopted: data-only "tickle" messages or
  RFC 8291-encrypted payloads (UnifiedPush does this natively), `ttl=0`, opt-in consent before
  registering any push endpoint, Art. 13 notice + (if Play) Data-safety declaration.
- **Play Integrity** (only if Q3 approves): **not** under the Firebase DPST — data handling rides
  the Google Play ToS; treat Google as an independent recipient in the notice; device/app
  integrity telemetry incl. key-attestation certificate and Play licensing status flows to
  Google. The `deviceRecall` feature (persistent device identifier) must stay **off**.
- **Server-verified Key Attestation** (the recommended Google-lean alternative): the device sends
  its attestation chain to **our** backend; the backend fetches Google's public CRL — no personal
  data flows to Google. No transfer issue.

## 6. Google Play obligations (only if Q1 includes Play)

- **Data safety form**: mandatory; must cover SDK-collected data (Play services/Firebase if
  present). Misdeclaration is an enforcement risk — derive it from the §7 dependency inventory.
- **Privacy policy URL** on the listing **and** in-app.
- **Account deletion requirement**: applies to apps that let users create accounts or direct
  users to signup. Basetool accounts originate in WoltLab/Discord outside the app; if the app
  merely consumes existing accounts, the duty arguably doesn't attach — but the cheap, robust
  path is to provide the web deletion-request URL (account deletion exists in the Basetool admin
  flow) in the form. Decide at store submission.
- Direct-APK distribution (GitHub Releases) carries **none** of these Play duties; GDPR/TDDDG/
  Impressum obligations apply identically either way.

## 7. Biometric app-lock and Art. 9

The optional app-lock uses `BiometricPrompt`: enrollment and matching happen inside the device's
TEE/SE; raw biometric data and templates **never reach the app or the operator** (verified Android
security architecture docs); the app receives only an authentication result + keystore key
release. The operator therefore does not process special-category biometric data (Art. 9 applies
to processing *for the purpose of uniquely identifying a person* — a local OS-mediated boolean is
not that). Posture: keep it **optional** with device-credential fallback, and state in the privacy
notice that biometric verification happens exclusively on-device by the OS. (No EDPB/DSK document
decides the exact controller question; EDPB practice endorses local-only biometrics kept
optional.)

## 8. Data minimization & retention in the app

- Cache only what the member's screens need; TTL-bound; wiped on logout and via a settings
  action ("Lokale Daten löschen"). Backup/D2D excluded (see security doc §4).
- No device identifiers collected or transmitted; no advertising ID (declare
  `AD_ID`-permission-free build if Play is chosen). Permissions: `INTERNET`; `USE_BIOMETRIC`
  (optional app-lock, on-device only); `DETECT_SCREEN_RECORDING` (API 35+, normal install-time
  permission for the capture warning); `POST_NOTIFICATIONS` only if a push/notification
  decision lands; the `dev` flavor additionally declares `ACCESS_LOCAL_NETWORK` (API 37
  enforcement) — never the release build.
- App logs: local ring buffer, no names/emails/tokens (mirrors REQ-OBS-004), export only by
  explicit user action (relevant to crash-reporting decision Q4).
- Data subject rights are served by the existing server-side processes (the app adds a
  settings link to the operator contact).

## 9. Action checklist (compliance work items by phase)

| Phase | Item |
|---|---|
| 0 | Extend web `privacy.html` + DE/EN bundles for the new API vhost access logs (31-day IP retention) |
| 1 | In-app screens: Datenschutzerklärung, Impressum (§ 18 MStV), OSS licenses, operator contact |
| 1 | VVT entry + TOM addendum + one-page DPIA negative assessment filed |
| 2–3 | Re-run the § 25 TDDDG table on every new stored artifact (review gate) |
| 4 | If push approved: consent flow before endpoint registration; Datenschutz section for the channel (repo precedent: mail-disabled ↔ privacy coupling); DPST acceptance if FCM |
| 5 | If Play: Data-safety form from dependency inventory; deletion-URL decision; store listing privacy URL |
| 5 | Final legal review of notice texts (DE primary, EN courtesy) |
