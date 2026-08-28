# Round 8 — chapter 10 draws a „+" the chapter never opens

> Written 2026-08-28. One missing artboard and three smaller notes on chapter 10 (Aufträge).
> Round 7: [`MISSING_ARTBOARD_PROMPTS_7.md`](MISSING_ARTBOARD_PROMPTS_7.md).
> Earlier rounds: [`_1`](MISSING_ARTBOARD_PROMPTS.md), [`_2`](MISSING_ARTBOARD_PROMPTS_2.md),
> [`_3`](MISSING_ARTBOARD_PROMPTS_3.md), [`_4`](MISSING_ARTBOARD_PROMPTS_4.md),
> [`_5`](MISSING_ARTBOARD_PROMPTS_5.md), [`_6`](MISSING_ARTBOARD_PROMPTS_6.md).

---

## 1 — „Neuer Auftrag" has no artboard, but artboard 1 has its FAB

Chapter 10's nine artboards are: queue, detail, Materialbörse, „Gesuch erstellen", three note
states, status change, terminal confirmation. **None of them is the order-create form** — and
artboard 4's „Gesuch erstellen" is the Materialbörse's request sheet, a different thing on a
different endpoint.

Artboard 1 nevertheless carries an orange „+" FAB at the bottom right. A FAB that opens nothing is
the one thing a queue must not have, so the form is being built; this asks for the drawing to
follow, because the app should not be the place where a screen's layout is decided.

**What it has to hold** — the web form (`orders-create.html`) and `POST /api/v1/orders`:

| Field | Kind | Notes |
| :-- | :-- | :-- |
| Auftragsart | segment: Materialauftrag / Item-Auftrag | the app builds Material first |
| Bearbeitende Einheit | picker, required | only profit-eligible units; a Bereich may not process |
| Auftraggeber | picker, required | **any** active unit, not only the member's own |
| Handle Ansprechpartner | text, required | max 200 |
| Materialien | repeating line: Material · Menge · Min. Qualität | at least one; add and remove |
| Kommentar | multiline, optional | max 1000 |

Three things worth a decision rather than an assumption:

**1.1 — How does a material line look on a phone?** The web puts material, amount and minimum
quality in one row; at 360 dp that is three fields across, which the design system's field height
will not carry. Built as a card per line with the material full-width and Menge · Min. Qualität
side by side beneath it, mirroring chapter 11's goods card, which is the nearest existing pattern.
Confirm or redraw.

**1.2 — Where does „Material hinzufügen" sit?** Built as a ghost button under the last card, like
chapter 11's. The alternative — a trailing „+" card — reads better when the list is long.

**1.3 — Is the item order in scope for the phone at all?** It needs a game-item picker, a blueprint
picker *per item*, and a derivation tree the web renders as nested lines
(`/orders/item-catalog/{gameItemId}/blueprints`, `…/blueprints/{blueprintId}/derivation`). That is
a screen of its own, not a segment on this one. If it is wanted, it needs its own artboards.

## 2 — Three notes on artboard 1, no redraw needed

**2.1 — „#A-1042" is not a field.** The system's order number is `displayId`, a plain sequential
integer; the web renders „#1", „#2". Same class as chapter 11's „#7841" — mock data that reads as
a format. The app renders the real one.

**2.2 — The age is a day count, and it was right to draw it that way.** „vor 94 Tagen" beside a
colour is the whole point of the line; the app's shared relative-time helper switches to
„26.05., 11:19" from two days out, which makes the reader do the arithmetic the colour has already
done. The queue now overrides it and counts days. The two thresholds are the operator's
(`job_order.age_yellow_days` = 30, `job_order.age_red_days` = 90).

**2.3 — The selected filter chip carries the status tint.** Artboard 1 selects „Offen" in blue and
„In Bearbeitung" in orange — each chip tinted by its own status. The app selects both in the
accent, because the status pill inside every row already carries the tone and a filter chip tinted
per-status reads as a second, competing legend. Left as built; say if the per-status tint is
deliberate.

## 4 — Reordering the queue has no phone form

The web reorders by dragging a row in the list. A phone has neither the list on screen nor a pointer
that can hold one row while the rest scrolls, and chapter 10 draws no alternative — the priority
figure is rendered, never edited.

Built as three ghost buttons on the **order detail**, beside „Status ändern", because that is where
the app already gates a Logistician's write: **AN DEN ANFANG · HÖHER · NIEDRIGER**. The endpoint
takes an absolute position (`PUT /orders/{id}/priority?priority=N`, no version — the service
reorders under a pessimistic lock), so the buttons express the intent and compute the position.

Two things that fell out of building it, both worth a ruling:

**4.1 — There is no „ans Ende".** The back of the queue is a page count away, and a control that
guessed at the length would drop the order somewhere nobody asked for. „Niedriger" moves one place.

**4.2 — Should it be on the queue instead?** The queue is where the ordering is *visible*, which
argues for putting the control there — a long-press on a row, say. It is on the detail because the
detail already holds the Logistician grant and the write plumbing, and because a control on the
queue would need the identity read the queue does not currently do. If the queue is the right place,
say so and it moves.

An order with no priority (completed, rejected) gets no control at all: it has left the queue, and
„move it up" would be an instruction to put it back.

---

## What is already correct and needs nothing

The multi-select status filter (the app sends the whole set server-side, and an empty set means
all), the „—" chip for an order with no processing unit, the Für/Durch pair, the priority column,
the material expander with its count, and the type chip. All of it matched on the device.
