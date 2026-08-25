> **Doc type:** Living audit — kept in sync with `main` until every row is closed.
> **Started:** 2026-08-25 · **Scope:** every app screen against `docs/design/android/` chapters 04–14
> **Binding source:** the design handoff (`README.md` there): *"High-fidelity. Colors, type, spacing,
> states and copy are final and binding — recreate pixel-perfectly (1 CSS px = 1 dp)."*

# Design parity audit

The handoff is binding and the app was built against it, but nobody had compared the two since the
screens landed. This is that comparison, screen by screen, with the evidence for each finding.

## The systemic finding: the app has no tiles

Every chapter draws its list items as **bordered cards**. The app draws almost all of them as bare
rows in a `Column`.

| | design chapters | app list screens, before | after |
| :-- | :-- | :-- | :-- |
| list item is a bordered card / `.card` | 10 of 10 (04–13) | **2 of 13** | **11 of 13** |

The two that remain are correct as they are, and reading the chapters is what settled it:

- **Benachrichtigungen** (ch. 07) is *not* a card in the design either — a row with a bottom hairline,
  a per-item background and a `box-shadow` left bar for unread. The app already draws exactly that.
- **Lager — Bestand** (ch. 09 artboard 1) is a tree, and its group header is a filled bar with
  `border-left: 4px solid #E77E23`, not a tile. The app had the orange rail and was missing the fill;
  it has both now.

Measured: `class="card"` and per-item `border:1px` inside an `<sc-for>` across the chapters; `KrtCard`
/ `KrtKpiCard` / a hairline border across the thirteen list screens. Before this audit only
`OrdersScreen` and `PromotionScreen` had one.

`KrtCard` has existed in `core:designsystem` since the design system landed. The screens simply did
not call it — which is why the app read as a list of text lines where the design reads as a stack of
tiles. That is the single change with the widest visual effect in this whole audit, and it was one
missing wrapper per screen.

**Twenty design-system components were never used by the app at all** — `KrtCard`, `KrtKpiCard` and
`KrtSparkline` have consumers now; the rest still do not, and several of them are exactly
the missing pieces below: `KrtSparkline`, `KrtTotalTile`, `KrtKpiCard`, `KrtCountBadge`,
`KrtDepartmentTag`, `KrtRecordCard`, `KrtPanelHeader`, `KrtDataValue`, `KrtCombobox`, `KrtSelectField`,
`KrtChipSelect`, `KrtRadioRow`, `KrtBottomCtaBar`, `KrtButton`, `KrtSuccessButton`, `KrtSpinner`,
`KrtOfflineBanner`, `KrtUpdateAvailablePill`, `KrtRefreshableFill`, `KrtPresenceIndicator`. The last
one is correct — presence is web-only by decision (ADR-0126). The rest are unexplained.

## Per screen

Status: **done** = matches the chapter · **gap** = verified deviation · **check** = flagged by field
probe, not yet read line by line.

### 10 Aufträge — artboard 1 (Queue) · `orders/OrdersScreen.kt` — **done**

The example the owner named. Verified against the chapter's `orders[]` model and its markup:

| design | app before | now |
| :-- | :-- | :-- |
| bordered card, 10 dp gap | bare `Column` | `KrtCard`, `spacedBy(sm)` |
| priority block: figure + „Prio" | `KrtChip("Prio 1")` | block |
| kind chip Material / Item | **absent** | `KrtChip`, primary / info |
| status pill + **colour-coded age** | status only; age buried in a grey sentence | pill + age in the operator's three bands |
| `Für` [badge] `Durch` [badge], SK styled apart | one muted sentence | two `KrtOrgBadge`, `SpecialCommand` for SK |
| chevron right | absent | present |
| „Materialien (n)" with chevron | plural text link | chevron + label + count |
| material line + progress bar | ✓ | ✓ |

The age colours are **not** constants: `job_order.age_yellow_days` / `job_order.age_red_days` are
operator settings in the web admin area (defaults 30 / 90, which is exactly what the mockup's sample
data shows). `JobOrderAgeThresholds` reads them and falls back to the seeded defaults, so a failed
read looks like a fresh server rather than an error.

### 10 Aufträge — artboards 2–4 · `orders/OrderDetailScreen`, `exchange/MaterialBoardScreen` — **check**

Artboard 2 draws four tabs (Positionen / Materialbedarf / Übergaben / Verlauf) with per-position
`booked` and `claimed` figures; artboard 3 the Materialbörse with `supplyCount` on a request. Neither
figure appears in the screens. No `KrtCard`.

