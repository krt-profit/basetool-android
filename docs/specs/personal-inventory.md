# Mein Inventar (`REQ-APP-PI-*`)

The member's own stock and their own blueprints — one screen, two halves (design ch. 09 § 4).
Phase 3's first two slices, and the app's first writes.

Upstream contract: `REQ-API-009` in the main repo freezes `GET/POST /api/v1/personal-inventory`,
`GET/PUT/DELETE /api/v1/personal-inventory/{id}` and `GET /api/v1/uex/locations/search`, including
the **required request fields** of the two write bodies.

---

### REQ-APP-PI-001 — Everything here is the caller's own, and the app never says whose

No path in this family names a user. The list is me-scoped by the server, the editor sends no id
but the row's own, and the screen states it in words the member can see:
"Nur für dich sichtbar."

That is why this slice went first. Phase 3 was ordered by ascending risk (owner decision,
2026-08-23): a mistake here can only affect the member making it, which is the right place to build
the write plumbing — the request verbs, the version echo, the conflict dialog, the offline rule —
that the five riskier slices then inherit.

**Acceptance**

- [x] The repository sends no user id on any call (`PersonalInventoryRepositoryTest`).
- [x] The empty state and the editor both say the list is private (`PersonalInventoryScreenTest`).

---

### REQ-APP-PI-002 — A save echoes the version it read, and a conflict keeps the typing

`PUT` carries the `version` the row was read at. A `409 OPTIMISTIC_LOCK` is rendered as its own
message — "Jemand anderes hat diesen Eintrag inzwischen geändert" — and **the editor keeps every
field exactly as the member left it**.

Clearing the form on a conflict would make the member pay for somebody else's edit. The version is
the only thing that has to be re-read, and re-reading it is what the reload after a successful save
is for: the server owns the new number, and the next edit has to be composed against it.

`POST` carries no version: there is nothing yet to conflict with, and the frozen contract records
that difference.

**Acceptance**

- [x] An edit sends the version the row was read at (`PersonalInventoryViewModelTest`).
- [x] A conflict leaves name, quantity and place untouched and reports itself
  (`PersonalInventoryViewModelTest`, `PersonalInventoryScreenTest`).
- [x] A successful save closes the editor and re-reads the list, rather than patching the row in
  place with a version the client guessed (`PersonalInventoryViewModelTest`).
- [x] The server's own 409 is classified as `OptimisticLock`, not as a generic failure
  (`PersonalInventoryRepositoryTest`).

---

### REQ-APP-PI-003 — Offline disables writes; it never queues them

While the device reports no network, the create action, the row taps and the delete action are
**visible and disabled** at 0.45 opacity, under a line that says why (design ch. 14).

Queueing is the tempting alternative and it is wrong here: a held-back mutation carries a `version`
that ages while it waits, which is exactly the write the server has to refuse. The member would
compose an edit, put the phone away, and learn hours later that it never landed. Disabled up front
is information; a failure after a filled-in form is a waste of their time.

Disabled rather than hidden: a missing button cannot explain itself.

The signal is `ACCESS_NETWORK_STATE` (owner decision, 2026-08-23) — a normal permission, no runtime
prompt, nothing leaves the device. It answers "is there a network", not "does the backend answer";
a captive portal or a backend outage still fails at the request, with the request's own message.

**Acceptance**

- [x] A save is not sent while offline, and the editor stays open
  (`PersonalInventoryViewModelTest`).
- [x] A delete is not sent while offline (`PersonalInventoryViewModelTest`).
- [x] The state follows the device both ways (`PersonalInventoryViewModelTest`).
- [x] The band and the disabled action render together (`PersonalInventoryScreenTest`).

---

### REQ-APP-PI-007 — The editor scrolls, so its actions cannot be pushed off the screen

The sheet's content is a scrolling column, and the place results inside it are a plain column
rather than a lazy one — two scroll containers in the same direction cannot nest, and 25 capped
rows need no laziness.

Found on a device, not in a test: with a place chosen and the keyboard up, the ABBRECHEN/SPEICHERN
row sat past the bottom edge and **the entry could not be saved at all**. The screen test rendered
the sheet's content directly at a fixed size and passed throughout. A sheet is only ever as tall as
the shortest phone it runs on.

**Acceptance**

- [x] The save action can be scrolled to with a place chosen, a note filled and twelve results
  listed (`PersonalInventoryScreenTest`).
- [x] **Observed on a device (2026-08-23)**, before and after.

---

### REQ-APP-PI-004 — The editor is the API's fields, not the Lager's form

Design ch. 09 § 4 says the editor is "das Buchen-Formular (Frame 2) im Modus „Persönlich"". It is
not, and cannot be: that form is material + SCU quantity with cSCU/µSCU precision + quality
(0–1000) + a Lager. A personal entry is **free text**, a **whole-number** count, a **UEX place** and
a note — there is no material, no quality and no sub-unit on the wire.

**Recorded deviation** (2026-08-23), of the same class as the three phase-2 ones: the aggregate the
design assumes does not exist in the API, and a client could only fake it. The form is built from
the design system's own field components, so it looks like the rest of the app; only the fields
differ, and they differ because the data does.

The Blueprints half arrived in the slice immediately after, and the segment with it.

**Acceptance**

- [x] The editor offers name, quantity, place and note — and nothing the API cannot store
  (`PersonalInventoryScreenTest`).
- [x] It is a `KrtBottomSheet` and the delete confirmation is a `KrtModal`, never a platform dialog.

---

### REQ-APP-PI-005 — The place picker admits when the list was cut

Places come from `GET /api/v1/uex/locations/search`, capped at 25. When the answer comes back full,
the picker says so: "Nur die ersten N Treffer."

