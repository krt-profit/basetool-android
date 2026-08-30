# Aufträge — the queue and one order

> **Doc type:** Living spec · **Area:** `REQ-APP-ORDERS-*` · **Design:** `docs/design/android/10 Auftraege.dc.html`
> **Server contract:** main repo `REQ-API-009`, `REQ-ORDERS-023` (the redacted view)
> **Related:** [`notifications.md`](notifications.md)

The job-order queue and one order in full. **Read-only**: creating an order, reordering priorities,
taking one on and recording a handover are all mutations and belong to Phase 3.

---

### REQ-APP-ORDERS-001 — The status filter is server-side, and the scope is not sent at all

Filtering a page the server had already truncated would make the stated count wrong — the same rule
every list in this app follows.

The `squadronId` parameter exists on the endpoint and is **deliberately unused**. Which orders a
member sees follows from their memberships and the `X-Active-Org-Unit-Id` header the interceptor
already applies; a client-side scope would be a second, weaker copy of a server-side rule.

**Acceptance**

- [x] Each selected status is its own repeated `status` parameter, and `UNKNOWN` — this build's word
  for "the server said something new" — is never sent (`JobOrderRepositoryTest`).
- [x] No `squadronId` reaches the wire.
- [x] Selecting the filter that is already applied does not re-read (`OrdersViewModelTest`).

**Code:** `JobOrderRepository.queue`, `OrdersViewModel`

---

### REQ-APP-ORDERS-002 — `redacted` reaches the screen and is said out loud

A requester sees their own order with the parts that are not theirs removed (`REQ-ORDERS-023`). The
flag is the only thing that distinguishes that from a complete order, and a member reading a reduced
order as the whole one is the failure the flag exists to prevent.

A **missing** flag is read as not redacted: treating its absence as "something is hidden" would put
a caveat on every order an older server sends.

**Acceptance**

- [x] `redacted = true` renders the sentence; `false` and absent render nothing
  (`OrdersScreenTest`, `JobOrderRepositoryTest`).

**Code:** `JobOrder.redacted`, `OrderDetailBody`

---

### REQ-APP-ORDERS-003 — The progress bar is a length, not a figure

The server sends no percentage, so the bar is computed from stock over need. That is not the
money-arithmetic the rest of this app refuses: it is a **bar length**, and no number derived from it
is ever shown — the row states the server's own `x / y` beside it.

A need of zero yields **no bar** rather than a full one: nothing was asked for, so nothing can be
complete, and a full green bar would say the opposite. The value is clamped, so an over-delivery
does not overflow the track.

**Acceptance**

- [x] 125 of 500 is a quarter; 250 of 100 is one; 10 of 0 is nothing (`JobOrderRepositoryTest`).
- [x] The bar turns green only at 100 %.

**Code:** `JobOrderMaterial.progress`, `MaterialLine`

---

### REQ-APP-ORDERS-004 — No "Zugesagt" total, because the wire has no total

`claims` is a **list** of individual promises, not a sum. Adding them up here would be this client
computing a quantity a member reads. The server's own `openAmount` already accounts for them, and
that is what the screen shows — the design's "noch offen".

The count of promises is carried, because a count is not an amount.

**Acceptance**

- [x] The model exposes `claimCount`, never a claimed quantity.

**Code:** `JobOrderMaterial`

---

### REQ-APP-ORDERS-005 — The material list is collapsed, and its open state outlives a scroll

Collapsed by default, as the web app has it, on a tap target of its own so that opening the list and
opening the order cannot be confused.

The open/closed set lives in the **screen's state**, not in each row's composable. A `LazyColumn`
disposes what leaves the viewport, so a member who opened three rows would find them shut on the way
back.

**Acceptance**

- [x] A collapsed row renders no material names (`OrdersScreenTest`).
- [x] Toggling is reported by row id and held in the state (`OrdersViewModelTest`).

**Code:** `OrdersState.expanded`, `OrderCard`

---

### REQ-APP-ORDERS-006 — One scrolling detail, not four tabs

The design shows four. Three short sections a member reads together are worse behind a control than
beneath each other — the same judgement the Operation detail records, and the opposite of the
Einsatz detail, which carries seven unrelated collections and earns its tabs.

`403`, `404` and an outage are three different sentences.

**Acceptance**

- [x] Comment, materials, assignees and handovers all render, each with its own empty sentence
  (`OrdersScreenTest`).
- [x] The three failures are worded differently.

**Code:** `OrderDetailScreen`

---

### REQ-APP-ORDERS-007 — A notification about an order now opens it

`notificationDestination` maps `JOB_ORDER` onto the order route. The other four entity types still
lead nowhere and their rows stay unclickable — in particular `BANK_BOOKING_REQUEST`, which is about
an approvals surface this build does not have: sending a member to the account instead would answer
a question they did not ask.

**Acceptance**

- [x] A `JOB_ORDER` notification produces `order/<id>` (`NotificationDestinationsTest`).
- [x] Every other type, an unknown type, a missing id and a blank id all produce `null`.

