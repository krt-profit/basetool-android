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

### REQ-APP-SYNC-001 — One stream per client, carrying the union of its screens

`GET /api/v1/live-sync/stream?topics=…`. The topic set is fixed for one stream's life. **The URL is
the subscription** — there is no subscribe frame, so there is no client-side subscription state that
can disagree with the server's after a reconnect.

**The set is the union of every screen currently observing, not one screen's rooms.**
`LiveSyncRepository` reference-counts demand per room, so a room enters the union with its first
observer and leaves with its last, and any change to that union closes the stream and opens
another — debounced, so three screens mounting together do not do it three times. A screen still
sees only its own rooms: the acceptance list it is handed is intersected with what it asked for, or
every screen would believe it is live because some other screen's room was accepted.

This is why SSE rather than a socket. A browser tab changes its rooms without navigating and needs a
subscribe protocol for it; here the union changes rarely enough that reopening the stream is
cheaper than holding server-side per-stream subscription state and a resubscribe path after every
network blip.

**The union is what the server's cap has to fit, and once it did not.** The endpoint's
`MAX_TOPICS_PER_STREAM` was sized at 8 against "what one screen needs", which is the wrong unit for
a client that multiplexes: screens left on the back stack keep their rooms, so a member moving
through the app accumulates them. In production one member crossed 8. Because the endpoint refuses
the whole request rather than the surplus, live sync went dead on **every** screen at once, silently,
and stayed dead behind the reconnect backoff. The server cap is now 16 (`REQ-FE-019` in the main
repo, matching the web relay's per-session cap); the client's own guard against re-asking is
`REQ-APP-SYNC-005`.

Topics are built by [`LiveSyncTopic`](../../core/data/src/main/kotlin/de/greluc/krt/profit/basetool/android/core/data/LiveSyncTopic.kt)'s
factories and never assembled at a call site. The wire string is the room key on **both** ends, so a
topic differing from the server's canonical form by so much as the case of its id opens a second,
empty room — and nothing anywhere reports that.

**Acceptance**

- [x] The requested rooms appear in the query, encoded (`LiveSyncRepositoryTest`).
- [x] An empty room set never opens a connection (`LiveSyncRepositoryTest`).
- [x] Two screens observing at once produce one stream asking for the union
  (`LiveSyncRepositoryTest`).
- [x] A room leaves the union when its last observer does, not its first (`LiveSyncRepositoryTest`).

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
strand the screen for the cases that recover on their own — a stale token above all, which is
precisely the refusal a retry fixes once the interceptor beneath has renewed it.

**It does give up on a status that classifies itself.** `403` and `400` both say something about
*this request* that an identical retry cannot change: the first that the caller may enter none of
the rooms, the second that the request is not one the server will accept at all. After two the
client stops and emits an empty acceptance list, which is the screens' cue to fall back to polling.
The scope is the union, not the app — a changed union opens a fresh stream with a fresh counter, so
a member who navigates away from whatever made the request unacceptable gets live sync back.

`400` was added after it happened: the union crossed the server's topic cap (`REQ-APP-SYNC-001`),
the `400` fell into the "dropped socket, try again" branch, and the app re-asked on the backoff for
hours while every screen stayed silent. Both halves were wrong and both are fixed — but a client
that hammers a request the server has already called malformed is wrong independently of what made
it malformed.

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
- [x] A `403` stops the attempt loop; so does a `400` (`LiveSyncRepositoryTest`).
- [x] A `401` does **not** — it keeps retrying (`LiveSyncRepositoryTest`).

**Code:** `core/data/…/LiveSyncRepository.kt`, `LiveSyncTopic.kt`, `app/…/ui/LiveSync.kt`

## Known gaps

- **The Materialbörse, the Raffinerie and Beförderung are not wired yet** — their rooms exist in the
  registry and their screens land later in phase 4; each wires its own as it ships.
- **Editor presence stays web-only**, deliberately and permanently for this app.
- **The stream reconnects whenever the union changes.** Accepted: one GET per set of screens
  mounting or unmounting, debounced so a burst counts once. A subscribe protocol over a long-lived
  stream would save it and cost server-held per-stream state plus a resubscribe path after every
  network blip.
