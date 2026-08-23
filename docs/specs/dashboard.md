# Übersicht — the dashboard

> **Doc type:** Living spec · **Area:** `REQ-APP-DASH-*` · **Design:** `docs/design/android/05 Dashboard.dc.html`
> **Server contract:** main repo `REQ-API-009` (`/announcement`, `/missions/search`)
> **Related:** [`missions.md`](missions.md), [`notifications.md`](notifications.md)

The app's home screen: greeting, announcement, the Einsätze of the next seven days and a preview of
what is unread. **Read-only.**

---

### REQ-APP-DASH-001 — The design's order is a reading order

Greeting → announcement → Einsätze ≤ 7 days → unread preview. The web home page puts the same
material in a different order; the design reorders it for one-handed phones, and that is the reason
to keep it rather than a layout preference.

**Acceptance**

- [x] The four bands render in that order, in one scrolling column with pull-to-refresh.

**Code:** `DashboardScreen`

---

### REQ-APP-DASH-002 — The two reads fail independently

The announcement and the Einsatz band are unrelated reads behind unrelated permissions. One outage
must not blank the other: a member who cannot reach the announcement still needs to know what is
starting tonight, and a member whose Einsatz band fails still needs to read the notice.

**Acceptance**

- [x] A failed announcement leaves the band populated, and vice versa (`DashboardViewModelTest`).
- [x] A failed band says **"Die Einsätze konnten nicht geladen werden"**, never "nothing is
  scheduled" — the second told in place of the first would have a member skip an Einsatz.

**Code:** `DashboardViewModel.loadAnnouncement`, `loadMissions`

---

### REQ-APP-DASH-003 — No announcement is a result, and renders as nothing

`GET /api/v1/announcement` answers `204 No Content` when there is nothing to announce. The correct
rendering is **no band at all** — an "Information" heading over an empty space reads as a notice
that failed to load.

Read through the ordinary path the empty body would fail to parse and surface as a broken server
contract, so `ApiReader.getOptional` exists for it: an empty body is `null`, a refusal is still a
failure. A blank `content` on a `200` is treated as no announcement too; the backend suppresses
those already, but the field is nullable on the wire and a banner made of whitespace would be a
visible defect for the sake of trusting a shape.

**Acceptance**

- [x] `204` → success with `null` (`ApiReaderOptionalTest`, `AnnouncementRepositoryTest`).
- [x] Blank content → no band.
- [x] `403`/`500` → failure, which hides the band and is logged rather than shown.
- [x] The heading is absent when the band is (`DashboardScreenTest`).

**Code:** `ApiReader.getOptional`, `AnnouncementRepository`

---

### REQ-APP-DASH-004 — The seven-day window is bounded at both ends, on the server's clock

`from = now`, `until = now + 7d`, computed against `ServerClock`. Unbounded above, the band would
show whatever the server had and its own heading would be a lie. On the device's clock, a phone
running a few minutes fast would drop the Einsatz that is about to start — the one a member most
needs to see (the same reason `REQ-APP-MIS-002` gives).

The band asks for **five** rows. It is a summary; the Einsatz tab is one tap away and shows all of
them with their filters.

**Acceptance**

- [x] The query carries both bounds and they are exactly seven days apart (`DashboardViewModelTest`).
- [x] The page size stays in summary territory.
- [x] "Alle Einsätze" leads to the full list.

**Code:** `DashboardViewModel`

---

### REQ-APP-DASH-005 — The announcement collapses; it does not truncate

Two lines, tap to expand. An announcement is written to be read, and a notice cut off with no way to
see the rest is worse than none.

The expanded state is **local and unsaved**. Marking the announcement read is a mutation (Phase 3),
so the app must not appear to remember a decision it cannot store — a banner that stayed collapsed
across restarts would imply the server had been told.

**Acceptance**

- [x] Collapsed shows two lines with an ellipsis; tapping toggles.
- [x] The tap target carries a content description naming what it will do, since the control is the
  text itself and has no label of its own.

**Code:** `AnnouncementBand`

---

### REQ-APP-DASH-006 — The unread preview and the inbox cannot disagree

The preview reads the **same state** the inbox does, filtered to unread and cut to three. Not
`/notifications/recent`: a second endpoint would let the dashboard and the inbox describe one
notification differently, and the wording is assembled on the device from the same type and
parameters either way.

**Acceptance**

- [x] A notification is worded identically in both places (`DashboardScreenTest`).
- [x] "Nichts Ungelesenes." is stated rather than the band being left blank.
- [x] "Alle ansehen" leads to the inbox.

**Code:** `DashboardScreen`, `BasetoolNavHost`

---

### REQ-APP-DASH-007 — The greeting has one source per fact

The member's name comes from the ID token, the org unit from the switcher, the date from the device.
None of them is copied into the dashboard's state: two sources for one fact is how a screen ends up
disagreeing with the top bar beside it.

A member whose name is not known yet is greeted without one, rather than with a placeholder.

**Acceptance**

- [x] The greeting and the context line render from the shell's values.
- [x] The date is recomputed rather than remembered, so "today" stops being today when the day rolls
  over with the app open.

**Code:** `Greeting`

---

## Known gaps, stated rather than omitted

- **The quick-action row is absent.** Its four entries (Check-In, Einbuchen, Auftrag, Angebot) are
  all mutations, and three lead to screens Phase 2 does not build. Four buttons that do nothing
  would be worse than the row arriving with what it promises.
- **"Als gelesen markieren" on the announcement** is a mutation (Phase 3), so the band cannot be
  dismissed — only collapsed.
- **The offline variant of the design (ch. 05, second mock)** — cached content plus an "Offline"
  banner and disabled writes — waits for the read cache, which is its own spec area (`offline.md`).
  Today an offline dashboard shows its failure states.

## Contract-set dependency (main repo)

`GET /api/v1/announcement` is in the `REQ-API-009` contract set and the vhost allow-list, **including
its 204**: a client that reads the empty body as a failure shows an error where "no banner" is
correct, and no schema can express that. The Einsatz band reuses `GET /api/v1/missions/search`,
which was frozen with the Einsatz list.

---

### REQ-APP-DASH-008 — "Nichts Ungelesenes" is only said once the inbox has answered

The empty line under `UNGELESEN` is rendered only when the unread read has completed. While it is
still in flight the band shows nothing at all.

An empty list means two different things — "there is nothing" and "nothing has arrived yet" — and
the first is a claim about the member's inbox. Made out of not knowing yet, it is wrong for as long
as the request takes, and on a device that is long enough to read.

**Acceptance**

- [x] `unreadKnown = false` renders neither the line nor the link (`DashboardScreenTest`).
- [x] `unreadKnown = true` with no unread renders the line (`DashboardScreenTest`).
- [x] **Observed on a device (2026-08-22).**
