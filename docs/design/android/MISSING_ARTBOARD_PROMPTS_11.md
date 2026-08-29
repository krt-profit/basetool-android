# Missing artboards, round 11 — the Einsatz's write half, built and awaiting its drawing

Round 10 asked whether the Einsatz's Verwaltung should be a sheet or per-tab affordances, and left
everything behind that question unbuilt. **That was the wrong call and the repository owner said so
on 2026-08-29**: a missing artboard is not a reason to stop, it is a reason to build from the
design system's own drawn parts, mark the composition unratified, and ask for the drawing.

So round 11 arrives with the work **done**. Every remaining parity row of chapter 06 is built,
tested and device-verified. What follows is not a list of things we are waiting on — it is a list
of things that are **shipping without a drawing** and that we would like corrected.

> [!important] Nothing here blocks a release; everything here is a deviation
> Each item names the drawn parts it was composed from. None of it invents a component, a colour or
> a spacing token. What it does invent is **composition** — which rows sit where, in what order,
> under which heading — and that is exactly what an artboard decides.

## What round 10 settled, so it is not re-litigated

| # | Question | Answer |
| --- | --- | --- |
| 10a | Verwaltung: one sheet, or per-tab? | **An eighth tab**, „Verwaltung", drawn only for a manager. Owner, 2026-08-29. [ADR-0018](../../adr/0018-the-verwaltung-is-a-tab-and-briefing-is-renamed-out-of-a-collision.md) |
| 10b | Per-section saves, so the three locks survive | Three saves, one per section. Built. |
| 10c | Is starting an Einsatz a verb? | Yes — a filled CTA, „Einsatz läuft jetzt", inside the Zeitplan section. Still not on the status badge; see § 11g. |
| 10d | A locked control at head density | **Retired.** Nothing manager-owned lives in the head any more. |
| 10e | Is chapter 12's remote combobox right for a member? | **Yes.** Built, debounced, single-flight, with the cap stated in the notice. |

## § 11a — The Verwaltung tab itself

**Built from:** `KrtSectionTitle` per section, `KrtTextField` rows, one full-width `KrtGhostButton`
per section save, a filled `KrtCtaButton` for „Einsatz läuft jetzt", `KrtRadioRow` for the internal
flag.

Four sections, in this order: **Kern** (Titel · Beschreibung · Treffpunkt), **Zeitplan** (four ISO
timestamps + the start CTA), **Sichtbarkeit** (one radio), **Personen** (§ 11e).

What we would like decided:

- **The rhythm.** Four sections in one scrolling column is what a form does; it is not what
  chapter 06 does anywhere else. Is a section a `KrtCard`? A `KrtHudBox`? Bare, as now?
- **The in-flight state.** All three saves disable while any one of them is writing, because they
  share a form. Nothing says *which* is writing. A spinner on the pressed button, a disabled state
  on the others, or something else?
- **The section that cannot be saved.** Personen has no save — the three writes fire on the pick.
  It sits under the same kind of heading as three sections that do, which reads as an omission.

## § 11b — The Ablauf editor

**Built from:** `KrtSectionTitle`, two `KrtTextField`s, a full-width `KrtGhostButton` save, and per
row **three** rows of buttons — Abhaken/Zurücksetzen full width, then Bearbeiten · Entfernen, then
Hoch · Runter.

Chapter 06 draws the Ablauf as a numbered checklist with the current phase marked. It does not draw
a row that can be acted on.

- **Five buttons over three rows is too much furniture for one checklist line**, and we know it.
  They arrived that way by measurement rather than by choice: three German labels across a 411 dp
  row put „ABHAKEN" on two lines, so at most two share a row. Is this a swipe (`KrtSwipeableRow`
  exists)? An overflow menu (`KrtOverflowMenu` exists)? A long-press?
- **The composer is above the list.** A „+" that opens something would keep the list at the top of
  the tab, which is where the reading eye goes.

## § 11c — The Ziele editor

**Built from:** `KrtSectionTitle`, one `KrtTextField`, a `FlowRow` of three `KrtFilterChip`s for the
kind, a full-width save, and per row four weighted `KrtGhostButton`s.

The three kinds are `PRIMARY` / `SECONDARY` / `NON_GOAL` — „Primär", „Sekundär", „Kein Ziel". The
read side already draws the kind as a `KrtChip`; the editor picks it as a `KrtFilterChip`. Two
different chips for one concept, three lines apart.

