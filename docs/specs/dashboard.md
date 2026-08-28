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

### REQ-APP-DASH-010 — Six details artboard 1 draws that the first build read past

None of these is a missing feature; each is a place where the screen said something slightly
different from what the chapter draws, and the differences compound into a screen that does not
look like the design.

**The greeting is uppercase and orange.** It was sentence case and white, which turns the one line
addressed to the member personally into another row of body text. The token artifact annotates its
headline entries "UPPERCASE, orange" and the artboard measures 20 sp at weight 900 with 1 sp of
tracking; the scale carries no such entry, so `headlineSmall` (19/25/0.95, annotated "h3 —
UPPERCASE") is used rather than a hand-rolled style that would match the mockup by a dp and leave
the system by a rung.

**The date is numeric, and it names both years.** `FormatStyle.FULL` writes „Mittwoch, 26. August
2026"; the artboard writes „Sonntag, 17.08.2956" — the in-fiction Star Citizen year, which runs 930
ahead of ours. Owner decision, 2026-08-26: **both**, as „Mittwoch, 26.08.2026 (2956)". Writing error
copy in character is one thing; a start screen that misstates today's date is another, and a member
reading it beside a calendar, Discord and the web tool would otherwise find three different years.
The pattern is translatable so a locale can reorder the fields; the offset is a named constant with
the reason attached.

**The shortcuts are a 2×2 grid of icon-beside-label tiles.** They were four across, which leaves
about 90 dp per tile and is why their labels had been cut to „Einbuchen" and „Angebot" — a shortcut
whose label needs its icon to disambiguate it is not much of a shortcut. The artboard's tiles are
194 dp on a 412 dp frame with the glyph *beside* the label, which is what lets „Einbuchen (Lager)"
and „Börse: Angebot" fit: a row wraps a long label under itself, a centred column cannot.

**The mission card wears the row-level pill, not the page-level badge.** The design system says
which is which in as many words — the pill is "the row-level status indicator … inside a list the
status must not compete with the record's name", the badge "belongs at the top of a detail screen
where a single status describes the whole record" — and the dashboard is a list.

**The unread preview keeps its timestamp.** Without it a row says that something happened but not
whether it is still worth acting on, which is the one thing a member skimming the dashboard is
deciding.

**The announcement gained its unread marker and its mark-read action** (below).

**„Alle ansehen" belongs in the section header.** It hung under the last row as a free-floating
orange line, which reads as one more row of the list rather than a control belonging to the section.
The artboard puts it beside „UNGELESEN", and `KrtSectionTitle` already had the slot for exactly that
(„optional content pinned after the rule, e.g. a count or an action"). The Einsatz band gets the
same treatment: the artboard draws no see-all there at all, but the pattern is established one
section below and a link floating under a card was the worse of the two readings.

**The rail says „BÖRSE", not „MATERIALBÖRSE".** Both the navigation map (ch. 03) and the tablet
dashboard (ch. 05) label the rail entry with the short form while the „Mehr" list spells it out — a
rail column is 88 dp wide and the compound crowds it. Destinations can now carry a navigation label
distinct from their name; only this one needs it.

**Acceptance**

- [x] The greeting asserts uppercase, so a sentence-case one fails rather than passing unnoticed
  (`DashboardScreenTest`).
- [x] Device-verified against the test stack: greeting, date, 2×2 shortcuts with full labels, the
  quiet blue pill on the Einsatz card, and „vor 6 Std." under a preview row.

---

### REQ-APP-DASH-011 — The announcement says whether it has been read, and can be marked

Artboard 1 draws the notice with an „UNGELESEN" chip beside its „INFORMATION" label and an „ALS
GELESEN MARKIEREN" action under the text, and the handoff adds that the mark "POSTs like the web".
The app drew the text and nothing else.

**The read flag lives on the member, not on the notice.** `/users/me` carries
`lastReadAnnouncementId`, so "is this unread" is a comparison between two reads rather than a
field. It is asked in the announcement repository rather than through `IdentityRepository`, which
caches its answer for the process on the explicit grounds that a member's id cannot change while
the app runs — this value can, on the very next tap.

**Unread defaults to false, and the marker only appears once the flag lands.** The marker is an
invitation to act; showing one for the frames before the answer arrives and then taking it back
teaches a member that it means nothing. A failed read leaves it counted as read for the same
reason.

**Marking is optimistic and a refusal puts the marker back.** A band that says „gelesen" while the
server disagrees will say „UNGELESEN" again at the next start, and the member will have learnt that
the button does not stick.

**The action is its own tap target.** The card toggles the fold; a member who taps to read the rest
of a notice must not thereby declare they have read it.

**An unread notice opens expanded**, since the reason it is marked is that they have not taken it
in yet, and greeting them with an ellipsis asks them to work for it.

**Contract observation, not an app decision:** editing an announcement **keeps its id**, so a notice
whose text is rewritten stays read for everyone who had read the old wording. Verified against the
running stack. The app reports the server's model rather than inventing a second notion of
freshness on the device; if that is wrong it is wrong in the backend, for the web app too.

**Acceptance**

- [x] Unread appears only after the flag lands; an already-marked notice reads as read; a second
  tap sends nothing; a refusal restores the marker (`DashboardViewModelTest`).
- [x] Device-verified end to end against the test stack: the band showed „UNGELESEN" and „ALS
  GELESEN MARKIEREN", the tap cleared both, and `/users/me` afterwards carried the notice's id.

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

### REQ-APP-DASH-012 — The greeting is a block, and the quick actions name the action

Two things artboard 1 draws that the first build read past, found by putting the render beside a
device screenshot rather than checking that the elements existed.

**The greeting sits in a filled block with the accent rail down its left edge**, not as text on the
bare background. It is the chapter's first element and the only one that addresses the member by
name; rendered plain it read as a caption above the announcement. The rail treatment is the same one
an order's note carries, so it is now one component (`KrtRailCard`) rather than the two hand-built
copies it had become.

**The quick-action glyphs are the action, not the destination.** The artboard draws an enter arrow,
a download arrow, a plus and a swap — what the member is about to *do*. The app drew a target, a
crate and a clipboard, which name the section each tile leads to: a second navigation bar under the
first one, with the same crate on the tile and on the Lager's rail entry.

That settles an open question one chapter over: **⤓ means Einbuchen.** Artboard 09.1 puts it on the
Lager's FAB and it had been read here as Ausbuchen, with „+" put in its place; chapter 05's
„EINBUCHEN (LAGER)" tile draws the identical arrow. The FAB carries it again.

**Acceptance**

- [x] Verified on the phone class against the rendered artboard: the greeting block with its rail,
      and all four glyphs.

**Code:** `dashboard/DashboardScreen.kt` (`Greeting`), `dashboard/QuickAction.kt`,
`inventory/InventoryScreen.kt` (the FAB), `core/designsystem/component/KrtContainers.kt`
(`KrtRailCard`)

