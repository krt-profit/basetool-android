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

## 4 — The Lager's tablet detail pane is the web's material table

**Ruled 2026-08-28.** Round 8 §5 asked what a screen with no detail pane does with a tablet's width.
For the **Lager** the owner chose the detail pane, like Einsätze, Aufträge and Raffinerie have.

**Corrected after reading the web.** An earlier draft of this section asked four open questions
about what the pane should hold. Three of them are already answered — `inventory-material.html` is
108 lines and holds one thing: a **paginated flat table of that material's entries**, columns
*Nutzer · Ort · Qualität · Menge*, plus a picker for jumping to another material. There is no
quality band, no earmarking view, nothing else. `GET /api/v1/inventory/material/{materialId}`,
paged.

That makes the pane a good fit for a tablet and a genuine addition rather than a duplicate: the
**tree** groups by holder and hides everything until it is opened, while the **table** shows every
entry of one material at once across all holders and places — which is what a thousand dp of width
is good for, and the same shape the Hangar's tablet table already uses.

| | Left pane (the tree) | Right pane (the table) |
| :-- | :-- | :-- |
| Groups by | holder, then place and quality | nothing; one row per entry |
| Shows | only what is opened | every entry of the selected material |
| Reads | `/inventory/aggregated` + `/inventory/all/grouped` | `/inventory/material/{id}` |

**What still needs a ruling, and it is one question, not four:** *what does a tap on a tree row do
on a tablet?* Today every tap toggles. If a material row now also selects the pane, toggling and
selecting share one gesture; if it does not, the pane needs its own affordance. The other three
screens do not face this because their list rows only ever select.

**On a phone** nothing changes: the tree keeps the whole screen and this pane is not reachable, the
same way the web's material page is a separate page rather than a panel.

Also worth drawing: a **tablet frame for chapter 09**, which has phone frames only — the inset case
of §1 and this pane both have to be inferred from chapter 03 today.

---

## 5 — The Materialbörse goes to two card columns

**Ruled 2026-08-28**, from round 8 §5's third option. The cards are self-contained — name, member,
figures, the withdraw button — so two fit side by side and the three quarters of empty card stop
being empty.

Nothing here blocks the build: it is a grid instead of a column above the same breakpoint the rest
of the tablet layouts use. What a drawing would still settle is **the column count above a tablet's
width** — two everywhere, or three on something wider than 1280 dp — and whether a card's internal
layout changes at all when it is half as wide.

