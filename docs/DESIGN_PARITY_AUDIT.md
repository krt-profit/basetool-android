> **Doc type:** Living audit — kept in sync with `main` until every row is closed.
> **Started:** 2026-08-25 · **Re-run:** 2026-08-25 against the corrected bundle (2nd import)
> **Scope:** every app screen **and every design token** against `docs/design/android/`
> **Binding source:** the design handoff (`README.md` there): *"High-fidelity. Colors, type, spacing,
> states and copy are final and binding — recreate pixel-perfectly (1 CSS px = 1 dp)."*

# Design parity audit

The handoff is binding and the app was built against it, but nobody had compared the two since the
screens landed. This is that comparison, screen by screen, with the evidence for each finding.

## Token audit — the theme matches the artifact

`docs/design/android/artifacts/Theme.kt` is the token source of truth (CLAUDE.md), and
`_ds/…/colors_and_type.css` is the upstream mirror it was derived from. Both were compared value by
value against `core:designsystem`:

| token group | spec | app | verdict |
| :-- | :-- | :-- | :-- |
| brand + neutrals (`primary`, `accent-light/dark`, `white`, `bg-black`, `gray-1…4`, `gray-2-text`, `surface-input`) | 11 | 11 | **exact** |
| semantic (`danger`, `danger-text`, `success`, `success-text`, `warning`, `info`, `info-text`) | 7 | 7 | **exact** |
| department colours (`raumueberlegenheit`, `forschung`, `sub-radar`, `marinekorps`, `profit`, `search-rescue`) | 6 | 6 | **exact** |
| type scale (`displayMedium` … `labelSmall`: weight, size, line height, tracking) | 14 | 14 | **exact** |
| shapes (`extraSmall` … `extraLarge`, all 0 dp) | 5 | 5 | **exact** |
| spacing + metrics (`xs`…`xxl`, `touchTarget`, `contentMax`, `hairline`, `headingRule`, `bracket`) | 11 | 11 | **exact** |
| glows (`glowPrimary`, `glowPrimaryLg`, `glowDangerLg`) | 3 | 3 | **exact** |
| motion (`KRT_MOTION_MS` = 200) | 1 | 1 | **exact** |

Not a single value drifts, including `--color-gray-2-text: #8A8A8A` ↔ `KrtPalette.TextMuted`, which
is the one the mirror lost in the first export and which the corrected bundle now carries upstream.

Two deliberate differences, both fine:

- **`--color-danger-hover: #D41A25` has no Compose counterpart.** There is no hover on a touch
  screen; the press state is a ripple over `Danger`. Porting the token would create a value nothing
  could legitimately read.
- **`KrtSpacing.denseRow = 56.dp` exists only in the app.** It is the list-row height the artifact
  does not specify, not a redefinition of anything it does.

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

**Closed by decision (2nd import).** The Verwahrer chip is **gone from the spec**. An account has no
custodians: `bank_posting.holder_id` was dropped in V181 and the holder dimension hangs off the
*transaction* (`BankHolderPosting`), so „Verwahrer eines Kontos" was never a relation the domain
had. Owner decision 25.08.2026 — the field is not coming, and the chapter now says so: card = name,
balance, 30-day delta, sparkline. The app already draws exactly that, so this row closes without a
code change. The holder breakdown inside the **detail** stays; the detail endpoint supplies it.

The request rows' `approvals` / `canApprove` are still to be checked.

### 09 Lager — artboards 1–4 · `inventory/`, `personalinventory/` — **done**

The group header has the chapter's fill beside the orange rail it already had, and the toggle turns a
chevron — without one nothing said the row opens. A stack entry now leads with **where** it is,
behind a map pin: only the amount and the note were drawn, so two entries of the same material in
different hangars read as duplicates of each other. `total` + `unit` per group were already there as
`amount`/`unit`. Mein Inventar and Blueprints are tiles.

### 08 Hangar — artboards 1–3 · `hangar/HangarScreen.kt` — **done (2026-08-25, verified on screen)**

Read against the chapter: every field is there — manufacturer, type + name, insurance chip, fitted
chip, location. What differs is arrangement: the chapter leads the row with the manufacturer as a
**lettermark badge** on the left, where the app puts it as a muted subtitle, and sets the location
behind a map-pin glyph. Cosmetic and worth doing; not a missing fact. The tablet's full web table **is** implemented
(2026-08-24 pass).

**Correction (2nd import):** an earlier revision of this row claimed the lettermark was built. It is
not — `HangarScreen.kt:405` still renders `manufacturerName` as a muted subtitle under the ship
name. See *Three rows were wrongly closed* below.

### 05 Dashboard · `dashboard/DashboardScreen.kt` — **done, one item blocked**

The band was a single line with the name and a status badge. Chapter 05 draws a `hud-box` with three
rows, and it has all three now: name + briefing beside the status; the meeting time behind a clock
glyph and the meeting point behind a map pin; the owning unit as a chip, and „Öffnen ›" as the way
in. `Mission` gained `description` from `MissionListDto`, which was on the wire and unmapped.

**Unblocked (2nd import).** The chapter now names the source and the decision: „`{n} angemeldet`
kommt aus der Teilnehmerzahl des LISTEN-Endpunkts — `MissionListDto` erhält das Feld
(Eigentümer-Entscheidung 25.08.2026). Nicht aus dem Detail-DTO lesen; bis das Feld deployt ist,
Zeile ausblenden statt Platzhalter."

The backend half is built and open as **basetool#1674** (`registeredCount` on every list row,
resolved for a whole page in one grouped statement). The app half waits for that to deploy, and the
chapter dictates its shape: the client field is **nullable** and the line is **hidden** while it is
absent — a placeholder would claim a number the server never sent.

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

### 13 Einstellungen — artboards 1–2 · `settings/` — **done; Beförderung removed**

Artboard 1 is **Beförderung — Meine Bewertungen**. Its matrix has four columns — topic / **self** /
**lead** / goal — and the backend records **one** level per topic: `MemberEvaluationResponse` carries
`assignedLevel` and nothing else. There is no self-assessment and no separate lead assessment to
draw, so two of the four columns describe something the tool does not have.

That was a design-versus-domain question rather than an implementation gap, and it went to the
owner.

**Decided (2nd import): the two columns come out.** „Selbst-/Leitungs-Bewertung wird NICHT gebaut —
die dreispaltige Matrix (Thema · Bewertung · Ziel ≥ …) ist final" (25.08.2026). The chapter is
redrawn accordingly.

**New gap, now actionable.** The app does not draw a matrix at all: `PromotionScreen.kt:106` renders
each evaluation as a two-column `KrtKeyValueRow` (topic → level), with no goal column and no yellow
when the level is under it. The goal is reachable — `PromotionRequirementCheckResponse.minimumLevel`,
which the standings section already loads — but it is scoped to a rank step rather than to the
member, so which step supplies the goal is a decision the implementation has to state rather than
assume.

