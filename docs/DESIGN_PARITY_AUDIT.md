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

| | design chapters | app list screens |
| :-- | :-- | :-- |
| list item is a bordered card / `.card` | 10 of 10 (04–13) | **2 of 13** |

Measured: `class="card"` and per-item `border:1px` inside an `<sc-for>` across the chapters; `KrtCard(`
across the thirteen list screens. Only `OrdersScreen` (fixed by this audit's first change) and
`PromotionScreen` use it.

`KrtCard` has existed in `core:designsystem` since the design system landed. The screens simply do
not call it — which is why the app reads as a list of text lines where the design reads as a stack
of tiles.

**Twenty design-system components are never used by the app at all**, and several of them are exactly
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

### 12 Bank — artboards 1–3 · `bank/BankScreen.kt` — **gap**

The account card is a bare `Column`. The chapter's `accounts[]` carries `spark` (a balance
sparkline), `delta` with its own colour, and `holders`; **`holders` appears nowhere in the screen**
and `KrtSparkline` is unused app-wide. The request rows carry `approvals` and `canApprove`.

### 09 Lager — artboards 1–4 · `inventory/`, `personalinventory/` — **check**

Tree nodes carry `total` + `unit` per group and each stack carries `ort`, quality `q` and `qPct`;
blueprints carry `missingRows`. No `KrtCard`, `KrtTotalTile` unused.

### 08 Hangar — artboards 1–3 · `hangar/HangarScreen.kt` — **check**

Ship rows carry manufacturer (`mfr` / `mfrName`, the lettermark placeholder the handoff documents),
`type`, `ort`, insurance `ins` and `fit`. The screen mentions a manufacturer once. Tablet artboard is
the full web table — that one **is** implemented (dense table, 2026-08-24 pass).

### 05 Dashboard · `dashboard/DashboardScreen.kt` — **check**

Mission rows carry `ort` and the quick actions a `count`; neither found. No `KrtCard`.

### 06 Missionen — artboards 1–5 · `missions/*` — **check**

Tab row carries a per-tab `count` (`KrtCountBadge` unused). Artboard 5 (Operation detail + payout)
exists, so **Operationen is covered** — contrary to the assumption that it had no template.

### 11 Raffinerie — artboards 1–2 · `refinery/RefineryScreen.kt` — **check**

Order rows carry `method` and `station` beside the status and the yield list. No `KrtCard`.

### 07 Benachrichtigungen · `notifications/NotificationsScreen.kt` — **check**

The chapter's `badges[]` (badge + note) is not in the screen. Swipe actions and the row itself were
built in the inbox pass and are believed to match.

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
