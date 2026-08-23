# Live sync — the app as a peer, not a spectator

> **Area:** `REQ-APP-SYNC-*` · **Server side:** main repo `REQ-FE-019`, ADR-0143 (bridging
> ADR-0094's tool-wide relay) · **Phase:** 4

## Context & goal

The web app has had live peer sync since ADR-0094: a member's change reaches every other browser
looking at the same thing, without anybody reloading. The app was outside it, and the cost ran both
ways — with the second direction being the one that actually broke something.

- **The app did not see the web.** An officer edited an Einsatz in a browser; the app showed
  yesterday until the member pulled to refresh.
- **The web did not see the app.** A member booked stock out in the app and every open Lager tab in
  the organisation kept showing stock that was no longer there. Phase 3 shipped seven write slices,
  so this was live in production: the app was not merely missing a feature, it was **breaking** live
  sync for everyone else on surfaces where it worked.

---

### REQ-APP-SYNC-001 — One stream per screen, its rooms named in the URL

`GET /api/v1/live-sync/stream?topics=…`. The topic set is fixed for the stream's life; navigating
to another screen closes the stream and opens a new one. **The URL is the subscription** — there is
no subscribe frame, so there is no client-side subscription state that can disagree with the
server's after a reconnect.

This is why SSE rather than a socket. A browser tab holds several rooms at once and changes them
without navigating; a phone shows one screen, so "resubscribe" and "navigate" are the same event,
and one extra HTTP GET rides a transition the member is already waiting through.

Topics are built by [`LiveSyncTopic`](../../core/data/src/main/kotlin/de/greluc/krt/profit/basetool/android/core/data/LiveSyncTopic.kt)'s
factories and never assembled at a call site. The wire string is the room key on **both** ends, so a
topic differing from the server's canonical form by so much as the case of its id opens a second,
empty room — and nothing anywhere reports that.

**Acceptance**

- [x] The requested rooms appear in the query, encoded (`LiveSyncRepositoryTest`).
- [x] An empty room set never opens a connection (`LiveSyncRepositoryTest`).

---

### REQ-APP-SYNC-002 — The accepted list is read, not assumed

The stream's first event names the rooms the server actually **opened**. A room the caller may not
join is dropped from the set rather than failing the stream, so a screen that asks for three rooms
and gets two is normal.

**The app must read that list**, because a live room that is quiet and a room that was refused are
byte-identical on the wire — both are silence — and only the second means the screen has to keep
refreshing on its own. The Auftrags-queue is the concrete case: a requester who only ever sees their
own Aufträge is refused that room exactly as they are refused the queue page.

**The acceptance event is never treated as a change.** The server closes every stream after thirty
minutes by design, so a screen that refreshed on connect would pay a full read every half hour for
nothing.

**Acceptance**

- [x] Only accepted rooms are reported (`LiveSyncRepositoryTest`).
- [x] The acceptance event does not trigger a refresh (`LiveSyncWiringTest`).

---

### REQ-APP-SYNC-003 — Coalesced, jittered, and re-read in place

A room is re-read at most once per window: **400 ms** for one resource, **1500 ms** for a tool-wide
room, both **full-jittered**. These are ADR-0094's numbers unchanged, and the reason is that the
binding cost is not the relay — a frame is sixty bytes — but the **re-fetch herd** it triggers. A
global room can hold every member of the organisation at once, and the jitter matters as much as the
window: without it one frame broadcast to two hundred viewers produces two hundred reads at the same
instant.

Frames inside a window are folded into one event carrying the **union** of their sections, and each
room's window is its own — one busy room must not delay another's change.

**A peer's change refreshes the screen in place.** No spinner over the content, no collapse, no
emptied list while the answer is in flight. The member did not ask for anything; watching their
screen blank itself because somebody on the other side of the organisation booked something out
would be worse than not syncing at all. The Lager keeps its open groups and stacks — the same
re-read path a member's own booking uses — and the Einsatz keeps its tab and its typing.

**Only the section that moved is read.** An Einsatz's roster and its money are separate requests;
refreshing both because one moved would double what a peer's check-in costs every viewer. The
Finanzen tab is loaded on a `finance` frame **only if the member has already opened it**, because
that tab is lazy on purpose.

**Acceptance**

- [x] Frames inside one window arrive as a single event carrying the union (`LiveSyncRepositoryTest`).
- [x] Two rooms do not share a window (`LiveSyncRepositoryTest`).
- [x] A screen is handed the sections and nothing else (`LiveSyncWiringTest`).

---

### REQ-APP-SYNC-004 — The app announces its own writes, and only its own

After a successful mutation the app posts `{topic, sections}` to `/api/v1/live-sync/changed`, which
is what makes an app write refresh open browsers. The frame carries **no data** — an opaque room and
section keys — and every receiver re-fetches through its own authorized read, which is why an
ordinary member may emit one at all.

**A change that arrived through a room is applied and never re-announced.** Two clients that
re-announced what they received would bounce one booking off each other indefinitely. The publish
therefore sits on the member's own write path, never in the live-sync handler.

**A failure here is silent.** The endpoint answers `202`; the mutation it follows has already
committed. A screen reporting an error would be telling the member their save went wrong because
somebody else's refresh did. A `429` is dropped rather than retried — the buckets exist to bound the
re-fetch herd, and retrying defeats the bound it just hit.

Wired today: the Lager's bookings (`inventory`/`stock`), an Einsatz's own participation
(`mission:{id}`/`crew`) and its Finanzen (`finance`), an Auftrag's assignee and status
(`order:{id}`), an Operation's payout confirmation (`operation:{id}`), and a bank account's settings
— which announce into `orgunit-bank`, not the account's own room, because the settings region is
what the **overview** renders and a peer on the list is who needs to know.

**Acceptance**

- [x] The announcement carries exactly the sections given (`LiveSyncWiringTest`).
- [x] Nothing is sent when nothing changed (`LiveSyncRepositoryTest`, `LiveSyncWiringTest`).
- [x] A refusal is answered, not raised (`LiveSyncRepositoryTest`).

---

### REQ-APP-SYNC-005 — The stream reconnects, and live sync is never a dependency

The server closes every stream after thirty minutes by design, and a phone loses its connection far
more often than that. The client reopens with a full-jittered backoff (1 s → 30 s ceiling) and keeps
going until the screen is closed.

**It does not give up on a failure it cannot classify.** The stream reader completes the flow the
same way for a clean close, a dropped socket and a `401`, so treating any of them as terminal would
strand the screen for the two cases that recover on their own. A genuinely refused stream costs one
attempt per backoff step, and the backoff is what bounds that.

**A screen built without the bridge still works.** `observeLiveSync` and `publishLiveSync` accept a
`null` source, so a preview or a test double needs no live sync, and a stream that never connects
degrades the app to exactly what it was in phase 3 — pull-to-refresh — rather than breaking it.

**No presence.** App members are not editor dots and do not see them. The server's bridge emits
`changed` and nothing else and offers no way to ask for presence; that is precisely why the Einsatz
room may be joined at all (ADR-0094 fails that class closed because a *web* subscribe emits a
snapshot of pseudonymous ids and callsigns).

**Acceptance**

- [x] A closed stream is reopened and keeps delivering (`LiveSyncRepositoryTest`).
- [x] The heartbeat is not mistaken for a change (`LiveSyncRepositoryTest`).
- [x] A frame for a room this build does not know is dropped, not synthesised (`LiveSyncRepositoryTest`).
- [x] A `null` bridge is a working screen (`LiveSyncWiringTest`).

**Code:** `core/data/…/LiveSyncRepository.kt`, `LiveSyncTopic.kt`, `app/…/ui/LiveSync.kt`

## Known gaps

- **The Materialbörse, the Raffinerie and Beförderung are not wired yet** — their rooms exist in the
  registry and their screens land later in phase 4; each wires its own as it ships.
- **Editor presence stays web-only**, deliberately and permanently for this app.
- **The stream reconnects on every navigation.** Accepted: one GET per screen change. A subscribe
  protocol over a long-lived stream would save it and cost server-held per-stream state plus a
  resubscribe path after every network blip.
