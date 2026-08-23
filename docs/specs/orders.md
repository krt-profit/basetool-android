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
