# Handoff: Profit Basetool — Android Companion App

## Overview
Complete UI specification for the native Android companion app of the **Profit Basetool**
(squadron-management tool of DAS KARTELL / Bereich Profit, Star Citizen). Target stack:
**Kotlin + Jetpack Compose, Material 3, minSdk 31 / targetSdk 37**, phones portrait,
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

## Compose implementation package

`artifacts/compose/` is the design system as Kotlin: `KrtTokens.kt` (palette incl. the text
tints, spacing, sizes, RectangleShape everywhere, Lato typography, the Material 3
`darkColorScheme` mapping), `KrtGlow.kt`, `KrtComponents.kt`, `KrtFieldsAndOverlays.kt`,
`KrtPatterns.kt` and its own README with the ten rules, the permission-gate recipe, a
web-class → composable table and the two design-system defects not to mirror. Start there, not
from the chapters: the chapters say what a screen shows, that package says what it is built from.

**Glow is capped (29.08.2026).** One scale, three sizes, and nothing may exceed radius 12 dp /
alpha 0.10: focus 6 dp / .10, emphasis 12 dp / .07, overlay 12 dp / .10. Every glow in all 18
chapters was brought down to it (69 declarations), `KrtGlow.krtGlow` refuses anything larger, and
elevation stays 0.dp on every container — depth is hairlines and corner brackets, never shadow.
**Design-system finding (report upstream, corrected in the mirror):** the loud blooms did not live
in the chapters at all — they are stylesheet tokens. `colors_and_type.css` defined
`--glow-primary` at 5px/.30, `--glow-primary-lg` and `--glow-danger-lg` at 20px/.20; the
component sheet added literals at 10px/.30 (`.scu-hint__bubble`), a 55 % presence keyframe and
0.45–0.5 alpha dropdown shadows. The mirror now carries the capped scale plus a new
`--glow-primary-emphasis` (12px/.07) for the CTA hover, with the caps written into the token
block as a comment. The web repo should take the same values.
   Two things the first pass missed, both worth knowing when auditing CSS: the bloom on
   `.scu-hint__bubble` was authored **colour-first** (`rgba(...) 0 0 10px`, later in percentage
   syntax) so a blur-first search never saw it — it now uses `var(--glow-primary-lg)`; and the
   `--shadow-dropdown` / `--shadow-drawer` TOKENS were left loud while their literal equivalents
   were softened, so the same popover archetype had two strengths. **Black separation shadows are
   now one value: alpha 0.30, blur ≤ 12 px**, and they exist only to lift a popover or drawer off
   what is behind it — never to suggest elevation. On Android none of them are ported: Compose
   popovers sit on hairline borders with no shadow at all.

## Round 12 — the deviation register, worked through (30.08.2026)

The app was verified artboard-by-artboard against the running code; the register it produced is
answered in full. **Chapter 18 „Abweichungs-Register" is the map** — it says for every item where
its correction lives, and it carries the answers to §C/§D/§G/§H plus the three surfaces that had no
artboard anywhere.

* **§A (11 · drawn, no wire field)** — dropped or redrawn around what the endpoint really takes:
  Operation has no time fields and no Einsatz picker (ch. 06 ab. 15); `bulk-checkout` carries
  `itemIds` and nothing else (ch. 09 ab. 20); the item offer has `productKey/quantity/remark` only
  and the item request carries **`minQuality`**, which no artboard drew — ab. 2 is redrawn around
  it (ch. 17); the material row's subtitle is the **category** (ch. 16); „Herstellung" now draws a
  **per-material plan plus the Einlagerung the endpoint requires**, and loses the variant and
  handover fields that do not exist (ch. 10 ab. 15).
* **§B (9 · the stated rule was wrong)** — corrected at the artboard, each marked „Korrektur
  (Runde 12)" in yellow: over-pledging is **refused**, not allowed (B1); the refinery's
  post-storage lock and delete refusal are **the app's rules, not the server's** (B2/B3 → G3);
  `bulk-checkout` is **atomic**, there is no partial outcome (B4); the exchange **amount is
  editable** and there is **no undo** after withdrawing (B5/B6); approval limits live in **one
  account's settings**, not a fifth tab, with **four** tiers and a „kein Limit" state (B7); the
  Lager tree is **material-only** so a game-item row opens in place (B8 → G4); `displayId` is a
  plain integer — „#A-1042" is now „#1042" everywhere (B9).
