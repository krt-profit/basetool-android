# Einsätze — list and detail

> **Doc type:** Living spec · **Area:** `REQ-APP-MIS-*` · **Design:** `docs/design/android/06 Missionen.dc.html`
> **Server contract:** main repo `REQ-API-009` (contract set), `REQ-SEC-035`/`036` (what the app's
> token carries) · **Related:** [`api-contract.md`](api-contract.md) (`REQ-APP-API-001`, `006`)

The Einsatz list and the Einsatz detail. Everything here is **read-only**; signing up, checking
in and finance entries are mutations and belong to Phase 3, which is why the detail deliberately
carries no call to action.

---

### REQ-APP-MIS-001 — The list is filtered on the server, never on the device

The list reads `GET /api/v1/missions/search`, not `GET /api/v1/missions`. The plain list takes only
paging, so every filter the design puts in the chip row — text, status, date range, "Vergangene aus"
— would have to be applied to a page the server had already truncated. The result would be a screen
that says "3 Einsätze" because the other 22 were on page 2.

The sort is named explicitly as `plannedStartTime,asc`. The backend answers an unlisted sort field
with `400`, so this is not a free-form string: getting it wrong does not reorder the list, it fails
to load it.

**The org scope is not sent and must not be.** Which units a member sees follows from their
memberships and the `X-Active-Org-Unit-Id` header the interceptor already puts on every request
(`REQ-APP-API-001`, `REQ-APP-API-006`). A client-side unit filter would be a second, weaker copy of
a rule that already exists server-side.

**Acceptance**

- [x] The search term is encoded exactly once — a term containing `&` or `=` reaches the server
  intact (`MissionRepositoryTest`). Built by `HttpUrl`, never by string concatenation; the failure
  mode of the latter is a search that silently matches nothing, which reads as "no Einsätze".
- [x] A blank term is left off the wire entirely rather than sent as an empty filter.
- [x] Each selected status is its own repeated `status` parameter.
- [x] The sort parameter is one the backend whitelists.
- [x] Nothing is cached: an Einsatz list is data whose staleness a member notices immediately, and
  pull-to-refresh exists because the answer is expected to change.

**Code:** `MissionRepository`, `ApiReader.get(path, query, deserializer)`

---

### REQ-APP-MIS-002 — "Vergangene aus" is a status filter, not a time bound

Hiding past Einsätze is expressed as `status=PLANNED&status=ACTIVE`. With the toggle on, no status
is sent and the server answers with everything the caller may see. A status the member ticked wins
over the toggle entirely.

**Amended 2026-08-22 (owner decision), after a device walk-through.** It was a `start` lower bound
of now on the server's clock — which also hides every **running** Einsatz, because a running one
gathered in the past by definition. That is the row a member most needs, the design's own
`seit 15:57` wording for it could then never appear, and the web app never behaved that way: its
`showPast` flips the same two statuses. The clock argument the old wording rested on (a phone
running fast hiding an Einsatz about to start) disappears with the bound.

A ticked status wins because subtracting the finished ones from an explicit "show me the finished
ones" answers with an empty list. An explicit date range is still sent as it is: the member asked
for it by name.

**Acceptance**

- [x] With the toggle off, `status=PLANNED&status=ACTIVE` is sent and no `start` bound is.
- [x] With the toggle on, neither a status nor a `start` bound is sent.
- [x] A ticked status is sent instead of the toggle's pair (`MissionRepositoryTest`).
- [x] An explicit `from` is sent unchanged (`MissionRepositoryTest`).
- [x] **Observed on a device (2026-08-22):** with the toggle in its default position, a running
  Einsatz whose gathering time had passed was absent from the list before the change and present
  after it.

---

### REQ-APP-MIS-003 — A malformed row costs itself, never the page

The list is defensive in one direction only: a single bad row must not cost the member the whole
screen, and a bad row must not be silently absorbed either.

