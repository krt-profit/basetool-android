> **Doc type:** Living audit — kept in sync with `main` until every row is closed.
> **Started:** 2026-08-25 · **Re-run:** 2026-08-25 against the updated bundle (chapters 04–15)
> **Scope:** every app screen against `docs/design/android/`
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

Status: **done** = read against the chapter and matching · **blocked** = the API cannot supply what
the chapter draws, flagged rather than invented · **layout open** = every fact is there, the
arrangement differs.

Every row has now been read. Three of the probe's leads turned out to be false — Raffinerie's
`station`/`method`, the Materialbörse's `supplyCount` and the Bank's sparkline were all being drawn
under different names — which is why the leads were marked as leads.

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

### 10 Aufträge — artboards 2–4 · `orders/OrderDetailScreen`, `exchange/MaterialBoardScreen` — **done**

A position now names its claims — the count was in the model and drawn nowhere, and it is what turns
an open figure into a plan. The Materialbörse's `supplyCount` was another probe false positive: it
**is** rendered, as `interestCount`. The board's rows are tiles with the sweep.

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

### 09 Lager — artboards 1–4 · `inventory/`, `personalinventory/` — **done**

The group header has the chapter's fill beside the orange rail it already had, and the toggle turns a
chevron — without one nothing said the row opens. A stack entry now leads with **where** it is,
behind a map pin: only the amount and the note were drawn, so two entries of the same material in
different hangars read as duplicates of each other. `total` + `unit` per group were already there as
`amount`/`unit`. Mein Inventar and Blueprints are tiles.

### 08 Hangar — artboards 1–3 · `hangar/HangarScreen.kt` — **done**

The row now leads with the manufacturer as the chapter's **lettermark square** — initials for a
multi-word maker, first letters otherwise — with the location behind a map-pin glyph beside the
insurance and fitted chips. It was a muted subtitle, which cost the list its scannable first column.

The full manufacturer name is no longer drawn, so it is given to assistive tech as the square's
content description instead: a reader announcing „A A" would have lost information the row used to
carry. The lettermark is the delivered design rather than a stand-in for it — the manufacturer marks
exist upstream only as SVGs with embedded rasters, and the handoff names this placeholder as the
design until clean vectors are re-exported.

The tablet's full web table **is** implemented (2026-08-24 pass).

### 05 Dashboard · `dashboard/DashboardScreen.kt` — **done, one item blocked**

The band was a single line with the name and a status badge. Chapter 05 draws a `hud-box` with three
rows, and it has all three now: name + briefing beside the status; the meeting time behind a clock
glyph and the meeting point behind a map pin; the owning unit as a chip, and „Öffnen ›" as the way
in. `Mission` gained `description` from `MissionListDto`, which was on the wire and unmapped.

**Blocked:** the chapter's „{n} angemeldet". `MissionListDto` carries no participant count — the
figure exists only on the detail DTO — so the list endpoint cannot supply it. Left out rather than
faked; a backend item, like the Bank's Verwahrer chip.

### 06 Missionen — artboards 1–5 · `missions/*` — **done**

Both list segments are tiles, and the detail's tab row carries the count artboard 2 puts on it —
derived from the collections the detail already holds, so no new read. Übersicht and Finanzen carry
none on purpose: the first is prose, the second loads separately, and a figure before that read
lands would be a promise the screen cannot keep.

Artboard 5 (Operation detail + payout) exists, so **Operationen is covered** — contrary to the
assumption that it had no template.

### 11 Raffinerie — artboards 1–2 · `refinery/RefineryScreen.kt` — **done**

Corrected from the probe: `station` and `method` **are** drawn — as `locationName` in the title and
`methodName` in the second line, which is why a probe looking for the design's field names missed
them. With the tile the row now matches artboard 1.

### 07 Benachrichtigungen · `notifications/NotificationsScreen.kt` — **done**

Read against the chapter: the row is deliberately not a card, and the app draws what ch. 07 draws —
bottom hairline, per-item background, and the orange inset bar for unread.

The `badges[]` the probe flagged is not a list on the screen at all: it is the chapter's own
specification table for the top-bar bell's badge states, and the bell's badge is wired from the same
unread state the inbox reads. A probe cannot tell a handoff table from a rendered list, which is the
method's honest limit.

### 13 Einstellungen — artboards 1–2 · `settings/`, `promotion/` — **blocked on the backend**

Artboard 1 is **Beförderung — Meine Bewertungen**. Its matrix has four columns — topic / **self** /
**lead** / goal — and the backend records **one** level per topic: `MemberEvaluationResponse` carries
`assignedLevel` and nothing else. There is no self-assessment and no separate lead assessment to
draw, so two of the four columns describe something the tool does not have.

That is a design-versus-domain question rather than an implementation gap, and it needs the owner:
either the artboard is drawn against an intended feature that was never built, or those columns
should come out of it. Not invented here either way. `goal` is available (`minimumLevel`), and the
requirement rows already carry it.

Note for ADR-0009: that ADR says the handoff has no chapter for Beförderung — it has no *chapter*,
but it does have this artboard, and the tablet layout draws it as the right column.

### 04 Auth, 14 System States — **believed done**

Login, approval-pending, terms and app-lock were built against ch. 04 and verified on a device. Ch. 14
covers Update erforderlich, 403/404/500, offline and the launcher; the in-fiction error copy is pinned
by tests.

## Re-run against the 2026-08-25 bundle

A new export landed after the first pass. It **adds** three surfaces — all three of the ones this
audit had reported as having no artboard, built from the prompts in
[`MISSING_ARTBOARD_PROMPTS.md`](design/android/MISSING_ARTBOARD_PROMPTS.md):