**Code:** `NotificationDestinations`

---

## Known gaps, stated rather than omitted

- **No priority drag and no create.** Reordering is a logistician's write; creating an order is the
  public request form. Both are Phase 3.
- **No age tint.** The design colours the created date yellow past 30 days and red past 90. The row
  states the age relatively ("vor 3 Wochen") and does not tint it — the thresholds are a web-side
  convention worth carrying over deliberately rather than by guess, and colour alone would carry it.
- **The "Bedarf" tab is absent.** `aggregatedMaterials` is the open demand across every order of the
  handling unit; it is a second reading of the same numbers and belongs with the Materialbörse
  slice, where the demand view has its own screen.
- **No handover capture.** "Übergabe erfassen" is a mutation.

## Contract-set dependency (main repo)

`GET /api/v1/orders` and `/orders/{id}` are in the `REQ-API-009` contract set and the vhost
allow-list. The queue path is exact and the vhost's read-only guard covers the family, because the
**same path** answers a `POST` that is `permitAll` by design — the public request form.

The five writes phase 3 adds are named exceptions to that guard:
`POST`/`DELETE /orders/{id}/assignees/{userId}`, `PUT`/`DELETE` on its `/note`, and
`PUT /orders/{id}/status`. The handovers, the production reports and the rest of the Logistician
edit surface stay behind it and keep answering `405`.

---

### REQ-APP-ORDERS-008 — A quantity the server did not send reads as a dash

An absent `inStock` or `needed` renders as `—`, and a material with no stock figure gets **no
progress bar at all** rather than an empty one.

Left blank the row read `" / 500"`, which looks like a rendering fault instead of an absent number
— found on a device, on an order whose materials were served redacted. An empty bar is a different
claim again: it says "none in stock", which is not what "not stated" means.

**Acceptance**

- [x] A row whose `inStock` is absent renders `— / 500` (`OrdersScreenTest`).
- [x] `JobOrderMaterial.progress` is `null` without a stock figure (`JobOrderRepositoryTest`).
- [x] **Observed on a device (2026-08-22)**, before and after.

---

### REQ-APP-ORDERS-009 — A member puts their own name on an order, and nobody else's

`POST` / `DELETE /api/v1/orders/{id}/assignees/{userId}` with the **caller's own** id. Assigning
anybody else needs LOGISTICIAN, and the app carries no surface that names another member here:
one CTA that reads „Übernehmen" or „Abmelden" depending on whether the caller is already on it.

**Nothing is offered until the app knows who the caller is.** The write addresses a member by id,
so a failed `/users/me` disables it rather than guessing — the order still reads, and what is lost
is only the ability to act on it. An assignee row the server sent without a user id is dropped for
the same reason: it could only offer actions that fail.

Each write answers with the whole order, and the screen redraws from that answer rather than
patching what it holds. The server decides the order of the assignee list and the new version.

**Acceptance**

- [x] The caller's own row is the one that offers anything (`OrderDetailViewModelTest`,
  `OrdersScreenTest`).
- [x] Nothing is writable while the caller is unknown (`OrderDetailViewModelTest`).
- [x] An assignee without a user id never reaches the screen (`JobOrderRepositoryTest`).
- [x] **Observed on a device (2026-08-23):** „Übernehmen" flipped to „Abmelden" with the row
  appearing under Zuständig, and back again.

**Code:** `JobOrderRepository.setAssigned`, `OrderDetailViewModel.onToggleAssignment`

---

### REQ-APP-ORDERS-010 — The assignee note is locked on its own version, never the order's

The note is the assignee's own context — when they work on it, which part they take — and it hangs
off the **assignee edge**, which carries a version of its own.

**Echoing the order's version would be wrong in both directions.** Sending it would 409 the note
against any unrelated change to the order; bumping it would 409 everyone else's screen for a note
nobody else reads. The edge's version is what the read hands over and what the write echoes.

An emptied editor **clears** the note rather than saving a blank one: those are the same intention
and the server has a verb for each. A refusal keeps the editor open with what was typed.

**Acceptance**

- [x] The write carries the edge's version, not the order's (`OrderDetailViewModelTest`,
  `JobOrderRepositoryTest`).
- [x] An emptied editor sends the clear, with the version in the query (`JobOrderRepositoryTest`).
- [x] A conflict keeps the draft and says so (`OrderDetailViewModelTest`, `OrdersScreenTest`).
- [x] **Observed on a device (2026-08-23):** the note appeared under the name without a reload.

**Code:** `JobOrderRepository.setAssigneeNote`, `OrderDetailViewModel.onSaveNote`

---

### REQ-APP-ORDERS-011 — The status control is a Logistician's, and the app asks before offering it

`PUT /api/v1/orders/{id}/status` needs `LOGISTICIAN` **and** per-order scope. The app reads
`isLogistician` from `/users/me` and offers the control only to a Logistician — the alternative is
either hiding a control they are entitled to, or offering one that answers 403.

