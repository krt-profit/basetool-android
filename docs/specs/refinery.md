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

### REQ-APP-REF-004a — `outputQuantity` is in UNITS, and one SCU is a hundred of them

The server tracks a good's `outputQuantity` in **units**. A run yielding 288 SCU arrives as
`28800`, and the store endpoint reads its `amount` back in **SCU** — multiplying by a hundred when
it writes the good again (`RefineryOrderService#updateGoodOutputQuantity`). The web app divides by
a hundred to display it.

**The app got this wrong in both directions and a device walk is what found it.** The screen
printed `28800 SCU` for 288 SCU, and — the part that matters — the booking sent the raw unit count
as if it were SCU. A real order would have created a Lager entry a **hundred times** the yield, and
there is no undo: the entry is real stock, and the order is marked eingelagert either way.

The conversion lives in the repository, once, at the boundary. Everything above it works in the
member's unit, `PIECE` materials are not divided, and the amount the booking sends is the same
number the screen shows.

**Acceptance**

- [x] `62200` on the wire reads as `622` SCU (`RefineryRepositoryTest`).
- [x] The booking sends the SCU figure and **never** the raw unit count — asserted as an absence,
  because the wrong value would still have produced a valid-looking request.
- [x] Walked on a device against real unit data: Lager entry `288 SCU`, and the order's good back
  at exactly `28800` — the round trip is stable.

**Code:** `RefineryRepository`, `RefineryScreen`

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

- [x] Timestamps render in the member's zone, never as the wire's ISO string — the detail printed
  `2026-08-24T02:53:02.557721Z` in both rows until a device walk showed it.
- [x] No scan control exists on the screen.
- [x] No value on the screen is labelled as an estimate.
- [x] Both figures use the shared `formatAmount`, so the app and the web read the same number.

**Code:** `RefineryScreen`

---

### REQ-APP-REF-007 — Einlagern is a form, and it is one call

„Einlagern" — design chapter 11, artboard 3. The app booked a finished run with a single
confirmation that could only send what the run had calculated; the web has a form.