- Should the **reading** chip and the **picking** chip be the same shape?
- „Kein Ziel" is a deliberate non-goal, not an absence. It reads as „no objective set" in a chip.
  Better wording, or a different tone?

## § 11d — The Einheit rename, and the Funktion an Bord

**Built from:** the existing `UnitComposer` (a `KrtTextField` + a `KrtRadioRow` + a save), reused
for renaming; per unit two stacked full-width `KrtGhostButton`s (Umbenennen, then Einheit
entfernen — „EINHEIT ENTFERNEN" is seventeen characters and will not share a row); per crew slot a
`FlowRow` of `KrtFilterChip`s over the CREW catalogue.

- **The rename reuses the composer at the top of the tab.** Tapping „Umbenennen" on the fifth unit
  fills a field that may be off-screen. An inline edit, or a sheet, or a scroll-to?
- **The crew chips are the reading of the roles now**, not a separate display — selected means held.
  Artboard 2 draws the crew as a line of text. Which is it?
- **„Person zuweisen" is a row of name chips**, one per roster member not yet aboard. Artboard 2
  annotates „+ Person zuweisen — antippen oder halten & ziehen" on a unit it does not draw: the
  **tap** half is built, the **drag** half is a gesture and needs the drawing. A unit with thirty
  candidates wraps to a wall of chips, which is the shape's obvious limit.

## § 11e — Where the three member picks sit

**Built from:** three full-width `KrtGhostButton`s under a „Personen" heading, each opening a
`KrtBottomSheet` holding chapter 12's `KrtCombobox`.

„Einsatzleitung setzen" · „Manager hinzufügen" · „Teilnehmer hinzufügen". One picker, three titles.

- Three buttons that all open the same control is a shape we invented. A single field with a
  preceding „wofür?" select would be one control instead of four.
- **The party lead is drawn in the head** („Leiter Rhea", artboard 2's fact row) and set from a tab
  eight taps away. Should the head's fact be the affordance?
- **Nothing lists the current managers.** The API returns them; no artboard draws them, so nothing
  shows them and „Manager hinzufügen" is an action with no visible state.

## § 11f — Reorder: two buttons, or a drag?

**Built from:** two weighted `KrtGhostButton`s per row, „Hoch" and „Runter", with the chevron
glyphs.

The endpoint wants the whole id list in its new order, so a one-place move produces it exactly as
well as a gesture would. Buttons were chosen because a drag is an *interaction* and inventing one
is a bigger deviation than inventing a button row.

If it should be a drag: what is the grip, what does the row look like while held, and what happens
to the other four buttons under it?

## § 11c-bis — The Ziele kind is now a German label on both sides

Fixed on the device rather than deferred: the reading chip showed the wire constant („SECONDARY")
while the picker beside it said „Sekundär". A kind the app knows now renders its own label; one it
does not is still shown verbatim, because a goal marked with an unfamiliar word beats one marked
with nothing. What § 11c still asks is whether the reading chip and the picking chip should be the
same **shape**.

## § 11g — Two things still genuinely missing

1. **A date-time picker.** The Zeitplan's four times are typed as ISO-8601 text
   (`2026-08-25T20:44:21Z`) because nothing in the design system draws a date or a time picker, and
   inventing one would be a component rather than a composition. This is the single ugliest surface
   in the app.
2. **Starting the Einsatz on the status badge.** § 10c asked and the answer never came; the CTA sits
   in the Zeitplan section instead. The badge („Geplant" → „Aktiv") is where a manager looks.

## § 11h — The constraint round 10 produced (carried forward)

**No tab, chip or in-page control may reuse a navigation label** — „Übersicht", „Einsätze",
„Aufträge", „Lager", „Mehr". The Einsatz's first tab was „Übersicht" and so is the shell's Home
destination 200 dp below it; the owner tapped the tab expecting the dashboard. It is „Briefing" now
(`REQ-APP-MIS-024`). Please check any new label against those five.

## What is correct and needs nothing

The seven reading tabs and their counts, the head's fact row, the roster row and its three manager
controls, the one-filled-CTA rule with its weights, the sign-up sheet, the locked-but-tappable
pattern for rows, and the „no silent caps" notice under a filtered list. None of that needs
redrawing.
