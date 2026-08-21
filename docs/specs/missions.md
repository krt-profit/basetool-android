# Einsätze — list

> **Doc type:** Living spec · **Area:** `REQ-APP-MIS-*` · **Design:** `docs/design/android/06 Missionen.dc.html`
> **Server contract:** main repo `REQ-API-009` (contract set), `REQ-SEC-035`/`036` (what the app's
> token carries) · **Related:** [`api-contract.md`](api-contract.md) (`REQ-APP-API-001`, `006`)

The Einsatz list is the app's first real member surface. Everything here is **read-only**; signing
up, checking in and finance entries are mutations and belong to Phase 3.

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

### REQ-APP-MIS-002 — "Vergangene aus" is a bound on the server's clock

Hiding past Einsätze is expressed as a `start` lower bound of **now as the server sees it**
(`ServerClock`), not as the device's `Instant.now()`.

A phone running a few minutes fast would otherwise hide an Einsatz that is about to start — the one
a member most needs to see, at exactly the moment they are looking for it. An explicit date range
wins over the toggle, because the member asked for it by name.

**Acceptance**

- [x] With the toggle off, a `start` bound is sent and is not earlier than the moment of the call.
- [x] With the toggle on, no `start` bound is sent at all.
- [x] An explicit `from` overrides the toggle (`MissionRepositoryTest`).

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

**One finding left open:** the search field reports `NAF="true"` to the accessibility tree and its
placeholder does not appear there at all, so a screen-reader user meets an unlabelled field. That is
a property of `KrtTextField` in the design system rather than of this screen, and every existing
caller has it; it needs fixing where the component lives, not here.

## Known gaps, stated rather than omitted

- **The segment "Einsätze / Operationen" is not built yet.** Its purpose is switching between two
  populated lists, and Operationen is still an empty destination — a control leading to "under
  construction" is worse than the control arriving with its second half. It ships with the
  Operationen slice. Both destinations already exist separately in the navigation (design ch. 03),
  so nothing is unreachable meanwhile.
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
- **Tapping a row is inert.** The detail screen is ch. 06 §2 and ships next; navigating to a
  placeholder that claimed to be the Einsatz would be worse than not navigating.

## Contract-set dependency (main repo)

`GET /api/v1/missions/search` must be added to the `REQ-API-009` contract set, its
`ExternalContractTest` entry, and the API vhost's default-deny allow-list before the app can reach
it in production — the allow-list grows one app phase at a time, and opening a family to the app and
freezing its shape are the same decision seen from two sides (main repo ADR-0135). Until then the
list works against the test stack only.
