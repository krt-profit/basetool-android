# Handel — the material catalogue and what the universe pays for it

> **Doc type:** Living spec · **Area:** `REQ-APP-MAT-*` · **Design:** `docs/design/android/16 Materialien.dc.html`
> **Server contract:** main repo `MaterialController`, `ProfitCalculationController`
> **Related:** [`api-contract.md`](api-contract.md), [`inventory.md`](inventory.md), [`ui.md`](ui.md)

The trade reference: every material with its two best prices, and one material's prices across every
terminal that trades it, the Material × Terminal price matrix, and the profit a full load of one
ship makes. **Read-only throughout** — prices come from the UEX sync and nothing in the app writes
one. Design chapter 16.

One entry in „Mehr", named „Handel", because chapter 16 puts the Preis-Übersicht (artboard 3) and
the Profitberechnung (artboard 4) in this screen's own overflow rather than in the navigation list.
All four artboards of the chapter are built.

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

### REQ-APP-MAT-003 — Die Preis-Übersicht: the Material × Terminal matrix

`GET /api/v1/materials/matrix`, `size=1000`, `sort=material.name,asc`, walked page by page.

**Drawn as it arrives.** Design ch. 16 artboard 3 rules a full-screen spinner out by name:
„Nachladen zeilenweise … die Ladezeile bleibt unten stehen und wird nie durch einen Vollbild-
Spinner ersetzt." Every page that lands is published, so the rows are readable while the rest is
still coming and the loading line sits underneath them.

**The one surface in this app that scrolls sideways**, by the design's own instruction: comparing a
material across terminals *is* reading along a row. The material column is 104 dp and stays put;
the terminal columns scroll under the header. Header and body share **one** scroll state — two
would drift apart on the first fling and put every price under the wrong terminal.

**One mode chip, not two figures per cell** (Verkauf | Einkauf). A cell carrying both would need
twice the width and stop being scannable. The row's best value is **tinted** — never bolded, never
given a second hue — and „best" flips with the mode: the dearest buyer on the sell side, the
cheapest seller on the buy side.

**The filters describe the data.** The star-system chips are the systems present in what has been
read, so a chip can never offer a narrowing that yields nothing; and a system filter **drops the
columns it emptied** rather than leaving a hundred all-dash columns standing.

A failure part-way through the walk keeps the rows already read and says the read stopped — a
half-matrix presented as the whole one is exactly what ADR-0104 forbids.

**Acceptance**

- [x] Rows are on screen while later pages are still arriving
- [x] Header and body scroll together
- [x] The tinted value follows the mode
- [x] A system filter removes the columns it emptied
- [x] A refused page keeps what arrived and reports the failure

**Enforced by:** `MaterialMarketViewModelTest`.

---

### REQ-APP-MAT-004 — Die Profitberechnung: one full load, priced per material

`GET /api/v1/materials/profit-calculation?shipId=…&starSystemNames=…`, gated
`KRT_MEMBER | OFFICER | ADMIN`. The ship list comes from `/ship-types` (page-walked, `scu > 0`
only — a full load of nothing is not a calculation) and the star systems from the distinct
`starSystemName` of `/terminals` (page-walked), which is where the web takes them from too.

> [!important] It is a **ship** calculation, not a material one
> Chapter 16's first draft read it as a quantity-and-quality form and corrected itself. The input is
> a hull plus a system filter; the answer is one row per material for a full load of that hull.

**Every figure is the server's.** The app renders `profitPerScu`, `maxProfitFullLoad` and
`marginPercent` and computes none of them: a margin is money advice, and one derived on the device
could not be reconciled with the web's own answer.

**The C2 Hercules Starlifter is preselected**, as in the web, and the calculation runs immediately —
a member who picked a ship has asked the question. With no C2 in the catalogue **nothing** is
chosen and the screen asks for a ship rather than guessing one.

**Both hint lines are the web's own wording.** „Berücksichtigt nur Auto-Load Terminals." always;
„Hull C Sonderregel (Loading Dock) aktiv." only for that hull.

> [!note] A deliberate narrowing of the web
> The web shows both hints unconditionally. The artboard makes the Hull-C line conditional, the
> design spec outranks behavioural parity, and the conditional version is the more truthful one —
> the Loading-Dock rule only changes the arithmetic for that hull.

**Without a ship the table is a sentence, not a skeleton** — a skeleton would pretend a calculation
is running that nobody asked for. A refused calculation **drops the previous answer**: leaving it
under a new ship's name would be a figure about the wrong hull.

**Three of the web's seven columns on a phone** — Material, Max Profit, Marge — with „Gewinn / SCU"
added on a wide window. Seven columns do not fit 412 dp, and the rest stay web-only rather than
being squeezed into an unreadable row.

> [!warning] The artboard's route sub-line („Lorville → ARC-L1") is **not** built
> `ProfitCalculationDto` names no terminals at all. The design handoff flags this itself as an
> unbacked proposal. On the design gap list.

**Acceptance**

- [x] Only hulls with a positive hold are offered
- [x] The C2 is preselected and calculated at once; an absent C2 leaves the picker empty
- [x] Excluding a system sends the remaining ones; excluding none sends an empty list
- [x] The Hull-C hint is tied to the hull
- [x] A refusal clears the rows rather than leaving them under another ship

**Enforced by:** `MaterialMarketViewModelTest`.