- A row **without an id** is dropped, because it cannot be opened and offering it produces a tap
  that does nothing. **The server's `totalElements` is left alone** — lowering it to match would
  hide the fault instead of surfacing it (main repo ADR-0104, no silent caps).
- A row with an **unparseable timestamp** keeps its place and loses only its time label.
- A row with a **status this build has never heard of** renders with the raw server value and the
  neutral "planned" tone. An untranslated word beats a missing badge, which reads as "no status";
  and a client being older than its server is not the member's fault.

**Acceptance**

- [x] All three, each with its own case (`MissionRepositoryTest`).
- [x] An empty page is a **success**, not a failure — "nothing matches" and "the list is broken"
  are different screens (`MissionsViewModelTest`).

---

### REQ-APP-MIS-004 — Typing is debounced; a tapped filter is not

A search term arrives one keystroke at a time and each one would otherwise be a round trip, so the
field waits **300 ms** (fixed by the design spec) before the term reaches the server. A tapped chip
is one deliberate act and reloads immediately — making the member wait 300 ms for it would read as
the app being slow rather than as the app being careful.

Every reload starts at page 0 and **replaces** the rows. Appending would leave the previous filter's
Einsätze underneath the new filter's, which reads as the filter not having worked.

**The field is a controlled component, so the state must carry the typed value too.** `searchText`
is updated synchronously on every keystroke; `query.text` is the debounced term that reaches the
server. Binding the field to the debounced value instead feeds the previous value back on every
recomposition, so the character the member just typed disappears as they type it — measured on a
device: the field accepted **nothing at all**, while the view-model tests (which render no field)
and the screen test (which is handed a static state) were both green. `isNarrowed` reads the typed
value for the same reason, so the reset chip appears on the first keystroke rather than 300 ms
later.

Resetting clears the **typed value** as well as the query object. Clearing only the latter leaves
the old term in the field, and the next keystroke would restore a filter the member believes they
removed.

**Acceptance**

- [x] Five keystrokes cost one request; nothing reaches the server before the debounce elapses.
- [x] A status chip reloads at once, and setting the same filter twice does not re-fetch.
- [x] A filter change requests page 0 and replaces the rows.
- [x] After a reset the next keystroke starts from empty (`MissionsViewModelTest`).
- [x] A keystroke reaches `searchText` **synchronously**, before anything reaches the server, and
  a reset clears it — each pinned by a test that fails without its half of the fix.
- [x] Verified on a device against the test stack: typing narrows the list, the reset chip appears
  from the first character, a term matching nothing shows the filtered-empty copy, and the reset
  works from inside that empty state (2026-08-21).

---

### REQ-APP-MIS-005 — Paging appends, and a failed continuation keeps what is on screen

The next page is appended; the response's own `page` is trusted over the requested one. A page
request is ignored while one is in flight or when the server has no more, so a fast scroll cannot
queue several requests for the same page.

A **failed next page leaves the rows already on screen**. Replacing a working list with an error
because its continuation failed loses the member data they already had, for no gain — they can
simply try again.

Appending reads the current state rather than the snapshot the request started from: a refresh may
have replaced the rows meanwhile, and appending to the stale snapshot would resurrect the ones it
removed.

**Acceptance**

- [x] The next page appends and clears `hasMore` when the server says it is the last.
- [x] Load-more is ignored when there is no further page.
- [x] A failed next page keeps the rows and the `Ready` phase (`MissionsViewModelTest`).
- [x] The list states `n von m Einsätzen`, so a truncated view can never look complete.

---

### REQ-APP-MIS-006 — The day heading follows the device's zone

Einsätze are grouped by day, with "Heute" / "Morgen" / weekday + date, and undated ones in a group
of their own placed **last**.

**The device's zone decides the day, not UTC.** The wire is UTC; an Einsatz at 22:30 UTC is
tomorrow's for a member in Europe/Berlin, and grouping it by the wire's date files it under the
wrong heading — visibly wrong exactly once a day, which gets reported as "the app shows the wrong
date sometimes" and is nearly impossible to reproduce on demand.

