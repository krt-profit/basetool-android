# Round 12 — the complete deviation register

Every place where the app and the design specification differ, in either direction, **verified
against the code on 2026-08-30**. Not a summary of one round: the whole standing register, so the
design side can work through it in one pass and nothing that has been carried since round 5 gets
lost.

Round 11's chapters arrived and are **all built** — ch. 06 ab. 12 and 15–17, ch. 09 ab. 20–21,
ch. 11 ab. 6–7, ch. 12 ab. 9–10, ch. 17 ab. 1–6 — and with them the
[Web Parity Programme](https://github.com/krt-profit/basetool-knowledge) has no drawn item left
open. So this register is what remains when the building stops.

> [!important] Everything here ships today
> Nothing is blocked. Each item names what the app does **now**, so the design side is ratifying or
> overriding a known fallback rather than a guess.

## How to read it

| § | Kind | Count | What we need |
| --- | --- | :-: | --- |
| **A** | Drawn, but **no wire field carries it** | 11 | Drop it, or ask the backend (§ G) |
| **B** | Drawn, but the **stated rule is wrong** | 9 | Correct the statement |
| **C** | Drawn, **deliberately not built** | 8 | Confirm, or tell us to build it |
| **D** | Built **deliberately different** from the drawing | 12 | Ratify, or overrule |
| **E** | **Not drawn at all** | 11 | Draw it, or declare it out of scope |
| **F** | Needs a **component**, not a composition | 2 | Draw the component |
| **G** | What we ask the **backend** for | 6 | Owner's call |
| **H** | **Closed** since the last audit | 4 | Nothing — do not re-raise |

§ B is the one to read first: each of those reads as authoritative, and a later reader with only
the artboard in hand would take its word.

---

## § A — Drawn, but there is no field to carry it

### A1 · Operation: „Beginn" und „Ende (geplant)" — ch. 06 ab. 15

`OperationCreateDto` / `OperationUpdateDto` carry a name, a description, a status and the owning
unit. **No time field of any kind.** An Operation has no times of its own; they live on its
Einsätze, and the roll-up derives from them.
*Today:* absent, with a `KrtHint` saying where the dates come from.

### A2 · Operation: „Einsätze zuordnen" — ch. 06 ab. 15

Also not a field. A mission joins an Operation through **its own** core section
(`PATCH /api/v1/missions/{id}/core` with `operationId`), which needs that mission's name **and** its
core version counter — so a picker here would have to read every candidate mission in full first.
*Today:* a `KrtHint` names the Einsatz as the place it happens.

### A3 · Sammel-Ausbuchen: „Grund", „Notiz", Herkunft-Planer — ch. 09 ab. 20

`POST /api/v1/inventory/bulk-checkout` carries **`itemIds` and nothing else**. All three exist on
`POST /inventory/{id}/book-out`, the rich single call — over which we deliberately did **not** loop,
because a loop half-succeeds (see B4).
*Today:* the sheet lists the rows with a „vollständig" chip each, the append-only sentence, one CTA.

### A4 · Item-Angebot: „Zustand" (Neu / Gebraucht) — ch. 17 ab. 1

`MaterialExchangeItemReleaseRequest` carries `productKey`, `quantity`, `remark`. **The chapter flags
this itself**: „Zustand" is a proposal, not derived copy.
*Today:* absent.

### A5 · Item-Angebot: „Blueprint (Variante)" — ch. 17 ab. 1

No variant field on either item route; a **product key already identifies the product**.
*Today:* absent; the picker shows `variantCount` so the variant can be named in the remark.

### A6 · Item-Gesuch: „Bis wann" — ch. 17 ab. 2

No deadline field. What `MaterialItemRequestCreateRequest` *does* carry, and no artboard draws, is
**`minQuality`**.
*Today:* „Bis wann" absent, minimum quality drawn instead. Please redraw ab. 2 around it.

### A7 · Materialzeile: „Veredelt · SCU" als Unterzeile — ch. 16

`MaterialPriceOverviewDto` carries neither type nor unit — it carries the **category**, which is
what the web groups by. Filling the artboard's subtitle would cost a request per row.
*Today:* category as the subtitle, „Unsortiert" as the fallback. The **detail** does draw type and
unit, because `/materials/{id}` answers with the whole record.

### A8 · Profit-Zeile: die Route („Lorville → ARC-L1") — ch. 16

`ProfitCalculationDto` names no terminals. **The handoff flags this itself** as unbacked.
*Today:* absent.

### A9 · Herstellung: drei Elemente, und eines fehlt — ch. 10 ab. 15

The artboard draws a **smaller** form than the endpoint takes:

- a **single** „Zutaten aus dem Lager ausbuchen" checkbox — the server takes a plan **per material**
  over named rows;
- **„Verwendete Variante"** — no variant field; blueprint-variant counting is an order-level setting
  (`PATCH /{id}/blueprint-variant-counting`);
- **„Übergeben an"** — handing over is the separate `POST /{id}/item-handovers`.

And it **omits the Einlagerung**, which the server requires.
*Today:* per-material plan, no variant field, no handover field, Einlagerung drawn.

### A10 · Materialsammelübersicht: „DREI Felder sind INLINE änderbar" — ch. 10

The web's Besitzer/Standort selects post to `/inventory/{id}/transfer`, a **proxy onto the
book-out**. That is moving stock, not editing a field.
*Today:* the delivery status is inline; the other two say where to change them — the Lager's
book-out sheet, where the amount and the earmark reductions are visible before anything moves.

### A11 · Auftrag: „Ohne Lagerbezug erfassen" — ch. 10

The endpoint cannot serve it and the web does not offer it.
*Today:* absent.

---

## § B — Drawn, but the stated rule is not the rule

### B1 · „Überzusage ist erlaubt" — ch. 10 ab. 13

It is **refused**. `MaterialClaimService` takes a row lock on the order and rejects any pledge whose
sum across Staffeln exceeds the bucket (`REQ-ORDERS-024`, ADR-0092).
*Today:* rendered as an over-pledge, named — not as „ungültige Eingabe".

### B2 · „Nach dem Einlagern sperrt der Server Kern und Waren" — ch. 11 ab. 6

The server does **not**. `RefineryOrderService.updateRefineryOrder` has no status guard; it clears
and re-adds a `COMPLETED` order's goods without complaint. The only „already completed and stored"
refusal in that service belongs to the **store** path.
*Today:* the app enforces it client-side. **It is the only thing doing so** — see G3.

### B3 · „Ein bereits eingelagerter Auftrag lässt sich nicht löschen" — ch. 11 ab. 7

Also unenforced. `DELETE /refinery-orders/{id}` sets `CANCELED` whatever the state, and the Lager
rows it produced stay.
*Today:* „Löschen" drawn locked with its reason on a booked run. Client-side only — see G3.

### B4 · „Ergebnis nennt gelöscht/übersprungen" — ch. 09 ab. 20

`bulk-checkout` is **atomic**: a foreign row or unknown id refuses the whole call. There is no
partial outcome. The shape was borrowed from the bulk *rebooking*, which really does return one.
*Today:* a done step or the refusal; a refusal keeps the selection.

### B5 · „nur die Anmerkung ist änderbar (Web-Regel)" — ch. 17 ab. 3

Not the web rule. `fragments/materialboerse-modal.html` edits the **amount** too, with an „Alles"
shortcut and a stock bound — and `MaterialExchangeOfferUpdateRequest` **requires** `offeredAmount`.
*Today:* the amount is editable; the bound stays the server's, since a board row carries no stock
figure.

### B6 · Der 5-Sekunden-Undo nach dem Zurückziehen — ch. 17 ab. 3

Withdrawing is `POST …/deactivate` and **nothing reactivates a row**. An undo would post a *new*
entry: different id, new timestamp, none of the interested members.
*Today:* a „cannot be undone" sentence in the confirmation instead.

### B7 · Freigabe-Limits als fünfter Tab — ch. 12 ab. 10

Every endpoint is `…/bank/accounts/{id}/approval-limit/…` and the values ride on **one account's**
settings. A tab would have to make the member pick an account first — a tab that is really a picker.
*Today:* in the account's own settings sheet, beside the visibility grants it resembles.
Two smaller things in the same artboard: it draws **three** tiers and the API has a **fourth**
(`area-members`); and there is no „kein Limit" state drawn, which is the common one.

### B8 · Game-Item-Zeile springt in den Lagerbaum — ch. 09 ab. 21

The Lager tree is **material-only** — its three reads send no `catalog` parameter.
*Today:* a row opens **in place** and lists its holders and places. See G4.

### B9 · „#A-1042" — ch. 10 ab. 1

`displayId` is a plain integer and the web renders „#1" too. Raised in round 8 § 2.1 and still
drawn as `#A-1042`.
*Today:* „#1".

---

## § C — Drawn, and deliberately not built

Each verified against the contract on 2026-08-30.

| # | Artboard | Why not | Still true? |
| --- | --- | --- | :-: |
| **C1** | 10.2 **Materialbedarf** tab | `JobOrderDto.aggregatedMaterials` is on the wire and unmapped in `core:data` — buildable, not built | ✔ |
| **C2** | 10.2 **Verlauf** tab | the API exposes **no** activity trail at all | ✔ |
| **C3** | 10.8 **status gating** | `JobOrderDto` has no `transitions[]`; guessing the rules client-side is what the chapter forbids | ✔ |
| **C4** | 09.2 **„Notiz (optional)"** | `InventoryItemCreateDto` has no note field, so the box would discard what a member types | ✔ |
| **C5** | 06.1 **„Einsatz erstellen"** FAB | the app cannot create an Einsatz at all — a missing feature, not a missing button | ✔ |
| **C6** | 06.3 **ship picker** in the Anmelden-Sheet | `AddParticipantRequest` carries `userId` and nothing else | ✔ |
| **C7** | 06.2 **date-range filter** | `MissionQuery` carries `from`/`until` and the repository sends them; no chip opens a picker (blocked by F1) | ✔ |
| **C8** | 08.2 **Hersteller** combobox | the type picker already searches across manufacturers and names the maker; a second cascading field narrows a search that does not need it | ✔ |

---

## § D — Built deliberately different from the drawing

Recorded deviations. Several were approved by the owner at the time; all are in the specs.

| # | Where | The difference | Standing |
| --- | --- | --- | --- |
| **D1** | ch. 06 §1 Operationen row | The mock's „2 Einsätze · 18 Teilnehmer" and payout chip are absent: the list DTO carries neither and the bulk endpoints deliberately do not spend the aggregate queries | **Owner-approved 2026-08-22** |
| **D2** | ch. 06 §5 Operation finances | Net + donations rather than an income/expense split — the web's own operation page shows the same pair | **Owner-approved** |
| **D3** | ch. 08 §1 Hangar | „Schiffe 42 · Fitted 31 · LTI 24" is absent: `/squadron-overview` is **paged**, so the total would be a silent truncation dressed as a headline | Recorded |
| **D4** | ch. 06 tab row | „Übersicht" renamed to **„Briefing"** — a tab may not reuse a navigation label ([ADR-0018](../../adr/0018-the-verwaltung-is-a-tab-and-briefing-is-renamed-out-of-a-collision.md)) | Recorded, generalised as a rule |
| **D5** | ch. 11 ab. 1 | **No extractor import, permanently** — the handoff is consumed once in a browser; a phone cannot receive it | Owner decision |
| **D6** | ch. 11 | **No invented UEX estimate** on a refinery order | Recorded |
| **D7** | ch. 11 ab. 7 | The success toast („Auftrag gelöscht.") shows on the **detail** for two seconds, then navigates — the artboard puts it on the list, which would mean handing a notice across two view models | Recorded, small |
| **D8** | ch. 13 | **Sign-out asks first** ([ADR-0012](../../adr/0012-sign-out-asks-before-it-wipes.md)); the chapter draws the button without a confirmation | ADR |
| **D9** | ch. 04.5 | **No „Gerätesperre verwenden" button** — `DEVICE_CREDENTIAL` is already an allowed authenticator, so the system prompt offers the PIN itself ([ADR-0013](../../adr/0013-the-lock-screen-has-one-button-because-the-prompt-has-two.md)) | ADR |
| **D10** | ch. 04.3 | **No „Push bei Freigabe"** — the app has no push channel at all (resolved decision Q2) | Resolved decision |
| **D11** | ch. 14 | The update gate's CTA opens the **release page**, not a store listing — distribution is GitHub Releases + Obtainium (plan Q1) | Recorded |
| **D12** | ch. 05 | The quick-action glyphs are the **action** (enter, download, plus, swap), not the destination — the app had drawn section icons, a second navigation bar under the first | Corrected to the artboard |

Two more the app **cannot** show rather than will not: the offline banner's „Zuletzt aktualisiert"
stamp and the **CACHE** chip. The app holds no cache and records no load time, so both would be
invented.

---

## § E — Not drawn at all

### E1–E3 · Web surfaces with no artboard anywhere in ch. 00–17

| Surface | Web route | What it is |
| --- | --- | --- |
| **E1 · Auftragsübergreifender Materialbedarf** | `orders-material-demand.html` | What every open order together still needs, per material — a planning view |
| **E2 · Blueprint-Datei-Import** | `/personal-blueprints/import/{preview,apply}` | Upload, preview what matched, apply |
| **E3 · Blueprints „alle löschen"** | `DELETE /personal-blueprints` | Ch. 17 ab. 4 rules out a menu entry for the *items*; the blueprint list has no selection mode, so this has no entry point at all |

### E4–E9 · Composition shipped unratified (rounds 10–11, still open)

Built from the design system's own drawn parts, per the owner's 2026-08-29 rule that a missing
artboard is not a reason to stop. None invents a component, colour or token — what they invent is
**composition**.

- **E4 · The Verwaltung tab's section rhythm** (§ 11a) — four folded sections in one column. Card?
  HUD box? Bare, as now? And nothing says *which* section is writing while a save runs.
- **E5 · The Ablauf editor's row actions** (§ 11b) — five buttons over three rows, because three
  German labels do not fit one 411 dp row. Swipe? Overflow? Long-press?
- **E6 · The Ziele editor** (§ 11c) — the reading chip and the picking chip differ.
- **E7 · Einheit rename and the crew-role chips** (§ 11d).
- **E8 · Reorder as two buttons rather than a drag** (§ 11f, and round 8 § 4 for the queue).
- **E9 · The Lager's tablet detail pane** (round 9 § 4) and **the Materialbörse's two card columns**
  (round 9 § 5).

### E10–E11 · Two questions round 8/9 asked and nothing answered

- **E10 · Chapter 01 states no gutter rule**, so eleven screens each decided for themselves
  (round 9 § 1). A rule would settle them all at once.
- **E11 · Two screens have no answer for a tablet's width** (round 8 § 5).

---

## § F — Needs a component, not a composition

- **F1 · There is no date-time picker in the design system.** The Einsatz's Zeitplan therefore types
  four ISO-8601 timestamps as text — the ugliest surface in the app — and the mission list's
  date-range filter (C7) cannot be built at all. This is a **component**, so it cannot be invented
  from drawn parts.
- **F2 · Starting an Einsatz is a CTA in a form**, not an action on the „Geplant" → „Aktiv" badge a
  manager actually looks at. Round 10 § 10c answered the verb half and not the badge half.

---

## § G — What we ask the backend for

Each is the alternative to dropping a drawn element. All are @greluc's call.

| # | Ask | Buys us |
| --- | --- | --- |
| **G1** | `bulk-checkout` gains `reason` and `note` | ch. 09 ab. 20 keeps its two fields (A3) |
| **G2** | The item request gains a deadline | ch. 17 ab. 2 keeps „Bis wann" (A6) |
| **G3** | Refinery `PUT`/`DELETE` refuse a `COMPLETED` order | Moves B2 and B3 from a client-side rule to a real one. **The one with a data-integrity edge:** today a direct API call can still rewrite a booked run's goods |
| **G4** | The Lager tree learns `catalog=ITEM` | Makes B8's jump reachable, and lets items live in the tree at all |
| **G5** | `personal-blueprints/overview` gains an `ownerCount = 0` filter | „Nicht erfasst" currently narrows only what is loaded, and says so |
| **G6** | A real bulk delete for personal inventory rows | The app loops `DELETE /personal-inventory/{id}` and reports „x gelöscht · y übersprungen", because a loop half-succeeds |

Two more that would close § C rather than § A: an **activity trail** on an order (C2) and
`transitions[]` on `JobOrderDto` (C3).

---

## § H — Closed since the last audit, do not re-raise

| Was | Now |
| --- | --- |
| 12.1 **Anträge** tab / 12.3 **Buchungsantrag** — „the app knows booking requests only as notification types" | **Built.** The member's request flow and the staff queue both ship |
| 12.2 **Verwahrung** chip, org line, sparkline | **Closed by owner decision 2026-08-25** — an account has no custodians (`bank_posting.holder_id` was dropped in V181); the chapter now says card = name, balance, delta, sparkline, which is what the app draws |
| 14.2 **five notification channels** | **Built.** The SSE kind arrived and `KrtNotificationChannels` files each push under its own |
| Round 11 § 11e — „nothing lists the current managers" | **Answered by ch. 06 ab. 12** — and building it turned up why: the app mapped neither `MissionDto.managers` nor `canManageManagers`, so `removeManager` was dead code (`REQ-APP-MIS-032`) |

---

## What is deliberately **not** on this list

Nothing about the design system itself. No component, colour, spacing token or copy rule was
invented in any round — where a drawn part was missing we said so (§ F) rather than drawing one.
