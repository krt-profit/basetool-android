# Lager — the stock tree

> **Doc type:** Living spec · **Area:** `REQ-APP-INV-*` · **Design:** `docs/design/android/09 Lager.dc.html`
> **Server contract:** main repo `REQ-API-009`
> **Related:** [`api-contract.md`](api-contract.md)

The org unit's stock, as a three-level tree — material, holding, entry — and the bookings that move
it. Phase 2 shipped the two read levels; phase 3 added the entry level and the booking form
(`007`–`012`); `013` and `014` added the selection mode and the bulk re-book behind it. The quality slider
remains out.

---

### REQ-APP-INV-001 — Two reads, one per level

`/inventory/aggregated` draws the group rows; `/inventory/all/grouped?materialIds=…` fills a group
the member actually opened.

Deliberately **not** `/inventory/all`, the flat entry list. A tree that fetched every leaf to draw
its roots would pull the whole warehouse to show a dozen headings, and the member would wait for
rows they may never expand.

Which org unit's Lager this is follows from the `X-Active-Org-Unit-Id` header; nothing about scope
is sent by this screen.

**Acceptance**

- [x] The first level is read from the aggregate, asserted by path (`InventoryRepositoryTest`).
- [x] A group's stacks are requested by `materialIds`, one id at a time.
- [x] Nothing is fetched for a group before it is opened (`InventoryViewModelTest`).

**Code:** `InventoryRepository`, `InventoryViewModel.onToggleGroup`

---

### REQ-APP-INV-002 — An opened group keeps what it loaded; a refresh does not

Closing a group keeps its stacks, so re-opening is instant: the Lager changes slowly enough that a
member re-opening a group within one visit expects what they just saw.

**Pull-to-refresh drops all of it.** That is the gesture that means "the world may have moved", and
holdings are exactly what moves.

A group **closed while its read is in flight must not spring open** when the answer lands — the
member decided, and an answer arriving late does not overrule them.

**Acceptance**

- [x] Re-opening after closing does not re-fetch; a refresh empties the loaded set
  (`InventoryViewModelTest`).
- [x] A late answer for a closed group is discarded.

**Code:** `InventoryViewModel`

---

### REQ-APP-INV-003 — A group that failed to open stays open and says so

Closing it would look like the tap did not register, and the member would try again — against
whatever is failing.

An **emptied** group says so too, rather than looking unopened: a group emptied between the tree
loading and the tap is an ordinary race, not an error.

**Acceptance**

- [x] `StackPhase.Failed` renders its own line under the group (`InventoryScreenTest`).
- [x] An empty result renders "In dieser Gruppe liegt nichts mehr."

**Code:** `StackPhase`, `InventoryTree`

---

### REQ-APP-INV-004 — A group without a material id is shown and not tappable

The aggregate can carry a row the server did not attribute to a material. It still states an amount
the org unit holds, so dropping it would quietly lower what the tree adds up to. It cannot be asked
for either, so it offers no tap: a control that reacts to nothing is how a member concludes the app
is broken.

**Acceptance**

- [x] Such a row renders and its tap does nothing at all — no callback fires
  (`InventoryScreenTest`).

**Code:** `GroupRow`

---

### REQ-APP-INV-005 — "Nur mit Bestand" filters the page, and says nothing about the rest

The endpoint has no such parameter. The chip therefore hides rows from the page the member already
holds — and that is honest only because the count under the list keeps stating **the server's**
total, never the filtered one. The alternative was to leave the chip out; hiding rows the member can
already see is a smaller claim than a filter that pretends to have searched the warehouse.

**Acceptance**

- [x] Filtering changes what is drawn and leaves `total` untouched (`InventoryViewModelTest`).
- [x] The empty state distinguishes "the Lager is empty" from "nothing on this page is in stock".

**Code:** `InventoryState.visibleGroups`

---

### REQ-APP-INV-006 — Quantities are plain, never scientific

The wire carries these as doubles, and Kotlin renders `12500000.0` as `1.25E7`. A warehouse figure
that reads like a physics constant is one a member cannot check, so the repository renders the plain
form and the screen's shared formatter groups it.