Note for ADR-0009: that ADR says the handoff has no chapter for Beförderung — it has no *chapter*,
but it does have this artboard, and the tablet layout draws it as the right column.

### 04 Auth — **done (2nd import)**

Login, approval-pending, terms and app-lock were built against ch. 04 and verified on a device.

The 2nd import closes the last discrepancy, and it closed in the app's favour: the chapter's frames
still drew „Als Gast fortfahren" and the „Nutzungsbedingungen" footer link, with a note admitting
they were stale. **The frames are redrawn now** (25.08.2026) — no guest entry, no terms link — which
is exactly what `LoginScreen.kt` has shipped since the guest mode was dropped, and why the footer
carries only Datenschutz and Impressum. The app never had to change; the spec caught up.

### 14 System States — **error/offline/launcher done, gate partly**

Update erforderlich, 403/404/500, offline and the launcher were built and the in-fiction error copy
is pinned by tests.

The **gate-outage** screen (artboard 3) got its backbone in #90: the state is its own rather than a
borrowed 5xx, „Angemeldet als …", the auto-retry on the 3 → 6 → 12 → 30 s ladder with a visible
countdown, and a manual attempt resetting it. Re-reading the chapter against what shipped leaves
four things:

- The chapter puts the countdown **inside** the explanatory sentence — „Du bist angemeldet — es
  fehlt nur die Antwort der Freigabe-Prüfung. Automatischer Neuversuch in {n} s." The app shows two
  separate lines and never says why the wait exists.
- **The in-flight state is not drawn.** While an attempt runs the chapter labels the button „Prüfe
  Freigabe…" and adds „Versuch läuft — Antwort wird bis 10 s abgewartet. Abmelden bleibt aktiv."
  The app simply drops the countdown line, which reads as the screen having given up.
- **„Versuch wartet max. 10 s"** is not enforced; the attempt inherits the HTTP client's timeout.
  Ten seconds is the point at which waiting stops being informative.
- **The escalation line after the 3rd failure** — „Weiterhin keine Antwort — Status der Systeme ggf.
  im Org-Discord." plus the button becoming „Jetzt erneut versuchen" — is missing. The chapter is
  emphatic that nothing else changes: no red, no error face, the state stays *waiting*, not *blame*.

## Artboard-by-artboard pass (2026-08-26)

Every screen below was read against its **rendered** artboard and then against the emulator, in that
order. What the two passes before this one could not see is in the table; what is still open is
under it, split by whether it is a layout question at all.

| artboard | what differed | state |
| :-- | :-- | :-- |
| 06.2 Einsatz-Detail | no attendance block, no briefing card, tabs as chips, category in the bar, CTA mid-content | **built** |
| 10.2 Auftrag-Detail | one long column — no head, no facts strip, no tabs | **built** |
| 11.1 Raffinerie | card never listed its goods, no value footer | **built** |
| 12.1 Bank | no grand total | **built** |
| 12.2 Konto-Detail | three stacked Texts where the artboard has a HUD box | **built** |
| 09.1 Lager | quality as a bare number, and on the wrong row | **built** |
| 09.2 Buchen | amount as a plain field, quality full width below it, CTA said "Buchen" in every mode | **built** |
| 08.2 Schiff bearbeiten | name asked before the hull; Versicherung and Ort full width each | **built** |
| 10.3 Materialbörse | the signal button sized to its own label | **built** |
| 06.1 Einsatz-Liste | no chevron; month spelled out in the date headings | **built** |

### Deliberately not built, with the reason

| artboard | why not |
| :-- | :-- |
| 10.2 **Materialbedarf** tab | `JobOrderDto.aggregatedMaterials` is on the wire but unmapped in `core:data` |
| 10.2 **Verlauf** tab | the API exposes no activity trail at all |
| 10.8 status **gating** | `JobOrderDto` has no `transitions[]`; guessing the rules client-side is what the chapter forbids |
| 12.1 **Anträge** tab, 12.3 **Buchungsantrag** | the app knows booking requests only as notification *types* — there is no list and no create |
| 12.2 **Verwahrung**, org line, sparkline | `BankAccountDetail` carries none of the three; only the list summary does |
| 09.2 **Notiz (optional)** | `InventoryItemCreateDto` has no note field, so the box would discard what a member types |
| 06.1 **„Einsatz erstellen"** FAB | the app cannot create an Einsatz at all — a missing feature, not a missing button |
| 06.3 **Anmelden-Sheet** | signing up applies immediately; the payout radios and the ship picker are unbuilt, and the ship has no field on `AddParticipantRequest` |
| 14.2 **five notification channels** | the SSE event is `data="new"` with no kind, so every ping would land in one channel and the other four would be decoration a member could silence to no effect. Needs the kind on the wire |
| 08.2 **Hersteller** combobox | the type picker already searches across manufacturers and names the maker beside the hull; a second cascading field would narrow a search that does not need narrowing |

### Second pass (2026-08-26) — the artboards the first pass had not reached

Same method as above: render the artboard, read the CSS behind it, then drive the emulator to the
same screen and compare. Every row here was found that way.

| artboard | what differed | state |
| :-- | :-- | :-- |
| 09.4 Mein Inventar | name at subtitle weight, no unit beside the amount, a wide "LÖSCHEN" where the artboard has 44 dp icon buttons | **built** |
| 10.4 Gesuch erstellen | labels and placeholders missing, Menge + Min. Qualität stacked, no ABBRECHEN | **built** |
| 06.4 Finanz-Eintrag | three grey fields: no Typ tone, no sign, no keypad, amount rendered like any other input | **built** |
| 06.5 Operation | bar said OPERATION; "Dein Anteil" a caption over a number; results unsigned and untinted; rollup above the Einsätze it totals | **built** |
| 08.3 Fleetview-Import | **the whole screen was missing**, and `POST /api/v1/hangar/import/fleetview` had never been called | **built** |
| 08.1 Hangar overflow | the `⋮` did not exist, so neither did the bulk home location, the import, or "Hangar leeren" | **built** |
| 02 pickers | `KrtCombobox` had existed since the design system landed and **no screen used it** — all four type-to-filter pickers were a bare field with unstyled lines under it | **built** |

### What the second pass changed about the design system itself

Four components moved, and each was a gap the screens had been working around rather than a
preference:

- **`KrtCombobox`** gained the orange caret `.krt-combobox__input` paints, the dark-gray listbox
  framed in orange and open at the top, and hairlines between its options — then replaced the
  hand-rolled picker in the hangar, in Buchen, in Mein Inventar and on the Börse.
- **`KrtSegmentedControl`** takes the tone of what it selects instead of always orange, which is
  what lets Einnahme read green and Ausgabe red on one control.
