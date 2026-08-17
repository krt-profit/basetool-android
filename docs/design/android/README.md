# Handoff: Profit Basetool — Android Companion App

## Overview
Complete UI specification for the native Android companion app of the **Profit Basetool**
(squadron-management tool of DAS KARTELL / Bereich Profit, Star Citizen). Target stack:
**Kotlin + Jetpack Compose, Material 3, minSdk 29 / targetSdk 37**, phones portrait,
tablets landscape (list-detail). Dark-ONLY — there is no light theme; Material You dynamic
color is deliberately disabled.

## About the design files
The `.dc.html` files in this bundle are **design references created in HTML** — interactive
prototypes showing intended look and behavior. They are NOT production code and must not be
shipped. The task is to **recreate these designs in Compose/Material 3** inside the new
Android module, using the mappings in `artifacts/Theme.kt` and the rules below.
Open `00 Index.dc.html` in a browser (keep the folder structure intact — the pages load
`_ds/…` CSS, `assets/…` and `support.js` relatively). `Spec Print Edition.html` is the
compact paper digest of everything.

## Fidelity
**High-fidelity.** Colors, type, spacing, states and copy are final and binding — recreate
pixel-perfectly (1 CSS px in the mockups = 1 dp). The only deliberate placeholders are the
manufacturer lettermarks (see Assets).

## Binding sources & precedence
1. **DAS KARTELL design system** (`krt-profit/design-system`, mirrored in `_ds/…` here) —
   colors, Lato-only type, radius 0, HUD brackets, button ladder. Never invent outside it.
2. **This spec** (chapters 00–14 + `artifacts/Theme.kt`) — the Android adaptation.
3. **`krt-profit/basetool`** web app — behavioral parity source (i18n strings in
   `frontend/src/main/resources/messages_de.properties`, icon sprite in
   `templates/fragments/icons.html`, permission model in `ROLES_AND_PERMISSIONS.md`).
   `github.md` in this bundle maps every screen to its repo sources.

## Chapters (each = one screen area, phone 412×915 + tablet 1280×800 + handoff notes)
| File | Contents |
| :-- | :-- |
| 01 Foundations.dc.html | M3 colorScheme/typography/shapes mapping, spacing, icon inventory |
| 02 Components.dc.html | Every component in default/pressed/focus/disabled/error states; Fan Kit band §9 |
| 03 Navigation.dc.html | Bottom bar (5), tablet rail (7+Mehr), Mehr list, back rules, deep links |
| 04 Auth.dc.html | Login (Keycloak Custom Tab + Discord + guest), approval pending, terms, app-lock |
| 05 Dashboard.dc.html | Übersicht: greeting, announcement, Einsätze ≤7 d, 4 quick actions, unread |
| 06 Missionen.dc.html | Einsätze/Operationen list, detail w/ 7 tabs, signup sheet, finance form, payouts |
| 07 Benachrichtigungen.dc.html | Inbox, swipe actions, 50-cap + load more, badge states |
| 08 Hangar.dc.html | Ship cards, add/edit, org overview, Fleetview import |
| 09 Lager.dc.html | Stock tree, book in/out/rebook, allocation split (Variante C/Modell G), blueprints |
| 10 Auftraege.dc.html | Order queue, detail w/ 4 tabs, Materialbörse offers/requests |
| 11 Raffinerie.dc.html | Orders w/ yields, detail, „In Lager buchen", extractor import |
| 12 Bank.dc.html | Accounts + sparkline, account detail, booking request, approvals |
| 13 Einstellungen.dc.html | Beförderung matrix + settings incl. app-lock, language, danger zone |
| 14 System States.dc.html | Offline, 409/429/503, forced update, in-fiction errors, push shade, adaptive icon |

## Design tokens
Everything is coded in **`artifacts/Theme.kt`** — drop-in `darkColorScheme`, `Typography`
(Lato Light 300 body / Bold 700 labels / Black 900 heroes, sp sizes + letterSpacing),
`Shapes` (all 0 dp), `KrtExtendedColors`, spacing object, motion constant. Key rules:
- `secondaryContainer = #E77E23`, `onSecondaryContainer = #000` → every M3 selection
  surface renders the brand rule „selection = orange bg + black text"