**The per-order half cannot be predicted, so the refusal is named.** A Logistician outside this
order's slice gets a 403 that the app words as „Für diesen Auftrag fehlt dir die Berechtigung."
rather than the generic write failure — the same wording it would use for a member without the
grant, because from the member's side it is the same fact.

The picker offers the four statuses the server knows and marks the current one.
`JobOrderStatus.UNKNOWN` is absent by construction: it carries a constant this build has never
seen, and the repository refuses it rather than folding it into one of the four.

**Acceptance**

- [x] A non-Logistician is offered no status control (`OrderDetailViewModelTest`,
  `OrdersScreenTest`).
- [x] A Logistician's write echoes the order's version (`OrderDetailViewModelTest`).
- [x] A `403` is worded as this order's refusal (`OrdersScreenTest`).
- [x] `UNKNOWN` is refused before a request goes out (`JobOrderRepositoryTest`).
- [x] **Observed on a device (2026-08-23):** the control appeared only after the Logistician grant
  was given, and moved the order to „In Bearbeitung" in place.

**Code:** `JobOrderRepository.setStatus`, `OrderDetailViewModel.onStatusChosen`, `IdentityRepository`

---

### REQ-APP-ORDERS-012 — Offline disables the order's writes; it never queues them

Same rule and the same shared band as [`REQ-APP-INV-010`](inventory.md) and
[`REQ-APP-PI-003`](personal-inventory.md): the assign CTA, the status control and the note editor's
save are disabled and faded under a line saying why.

**Acceptance**

- [x] Nothing is sent while offline, and the state follows the device (`OrderDetailViewModelTest`).
- [x] The band renders and the CTA is disabled (`OrdersScreenTest`).

**Code:** `ui/OfflineWrites.kt`, `OrderDetailViewModel`

### REQ-APP-ORDERS-013 — A member raises a material order from the queue

The Aufträge queue carries the „+" its artboard draws, and it opens the form the web has at
`/orders/create`. The form is the web's, field for field: the processing unit, the customer, the
contact handle, one or more material lines of *Material · Menge · Min. Qualität*, and an optional
comment. `POST /api/v1/orders`.

**The two unit pickers are not the same list.** The customer is any active org unit — a member may
raise an order *for* a Bereich or the Organisationsleitung — while the processing unit is only the
profit-eligible subset, because a Bereich can never work an order. Both come from a single read of
`/api/v1/org-units/active-all-kinds`, with the second derived from the first, exactly as the web
derives it: two reads could disagree.

**Item orders are the same form under a switch** — see `REQ-APP-ORDERS-016`. What stays out of
scope is the sub-assembly tree the web renders as nested lines, which design round 8 §1.3 carries.

**The material picker is `search` with `jobOrderOnly`, not the unbounded job-order list.** The
bounded page keeps the payload small and reuses a path already on the API vhost's allow-list; when
the server holds more matches than the page carries, the picker says so rather than letting the
member conclude their material does not exist (ADR-0104). When a query of two or more characters
matches nothing, it says that too — an empty dropdown reads as a broken picker.

**A half-filled line blocks the submit.** `POST /orders` takes whatever lines it is handed, so
dropping a line the member had typed a material into would raise an order missing that material
and say nothing. A wholly empty trailing line is not half-filled — it is the one the form keeps.

**„No processing unit is enabled" is a statement about the organisation** and may only be made once
the server has answered. A failed read says the units could not be loaded instead: telling a member
their org has no eligible unit when the phone could not ask sends them to an administrator over a
dropped connection.

**Acceptance**

- [x] Both units, a handle and one complete line are required; a half-filled line blocks the submit
      (`OrderCreateTest`).
- [x] A decimal comma is an amount (`OrderCreateTest`).
- [x] A typed material name without a pick is not a material (`OrderCreateTest`).
- [x] A blank comment is sent as absent, not as an empty string (`OrderCreateTest`).
- [x] Verified on a device end to end: order #10, „Quantainium (Raw)" 12,5, customer „Bereich
      Profit", processing unit „IRIDIUM".

**Code:** `orders/OrderCreateScreen.kt`, `orders/OrderCreateViewModel.kt`,
`core/data/JobOrderRepository.kt` (`JobOrderCreateSource`), `core/data/OrgUnitRepository.kt`
(`activeAllKinds`)

### REQ-APP-ORDERS-014 — The queue counts an order's age in days

An order's age is the queue's signal, and the two thresholds are the operator's
(`job_order.age_yellow_days` = 30, `job_order.age_red_days` = 90). The row renders it as a day
count — „vor 94 Tagen" — rather than through the app's shared relative-time helper, which switches
to „26.05., 11:19" from two days out and makes the reader do the arithmetic the colour beside it
has already done. Today and yesterday keep their words.

Design chapter 10 artboard 1 draws exactly this.

**Acceptance**

- [x] Verified on a device: „vor 12 Tagen", „vor 2 Tagen", „gestern", all grey below the yellow
      threshold.

**Code:** `orders/OrdersScreen.kt` (`ageText`), `core/data/JobOrderRepository.kt`
(`JobOrderAgeThresholds`)