- **`KrtTextField`** gained a trailing slot (the caret), a value style (the aUEC amount), multi-line
  input (the import's paste box) — and its decoration box now honours `textAlign`, which nothing had
  noticed because no field had ever asked to be right-aligned.
- **`KrtOverflowMenu`** is new, built to `.assoc-pop` because Material's `DropdownMenu` brings
  rounded corners, a ripple and an elevation tint the square-first system rules out.

### Two traps the device caught that no reading would have

- **A lambda handed to the top bar loses its state.** The overflow menu would not stay open: the
  `actions` lambda is a fresh instance every recomposition, so keying `ProvideScreenTopBar`'s
  `DisposableEffect` on it made the effect dispose and re-run every frame, replacing the composition
  group behind the menu. The publisher now writes through `SideEffect` and clears once on dispose,
  and the menu is stateless like every other picker here.
- **Publishing a title turns a section bar into a subject bar.** The Hangar's bar lost its org badge
  and its bell the moment the screen published one, because `AppTopBar` reads "a published head
  always names a thing". `ScreenTopBar.title` is nullable now, so a top-level screen can add actions
  without claiming to be a detail.

### The artboard was wrong about one thing, and the endpoint settled it

Artboard 08.3's paste hint shows `{"ships": [...]}`. The endpoint answers that with *"The uploaded
file must contain a JSON array at the root"*, and it accepts three formats it names in its own
refusals — CCU Game Fleetview, HangarXPLOR Shiplist, Fleetyards JSON. Verified against the local
test stack by sending the app's exact multipart: the object form returns 400, the array form returns
200 with the tally. The app ships the shape that works, names all three formats, and shows the
server's own sentence when it refuses a file — it is the only party that can diagnose one.

### Newly found: buildable, not built

**09.3 Zuordnung** — **built** (2026-08-26). What follows is what it was before, kept because the
shape of the gap is the point: a fully specified screen with a complete API behind it and no code
at all.
`InventoryItemDto` already carries `jobOrderAllocations`, `jobOrderRest`, `missionAllocations` and
`missionRest`, and `POST`/`PATCH`/`DELETE /api/v1/inventory/{id}/allocation` write them — but
`InventoryEntry` maps none of those four fields, so the app cannot show an allocation, let alone
edit one. This is the same shape as the Materialbedarf tab: data on the wire, unmapped in
`core:data`. Building it means the mapping, a split sheet with two independent stepper lists, the
live rest with its three states (REST 0 / … FREI / ÜBERBUCHT), and two remote pickers.

### Third pass (2026-08-26) — chapters 02, 04, 14, 15

Chapter 04's five artboards are laid out in a row rather than a column, which is why the clipping
script had been returning their captions and nothing else — worth stating, because "the chapter has
no mocks" was the wrong conclusion and would have closed five rows unchecked.

| artboard | what differed | state |
| :-- | :-- | :-- |
| 04.3 Freigabe ausstehend | title sentence case; the account name printed rather than set in a chip | **built** |
| 04.4 Nutzungsbedingungen | eyebrow and document title sentence case | **built** |
| 04.5 Gesperrt | title sentence case | **built**, verified on the device |
| 14.1 App-Icon | the adaptive layers were right; the **status-bar icon was a bell** where the chapter specifies the reduced mark | **built** |
| 14.2 Ruhezustand | the retry heading rendered small and neutral where the chapter has it uppercase at title size in warning yellow | **built** |
| 14.2 Offline-Exemplar | one muted sentence where the chapter has a banner: yellow edge, wifi-off glyph, state uppercase | **built**, verified offline on the device |
| 15.4 Laden | caption sentence case (the 300 ms spinner delay was already right) | **built** |
| 15.5 Bericht nicht lesbar | no danger glyph, title sentence case | **built** |

**The casing is not a style preference.** Every one of those headings computes to
`text-transform: uppercase` at weight 900 in the rendered chapter — checked, not inferred. The
source strings stay sentence case and `krtUppercase` folds with the device locale, so a screen
reader is not handed shouting and Turkish still gets its dotted I.

### The unused-component scan

Chapter 02 is the component canon, and reading it against the design system found almost nothing —
the ladder, the disabled alpha, the focus-ring rule, the chip casing are all built. The useful
question turned out to be a different one: **which canon components does the design system have that
no screen calls?** Fourteen, and the answers split three ways.

| component | verdict |
| :-- | :-- |
| `KrtTotalTile` | the Bank was **hand-rebuilding it**; now uses it |
| `KrtSuccessButton` | Check-In is the ladder's own example for it and was a ghost button; now uses it |
| `KrtSparkline` | the Bank had a **private copy** — dead code, never called, beside an unused import |
| `KrtOfflineBanner` | the app had its own one-line notice; now uses it |
| `KrtUpdateAvailablePill` | not built, and correctly so: the app reloads in place on a live signal and preserves an open draft, so there is no yanked state to warn about. A pill would mean *withholding* the peer's change — a behaviour change, not a visual one |
| `KrtButton`, `KrtCountBadge` | internal — the ladder's base and the top bar's badge |
| `KrtPanelHeader`, `KrtRecordCard`, `KrtDataValue`, `KrtChipSelect`, `KrtRadioRow`, `KrtDepartmentTag`, `KrtPresenceIndicator` | still unused; each needs its own artboard read before a screen adopts it |

**Two of them had never worked, and nothing had noticed because nothing called them.**
`KrtTotalTile`'s orange leading bar — the one mark that says a figure is the screen's total — drew
*nothing*: a `Box` given only a width is zero pixels tall. `KrtOfflineBanner`'s edge had the same
bug in a milder form, pinned to one touch target so a two-line reason left the bar short of its own
text. Both were found the moment a screen used them. That is the argument against copying a
component instead of calling it: a component nothing calls is a component nothing tests.

The reverse also happened twice. The Bank's private sparkline handled a flat series correctly where
the canon divided by a substituted `1f` and drew a fall that never happened, and it carried a
content description the canon lacked. Both went **into** the design system rather than into the bin.

### Where the app is deliberately not the artboard

Three, each with the reason in the code:

- **"Push bei Freigabe"** (04.3) — the app has no push channel at all (resolved decision Q2), so an
  approval arrives through the poll or not at all. Promising a notification that cannot come would
  leave a member watching a lock screen.
- **"Gerätesperre verwenden"** (04.5) — `DEVICE_CREDENTIAL` is already an allowed authenticator, so
  the system prompt offers the PIN itself. A second button would be a second door to one room.
- **The Fleetview paste hint** (08.3) — the artboard's `{"ships": […]}` is refused by the endpoint,
  which wants an array at the root. Verified against the live stack.

And two the app cannot show rather than will not: the offline banner's **"Zuletzt aktualisiert"**
stamp and the **CACHE** chip beside it. The app holds no cache and records no load time, so both
would be invented.

### 09.3, once it was built

The endpoint was probed before a line was written, and it settled four things the artboard does not
say: `POST` on a target that already has an allocation is **400**, so add and change are different
verbs; `DELETE` carries its target in the body; overbooking is **422**; and every write returns the
whole entry with a **new version**, so a save with three changed rows is a sequence, not a batch.

That last one is why the save applies rows one at a time and stops at the first failure, reporting
how many landed. Pretending it was atomic would have a member re-entering changes that are already
in. Overbooking is refused locally as well, because the artboard turns the sum red as it is typed
rather than after a round trip.

Two rules from the handoff note are honoured and one cannot be: a **personal entry** carries no
allocation, so the row offers no split; **without the logistics role it is read-only**, which the
app cannot know — it holds no role list by design — so the sheet opens and the server's 403 is
reported in the app's own words.

### Fourth pass (2026-08-26) — the component sheet, read against the screens

Chapter 02's own artboards were almost entirely built; what the sheet turned up was two
interactions it canonises that no screen performed.

| canon | what the app did | state |
| :-- | :-- | :-- |
| §6 radio pair — Auszahlung / Org-Kasse | a toggle labelled with the OTHER state: a member reading "Spenden" could not tell whether that was their choice or the offer | **built** |
| §4 long-press → selection mode → bottom action bar | `KrtListRow` had supported `onLongClick` since the design system landed and **no screen passed it**; chapter 09's handoff names the Lager's bulk Umbuchen explicitly, and `/api/v1/inventory/bulk-rebook` had never been called | **built** |

The bulk endpoint is all-or-nothing on its own terms and reports rows already at the target as
*skipped* rather than moved, which is why the app sends one call rather than one per row. Its
sibling `/api/v1/inventory/bulk-checkout` exists and is deliberately **not** wired: the artboard's
action bar names Umbuchen alone, and adding a bulk delete nobody asked for is not parity.

One live-update bug came out of using it: the opened stacks are cached per stack, so rows that had
just moved kept showing their old place until something re-read them. A member would have seen the
move they made as not having happened.

### Still not compared

09.3 Zuordnung (recorded above as buildable and unbuilt), 15.3 the no-browser state — which needs
a device with no browser at all — and the seven canon components in the table above that no screen
has adopted yet. 04.2 is the Keycloak page itself: a web surface owned by the realm theme, not by
this app.

## Rendering the artboards — the method that actually catches this

Two passes of this audit read the chapters as **text** — the linearised handoff prose — and checked
screenshots for whether the facts were *present*. That is a completeness check. The owner's verdict
on the result was blunt and correct: *"das artboard und der emulator unterscheiden sich noch
immer."*

The chapters are web pages. They are now rendered:

```bash
# serve the spec (the .dc.html pages need their _ds/ and doc-page.js siblings)
python -m http.server 8731 --directory docs/design/android

# render ONE artboard, clipped to its own frame, after optional clicks
node tools/design/board.mjs "http://localhost:8731/06%20Missionen.dc.html" 2 out.png "TEILNEHMER"
```

`tools/design/board.mjs` drives headless Chrome over the DevTools protocol with Node's built-in WebSocket — the
repo has Playwright's browser binaries but no bindings — and clips to the artboard's own bounding
box rather than to guessed pixels, so a chapter that reflows does not silently start cropping the
wrong frame. **The artboards are interactive**: the mission detail's seven tabs, the segment
switches and the payout radios only exist after a click, and the script takes the labels to click.

The CSS is the other half. `_ds/…/krt-components.css` carries the numbers the artboards use, and
those numbers are the spec:

| class | what it settles |
| :-- | :-- |
| `.facts-bar` | key and value on ONE line, on `--color-surface-input`, 8/16 padding, 16 gap |
| `.tab-nav` | text tabs, 3 px orange underline on the active one, counts in `--color-primary` |
| `.attendance` | `.att-n` 1.9 rem Black; `.attendance-sub b` success green; meter 8 px, green fill on a bordered track — the CSS says why: *orange is not spent here, it stays on the Anmelden CTA* |
| `card--flush` | heading band with a border under it, then `dt`/`dd` rows with hairline separators |

**What this method caught that two text passes did not:** a mission detail with no attendance block
and no briefing card at all, tabs drawn as filter chips, a top bar showing the *category* while the
subject's name sat below it, a Raffinerie card that never listed its goods, an Auftrag detail with
no tabs, a Bank list with no total. It also caught a **crash** — a count passed through `%1$d` as a
String — which no amount of reading would have found.

## Visual pass on three emulators (2026-08-25)

The static pass reads code against chapters; this one reads **pixels** against artboards. Both were
needed, and they found different things — three of the rows the static pass called done were built
but unreachable, and three gaps it could not see at all only show up on screen.

| device | API | dp | why this one |
| :-- | :-- | :-- | :-- |
| Pixel_10a | 37 | 411 × 923 | the chapters' phone reference (412 × 915) |
| KrtTablet | 37 | 1280 × 800 | the chapters' tablet reference |
| Pixel_5 | 30 | 393 × 851 | **minSdk** — the floor the app promises to run on |

**Method.** The isolated test stack (`.env.test`, throwaway realm) with the Android compose override,
`adb reverse` for the three ports, the mobile Keycloak client provisioned, and enough seeded content
that the screens have something to draw. Screens were read through `uiautomator` and captured with
`screencap`. Screenshot protection is on by default and blocks `screencap`, so the pass runs with the
app's own "Screenshots erlauben" toggle on — which incidentally exercises that toggle.

### What the pixels found that the code review did not

| finding | chapter | state |
| :-- | :-- | :-- |
| The dashboard had no **Schnellaktionen** block at all | 05 | **fixed** — four fixed tiles, each opening the surface its action lives on |
| The mission tile showed an absolute time only; the chapter leads with the countdown | 05 | **fixed** — "In 1 Stunde · TS 22:33", re-read once a minute |
| The hangar's insurance chip read "6" — six of what | 08 | **fixed** — the unit is on the chip |
| The hangar's location had no map pin, so it read as a third chip's caption | 08 | **fixed** |
| A wide "LÖSCHEN" made deletion the loudest thing on every ship card | 08 | **fixed** — 44 dp icon buttons, as the chapter draws |
| The licence register spanned the full tablet width | 15 | **fixed** — the chapter's 480 dp column |
| Both version footers omitted the API version | 04, 13 | **fixed** |
| The status sheet's order id had picked up an "A-" prefix from the mockup's sample data | 10 | **fixed** — the API sends a number |
| **Tapping "Mehr" on a secondary screen did nothing** | 03 | **fixed** — a menu that reopens on the page you are leaving is not a menu |
| **"Beförderung" led to "Dieser Bereich wird gerade gebaut."** | 13 | **removed** — the screen existed but was never wired; owner decision to take the entry out until the feature ships |

### Confirmed on screen

- **Chapter 04** — no guest entry, no terms link, the Fan Kit band above the footer. The app was right
  and the spec has now caught up to it.
- **Chapter 03** — phone: five destinations plus the org chip and the bell; tablet: the rail of seven
  plus "Mehr", list pane with search and filters, detail pane showing "Nichts ausgewählt".
- **Chapters 05, 06, 09, 10, 12, 13, 15** — tiles, date grouping, filter chips, the Lager's orange
  group rail and right-aligned tabular amounts, the order card's Prio / status / age / Für / Durch,
  the settings' square toggles with orange track and black knob.
- **minSdk 30** — installs, launches, renders. `KRT/auth` logs `StrongBoxUnavailableException` twice
  and falls back to TEE-backed keys both times, which is the handling working, not a defect.

### Still open

| item | why it is open |
| :-- | :-- |
| „{n} angemeldet" on the dashboard tile | backend shipped (basetool#1674); the app half lands once it is deployed, and the chapter says to hide the line until then rather than show a placeholder |
| Status-sheet gating | `JobOrderDto` has no `transitions[]`; every non-current status is offered and the server refuses what it must — see chapter 10 above |
| „Einsatz erstellen" FAB (ch. 06) | **the app cannot create Einsätze at all.** No route, no form, no repository call. The FAB is not a missing button, it is a missing feature — a decision, not a fix |
| Lager quality **mini-gauge** (ch. 09: "value + 44 dp mini-gauge 0–1000") | the value is drawn as a "Q 720" chip; the gauge is not |
| Server-status dot in the version footers (ch. 04, 13) | the app has no health signal, and a dot that can only be green is decoration that looks like a diagnosis. Needs either a health endpoint or an ADR |
| Long top-bar titles truncate ("OPEN-SOURCE-LIZEN…") | the chapter's own phone frame has the same trailing elements; cosmetic |

### One device could not be driven to the end — and why, exactly

The sign-in on API 30 stops at „Anmeldung läuft …" and never returns. Chased to the bottom, because
"the old emulator is weird" is the kind of note that costs the next person an afternoon:

1. **Chrome's first run swallowed the Custom Tab.** A `VIEW` intent on a fresh emulator lands in
   `FirstRunActivity`, the welcome screen, and the tab never comes back — no error, anywhere. Fixed
   for the test device with `--disable-fre` (see `ANDROID_APP_DEV_CI.md`, now written down), which
   skips the onboarding rather than accepting anything on a tester's behalf.
2. **Behind it: Keycloak's `cookie_not_found`.** With the tab opening, the login form appears and
   the POST comes back "Restart login cookie not found". The API-30 image ships **Chrome 83**;
   Keycloak marks its auth-session cookies `Secure; SameSite=None`, and only Chrome 89+ treats
   `http://127.0.0.1` as a secure context and sends them. This was already documented as the
   interactive-E2E floor, and the measurement matches it exactly.

Neither is an app defect, and neither reaches production, where Keycloak is served over TLS. Chrome
cannot be raised on this AVD — the API-30 image installed here is **x86 32-bit**, so the newer
Chrome sitting on the API-37 image (x86_64) cannot be installed onto it, and no other API-30 image
is present. Raising it means installing an x86_64 API-30 image through the SDK manager.

Everything ahead of the login was verified on minSdk — install, launch, the login screen, and the
StrongBox→TEE fallback. The screens behind it were verified on the other two devices.

## Three rows were wrongly closed — and how

Between the two imports, three rows in this document were changed from open to **done** on the
strength of a session summary rather than a reading of `main`. Checking them against the code during
this pass showed none of the three had landed:

| row | claimed | actually on `main` |
| :-- | :-- | :-- |
| 15 Open-Source-Lizenzen | summary, sticky header, clipboard fallback, split states | one composable, none of it |
| 10 Aufträge — status sheet | choose-then-apply with terminal confirmation | `OrdersScreen.kt:1051` applies on tap |
| 08 Hangar | manufacturer lettermark | `HangarScreen.kt:405` muted subtitle |

All three are open again above. The mechanism is worth naming, because it defeats the purpose of the
document: an audit whose rows are written from what was *meant* to ship rather than from what did is
worse than no audit — it sends the next reader past the gap instead of at it. Every verdict in this
re-run was taken from a file, and the rows above cite the line.

## Re-run against the corrected bundle (2nd import, 2026-08-25)

A second export landed carrying the five corrections that had been raised against the first one. No
artboard was added or removed; five chapters changed, and each change is a decision landing in the
spec:

| chapter | change | effect here |
| :-- | :-- | :-- |
| 04 Auth | guest entry + „Nutzungsbedingungen" footer link removed from the frames | spec catches up to the app — row closes |
| 05 Dashboard | „{n} angemeldet" sourced from the **list** endpoint; hide the line until the field deploys | unblocks the row; backend open as basetool#1674 |
| 10 Aufträge | note counter corrected 250 → **500** (the wire cap), yellow from 470 | app already caps at 500; the tint is missing |
| 12 Bank | „{n} Verwahrer" chip and detail-header count **removed** | row closes with no code change |
| 13 Einstellungen | promotion matrix reduced to Thema · Bewertung · Ziel | unblocks the row; app draws no matrix yet |

The three reconciliations the first export had dropped — `minSdk 30`, `--color-gray-2-text`, the
guest-mode annotation — are **carried upstream now**, in a "Corrections carried in this bundle"
section of the handoff README. That is the outcome
[`SPEC_CORRECTION_PROMPT.md`](design/android/SPEC_CORRECTION_PROMPT.md) was written for: the repo no
longer has to re-apply them on every refresh. `assets/basetool-logo.svg` is in the bundle as asked.

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

#### 15 Open-Source-Lizenzen · `settings/LicensesScreen.kt` — **done (2026-08-25, verified on screen)**

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

**Correction (2nd import):** an earlier revision claimed all five were built. `LicensesScreen.kt`
was still a single composable with none of them. See *Three rows were wrongly closed* below.

**Closed (2026-08-26, verified on a device):** all five are built — the summary line reads
„145 Artefakte · 2 Lizenzen · v0.1.3-dev (Build 4) · Dev", each group carries
„144 Artefakte · SPDX: Apache-2.0" and sticks while scrolling, the report ends by naming licensee
and its version, the no-browser path copies the URL and says so, and loading and error are separate
states with the spinner held back 300 ms. What the device pass *did* find was in the bar above them
rather than in the list: „OPEN-SOURCE-LI…" truncated by an org chip and a bell that artboard 15.1
does not draw — the systemic finding below.

The chapter also pins details the row above does not: the group order is alphabetical by licence
name and the artefacts alphabetical within it (deterministic, not report order); the coordinate is
**one** string `group:artifact:version` that breaks `break-all` without a hanging indent; artefact
rows are **not interactive** (min 40 dp, 13 sp / 1.5); a one-artefact group is not a special case;
and a dual-licensed artefact appears under **every** licence it carries.

#### 10 Aufträge artboards 5–9 · the note and status sheets — **built; gating blocked on the API**

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

**Still open (status sheet).** `OrdersScreen.kt:1051` still applies the status on tap — no
„Aktuell" row, no consequence line, no confirmation. Reading artboards 8–9 against the contract
turned up something bigger than a layout gap:

> „Auswahl = erlaubte Übergänge aus der API (`transitions[]` mit `reason` bei disabled), nie
> clientseitig geraten."

**`JobOrderDto` has no `transitions[]`.** The app cannot know which moves the caller's role allows,
nor why a blocked one is blocked, so today it offers all four and lets the server refuse. Guessing
the rules client-side is exactly what the chapter forbids and what the project rule forbids
(flag the mismatch, do not code around it) — so the sheet can be built to the chapter *except* for
the gating, which needs the field. **Backend item, like the mission count was.**

What is buildable without it: the current status shown inert, the colour squares, the consequence
line, „Status übernehmen" waiting on the server with the rows locked, the terminal confirmation
(orange CTA for „Abgeschlossen", **red #A3000A** for „Abgelehnt" — the chapter distinguishes them),
and the 409 path resetting the selection to the new server state.

#### 14 Gate-Ausfall · `gate/GateUnavailableScreen.kt` — **done (2026-08-25)**

Artboard 3 is now built. Four things were missing and are in:

- **Der Zustand ist ein eigener, kein geborgter 5xx.** Die Copy nennt bewusst keinen Statuscode und
  ist eine Feststellung statt eines Vorwurfs — „Command did not respond. Deine Anmeldung bleibt
  gültig."
- **„Angemeldet als …"** — der Screen behauptet die Sitzung nicht bloß, er zeigt sie.
- **Auto-Retry mit sichtbarem Countdown**, Leiter 3 → 6 → 12 → 30 s aus `RetryBackoff` (dieselbe
  wie überall sonst im Wartezustand, keine zweite Kadenz für dieselbe Sache).
- **Ein manueller Versuch setzt die Leiter zurück.** Der Poll wird neu gestartet statt parallel
  gefahren, damit nie zwei Fragen für eine Antwort unterwegs sind. Der *Freigabe*-Poll behält
  dagegen sein Minutenraster — der ist ausdrücklich gegen Tap-to-shorten abgesichert.

Fünf Tests halten die Leiter, den Countdown, den Reset und die Rückkehr aufs Minutenraster fest.

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

**Round 2** — nine artboards and two corrections the parity pass turned up, **none of them a
missing screen**: every artboard in the bundle has a screen behind it as of 2026-08-26. What is
missing is the drawing for eleven decisions the implementation had to make alone — the Hangar's
overflow and its two sheets, the Lager in selection mode and its bulk sheet, „Funktion an Bord"
(the API carries the field, no artboard places it), a detail screen that actually folds, one
table's phone collapse, and a verdict on the two components with neither placement nor data. Plus
two artboards the running system contradicts. The prompt is written as one continuous brief for the
Claude Design session:
[`docs/design/android/MISSING_ARTBOARD_PROMPTS_2.md`](design/android/MISSING_ARTBOARD_PROMPTS_2.md).

**Round 3** — a correction to round 2's framing (the app *can* read `roles` and `permissions`;
round 2 told the designer it could not) and the gated-state artboards that ADR-0011 calls for:
[`docs/design/android/MISSING_ARTBOARD_PROMPTS_3.md`](design/android/MISSING_ARTBOARD_PROMPTS_3.md).