UEX knows hundreds of places and several share a name, so the rows carry the parent and the star
system as well. A picker that silently drops the place a member is looking for sends them hunting
for a bug that is a cap (ADR-0104 in the main repo).

Below two characters nothing is searched: at one character the answer would be most of the
catalogue, arriving one keystroke before it is useful.

**Acceptance**

- [x] The request sends the cap, and a full answer sets the capped flag
  (`PersonalInventoryRepositoryTest`, `PersonalInventoryViewModelTest`).
- [x] The picker renders the notice (`PersonalInventoryScreenTest`).
- [x] One character searches nothing; typing is debounced to one request
  (`PersonalInventoryViewModelTest`).
- [x] A place kind this build does not know maps to `UNKNOWN` rather than failing the row
  (`PersonalInventoryRepositoryTest`).

---

### REQ-APP-PI-006 — A delete names what it is about to delete

The confirmation is a danger-toned `KrtModal` carrying the entry's name. "Wirklich löschen?" alone
is a question the member cannot answer — a list of similar entries is exactly where a mis-tap
happens.

Deleting has its own action rather than sharing the row's tap target: a mis-tap that opens the
editor costs a dismissal, a mis-tap that deletes costs the entry.

**Acceptance**

- [x] Nothing is deleted before the confirmation (`PersonalInventoryViewModelTest`).
- [x] The modal names the entry (`PersonalInventoryScreenTest`).
- [x] A failed delete is reported once and then cleared (`PersonalInventoryViewModelTest`).

---

---

### REQ-APP-PI-008 — One screen, two halves, two view models

The segment (`Items` / `Blueprints`) belongs to the screen; each half keeps its own view model and
its own state.

They read different endpoints and fail independently: a Blueprints outage must not empty the Items
list a member was reading. Switching tabs keeps what each half already loaded, and the chosen tab
survives process death — coming back to the wrong half is the kind of small wrongness that is hard
to name and easy to feel.

The Blueprints half loads on **first display**, not on first composition: it costs two requests, and
a member who never opens the tab should never pay for them.

**Acceptance**

- [x] Each half has its own view model, and the route wires both (`MeinInventarRoute`).
- [x] **Observed on a device (2026-08-23):** the segment switches, and each half keeps its list.

---

### REQ-APP-PI-009 — The craftability chip says nothing rather than guessing

Craftability is a **second, independent read**. While it has not answered — or after it failed —
the row carries **no chip at all**.

A chip reading "nicht baubar" because a request did not come back would be a claim about the
member's stock made out of an outage. The list itself is still true and stays on screen.

Three states when it does answer: `Baubar`, `N Materialien fehlen`, and `Rezept unbekannt` for a
recipe the server could not resolve — the last one because a blueprint whose recipe is unknown is
not the same as one that cannot be built.

**Acceptance**

- [x] A failed craftability read leaves the list standing and the chips absent
  (`PersonalBlueprintsViewModelTest`, `PersonalBlueprintsScreenTest`).
- [x] The three answered states render as themselves (`PersonalBlueprintsScreenTest`).
- [x] **Observed on a device (2026-08-23)** against eight owned blueprints.

---

### REQ-APP-PI-010 — Refining is a second answer to the same question, not a second request

The `Mit Raffinerie` toggle switches the chip between what is reachable now and what is reachable
once refining counts. Both numbers come from the **same** call, which asks for them together
(`includeRefinery=true`).

Re-reading on a toggle would make a display preference cost a round trip, and the two answers could
then disagree because they were taken at different moments.

**Acceptance**

- [x] The toggle changes no request (`PersonalBlueprintsViewModelTest`).
- [x] The shortfall count follows the toggle, per material (`PersonalBlueprintRepositoryTest`).
- [x] **Observed on a device (2026-08-23).**

---

### REQ-APP-PI-011 — Only what the server calls removable is offered a remove action

A row is offered `Entfernen` **only** when the server sets `removable`. When the field is absent the
app assumes `false`.

Default-granted blueprints are not removable, and the rule is invisible from the row itself. A
button that answers `409` reads as a broken button rather than as a rule; assuming the permissive
value is how that happens.

**Acceptance**

- [x] An entry without the flag renders no remove action (`PersonalBlueprintsScreenTest`).
- [x] An absent flag maps to `false` (`PersonalBlueprintRepositoryTest`).
- [x] **Observed on a device (2026-08-23):** the eight default-granted blueprints carry no remove
  action; the one added by hand does.

---

### REQ-APP-PI-012 — The catalogue picker will not offer what the member already owns

A product with `ownedByCurrentUser` is listed — greyed, labelled "Hast du schon" — and **cannot be
picked**.

Hiding it would answer the member's actual question ("do I have this one?") with silence and send
them looking. Offering it would set up a create the server refuses.

**Acceptance**

- [x] An owned product cannot be submitted (`PersonalBlueprintsViewModelTest`), and the picker says
  why (`PersonalBlueprintsScreenTest`).
- [x] A product row without a key never reaches the picker (`PersonalBlueprintRepositoryTest`).
- [x] The search is capped, debounced, and needs two characters, like the place picker
  (`PersonalBlueprintsViewModelTest`).
- [x] **Observed on a device (2026-08-23):** added "9-Series Longsword Cannon", changed its note,
  removed it again — `201`, `200`, `204`.

## Known gaps

- **The blueprint file import** (`/personal-blueprints/import/*`) and the bulk delete. Phase 4,
  with the other file flows.
- **`acquiredAt`.** The API accepts it on create and update; the app offers no field for it and
  deliberately never sends it, so a save cannot rewrite a value the member cannot see.
- **Sorting.** The list arrives in the server's default order; the web app offers no sort either.
- **The admin surface** (`/api/v1/admin/personal-inventory/**`) stays web-only, permanently, like
  the rest of the admin area.