**Acceptance**

- [x] `1.25E7` reaches the model as `12500000` (`InventoryRepositoryTest`).
- [x] `1250.5` renders as `1.250,5` in German (`InventoryScreenTest`).

**Code:** `InventoryRepository`, `common/Amounts.kt`

---

### REQ-APP-INV-007 — The third level is read on the tap that opens it, like the second

`/inventory/all/stack/entries?materialId=…&locationId=…&userId=…&quality=…` fills one **stack**,
not one group:
the four parameters are exactly the fields the stack row aggregates, so what comes back is the
entries that row sums up and nothing else.

A stack keeps what it loaded and a second tap closes it, matching the group level above it — and a
stack **closed while its read is in flight must not spring open** when the answer lands.

An entry the server sent without an id is dropped by the repository rather than drawn: a row that
cannot be addressed cannot be booked, and one that offers a booking action that always fails is
worse than one that is absent.

**Acceptance**

- [x] Opening a stack requests the four fields it aggregates (`InventoryRepositoryTest`).
- [x] A closed stack draws no entries; an opened one draws them (`InventoryScreenTest`).
- [x] A late answer for a closed stack is discarded (`InventoryViewModelTest`).
- [x] An entry without an id never reaches the screen (`InventoryRepositoryTest`).

**Code:** `InventoryRepository.entries`, `InventoryViewModel.onToggleStack`, `InventoryScreen`

---

### REQ-APP-INV-008 — One form for every booking, and a mode is only offered when it can be used

Booking in, booking out and editing an entry's note are one bottom sheet with a segment, not three
sheets. The amount is the field the moving modes share, and a member who typed it before changing
their mind must not type it again.

**The segment lists only the modes that apply to what the sheet was opened on.** The tree's own
action names no entry, so it shows booking in alone; an entry shows booking out and its note. A
segment half that can only ever return a refusal is a control that lies.

**Private stock is absent from this screen in both directions.** The Lager reads exclude it
(`i.personal = false` in the grouped and the entry query alike), so an entry that could be rebooked
never reaches the sheet, and material booked in as private would land where no screen of this app
can show it again. The rebooking mode and the "Persönlich" switch were both built, walked on a
device and then removed for exactly that reason.

Each mode's save is offered only once the form holds something the server will accept — the
transfer needs a recipient or a place *it does not already have*, the sale needs a terminal, a move
needs an amount above zero.

**Acceptance**

- [x] An entry is offered booking out and its note, and no rebooking (`BookingSheetTest`).
- [x] The amount survives a change of mode (`BookingViewModelTest`).
- [x] Each mode's minimum gates the save (`BookingViewModelTest`).
- [x] A transfer to the entry's own holder, changing no place, is not submittable
  (`BookingViewModelTest`, `BookingSheetTest`).

**Code:** `BookingViewModel`, `BookingSheet`, `BookingHost`

---

### REQ-APP-INV-009 — Every booking echoes the entry's version, and a refusal keeps the typing

The write DTOs carry the `version` the entry was read with; a concurrent change comes back as
`409 OPTIMISTIC_LOCK` and is shown in the app's own wording, not the server's.

**The form stays open with every field as typed.** A conflict is not a reason to make a member
re-pick a material, a place and an amount — and the reload they need is the tree's, not the form's.

**A booking that lands re-reads the whole open path and leaves it open.** The group's total, the
stack's total and the entry list can all have changed at once, so all three are re-read rather than
patched; collapsing the tree instead would make the member re-open the group and the stack to see
what their own booking just did.

**Acceptance**

- [x] A book-out sends the version it read (`BookingViewModelTest`).
- [x] A refused booking keeps the amount and shows the conflict copy
  (`BookingViewModelTest`, `BookingSheetTest`).
- [x] A booking that lands closes the form and re-reads the tree (`BookingViewModelTest`).
- [x] The open group and the open stack are still open afterwards (`InventoryViewModelTest`).