### REQ-APP-ORDERS-015 — A Logistician moves an order in the queue, without a drag

The web reorders by dragging a row; a phone has neither the whole queue on screen nor a pointer that
can hold one row while the rest scrolls. The order detail carries three ghost buttons instead —
**An den Anfang · Höher · Niedriger** — beside „Status ändern", which is where the app already gates
a Logistician's write. `PUT /api/v1/orders/{id}/priority?priority=N`.

**No version is echoed, and that is deliberate.** The service reorders the whole queue under a
pessimistic write lock, so the optimistic version this app echoes on every other write has nothing
to guard here; sending one would suggest a conflict check that does not happen.

**There is no „ans Ende".** The back of the queue is a page count away, and a control that guessed at
its length would drop the order somewhere nobody asked for.

**An order with no priority gets no control.** A completed or rejected order has left the queue, and
„move it up" would be an instruction to put it back into one.

**An order already at the front does not offer Höher or An den Anfang.** Both would send position 1
again, which the server accepts and reorders the whole queue for — a write that changes nothing is
still a write.

Design round 8 §4 asks for the drawing, and asks whether the control belongs on the queue instead.

**Acceptance**

- [x] The control belongs to a Logistician alone; a member without the grant sends nothing
      (`OrderDetailViewModelTest`).
- [x] „Niedriger" sends the next position; an order at the front sends nothing (same).
- [x] An order out of the queue offers no control (same).
- [x] Verified on a device: order #1 moved to 2 and #2 took 1 in the database, the header redrew as
      „Prio 2", and „An den Anfang" put it back.

**Code:** `orders/OrdersScreen.kt` (`PriorityControls`), `orders/OrdersViewModel.kt`,
`core/data/JobOrderRepository.kt` (`setPriority`)

### REQ-APP-ORDERS-016 — An item order is the same form under a switch

The web's `/orders/create` raises two kinds of order behind one radio pair, and the app carries both
behind the segmented control. The head is shared — the two units, the contact handle, the comment —
and only the lines differ: a material line is *Material · Menge · Min. Qualität*, an item line is
*Item · Blueprint · Anzahl*. `POST /api/v1/orders/items`, with the pickers on
`GET /api/v1/orders/item-catalog` and `…/{gameItemId}/blueprints`.

**Both line sets survive the switch.** A member who typed three materials, looked at the item form
and came back finds their three materials still there; only `kind` decides what is submitted. The
alternative — clearing on switch — throws away typed work to save a field in the state.

**The blueprint picker stays shut until an item is picked**, because the blueprints are read *for*
that item; an empty dropdown first reads as a broken control. A **single** blueprint is picked
outright: the member has no choice to make, and one more tap on a one-entry dropdown is only a way
to leave the line unfinished. An item the catalogue holds but has **no** blueprint for says so — the
server would refuse the line, and a dropdown that opens on nothing does not explain why.

**Typing past a pick clears the item *and* its blueprint.** A blueprint belongs to one item, so
leaving it behind would submit a pairing the member never made. The same rule as the material
picker's, one level deeper.

**A blueprint the server named with neither an output name nor a wiki key is still pickable**, under
its id. Its id is what the wire wants, and hiding it would make its item unorderable.

**A half-filled item line blocks the submit**, for the same reason a material one does.

**Out of scope:** the sub-assembly tree — adopting a blueprint's own components as further lines,
the web's `clientLineId` / `parentClientLineId` machinery. The order is raised with the items named
and the server derives their materials. Design round 8 §1.3 carries it.

**Acceptance**

- [x] A complete item form may be sent; a line without a blueprint, without a count, or with only a
      typed name blocks the submit (`OrderCreateTest`).
- [x] The kind decides which line set is judged — a finished material line does not make an
      unfinished item form sendable, nor the other way round (`OrderCreateTest`).
- [x] The picker reads the catalogue and drops rows without an id; a nameless blueprint falls back
      to its wiki key and then to its id (`JobOrderRepositoryTest`).
- [x] The create posts `gameItemId`, `blueprintId` and `amount` (`JobOrderRepositoryTest`).
- [x] Verified on a device end to end: order #11, „11-Series Broadsword Cannon" ×2, both units
      IRIDIUM — written as `job_order.type = ITEM` with one `job_order_item` row, and shown back in
      the queue and the detail.

**Code:** `orders/OrderCreateScreen.kt` (`KindSwitch`, `ItemLineCard`),
`orders/OrderCreateViewModel.kt` (`OrderKind`, `OrderItemLineDraft`),
`core/data/JobOrderRepository.kt` (`searchItems`, `blueprintsFor`, `createItems`)

### REQ-APP-ORDERS-017 — An item order shows its own positions

An order carries one kind of line or the other, and the app reads both. `JobOrderDto.items` was
unread, so an item order showed **Positionen 0** and an empty tab while its items sat on the wire —
a gap that predates the app's own item form, because item orders raised on the web already reached
the queue.

