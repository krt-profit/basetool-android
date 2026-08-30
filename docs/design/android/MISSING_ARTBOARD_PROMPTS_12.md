# Round 12 — where the delivered artboards and the API disagree

Round 11's chapters arrived and were built: **chapter 06 artboards 15–17** (Operationen anlegen,
bearbeiten, Auszahlung zurücknehmen), **09 artboards 20–21** (Sammel-Ausbuchen, Game-Item-Bestand),
**11 artboards 6–7** (Raffinerieauftrag bearbeiten, löschen), **12 artboards 9–10** (Direktbuchung,
Freigabe-Limits), **17 artboards 1–6** (Item-Seite der Börse, Eintrag bearbeiten, Sammelaktionen,
Mehrfach-Blueprints, Blueprint-Verfügbarkeit), and chapter 06 artboard 12's Personen composition.
Everything is on `feat/order-production`, tested, `./gradlew build` green.

This round is therefore **not** a list of things waiting for a drawing. It is the shorter and
sharper list of places where a delivered artboard asks for something the API cannot serve, or
states a rule that is not the rule. Every item names the endpoint or DTO that decides it, so none
of this has to be taken on trust.

> [!important] Nothing here is a blocked feature
> Each item ships today with a fallback, named below. What we need from the design side is a
> decision: **drop the element**, **redraw it around what exists**, or **ask the backend to grow
> the field** — the third being a real option, and § D lists the four cases where we think it is
> the right one.

The three kinds are kept apart on purpose, because they need different answers:

| § | Kind | What the design side has to do |
| --- | --- | --- |
| **A** | Drawn, but **no wire field exists** | Drop it, or ask for the field (§ D) |
| **B** | Drawn, but the stated **rule is wrong** | Correct the statement |
| **C** | **Not drawn at all**, and the web has it | Draw it, or declare it web-only |

---

## § A — Drawn, but there is no field to carry it

### A1 · Operation: „Beginn" und „Ende (geplant)" — ch. 06 artboard 15

`OperationCreateDto` and `OperationUpdateDto` carry a name, a description, a status and the owning
unit. **Neither has a time field of any kind.** An Operation has no times of its own; they live on
its Einsätze, and the roll-up derives from them.

*Today:* both fields are absent, and a `KrtHint` says where the dates actually come from.
*Question:* drop them, or is an Operation supposed to gain its own planned window?

### A2 · Operation: „Einsätze zuordnen" — ch. 06 artboard 15

Also not a field. A mission joins an Operation through **its own** core section —
`PATCH /api/v1/missions/{id}/core` with `operationId`, which needs that mission's name **and** its
core version counter. A picker in the Operation form would have to read every candidate mission in
full before it could write one.

*Today:* a `KrtHint` names the Einsatz as the place the assignment happens.
*Question:* is the assignment supposed to live on the Einsatz (where the API puts it), and if so
should artboard 15 lose the list — or does the Operation form need a batched endpoint?

### A3 · Sammel-Ausbuchen: „Grund", „Notiz", Herkunft-Planer — ch. 09 artboard 20

`POST /api/v1/inventory/bulk-checkout` carries **`itemIds` and nothing else**. No reason
(„Verbraucht" / „Verworfen"), no note, no per-row source plan — the rows are deleted whole and their
earmarks cascade away rather than being sourced.

All three **do** exist on `POST /inventory/{id}/book-out`, the rich single call. We deliberately did
**not** loop over it per row: a loop half-succeeds, which is worse than a refusal that changed
nothing (see B4 for why that matters here).

*Today:* the sheet lists the chosen rows with a „vollständig" chip each, the append-only sentence,
one CTA.
*Question:* drop the three fields from the artboard, or should `bulk-checkout` grow a reason and a
note (§ D1)?

### A4 · Item-Angebot: „Zustand" (Neu / Gebraucht) — ch. 17 artboard 1

`MaterialExchangeItemReleaseRequest` carries `productKey`, `quantity`, `remark`. There is no
condition field. **The chapter flags this itself** in its provenance note: „Zustand" is a proposal,
not derived copy, and „muss mit der Web-Seite abgeglichen … werden".

*Today:* absent.

### A5 · Item-Angebot: „Blueprint (Variante)" — ch. 17 artboard 1

No wire field either. A **product key already identifies the product**, and no write on either item
route carries a variant.

*Today:* absent; the product picker shows `variantCount` beside the name so a member can name the
variant in the remark when it matters.

### A6 · Item-Gesuch: „Bis wann" — ch. 17 artboard 2