A **running** Einsatz is filed under the day it actually started, not the day it was planned for: a
member looking for it looks under today.

Undated Einsätze are kept rather than dropped — dropping them would make the list disagree with the
total it states — but are placed last, because a heading with no date has no place on a timeline.

**Acceptance**

- [x] Today / tomorrow / any other day each get their own heading.
- [x] 22:30 UTC groups under tomorrow for a Berlin device.
- [x] A running Einsatz groups by its actual start; the meeting time stands in when there is no
  start time at all.
- [x] An undated Einsatz is kept and kept last (`MissionDaysTest`).

---

### REQ-APP-MIS-007 — The four list states each say something different

Loading, failed, empty-unfiltered and empty-filtered are four states and get four screens.

"Keine Einsätze — für diesen Zeitraum ist nichts geplant" and "Nichts gefunden — kein Einsatz passt
zu diesen Filtern" are different facts. Showing the first when the member's own filter is what hid
them says the squadron is idle when it is not; the filtered variant therefore also offers the reset
in place.

A failure shows the in-fiction error copy ("Signal Lost"), never a code or a stack trace. The
classified `ApiError` is logged by the view model so a report can be matched against it.

**Acceptance**

- [x] All four render, each asserted against the German bundle (`MissionsScreenTest`).
- [x] Tapping a row opens that Einsatz and no other.
- [x] A status chip reports the **whole resulting set**, not just the tapped one — the view model
  replaces the set, so a chip reporting only itself would silently clear every other selection.

---

---

### REQ-APP-MIS-008 — The detail is one read; the money is a second, later one

Six of the seven tabs come from a single `GET /api/v1/missions/{id}`, which already carries the
participants, units, steps, objectives and frequencies. Switching tab therefore costs nothing and
the seven tabs cannot disagree with each other about the same Einsatz.

**The Finanzen tab is fetched lazily, when it is first opened**, and it is two calls — the totals
summary and the entries. Three reasons it is not folded into the first read:

- It is **differently guarded**. `/missions/{id}` is anonymous-with-redaction; the Finanzen reads
  require `isAuthenticated() and isMemberOrAbove() and canSeeMission` (main repo REQ-SEC-037). A
  member may legitimately see the Einsatz and be refused its books.
- Fetching it up-front would therefore turn an ordinary lack of permission into an error on a
  screen that is otherwise perfectly fine.
- Most members opening an Einsatz never look at it, and it would cost two requests every time.

The two finance calls **succeed or fail together**. A total over an empty list, or a list under a
blank total, reads as data rather than as the partial answer it is.

**Acceptance**

- [x] The money is not fetched until its tab is opened, and then exactly once — switching away and
  back does not re-fetch (`MissionDetailViewModelTest`).
- [x] A refused Finanzen tab leaves the Einsatz `Ready`; the tab carries its own failure.
- [x] The tab can be retried without re-reading the Einsatz around it.
- [x] A refresh re-reads the money **only** when its tab was already opened — it must neither
  silently acquire a permission-dependent read the member never asked for, nor skip one they are
  looking at.
- [x] A refused summary or a refused entries page fails the whole tab (`MissionDetailRepositoryTest`).

---

### REQ-APP-MIS-009 — The redacted answer is a smaller Einsatz, not a broken one

The backend redacts the detail for anonymous and role-less callers (main repo ADR-0034): no
description, no owner, no managers, participants without their payout preference or comment. An
**internal** or **terminal** Einsatz is refused outright with 403.

Every one of those fields is therefore **legitimately absent**, and the app treats it as such. An
app that required any of them would show "Signal Lost" on an Einsatz the server served without
complaint.

The description is the one absence the screen **states**: "Die Beschreibung ist nur für Mitglieder
sichtbar." A blank section reads as an Einsatz nobody bothered to describe, which is a different
and wrong claim.

**Acceptance**

