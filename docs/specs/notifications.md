# Benachrichtigungen — inbox, badge and push

> **Doc type:** Living spec · **Area:** `REQ-APP-NOTIF-*` · **Design:** `docs/design/android/07 Benachrichtigungen.dc.html`
> **Server contract:** main repo `REQ-API-009`, `REQ-NOTIF-010` (the stream), `REQ-NOTIF-019` (newest 50)
> **Related:** [`api-contract.md`](api-contract.md)

The inbox, the bell badge and the push channel behind both. **Read-only**: marking read, deleting
and the swipe gestures are mutations and belong to Phase 3, so this inbox shows state and offers no
action on it.

---

### REQ-APP-NOTIF-001 — The badge and the inbox are one state

One view model owns the unread count and the list. Two sources would be allowed to disagree, and a
member seeing "3 neu" over a list whose top three rows are already read has been told something
false by the app itself.

The count comes from `GET /notifications/unread-count`, not from counting the first page: the badge
must be right about the hundredth unread notification as well as the tenth, and the page is capped
at fifty.

**Acceptance**

- [x] The badge is read as soon as the app is in front, **without** loading the inbox — the bell is
  on every screen and the list is one screen (`NotificationsViewModelTest`).
- [x] Opening the inbox twice reads it once; pull-to-refresh is how a member asks for fresh rows.

**Code:** `NotificationsViewModel`, `BasetoolApp`

---

### REQ-APP-NOTIF-002 — Push and polling both run, and the poll is not the fallback

The design asks for both ("badge + list kept fresh by polling + SSE push") and the server's own
behaviour is why. The stream is best-effort: it is closed after thirty minutes, the oldest
connection is evicted when a member has six, and any proxy in between may drop it. A badge kept
fresh only by push would go stale in exactly those cases, silently.

The poll therefore runs every 60 s regardless, and the stream makes the common case immediate rather
than up-to-a-minute late.

**Both stop when the app leaves the foreground.** Holding a socket open for a screen nobody is
looking at spends the member's battery to learn something they cannot see.

**Acceptance**

- [x] The count is re-read on a timer while the app is in front (`NotificationsViewModelTest`).
- [x] Leaving the foreground stops the poll and closes the stream; returning restarts both.
- [x] A push signal re-reads the badge at once, and the **list** only once it has been opened —
  refreshing a list nobody is looking at would fetch fifty rows for nothing.
- [x] The stream ending is ordinary, not an error: the watcher reconnects with a 2 s → 60 s backoff,
  reset by any event that arrives. A stream that carried nothing at all — a `401` after a
  sign-out — backs off rather than retrying in a tight loop.

**Code:** `NotificationsViewModel.onForeground`, `watchStream`

---

### REQ-APP-NOTIF-003 — A failed count leaves the badge alone

Showing `0` on a failed read would tell the member their inbox is clear. That is a claim about their
notifications made out of an outage, and it is the kind of wrong answer nobody checks.

**Acceptance**

- [x] A failed `unread-count` keeps the previous number (`NotificationsViewModelTest`).
- [x] A failed inbox read is a failure state, never an empty list.

**Code:** `NotificationsViewModel.refreshUnread`

---

### REQ-APP-NOTIF-004 — The SSE reader is ours, and the framing rules are asserted

`okhttp-sse` was not adopted. The framing this needs is three rules — `event:`, `data:`, a blank
line ends the event — and a new third-party dependency is a decision under this repo's privacy gate
rather than a detail. The library would also not supply the part that actually matters, which is the
reconnect policy: only the caller knows whether the screen behind the stream is still on show.

The stream client is **derived** from the shared API client with the read timeout removed. A stream
is idle by design between heartbeats, so the shared timeout would tear it down as a matter of
course; deriving it keeps the connection pool, the interceptors, the bearer token and the mandatory
headers.

**Acceptance**

- [x] A blank line ends an event, a newline does not (`SseStreamTest`).
- [x] A comment line (`: keep-alive`) produces no event — treating it as data would deliver an empty
  event every few seconds, and a caller re-reading on each one would be polling while believing it
  was using push.
- [x] Multiple `data:` lines are joined; a nameless event takes the format's default.
- [x] A refused stream **completes** rather than throwing: a `401` means the token expired and the
  caller must be able to stop.
- [x] Only the `notification` event reaches the repository's caller; `connected`, `heartbeat` and
  `replaced` are the stream's own bookkeeping (`NotificationRepositoryTest`).

**Code:** `SseStream`, `NotificationRepository.changes`

---

### REQ-APP-NOTIF-005 — The sentence is assembled on the device, and degrades honestly

