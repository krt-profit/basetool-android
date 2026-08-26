# Benachrichtigungen — inbox, badge and push

> **Doc type:** Living spec · **Area:** `REQ-APP-NOTIF-*` · **Design:** `docs/design/android/07 Benachrichtigungen.dc.html`
> **Server contract:** main repo `REQ-API-009`, `REQ-NOTIF-010` (the stream), `REQ-NOTIF-019` (newest 50)
> **Related:** [`api-contract.md`](api-contract.md)

The inbox, the bell badge and the push channel behind both. **Fully interactive** since 2026-08-24:
marking read, deleting, „Alle als gelesen markieren", „Gelesene löschen" and the two swipe gestures.

It was read-only until then, and the reason recorded here was that those were "mutations and belong
to Phase 3". Phase 3 shipped without them and nobody lifted the deferral, so a sentence that
described a plan ended up describing nothing — the gap it justified had outlived the plan. The four
endpoints existed the whole time; what was missing was their place on the frozen contract and the
API vhost's allow-list.

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

The backend raises notifications for five entity types. **`JOB_ORDER` opens the order** since the
Aufträge slice gave it a screen (`REQ-APP-ORDERS-007`). The other four lead nowhere: a
`BANK_BOOKING_REQUEST` is about the approvals surface this build does not have — sending a member to
the account instead would answer a question they did not ask — the Materialbörse arrives in phase 4,
and the registration queue is admin work that stays on the web permanently.

`notificationDestination` returns `null` for those, and the row is drawn unclickable. A control that
reacts to nothing is worse than one that does not offer itself — the member repeats the tap and
concludes the app is broken rather than that the screen does not exist yet.

**Acceptance**

- [x] The mapping is written as one `when` over the entity types, so the next area's slice adds a
  line there rather than discovering the mapping is missing from a bug report.
- [x] Every mapping is asserted, the negatives included (`NotificationDestinationsTest`): a missing
  id, a blank id and a type this build has never seen all lead nowhere.

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

---

### REQ-APP-NOTIF-010 — The shade, for what the running app itself received

Design chapter 14 mocks up notifications in the system shade. Without a push channel (plan Q2) that
cannot mean what it means in most apps, and the honest scope is worth stating rather than implying:
**this reaches a member whose app is running** — one who switched screens or locked the device — and
nobody else. When the app is closed there is no FCM and nothing arrives. That is the whole of what
is available, and it is real: the inbox's own SSE stream is what triggers it.