- [x] A fully redacted payload parses to a `Success` with `description`, `partyLeadName` and the
  collections empty rather than to a failure (`MissionDetailRepositoryTest`).
- [x] The screen says the description is members-only instead of rendering an empty section.
- [x] An id the server omits falls back to the one that was requested — a detail read is addressed
  by id, so a cosmetic server change must not produce a dead screen.

---

### REQ-APP-MIS-010 — Refused, gone and broken are three different sentences

The detail can fail in three ways a member can act on differently, so it says three different
things rather than one that covers all of them:

| Failure | Copy | Why |
|---|---|---|
| `403` | **Access Denied** — "Dieser Einsatz ist für dich nicht einsehbar." | an outsider's internal or terminal Einsatz; retrying can never help |
| `404` | **Signal Lost** — "Diesen Einsatz gibt es nicht mehr." | a stale link or a deleted Einsatz |
| anything else | **System Malfunction** | an outage, and the only one worth retrying |

The Finanzen tab draws the same distinction on its own: a `403` says the books are not visible and
offers **no** retry, because retrying a permission the member does not have is advice that cannot
possibly help.

**Acceptance**

- [x] All three render their own copy (`MissionDetailScreenTest`).
- [x] A refused Finanzen tab offers no retry; any other failure does.
- [x] A tab with nothing in it says so — a blank tab is indistinguishable from a rendering fault.

---

### REQ-APP-MIS-011 — What the detail deliberately does not interpret

Two values are passed through **verbatim** rather than mapped:

- An **objective kind** this build has never heard of is rendered as it came. A goal with no
  marking at all is worse than one marked with a word the member has not seen before.
- A **status** this build does not know renders its raw server value with the neutral "planned"
  tone, exactly as the list does (REQ-APP-MIS-003).

Amounts are **carried** as the strings the server rendered and **formatted** for display from
`BigDecimal` — grouped for the locale, stripped of the zeros a fixed-scale column pads with, and
signed from the entry's kind rather than from its digits (the server stores both incomes and
expenses as positive magnitudes). No `Double` is ever involved: parsing a decimal into one to print
it again is how a total gains a rounding error it did not have on the server.

Displaying the raw string was the first attempt and a device run rejected it — `86400.0000` is
faithful and unreadable, while the design's figures are `+86.400` / `−11.700` / `74.700`.

**Acceptance**

- [x] An unknown objective kind is displayed (`MissionDetailScreenTest`).
- [x] `1234567.89` survives the repository round trip byte for byte (`MissionDetailRepositoryTest`).
- [x] The rendered form is grouped, zero-stripped and signed, and a 17-digit value survives
  formatting — a `Double` would round it, which is what that test exists to catch
  (`MissionAmountsTest`).

---

### REQ-APP-MIS-012 — A required enum the client does not know costs the whole screen

**This is a known fragility, recorded rather than solved here.** `JobTypeDto.archetype` is a
non-nullable generated enum, and kotlinx's `coerceInputValues` rescues only nullable ones. A
constant added server-side therefore makes the **entire detail response** unparseable — every tab
gone, on an APK in the field that cannot be redeployed — while the list (which reaches no nested
enum) keeps working. The member would see a list whose every row fails to open.

It is not fixable in the app: openapi-generator's `enumUnknownDefaultCase` is a no-op for
kotlinx_serialization (measured — the generated enum was unchanged), and the app does not even read
the field. It is required purely to parse.

The mitigation lives in the main repo: **REQ-API-009 now freezes every required enum reachable from
a contract operation**, so adding a constant fails the *backend* build and forces the release order
— an app build that knows it ships first.

**Acceptance**

- [x] The fragility is pinned by a characterisation test that fails, with an instruction, the day
  the app can survive it (`MissionDetailRepositoryTest`).
- [x] The backend-side guard exists and was verified by adding a constant (main repo
  `theContractRequiredEnumsAreFrozen`).

---

## Device verification (2026-08-21)