**Code:** `BookingViewModel.onSave`, `InventoryViewModel.onBookingSaved`

---

### REQ-APP-INV-010 — Offline disables the bookings; it never queues them

Both booking affordances — the screen's own action and an entry's — and the form's save are
**disabled and faded** while the device has no network, under a band saying why.

A booking taken offline and sent minutes later would land against a Lager that has moved on, and
the member would never see the conflict it caused. This is the same rule as
[`REQ-APP-PI-003`](personal-inventory.md); the band and the fade are now one shared composable so
the four write surfaces cannot drift apart.

**The band and the fade sit in one shared composable** (`ui/OfflineWrites.kt`) rather than a copy
per screen: four private copies of the same rule is how one of them ends up saying something else.

**Acceptance**

- [x] Offline, neither booking action is enabled and the band is shown (`InventoryScreenTest`).
- [x] Offline, the form's save is disabled and the band is shown (`BookingSheetTest`).
- [x] A save attempted offline sends nothing (`BookingViewModelTest`).

**Code:** `ui/OfflineWrites.kt`, `InventoryScreen`, `BookingSheet`, `BookingViewModel`

---

### REQ-APP-INV-011 — A sale offers the terminals of the entry's own material

`/materials/{id}/terminals` is read when the sale mode is chosen, and it is read by the **entry's**
material id — which is why the entry model carries one. Reading it from the picked material would
work for booking in and never for a sale, which is the only mode that can sell.

Without terminals the sheet **says so** rather than showing an empty list that reads as a failed
load. The wire field is a free terminal name, so a sale is still possible without the catalogue.

**Acceptance**

- [x] Choosing "Verkaufen" on an entry lists that material's terminals (`BookingViewModelTest`).
- [x] With none recorded the sheet says so (`BookingSheetTest`).

**Code:** `BookingViewModel.loadTerminals`, `InventoryRepository.terminals`

---

### REQ-APP-INV-012 — A note is its own mode, and emptying one is a change

The note is a separate `PUT` that moves no material, so it needs no amount — requiring one would
make the mode unusable. It opens with what the entry already says.

**An emptied note is a deliberate edit, not an incomplete form**, and saves as no note at all. A
member who cannot clear a note has to overwrite it with a space.

**Acceptance**

- [x] The note mode opens with the entry's note and offers no amount field
  (`BookingViewModelTest`, `BookingSheetTest`).
- [x] Clearing the note is submittable and sends `null` (`BookingViewModelTest`).
- [x] An unchanged note is not submittable (`BookingViewModelTest`).

**Code:** `BookingViewModel`, `BookingSheet`

---

### REQ-APP-INV-013 — Selection is a set of entries, and the mode says so on every surface

Long-pressing a row starts a multi-selection; a plain tap then continues it, because having to keep
long-pressing every further row makes picking twelve stacks a chore nobody finishes.

