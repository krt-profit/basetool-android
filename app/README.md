# app

Application module: Hilt DI graph, navigation shell (bottom bar on compact width, navigation
rail + list-detail on expanded), flavors (`dev` = test stack + debug trust, `prod` = real
hosts, no custom trust), manifest, adaptive app icon. Gradle scaffold lands in Phase 1
(`docs/ANDROID_APP_PLAN.md` §6).

Manifest / platform hardening owned by this module (`docs/ANDROID_APP_SECURITY.md` §4):
`android:taskAffinity=""` (StrandHogg task-affinity hijack), a minimal set of `exported`
components, `FLAG_SECURE` on every authenticated screen, and `filterTouchesWhenObscured` on
sensitive confirm actions (tapjacking — complements `setHideOverlayWindows`). Backup exclusion
is declared in **all three** rule sets (legacy `fullBackupContent` plus `dataExtractionRules`
with excludes in both `<cloud-backup>` and `<device-transfer>`), because minSdk 29 spans both
worlds and `allowBackup=false` alone does not reliably stop device-to-device transfer.