## Chapter 09 against the delivered bundle (2026-08-26)

The answers to rounds 2 and 3 arrived as ten new artboards in chapter 09 (5–14). Every one was
rendered, measured in the DOM rather than read off the picture, built, and then checked on the
emulator as a plain `KRT Member` against stock that belongs to somebody else — which is the only
caller who sees both lock kinds at once.

| Artboard | What it asked for | Verdict |
| --- | --- | --- |
| 5 · Auswahlmodus | „✕ n gewählt" head, entry-level selection, group chip, checkboxes, no row actions, no FAB or nav | **was six gaps** — all closed |
| 6–7 · Ziel wählen | count in the CTA, skip hint before the write | **was two gaps** — closed |
| 8 · Speichern läuft | CTA spinner, fields locked | already right |
| 9 · Ergebnis | result as a step in the sheet, two tiles, the sentence | **was a gap** — the sheet closed silently |
| 10 · Abgelehnt (403) | canon copy, sheet open, selection survives | **was a gap** — generic „write failed" |
| 11–12 · Gesperrte Zeile | 45 % + an obligatory lock badge, refusal toast in the warning tint | **was three gaps** — closed |
| 13 · Sheet mit gesperrtem CTA | values legible, editors at 55 %, lock inline before the label | **was a gap** — closed |
| 14 · Zwei Sperr-Arten | one picture, two copies: role lock and row lock | **was a gap** — both ran off one gate |