No deadline field on `MaterialItemRequestCreateRequest`. What it *does* carry, and no artboard
draws, is **`minQuality`**.

*Today:* „Bis wann" is absent and the minimum-quality field is drawn instead.
*Question:* please redraw artboard 2 around `minQuality`; and if a deadline is genuinely wanted, it
is a backend ask (§ D2).

### A7 · Materialzeile: „Veredelt · SCU" als Unterzeile — ch. 16

`MaterialPriceOverviewDto` carries neither the type nor the unit. It carries the **category**, which
is what the web itself groups by. Filling the artboard's subtitle would cost one request per row.

*Today:* the category is the subtitle, „Unsortiert" as the fallback. The **detail** page draws type
and unit, because `/materials/{id}` answers with the whole record.

### A8 · Profit-Zeile: die Route („Lorville → ARC-L1") — ch. 16

`ProfitCalculationDto` names no terminals at all. **The handoff flags this itself** as an unbacked
proposal.

*Today:* absent.

### A9 · Herstellung: drei Elemente — ch. 10 artboard 15

The artboard draws a smaller form than the endpoint takes:

- a **single** „Zutaten aus dem Lager ausbuchen" checkbox for the whole run — the server takes a
  plan **per material** over named rows, so the sheet carries the web's per-material skip instead;
- **„Verwendete Variante"** — the payload has no variant field; blueprint-variant counting is an
  order-level setting (`PATCH /{id}/blueprint-variant-counting`);
- **„Übergeben an"** — handing over is the separate `POST /{id}/item-handovers` write.

And the artboard **omits the Einlagerung**, which the server requires.

*Today:* the per-material plan, no variant field, no handover field, and the Einlagerung is drawn
because it is mandatory.

### A10 · Materialsammelübersicht: „DREI Felder sind INLINE änderbar" — ch. 10

The web's two inline selects (Besitzer, Standort) post to `/inventory/{id}/transfer`, which is a
**proxy onto the book-out**. That is moving stock, not editing a field.

*Today:* the delivery status is editable inline; Besitzer and Standort say where to change them —
the Lager's own book-out sheet, where the amount and the earmark reductions are visible before
anything moves. A silent inline picker would move stock without showing what moves.

### A11 · „Ohne Lagerbezug erfassen" — ch. 10

The endpoint cannot serve it and the web does not offer it.

*Today:* absent.

---

## § B — Drawn, but the stated rule is not the rule

These are the ones we would most like corrected, because each one **reads as authoritative** and a
later reader would take the artboard's word for it.

### B1 · „Überzusage ist erlaubt" — ch. 10 artboard 13

It is not. `MaterialClaimService` takes a row lock on the order and **refuses** any pledge whose sum
across Staffeln exceeds what the bucket needs (`REQ-ORDERS-024`, ADR-0092).

*Today:* the refusal is rendered as exactly that — an over-pledge, named — rather than as „ungültige
Eingabe".

### B2 · „Nach dem Einlagern sperrt der Server Kern und Waren" — ch. 11 artboard 6

The server does **not**. `RefineryOrderService.updateRefineryOrder` has no status guard at all; it
clears and re-adds the goods of a `COMPLETED` order without complaint. The only „already completed
and stored" refusal in that service belongs to the **store** path.

*Today:* the app enforces it client-side — a booked run renders its refinery, method and goods
locked with the reason, money and Einsatz still open — because the invariant is real: the yield
already exists as Lager rows. But **the app is the only thing enforcing it** (§ D3).

### B3 · „Ein bereits eingelagerter Auftrag lässt sich nicht löschen" — ch. 11 artboard 7

Also not enforced. `DELETE /api/v1/refinery-orders/{id}` sets `status = CANCELED` whatever the
order's state, and the Lager rows it produced stay.

*Today:* „Löschen" is drawn **locked with its reason** on a booked run. Again client-side only
(§ D3).

### B4 · „Ergebnis nennt gelöscht/übersprungen wie das Bulk-Umbuchen" — ch. 09 artboard 20

`bulk-checkout` is **atomic**: a foreign row or an unknown id refuses the whole call with 403/404.
There is no partial outcome to report. The shape was borrowed from the bulk *rebooking*, which
really does return one (`BulkRebookResultDto`).

*Today:* the sheet shows either its done step or the refusal, and a refusal keeps the selection.

### B5 · „nur die Anmerkung ist änderbar (Web-Regel)" — ch. 17 artboard 3

