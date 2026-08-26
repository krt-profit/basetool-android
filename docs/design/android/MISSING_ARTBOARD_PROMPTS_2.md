# Prompts for the missing artboards — round 2

Round 1 is [`MISSING_ARTBOARD_PROMPTS.md`](MISSING_ARTBOARD_PROMPTS.md) and is closed: all three
surfaces it asked for were delivered in the 2026-08-25 bundle.

This round comes out of the artboard-by-artboard parity pass recorded in
[`docs/DESIGN_PARITY_AUDIT.md`](../../DESIGN_PARITY_AUDIT.md), where every chapter was **rendered**
and compared against the running app on three emulators. It asks for **eight artboards and two
corrections**, and each one exists for the same reason: the implementation had to make a decision
the spec does not draw, and a decision made in Kotlin is a decision nobody reviewed.

Two things this round deliberately does **not** ask for:

- **Screens the app is missing.** There are none left. Every artboard in the bundle has a screen
  behind it as of 2026-08-26.
- **More component swatches.** Chapter 02 is close to complete. What is missing is not another
  drawing of `KrtPanelHeader` — it is an artboard of a *screen* that uses one, so the placement is
  a design decision rather than an implementer's guess.

---

## Shared preamble

> You are extending an existing, delivered high-fidelity UI specification: the **Profit Basetool
> Android companion app**. Kotlin + Jetpack Compose, Material 3, minSdk 30, **dark only**.
> Match the existing bundle exactly: one page per screen area, a phone artboard at **412 × 915**
> plus a tablet artboard at **1280 × 800** where the surface differs, numbered section labels,
> handoff notes underneath. 1 CSS px = 1 dp. The DAS KARTELL design system is binding and mirrored
> in `_ds/`: radius 0, Lato only, brand orange `#E77E23`, no emoji, no icon library beyond the
> in-house stroke set, no native-styled dialogs. Copy is German-first, military-terse, labels
> UPPERCASE. Draw every state the surface can be in — default, loading, empty, error, disabled — as
> separate artboards.
>
> Three constraints that have bitten this project and are not negotiable:
>
> 1. **The app has no push channel** (resolved decision Q2). Never draw copy that promises a
>    notification.
> 2. **The app holds no role list.** It cannot know whether the member may perform an action; the
>    server answers 403 and the app reports it. Do not draw a permission-dependent control as
>    hidden — draw it as present and refusable.
> 3. **Everything you draw must be backed by a field that exists on `openapi.json`.** Where you want
>    something that does not, say so in the handoff note as a request to the backend rather than
>    drawing it as if it were there.

---

## A · Chapter 08 — the Hangar's overflow, drawn

Artboard 08.1 shows a `⋮` in the top bar and the handoff note names its three entries — *„Overflow
(⋮): Home-Location setzen (bulk), Hangar leeren (type-safe danger modal), Import"* — but nothing
draws any of them. All three are now built, from that sentence alone.

### A1 · The open overflow menu

**Draw:** the Hangar with its menu open.

The implementation followed `.assoc-pop` from `krt-components.css` (260 dp wide, `--color-bg-dark-gray`
fill, orange frame, hairlines between entries) because it was the closest thing in the system. That
was a guess.

**States to draw:** open with all three entries enabled; open with two entries disabled (an empty
hangar cannot be emptied and has no fleet to relocate).

**Answer in the handoff notes:**

- Does a disabled entry stay visible, or leave? The build keeps it, so the menu does not change
  shape between openings — confirm or overrule.
- Is the destructive entry tinted in the menu, or only in the modal it opens?
- Does the menu carry leading glyphs? The build does.
- Where does it anchor on a tablet, where the bar is wider?

### A2 · „Home-Location setzen" — the bulk sheet

**Draw:** the bottom sheet behind the first entry. One location for the whole fleet
(`POST /api/v1/hangar/ships/home-location`, body `{locationId}`).

**States:** nothing picked yet; a location picked; saving; refused.

**Answer:** does the sheet say how many ships it will move? The build does not, and the count is
available. Is a confirmation needed for a bulk write that is not destructive?

### A3 · „Hangar leeren" — the danger modal

**Draw:** the modal. The 08.3 note quotes its copy — *„Alle 3 Schiffe löschen?"* — and the 08.1 note
calls it a **type-safe** danger modal. Those two are not the same thing, and the build had to pick:
it names the count and does **not** ask the member to type anything.

**Answer:** which is it? Chapter 02 §7 reserves the type-to-confirm hurdle for *irreversible admin
actions*. Emptying a personal hangar is reversible only by re-importing, so it is a genuine
question, not a formality.

---

## B · Chapter 09 — selection mode and the bulk sheet

Chapter 02 §4 canonises the interaction (long-press → 3 dp orange inset bar + check → bottom action
bar) and chapter 09's handoff names its use — *„Long-press starts selection mode → bulk Umbuchen via
bottom action bar"* — but no chapter-09 artboard shows the Lager in that mode.

### B1 · The Lager tree in selection mode

**Draw:** artboard 09.1's tree with two entries selected.

The Lager is a three-level tree — Material (group) → Nutzer/Stack → Eintrag (leaf) — and the canon
row in chapter 02 is a flat row. The build selects **leaf entries only** and leaves group and stack
rows untouched, because a group's rows can span several places and "select the group" has no single
meaning. That is a guess.

**Answer:**

- Can a group or a stack be selected as a whole? If yes, what does selecting a group whose entries
  sit in three different hangars mean?
- What happens to the per-row actions (Buchen, Zuordnen) while the mode is on? The build leaves them
  in place and they still work, which may be wrong.