### What only the device found

Four defects survived the measurement and died on the emulator:

- The refusal toast's bloom was several times the artboard's 25 %, because `krtBloom` scales from
  the colour's own alpha and the warning tint was passed at full strength.
- The toast passed **under** the floating action button rather than over it.
- The switcher's kind marker was added unconditionally, and the organisation names a
  Spezialkommando „SK Nebelkraehe" — the sheet read „SK SK NEBELKRAEHE".
- The selection plural said „ausgewählt" where every artboard writes „gewählt".

None of these is visible in a DOM measurement, a unit test or a screenshot of the artboard. They
are the reason the optical pass on a real device is a separate step and not a formality.

### One judgement call, stated rather than buried

Artboards 6–9 carry the line „12 Einträge · Modus LOCATION · POST inventory/bulk-rebook" under the
sheet's title. Only „12 Einträge" was shipped: the mode name and the endpoint are handoff
annotation, and no member needs the HTTP verb of the thing they just tapped.

## Chapters 02, 04, 06 and 08 against the delivered bundle (2026-08-26)

| Chapter | What arrived | Verdict |
| --- | --- | --- |
| 02 · Components | Presence and the Bereich tag marked **web-only** | app draws neither; badge value corrected to „Alle Einheiten" |
| 04 · Auth | the push-promise correction | **already right in the app** — the artboard is the stale half |
| 06 · Missionen | „Funktion an Bord" (artboard 3) | **was a gap** — signing up was one tap with no sheet at all |
| 08 · Hangar | overflow, wipe, bulk place, the collapse rule (4–11) | **six gaps** — all closed |

