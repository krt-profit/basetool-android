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

### REQ-APP-INV-015 — A transfer names all three of its targets, and the pool defaults to the one it is in

„Umbuchen" is a **kind** of booking out, not an operation of its own: the server takes it as
`POST /inventory/{id}/book-out` with `type: TRANSFER`. It carries **three** targets, and the app
offers all three, because the web frontend's Umbuchen dialog does:

| Target | Field | Offered as |
| --- | --- | --- |
| The recipient | `targetUserId` | the member picker |
| The place | `targetLocationId` | the place picker |
| The org-unit pool the moved row lands in | `targetOwningOrgUnitId` | a list of the **receiving** member's memberships |

**The pool picker asks about the receiving member, not the caller.** The server validates the
choice against the destination user's own memberships
(`OwnerScopeService.resolveOrgUnitForPickerOutputNullable`), so a picker filled from anyone else
would offer units the write then refuses. It reads
`GET /api/v1/users/{id}/memberships?allKinds=true` — **all four** org-unit kinds, Staffel and SK
and Bereich and Organisationsleitung. The default (`allKinds=false`) returns Staffel and SK only
and would hide a Bereich or OL member's own pool from a picker the server accepts it in.

**It presets to the pool the entry is already in.** This is the load-bearing half. A member who
changes only the place must not silently re-pool the row — everyone scoped to the old unit would
lose sight of stock that never left, with nothing on screen having said so. An explicit choice
survives a re-read that still offers it; otherwise the preset is restored.

**It is not drawn when there is nothing to choose.** A receiver with one membership has no decision
to make and the server resolves it; a receiver with none leaves the row unpooled, which is the
server's own outcome and not something a form can fix.

**`mergeStock` is offered only for an SCU material.** The server merges a `PIECE` transfer into a
matching target stack unconditionally, so a toggle there would be a control that changes nothing —
which a member cannot tell from one that does. The app also does not *send* the flag for a `PIECE`
material, nor for a discard or a sale, both of which terminate the row and create no stamp.

**The call to action names the move, not the mode.** Ausbuchen, Umbuchen and Verkaufen are three
different events in the ledger reached through one segment, and the button is the last thing a
member reads before committing one.

**Acceptance**

- [x] The pool options come from the receiving member, and change when the recipient does
  (`BookingViewModelTest`).
- [x] The pool presets to the entry's own (`BookingViewModelTest` — verified to fail without it).
- [x] An explicit pick survives a re-read that still offers it (`BookingViewModelTest`).
- [x] A receiver with no membership leaves it unset rather than guessing (`BookingViewModelTest`).
- [x] A transfer sends the pool and the merge opt-in (`BookingViewModelTest`).
- [x] A `PIECE` material never sends the merge opt-in (`BookingViewModelTest`).
- [x] A discard sends no pool even when one was picked (`BookingViewModelTest`).
- [x] „Umbuchen" is an out-kind and not a top-level mode (`BookingSheetTest`).
- [x] Walked on a device against the test stack: 12 SCU moved to another member's
  **Spezialkommando** pool, confirmed in the database as `SPECIAL_COMMAND`.

**Code:** `BookingViewModel`, `BookingSheet`, `InventoryRepository.orgUnitsFor`

---

### REQ-APP-INV-016 — „Herkunft der Menge": a deducted amount is sourced once per dimension