Run against the isolated test stack with eight seeded Einsätze spanning today, tomorrow, later this
week, one running, one completed, one cancelled and one with no date. Everything below was read off
the accessibility tree, because the app sets `FLAG_SECURE` and screenshots come back black.

Confirmed: the day headings (`HEUTE` / `MORGEN` / `DIENSTAG, 25. AUGUST 2026`), the status chip row,
`VERGANGENE AUS` hiding past Einsätze by default and `VERGANGENE AN` revealing them, `seit 15:57` on
the running one, the status filter narrowing to a single row, the `IRI` org badge, `ENDE DER LISTE`,
the search, the filtered-empty copy and the reset — including from inside the empty state.

Also confirmed on this path: the account reconciled to **`KRT Member`**, not `Guest` — the
main repo's REQ-SEC-035 working end to end through the app.

**One defect found and fixed here:** the search field discarded every keystroke (see
REQ-APP-MIS-004). No unit test could have caught it; it needed a real field bound to real state.

**One finding fixed where it belonged:** the search field reported `NAF="true"` to the
accessibility tree and its placeholder did not appear there at all, so a screen-reader user met an
unlabelled box. Both were properties of `KrtTextField` — a `BasicTextField` supplies neither by
default — and every caller had them, so the fix went into the design system rather than this screen:
the placeholder moved into the field's own `decorationBox` (a sibling drawn behind a full-width
field is obscured, and obscured nodes are pruned), the field gained an accessible name from
`label ?: placeholder` that survives the member typing, and an error is now attached via the `error`
semantics instead of merely rendered beneath. Six tests in `KrtTextFieldAccessibilityTest`.

## Device verification — the detail (2026-08-22)

Run against the isolated test stack with one Einsatz filled out across all seven tabs, plus an
**internal** one and a terminal one to exercise the outsider rules. Read off the accessibility tree,
because the app sets `FLAG_SECURE`.