An item position renders as *name · built / asked-for* with the bar under it, the same shape a
material position has, plus the handed-over count when any have moved. The **„Rezept veraltet"**
chip carries the web's `blueprintStale` warning: the blueprint has changed since the order was
raised, so what will be built may no longer be what was costed.

**The counts default to zero, not to absent.** The server omits them at zero, and a screen that had
to tell „none built" from „not stated" would be drawing a distinction the wire does not make.

**„Keine Materialien" is only said when the order carries nothing at all.** An item order has no
materials of its own — the server derives them from the blueprint — so the line under its items
would read as a defect.

The queue card's disclosure names whichever lines the order has: **Materialien (n)** or
**Items (n)**. An item order used to show no disclosure at all, so its positions were reachable only
by opening the order.

**Acceptance**

- [x] Verified on a device: order #11 shows the `ITEM` chip, „Items (1)" on the card, and
      „Positionen 1" with „11-Series Broadsword Cannon 0 / 2" in the detail.

**Code:** `core/data/JobOrderRepository.kt` (`JobOrderItem`), `orders/OrdersScreen.kt` (`ItemLine`),
`orders/OrderDetailTabs.kt`, `orders/OrderTab.kt`

### REQ-APP-ORDERS-018 — A handover is what finishes an Auftrag, and it books a stock row out

Design ch. 10 artboard 14, the round-8 parity programme's heaviest item: in the web the handover is
what **closes** an Auftrag, so an app that could take one on and record no delivery could never end
one either.

**A stock row is mandatory.** `JobOrderHandoverItemCreateDto.inventoryItemId` is `@NotNull` on the
server and the web's own form refuses to submit without one
(`error.joborder.handover.noitems`). The candidates come from
`GET /orders/{id}/materials/{matId}/inventory` — the rows already linked to **this** order line —
which is what makes the handover a booking-out rather than a search. A single candidate is
preselected; two or more are not, because choosing for the member would book out a row they did not
pick.

> [!warning] The artboard's „Ohne Lagerbezug erfassen" is **not** built
> The endpoint cannot serve it and the web does not offer it. Flagged in the design gap list rather
> than coded around (`CLAUDE.md`: flag a contract mismatch, never work around it).

**The projection is computed, never formed in somebody's head** — „Nach dieser Übergabe 300 / 400 ·
75 %", turning Success and saying „Erfüllt" at the need.

> [!danger] „Already handed over" is the sum of the handover LINES, never `amount - openAmount`
> The server's `openRemaining` is `required - claimed` (`MaterialClaimService`), so it measures
> **promises**. Subtracting it would report every merely-claimed line as delivered. The app carries
> `JobOrderHandover.lines` for exactly this and sums the ones naming the material.

**Append-only, said in the form.** Nothing in the app takes a handover back, and the warning sits in
the form rather than in a modal afterwards — a warning that arrives after the decision is one nobody
acted on.

**A comma is a decimal point.** A German keyboard sends `,`; a member typing the number their locale
shows them must not be rejected for it.

**Acceptance**

- [ ] The Übergaben tab offers a record action per material line that carries a material id.
- [ ] The form is not submittable without a stock row, an amount > 0 and a non-blank handle.
- [ ] The projection adds what is typed to the summed handover lines, never to `amount - openAmount`.
- [ ] A successful write closes the sheet and re-reads the Auftrag; a refusal keeps everything typed.

**Enforced by:** `OrderHandoverTest`, `OrdersScreenTest`.

## REQ-APP-ORDERS-019 — Die Herstellung: was ein Item-Auftrag tatsächlich baut

**Status:** implemented · **Since:** 2026-08-29

An item Auftrag has **two** writes and they are not the same thing. The Übergabe hands finished
goods to somebody; the **Herstellung** consumes the earmarked raw material and creates item stock,
and it is the only one that moves a line's „hergestellt" figure. Until this existed the app could
deliver items it could never record having built.

`POST /api/v1/orders/{id}/items/{itemId}/production`, gated `LOGISTICIAN | OFFICER | ADMIN` plus
`canEditJobOrder`.