### Four artboards that could not be built, and why

Each of these is drawn in the bundle and has **no data or no endpoint** this app can reach. None is
faked with a dash or a disabled control, because both claim the thing exists and is merely missing
today:

- **The LTI tile** over the Hangar's org aggregate (ch. 08, artboard 1). `SquadronShipOverviewDto`
  carries `count` and `fittedCount`; `SquadronShipDetailDto` carries owner, location and `fitted` —
  no insurance anywhere.
- **„Eigenes Schiff einbringen"** in the sign-up sheet (ch. 06, artboard 3). Adding a unit is
  `POST /missions/{id}/units`, guarded by `canManageMission`, and `AddParticipantPublicRequest` has
  no ship field. No participant can reach it.
- **„EINGEREICHT · vor 2 Std. · via Discord"** on the approval screen (ch. 04, artboard 3).
  `RegistrationStatusDto` carries exactly one field: `approvalStatus`.
- **„Du wirst benachrichtigt"** in the same card's body — this one is not a gap but a
  contradiction: the artboard's own annotation beside it reads *„Never promise a notification
  here"*, and the shipped copy already follows the annotation. Sweeping both string bundles for the
  same shape found no other instance.

### One deviation, recorded rather than absorbed

The lock screen draws **one** unlock button where artboard 5 draws two — the second one opens the
same system prompt as the first, which already carries the device-credential fallback inside it.
[ADR-0013](adr/0013-the-lock-screen-has-one-button-because-the-prompt-has-two.md) has the reasoning
and what was rejected.