| new | where |
| :-- | :-- |
| Open-Source-Lizenzen, 5 artboards + tablet | **chapter 15** (new file) |
| Auftrag — Notiz-Sheet (leer / bearbeiten / 409) und Status-Wechsel (gesperrte Option, Terminal-Bestätigung) | **chapter 10, artboards 5–9** |
| Gate-Ausfall nach Login, with a live countdown | **chapter 14** |

Every other chapter changed too, but only in markup and annotations — no artboard was added, removed
or redrawn. Two annotation changes are worth knowing: chapter 13 now points the licences entry at
chapter 15, and chapter 14's icon section now names the Basetool logo family instead of the old
"spec approximation" wording.

### The export dropped three of our reconciliations

This bundle is a fresh upstream export, and it does not carry the corrections the repo copy had.
All three are restored on import and recorded in the handoff README, because a silent revert here
would be invisible until somebody built against it:

1. **minSdk read 29.** ADR-0006 raised the floor to 30 and deleted the API-29 app-lock path — on API
   29 the only auth-bound key is time-bound, which no `CryptoObject` accepts, so the lock could
   neither be armed nor opened on the whole minSdk platform. An owner-approved, implemented
   decision; a spec saying 29 reopens it silently.
2. **`--color-gray-2-text: #8A8A8A` was gone** from the `_ds` mirror. Grau 2 (`#646464`) reads at
   ~3.5:1 on flat black and fails WCAG AA as small text; this token is the accessible tint and
   `KrtPalette.TextMuted` mirrors it. Losing it leaves the Android counterpart with no upstream
   source and invites a "reconciliation" back to a failing colour.
3. **Chapter 04 lost the guest-mode annotation** — the owner decision of 2026-08-18 that guest mode
   is cancelled and „Als Gast fortfahren" is dropped without replacement. Without it the chapter
   shows a guest button and nothing says it must not be built.

`assets/basetool-logo.svg` is kept for the same reason: `ic_launcher_foreground.xml` and
`krt_basetool_logo.xml` both cite it as the artwork they were traced from, and the export replaced
it with a favicon variant rather than a redrawn mark.

**This is a recurring cost, not a one-off.** Every hand-refreshed mirror loses local corrections
unless somebody re-applies them, and nothing in the build detects it. The README's new "Reconciled
on import" section is where the list lives so the next refresh has something to check against.

The real fix is upstream, and there is a prompt for it:
[`SPEC_CORRECTION_PROMPT.md`](design/android/SPEC_CORRECTION_PROMPT.md) asks the design side to
carry all four corrections **in the source** and to settle the four places where an artboard draws a
figure the API does not have. Until that comes back, every import re-applies them by hand and the
five-point check at the end of that prompt is what an importer should run.

### New surfaces — parity

#### 15 Open-Source-Lizenzen · `settings/LicensesScreen.kt` — **gap**

The screen has the intro, a section title per licence and the rows. The chapter adds five things it
does not have:

- a **summary line** — „102 Artefakte · 4 Lizenzen · v1.4.2 (Build 37) · Prod";
- a **per-group subtitle** — „100 Artefakte · SPDX: Apache-2.0";
- a **sticky group header** while scrolling, and an end-of-report line naming the generator and its
  version;
- the **no-browser fallback**: copy the URL and say so in a toast, rather than a dead row. This is
  the open acceptance item on `REQ-APP-SET-005`, and the chapter now specifies it;
- **loading** (spinner only after 300 ms) and **error** („Bericht nicht lesbar" + „Erneut versuchen")
  as distinct states; the screen has one combined unavailable state.

#### 10 Aufträge artboards 5–9 · the note and status sheets — **done bar one row**

The note sheet has a title, a hint, a field and two buttons. The chapter adds the order number and
„Nur deine eigene Zuweisung" as a subtitle, a **250-character counter**, „Leeres Feld speichern
entfernt die Notiz.", a „Gespeichert."-toast, and — the one that matters — the **409 conflict
state**: „Konflikt — Notiz zwischenzeitlich geändert", the rejected text under „Deine abgelehnte
Fassung", and „Meine Fassung übernehmen". Optimistic locking is a project-critical rule and this is
the first artboard that draws its UX.

**Built:** the subtitle, the counter, and the 409 state — a lost race now re-reads the order, shows
what it says *now* in the field, holds the refused text beside it under „Deine abgelehnte Fassung"
and offers „Meine Fassung übernehmen". If the re-read fails too, the typed text stays put: throwing
a member's paragraph away at the moment the network cannot give it back is the worse of the two
failures, and a test pins both branches.

**Design-versus-contract, for the owner:** the chapter's counter reads `0 / 250`;
`AssigneeNoteRequest.note` is capped at **500** on the wire. The client uses 500 — enforcing 250
would refuse text the server accepts — but the two should agree.

**Built:** the status sheet is choose-then-apply, as artboard 8 draws it — a colour square per
status, the consequence beneath the ones that carry one, „Aktuell" on the current status, the role
footer, „Status übernehmen", and a confirmation before either terminal status (artboard 9). It used
to move the order on the first tap, which for „Abgeschlossen" meant a mistap was final.

**Not built — nothing to back it:** the artboard also draws a *disabled* option with a per-status
reason („Erfordert Rolle Logistiker"). The app's status control is role-gated as a whole
(`statusChangeable = me.logistician`), and neither the API nor the app expresses which single
transitions a role may make. Drawing a disabled row would mean inventing the rule it states.

#### 14 Gate-Ausfall · `gate/GateUnavailableScreen.kt` — **check**

Newly specified, with a live countdown for the automatic retry. To be read against the screen.

## Screens with no artboard

**None as of the 2026-08-25 bundle.** The three below were the gap; all three were delivered. Kept
for the record of what was asked and what came back:

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