- `surfaceTint = surface`, tonalElevation 0 dp app-wide — flat surfaces, depth = 1 dp
  hairlines (#282828) + orange corner brackets + bloom (never drop shadows)
- `error = #F2564B` (text tint), `errorContainer = #A3000A` (fill) — the *-text tints are
  mandatory whenever a semantic hue is small text on black (WCAG AA)
- Orange = action + identity: THE one filled CTA per context; labels neutral #D2D2D2 bold,
  data values white on dark chips, buy prices red −, sell prices green +
- Uppercase via `text.uppercase()` at call sites (Compose has no text-transform);
  numeric readouts `fontFeatureSettings = "tnum"`; font scale 1.3× without truncation

## Interactions & behavior (system-wide)
- Navigation: `NavigationSuiteScaffold` — bottom bar ≤5 / rail on expanded; SQUARE 56×32 dp
  orange indicator (never the M3 pill); per-destination back stacks; predictive back;
  back on a root → Übersicht; deep links per ch. 03 table (cold start synthesizes Übersicht)
- Motion: 200 ms color/fade only, no bounce/parallax, honor reduced motion
- Touch ≥48 dp; dense rows ~56 dp; pull-to-refresh on every list (orange ring spinner)
- Overlays: KRT modal (3 dp orange top edge, 13 dp brackets, ONE filled CTA right, ghost
  cancel; danger variant names the consequence) — never native dialogs; bottom sheets
  shape 0 with 3 dp orange edge; toasts with corner brackets; undo toast 5 s for swipes
- Offline: cached banner + „Cache" chips; write actions disable at 45% — never queue
  mutations offline (append-only ledgers). 409 → conflict dialog preserving input;
  429/503 full-screen retry only on first load (backoff 3/6/12/30 s, honor Retry-After)
- Live sync: presence pill (never blocks input) + „Aktualisierung verfügbar" pill; badge
  and inbox fresh via polling + SSE (web parity)
- Auth: Keycloak in a Custom Tab (toolbar #141414, never WebView); Discord IdP fail-closed;
  approval-pending polls 60 s; terms block until accepted; app-lock = custom KRT lock
  screen under BiometricPrompt, FLAG_SECURE app-wide

## State management (per screen — details in each chapter's handoff card)
Screen state survives navigation + process death via `SavedStateHandle` (scroll, filters,
`?tab=`). Optimistic writes with `version` (optimistic locking) → 409 dialog. Countdown
texts relative („in 2 Std."), re-rendered each minute.

## Copy rules (binding, from product owner)
German-first, military-terse, UPPERCASE labels, **no emoji**. „**Einsätze**" (never
„Missionen"), „**Bereich Profit**" as org context, „**Administration**" (never „Führung").
Error states keep the EN in-fiction canon (403 „Access Denied — Insufficient security
clearance…", 404 „Signal Lost…", 500 „System Malfunction…", CTA „Zurück zur Basis").
All strings externalized (DE default, EN full) — reuse the web `messages*.properties` keys
where they exist.

## Legal (mandatory)
**Fan Kit compliance band** (ch. 02 §9): unmodified white „Made By The Community" artwork
(36 dp, `assets/made-by-the-community.png`) + byte-exact notice
`Star Citizen®, Roberts Space Industries® and Cloud Imperium ® are registered trademarks of Cloud Imperium Rights LLC`
— one inseparable component, verbatim ENGLISH in every locale, ≥14 sp #D2D2D2, static,
no KRT styling. Placements: Login (above version footer) + Einstellungen. Nowhere else.
**Logo rule:** the KRT mark renders ONLY in #E77E23, white or black.

## Assets
- `assets/krt-icons-mobile.js` — full icon sprite: product set verbatim from
  `fragments/icons.html` + 20 mobile extensions (same contract: 24 dp, stroke 2, round
  caps, currentColor). Export as VectorDrawables per `artifacts/icon-export.md`
- `assets/krt.webp` — KRT mark (raster; adaptive-icon vector per ch. 14 geometry)
- `assets/made-by-the-community.png` — official artwork, use unmodified
- Lato WOFF2/TTF in `_ds/…/fonts/` — bundle as app fonts (Light/Regular/Bold/Black)
- **Open item:** manufacturer logos (Anvil/Drake/MISC) exist in the repo only as SVGs with
  embedded rasters (`META-INF/resources/images/*_white.svg`) — re-export clean vectors;
  until then the spec's lettermark placeholder IS the design
- `assets/android-frame.jsx`, `support.js`, `doc-page.js` — prototype plumbing only, ignore

## Suggested implementation order
1. Theme + tokens (`Theme.kt`), icon VectorDrawables, Lato fonts
2. Component library (ch. 02): buttons ladder, chips/pills, rows, forms, modal/sheet/toast,
   empty/loading/offline, Fan Kit band
3. Navigation shell (ch. 03) + auth flow (ch. 04)
4. Screens 05 → 13 in chapter order; system states (ch. 14) alongside
5. Adaptive icon + notification channels (ch. 14)

## How to hand this to Claude Code
Place this folder in the repo (suggested: `docs/design/android/` or alongside
`.claude/skills/das-kartell-design/`), then start Claude Code in the repo and prompt e.g.:
> Lies docs/design/android/README.md vollständig. Erarbeite daraus einen Implementierungs-
> plan für das neue Android-Modul (Kotlin, Compose, M3) und setze ihn schrittweise um —
> beginne mit Theme.kt und der Komponentenbibliothek aus Kapitel 02. Die .dc.html-Dateien
> sind Design-Referenzen (im Browser öffnen), nicht zu portierender Code. Halte dich strikt
> an die Copy- und Legal-Regeln im README.