**The selection is always a set of *entries*.** A group or stack row carries no selection state of
its own — long-pressing one selects every leaf beneath it (design ch. 09, artboard 5: „Auswahl ist
IMMER Eintrags-Menge"). That is also what the endpoint takes: `POST /inventory/bulk-rebook` in
`LOCATION` mode wants entry ids plus **one** target, and the sources may differ per entry, so a
group spanning three hangars is not a special case. A branch whose entries have not been read
selects nothing rather than guessing at ids.

**While it runs, the mode owns the frame.** The top bar is replaced by „✕ n gewählt", and the
floating action button and the bottom navigation give way to the action bar at the foot. Nothing
about the mode is subtle, because the alternative is a member who does not know why a tap now
selects instead of opening. There are exactly two ways out — the ✕ and the system back gesture —
and neither is "deselect the rows one at a time".

**Row actions step aside for the checkbox.** Buchen and Zuordnen are not offered while selecting: a
tap would otherwise mean two things at once.

**Collapsing is a change of view, not of selection.** A collapsed group keeps its picked entries,
its chip keeps counting them (`n gewählt`, against `n/m gewählt` while open), and the action bar
keeps counting them too — otherwise rows in play would silently vanish with the branch.

**A selection spanning rows that are not the caller's locks the bulk action**, in the same
disabled-style-but-tappable form an individual row uses (`REQ-APP-AUTH-013`); the refusal names the
rule and **the selection survives it**.

**Acceptance**

- [x] Long-pressing a group or stack selects all its leaves, and again clears them; an unopened
  branch selects nothing (`InventoryViewModelTest`).
- [x] Collapsing keeps the selection and the group's count (`InventoryViewModelTest`).
- [x] Verified on a device against the test stack: the head becomes „✕ 1 gewählt", the group wears
  „1/1 GEWÄHLT", the row wears its checkbox, the FAB and the navigation are gone, and „Umbuchen"
  renders locked over somebody else's row.

### REQ-APP-INV-014 — The batch reports what it did, in the sheet, before anything closes

`POST /inventory/bulk-rebook` in `LOCATION` mode answers with two figures: **rebooked** and
**skipped**. A row already standing at the target is *skipped*, which is not a failure — nothing
needed doing.

**The result is the sheet's second step, not a toast.** A skipped count without its sentence reads
as that many failures, and a toast is too fleeting to carry the sentence (design ch. 09, artboard 9).
Two tiles state the figures — the rebooked one in the success tint, the skipped one neutral — and a
line underneath names the target and says in words that nothing was wrong. Only **Schließen** ends
the batch: it leaves selection mode and re-reads the tree, dropping the cached entries as well as
the group list, because the rows that just moved still carry their old place.

**The skip is announced before the write, too**, under the target picker — a member told afterwards
reads it as damage.

**A refusal changes nothing and takes nothing away.** On `403` the sheet stays open, the message
names what happened, and the **selection survives** (artboard 10). Re-picking twelve rows to retry
would punish the member for the server's answer. Real failures — network, `5xx` — leave the sheet
open the same way; there is no silent abort.

**Acceptance**

- [x] A finished batch keeps the sheet open on its result and keeps the selection; closing it clears
  both and drops the cached entries (`InventoryViewModelTest`).
- [x] A refused batch keeps the sheet, the error and the selection, and produces no result
  (`InventoryViewModelTest`).
- [x] Verified on a device against the test stack by re-booking a row onto the place it already
  stood at: `UMGEBUCHT 0 / ÜBERSPRUNGEN 1`, the sentence naming ARC-L1, and Schließen returning the
  tree to its normal head.

---

## Known gaps, stated rather than omitted

- **No Material and no Ort filter.** Both need pickers (design ch. 02 bottom sheets) and a catalog
  read; they ship with the shared picker work, together with the Einsatz list's date range.
- **Private stock is not reachable from the app at all.** It needs the `my-inventory` read, which is
  its own screen in the web app and its own slice here. `POST /inventory/{id}/personal-rebook` is in
  the contract set and on the vhost allow-list ready for it, and no app code calls it today.
- **The quality of an existing entry cannot be corrected.** The correction endpoint is not in the
  contract set, and booking in is the only place a quality is set.
- **The quality mini-gauge is a chip, not a gauge.** The 44 dp 0–1000 gauge of the design is a
  drawn control; the value is shown as `Q 880` until it exists.
- **Collapse state is not persisted.** The web app keeps it in `localStorage`; here it lives for the
  visit. Persisting it needs the read cache, which is its own spec area.

## Contract-set dependency (main repo)

`GET /api/v1/inventory/aggregated`, `/inventory/all/grouped`, `/inventory/all/stack/entries`, `/materials/search`, `/materials/{id}/terminals`,
`/locations/search` and `/users/search` are in the `REQ-API-009` contract set and the vhost
allow-list. So are the writes — `POST /inventory`, `POST /inventory/{id}/book-out`,
`POST /inventory/{id}/personal-rebook` and `PUT /inventory/{id}/note` — as named exceptions to the
vhost's read-only guard on the family; every other verb on it still answers `405`. Three of the four
are sent by this screen; `personal-rebook` waits for the `my-inventory` slice (see the gaps above).