- Does the group header show how many of its rows are selected?
- Does collapsing a group with selected rows inside keep or drop the selection?

### B2 · „Umbuchen" — the bulk sheet

**Draw:** the sheet behind the action bar's CTA
(`POST /api/v1/inventory/bulk-rebook`, mode `LOCATION`).

The endpoint reports two numbers: **rebooked** and **skipped** — a row already at the target is
skipped, not moved, and not an error.

**States:** location not yet picked; picked; saving; a result with skipped rows; refused.

**Answer:** how is the result told? The build closes the sheet and reloads, which throws the two
counts away. A member who moves twelve stacks and sees eleven move deserves the sentence that
explains the twelfth. Is that a toast, a line in the sheet, or a result step?

---

## C · Components the canon defines that no screen places

Each of these exists in chapter 02 and is used by **no screen**, verified by counting call sites
across the app. Two of them are almost certainly design decisions that were never made, rather than
components nobody needed.

### C1 · `KrtPanelHeader` — which detail screens fold?

Its own documentation says it is *„used to fold long detail screens (Finanzen, Teilnehmer, …) into
scannable sections"*, with an orange leading bar, a count and a chevron. Chapter 02 §2 draws it with
`FINANZEN 4`. **No detail artboard uses it.** The mission detail (06.2) draws seven tabs instead,
and the Operation detail (06.5) draws flat section titles.

**Draw:** one detail screen with folding sections, so the relationship between tabs and panels is
decided. **Answer:** are panels an alternative to tabs, a level below them, or the tablet's answer
to the phone's tabs? Which sections start folded?

### C2 · `KrtChipSelect` — „Funktion an Bord" has a field and no screen

Chapter 02 §6 draws it with a `PILOT` chip labelled *„Funktion an Bord"*. The API has exactly this:
`MissionParticipantDto.desiredMissionJobType` and `plannedMissionJobType`, writable through
`UpdateParticipantRequest`. **No artboard shows a screen with that field**, so the app does not show
it at all — a member cannot say what they want to fly, and a mission manager cannot assign it.

**Draw:** the Teilnehmer tab of 06.2 with the field on a participant row, and the sign-up sheet
(06.3) with the member choosing their own. **Answer:** who may change the *planned* type as opposed
to the *desired* one, and how does the row show the difference when they disagree?

### C3 · `KrtDepartmentTag` — where does a Bereichsfarbe apply?

The component pins fixed department colours and its documentation says *„only ever used where that
department actually applies"*. **No artboard shows one.**

**Draw:** the surfaces where a department is a fact about the row — or say in the handoff note that
the Android app has none, in which case the component should be marked as web-only rather than
sitting in the Android canon unused.

### C4 · `KrtPresenceIndicator` — no data and no placement

A pulsing orange dot plus the names of peers editing right now. Chapter 02 §3 draws it under
„Page-level status badge + presence". **No screen artboard shows it, and the Android app receives no
presence data at all** — the web app's presence WebSocket is not consumed here.

**Answer first, draw second:** should the app have presence? If yes it needs a backend seam, and the
artboard should show which surfaces carry it and what happens when the peer list is long. If no, the
component is web-only and should be labelled that way.

### C5 · `KrtRecordCard` — which tablet screens keep a table?

Chapter 02 §5 draws the rule — *tablet keeps tables, phone collapses to key-value cards* — using
Lager data. But chapter 09's tablet artboard draws the **blueprints master-detail**, not the stock
table, and chapter 08's tablet artboard draws a ship table without showing what it becomes on a
phone.

**Draw:** the phone collapse for at least one real table, so the rule has one worked example in a
screen rather than in the component sheet.

---

## D · Two corrections to artboards that already exist

Neither is a drawing problem; both are statements the running system contradicts.

### D1 · 08.3's paste example is refused by the endpoint

The Fleetview import's paste box shows `{"ships": [{"name": "Meridian", "type": "Carrack"}]}`.
`POST /api/v1/hangar/import/fleetview` answers that with **400 — „The uploaded file must contain a
JSON array at the root"**, and it accepts three formats it names itself: *CCU Game Fleetview,
HangarXPLOR Shiplist, Fleetyards JSON*. Verified against the live stack by sending the app's exact
multipart: the object form is refused, the array form returns 200 with the tally.

**Please redraw the hint as an array**, and name the three accepted formats in the artboard the way
the server does.

### D2 · 04.3 promises a push that cannot arrive

The approval-pending screen's footer reads *„Automatische Prüfung alle 60 s — Push bei Freigabe."*
The app has **no push channel at all** (resolved decision Q2); an approval arrives through the poll
or not at all. The implementation ships the first half and drops the second, because promising a
notification that can never come leaves a member watching a lock screen.

**Please drop the second half**, or, if push is meant to be reconsidered, raise it as a decision
rather than as copy.

---

## What is deliberately still open, and needs no artboard

For completeness, so the next round does not re-ask:

- **15.3 „Kein Browser erkannt"** is drawn and built. It is unverified on a device only because
  every emulator here has a browser; that is a testing gap, not a design gap.
- **`KrtDataValue`** is an internal building block of other components. It needs no screen artboard.
- **`KrtUpdateAvailablePill`** is unused on purpose: the app reloads in place on a live signal and
  preserves an open draft, so there is no yanked state to warn about. Drawing it would mean
  *withholding* a peer's change, which is a behaviour decision — raise it as one if it is wanted.
- Several artboard details are unbuilt because the wire carries no field for them, listed under
  *„Deliberately not built, with the reason"* in the parity audit. Those are backend questions, not
  drawings.