### 12 Bank — artboards 1–3 · `bank/BankScreen.kt` — **done, one item blocked**

Corrected from the first pass: the screen *did* draw balance, delta and a sparkline — it had its own
local `Sparkline` composable, so the probe's "unused `KrtSparkline`" reading was misleading. What was
actually wrong was the container: a bare `Column` with a hairline underneath instead of the
chapter's `kpi-card`, the balance on the name's line rather than beneath it, and a grey delta where
the sign is the whole point. It is `KrtKpiCard` now, which is that card, and the local sparkline is
gone with it.

**Blocked, not fixed:** the chapter's `{n} Verwahrer` chip. `BankAccountDto` carries no holder count,
so this is a backend contract gap and belongs in the main repo rather than being invented here — the
project rule is to flag a mismatch, not code around it. The request rows' `approvals` / `canApprove`
are still to be checked.

### 09 Lager — artboards 1–4 · `inventory/`, `personalinventory/` — **part done**

The tree's group header now carries the chapter's fill beside the orange rail it already had; Mein
Inventar and Blueprints are tiles. Still to check: `total` + `unit` per group node, `ort` / quality
`q` / `qPct` per stack, and `missingRows` on a blueprint. `KrtTotalTile` remains unused.

### 08 Hangar — artboards 1–3 · `hangar/HangarScreen.kt` — **part done**

Ship rows are tiles now. Still open: the manufacturer lettermark (`mfr` / `mfrName`, the placeholder
the handoff documents) and whether insurance `ins` and `fit` read as the chapter draws them. The tablet's full web table **is** implemented (2026-08-24 pass).

### 05 Dashboard · `dashboard/DashboardScreen.kt` — **part done**

The Einsätze band is a tile now. Still open: `ort` on a mission row and a `count` on the quick actions.

### 06 Missionen — artboards 1–5 · `missions/*` — **part done**

Both list segments are tiles now. Still open: the per-tab `count` on the detail's tab row
(`KrtCountBadge` unused). Artboard 5 (Operation detail + payout) exists, so **Operationen is
covered** — contrary to the assumption that it had no template.

### 11 Raffinerie — artboards 1–2 · `refinery/RefineryScreen.kt` — **part done**

Order rows are tiles now. Still open: `method` and `station` beside the status.

### 07 Benachrichtigungen · `notifications/NotificationsScreen.kt` — **done bar one**

Read against the chapter: the row is deliberately not a card, and the app draws what ch. 07 draws —
bottom hairline, per-item background, and the orange inset bar for unread. Still open: the chapter's
`badges[]` (badge + note), which is not in the screen.

### 13 Einstellungen — artboards 1–2 · `settings/`, `promotion/` — **check**

Artboard 1 is **Beförderung — Meine Bewertungen**, with `reqs[]` (requirement rows with a progress
bar) and `matrix[]` (topic / self / lead / goal). `goal`, `leadColor` and `meta` are not in the
screen. Note for ADR-0009: that ADR says the handoff has no chapter for Beförderung — it has no
*chapter*, but it does have this artboard, and the tablet layout draws it as the right column.

### 04 Auth, 14 System States — **believed done**

Login, approval-pending, terms and app-lock were built against ch. 04 and verified on a device. Ch. 14
covers Update erforderlich, 403/404/500, offline and the launcher; the in-fiction error copy is pinned
by tests.

## Screens with no artboard

Everything else in the app maps to an artboard. These do not, and need one before they can be judged:

1. **Open-Source-Lizenzen** (`settings/LicensesScreen.kt`) — ch. 13 names the entry point but draws
   no list.
2. **Auftrag: Notiz-Sheet und Status-Wechsel** (`ORDER_NOTE_SHEET_TAG`, `ORDER_STATUS_SHEET_TAG`) —
   ch. 10 draws four artboards, neither of them these two sheets.
3. **Gate nicht erreichbar** (`gate/GateUnavailableScreen.kt`) — ch. 14 covers 403/404/500 and
   offline, not "the approval gate itself could not be read".

Prompts for these three: [`docs/design/android/MISSING_ARTBOARD_PROMPTS.md`](design/android/MISSING_ARTBOARD_PROMPTS.md).

## How this audit was made

- Chapters parsed for their `<sc-for>` templates: what each list repeats and which fields it shows.
- Those field names probed against the screen sources, then the notable hits read line by line.
- `KrtCard` and design-system component usage counted across the app.

A probe hit is a lead, not a verdict — a field can be rendered under a different name. Rows marked
**check** are leads; rows marked **gap** and **done** were read.
