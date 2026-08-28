# Round 9 — the content gutter, and the item order's own form

> Written 2026-08-28. Two asks that came out of building the item order and out of a tablet review.
> Round 8: [`MISSING_ARTBOARD_PROMPTS_8.md`](MISSING_ARTBOARD_PROMPTS_8.md).
> Earlier rounds: [`_1`](MISSING_ARTBOARD_PROMPTS.md), [`_2`](MISSING_ARTBOARD_PROMPTS_2.md),
> [`_3`](MISSING_ARTBOARD_PROMPTS_3.md), [`_4`](MISSING_ARTBOARD_PROMPTS_4.md),
> [`_5`](MISSING_ARTBOARD_PROMPTS_5.md), [`_6`](MISSING_ARTBOARD_PROMPTS_6.md),
> [`_7`](MISSING_ARTBOARD_PROMPTS_7.md).

---

## 1 — The dense row lists are drawn full-bleed; the app now insets them

**The app has changed and the artboards have not.** The owner ruled, looking at the tablet, that
every screen's content sits in the same gutter (`REQ-APP-UI-010`). Several chapters draw their row
lists without one.

Measured on the rendered artboards, phone frame `x = 32…428`, screen area `x = 48…412`:

| Chapter | Element | Drawn | Now built |
| :-- | :-- | :-- | :-- |
| 09 Lager | tree row (`Quantainium`) | `49…411` — full bleed | inset by `KrtSpacing.md` |
| 09 Lager | holder row (`Rhea`) | `49…411` — full bleed | inset |
| 08 Hangar | ship card | inset ≈ 16 px | unchanged — this is the pattern the rest now follows |

The distinction the artboards make — cards inset, dense tables full-bleed — is a real one and it
reads well on a phone. It does **not** survive the tablet: with the rail on the left and a pane over
a thousand dp wide, a full-bleed row list has its first character against the rail and its last
figure against the screen edge, and it sits beside an inset Hangar, so the app disagrees with itself
screen to screen.

**The ask:** redraw the dense row lists with the same gutter the cards have — chapter 09's tree
(all levels), chapter 07's inbox rows, chapter 05's dashboard sections, and the personal-inventory
rows. If the full-bleed treatment is wanted on the phone and the gutter only on the tablet, say so
and the app will make it width-conditional; right now it is unconditional, because that is what was
asked for and because two rules are harder to keep than one.

**Also worth a line in chapter 01:** the gutter is a foundation, not a per-screen decision. One
sentence there would have prevented eleven screens drifting apart.

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
