# Hangar — my ships and the org unit's fleet

> **Doc type:** Living spec · **Area:** `REQ-APP-HANGAR-*` · **Design:** `docs/design/android/08 Hangar.dc.html`
> **Server contract:** main repo `REQ-API-009` (`/hangar/my-ships`, `/hangar/squadron-overview`)
> **Related:** [`api-contract.md`](api-contract.md)

The member's own ships and the aggregate over their active org unit. **Read-only**: adding,
editing, deleting, the Fleetview import and the overflow's bulk actions are all mutations and belong
to Phase 3.

---

### REQ-APP-HANGAR-001 — `my-ships`, never `ships`

The family also carries `GET /hangar/ships`, which reads **every** member's ships behind a
permission most members do not have, and `GET /hangar/users/{id}/ships`, which is admin-only. The
app reads the caller's own list and the aggregate the org unit already publishes; neither of the
other two is on the vhost's allow-list, and neither should ever be.

Which org unit the aggregate covers follows from the `X-Active-Org-Unit-Id` header the interceptor
sets. Nothing about scope is sent by this screen.

**Acceptance**

- [x] The aggregate is read from `/squadron-overview`, asserted by path (`HangarRepositoryTest`).
- [x] The filter is sent as `search`, encoded exactly once, and a blank one is left off the wire.

**Code:** `HangarRepository`

---

### REQ-APP-HANGAR-002 — The two halves are separate state

Rows, totals, page index and failure are per half. Sharing them would make a switch show the other
half's content for a frame, and a failure on one half would present itself as a failure of the
other.

Switching reloads that half **from page 0**. Keeping whatever was last loaded would show a member an
aggregate from ten minutes ago under a header that says it is current.

**Acceptance**

- [x] Switching reads the new half and leaves the old half's rows intact (`HangarViewModelTest`).
- [x] Tapping the half already showing does nothing.
- [x] The filter applies to the showing half only; the other is not re-read.

**Code:** `HangarViewModel`, `HangarState`

---

### REQ-APP-HANGAR-003 — The card shows names, not the wire's shape

`shipType`, `manufacturer` and `location` are nested objects on the wire; the card shows their
names. The model flattens them rather than mirroring the payload, so what the screen needs is
readable from the type.

The headline is the **type**, because that is what identifies a ship at a glance; the member's own
name for it, when they gave one, follows in quotes as the web app writes it. Insurance, fitted state
and place are optional in the web app's own form, and a card missing them must render rather than
vanish — "Keine Versicherung" is stated instead of an empty chip.

**Acceptance**

- [x] A fully populated ship maps to type, maker, name, insurance, place and fitted
  (`HangarRepositoryTest`).
- [x] A ship with none of the optional four still renders (`HangarScreenTest`).
- [x] A blank name is treated as no name.
- [x] A row without an id is dropped — it cannot be identified — but the server's total is passed
  through, because lowering it quietly would hide the fault.

**Code:** `Ship`, `ShipCard`

---

### REQ-APP-HANGAR-004 — Three empty states, because there are three facts

"You own no ship", "the org unit has none" and "your filter matches none" are different, and one
message for all three would tell a member something untrue about their own fleet. The own-hangar
empty state also names where ships are added today, since this build cannot add them.

**Acceptance**

- [x] Each renders its own title and message (`HangarScreenTest`).
- [x] A failure is a fourth state with a retry, never an empty hangar.

**Code:** `HangarEmpty`

---

### REQ-APP-HANGAR-005 — The three-number band is absent, and that is a deviation

Design ch. 08 §1 puts "Schiffe 42 · Fitted 31 · LTI 24" above the org half. That is an aggregate
over the whole org unit and the API offers no such total: `/squadron-overview` is **paged**, so
adding up what is loaded would state a number the page cannot know — the same silent-truncation
failure the main repo's ADR-0104 forbids, dressed as a headline figure.

The per-type rows carry their own counts, and those are the server's.

An aggregate endpoint would close this; it is a backend change and has not been asked for.

**Acceptance**