An entry's stock is tagged **twice and independently** — once by Auftrag, once by Einsatz — so the
same unit can be promised to both. When a quantity leaves the entry it is therefore sourced **once
per dimension**, not once in total. Every book-out and every transfer may carry
`jobOrderReductions` and `missionReductions` (REQ-INV-027, „Variante C"); what the member does not
assign comes from that dimension's not-yet-assigned rest.

The server's rules, mirrored in the sheet so a violation is shown before the write:

| Rule | The server answers | The sheet shows |
| --- | --- | --- |
| Per dimension, the assigned sum ≤ the deducted amount | 400 | „ÜBERZEICHNET +n", save refused |
| The rest absorbs what the tags did not cover | 422 | „Rest zu klein", save refused |
| Tags cover the whole deduction | accepted | „GEDECKT" |
| Part comes from the rest | accepted | „n aus Rest" |
| An omitted or all-zero plan | the default | nothing sent |

**One shape has no choice in it.** Exactly one tag and no rest means every unit leaving has to come
from that tag, so the field is filled from the amount and **locked** — typing anything else could
only ever trip one of the two refusals. The chip reads „AUTOMATISCH".

**A tag left at zero is omitted, not sent as zero.** An empty list is the server's documented
"take it from the rest first"; a list of zeroes says the same thing in a form that has to be parsed
to mean nothing, and it would make an untouched sheet look like a deliberate plan in the audit log.

> [!warning] No earmarks is not an empty rest
> The server sends no rest for a dimension it has no split in. Reading that absence as a rest of
> **zero** makes every ordinary entry — most of them — fail the „the rest cannot carry it" check.
> The existing booking tests caught this during implementation; `HerkunftPlanTest` now names it.

**Rows, not a table.** The web frontend lays this out as a grid of tag against amount, which needs
horizontal room a phone does not have. Design ch. 09 artboards 18–19 answer with a row per earmark
and, beneath each dimension, always the „Vom Rest" line — the two numbers a member reconciles sit
above one another rather than across a scroll. Auftrag takes the app's orange, Einsatz its info
blue, matching the allocation sheet so the dimensions read apart without a legend.

**Acceptance**

- [x] All five states and their chips (`HerkunftPlanTest`, 11 cases).
- [x] Floating-point noise does not read as an over-allocation — the plan is SCU-rounded before the
  epsilon comparison, as the server does.
- [x] An entry with no earmarks at all is never refused (`HerkunftPlanTest`).
- [x] An over-allocated plan and one whose remainder exceeds the rest both block the save
  (`BookingViewModelTest`).
- [x] The plan travels with the booking, and an untouched one sends nothing
  (`BookingViewModelTest`).
- [x] Walked on a device: „10 AUS REST" with „zugeordnet 3 SCU" and „Vom Rest 10 SCU (frei: 93,25)",
  then „ÜBERZEICHNET +10" with the server's own 400 wording once the tag claimed 20 of a 10 deduction.
- [ ] The sale's coupled-proceeds hint (artboard 19). The mission reductions already drive the split
  server-side; the live „Gekoppelter Verkaufserlös" line is not drawn yet.

**Code:** `HerkunftPlan`, `HerkunftSection`, `BookingViewModel`, `InventoryRepository`

---

## Known gaps, stated rather than omitted

- **No Material and no Ort filter.** Both need pickers (design ch. 02 bottom sheets) and a catalog
  read; they ship with the shared picker work, together with the Einsatz list's date range.
- **Private stock is not reachable from the app at all.** It needs the `my-inventory` read, which is
  its own screen in the web app („Mein Lager", `/inventory/my`) and its own slice here.
  `POST /inventory/{id}/personal-rebook` is in the contract set and on the vhost allow-list ready
  for it, and no app code calls it today. The same slice carries the bulk bar's
  `POST /inventory/bulk-checkout` and the `PERSONALIZE` / `DEPERSONALIZE` modes of
  `POST /inventory/bulk-rebook` — the app sends only `LOCATION` today.
- **The sale's coupled-proceeds hint is not drawn.** Mission reductions already decide who is
  credited what — that is server-side and works — but artboard 19's live „Gekoppelter Verkaufserlös"
  line under the sale amount is not there yet, so a seller cannot see the split before committing
  to it. See `REQ-APP-INV-016`.
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

### REQ-APP-INV-017 — The tree has a holder level, and it carries their total

Artboard 1 draws the Lager as **material → holder → entry**. The app drew **material → stack →
entry**, where a stack merged the holder and the place into one row: „Rhea · ARC-L1". A member
holding one material at two places therefore got two unrelated rows, and nothing on the screen said
how much they held in total.

The tree now heads each holder's stacks with their name and their **subtotal**, and a stack names
its place alone. The app keeps its fourth level — the entries inside a stack — which the artboard
does not draw only because every stack in its fixture holds one entry.

**The wire has no holder level.** `/inventory/all/grouped` answers stacks keyed by
(holder, place, quality), so the app groups them. This is the one place the app adds a figure of its
own, and it is defensible for a reason that does not generalise: **a subtotal is the sum of the rows
directly beneath it**, every one of which the member can see. Nothing is derived from data that is
off screen, which is what separates it from the „Zugesagt" total this app refuses to compute
(`REQ-APP-ORDERS-004`).

Two rules keep it honest:

- **Summed as `BigDecimal`, from the strings the server sent.** A quarter-SCU does not drift, and no
  locale parses into the arithmetic.
- **If any one stack's amount cannot be parsed, the whole subtotal is dropped.** A total quietly
  missing one of its parts reads as authoritative and is wrong; no total reads as no total.

**The holder row does not open or close.** Its stacks are already visible — it is a heading with a
figure, and a chevron would promise a fourth thing to unfold that does not exist. The artboard draws
it without one.

**Order is the server's, first-seen.** Re-sorting by name or by amount would move rows a member had
just looked at.

The stack keys, the selection and the booking sheet are untouched: `stackKey` is still
(material, holder, place, quality), and the holder level is presentational.

**Acceptance**

- [x] A holder's two places are headed by one row carrying their sum, with both places' own figures
      beneath it (`InventoryScreenTest`).
- [x] An unparseable amount anywhere in a holder's stacks removes the subtotal rather than
      shortening it (`InventoryScreenTest`).
- [x] A stack names its place; the holder is no longer repeated on it (`InventoryScreenTest`).

**Code:** `inventory/InventoryScreen.kt` (`byHolder`, `HolderStacks`, `HolderRow`, `openedGroup`)

### REQ-APP-INV-018 — On a tablet the Lager shows one material's entries beside the tree

From the **expanded** breakpoint the Lager is a list-detail, like Einsätze, Aufträge and Raffinerie:
the tree on the left, and on the right every entry of the selected material as a table —
*Nutzer · Ort · Qualität · Menge*, paged. `GET /api/v1/inventory/material/{materialId}`. Below that
width the tree keeps the whole screen and the pane does not exist, which is how the web has it too
(its material page is a page, not a panel).

**It is a different view, not a wider one.** The tree groups by holder and shows only what is
opened; the table shows every entry of one material at once, across all holders and places. That is
what a thousand dp of width is for, and it is why the Lager's answer to design round 8 §5 is the
pane rather than a capped measure: there was a second view to show, and the tree was only being
stretched (owner ruling, 2026-08-28; round 9 §4).

**One tap does both.** Tapping a material row opens it in the tree *and* selects it in the pane —
the same intent, and a second control on the row whose only job is „and also over there" would be
one affordance too many. The call is made only when the window is wide, so a phone never reads a
page nothing draws. **Assumption, flagged in round 9 §4:** if the two are wanted apart, the row
needs its own affordance and this changes.

**Its own seam and its own holder.** `MaterialDetailSource` carries the one read, because every
other inventory read answers a *branch* keyed by where in the tree the member is, and this answers a
material flat. `MaterialPaneLoader` carries the state, because `InventoryViewModel` was already at
the size where one more responsibility stops being findable — and both limits are enforced by
detekt rather than by taste.

**A stale answer is dropped.** The pane reads one material at a time and a member moves down the
tree faster than a page arrives; a read that lands after the selection moved on must not fill the
pane under the new material's heading. Re-selecting the material already shown does nothing, so
toggling a group shut and open again neither re-reads nor blanks what is being read.

**Read-only.** A booking is still made from the tree, where the row's own actions are.

**Acceptance**

- [x] Selecting reads page 0; re-selecting the same material does not read again
      (`MaterialPaneLoaderTest`).
- [x] A failed read leaves the pane on its material and the retry re-reads it
      (`MaterialPaneLoaderTest`).
- [x] An answer for a material the pane has left is dropped (`MaterialPaneLoaderTest`).
- [x] Verified on the tablet: Quantainium selected, the pane heads „Quantainium" and lists
      test-admin · ARC-L1 Wide Forest Station · 880 · 408,5 SCU, closing with „ALLE 1 EINTRÄGE".
      The app bar still says LAGER — the pane's title stays in the pane (`REQ-APP-UI-009`).

**Code:** `inventory/MaterialPaneLoader.kt`, `inventory/InventoryScreen.kt` (`MaterialPaneBody`,
`MaterialTable`), `core/data/InventoryRepository.kt` (`MaterialDetailSource`, `MaterialEntryPage`)

---

### REQ-APP-INV-019 — Sammel-Ausbuchen: whole rows, all or nothing, and three fields the call cannot carry

Design ch. 09 artboard 20. The **second action of the same selection bar** the bulk rebooking uses
— no second entry point and no second selection pattern. The same own-row gate applies, drawn as a
lock: the endpoint refuses a foreign row and takes the whole call down with it.

**Whole rows only**, which is what the endpoint does: each listed row is deleted in full and its
earmarks cascade away. Every row carries the „vollständig" chip **before** the CTA, because a
member expecting a partial amount has to learn that here and not afterwards; partial amounts are
the single book-out, which is the call that takes an amount.

**Append-only in the sense the artboard means it:** the rows leave the stock and
`INVENTORY_BULK_CHECKED_OUT` stays in the audit log.

> [!warning] Three drawn fields with no wire field, and one outcome that cannot exist
> `POST /api/v1/inventory/bulk-checkout` carries **only `itemIds`**. There is no „Grund"
> („Verbraucht" / „Verworfen"), no note, and no per-row Herkunft planner — the earmarks cascade
> away with the row rather than being sourced. And the call is **atomic**: a foreign row or an
> unknown id refuses everything, so the artboard's „ausgebucht / übersprungen" result — borrowed
> from the bulk *rebooking*, which really does report one — cannot exist here. The sheet shows the
> done step or the refusal, and a refusal keeps the selection. All on the design gap list.
>
> The reason and the source planner **do** exist — on `POST /inventory/{id}/book-out`, which is the
> rich call. A per-row loop over it would honour the artboard fully and was rejected: it would
> half-succeed, which is worse than a refusal that changed nothing.

**Acceptance**

- [x] A bulk checkout sends every selected row and ends the selection (`InventoryViewModelTest`).
- [x] A refused one keeps the selection and does not claim success (`InventoryViewModelTest`).
- [x] The action wears the same own-row lock as the bulk rebooking (`InventoryScreen`).
- [ ] Observed on a device.

**Code:** `InventoryRepository.bulkCheckout`, `InventoryViewModel.BulkCheckoutActions`,
`InventoryScreen.BulkCheckoutSheet`

---

### REQ-APP-INV-020 — „Game-Items" is a surface of its own, and its rows open in place

Design ch. 09 artboard 21, reached from „Mehr". The Lager tree groups by **material**, where an
item counted in pieces disappears between SCU figures; the question here is „how many do we have
and where", with pieces as the unit throughout. Read-only, and no price column — game items have
no UEX price.

**One read, and therefore an honest filter.** `GET /inventory/all/grouped?catalog=ITEM` answers
with every item and its stacks, each stack carrying its holder and its place — which is where
„N Halter · M Orte" comes from, counted rather than asked for. Because the whole list arrives at
once, the search and the category chips filter a **complete** list: there is no page behind them
and so nothing to declare (ADR-0104 is satisfied by the read).

**The chips are built from the data.** `kind` is free text on the wire, so a hardcoded
„Waffen / Medizin / Bauteile" would quietly hide whatever the catalogue grows next.

> [!warning] The artboard's jump into the Lager tree is not reachable
> It asks for a row to open the item in the tree on its path. The app's tree is **material-only**
> — `InventoryRepository`'s three tree reads send no `catalog` parameter — so there is nowhere to
> jump to. A row therefore opens **in place** and lists the places that came with it: the same
> information, one tap earlier. Teaching the tree `catalog=ITEM` is the real fix and is on the gap
> list.

**Acceptance**

- [x] The category chips come from the data (`GameItemStockTest`).
- [x] The search and the category narrow together, over a complete list (`GameItemStockTest`).
- [x] The total counts what is shown (`GameItemStockTest`).
- [ ] Observed on a device.

**Code:** `InventoryRepository.gameItemStock`, `GameItemStock`, `GameItemStockViewModel`,
`GameItemStockScreen`

---

### REQ-APP-INV-019 — Booking in needs a grade, and the cSCU hint belongs to SCU

**A material row without a grade is a rejection the form knows about.**
`InventoryItemCreateDto` pairs the two with an `@AssertTrue` (REQ-INV-029): a material row requires
a quality, a game-item row forbids one. The web form marks the field `required`. The app left it
optional, so the CTA invited a booking the server answers with a `400` — legible since the
field-error fix, but still a refusal the form could have prevented. Zero is a grade and passes;
blank is the absence the server refuses.

**The cSCU/µSCU hint is shown only for an SCU material.** cSCU and µSCU are SCU words, and over a
field counting pieces the hint offered fractions of a thing that has none. This is the rule the
merge opt-in beside it already followed — the web applies it too, hiding its own hint for `PIECE`.

**Acceptance**

- [x] Material, place and amount are not enough; a grade is (`BookingViewModelTest`).
- [x] A grade of `0` passes and a blank one does not (`BookingViewModelTest`).
- [x] The hint stands beside an SCU material and is absent from a piece one (`BookingSheetTest`).
- [ ] Walked on a device: outstanding.

**Code:** `BookingViewModel` (`submittable`, `qualityGiven`), `BookingSheet`

---

### REQ-APP-INV-020 — A book-in names one of two catalogues, and they are not interchangeable

The web's booking form has carried a Material/Item switch since the item inventory landed
(REQ-INV-029, ADR-0101); the app could read item stock („Game-Items" under „Mehr") but never book
any in. It can now, and the two kinds are **kinds of row rather than one list with a filter** —
`InventoryItemCreateDto` takes them in mutually exclusive fields, mirrored by the DB check
constraint `chk_inventory_item_catalog_xor`, and asks different things of each:

| | material row | item row |
| --- | --- | --- |
| catalogue field | `materialId` | `gameItemId` — never both, never neither |
| grade | **required** | **forbidden** |
| amount | SCU or pieces, per the material | positive **whole** units, always |
| `mergeStock` | honoured for SCU | ignored; a piece row always merges |
| mission earmark | allowed | refused (REQ-INV-031) |

Each of those is a `400` when got wrong, so the form settles all of them before the CTA lights:
the item side asks for no grade **and shows no field for one** (a field whose every value is a
rejection is worse than none), the amount is refused unless whole, and the SCU affordances — the
cSCU hint and the merge opt-in — stay away because an item is never measured in SCU.

**Switching kinds drops the other side's pick**, rather than keeping it out of sight. The server
takes exactly one catalogue reference, so a material still held in state behind a showing item is a
booking nobody can watch being assembled — and the grade goes with it, because the item row refuses
one. Pinned by a test that picks a material, types a grade, switches, and asserts that neither
reaches the wire.

**The amount label now says the unit in German.** It printed the wire's own word, so a piece-counted
material read „Menge (PIECE)" on a German form. `unit()` still answers the wire value — the
whole-number check compares against it — and `unitLabel()` is the rendering.

Earmarks travel with the booking — see `REQ-APP-INV-021`, which closed the last difference between
the two forms.

**Acceptance**

- [x] An item booking needs its item and a whole amount, and no grade (`BookingViewModelTest`).
- [x] Half an item is not a quantity (`BookingViewModelTest`).
- [x] A material and a grade picked before the switch reach neither the state nor the wire
  (`BookingViewModelTest`, `InventoryRepositoryTest` — asserted as an absence).
- [x] The item side draws the switch, no grade field, and „Menge (Stück)" (`BookingSheetTest`).
- [x] The item picker searches `/api/v1/orders/item-catalog` and reports its overflow
  (`InventoryRepositoryTest`).
- [ ] Walked on a device: outstanding.

**Code:** `InventoryRepository` (`BookInDraft.gameItemId`, `gameItems`, `GameItemOption`),
`BookingViewModel` (`BookingCatalogKind`), `BookingSheet`

---

### REQ-APP-INV-021 — Earmarks are entered with the booking, not after it

The web has split at check-in since Variante C (REQ-INV-027 R4): booking 400 SCU in and saying „250
for #91, 150 for #104" is **one** request, and the server checks the sum against the amount and
every target against its own requirement in the same transaction that creates the row. The app
booked first and assigned afterwards, one call per target, which is a different thing in four ways
a member can feel:

- two or three steps where the web has one;
- between them the stock sits **unassigned** in the Lager, where everyone else sees it as free;
- each nachtrag is its own write with its own `version` echo and its own 409, where the web's split
  can only land or fail as a whole;
- abandoning after the booking leaves an unassigned row that looks like any other.

The app now sends `jobOrderAllocations` / `missionAllocations` with the booking. What follows from
the server's rules:

**The CTA goes dark when a split promises more than is being booked in.** The server refuses the
**whole booking**, not just the earmark, so letting it be sent would take a member's row away for a
figure they could have been shown.

**The two splits are reconciled apart.** The same 400 SCU may be promised to an Auftrag *and* to an
Einsatz; one shared rest would be wrong in both directions. This is the same rule the Zuordnung
sheet already followed.

**An Auftrag that never asked for this material is not offered.** `JobOrderReferenceDto` carries
`requiredMaterialIds` / `requiredGameItemIds`, which the app used to drop on the floor; the web
filters its options by exactly those. Offering the rest would be offering a rejection. A target
naming **no** requirement is offered regardless — and missions carry none at all, so they are
filtered only for duplicates.

**An item row never carries a mission earmark** (REQ-INV-031). The form does not offer the split in
item mode, and the send drops it a second time for a split entered before the kind was switched.

**A new row starts at the rest, not at zero**, because a member earmarking a booking usually means
all of it. **An earmark without a readable amount is dropped rather than sent as a zero**, which the
server would create: a target promised nothing, standing among targets promised something.

**One rendering, not two.** The Zuordnung sheet and the book-in draw the same `Split`, over a
`SplitPane` / `SplitActions` pair. The one difference is a remove button, and it is a real one: a
sheet row exists on the server and is taken back by writing a zero, while a book-in row exists only
in the form until it is sent.

The split handling lives in `BookingSplitHolder` rather than on the view model — the function cap
was the signal, and the honest reading of it is that „what, how much, where" and „and to whom" are
two questions.

**Acceptance**

- [x] An earmark travels in the booking request, once (`BookingViewModelTest`,
  `InventoryRepositoryTest`).
- [x] A new row starts at the rest, and the next one at what the first left (`BookingViewModelTest`).
- [x] Promising more than is booked blocks the whole booking (`BookingViewModelTest`).
- [x] The two splits are reconciled apart (`BookingViewModelTest`).
- [x] An item row never carries a mission earmark (`BookingViewModelTest`).
- [x] An Auftrag that never asked for the material is not offered (`BookingViewModelTest`).
- [x] An earmark without an amount is dropped, not sent as a zero (`InventoryRepositoryTest`).
- [ ] Walked on a device: outstanding.

**Code:** `InventoryRepository` (`BookInDraft`, `AllocationTarget.krtAccepts`),
`BookingSplitHolder`, `BookingViewModel`, `BookingSheet` (`BookInSplits`), `AllocationSheet`
(`Split`, `SplitPane`, `SplitActions`)