* **§C (8)** — all confirmed. C7 is no longer blocked: the picker now exists (F1).
* **§D (12)** — all ratified; the drawing follows the app. D1 and D3 pulled through into ch. 06 and
  ch. 08, D7 noted in ch. 11. The offline banner's timestamp and CACHE chip are **struck**, not
  deferred — the app holds no cache and measures no load time.
* **§E** — E1–E3 drawn in ch. 18 (cross-order material demand, the two-step blueprint file import,
  and a selection mode so „alle löschen" has an entry point at all); E4–E9 ratified there with the
  composition decided; **E10/E11 answered as rules in ch. 01** (one spacing scale, three tablet
  answers — every full-screen surface picks one of the three).
* **§F** — both drawn: **F1 the date/time picker** as a new component (ch. 02 §11: field pair,
  date grid, time steppers, range variant) with the rule that no member ever types a timestamp;
  **F2 starting an Einsatz from the status badge**, outline not filled, because „Anmelden" keeps
  the one filled orange (ch. 06).
* **§G** — the six backend asks are recorded in ch. 18 in priority order, **G3 first**: it is the
  only one with a data-integrity edge, because today a direct API call can still rewrite a booked
  refinery run's goods.

## Round 13 — nine internal contradictions, resolved (30.08.2026)

Building chapter 18 surfaced nine items, six of them places where the spec contradicted itself.
Each is now settled at ONE place, and the value the app already uses is the one that survived
wherever the app was right:

* **A1/A2 — sizes.** Ch. 02 §1's old framing line („all interactive heights ≥ 48 dp") read as a
  floor and cost a real defect: the field frame derived its **height** from the touch token, so
  lowering the floor to 44 shrank every input. Now three tokens, stated once: `controlHeight` 48 dp,
  `navIconFloor` 48 dp, `touchTarget` **44 dp as a minimum tap area only**. §11's date/time pair is
  48 dp — matching the field was its own stated intent.
* **A3 — one spacing scale.** §8 named a second, narrower scale (`xs 6 · sm 10 · md 16 · lg 20 ·
  xl 24`) that covered neither the artboards nor its own margin table. §5's strip is the source and
  now carries the three steps the table needed: **4 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 32**,
  mirrored verbatim in `KrtSpacing.s4 … s32`. Renaming would have moved ~830 call sites onto values
  no artboard uses.
* **A4/A5 — ch. 18's own text.** E4's section rhythm applies to the **Verwaltung** tab's four
  (Kern · Zeitplan · Sichtbarkeit · Personen); Ziele and Ablauf belong to the Ablauf tab. E5 counted
  five row actions where there are **six** — the tick was missed, and it stays **visible outside**
  the overflow because it is the checklist row's primary action.
* **A6 — `KrtFieldWarning` ratified.** The system had no warning-toned inline field message; §11
  needed one. Same shape as the error line, #FFD23F, and the distinction is written down: error
  blocks, warning does not, never both at once.
* **B1 — „im Lager frei" struck.** `MaterialDemandRowDto` carries no stock field; joining
  `/inventory/aggregated` would need an unbounded page-walk and still report the *total*, because
  that read knows nothing about claims. The row now shows **claimed / handed over** (both in the
  DTO). Backend ask **G7** (`freeStock`) brings the line back.
* **B2 — a fourth figure.** The import preview answers **five** statuses; `SUGGESTED` rows carry
  `productKey = null` and need a human pick. Ratified: the app does **not** take them, counts them
  as „Zu klären" and says the pick happens in the web portal. Auto-accepting a top suggestion would
  write a choice nobody made; dropping them silently loses importable rows.
* **C1 — `ic_krt_plus` for „duplizieren"** (ratified). The in-house set has no duplicate glyph and
  the system forbids inventing one in application code; plus is the true statement — duplicating
  appends a row — and the overflow shows the label beside it.
* **`#464646`** is now a **ratified fifth grey with exactly one call site**: a neighbouring month's
  day in the date grid. Tappable, but must not read as the active month; #646464 is too close and
  #282828 looks disabled.

## Round 14 — 31 items from running the app on a device (31.08.2026)

Every screen and state opened on three emulators and held against its **rendered** artboard rather
than the chapter's prose — which is the only way half of these show up: a caption can be correct
while the picture beside it is not.

* **Drawn without a wire field — struck, with an ask.** Ziele are not tickable (S2 → G8); HVU is a
  property of the *Einheit*, not a person (S7, moved); the UEX freshness stamp has no field
  (S16 → G11); a Auftrag has no due date (S21 → G9); no endpoint carries a rank (S26 → G12); a
  refinery order has no displayId, so its card leads with **station + start time** (S15 → G10); the
  Lager's material/location chips cannot narrow a paged aggregate read (S11 → G13); no
  unauthenticated read names the configured identity providers, so the Discord button stays
  undrawn (S18 → G14).
* **Rules that needed a number.** Frequencies are two decimals — the server validates
  `@Digits(3,2)` and refused every drawn value (S5 → G15 to widen it); the per-head share divides
  by **payout takers**, and the label names the divisor (S6); the demand figure's tint is three
  thresholds on the *outstanding share of required* — &lt;30 % white, 30–99 % yellow, 100 % red (S22);
  the KPI figure is headlineMedium/Black 20 (S12); an icon button is 48 dp, the Ablauf move buttons
  the one ratified 40 × 44 exception (S3).
* **One look per component.** Filter chips: hairline off, orange on — no status tone, no dashed
  off-state (S9). Data chips uppercase, value keeps the brighter weight (S20). The status badge has
  **four** parts — tint, hairline, 3 dp leading edge, square dot, white label; the stylesheet draws
  all four and Compose had dropped two (S24). The org pill's tone follows the unit's **kind**, not
  the column it sits in (S28). The lifecycle word appears once, in the band (S4).
* **Where an action lives.** Ablauf row: tick · ↑ · ↓ · ⋮, and the picture is finally redrawn to
  match its round-12 caption (S1). Another member's payout: row overflow (S8). A single ship:
  the editor sheet the pencil opens (S14). The priority band stays until the queue's drag exists
  (S23). The server-status dot is struck in Einstellungen — no health endpoint — and kept on the
  login screen where the gate check answers it (S27).
* **Beförderung** is out of ch. 03's „Mehr", ch. 13 is titled „Einstellungen", and the promotion
  artboard carries a struck-through banner — it stays as *history*, because the handoff is a record
  of what was delivered, not a rewrite (S19, S25). **Organigramm** is removed too: nothing draws it,
  no endpoint is near it — it was the web menu's entry.
* **Tablet, three open layout questions, decided.** The Materialbörse is two columns of **cards**
  with the segment kept (S29); **Operationen is the second half of the Einsätze surface**, reached
  from the rail with the same list-detail answer (S30); and ch. 02 §5's rule extends to **list
  rows** — a phone card becomes a row in a tablet's list column (S31).
* **S10 is not a design gap:** the local stack runs a ten-day-old backend image, so
  `MissionListDto.registeredCount` is in the contract but not on the wire yet. The artboard stands.

## Fidelity
**High-fidelity.** Colors, type, spacing, states and copy are final and binding — recreate
pixel-perfectly (1 CSS px in the mockups = 1 dp). The only deliberate placeholders are the
manufacturer lettermarks (see Assets).

## Corrections carried in this bundle
Regenerations of this bundle have repeatedly dropped the items below. They are factual
corrections, not preferences — preserve them verbatim in every re-export.
1. **minSdk 31, never below 30** (floor raised to 31 by owner instruction 27.08.2026; ADR-0006
   had already raised it to 30 and deleted the API-29 path): on API 29 the only
   auth-bound Keystore key is time-bound — no CryptoObject accepts it and Cipher.init throws
   until an authentication already exists, so the app lock can neither be armed nor opened.
   The API-29 path was deleted; a spec saying 29 invites reinstating a path that cannot work.
2. **`--color-gray-2-text: #8A8A8A` stays in `_ds/…/colors_and_type.css`** (directly after
   `--color-gray-2`): #646464 fails WCAG AA as small text on black (~3.5:1). Android mirrors
   the token as `KrtPalette.TextMuted` — without it the mirror has no source, and the next
   "reconciliation" moves the app back to a colour below the contrast floor.
3. **Ch. 04 login offers exactly two entries** — Keycloak (Custom Tab) and Discord (binding:
   Q8 in ANDROID_APP_PLAN.md, REQ-APP-AUTH-007/008); the footer carries Datenschutz + Impressum
   only (consent is forced and versioned at first login). Do not add further entry points or
   footer links when regenerating.
4. **`assets/basetool-logo.svg` ships in the bundle**: `ic_launcher_foreground.xml` and
   `krt_basetool_logo.xml` name it in their header comments as the artwork they were traced
   from — the raster and favicon variants do not replace the source geometry.
5. **Fleetview import root is a JSON ARRAY** — the object form {"ships": [...]} gets 400
   ("must contain a JSON array at the root"). Server-named formats: CCU Game Fleetview,
   HangarXPLOR Shiplist, Fleetyards JSON (ch. 08.3, verified against the running stack).
6. **Locked-but-tappable permission pattern** (ch. 09, artboards 11–14): actions the caller
   provably may not perform render disabled-STYLE but stay tappable and name the missing role
   from ROLES_AND_PERMISSIONS.md ("Dafür brauchst du die Rolle Logistiker.") — never hidden,
   never Compose enabled=false. Roles come from /api/v1/users/me (roles/permissions) and
   realm_access.roles — the app must consume them, not discard them.
7. **No push promises anywhere** (decision Q2: the app has no push channel): the approval
   screen polls every 60 s — a promised notification that cannot arrive is copy that lies (ch. 04.3).
8. **Ch. 09 „Umbuchen" is the TRANSFER contract** (InventoryItemBookOutDto): three target
   fields — member, place, org-unit pool (/users/{id}/memberships?allKinds=true, preset to the
   row's current unit) — plus mergeStock (SCU only; PIECE always merges), guarded inline by
   "must change user or location". Personal ↔ shared is a SEPARATE endpoint
   (POST /inventory/{id}/personal-rebook, only in Mein Lager; UI wording = Umbuchungsart „Als persönlich umbuchen" / „Ins gemeinsame Lager umbuchen"). Never redraw them as one field.
9. **Ch. 11 „In Lager buchen" is a per-material FORM** (RefineryOrderStoreItemDto): amount
   overrides the calculated yield (the point of the screen), allocation happens AT store time,
   personal × jobOrderId are mutually exclusive (400), personal rows get no mission earmark.
10. **No extractor-import affordance on Android** (ch. 11, decision 27.08.2026): the extractor
   handoff is a desktop→browser ingest consumed once — the phone create form is manual.
11. **Wording follows the web frontend verbatim** (frontend messages_de.properties): „Raffinerieauftrag"
   (never „Order"), „Einlagern" (store), „Halter" + „Verwahrung" (bank), „Bestätigen" (request
   approval), „Umbuchen zu Nutzer/Ort" (transfer targets), Umbuchungsart „Als persönlich umbuchen" /
   „Ins gemeinsame Lager umbuchen", „Herkunft der Menge" with „Marken" and „Vom Rest", „Buchungen",
   „Massenumbuchung". Never invent app-only terms; no guest mode exists anywhere.
12. **The Fan Kit band carries THREE coupled elements** (Fankit Agreement clause 2(g), cumulative
   with Guidelines §2b): logo + §2b line + the 370-char 2(g) notice, byte-exact:
   "This site is not endorsed by or affiliated with the Cloud Imperium or Roberts Space
   Industries group of companies. All game content and materials are copyright Cloud Imperium
   Rights LLC and Cloud Imperium Rights Ltd.. Star Citizen®, Squadron 42®, Roberts Space
   Industries®, and Cloud Imperium® are registered trademarks of Cloud Imperium Rights LLC.
   All rights reserved."
   „Ltd.." keeps its two stops; 2(g) has NO space before its four ® while §2b keeps one before
   its third ® — never harmonise (tests pin both byte-exact plus the spacing-difference
   assertion). Never folded behind a tap; both notices 14 sp; the login PAGE scrolls,
   Einstellungen shows the full paragraph.
13. **Round-7 app audit — Einsätze (ch. 06, artboards 6–14, 29.08.2026).** Checked against
   basetool-android@main. Six deviations to fix in the app, each with its repo location in the
   findings card of ch. 06:
   a) `MissionDetailScreen.kt:587` hides the Verwaltung tab (`filter { canManage || it != ADMIN }`)
      — the binding rule is locked-but-tappable, never hidden; the app's own Bank does it right.
      A tap on the locked tab does NOT open it, it raises the role toast (artboard 6).
   b) The four schedule timestamps are free `KrtTextField`s — must be Datum+Zeit pairs, and
      "tatsächlicher Start" is a state line plus an action, not a text field (artboard 8).
   c) `KrtRadioRow` used for the booleans "Nur intern" and "HVU" — booleans are square
      checkboxes; the round radio stays reserved for one-of-N (artboard 10).
   d) `CrewAdd` offers the whole roster as `KrtFilterChip`s — must be one `.assoc-add` surface
      opening a roster picker (artboard 14).
   e) `MissionAdminUi.Hint` uses `KrtPalette.Gray2` (#646464) as text — fails AA; use
      `TextMuted` (#8A8A8A) as the same helper in `MissionStructureUi` already does.
   f) `StepRowActions` stacks up to five full-width buttons per row — three 44 dp icon buttons
      (aria-label + title) plus drag-handle reordering with a click fallback (artboard 13).
   Also newly drawn because the code says it is unratified: the Verwaltung tab as folded
   panel-header sections (7), the start confirmation (9), per-section save/saved/conflict states
   (10, 11) and the Personen section with the shared member picker (12).
   Accepted FROM the app into the spec: tab 1 is „Briefing", German Zielart labels, payout
   preference above the action bar.
14. **Round-8 web-parity coverage (29.08.2026).** Every open ❌ of the Web Parity Programme that is
   in scope now has a drawing. New chapters: **16 Materialien & Referenz** (the six web reference
   pages folded into one list + a three-tab detail + the profit-calculation sheet; read-only) and
   **17 Börse & Mein Inventar** (item offer/request behind ONE Material-|-Item switch, editing an
   existing row, the selection-mode bulk actions, org-wide blueprint availability, Officer+).
   Extended chapters: **10** artboards 10–16 (order edit full + requester-limited, item lines with
   the two-level sub-assembly tree, claims, HANDOVER and Herstellung — the one gap that stopped an
   order being finished from the app —, unlink); **06** artboards 15–17 (Operation create, edit,
   revoke a paid-out); **11** artboards 6–7 (refinery order edit, delete); **09** artboards 20–21
   (bulk check-out, game-item stock page); **12** artboards 9–10 (direct booking — the delta is
   CONFIRMED and drawn, Verwaltung-only — and the KRT-Freigaben approval-tier tab).
   Decisions that close the programme's open questions: the extractor import stays out (item 10);
   the direct booking forms stay IN but only in the Verwaltung scope; the reference pages become
   two screens, not five nav entries; bulk actions always run through the ch. 02 §4 selection mode
   rather than a menu item that deletes an unseen list. Still deliberately web-only: deleting an
   Einsatz/Auftrag/Operation (admin), Beförderung, the admin area, and moving stock between Lager
   and private inventory (the read side does not exist — see the programme's own note).
   Not drawn because unmapped: `profile.html` — enumerate what it offers beyond the KONTO rows
   first.
15. **Round-8 grounding pass (29.08.2026) — three specs were wrong and are corrected.** The first
   draft of chapters 16/17 and the bank's fifth tab was derived from the parity programme's
   one-line summaries; re-derived from `frontend/.../messages_de.properties` (correction 11 applies
   to NEW chapters too):
   a) The profit calculation is a **ship/route** table, not a per-material calculator:
      `profit.filter.ship` „Schiff wählen", `profit.filter.systems` „Systeme einschränken",
      `profit.col.margin` „Marge (%)", mandatory notes „Berücksichtigt nur Auto-Load Terminals."
      and „Hull C Sonderregel (Loading Dock) aktiv.", states „Berechnung läuft…" /
      „Fehler beim Abrufen der Profitberechnung." (ch. 16 artboard 4).
   b) **Material-/Itemsammelübersicht belong to the ORDER, not to the material reference**
      (`material.collection.back` = „Zurück zum Auftrag"): columns Besitzer · Standort ·
      Material/Item · Qualität · Menge („von {0} im Bestand") · Geliefert, with three INLINE
      editable fields and their verbatim toasts. Drawn in ch. 10 artboard 16, removed from ch. 16.
   c) The bank's fifth tab is **Freigabe-Limits**, not approval tiers: per tier (incl.
      „Alle Mitglieder der Org-Einheit") and per user an amount up to which no extra approval is
      needed; actions „Setzen" / „Entfernen"; both hint texts verbatim; the request side carries
      „Freigabe erteilen", „Über Limit", „Freigegeben" and the confirm checkbox
      „Freigabe durch Kontoverantwortlichen erfolgt" (ch. 12 artboard 10).
   Also corrected: material types are „Rohmaterial" / „Veredelt" (+ „Unsortiert"), price columns
   „Einkaufspreis" / „Verkaufspreis", `materials-overview` is a **matrix** („Lade Matrix…"), and
   `blueprintOverview` has two columns — „Blueprint" and „Verfügbar bei" — with per-row owner
   loading states and „kein Einheitsmitglied", not a craftability chip.
   **Known copy gap:** the exchange's ITEM routes (`/item-offers`, `/item-requests`) have no
   `messages_de` keys at all. Ch. 17 artboards 1–3 mark „Zustand" and „Bis wann" as PROPOSALS
   („unbelegt") to be reconciled with the web — or created there — before implementation.
16. **Contrast sweep (29.08.2026) — correction 2 enforced across all 18 chapters.** 243 text fills
   of `#646464` were swapped to `#8A8A8A` (`--color-gray-2-text` / `KrtPalette.TextMuted`):
   search placeholders, „—" missing-value cells, dimmed units, chip labels and field caps all read
   at 2.9–3.6:1 against their surface and failed the AA floor the token exists for. `#646464`
   remains only where it belongs — hairlines, scrollbar thumbs, disabled fills, decorative glyphs
   (`border-color` / `background-color` declarations were left untouched). When implementing:
   `KrtPalette.Gray2` is never a text colour.
   **Design-system finding (report upstream):** `krt-components.css` gives EVERY `.chip--<tone>`
   the canonical FILL value as its `color` — `muted` #646464 (3.55:1), `info` #355DDC (2.92:1),
   `success` #239E33 (4.20:1), `danger` #A3000A — while the text tints `--color-gray-2-text`
   #8A8A8A, `--color-info-text` #6C93EF, `--color-success-text` #2EBC3D and `--color-danger-text`
   #F2564B sit defined and unused. That is the case correction 2 and the system's own rule
   („Farbe als TEXT nimmt die Text-Tints") both forbid. Every toned chip in this bundle therefore
   pins its text tint inline — muted #8A8A8A, success #2EBC3D, info #6C93EF, danger #F2564B (71 occurrences across
   12 chapters). The Compose mirror must use `KrtPalette.TextMuted` for `KrtChip`'s muted
   label, not `Gray2`; the Compose mirror must likewise use `SuccessText` / `InfoText` /
   `DangerText` for `KrtChip`'s toned labels, never the fill colours. The web side should change
   the `color` token in all four `.chip--<tone>` rules (fills and borders keep the canonical values).
17. **Provenance discipline for new copy.** Chapters may only assert a `messages_de` key they were
   read against; anything else is marked „unbelegt" inline (yellow) and in the artboard's card.
   Currently marked: ch. 17 „Zustand" / „Bis wann" (no keys for the item routes at all), ch. 16
   „Gewinn" (only `profit.col.material` and `profit.col.margin` are backed) and the derived screen
   title „Profitberechnung". Titles that ARE backed now use the web's own navigation strings —
   `nav.materials` „Handel", `nav.materials.list` „Material-Übersicht",
   `nav.materials.overview` „Preis-Übersicht" (the chapter previously invented „Preismatrix").

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
| 04 Auth.dc.html | Login (Keycloak Custom Tab + Discord), approval pending, terms, app-lock |
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
- Touch target floor 44 dp (nav / app-bar icons 48 dp); list rows min 44 dp, dense rows 56–64 dp by
  content; pull-to-refresh on every list (orange ring spinner)
- Overlays: KRT modal (3 dp orange top edge, 13 dp brackets; the HUD box uses 10 dp — two values,
  both straight from the stylesheet, Overlay glow 12 dp/.10, ONE filled CTA right, ghost
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
PLUS the byte-exact Fankit-Agreement clause-2(g) notice (correction 12 below)
— one inseparable component of THREE elements, verbatim ENGLISH in every locale, both 14 sp #D2D2D2, static,
no KRT styling. Placements: Login (above version footer) + Einstellungen. Nowhere else.
**Logo rule:** the KRT mark renders ONLY in #E77E23, white or black.

## Assets
- `assets/krt-icons-mobile.js` — full icon sprite: product set verbatim from
  `fragments/icons.html` + 20 mobile extensions (same contract: 24 dp, stroke 2, round
  caps, currentColor). Export as VectorDrawables per `artifacts/icon-export.md`
- `assets/basetool-appicon-512.png` / `basetool-favicon.svg` — Basetool-Logofamilie (DS):
  app logo everywhere in-app (full mark on black; favicon variant for small chrome like the
  tablet rail); adaptive-icon vector per ch. 14 geometry. `assets/krt.webp` stays the org mark (web)
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