It was unbuildable until the ETag fault (main repo #1653) was found, because until then the stream
delivered no bytes at all and there was nothing to post about.

**Two channels**, per chapter 14: `krt_operations` (Einsätze and system messages, high importance)
and `krt_general`. Created idempotently on every post — Android keeps a channel's user-chosen
importance once it exists, so re-creating changes nothing a member configured.

**Nothing sensitive reaches a locked screen, by construction rather than by convention.** The
channels carry `VISIBILITY_PRIVATE` and the builder attaches the public replacement chapter 14
dictates — „Neue Benachrichtigung" and nothing else — **unconditionally**. A builder that added it
only for notifications it judged sensitive would be one judgement call away from putting a member's
amounts on a locked screen, and nothing in the app would flag it. The headline is resolved in the
object graph, so no caller can thread free text into the shade.

**Two gates before posting**: the runtime permission on API 33+ *and* the member's own switch for
the app. Checking only the first posts into a void while believing somebody was told. The permission
is referenced by name, because the constant is API 33 and this app starts at 30.

**And the app has to ASK for it.** It checked the permission and never requested it, so on Android
13+ — where it is denied until asked — the whole shade half could not work for anybody: the channels
were created, `canPost` answered `false`, and the notifier silently posted nothing, correctly and
uselessly. Nothing failed anywhere, which is why only a device found it.

`RequestNotificationPermissionOnce` asks **behind the approval and terms gates**, not at launch: a
system dialog in front of somebody who may not have an account yet is a question they cannot answer
usefully. Once, and never again by us — Android remembers a denial, and without a push channel the
permission buys a notification only while the app is running, which is not worth pressing anybody
about. The answer is not stored: `canPost` reads the live state before every post, so a grant works
immediately and a change in the system settings is not shadowed by a stale copy.

**One notification id, reused.** The app cannot clear entries it posted before a restart, so a
growing stack would outlive its own truth; one entry saying the inbox has something new is what it
can honestly maintain.

**Acceptance**

- [x] The channels exist with the importances chapter 14 names, and are private on the lock screen
  (`KrtNotificationChannels`).
- [x] Every posted notification carries the fixed public version (`SystemNotifier`).
- [x] Nothing is posted without both gates (`KrtNotificationChannels.canPost`).
- [x] **Walked on a device** (2026-08-24). The permission dialog appears once, behind the gates. A
  notification published onto the backend's own fan-out channel reached the shade, and the posted
  record carries every rule chapter 14 states: `channel=krt_general`, `color=0xffe77e23`,
  `category=social`, `vis=PRIVATE`, `flags=AUTO_CANCEL`, a `contentIntent` that starts the app —
  and two titles, „Neues im Basetool" in the shade against „Neue Benachrichtigung" in the
  `publicVersion` the lock screen shows.
- [x] The lock-screen rule is verified by that pair rather than by reading the code: the public
  version is the only thing standing between a member's lock screen and whatever the shade says.

**Code:** `app/…/notifications/KrtNotificationChannels.kt`, `SystemNotifier.kt`,
`NotificationPermission.kt`

### REQ-APP-NOTIF-011 — Every inbox action reaches the member before the network

Marking read, deleting and both bulk actions apply to the visible state first and call afterwards.
A failure restores exactly what was there, field by field — a failed mark-read puts back the row's
**previous** read flag rather than setting "unread", so a race cannot manufacture an unread row out
of one that was already read.

**A delete is the one that waits.** The server has no un-delete, so an undo offered after the call
would be a button that cannot do what it says. The row leaves the list at once, the call is
scheduled five seconds out, and „Rückgängig" cancels it. The delete therefore lands five seconds
late, which nothing depends on, and the take-back is real, which the member does depend on. A
second delete commits the first rather than holding two; leaving the screen commits a pending one,
because a member who deletes and immediately leaves must not find the row back on return.

„Gelesene löschen" has no undo window, deliberately: it names exactly what it removes, touches
nothing the member has not already seen, and holding an unbounded number of rows to offer a
take-back would be a different feature.

Both bulk actions take the new badge value from the server's own `unreadCount` rather than assuming
zero — another device may have produced an unread row while the call was in flight.

**Code:** `app/…/notifications/NotificationsViewModel.kt`,
`core/data/…/NotificationRepository.kt`

### REQ-APP-NOTIF-012 — The swipe is the fast path, never the only path

Swipe right marks read (green reveal), swipe left deletes (red reveal). Reveal at 88 dp, commit at
50 % of the row width or on a fling, spring back over `KrtTheme.motionMs` — which is `0` under
reduced motion, so the row snaps instead of gliding.

Every row also carries the two actions as 48 dp icon buttons. They are **not** a fallback to be
dropped once the gesture works: a gesture is invisible to a screen reader and hard for anyone with
a motor impairment, and the design's own handoff says the buttons remain for assistive technology.

**Code:** `core/designsystem/…/component/KrtRows.kt` (`KrtSwipeableRow`),
`app/…/notifications/NotificationsScreen.kt`

### REQ-APP-NOTIF-013 — The count sits in the bar, and time is told in four forms

Two details of artboard 1 that the first build read past.

**The unread count is a chip in the top bar, not a line in the list.** It rode above the first row
as plain orange text, which scrolls away — and a count that disappears when the member scrolls is
worth less than no count at all, because they now have to scroll back to answer „wie viele noch?".
The bar is the one surface that stays. It also puts the count next to the title it qualifies, which
is where the eye goes when a screen is opened from a badge.

**Timestamps use the ladder chapter 07 writes out**, and it has four rungs:

| distance | form |
| --- | --- |
| under an hour | `vor 4 Min.` |
| earlier today | `vor 2 Std.` |
| the previous calendar day | `gestern, 21:14` |
| older | `15.08., 09:30` |

The platform supplies the first two and nothing else: `getRelativeDateTimeString` never says
„gestern" in German — it answers with `25.8.2026, 22:19` — and the plain span calls an evening two
days back `Vor 39 Std.`, which is a number a reader has to convert into a day. The upper rungs stay
with the platform, which knows the abbreviations and plural rules of every locale; the lower two are
composed, from a **translatable** date pattern so a locale can reorder the fields.

The boundary between the rungs is the **calendar day**, not a count of elapsed hours. That is what
makes 21:14 read as „gestern" at two in the morning instead of as „vor 5 Std." — the reader thinks
in days, and at 02:00 „vor 5 Std." and „vor 5 Std." mean two different evenings.

**Anything in the future stays relative on every rung.** A countdown reads „morgen" or „übermorgen";
an absolute date is a correct answer to a question nobody asked about something that has not
happened yet. Where German has a word for the distance, the platform's word wins over a literal
count — „übermorgen" over „in 2 Tagen".

One ladder serves the inbox, the Kartellbank and the dashboard. Three private copies of the
formatter is how „gestern" ends up looking different on two screens of the same app.

**The clock is printed in the zone the caller passes.** `DateUtils.formatDateTime` ignores any zone
and uses the system default, so the formatter took a zone for the day boundary and printed the time
in a different one. On a device the two are the same and nothing shows; CI, whose runner defaults to
UTC, rendered „gestern, 21:14" two hours early. `DateFormat.getTimeFormat` keeps what `DateUtils` was
here for — the member's own 12/24-hour setting — and accepts a zone. The test now pins a **foreign**
default zone so the mismatch fails locally instead of remotely.

**Acceptance**

- [x] All four rungs pinned against the real `DateUtils` and real resources, plus the two future
  forms (`RelativeTimeTest`).
- [x] The list body carries no count — the assertion is inverted, so putting one back fails
  (`NotificationsScreenTest`).
- [x] Verified on a device: „← BENACHRICHTIGUNGEN [7 NEU]" in the bar, „vor 5 Std." in the rows,
  „morgen · TS 21:44" on the dashboard.
- [x] All four rungs device-verified. The inbox could only show the top one — every fixture
  notification is minutes old — but the Auftrags-Queue reaches further back: „vor 6 Std.",
  „gestern, 21:44" and, after ageing one row in the throwaway stack's database, „15.08., 18:17".
  The future rung shows on the dashboard as „morgen · TS 21:44".

---

### REQ-APP-NOTIF-014 — Tapping a notification must not kill the app

The inbox's whole point is the tap that reaches it, and that tap crashed the process.

A notification's `PendingIntent` carries `FLAG_ACTIVITY_NEW_TASK`, because it may be the thing that
starts the app. Navigation answers that flag by rebuilding the task through `TaskStackBuilder` and
finishing the current activity — which is how the chapter's „Kaltstart: Back-Stack = Ziel +
Übersicht" is synthesised, and is correct. The replacement activity then opened a **second**
DataStore on `krt_settings`, DataStore threw, and the process died before the inbox was drawn.

From the member's side: they tap a notification about something that needs them, and the app
disappears to the home screen. Nothing in the app reports it.

The fix is ownership, recorded as [ADR-0014](../adr/0014-every-datastore-is-owned-by-the-application.md):
every store is a property of `BasetoolApplication`. The rule is enforced by a test rather than a
comment, because the identical mistake had already been made once for the token store, fixed
correctly, documented next to the fix — and repeated in a different file.

**Acceptance**

- [x] `ProcessStoreOwnershipTest` fails the build when any source outside the application opens a
  store; verified to fail by reintroducing the defect, not only to pass.
- [x] Verified on a device against the test stack: with the app running,
  `basetool://notifications` lands on the inbox and back returns to Übersicht. Before the fix the
  same command left the launcher on screen with `IllegalStateException: There are multiple
  DataStores active for the same file` in the log.

---

## Known gaps, stated rather than omitted

- **No system notification shade.** Design ch. 14, and the plan's Q2 decision rules out a push
  channel entirely — the app has no Firebase and will not get one.
- **The unread preview on the dashboard** is part of the Dashboard slice, which reads this same view
  model.
- **Only `JOB_ORDER` rows open anything.** The Aufträge slice gave that entity type a screen; the
  other four still lead nowhere and their rows stay unclickable (`REQ-APP-ORDERS-007`).
- **A member with five browser tabs open can evict the app's stream**, because the server caps
  concurrent streams at five per user and drops the oldest. The poll covers it, which is one of the
  reasons the poll is unconditional.

## Contract-set dependency (main repo)

`GET /api/v1/notifications`, `/unread-count` and `/stream` are in the `REQ-API-009` contract set and
the vhost allow-list. The stream's response carries `X-Accel-Buffering: no` so an nginx in front of
it cannot hold a trickling body in a buffer — without it the events arrive late, in bursts, or not
at all, and the failure reads as a broken push rather than as a proxy setting.

---

### REQ-APP-NOTIF-009 — The placeholder scanner replaces a regular expression that only works on the JVM

`fillTemplate` walks the template character by character. It must not use a regular expression.

`Regex("\\{([A-Za-z0-9_]+)}")` compiles on the JVM and **throws on Android**, whose ICU engine
rejects the unescaped closing brace. Every unit test runs on the JVM through Robolectric, so the
suite stayed green while the app crashed on launch for any member who had a notification — caught
only on a device. Escaping the brace would fix that one instance; scanning removes the class,
because there is no second regex dialect left to disagree with.

A brace that encloses no valid name is a literal and is copied through, so wording may contain one.

**Acceptance**

- [x] `Fertig {}` and `50 % von {12}` survive as text (`NotificationTextTest`).
- [x] An unclosed brace is text, not a swallowed sentence (`NotificationTextTest`).
- [x] Two adjacent placeholders are both filled (`NotificationTextTest`).
- [x] **Observed on a device (2026-08-22):** the inbox rendered where it previously crashed the app
  at launch.