### What the chapter-02 note gets half right

Its web-only annotation groups „Presence/Live-Sync". **Presence** is correct: no production screen
draws the indicator or the update pill — `KrtPresenceIndicator` exists in the design system and is
called only from the dev showcase. **Live-sync** is a different thing and does ship: eight
ViewModels observe `/ws/sync` rooms and refresh silently, which draws nothing at all. The reason
given in the note — „openapi.json führt keine Presence-Daten" — is true of presence and not of the
sync rooms.

## The top bar's two ends were asked two different questions (2026-08-26)

Found on the licences screen and true of nine others. The back arrow came from the destination; the
org chip and the bell came from whether a screen happened to **publish a title**. Every pushed
screen that publishes none got all three — arrow, chip and bell — and the Hangar's title was
truncated to make room.

Four artboards draw the same head for four such screens, and none of them has a chip or a bell:

| artboard | bar |
| --- | --- |
| 07.1 Benachrichtigungen | ← BENACHRICHTIGUNGEN · „3 NEU" |
| 08.4 Hangar | ← HANGAR · ⋮ |
| 13.1 Einstellungen | ← EINSTELLUNGEN |
| 15.1 Open-Source-Lizenzen | ← OPEN-SOURCE-LIZENZEN |

Both ends now come from one predicate — is this destination in the navigation set for this form
factor — which also makes the phone/tablet split fall out for free: Hangar, Raffinerie and
Materialbörse are pushed behind „Mehr" on a phone and roots on a tablet's rail, and the bar follows
without a second rule. `REQ-APP-UI-005` and `TopBarOwnershipTest`.

## Chapter 07 against the delivered bundle (2026-08-26)

The inbox matched on everything structural — the type glyph per kind, the 3 dp orange rail on an
unread row, both swipe directions, „Alle als gelesen markieren" / „Gelesene löschen", the `99+` cap
and the „Zeige die neuesten n von m" line. Two things did not.

**The unread count was in the wrong place.** The app drew it as an orange line above the first row;
artboard 1 puts it in the top bar as a chip beside the title. The difference only shows up once the
list is scrolled: the app's count scrolled away with the content, which is precisely when a member
wants it. Moved to `ProvideScreenTopBar(actions = …)`, and the screen test now asserts the list body
has **no** count, so the old placement cannot come back unnoticed.

**Timestamps were one rung of a four-rung ladder.** The app rendered everything through
`DateUtils.getRelativeTimeSpanString` with default flags — „Vor 5 Stunden" — where the chapter writes
`vor 4 Min.`, `vor 2 Std.`, `gestern, 21:14` and `15.08., 09:30`.

Three separate things were wrong there, and only the first was obvious:

1. **Not abbreviated.** One flag, `FORMAT_ABBREV_RELATIVE`.
2. **Capitalised.** The platform capitalises a standalone span; every artboard writes it lower-case.
   Lowered on the first character only, in the resolved locale, so a locale opening on a proper noun
   keeps it. English is unaffected — its abbreviated spans start with the number.
3. **No day rungs at all.** This is the one that needed measuring rather than reading. The obvious
   candidate, `getRelativeDateTimeString`, was probed with a throwaway Robolectric test before any
   code was written, and it turned out to answer *every* distance with a fully qualified date —
   `26.8.2026, 18:15` even for four minutes ago — and to never say „gestern" in German. So the lower
   two rungs are composed here, from a translatable date pattern, while the upper two stay with the
   platform.

That probe paid for itself twice more. `FORMAT_NUMERIC_DATE` renders „15.8." in German where the
artboard writes „15.08.", which is why the date is a padded pattern and not a platform flag. And a
countdown two days out comes back as „Übermorgen", not „in 2 Tagen" — the platform's word, kept,
because German has one and a literal count is what a language without the word falls back to.

The formatter existed **three times**, privately, in the inbox, the Kartellbank and the dashboard.
All three now share one ladder; three copies is how „gestern" ends up looking different on two
screens of the same app.

Device-verified: „← BENACHRICHTIGUNGEN [7 NEU]" in the bar, „vor 5 Std." on the rows, and
„morgen · TS 21:44" on the dashboard, which had read „Morgen · TS 21:44" before. The „gestern" and
„15.08." rungs are **not** device-verified and are recorded as such in `REQ-APP-NOTIF-013`: every
notification in the test stack is minutes old, and the emulator runs a Play image that cannot be
rooted to move its clock.

## Chapter 03 against the delivered bundle (2026-08-26)

Two frames — the phone bar and the tablet rail — plus ten binding rules. Both frames match, down to
the rail promoting Hangar, Raffinerie and Börse out of „Mehr" on the larger screen. Of the rules,
six held, one was crashing, two were broken and one is still open.

The crash is written up separately (`REQ-APP-NOTIF-014`, ADR-0014): the chapter's „Push →
Ziel-Screen direkt" was not merely unverified, it killed the app, and the whole rule could only be
checked once that was fixed. It then held on the first try, back stack and all.