**The amount is the reason the screen exists** (handoff, verbatim: „amount (Pflicht, überschreibt
die Berechnung — der Grund, warum der Screen existiert)"). Every line opens at the computed figure
with that figure still shown beside it, and is meant to be corrected: what a refinery calculated and
what came out of it are not always the same number.

Each line also carries what the item has always allowed and the app never sent: a personal entry, a
job order, a note (≤ 1000), a receiving member, and the org unit to book into. **`personal` and
`jobOrderId` exclude each other** — the server answers 400 — so ticking personal clears the order
rather than letting the pair be assembled, and a personal line never inherits the order's mission
earmark.

**One call for the run, not one per card.** The handoff has each card book and acknowledge on its
own. `RefineryOrderService.storeOrder` books whatever the call carries and then sets the order
`COMPLETED`, refusing every later call with „Refinery order is already completed and stored." Built
per card, the second material is lost — which is what happened on a device: line one booked, order
closed, line two refused with 400. The editing stays per line; only the submit is shared, and a line
the app cannot read stops the whole submit rather than quietly leaving one material behind.

**A line is identified by material *and* grade.** A run yields the same material at several
qualities — Agricium at 733 and at 874 — and keying on the material alone is a duplicate list key,
which Compose treats as fatal. It crashed the app the first time a real order was opened.

**Acceptance**

- [x] The same material at two grades is two lines, and a line's identity survives an edit
  (`RefineryStoreTest`).
- [x] A personal line carries no job order (`RefineryStoreTest`).
- [x] A German decimal is read as one (`AmountsTest`).
- [x] Verified on a device against the local test stack: a corrected amount of „1,9" against a
  computed 1,8 books **1.9** into the Lager, and a run with two Agricium lines books both in a
  single `POST …/store -> 200`.

**Code:** `RefineryRepository` (`RefineryStoreSource`), `RefineryDetailViewModel`,
`RefineryStoreSheet`

---

### REQ-APP-REF-008 — What a member typed is not what `toDoubleOrNull` expects

The app is German-first. On a German locale the decimal key of the keyboard is a comma, and every
numeric field in this app parsed with `toDoubleOrNull()` / `toBigDecimalOrNull()`, which reject it.

Two shapes of harm, both found on a device:

- the refinery's Einlagern sent **no request at all** — the parse failed and the write was refused
  before it left, with a generic message;
- the Lager's booking draft and every bank amount fall through to `0.0` / `BigDecimal.ZERO`, which
  does not refuse anything: it books zero and reports success.

`parseTypedAmount` / `parseTypedDecimal` accept both separators and treat blank as `null` rather
than zero — "nothing typed" and "zero" are different answers and only the caller knows which is
acceptable. Every member-typed figure goes through them.

**Acceptance**

- [x] „1,9" and „1.9" both read as 1.9, blank reads as `null`, and text that is not a figure stays
  refused (`AmountsTest`).

**Code:** `core/data/Amounts.kt`, `RefineryRepository`, `InventoryRepository`, `BankRepository`,
`BankStaffRepository`

---

### REQ-APP-REF-009 — Recording a run, without an importer the phone cannot have

„Neuer Raffinerieauftrag" — design chapter 11, artboards 4 and 5. The app could read a run and book
its yield but not record one.

**One scrolling form, not two screens.** The artboards split it because a 412 dp frame cannot show
both halves at once, not because it is two steps. Nothing here is a wizard, and a member who only
wants to record what a run cost should not have to walk through goods to reach the money.

**No extractor import, permanently.** The Extractor is a Windows desktop app whose handoff runs
through the ingest gateway and is consumed once in a browser; a phone cannot receive it. The scan
icon and the import box of artboard 1 are deliberately absent.

**Required is what the server requires, checked where the fields are.** A location, a method, and
**every** goods line complete: an input material and both quantities at 1 or more (`@NotNull
@Min(1)` on `RefineryGoodDto`). A half-filled line is not an omission the server tolerates — it
refuses the whole order with a `goods[0]`-shaped message that names an index rather than a field.
The CTA is validation-dimmed until the form is whole, without a padlock: nothing here is forbidden,
it is unfinished, and the design distinguishes the two.

**The material fields are pickers, not free text.** The wire wants a material id; a typed name
carries none, so every line would be dropped and the form could never be sent — with the CTA
correctly dimmed and no way to un-dim it. Typing again clears the pick, so a stale id is never sent
under a new label. The search is the one the Lager's booking form uses: a run's ore is an ordinary
material and a second list would be a second answer to the same question.

**„Gestartet" is a date and a time in the member's own format**, assembled into the instant the wire
wants. An unreadable pair is `null` rather than a guess. **„Endet" is computed** from start plus
duration and shown as text — a second editable time would be a place for the two to disagree. So is
the **profit preview**, which is the web's own definition: ore sales less costs and other costs.

**The money block starts closed.** All three of its fields are usually zero, and a block that is
usually empty should not stand between a member and the CTA.

**`/refining-methods` answers a page, not a list.** `/locations/refineries` beside it answers a bare
array, and the two are easy to assume alike — parsed as a list the picker renders empty and the form
is silently unsendable, which is exactly how it presented on a device.

**Acceptance**

- [x] A complete form may be sent; a typed material name without a pick may not
  (`RefineryCreateTest`).
- [x] A line without an output quantity, or with zero, is not sendable, and one incomplete line
  blocks the whole order (`RefineryCreateTest`).
- [x] Without a refinery or a method nothing is sent (`RefineryCreateTest`).
- [x] The start is read from the two fields, and a half-typed date is no date (`RefineryCreateTest`).
- [x] Verified on a device against the local test stack: both pickers load (`200`/`200`), the goods
  material picker searches, and `POST /api/v1/refinery-orders` answers **200** — the order lands as
  `IN_PROGRESS` at Levski · Cormack with 620 → 442 SCU at quality 874, and the app opens it.

**Vhost:** `/api/v1/refinery-orders` (POST), `/api/v1/locations/refineries`,
`/api/v1/refining-methods` — runbook Phase M.

**Code:** `RefineryRepository` (`RefineryCreateSource`), `RefineryCreateViewModel`,
`RefineryCreateScreen`
