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

**The band and the 0.45 fade are one shared composable** (`ui/OfflineWrites.kt`), used by every
write surface — Mein Inventar, Blueprints, Hangar and the Lager's bookings
([`REQ-APP-INV-010`](inventory.md)). Four private copies of the same rule is how one of them ends up
saying something different.

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

A product with `ownedByCurrentUser` is **not listed at all**, and the sheet carries a notice line
saying so — design ch. 17 artboard 5, which follows the web („Bereits vorhandene werden nicht
angeboten"; „die Notice-Zeile sagt das, damit ein fehlender Treffer nicht als Suchfehler gelesen
wird").

> **Amended 2026-08-30.** The first version listed an owned product greyed out and labelled „Hast
> du schon", on the reasoning that hiding it answers the member's real question with silence. The
> design spec's chapter 17 settled it the other way and gave the notice line as the answer to that
> objection. Recorded rather than quietly rewritten, so the earlier reasoning stays readable.

**Acceptance**

- [x] An owned product is not offered (`PersonalBlueprintsViewModelTest`), and the sheet says why
  (`PersonalBlueprintsScreenTest`).
- [x] A product row without a key never reaches the picker (`PersonalBlueprintRepositoryTest`).
- [x] The search is capped, debounced, and needs two characters, like the place picker
  (`PersonalBlueprintsViewModelTest`).
- [x] **Observed on a device (2026-08-23):** added "9-Series Longsword Cannon", changed its note,
  removed it again — `201`, `200`, `204`.

---

### REQ-APP-PI-013 — Several blueprints at once, and the result line that says what happened

Design ch. 17 artboard 5: the **same** search sheet with checkboxes, no second entry point and no
„bulk mode" — picking several is the normal case as soon as more than one hit fits. A second tap
takes a row back off. The CTA names the count („3 Blueprints übernehmen") and is validation-dimmed
without a selection — dimmed, not locked: nothing here is forbidden, it is unfinished.

**One picked keeps the single create.** `POST /personal-blueprints/batch` carries **only**
`productKeys` — no note, no `acquiredAt`. So one product goes through `POST /personal-blueprints`,
which carries the note, and several go through the batch; with several picked the note field is
drawn locked with that reason rather than removed, because a note silently dropped is worse than a
note that says it does not apply.

**The sheet stays open on a batch and reports what the server did** — „2 übernommen · 1 bereits
vorhanden", from `PersonalBlueprintBatchResult`. Closing on a partial result would hide the skipped
ones, which is the one thing that line exists to say. The list is only re-read when something was
actually added.

**Acceptance**

- [x] Several products go through the batch and never the single create
  (`PersonalBlueprintsViewModelTest`).
- [x] The sheet stays open and carries the two counts (`PersonalBlueprintsViewModelTest`).
- [x] A second tap takes a product back off (`PersonalBlueprintsViewModelTest`).
- [ ] Observed on a device.

**Code:** `PersonalBlueprintRepository.addAll`, `BlueprintBatchResult`,
`BlueprintEditor.Adding.chosen` / `.noteApplies` / `.offered`, `BlueprintAddSheet`

---

### REQ-APP-PI-014 — „Blueprint-Verfügbarkeit" is a screen of its own, with a role

Design ch. 17 artboard 6, reached from „Mehr". **Not** a third tab of „Mein Inventar": the data is
org-wide and the screen has its own role, and org-wide rows in a personal list would be the wrong
place twice over.

Two columns, as the web page has: the blueprint, and **„Verfügbar bei"**. The chapter's own
correction is explicit that there is **no buildability chip** here — the question this screen asks
is *who has it*, not *can it be built*; buildability lives on the member's own blueprint. Drawn as
cards rather than table rows, because the owner list wraps.

**The role gate is drawn, never hidden** (app ADR-0011). The „Mehr" row is always there; without
`canSeeBlueprintOverview` (`GET /me/capabilities` — officer and above, in the caller's oversight
scope) it is locked-tappable and the toast names the role. Before the first `/me` lands the flag
reads as `false`, which locks the row rather than opening a screen the server would refuse.

**Owners load per row**, and all three of the artboard's per-row states are real: „Besitzer werden
geladen …", „Keine Besitzer in deiner Orgeinheit.", „Besitzer konnten nicht geladen werden." The
overview page carries counts only, so a second call per row is unavoidable — and one row's failure
must not take the list with it. A row is asked once, when its card appears.

An owner outside the unit gets the muted chip „kein Einheitsmitglied" and the artboard's sentence,
quoted verbatim in **both** locales because it explains a server rule rather than describing the
UI: „Über die globale Blaupausen-Freigabe sichtbar, kein Mitglied der gewählten Einheit."

> [!warning] „Nicht erfasst" has no wire filter
> `GET /personal-blueprints/overview` takes a search term and paging — nothing else. The chip
> therefore narrows **the rows loaded so far**, and while the server has more pages the list says
> so in a line of its own rather than letting a short result read as a complete answer (ADR-0104).
> A server-side `ownerCount = 0` filter is the fix, and is on the gap list.

**Acceptance**

- [x] One row's failed owner read stays that row's (`BlueprintOverviewTest`).
- [x] A row is asked for its owners once (`BlueprintOverviewTest`).
- [x] The „Nicht erfasst" chip reports that it is partial while more pages exist
  (`BlueprintOverviewTest`).
- [ ] Observed on a device, with and without the role.

**Code:** `PersonalBlueprintRepository.overview` / `.owners`, `Identity.blueprintOverview`,
`BlueprintOverviewViewModel`, `BlueprintOverviewScreen`, `MoreScreen`

## Known gaps

- **The blueprint file import** (`/personal-blueprints/import/*`) and the „alle löschen" bulk
  delete (`DELETE /personal-blueprints`). Phase 4, with the other file flows. The **multi-add**
  that once sat here landed 2026-08-30 as `REQ-APP-PI-013`.
- **`acquiredAt`.** The API accepts it on create and update; the app offers no field for it and
  deliberately never sends it, so a save cannot rewrite a value the member cannot see.
- **Sorting.** The list arrives in the server's default order; the web app offers no sort either.
- **The admin surface** (`/api/v1/admin/personal-inventory/**`) stays web-only, permanently, like
  the rest of the admin area.
