# Lager — the stock tree

> **Doc type:** Living spec · **Area:** `REQ-APP-INV-*` · **Design:** `docs/design/android/09 Lager.dc.html`
> **Server contract:** main repo `REQ-API-009`
> **Related:** [`api-contract.md`](api-contract.md)

The org unit's stock, as a two-level tree. **Read-only**: booking in, out and across, the bulk
re-book and the quality slider are all mutations and belong to Phase 3.

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

## Known gaps, stated rather than omitted

- **The third level — the individual entry — is not drawn.** It is where booking happens, and
  booking is Phase 3. The stack row states how many entries it sums up instead.
- **No Material and no Ort filter.** Both need pickers (design ch. 02 bottom sheets) and a catalog
  read; they ship with the shared picker work, together with the Einsatz list's date range.
- **No long-press selection and no bulk re-book.** Mutations.
- **The quality mini-gauge is a chip, not a gauge.** The 44 dp 0–1000 gauge of the design is a
  drawn control; the value is shown as `Q 880` until it exists.
- **Collapse state is not persisted.** The web app keeps it in `localStorage`; here it lives for the
  visit. Persisting it needs the read cache, which is its own spec area.

## Contract-set dependency (main repo)

`GET /api/v1/inventory/aggregated` and `/inventory/all/grouped` are in the `REQ-API-009` contract set
and the vhost allow-list, as exact paths. The booking endpoints beside them are not, and the vhost's
read-only guard covers the family.