Confirmed as a **member**: the head (title, status, `IRI` badge, "3 angemeldet, davon 2
eingecheckt", the TS/JOIN/ENDE/ORT fact band), all seven tabs with content — including the seventh,
which is off-screen in the horizontally scrolling tab row and reachable by scrolling it — the
check-in marks, the HVU chip and crew count, the Ablauf's "ERLEDIGT", the objective kinds, the
tap-to-copy frequencies, and the Finanzen tab loading only once its tab was opened.

Confirmed as a **role-less outsider** (achieved by stripping the Keycloak client scope, which is
what the app's own REQ-SEC-035 provisioning otherwise prevents):

- the internal Einsatz **disappears from the list**;
- the detail still opens, and says *"Die Beschreibung ist nur für Mitglieder sichtbar."* rather
  than showing a blank section;
- the **Finanzen tab alone** refuses, with its own sentence and **no retry**, while the Einsatz
  around it stays fully rendered — which is the entire reason the two reads have separate load
  states.

**One defect found and fixed here:** the amounts rendered as `86400.0000`. Faithful to the wire —
the column is `numeric(_,4)` — and unreadable; the design's own figures are `+86.400` / `−11.700` /
`74.700`. Fixed by formatting from `BigDecimal` (grouping and zero-stripping are lossless) rather
than by relaxing the no-`Double` rule that kept the raw string in the first place. Eight tests in
`MissionAmountsTest`, one of them a 17-digit value that a `Double` would round.

## Known gaps, stated rather than omitted

- **The segment "Einsätze / Operationen" now exists** and navigates rather than toggling — see
  [`operations.md`](operations.md) `REQ-APP-OPS-001` for why, and for the two approved deviations
  the Operationen half carries.
- **The design mock shows a fifth status badge, "Briefing".** It has no counterpart anywhere in the
  backend or the web app: `mission.status.*` carries exactly four keys (`PLANNED`, `ACTIVE`,
  `COMPLETED`, `CANCELLED`) and `briefing` appears only as a section heading ("Auftrag"). It is
  therefore treated as mock copy. `KrtStatusTone.Briefing` exists in the design system and is
  deliberately unused. A briefing phase would need a backend change first, not a client-side guess.
- **The date-range picker is not built.** `MissionQuery` carries `from`/`until` and the repository
  sends them, but no chip opens a picker yet; only "Vergangene" is exposed. It ships with the
  bottom-sheet pickers of design ch. 02.
- **The "Einsatz erstellen" FAB is absent.** It is a mutation (Phase 3) and role-gated on
  `MISSION_MANAGER`.
- **Tapping a row opens the Einsatz** (ch. 06 §2), on the parameterised route
  `mission/{missionId}` — the only one in the graph, so a notification about one Einsatz can deep
  link straight to it.
- **The detail carries no call to action.** "Anmelden", "Check-In" and "Finanz-Eintrag hinzufügen"
  are the design's bottom-anchored CTA and its sheets; all three are mutations and belong to
  Phase 3.
- **The Ablauf checklist is read-only.** Ticking a step is a `PATCH`.

## Contract-set dependency (main repo)

`GET /api/v1/missions/search` must be added to the `REQ-API-009` contract set, its
`ExternalContractTest` entry, and the API vhost's default-deny allow-list before the app can reach
it in production — the allow-list grows one app phase at a time, and opening a family to the app and
freezing its shape are the same decision seen from two sides (main repo ADR-0135). Until then the
list works against the test stack only.

---

### REQ-APP-MIS-013 — A member signs themselves up, and finds themselves in the roster

`POST /api/v1/missions/{id}/join` and the **slim** `DELETE …/participants/{pid}/slim`. The band
sits under the head rather than inside the Teilnehmer tab: it is about the caller and not about the
list, and a member opening an Einsatz to sign up should not have to find the right tab first.

**Which row is the caller's is decided by `user.id`, never by a name.** The server sends
`displayName` when a member set one and `username` otherwise, so a name match would work for some
members and silently fail for exactly those who personalised their profile. A failed `/users/me`
therefore disables every write rather than guessing — the Einsatz still reads.

The caller's own row is drawn in the brand colour, so they can find themselves in a roster of
thirty.

**The withdrawal re-reads the Einsatz; the other three do not.** It answers `204`, and the counts
above the roster move with it — inventing them here would put two numbers on screen that disagree.

**Acceptance**

- [x] Sign-up and withdrawal address the caller's own row (`MissionDetailViewModelTest`).
- [x] Nothing is writable while the caller is unknown (`MissionDetailViewModelTest`).
- [x] The withdrawal re-reads the roster (`MissionDetailViewModelTest`).
- [x] **Observed on a device (2026-08-23):** „Anmelden" flipped to „Abmelden", the header moved to
  „1 angemeldet", and back again.

**Code:** `MissionRepository.join` / `.leave`, `MissionDetailViewModel.onToggleSignUp`

---

### REQ-APP-MIS-014 — Checking in is offered only once the Einsatz has started

The server refuses it before then — *"Cannot check in before mission actual start time is set"* —
and `actualStartTime` is the same fact that refusal is about. The control is therefore absent until
the Einsatz is running, and a muted line says why rather than leaving a member wondering where the
action went.

**The slim endpoints answer with the row alone, and the screen patches that row in place.** The
count above the roster is recomputed from the patched list, not taken from the answer: re-reading
the whole Einsatz for one timestamp would make a check-in cost what opening the screen costs, and
a stale header would contradict the list right under it.

**Acceptance**

- [x] No check-in control before the Einsatz has started, and the line says why
  (`MissionDetailViewModelTest`, `MissionDetailScreenTest`).
- [x] A check-in patches the row and the count, with no second read
  (`MissionDetailViewModelTest`).
- [x] **Observed on a device (2026-08-23):** the control was absent while the Einsatz was
  `PLANNED`, appeared once it went `ACTIVE`, and moved the header to „davon 1 eingecheckt".

**Code:** `MissionDetailState.checkInPossible`, `MissionRepository.setCheckedIn`

---

### REQ-APP-MIS-015 — The payout preference is a toggle, and it says what the other option is

`PUT …/payout-preference/slim` with `PAYOUT` / `DONATE`. The button names the **other** state —
„Spenden" while the share is being paid out, „Auszahlen" while it is being donated — because a
two-state control labelled with its current state is one a member has to press to find out what it
does.

An absent preference is read as "paid out" for the label but is **not** claimed as a fact anywhere:
the server stating nothing is different from the server stating `PAYOUT`.

**Acceptance**

- [x] The toggle sends the opposite of what the row holds (`MissionDetailViewModelTest`).
- [x] The label follows the row (`MissionDetailScreenTest`).
- [x] **Observed on a device (2026-08-23).**

**Code:** `MissionRepository.setDonating`, `MissionDetailViewModel.onTogglePayoutPreference`

---

### REQ-APP-MIS-016 — Offline disables the Einsatz's writes; it never queues them

Same rule and the same shared band as [`REQ-APP-INV-010`](inventory.md),
[`REQ-APP-ORDERS-012`](orders.md) and [`REQ-APP-PI-003`](personal-inventory.md).

**Acceptance**

- [x] Nothing is sent while offline, and the state follows the device (`MissionDetailViewModelTest`).
- [x] The band renders and the sign-up is disabled (`MissionDetailScreenTest`).

**Code:** `ui/OfflineWrites.kt`, `MissionDetailViewModel`

---

### REQ-APP-MIS-017 — A booking needs a sign-up to book against, and only its owner may change it

`POST /api/v1/finance-entries` names a participant, and the only one the app may name is the
caller's own sign-up. A member who has not signed up is told that — *"Buchen geht, sobald du für
den Einsatz angemeldet bist."* — rather than shown a button that answers `403`.

**Edit and delete are offered on the caller's own bookings alone.** The server refuses either by
anyone but the owner or an admin, and the app does not offer what it knows will be refused.
Ownership is decided by the entry's `participant.id` against the caller's own sign-up id, never by
a name.

The direction is a **segment**, not a signed amount: a minus typed into a number field is a
character a member can lose, and the sign is what decides whether the Einsatz earned or spent. The
editor opens on the whole-number form of the amount — the wire carries `2500.0000` and the field
takes digits alone, so the raw form would change shape under the member as soon as they typed
(found on a device).

**Every booking re-reads the tab.** The three totals above the list move with each one, and
patching a row would leave a sum that disagrees with the rows under it.

**Acceptance**

- [x] Booking is offered only with a sign-up, and the reason is stated
  (`MissionDetailViewModelTest`, `MissionDetailScreenTest`).
- [x] Only the caller's own booking offers a change and a delete (`MissionDetailScreenTest`).
- [x] The editor opens on a number the field can hold (`MissionDetailViewModelTest`).
- [x] An amount of nothing is not submittable (`MissionDetailViewModelTest`).
- [x] Each write re-reads the tab (`MissionDetailViewModelTest`).
- [x] **Observed on a device (2026-08-23):** an expense booked (`201`), changed (`200`) and removed
  (`204`), the band moving to `−2.500`, `−3.000` and back to `0`.

**Code:** `MissionRepository.addFinanceEntry` / `.updateFinanceEntry` / `.deleteFinanceEntry`,
`MissionDetailViewModel.onSaveEntry`

---

### REQ-APP-MIS-018 — Negative figures use one minus, everywhere

`formatAmount` prints the typographic minus (`−`, U+2212) rather than the hyphen the platform
formatter reaches for, which is what `formatSignedAmount` already used.

Found on a device in the Finanzen band: the expense sum read `−2.500` and the net one line below it
read `-2.500`. The same figure printed two ways, one line apart, reads as two different kinds of
number.

**Acceptance**

- [x] A negative amount formats identically through both helpers (`AmountsTest`).
- [x] **Observed on a device (2026-08-23)**, before and after.

**Code:** `common/Amounts.kt`
