# Round 9 — the content gutter, and the item order's own form

> Written 2026-08-28. Two asks that came out of building the item order and out of a tablet review.
> Round 8: [`MISSING_ARTBOARD_PROMPTS_8.md`](MISSING_ARTBOARD_PROMPTS_8.md).
> Earlier rounds: [`_1`](MISSING_ARTBOARD_PROMPTS.md), [`_2`](MISSING_ARTBOARD_PROMPTS_2.md),
> [`_3`](MISSING_ARTBOARD_PROMPTS_3.md), [`_4`](MISSING_ARTBOARD_PROMPTS_4.md),
> [`_5`](MISSING_ARTBOARD_PROMPTS_5.md), [`_6`](MISSING_ARTBOARD_PROMPTS_6.md),
> [`_7`](MISSING_ARTBOARD_PROMPTS_7.md).

---

## 1 — Chapter 01 states no gutter rule, so eleven screens each decided for themselves

**Ruled and built, 2026-08-28 — this asks for one sentence, not a redraw.**

The owner ruled that a screen's scrolling content is inset by `KrtSpacing.md` **from a medium window
up, and not below it** (`REQ-APP-UI-010`). A phone keeps the full bleed the artboards draw; a tablet
gets the gutter. Both readings measured on the rendered chapters, phone frame `x = 32…428`, screen
area `x = 48…412`:

| Chapter | Element | Drawn | Built on a phone | Built on a tablet |
| :-- | :-- | :-- | :-- | :-- |
| 09 Lager | tree row (`Quantainium`) | `49…411` — full bleed | unchanged | inset |
| 09 Lager | holder row (`Rhea`) | `49…411` — full bleed | unchanged | inset |
| 05 Dashboard | „Willkommen" band | `48…402` — full bleed | unchanged | inset |
| 08 Hangar | ship card | inset ≈ 16 px | unchanged | unchanged |

The artboards therefore need **no redraw**: what they draw is what the phone does. What is missing
is upstream of all of them.

**The ask: state the gutter in chapter 01 (Foundations).** One line — *content is full-bleed on a
phone and inset by `md` from `sm`/medium up; cards are inset at every width* — is the difference
between a rule and eleven independent decisions. Eleven lists had no inset at any width, which is
not a series of mistakes so much as a foundation nobody had written down.

**Worth drawing eventually, though not blocking:** a tablet frame for chapter 09's tree, so the
inset case is shown rather than inferred. Chapter 09 has phone frames only, which is the same gap
§4 of this round records for the Lager's wide layout.

---

## 2 — The item order's half of „Neuer Auftrag"

Round 8 §1 asked for the create form and noted the app would build the material half first. Both
halves are now built (`REQ-APP-ORDERS-016`), still without the sub-assembly tree, so the artboard
ask stands and grows one section.

**What the item half holds:**

| Field | Kind | Notes |
| :-- | :-- | :-- |
| Auftragsart | segment: Material / Items | shared head above it, lines below |
| Item | remote combobox, required | `GET /orders/item-catalog?search=`, 25 rows |
| Blueprint | picker, required | `…/{gameItemId}/blueprints`; **shut until an item is picked** |
| Anzahl | number, required | integer, greater than zero |

Three states the drawing has to settle, all of which the app currently answers in words:

1. **No item picked** — the blueprint picker is disabled with „Erst ein Item wählen." under it.
2. **Item picked, one blueprint** — it is selected outright; the picker shows it rather than asking
   for a tap that cannot go any other way.
3. **Item picked, no blueprint on file** — „Für dieses Item ist kein Blueprint hinterlegt." The
   server would refuse the line; a dropdown that opens on nothing does not explain why.

---

## 3 — An item position in the order detail

Chapter 10 artboard 2 draws a material position: name, `have / need`, the bar. An item position now
renders in the same shape — name, `gebaut / bestellt`, the bar — plus two things the material
position has no equivalent for:

- **„Rezept veraltet"**, a warning chip, when the server sets `blueprintStale`. The blueprint has
  changed since the order was raised, so what will be built may no longer be what was costed. The
  web draws this; the app now copies it as a `KrtChip` in the Warning tone.
- **„n übergeben"**, in the primary colour under the bar, when any have been handed over.

The ask is a drawn variant of artboard 2's position row for the item case, so the two are not left
to converge by accident.

---

## 4 — The Lager gets a detail pane, and it needs drawing

**Ruled 2026-08-28.** Round 8 §5 asked what a screen with no detail pane does with a tablet's width
and offered three ways out. For the **Lager** the owner chose the largest: give it a detail pane,
like Einsätze, Aufträge and Raffinerie already have.

This is a **new screen**, not a re-hang of an existing one. The app's Lager is a pure inline tree —
every tap toggles a group or a stack and nothing opens — so there is no detail today to move into a
pane. The web has two templates that were never mapped to the app and that this closes:
`inventory-material.html` and `inventory-game-item.html`.

**What the drawing has to settle:**

1. **What a row opens.** A material row and a holder row are different things; do both open the same
   pane, or only the material?
2. **What the pane holds.** The web's material page carries the holders, the entries, the
   earmarking against orders and missions, and the quality band. Which of those belong on a tablet
   pane, and in what order?
3. **The empty state**, before anything is selected — chapter 03's „Nichts ausgewählt" card, or
   something the Lager-specific?
4. **What the phone does.** The same pane pushed as a screen, which is how the other three work.

Until it is drawn the Lager keeps the stretched list on a tablet, which is the state round 8 §5
described.

---

## 5 — The Materialbörse goes to two card columns

**Ruled 2026-08-28**, from round 8 §5's third option. The cards are self-contained — name, member,
figures, the withdraw button — so two fit side by side and the three quarters of empty card stop
being empty.

Nothing here blocks the build: it is a grid instead of a column above the same breakpoint the rest
of the tablet layouts use. What a drawing would still settle is **the column count above a tablet's
width** — two everywhere, or three on something wider than 1280 dp — and whether a card's internal
layout changes at all when it is half as wide.