- [x] No total is computed on the device.
- [x] The footer states loaded-of-total for the showing half, pluralised for its own unit — ships
  or ship types.

**Code:** `HangarBody`, `countLabel`

---

---

### REQ-APP-HANGAR-006 — Only the member's own half is writable

The create action and the row taps exist on `Meine Schiffe` and nowhere else. The org aggregate is
a count per hull, not a list of ships, and the ships behind it belong to other people.

The write path is `POST/PUT/DELETE /api/v1/hangar/ships`, never `/hangar/users/{id}/ships`: the
second names a member, is the admin surface, and is not on the vhost at all.

**Acceptance**

- [x] The add action renders on the own half and not on the aggregate
  (`HangarScreenTest`, two cases — the compose rule accepts one `setContent` per test).
- [x] No request ever carries `/users/` (`HangarRepositoryTest`).
- [x] **Observed on a device (2026-08-23).**

---

### REQ-APP-HANGAR-007 — Insurance is a choice plus a number, never free text

The editor offers a segment — `LTI` or `Monate` — and, for the second, a numeric field bounded at
0–120. Nothing else can be entered, and the save action stays disabled until what is entered is
something the server accepts.

The server's rule is `^(0|[1-9]|…|120|LTI)$`. A free-text field would let a member type "lifetime",
or 240, and learn it was wrong only after the save — which teaches them the app is unreliable when
in fact it simply passed on something it could have refused itself.

**Acceptance**

- [x] 121 cannot be submitted, 120 can (`HangarViewModelTest`, `HangarScreenTest`).
- [x] An edit seeds the segment from the stored value — a month count means the second half
  (`HangarViewModelTest`).
- [x] **Observed on a device (2026-08-23):** a ship saved with `LTI`, then changed to 35 months.

---

### REQ-APP-HANGAR-008 — The editor is seeded from the row, hull and place included

Opening a ship fills every field from what the row already carries, which is why `Ship` gained
`typeId`, `locationId` and `version` in phase 3.

A member who only flips `Fitted` must not have to search for the hull again — and a form that
re-sends a hull the member re-picked by hand is a form that can lose it.

**Acceptance**

- [x] The hull, the place, the insurance kind and the fitted flag are all seeded
  (`HangarViewModelTest`).
- [x] The edit echoes the version the row was read at (`HangarViewModelTest`,
  `HangarRepositoryTest`).
- [x] A conflict keeps the editor exactly as it was (`HangarViewModelTest`).

---

### REQ-APP-HANGAR-009 — The hull picker admits what it is not showing

The catalogue runs to hundreds of hulls; the picker matches on the hull **and its maker** — "Anvil"
is how somebody looks for a Carrack they cannot spell — and shows the first eight matches with a
line saying how many more there are.

**Acceptance**

- [x] A maker matches (`HangarRepositoryTest`).
- [x] The overflow line states the remainder (`ShipEditorSheet`, plural resource).
- [x] A hull without an id never reaches the picker (`HangarRepositoryTest`).

## Known gaps, stated rather than omitted

- **No FAB, no overflow, no import.** Adding, editing, deleting, "Home-Location setzen", "Hangar
  leeren" and the Fleetview import are mutations (Phase 3). The design's chapter covers all of them;
  this build covers the reading half.
- **Manufacturer marks are text, not logos.** The design says so itself: the repo ships white SVGs
  for three makers only and they embed raster data, so the lettermark *is* the spec until clean
  vectors are re-exported.
- **The aggregate row is not tappable.** The design opens a filtered ship list from it; that list is
  `/hangar/ships`, which is exactly the permission-gated read this screen stays away from.

## Contract-set dependency (main repo)

`GET /api/v1/hangar/my-ships` and `/squadron-overview` are in the `REQ-API-009` contract set and the
vhost allow-list, as **exact** paths. The row's nested `shipType.name` and `location.name` are
frozen too — the contract guard walks two levels for that reason. `owner` is deliberately not
frozen: it is a full user record, always the caller's own here, and the app does not read it.
