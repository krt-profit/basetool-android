# Handel — the material catalogue and what the universe pays for it

> **Doc type:** Living spec · **Area:** `REQ-APP-MAT-*` · **Design:** `docs/design/android/16 Materialien.dc.html`
> **Server contract:** main repo `MaterialController`, `ProfitCalculationController`
> **Related:** [`api-contract.md`](api-contract.md), [`inventory.md`](inventory.md), [`ui.md`](ui.md)

The trade reference: every material with its two best prices, and one material's prices across every
terminal that trades it. **Read-only throughout** — prices come from the UEX sync and nothing in the
app writes one. Design chapter 16, artboards 1 and 2.

One entry in „Mehr", named „Handel", because chapter 16 puts the Preis-Übersicht (artboard 3) and
the Profitberechnung (artboard 4) in this screen's own overflow rather than in the navigation list.
Those two are **not built yet** and are the remainder of this area.

---

### REQ-APP-MAT-001 — Die Material-Übersicht: the whole catalogue, filtered on the device

`GET /api/v1/materials/prices-overview`, page-walked to completion (`size=500`, `sort=name,asc`).

**The whole catalogue is held, not paged on screen.** The two price filters the design draws —
„Min. Einkaufspreis" and „Max. Verkaufspreis" — are **not** query parameters on that endpoint, so
filtering them over a partially loaded list would answer from a fraction of the universe while
looking like a complete answer (ADR-0104's rule against a silent cap). The web makes the same call
with `size=10000`. Roughly two hundred rows is a cheap thing to hold and an expensive thing to get
wrong.

Search, the two bounds and the category chip therefore all narrow **locally**, and they compose
rather than replacing each other. „Filter zurücksetzen" clears all four.

**A bound drops the rows that have no such price.** „Mindestens 50" is a question about a price, and
a material nobody sells has no answer to it — keeping it would list a material nobody trades as a
match. A bound that does not parse (a separator typed before its first digit) narrows nothing, because
a moment in typing is not an instruction.

**A missing price is an em dash, never `0,00`.** „Nobody trades it" and „it is worth nothing" are
different facts and only one of them is true.

> [!warning] The row's subtitle is the **category**, not „Veredelt · SCU"
> The artboard reads the type and the unit into each row. `MaterialPriceOverviewDto` carries
> neither — it carries the category, which is what the web itself groups by, with „Unsortiert" as
> the fallback. Filling the artboard's subtitle would mean a second request per row. The **detail**
> page does draw type and unit, because `/materials/{id}` answers with the whole record. On the
> design gap list.

The chips are the categories **the data actually has**, so no chip can offer a narrowing that yields
nothing. „Alle" is first.

**Acceptance**

- [x] The list read is page-walked until the server's own `totalPages` is exhausted
- [x] Search, category and both bounds narrow together and reset together
- [x] A row without the bounded price is dropped by that bound
- [x] An unparseable bound narrows nothing
- [x] A refusal is a failure state, never an empty catalogue

**Enforced by:** `MaterialsViewModelTest`.

---

### REQ-APP-MAT-002 — „Preise und Terminals": one material's market

`GET /api/v1/materials/{id}` for the record, then `GET /api/v1/materials/{id}/prices`, page-walked
for the reason the list is: the terminal filter is local, and a filter over half the terminals is a
wrong answer rather than a short one.

**The record is read first.** A 404 on it is the design's „Material nicht gefunden" and has to reach
the screen as a failure — an empty price table says „Keine Preisdaten verfügbar.", which is a
statement about a material that *does* exist and a different, untrue thing to tell somebody who
followed a dead link.

**The two figures at the head are a selection, not a computation.** The dearest buyer and the
cheapest seller are rows the server sent, shown with their own terminal names: a price without the
place it applies at answers half the question. A terminal that pays but does not sell can never
become the „cheapest seller".

**The table stays a table on the phone.** Chapter 16 grants this as an explicit exception to the
design system's „a table is the tablet shape" rule — comparing prices *is* reading down a column.
The way it stays inside the rules is that the **column headings shorten** on a narrow window
(„Einkauf" / „Verkauf" instead of „Einkaufspreis" / „Verkaufspreis") rather than the page scrolling
sideways. The best row on each side is emphasised where it stands.

Three empty states, kept apart because they say different things: „Keine Preisdaten verfügbar."
(the material has no prices), „Keine Terminals gefunden." (the filter matched none), and chapter
14's failure picture for a material that does not exist.

**Acceptance**

- [x] The two head figures name their terminals
- [x] A row missing one side never becomes that side's best
- [x] The terminal filter narrows the table and not the head
- [x] A 404 on the material is a failure; a material with no prices is a result
- [x] The headings shorten on a compact window and the page never scrolls sideways

**Enforced by:** `MaterialDetailViewModelTest`.

---

## Still to build in this area

- **Preis-Übersicht** (chapter 16 artboard 3) — the Material × Terminal matrix behind
  `GET /api/v1/materials/matrix`, with a sticky material column and a Verkauf/Einkauf mode chip.
- **Profitberechnung** (artboard 4) — `GET /api/v1/materials/profit-calculation?shipId=…`, whose
  ship list and star systems have to be page-walked out of `/ship-types` and `/terminals`. The
  artboard's „Gewinn" column and its route sub-line are marked by the design handoff itself as
  unbacked proposals; the DTO carries `profitPerScu`, `marginPercent`, `fullLoadCost` and
  `maxProfitFullLoad`, and **no route at all**.
