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