**Unknown route → 404.** `basetool://voelligunbekannt` opened the dashboard, silently. The
uncomfortable part is that `destinationOf`'s KDoc already described the correct behaviour — "the
caller renders the in-fiction Signal Lost screen rather than silently falling back to the
dashboard" — next to a call site reading `destinationOf(route) ?: KrtDestination.Home`. A comment
that describes the opposite of its code is worse than no comment: it answers the reader's question
and sends them away.

The first fix was wrong in an instructive way. Registering a catch-all `basetool://{route}` on the
404 destination looked exactly right — a literal host is more specific than a wildcard, so the
literal should win. Navigation does not rank by specificity but by **how many arguments a match
fills**: the wildcard fills one, every literal route fills none, and the wildcard outranked all of
them. Every deep link in the app landed on „Signal Lost", which is a total inversion of the intent
and produced no warning at compile time and none in review. The device found it in one command.
Asking `navController.graph.hasDeepLink(uri)` instead cannot drift, because it is the graph.

A second detail only the device showed: the „ZURÜCK ZUR BASIS" CTA pushed a *second* Übersicht on
top of the first, so afterwards back on Übersicht landed on Übersicht rather than leaving the app —
quietly breaking a different rule of the same chapter while fixing this one.

**Predictive back** is asked of every screen. targetSdk 37 gets it free on Android 16+, which is
what the emulator runs; minSdk 30 means members on 13 through 15 needed
`enableOnBackInvokedCallback`, which was not set. Now set, with `tools:targetApi="33"` rather than a
blanket lint ignore — lint is right that the attribute predates minSdk and wrong that it is a
problem. This is the one item in the chapter that cannot be device-verified here, and it is recorded
as resting on the platform contract.

**Re-tap scrolls to top** — closed in the same pass. Popping already happened; scrolling could not,
because no screen held a `LazyListState` and `animateScrollToItem` appeared nowhere in the app. The
part worth recording is why a flag would not do: the pop *rebuilds* the destination, and the rebuild
restores the list from saved state, so the new screen is already back where the member left it and
has no way to tell „I came back" from „I asked for the top". A per-route counter that outlives the
rebuild does, and each list remembers in its own saveable state which value it last acted on. One
shared counter — the obvious version — would make every screen jump to the top on its next
composition, so a member who once re-tapped „Lager" would afterwards lose their place in „Aufträge"
for no visible reason.

Verified on „Aufträge", which is the only bar destination whose list is longer than the screen.
„Einsätze" and „Lager" fit, so they scroll nowhere and a pass on them would have proved nothing —
the first attempt at verification did exactly that and looked like a success.

## Chapter 05 against the delivered bundle (2026-08-26)

The dashboard had every element the chapter asks for and looked like a different screen, which is
the interesting kind of miss: nothing was absent, six things were slightly off, and slightly off
six times over is a redesign.

**Measuring beat reading, twice.** The shortcut tiles looked like a copy problem — „Einbuchen" where
the artboard writes „Einbuchen (Lager)" — and the obvious fix would have been to lengthen the
strings into tiles that cannot hold them. Querying the DOM gave 194 dp tiles in a 2-column grid on a
412 dp frame, and the tile itself a flex **row** with a 22 dp glyph beside the label rather than
above it. The labels were short because the layout was wrong; lengthening them alone would have
produced four ellipses.

**A rendering artifact nearly became a finding.** The mission card's status marker measured
`background: transparent, border: 0` in this chapter, which reads as "the app's bordered chip is
wrong". Chapter 02 — the component canon — shows the same class *with* its 8 dp square dot, next to
a separate larger badge that does have the border and the uppercase. So the difference was real but
not the one it first looked like: the app was using the **page-level badge inside a list**, and the
design system's own KDoc had already said which of the two belongs where. Checking the canon chapter
rather than trusting one chapter's computed style is what separated those two readings.

**The announcement was the one real gap**, and it was invisible because the test stack had no
announcement at all: `GET /announcement` answers `204`, the band hides, and the screen looks
complete. Creating one through the API surfaced that the unread marker and the mark-read action
were never built. Probing the contract first paid for itself again — `lastReadAnnouncementId` lives
on `/users/me`, not on the notice, and **editing an announcement keeps its id**, so a rewritten
notice stays read. That last one is the server's model, shared with the web app; the app reports it
rather than inventing a second notion of freshness.

Not adopted: the artboard's date reads „Sonntag, 17.08.2956" — the in-fiction Star Citizen year.
The numeric **format** is adopted; the year is not. Writing error copy in character is a different
decision from misstating today's date, and that one is the owner's.

## Chapter 14 against the delivered bundle (2026-08-26)

The chapter's rules held better than any other so far, and the two places they did not are both
places where the app cannot simply be corrected.

**Held:** the lock-screen rule, enforced by construction rather than per call site — the channel is
`VISIBILITY_PRIVATE` and every posted notification carries a „Neue Benachrichtigung" public version,
so no amount or name can be read off a locked device. The 24 dp alpha-only small icon with the brand
accent. The forced-update wall standing outside every other gate, with its store-button deviation
already recorded. The 429/503 countdown with its 3→6→12→30 backoff. The in-fiction titles („Access
Denied", „Signal Lost", „System Malfunction") over one plain German line, which is the shape the
chapter asks for.

The offline band deserves a note for a different reason: it already **records what it leaves out**.
Its KDoc says there is no „Zuletzt aktualisiert" stamp and no CACHE chip because "the app holds no
cache and records no load time, so any timestamp would be invented". That is the discipline this
audit is for, applied before the audit reached it.

**The dead channel.** `krt_operations` was created at high importance on every start and nothing
ever posted to it — every notification went to `krt_general`. A channel nothing uses is not a
harmless leftover: it is a switch in the member's system settings that silences nothing, and they
have no way to discover that. Deleted on start.

Behind it sits the reason the five channels the chapter names cannot exist yet, and it is not in
this repo: the stream event is `name="notification", data="new"`. A bare ping. Everything in the
shade is the same message because the signal carries no kind — and the same absence is why a tapped
notification always opens the inbox rather than the target screen chapter 03 asks for.

**The 409 dialog** is the largest thing chapter 14 leaves open, and the interesting part is that its
copy cannot be adopted. The artboard names the other editor („von Rhea") — the 409 carries no
identity — and promises the input is on the clipboard, which would mean writing a member's data
somewhere every other app on the device can read. The app's existing sentence is the accurate one.
So the gap is presentation only: eleven write surfaces show an inline `KrtFieldError` where a modal
belongs, and each needs a reload path it does not have.

## How this audit was made

- Chapters parsed for their `<sc-for>` templates: what each list repeats and which fields it shows.
- Those field names probed against the screen sources, then the notable hits read line by line.
- `KrtCard` and design-system component usage counted across the app.

A probe hit is a lead, not a verdict — a field can be rendered under a different name. Rows marked
**check** are leads; rows marked **gap** and **done** were read.