Not the web rule. The web's own modal (`fragments/materialboerse-modal.html`) edits the **amount**
too, with an „Alles" shortcut and a „darf den Lagerbestand nicht überschreiten" bound — and
`MaterialExchangeOfferUpdateRequest` **requires** `offeredAmount`.

*Today:* the amount is editable; the stock bound stays the server's to enforce, because a board row
carries no stock figure to check against.

### B6 · Der 5-Sekunden-Undo nach dem Zurückziehen — ch. 17 artboard 3

Withdrawing is `POST …/deactivate` and **no endpoint reactivates a row**. An undo would have to post
a *new* entry: a different id, a new timestamp, and none of the interested members.

*Today:* the confirmation carries a „cannot be undone" sentence instead.

### B7 · Freigabe-Limits als fünfter Tab — ch. 12 artboard 10

Every limit endpoint is `…/bank/accounts/{id}/approval-limit/…` and the current values ride on
**one account's** settings. A tab would have to make the member pick an account before it could show
anything — a tab that is really a picker.

*Today:* the section sits in the **account's own settings sheet**, beside the visibility grants it
resembles: same scope, same owner, same read, reached the same way.

Two smaller things in the same artboard: it draws **three** tiers, and the API has a **fourth**
(`area-members`, drawn when the server says it is supported); and the current-value column has no
„kein Limit" state drawn, which is the common one.

### B8 · Game-Item-Zeile springt in den Lagerbaum — ch. 09 artboard 21

The app's Lager tree is **material-only** — its three reads send no `catalog` parameter — so there
is nowhere to jump to.

*Today:* a row opens **in place** and lists the holders and places that came with it: the same
information, one tap earlier.
*Question:* is an item mode of the tree wanted (§ D4), or is the in-place expansion the answer?

### B9 · „Bereits vorhandene werden nicht angeboten" — ch. 17 artboard 5

Not a disagreement — a **reversal we made on the artboard's word**, recorded here so it is not
re-litigated. The app previously listed an owned blueprint greyed out with „Hast du schon", on the
reasoning that hiding it answers the member's real question with silence. The artboard follows the
web instead and supplies the notice line as the answer to that objection. We changed to match
(`REQ-APP-PI-012`, amended and dated).

---

## § C — Not drawn at all

Three web surfaces with no artboard anywhere in chapters 00–17. We are **not** building them from
composed parts this round; they need a decision first.

| Surface | Web route | What it is |
| --- | --- | --- |
| **C1 · Auftragsübergreifender Materialbedarf** | `orders-material-demand.html` | What every open order together still needs, per material. A planning view, not a per-order one. |
| **C2 · Blueprint-Datei-Import** | `/personal-blueprints/import/{preview,apply}` | Two-step: upload, preview what matched, then apply. |
| **C3 · Blueprints „alle löschen"** | `DELETE /personal-blueprints` | Ch. 17 artboard 4 rules out a menu entry for the *items* („Alles wählen" + löschen instead). The blueprint list has no selection mode, so the same rule leaves it with no entry point at all. |

---

## § D — Four things we would ask the backend for

Recorded here rather than raised separately, because each one is the alternative to dropping a drawn
element. All four are @greluc's call.

| # | Ask | Why |
| --- | --- | --- |
| **D1** | `bulk-checkout` gains `reason` and `note` | Would let ch. 09 artboard 20 keep its two fields. Both already exist on the single book-out. |
| **D2** | Item request gains a deadline | Would let ch. 17 artboard 2 keep „Bis wann". |
| **D3** | Refinery `PUT` and `DELETE` refuse a `COMPLETED` order | Would move B2 and B3 from a client-side rule to a real one. **This is the one with a data-integrity edge**: today anybody with a direct API call can rewrite a booked run's goods. |
| **D4** | The Lager tree learns `catalog=ITEM` | Would make B8's jump reachable, and would let items live in the tree at all. |

Two smaller ones that only remove an app-side compromise:

- `GET /personal-blueprints/overview` gains an `ownerCount = 0` filter — „Nicht erfasst" currently
  narrows only the rows already loaded, and the list says so.
- A real bulk delete for personal inventory rows — the app loops `DELETE /personal-inventory/{id}`
  and counts, and reports „x gelöscht · y übersprungen" because a loop can half-succeed.

---

## What we did **not** put on this list

Composition we invented and are content to have ratified silently — the Verwaltung's section
rhythm, the Game-Item card, the limits rows. Round 11 asked about those and the answer was to build
and mark them; nothing here re-opens them.

Nor anything about the design system itself. No component, colour, spacing token or copy rule was
invented this round.