The server stores a `type` and a map of **named** values; the wording lives in the app's own
bundles, mirroring the web app's `notifications.type.*` keys one for one. That is what lets one
stored notification read German for one member and English for the next.

Named placeholders, not positional format arguments: the two bundles order the values differently,
and a positional format would silently swap them.

**An unfilled placeholder falls back to the generic wording.** Printing `Neuer Auftrag #{displayId}`
with the braces showing is a sentence that looks like a defect and hides which notification it was.
This is the client's own defence, because the contract freezes that `params` exists — not what is in
it.

**Acceptance**

- [x] A known type is filled from the server's map (`NotificationTextTest`).
- [x] A renamed or blank parameter falls back to the generic wording.
- [x] An unknown type resolves to the generic wording rather than to nothing — the server may add a
  rule at any time and the member must still be told that something happened.
- [x] All ten types the backend raises today have their own wording, asserted as a set so a new one
  cannot be added to the server without this list noticing.

**Code:** `NotificationText`, `notificationTypeRes`

---

### REQ-APP-NOTIF-006 — Read and unread differ in more than colour

An unread row carries the design's 3 dp orange inset bar, a bright bold sentence and an orange type
icon; a read one is muted throughout. Three channels rather than one, because colour is the channel
a member with a colour-vision deficiency does not have.

The type icon follows the design's rule — Einsatz/target, Auftrag/clipboard-list, Bank/bank,
Börse/swap, System/info — derived from the `type` prefix, since the server sends no such
classification. An unclassifiable type is `SYSTEM`, which is not a failure.

**Acceptance**

- [x] The unread marker, the weight and the icon tint all change with `read`.
- [x] The icon carries no content description: it repeats the source area the sentence already
  names, and announcing it would read every row twice.

**Code:** `NotificationRow`, `NotificationKind.from`

---

### REQ-APP-NOTIF-007 — A row that leads nowhere does not pretend otherwise

The backend raises notifications for five entity types (`JOB_ORDER`, `BANK_BOOKING_REQUEST`,
`MATERIAL_EXCHANGE_OFFER`, `MATERIAL_EXCHANGE_REQUEST`, `DISCORD_REGISTRATION`). None has a screen in
this build: Aufträge and Bank arrive later in phase 2, the Materialbörse in phase 4, and the
registration queue is admin work that stays on the web permanently.

`notificationDestination` therefore returns `null` today and the tap does nothing. A control that
reacts to nothing is worse than one that does not offer itself — the member repeats the tap and
concludes the app is broken rather than that the screen does not exist yet.

**Acceptance**

- [x] The mapping is written as one `when` over the entity types, so the next area's slice adds a
  line there rather than discovering the mapping is missing from a bug report.

**Code:** `NotificationDestinations`

---

### REQ-APP-NOTIF-008 — The list states what it is not showing

Fifty rows plus "Mehr laden", matching the web app's own cap (`REQ-NOTIF-019`) so the two clients
truncate at the same place. The footer names both numbers — "Zeige die neuesten 50 von 123" — which
is the main repo's ADR-0104 rule applied to this list.

**Acceptance**

- [x] The page size sent is 50 (`NotificationRepositoryTest`).
- [x] The footer states loaded-of-total while more exist, and "Ende der Liste" when they do not.
- [x] A row without an id is dropped — it cannot be opened — but the server's total is passed
  through untouched, because lowering it quietly would hide the fault.
- [x] A row without a `type` is **kept**: the screen has a sentence for it, and dropping it would
  hide a notification the server thought worth raising.

**Code:** `NotificationRepository.inbox`, `NotificationsList`

---

## Known gaps, stated rather than omitted

- **No mark-read, no delete, no swipe, no "Alle gelesen".** All are mutations (Phase 3). The design's
  inbox is fully interactive; this one deliberately is not.
- **No system notification shade.** Design ch. 14, and the plan's Q2 decision rules out a push
  channel entirely — the app has no Firebase and will not get one.
- **The unread preview on the dashboard** is part of the Dashboard slice, which reads this same view
  model.
- **A member with five browser tabs open can evict the app's stream**, because the server caps
  concurrent streams at five per user and drops the oldest. The poll covers it, which is one of the
  reasons the poll is unconditional.

## Contract-set dependency (main repo)

`GET /api/v1/notifications`, `/unread-count` and `/stream` are in the `REQ-API-009` contract set and
the vhost allow-list. The stream's response carries `X-Accel-Buffering: no` so an nginx in front of
it cannot hold a trickling body in a buffer — without it the events arrive late, in bursts, or not
at all, and the failure reads as a broken push rather than as a proxy setting.
