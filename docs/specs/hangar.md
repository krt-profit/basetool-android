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

### REQ-APP-HANGAR-010 — The overflow says what each entry does, and why one cannot be used

The `⋮` opens a 268 dp dropdown right-aligned under the bar — on the phone as on the tablet, because
three entries do not justify a bottom sheet. The order is the artboards' own: **Home-Location
setzen, Hangar leeren, Import** (design ch. 08, artboards 4 and 5).

Every entry leads with an 18 dp glyph in its own tint. **„Hangar leeren" is red in the menu**, not
only in the modal it opens: the colour is the warning and the modal is the confirmation, and red
appearing first at the second step would be a surprise where there should be none.

**An entry that cannot be used stays.** It recedes to 45 % and gains a line saying why — „Keine
Schiffe im Hangar", „Ohne Verbindung nicht möglich". A menu that changes shape between openings
teaches a member nothing, and a dimmed row without a reason looks broken. Home-Location carries a
line even when it *can* be used, because „Ein Ort für die gesamte Flotte" is the fact a member needs
before tapping it.

An entry the caller lacks the **grant** for is drawn the same way plus a lock, and answers on tap
with the role's name (`REQ-APP-AUTH-013`) — never hidden.

---

### REQ-APP-HANGAR-011 — Emptying the hangar is guarded three times, and none of them is a typing hurdle

Design ch. 08, artboard 6 resolves the contradiction between 08.1 („type-safe") and 08.3: the danger
modal names the **count**, and there is **no** typing hurdle. Chapter 02 §7 reserves that hurdle for
irreversible admin actions on organisation-wide data; a personal hangar is the member's own and
comes back from another Fleetview import, so spending the hurdle here would blunt it where it is
meant to bite.

What guards it instead is three things in a row: the menu entry is already red, the modal names the
count **and** the consequence **and** the way back („Wiederherstellung nur über erneuten
Fleetview-Import. Einsätze, Aufträge und Lager bleiben unberührt."), and the confirm repeats the
count („Alle 4 Schiffe löschen").

The `DELETE` waits for the server. On success the app says **how many went** — an emptied hangar and
a hangar that was always empty look identical, so the count is the only thing that distinguishes "it
worked" from "nothing happened".

---

### REQ-APP-HANGAR-012 — The bulk home location states its scope instead of asking again

The sheet's CTA names the count („Für 4 Schiffe übernehmen") and a line under the picker states what
that means: it applies to every ship in „Meine Schiffe" and overwrites the places they have. The
figure is the length of the loaded list; there is no API field for it.

**No confirmation dialog.** Nothing is lost — the write sets a location and can be repeated at will
— so a second confirmation stacked on a sheet would be ceremony without a risk (design ch. 08,
artboard 10).

**A refusal keeps the sheet and the picked place.** Nothing was written, and re-picking after a
refusal charges the member for the server's answer. Success closes the sheet and says how many ships
it touched.

---

### REQ-APP-HANGAR-013 — The org-unit aggregate stays a table, and says what it cannot show

Design ch. 08, artboard 11 states the collapse rule and its limit in one breath: wide data tables
(≥ 5 columns) fall together into key-value cards on the phone — that is „Meine Schiffe" — but
**narrow aggregates stay tables**, because the collapse is about width, not about tables as such.

So the org-unit half renders as a table on the phone too: `SCHIFFSTYP | ANZAHL | FITTED`, each row
led by the manufacturer's lettermark, the fitted figure in the success tint. Above it sits the
figure band of artboard 1. Tapping a row is the artboard's own affordance — „Zeile antippen →
gefilterte Schiffsliste" — and it puts the type into the search and moves to „Meine Schiffe" rather
than opening a screen of its own.

**The LTI figure is not shown, and that is stated rather than faked.** Artboard 1 draws three tiles:
Schiffe, Fitted and LTI. The aggregate endpoint carries `count` and `fittedCount`, and
`SquadronShipDetailDto` carries owner, location and `fitted` — **no insurance at all**. A third tile
would have to invent the number or show a dash, and a dash claims the figure exists and is merely
missing today. It needs a field on the aggregate in the main repo before it can ship here.

**Acceptance**

- [x] The aggregate renders as a table on a phone, with its own headers (`HangarScreenTest`).
- [x] Every cell carries its column's weight. `KrtTable` passes `cell` a `RowScope` and leaves the
  weight to the caller; the header row applies it, so a table that forgets it puts its figures
  beside the wrong titles. Both tables here had that defect and it was invisible until two numeric
  columns sat side by side.
- [ ] The LTI figure. **Open** — blocked on the aggregate endpoint (main repo).

---

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

### REQ-APP-HANGAR-014 — The mark is the catalogue's abbreviation, and the chips carry the artboard's order and tone

Three things artboard 1 draws that a field-presence check passes and a picture comparison does not.

**The lettermark is the manufacturer's own short form**, not initials derived from the legal name.
`ManufacturerDto.abbreviation` carries it — „Drake", „MISC", „RSI" — and the mark takes the first
four characters, uppercased, so the square reads DRAK / MISC / RSI as the artboard draws it. The
initials rule that preceded it counted every word of a legal name and turned „Musashi Industrial
and Starflight Concern" into **„MIA"**, which is not what anyone calls MISC. Initials remain the
fallback for a maker the catalogue gives no short form.

Capped at four rather than shown whole because the catalogue's short forms are short *names* rather
than codes („Crusader" → „CRUS"): a visible truncation of the maker's own word beats an
abbreviation the app invented.

**Insurance comes before fitted.** The artboard's order, and the useful one — the policy is the fact
that expires.

**The insurance chip is neutral unless the policy is named.** A month count is a plain term and
takes the muted tone; anything else the catalogue passes through — „LTI" above all — is a standing
policy and takes the accent, which is what the artboard's orange marks. `Info` blue appears on no
chip in this chapter and was on every insurance chip.

**Acceptance**

- [x] Verified on the phone class against the rendered artboard: DRAK / DRAK / DRAK / MISC, the
      insurance chip first and grey, FITTED green, NICHT FITTED grey.

**Code:** `hangar/HangarScreen.kt` (`ManufacturerMark`, `markOrNull`, `ShipCardBody`,
`insuranceIsTerm`), `core/data/HangarRepository.kt` (`manufacturerAbbreviation`)