**The plan must cover each material's demand exactly.** The server refuses anything else
(`ProductionAllocationException` → „Zuweisung deckt den Materialbedarf nicht exakt."), so the sheet
holds its own submit until every non-skipped material reconciles. The demand for a partial run is
the **line's** total scaled by the share being built (`requiredTotal × units ÷ lineAmount`), rounded
per the material's `quantityType` — whole numbers for `PIECE`, thousandths otherwise; the app never
derives a per-unit figure and multiplies it back up, which would compound the rounding.

**Only rows earmarked to this Auftrag are candidates**, and each can give at most
`min(earmark, stock)`: an earmark can outlive the material it was made against, and offering the
promise as if it were material would put a number on screen the server then refuses. Each draw
echoes the row's own `version`, so a concurrent stock change is a 409 rather than a double-spend.

**„Nicht ausbuchen" is per material, not per run.** A material consumed outside the tool goes on
`skippedMaterialIds`; its demand drops out of the gate and no draw is sent for it. The amounts
already typed are kept, so unticking restores the plan.

**„Bedarf decken" fills the plan.** Three rows and a demand of 1 234,5 is a subtraction nobody
should do on a phone; the action takes from the rows in order and never asks one for more than it
can give.

**The Einlagerung is mandatory** — `bookIn.locationId` is `@NotNull`. The owner defaults to the
acting member (the server's own default for a `null` `ownerUserId`, and the reason the app does not
have to hold a name for it). The org-unit picker appears only when the owner has more than one
membership, preselecting the Auftrag's responsible unit when they belong to it — which keeps the
REQ-ORG-004 „more than one membership and no pick → 400" branch unreachable. „Persönlich" and „dem
Auftrag zuordnen" are mutually exclusive; ticking the first clears and disables the second.

> [!warning] Three artboard elements are **not** built, and are on the design gap list
> Design ch. 10 artboard 15 draws a smaller form than the endpoint takes:
>
> - a **single** „Zutaten aus dem Lager ausbuchen" checkbox for the whole run — the server takes a
>   plan per material over named rows, so the sheet carries the web's per-material skip instead;
> - **„Verwendete Variante"** — the payload has no variant field; blueprint-variant counting is an
>   order-level setting (`PATCH /{id}/blueprint-variant-counting`);
> - **„Übergeben an"** — handing over is the separate `POST /{id}/item-handovers` write.
>
> And the artboard omits the Einlagerung, which the server requires. Flagged, not coded around.

**The action is drawn even when refused** (ADR-0011): a member without the Logistician role sees the
button at 45 % with the lock ahead of its label, and the tap names the grant. A line with nothing
left to build carries no action at all — that is not a permission question.

**Acceptance**

- [ ] Every item line with something left to build offers „Herstellung erfassen".
- [ ] The submit is held until the amount is within what is open, every non-skipped material
      reconciles exactly, and a Lagerort is picked.
- [ ] A skipped material is named in `skippedMaterialIds` and contributes no draw.
- [ ] Each draw carries its stock row's own version; the booking carries the item line's.
- [ ] „Persönlich" clears the order earmark, and the org unit is only sent when there is a choice.
- [ ] A booked run closes the sheet and re-reads the Auftrag; a refusal keeps the whole plan.

**Enforced by:** `OrderProductionTest`, `OrdersScreenTest`.

## REQ-APP-ORDERS-020 — Die Item-Übergabe: what an item Auftrag actually delivers

**Status:** implemented · **Since:** 2026-08-29

The third write on this edge, and the one that closes an item Auftrag.
`POST /api/v1/orders/{id}/item-handovers`, gated `LOGISTICIAN | OFFICER | ADMIN` plus
`canEditJobOrder` — the same gate as the other two.

It is **not** the material handover with a count. The units are pieces, there is no stock row to
book out (the goods were built rather than fetched), the server keeps a separate log, and this write
moves `deliveredAmount` and auto-completes the order once every line is fully delivered.

> [!danger] The ceiling is `manufactured − delivered`, never `ordered − delivered`
> A unit can only be handed over once it has been built (REQ-ORDERS-025).
> `JobOrderItemHandoverService` refuses anything above the manufactured-but-undelivered count with a
> **400**. The obvious subtraction would put a number on screen the server rejects for a reason the
> member has no way to see. The stepper stops at the real ceiling and the form names it: „Hergestellt
> und noch nicht übergeben: N".

**The log was being left unread.** `JobOrderDto.itemHandovers` existed and the app mapped only
`handovers`, so the Übergaben tab told a fully delivered item order that nothing had been handed
over. Both logs are now read and the empty state only appears when **both** are empty.

**One line per write.** The wire takes a list of entries; a form that offered several at once would
have to reconcile several independent ceilings before it could tell the member which one it broke.

Append-only, and said in the form — the same rule as the material handover. A `400` from the server
is rendered as the ceiling having moved under the sheet (somebody handed the same units over first)
rather than as „ungültige Eingabe", because that is what it almost always is.

The action is **drawn even when refused** (ADR-0011), beside „Herstellung erfassen" on the same
line, because both are booked against that one line. A line with nothing built and undelivered
carries no action at all — that is a fact about the line, not a permission.

**Acceptance**

- [ ] The stepper's ceiling is the manufactured-but-undelivered count
- [ ] A blank recipient is refused before the write
- [ ] The Übergaben tab lists item handovers as well as material ones
- [ ] A recorded handover closes the sheet and re-reads the Auftrag; a refusal keeps what was typed
- [ ] A line with nothing deliverable offers no action, locked or otherwise

**Enforced by:** `OrderItemHandoverTest`, `OrdersScreenTest`.

## REQ-APP-ORDERS-021 — Zusagen: a Staffel signing up to deliver

**Status:** implemented · **Since:** 2026-08-29

A fourth tab on the order detail, and the members' half of the Auftrag workflow that had no app
surface at all. `GET/POST /api/v1/orders/{id}/claims` and `DELETE .../claims/{claimId}`.

**A claim is an intention, never a booking.** Nothing moves in the Lager; delivery is the
Übergabe. That is why withdrawing one asks nothing first — it is undone by pledging again — and it
is the same fact that makes the server's `openRemaining` (`required − claimed`) a count of
**promises**, which REQ-APP-ORDERS-018 must never read as deliveries.

> [!important] Only on a **Spezialkommando** order
> `MaterialClaimService.assertClaimable` refuses a claim whose responsible unit is not an SK, and
> freezes claims once the order is `COMPLETED` or `REJECTED`. The tab is therefore **not** offered
> on a Staffel's own order. `SquadronReferenceDto` carries no kind, so the responsible unit's id is
> matched against `/org-units` (all kinds) rather than guessed from a name beginning „SK".

**A claim belongs to a Staffel, not to a member.** The wire keys it on
`(bucket, claimingOrgUnit)`, so setting and changing are one upsert and an existing pledge opens
filled in. The picker offers exactly what the server accepts — the caller's own memberships,
filtered to **profit-eligible squadrons**: a Spezialkommando places orders and never claims against
one, and a squadron nobody marked profit-eligible is outside the order workflow. A caller with no
such membership is told that plainly, which is a fact about their memberships rather than a missing
grant.

**A bucket is `(material, quality)`, not a material.** The same material at „gut" and at „egal" are
two separate demands and a claim names which it covers.

> [!warning] Design ch. 10 artboard 13 says „Überzusage ist erlaubt" — it is not
> `MaterialClaimService` takes a row lock on the order and refuses any pledge whose sum across
> Staffeln exceeds what the bucket needs (REQ-ORDERS-024, ADR-0092). The refusal is rendered as
> exactly that rather than as „ungültige Eingabe". On the design gap list.

The buckets are **re-read** after every write: a pledge moves `claimed` and `openRemaining`, and
both are the server's arithmetic.

**Acceptance**

- [ ] The tab appears only once the responsible unit is known to be a Spezialkommando
- [ ] The unit picker offers only profit-eligible squadrons the caller belongs to
- [ ] An existing pledge opens with its amount and offers a withdrawal; a first one does neither
- [ ] A comma is a decimal point
- [ ] A refusal keeps the sheet and its amount, and names the overclaim rule

**Enforced by:** `OrderClaimsTest`.

## REQ-APP-ORDERS-022 — Auftrag bearbeiten: one form, two endpoints

**Status:** implemented · **Since:** 2026-08-29

The **create form, pre-filled** — design ch. 10 artboard 10 is explicit that there is no second
layout, and the server agrees: `PUT /orders/{id}` takes exactly the same payload as the create and
**replaces** the details and the whole material list rather than patching either.

Two writes behind it, and the app is **told** which it opened rather than working it out:

| Who | Endpoint | Gate |
| --- | --- | --- |
| Logistician | `PUT /orders/{id}` | `LOGISTICIAN` + `canEditJobOrder` |
| The requester | `PUT /orders/{id}/requested` | `isAuthenticated` + `canEditJobOrderAsRequester` |

**The requester's form is narrower, and the locked fields are drawn rather than removed.** The two
units and the handle belong to the processing side; the server takes them from the stored order
whatever the payload says, so an editable control there would silently do nothing — worse than one
that says why. Artboard 11's own sentence sits at the top of the form, and the locked block carries
the reason.

> [!important] The requester's freeze is on the **whole order**, not per line
> One handover anywhere — material or item — closes that path for everything
> (`canEditJobOrderAsRequester`), and the attempt is a 400. The entry is therefore offered only
> while nothing at all has been delivered.

> [!danger] A line may not fall below what has already been handed over
> The floor is the sum of the handover **lines**, never `amount − openAmount` — that one counts
> claims (`MaterialClaimService`) and would put the floor in the wrong place. The server refuses the
> save outright, so the form names the offending line rather than letting the save fail unlabelled.

**The version travels.** The form reads the order, keeps its `version`, and sends it back; a
mismatch is the 409 that stops two people overwriting each other.

**Not offered on an item order.** `PUT /orders/{id}/items` takes a different payload and needs the
blueprint-variant picker and the sub-assembly tree of artboard 12, which is its own screen and still
web-only. The overflow entry is **drawn** with that reason — a capability this build lacks, not a
permission the caller lacks — rather than hidden.

Also added on the way: `JobOrder` now carries `handle`, `requestingOrgUnitId` and
`responsibleOrgUnitId`. The app had been dropping all three, and without them the edit form could
neither pre-fill its pickers nor pass its own submit gate.

**Acceptance**

- [ ] The form arrives pre-filled, including the handle and both unit pickers
- [ ] A Logistician writes `/{id}`; a member of the requesting unit writes `/{id}/requested`
- [ ] The requester's two unit fields and handle are drawn locked with a reason
- [ ] A line under what was delivered blocks the save and says which line
- [ ] The order's version is echoed on the save
- [ ] An item order's entry is drawn with the reason it cannot be used

**Enforced by:** `OrderEditTest`.

## REQ-APP-ORDERS-023 — Die Materialsammelübersicht: which stock is promised to this Auftrag

**Status:** implemented · **Since:** 2026-08-29

`GET /api/v1/orders/{id}/material-collection` — the stock rows linked to one Auftrag, reachable
from its overflow. Design ch. 10 artboard 16.

> [!important] It belongs to the **Auftrag**, not to the material reference
> `material.collection.back` reads „Zurück zum Auftrag". Design chapter 16's first draft filed this
> page under Materialien and corrected itself; the app follows the correction.

The read is open to anyone who may see the order, and the **owners are redacted** for a
requesting-side viewer (`canSeeJobOrderInventoryOwners`) — which is why the owner is left out of the
row rather than drawn as an empty field.

**Two writes, both gated `LOGISTICIAN | OFFICER | ADMIN` plus edit scope:**

- **Lieferstatus** — `PATCH /inventory/{id}/delivered`, echoing the **row's own** version, so a
  concurrent stock change is a 409 rather than a silent overwrite.
- **Verknüpfung lösen** — `DELETE /orders/{id}/inventory/{entryId}/unlink` for a stock row, and
  `DELETE /orders/{id}/materials/{materialId}` for a required material nothing covers.

**Only a link with an amount behind it asks first.** The unlink removes the earmark and leaves the
stock alone, so a row that promised nothing has nothing to warn about — and asking anyway teaches
members to click through confirmations. The confirmation names both halves: what goes (the
assignment) and what stays (the stock).

**The second section is derived**, because the server has no endpoint for it: „Verknüpfte
Materialien (ohne Bestand)" is the order's required materials minus the materials the linked rows
cover.

> [!warning] Besitzer and Standort are **not** editable here, deliberately
> The web's two inline selects post to `/inventory/{id}/transfer`, which is a proxy onto the
> backend's **book-out** — that is moving stock, not editing a field. The app offers exactly that in
> the Lager's own book-out sheet, where the amount and the earmark reductions are visible before
> anything moves. A silent inline picker would move stock without showing what moves. The screen
> says where to do it; the artboard's „DREI Felder sind INLINE änderbar" is on the design gap list.

**Acceptance**

- [ ] A required material no linked row covers appears in the second section
- [ ] A row with an earmark asks before unlinking and names the amount; one without does not ask
- [ ] The delivered flag echoes the row's own version
- [ ] Every write re-reads the page, because each moves a figure the server computes

**Enforced by:** `OrderCollectionTest`.

## REQ-APP-ORDERS-024 — Item-Positionen: the sub-assembly tree, and editing them

**Status:** implemented · **Since:** 2026-08-29

Design ch. 10 artboard 12 — the last of chapter 10.

### The tree

> [!important] A sub-assembly is a **real ordered line with a parent**, not a recipe
> `JobOrderItemDto.parentItemId` is how the server models it, which is what lets the tree be drawn
> from the order alone rather than from a blueprint read. The app had been dropping that field.

**Display only, and two levels.** Assembly → its materials, and no further — deeper does not fit a
phone, and a branch whose child has children says so („Die Rezeptur geht tiefer …") rather than
truncating in silence. Indented with a rail and no chevrons: the tree does not fold, it shows.

**The availability chip** per sub-assembly comes from `GET /orders/{id}/item-stock` — „Lager" when
the earmark covers what was ordered, „Fehlt n" otherwise. The same two words the craftability chip
uses in Mein Inventar, so one reading carries across. The read only happens for an item order: a
material order has no game-item stock, and asking would be a round trip for an always-empty answer.

The quantities are the **server's own line totals**, already scaled to each line's count; the app
renders them and multiplies nothing.

### The edit

`PUT /orders/{id}/items`, reusing the item form of the create with its blueprint-variant picker —
which **is** the „blueprint variant counting" parity point. The line's own variant is pre-filled, so
the picker offers alternatives without the member re-picking the item first.

> [!danger] Only a Logistician, and only before the first handover
> The requester's own item path (`/{id}/items/requested`) is **not** built, so a requester form on
> an item order writes nothing rather than sending the wrong shape. And the server refuses the write
> once anything has been handed over — the lines are what the delivery was measured against. The
> overflow entry is drawn with that reason rather than hidden.

Also added on the way: `JobOrderItem` now carries `gameItemId`, `blueprintId` and `parentItemId`.
All three are on the DTO and none was mapped; without the first two the edit could not pre-fill a
single line, and without the third there was no tree.

**Acceptance**

- [ ] A sub-assembly appears inside its parent's branch, never as a second ordered row
- [ ] A branch deeper than two levels says so
- [ ] The availability chip reads „Lager" only when the earmark covers what was ordered
- [ ] The edit pre-fills each line's item and its variant, and echoes the order's version
- [ ] An item order with a handover offers the edit entry with the reason it cannot be used

**Enforced by:** `OrderEditTest`, `OrdersScreenTest`.
