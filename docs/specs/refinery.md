# Raffinerie — the member's own refining orders

> **Doc type:** Living spec · **Area:** `REQ-APP-REF-*` · **Design:** `docs/design/android/11 Raffinerie.dc.html`
> **Server contract:** main repo `REQ-API-009`, `docs/specs/refinery-orders-overview.md`
> **Related:** [`api-contract.md`](api-contract.md), [`sync.md`](sync.md), [`inventory.md`](inventory.md)

„Meine Orders" with the live status filter, one order in full, and „In Lager buchen". Phase 4,
slice 3 (krt-profit/basetool-android#65).

---

### REQ-APP-REF-001 — Only the member's own orders

`/api/v1/refinery-orders/all`, `/users/{id}` and `/mission/{id}` are the Logistik surface. The app
reads `my-orders` and nothing else, the same way the Bank slice stays off the bank-employee
endpoints (`REQ-APP-BANK-001`).

**Acceptance**

- [x] The list read is asserted by path, so a later edit cannot quietly widen it
  (`RefineryRepositoryTest`).
- [x] An empty list is an ordinary answer — a member with no refining runs sees the empty state,
  not a failure.

**Code:** `RefineryRepository`

---

### REQ-APP-REF-002 — „Abholbereit" is derived from a clock, not read from a status

The server has four statuses — `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELED` — and **none of them
means "ready to collect"**. An order stays `IN_PROGRESS` until somebody books its yield in, so
readiness is the run's end time having passed and nothing else.

That has three consequences, and each is a rule rather than an implementation detail:

- **The phase is computed against a clock the screen owns** (`RefineryOrder.phaseAt(now)`), not
  frozen when the response is mapped. A phase fixed at mapping time would leave a finished run
  reading „In Arbeit" until the member pulled to refresh — on the one screen whose whole purpose is
  telling them it is done.
- **The clock ticks once a minute**, which is the granularity chapter 11 asks for. It is a
  constructor parameter (`minuteTicker()`), not a `while (true)` inside the ViewModel: an endless
  ticker started in `init` never lets a test's virtual clock go idle, so `advanceUntilIdle()` hangs
  forever rather than failing. That was found by writing the test, not by reasoning about it.
- **An unknown or unparseable end time reads as still running.** The safe direction: offering „In
  Lager buchen" on a run that has not finished books a yield that does not exist.

The detail response carries no `endsAt` — only the list one does — so the detail computes it from
`startedAt + durationMinutes`. Without that, a detail would show „Restzeit unbekannt" for an order
the list beside it was counting down.

**Acceptance**

- [x] One order, one server answer, two phases as the clock moves (`RefineryRepositoryTest`).
- [x] A `COMPLETED` order whose end time is also in the past stays `STORED` — a phase read off the
  clock alone would offer to book a yield already in the Lager.
- [x] The detail's computed end time equals the list's sent one for the same order.
- [x] The list's `RUNNING` filter stops showing a row once the clock passes its end
  (`RefineryViewModelTest`).

**Code:** `RefineryRepository`, `RefineryViewModel`

---

### REQ-APP-REF-003 — The two live filters are one server request, split on the device

„In Arbeit" and „Abholbereit" both ask for `status=OPEN&status=IN_PROGRESS`, because the server
cannot tell them apart. „Eingelagert" asks for `COMPLETED`. An unknown status is **never echoed
back**: `UNKNOWN` is this build's name for a status the server added, and sending it would turn one
unrecognised row into a `400` on the whole page.

**The screen therefore shows no total** (`REQ-APP-UI-*`, main repo ADR-0104's no-silent-caps
principle). A server count would describe the unsplit pair and a local one only the pages fetched
so far; neither is the number a member would read it as. The „mehr laden" control names how many
rows are loaded, which is a claim that is true.

**Acceptance**

- [x] Both live filters send the same status pair (`RefineryViewModelTest`).
- [x] „Eingelagert" sends `COMPLETED` alone.
- [x] `UNKNOWN` reaches no query parameter (`RefineryRepositoryTest`).
- [x] No total is rendered anywhere on the list.

**Code:** `RefineryFilter`, `RefineryRepository`

---

### REQ-APP-REF-004 — A booking is derived from the order, and never sent empty

„In Lager buchen" creates **one Lager entry per material** at the order's own location, with the
good's quality and output amount, and flips the order to eingelagert. The payload is derived
entirely from the loaded order — chapter 11 has no picker, and inventing one would ask the member a
question the tool already knows the answer to.

**The output material is what gets booked, not the input.** The ore went in and no longer exists;
booking the input would put material in the Lager that was consumed.

**A booking with no bookable good is refused before it is sent.** The endpoint marks an order
`COMPLETED` whatever its item list contains, so an empty list is the quiet way to lose a whole run's
yield — the order would read „Eingelagert" with nothing in the Lager behind it. A good whose
`outputMaterial` is absent is left out of the payload but still **shown** on screen under its input
name, so the member can see what was not booked.

An order that records no quality books at quality `0` rather than being refused: the material
exists either way, and a booking withheld over a missing grade loses the yield.

**Acceptance**

- [x] One item per bookable good, with the order's location and the good's quality
  (`RefineryRepositoryTest`).
- [x] A good without an output material is shown, named from its input, and excluded from the
  payload.
- [x] An order with nothing bookable sends no request at all.
- [x] „In Lager buchen" is offered only once the run has ended (`RefineryViewModelTest`).
- [x] A failed booking reports and leaves the action available.

**Code:** `RefineryRepository.store`, `RefineryDetailViewModel`

---

### REQ-APP-REF-005 — A booking announces three rooms

A booking changes three things that are not the same screen: the order, the „Meine Orders" queue,
and the **Lager it just wrote entries into**. Announcing only the order would leave every open Lager
— browser tab or phone — showing a stock figure the booking has already made wrong, which is the
`REQ-APP-SYNC-004` failure this rule exists to prevent.

Received: `refinery` / `queue` reloads the list, and `refinery-order:{id}` / `order` + `store`
reloads the detail.

**Acceptance**

- [x] One booking publishes to `refinery-order:{id}`, `refinery` and `inventory`
  (`RefineryViewModelTest`).

**Code:** `RefineryDetailViewModel`

---

### REQ-APP-REF-006 — Two recorded deviations from design chapter 11

**The Extractor-JSON import is not in this slice** (owner decision, 2026-08-23). Chapter 11 puts a
scan icon on the screen; it moves to phase 5 together with the Fleetview and blueprint imports,
because all three need a file picker plus the permission and privacy work they share. The icon is
absent rather than present-and-inert: a control that does nothing is worse than one that is not
there yet.

**The „Geschätzter Wert" is the recorded figure, not a UEX estimate.** Chapter 11 labels the value
„UEX-Schätzung", and **no endpoint provides one** — the server carries `oreSales`, which a member
types in, and a `profit` derived from it. Computing an estimate on the device from terminal prices
would print a number the web app never shows and nobody could reconcile, so the screen shows „Ore
Sales" and „Gewinn/Verlust" under their own names. Chapter 11's colour rule is kept: both render as
data, white, never orange.

**Acceptance**

- [x] No scan control exists on the screen.
- [x] No value on the screen is labelled as an estimate.
- [x] Both figures use the shared `formatAmount`, so the app and the web read the same number.

**Code:** `RefineryScreen`
