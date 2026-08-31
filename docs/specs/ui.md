# UI — cross-screen behaviour

Requirements that hold for more than one screen. Per-screen rules live in that screen's spec; the
binding visual reference is the design handoff at [`docs/design/android/`](../design/android/README.md).

---

### REQ-APP-UI-001 — An empty screen can still be pulled to refresh

Every body inside a `PullToRefreshBox` is scrollable, including the empty and the "filtered to
nothing" states. The design system provides `KrtRefreshableFill` for exactly that, and the six list
screens use it.

`PullToRefreshBox` learns about the gesture through nested scroll. A child that does not scroll
never forwards one, so the pull did nothing at all on precisely the screens where a member wants to
try again — an empty Auftrag queue, a Lager that has just been filled elsewhere, a Bank account
that was granted a minute ago. It reads as a frozen screen, not as an empty one. A scroll container
whose content fits consumes no drag and passes the whole of it upwards, which is what the refresh
box is waiting for.

The content inside it must not `fillMaxSize`: a scrolling column is measured without an upper
bound.

**Acceptance**

- [x] An empty queue fires `onRefresh` on a downward swipe (`OrdersScreenTest`). The test was
  checked against the unfixed code — with the scroll removed from `KrtRefreshableFill` it fails, so
  it pins the fix rather than the framework.
- [x] The six list screens — Einsätze, Operationen, Aufträge, Lager, Bank, Benachrichtigungen —
  wrap their empty state in `KrtRefreshableFill`.
- [x] **Observed on a device (2026-08-22):** the Bank's "Keine Konten" state was pulled and picked
  up an account granted seconds earlier.

---

### REQ-APP-UI-002 — A bottom sheet keeps its actions out of the gesture bar

`KrtBottomSheet` is padded at the bottom by the system's own inset, so the sheet's action row — the
last thing in every sheet, and the one control the member came for — sits above the navigation
gesture area rather than under it.

Measured on a device (2026-08-23): the Lager's booking sheet drew to the very bottom edge, its
`BUCHEN` button spanning y 2320–2424 on a 2424 px screen with a 63 px bottom inset. A tap on the
button's own label at y 2383 went to the system and never reached the button; the same tap 45 px
higher booked. Nothing was broken in the app's own logic, which is why no test caught it and why
the fix belongs to the design system rather than to one screen.

**The inset is read at the app root and handed down** (`LocalKrtBottomBarInset`, provided by
`KrtTheme`). Inside the sheet every inset API — `navigationBarsPadding()`, the raw
`WindowInsets.navigationBars`, `consumeWindowInsets(WindowInsets(0))`, the sheet's own
`contentWindowInsets` — resolves to zero, because the sheet's window reports no navigation bar.
Each of those was tried on the device before the composition local was introduced.

**It pads the sheet, not the sheet's content.** Padding the content only makes the content taller:
the sheet is bottom-anchored and its body scrolls, so the action row is pushed further past the
fold instead of being lifted clear.

**Acceptance**

- [x] **Observed on a device (2026-08-23):** with the padding in place the booking sheet's action
  row moved from y 2362–2404 to y 2331–2373 and a tap on its centre reached the button.
- [x] Every sheet in the app inherits it — the org switcher, the booking form, the ship editor and
  the personal-inventory editor all go through `KrtBottomSheet`.

---

### REQ-APP-UI-003 — A busy server counts down; a refused one does not

Design chapter 14 gives a busy server its own full screen: the seconds left inside the orange ring,
a backoff of 3 → 6 → 12 → 30, and `429` using the server's `Retry-After`. Three parts, and the value
is in what each of them refuses to do.

**The ladder** ([`RetryBackoff`](../../core/common/src/main/kotlin/de/greluc/krt/profit/basetool/android/core/common/RetryBackoff.kt))
holds at thirty seconds rather than growing, because a screen the member is still looking at has to
keep trying at a rate they can perceive as trying. A `Retry-After` of zero or less is **ignored** —
"retry immediately" from a server that just rate-limited you is not an instruction worth following —
and an unreasonably long one is capped back onto the ladder, because a live countdown running for an
hour is a frozen app in the member's eyes. Only the delta-seconds form of the header is parsed: the
HTTP-date form would mean trusting the device clock against the server's, and a skew turns three
seconds into hours or into none.

**The ring** (`KrtRetryCountdown`) is handed the number rather than ticking itself. The waiting
belongs to whoever owns the retry, and a composable counting on its own would keep going through a
configuration change while the real timer did something else. A negative value renders as zero: an
overrun timer is our defect, and `-2` hands it to the member.

**The adoption rule, which only a screen can honour.** The countdown replaces the screen **only when
the first load failed and there is nothing on it**. A screen with content keeps it and shows its
banner — replacing loaded data would take away what the member was reading in order to tell them
something the banner says without costing them their place.

**A screen adopts it with three lines**, since the conditions themselves moved into
[`FirstLoadRetry`](../../app/src/main/kotlin/de/greluc/krt/profit/basetool/android/ui/FirstLoadRetry.kt)
(2026-08-24). The Bank held the only copy, and the next nine screens would each have re-derived
those conditions from the one before it.

1. a `retryIn: Int?` on the state and a `FirstLoadRetry` wired to write it;
2. `retry.onFailure(error, hasContent)` on the failure branch and `retry.onSuccess()` on the
   success one — the second matters as much as the first: without it a screen that recovered would
   still be waiting thirty seconds a week later;
3. an `onRetryNow` callback on the composable, kept **separate from `onRefresh`**. A pull-to-refresh
   does not carry the ladder reset, and collapsing the two would lose it silently.

**The ladder is deliberately endless** against a server that stays busy — the member is looking at
the screen and the countdown keeps telling them how long. That has one consequence for tests worth
writing down, because it cost two debugging rounds: `runTest` drains the scheduler at teardown, so a
test that starts a countdown and does not cancel the ViewModel's scope **hangs instead of failing**,
and `advanceUntilIdle()` never returns. Use `runCurrent()`, and cancel the scope at the end.

**Acceptance**

- [x] The ladder, the `Retry-After` precedence and all three refusals (`RetryBackoffTest`, 6 tests).
- [x] The ring shows the number, clamps a negative one and reports the press
  (`KrtRetryCountdownTest`, 4 tests).
- [x] The Bank shows the countdown for a busy server and the ordinary empty state otherwise
  (`BankViewModel`, `BankScreen`).
- [x] The shared ladder starts on `503`, climbs, and is reset by a manual retry; a `403` gets no
  countdown at all (`RefineryViewModelTest`). This is the first test the ladder has ever had — the
  hang above is why.
- [x] **Adopted by every screen that loads from the server** (2026-08-24): Einsätze and one
  Einsatz, Operationen and one Operation, Aufträge and one Auftrag, Lager, Hangar,
  Benachrichtigungen, Mein Inventar, Blueprints, Bank, Materialbörse, Raffinerie list and detail.
  That is the whole list — a screen added later without it is the defect this line exists to make
  visible.
- [x] **Walked on a device** (2026-08-24), against a real rate-limited server rather than a fake
  one: the ring showed the server's own `Retry-After` — 26, then 19 four seconds later — beside
  „Der Server ist ausgelastet. Automatischer Neuversuch in 26 s.", with the manual retry offered.
  Producing the 429 needed the test stack's per-IP budget squeezed to a handful of requests; a
  server nobody can rate-limit is a rule nobody can check.

  That walk found the reason the rule had never actually held: the backend answered a 429 with
  `X-Rate-Limit-Retry-After-Seconds` and **no `Retry-After`**, so „429 uses the server's
  `Retry-After`" was unreachable however correct the client was. Fixed in the main repo; the app
  needed no change.

---

### REQ-APP-UI-004 — The forced-update gate stands outside every other gate

Design chapter 14's non-dismissible „Update erforderlich" screen. The server names a floor
(`GET /api/v1/app/version-policy`, main repo REQ-API-010) and a build below it stops running.

**Outermost, ahead of the lock and the session.** The endpoint is anonymous for exactly this
reason: when the breaking change is in the auth flow itself, the old build cannot sign in, so a wall
placed behind the session gate would never appear for the one case it exists for — the member would
see an authentication error blaming their credentials instead.

**It fails open in three separate ways**, because a wall is the most destructive state this app has:
it takes the whole tool away.

- A **failed read** runs the app. There is no screen for "we could not check whether you may run",
  and inventing one would be the same wall with a different sentence — shown to a member whose only
  problem is a train tunnel.
- A **zero floor** allows everything. That is what an unconfigured server answers, and any other
  reading of it would refuse every installed build the first time the code shipped.
- The policy is read **once per process**, not on a loop. A wall appearing mid-session over work in
  progress is worse than one that waits for the next start, and the floor does not move often
  enough to justify polling.

**Nothing is wiped.** The chapter is explicit that cached data survives, so the gate composes over
the app rather than signing anybody out. Back **exits** — there is nothing behind the screen, and a
back press that did nothing would read as a frozen app.

**Recorded deviation:** the chapter's CTA points at a store listing. Distribution is GitHub Releases
plus Obtainium (plan Q1), so the button opens the release page the server names.

**Acceptance**

- [x] Above the floor runs; below it walls off and carries the release URL (`UpdateGateTest`).
- [x] A zero floor and a failed read both run the app.
- [x] The policy is read once however often the gate is composed.
- [x] A newer build being available is not treated as a refusal — the two numbers stay apart, or
  every release would be a forced one.
- [x] **Walked on a device** (2026-08-24): with the floor set above the installed build the wall
  appeared with its call to action, back left the app, and clearing the floor let it run again.
  Setting the floor at all needed a compose passthrough that did not exist — REQ-API-010 promised
  an env var and a restart, and the variable reached nothing.

**Code:** `UpdateGate`, `UpdateGateViewModel`, `AppVersionRepository`

---

### REQ-APP-UI-006 — A link that goes nowhere says so, and back behaves as chapter 03 draws it

Chapter 03 is mostly rules rather than pixels, and rules are the part of a spec that rots without
anyone noticing. Checked one by one against the running app:

| rule | state |
| --- | --- |
| Phone: bottom bar, five destinations | holds |
| Tablet: rail, seven + „Mehr" | holds — Hangar, Raffinerie and Börse move up, as drawn |
| Each destination keeps its own back stack | holds |
| Back on a destination root returns to Übersicht; back on Übersicht leaves the app | holds |
| No hamburger anywhere; arrow-left only on pushed screens | holds (`REQ-APP-UI-005`) |
| Transitions 200 ms, no parallax | holds — `KRT_MOTION_MS`, fade |
| Push → target screen directly; cold start synthesises target + Übersicht | holds, **after** the crash in `REQ-APP-NOTIF-014` |
| Unknown route → 404 in-fiction | **was broken**, see below |
| Predictive back on every screen | **was partly broken**, see below |
| Re-tap the active destination pops to its root *and scrolls to top* | holds — see below |

**An unknown address used to land on the dashboard.** `basetool://voelligunbekannt` opened the app
on Übersicht, silently — indistinguishable from a link that worked. Worse, `destinationOf`'s KDoc
already claimed the caller "renders the in-fiction Signal Lost screen rather than silently falling
back to the dashboard", which is the one comment state a reader cannot defend against: it described
the intended behaviour of code that did the opposite.

It is reachable in practice — a notification from a newer server, a hand-typed address, a web link
into an area this build predates — so it gets chapter 14's 404: „Signal Lost", one plain German
line, CTA „Zurück zur Basis".

**The graph decides what it can match, not a second copy of the route table.** The first attempt
registered a catch-all `basetool://{route}` deep link on the 404 destination, reasoning that a
literal host would outrank a wildcard. It does not: Navigation ranks a match by how many arguments
it fills, the wildcard fills one and every literal route fills none — so **every** deep link in the
app landed on „Signal Lost". The code read correctly and only the device disagreed. Asking
`navController.graph.hasDeepLink(uri)` cannot drift from the graph, because it *is* the graph.

**The CTA returns to the Übersicht already on the stack**, not a second copy on top of it. Popping
only the 404 and pushing Home leaves two, and then back on Übersicht lands on Übersicht — breaking
the one thing chapter 03 says back on Übersicht must do.

**Predictive back was off below Android 16.** targetSdk 37 makes the platform enable it by default
on 16+, which is every emulator image in use here; minSdk is 30, and on a device running 13 through
15 the preview only runs with `enableOnBackInvokedCallback`. The flag is now set. The one case it
exists for is also the one a current emulator cannot show, so this rests on the platform contract
rather than on a device pass.

**Acceptance**

- [x] Device-verified: `basetool://notifications` and `basetool://bank` open their screens;
  `basetool://xyzunbekannt` shows „Signal Lost" + „Diese Adresse gibt es in dieser App nicht." +
  „ZURÜCK ZUR BASIS"; the CTA lands on Übersicht and back from there leaves the app.
- [x] Every destination has a distinct address, and the 404 is unreachable from the bar, the rail
  and „Mehr" (`DeepLinkRoutingTest`).
- [x] Re-tapping the active destination returns it to the top. Device-verified on „Aufträge",
  the one bar destination whose list is longer than the screen: 36 rows from „MATERIAL", scrolled
  to 32 rows from „OFFEN", re-tapped back to 36 from „MATERIAL". „Einsätze" and „Lager" cannot show
  it — their content fits — which is worth writing down, because a pass on those two would have
  meant nothing.
- [x] The counter is per route, so a re-tap on „Lager" cannot make „Aufträge" lose its place
  (`RootScrollSignalsTest`).

**Why the scroll needed a counter at all.** The pop that precedes it tears the destination down and
rebuilds it, and the rebuild restores the list from its saved state — so by the time the new screen
exists it has already been put back where the member left it, and nothing about the rebuild
distinguishes „I came back here" from „I asked for the top". The counter outlives the rebuild;
each list remembers, in its own saveable state, which value it last acted on. One **shared** counter
is the obvious version and is wrong: every screen watching it would jump to the top on its next
composition, so returning to a list would silently lose the member's place because they had once
re-tapped a different tab.

---

### REQ-APP-UI-007 — Five channels, the real wording, and a tap that opens the right screen

Chapter 14 names five notification channels — Einsätze & Check-In and Aufträge & Zuweisungen at
high importance, Materialbörse, Bank & Auszahlungen and System & Ankündigungen at default — so a
member can silence one kind and keep another. Chapter 03 adds that a tap opens the target screen,
and chapter 14's shade mockups show the notification's real wording rather than a fixed line.

None of the three was possible: the stream event was `data="new"`, a bare ping with no kind, no
entity and no parameters. Every push was the same message, four channels would have been switches
that silence nothing, and every tap opened the inbox.

**The backend now says what arrived** (main repo REQ-NOTIF-021, ADR-0146). The app reads the signal
and everything else follows from the two fields it carries:

- **The channel comes from `NotificationKind`**, which the inbox already uses to pick a row's glyph.
  One classification, two uses — a kind that files a row under a glyph files a push under a channel,
  and the five buckets it already had are the chapter's five channels.
- **The wording is assembled on the device** from the type template and its parameters
  (`REQ-APP-NOTIF-005`), so it is localised here and an unknown type degrades to the generic line
  rather than to a blank. Owner decision, 2026-08-26: the shade carries the real wording, as drawn.
- **The tap opens what the message is about**, through the same resolver the inbox row uses, so the
  list and the push cannot disagree — and an entity this build has no screen for still falls back to
  the inbox rather than to a route that does not exist.

**The channels are created at start, not at the first push.** A channel Android has never been told
about is absent from the app's notification settings, so the member's choice would only appear after
the first message of that kind had already arrived — which is the one moment the choice is too late.

**One notification id per channel**, so at most five entries and each replaced by the newest of its
own kind. An Auftrag must not overwrite the Einsatz that starts in ten minutes.

**The lock-screen rule matters more now than it did.** Every channel is `VISIBILITY_PRIVATE` and
every notification carries a public replacement reading „Neue Benachrichtigung" and nothing else.
That was belt-and-braces while the shade said nothing; it is now the only thing between the real
wording and a locked screen, and it stays enforced by construction rather than per call site.

**A push with no kind still lands somewhere.** The server degrades to the bare `new` on several
paths, and a notification type this build has never seen classifies as `SYSTEM`. Both belong on
„System & Ankündigungen", which is where "something happened and this build cannot say what" goes.

**Acceptance**

- [x] Each kind maps to its own channel — two kinds sharing one would mean a single switch silences
  both — and an unknown or absent type lands on the system channel
  (`NotificationChannelRoutingTest`).
- [x] The shade and the inbox resolve the same destination, and an entity with no screen invents no
  route (`NotificationChannelRoutingTest`).
- [x] Every unreadable payload degrades to a bare refresh rather than throwing
  (`NotificationSignalTest`).
- [x] Device-verified end to end against a locally built backend: creating an Auftrag through the
  API put an entry in the shade on **`channel=krt_orders`** at importance 4, coloured `#E77E23`,
  `vis=PRIVATE` with a `publicVersion`, titled „Neuer Auftrag #9 für IRI" — and tapping it opened
  Auftrag **#9**, not the inbox. The five channels appear in the system settings with the chapter's
  names and importances before any notification arrives; the two channels this app used to create
  are marked deleted.

---

### REQ-APP-UI-008 — A refused save raises the dialog chapter 14 draws

Chapter 14 draws the 409 as a modal: **„KONFLIKT FESTGESTELLT"**, a sentence, and two actions. The
app showed a `KrtFieldError` under the form at all eleven write surfaces — a line that is easy to
miss under a scrolled sheet, and a member who misses it believes they saved.

**Two of the chapter's sentences are not used, and neither is a translation question.**

- *„…zwischenzeitlich von Rhea geändert"* — the 409 carries no identity. Naming somebody would be
  inventing them.
- *„Deine Eingaben bleiben in der Zwischenablage erhalten"* — nothing is put on the clipboard, and
  making that sentence true would mean writing the member's input to the **system** clipboard, where
  every other app on the device can read it. The wording used says what actually happens.

**The primary action reloads; it does not retry.** The chapter labels it „NEU LADEN UND ERNEUT
VERSUCHEN". A button that re-sent the same values against the newer version would overwrite whatever
the other person changed without either of them seeing it — the exact outcome optimistic locking
exists to prevent. So it reloads, the member sees the current state, and they decide.

**The dialog lives at the host, not in the sheet.** Threading a reload down to each leaf that draws
an error meant four parameters through composables with no business knowing about refresh; every one
of those leaves reads the same screen-level state, so one `ConflictOn` per host covers them all and
„Neu laden" can close the form and make the screen re-read.

**The form keeps a short line, not the dialog's sentence.** The first wiring rendered both, so the
same two sentences sat under one another. The inline line is now „Nicht gespeichert — gleichzeitig
geändert." — enough to explain the state a member returns to after dismissing the dialog, without
repeating it.

**The Auftrag note is exempt.** Chapter 10 gives it a richer recovery — a refused note comes back as
`rejectedNote` with „Meine Fassung übernehmen" — and a generic „Neu laden" over it would offer to
throw away the very text that flow exists to preserve.

**Dismissal is tracked by identity.** `ApiError.OptimisticLock` is a data class, so two separate
refusals compare equal; a `remember(error)` key would treat the second as the first and a member who
dismissed the dialog once would never see it again that session. The decision is a named function so
the rule can be tested without going through two dialog windows.

**Acceptance**

- [x] Nine `ConflictOn` call sites cover all eleven refusal paths.
- [x] The rule is pinned directly, including the case that matters: a **new** refusal equal to a
  dismissed one is still raised (`ConflictModalTest`).
- [x] Device-verified end to end against the test stack: an item was opened in „Mein Inventar", the
  same record was changed through the API to move its version, and saving raised
  „KONFLIKT FESTGESTELLT" with „ABBRECHEN" and „NEU LADEN". „Neu laden" closed the editor and the
  list showed the other writer's value.

---

### REQ-APP-UI-005 — The top bar's two ends answer one question

The bar has a left end — a back arrow, or nothing — and a right end: the org chip and the bell, or
whatever the screen itself owns. Both answer the same question: **is this a destination the
navigation offers, or something pushed on top of one?**

They used to be asked differently. The arrow came from the destination; the chip and the bell came
from whether a screen happened to publish a title. So every pushed screen that publishes none — the
inbox, Einstellungen, the licences, the Fleetview import, and everything reached from „Mehr" — got a
back arrow **and** the chip **and** the bell. The Hangar's title was truncated to „OPEN-SOURCE-LI…"
shape to make room for a chip that does not belong there.

**One predicate decides both:** whether the destination is in the navigation set for the current
form factor — the bottom bar's five on a phone, the rail's eight on a tablet. A destination the
navigation offers has no back arrow and carries the chip and the bell; anything else has the arrow
and neither.

The form factor is part of it, not an afterthought: Hangar, Raffinerie and Materialbörse sit behind
„Mehr" on a phone and have their own rail entry on a tablet, so the same screen is pushed on one and
a root on the other.

**What a pushed screen puts on the right is its own.** The Hangar's overflow, the inbox's „3 NEU"
chip, a detail's actions — the bar's right end is free precisely because the chip and the bell have
left it (design ch. 07 artboard 1, ch. 08 artboard 4, ch. 13 artboard 1, ch. 15 artboard 1, which
all draw the same head).

**Acceptance**

- [x] The five phone destinations and the eight tablet ones are the only ones without a back arrow;
  the inbox is never among them (`TopBarOwnershipTest`).
- [x] No destination is both a navigation entry and a sub-destination (`TopBarOwnershipTest`).
- [x] Verified on a device: „← HANGAR ⋮" and „← OPEN-SOURCE-LIZENZEN" with the full title, no chip
  and no bell on either.

---

### REQ-APP-UI-009 — On a tablet the bar names the section and the pane names the row

A pushed detail publishes a `ScreenTopBar`, and the shell draws it as the app bar's title. That is
right on a phone, where the detail **is** the destination. In `KrtListDetail` it is a pane of a
section the rail is still highlighting, so a selected row left the bar reading „#1 · Offen · Prio 1"
above a rail that said AUFTRÄGE — the two disagreeing about where the member was.

**The publication is redirected, not suppressed.** Suppressing it alone is worse than the bug: the
pane then identifies nothing, and the list does not mark its selection either, so the detail belongs
to no visible row. `KrtListDetail` gives its detail slot a `LocalScreenTopBar` of its own and draws
what the content publishes — title, subtitle and actions — at the top of the pane, above a hairline.

**Only the detail slot is redirected.** The list keeps publishing to the shell, because the Lager's
selection bar is the list's and has to replace the whole bar (`REQ-APP-INV-*`, design ch. 09
artboard 5).

The pane head is deliberately not a `KrtTopBar`: that one applies status-bar insets and owns the org
chip and the bell, none of which belong to a pane sitting under the real bar. It keeps the subject
styling, so a pane head and a pushed screen's head read as the same thing.

**Acceptance**

- [x] The pane draws the head its content published (`ListDetailHeadTest`).
- [x] That head does not reach the shell's slot (same).
- [x] The list's own publication still does (same).
- [x] Verified on the tablet class (`KrtTablet`, 1280×800 dp): the bar reads „AUFTRÄGE" with the org
      chip and the bell while the pane head reads „#1 · Offen · Prio 1".

**Code:** `ui/ListDetail.kt` (`DetailPane`, `DetailPaneHead`)

### REQ-APP-UI-010 — The content gutter is a tablet's, and a phone keeps its full bleed

From a **medium** window up, a screen's scrolling content is inset by `KrtSpacing.md` on both sides.
Below that it is not: a phone keeps whatever the artboard draws.

Both readings are right for their own width, which is why one rule could not serve both. On a phone
a dense row list is drawn full-bleed inside the frame — chapter 09's Lager tree spans `49…411` of a
`48…412` screen, chapter 05's dashboard band `48…402` — and there is no width to give away. On a
tablet the same list puts a row's first character against the navigation rail and its last figure
against the screen edge, about two thousand device pixels apart, beside a Hangar whose cards *are*
inset. Eleven lists had no inset at **any** width: the Lager tree, the notification inbox, Mein
Inventar and its Blueprints, Operationen and one operation, an order's detail pane, a bank account,
and the tablet dashboard's two columns.

**The breakpoint is medium, not `isWideWindow`'s expanded.** This is a question about how much width
there is to spare, not about whether a list fits beside its detail; a 700 dp window has width to
spare long before it has room for two panes. `contentGutter()` in `ui/WindowWidth.kt` is the single
place the number lives, next to `isWideWindow()` for the same reason that one is there.

**Cards do not use it.** A card list — Hangar, Materialbörse, the Aufträge queue, Raffinerie,
Missionen — is inset at every width, because the artboards draw it that way on the phone too.
Passing those through `contentGutter()` would take their phone gutter away.

**The gutter belongs to the scroll container, not to the rows.** `contentPadding` on the list keeps
the scrollbar and the overscroll at the true edge and lets a row draw its own full-width background
inside the inset; `Modifier.padding` on the list clips both. On the tablet dashboard it sits on the
enclosing column instead, so the greeting and the announcement line up with the two columns of cards
under them.

**History, because the first attempt shipped the other rule.** The gutter was applied
unconditionally first, which put it on the phone as well and contradicted the drawn dense lists. The
owner ruled it tablet-only on 2026-08-28 (having ruled the gutter itself into existence the same
day, from the tablet). Chapter 01 states no gutter rule at all, which is why eleven screens each
decided for themselves — design round 9 §1 asks for one sentence there.

**Acceptance**

- [x] Verified on the tablet: Lager, Aufträge (list and detail pane), Übersicht, Benachrichtigungen,
      Operationen, Mein Inventar, Börse, Bank, Raffinerie — all inset, all aligned with the filter
      chips above them.
- [x] Verified on the phone and on minSdk: the Lager tree is full-bleed again, its accent spine at
      the frame's edge, as chapter 09 draws it.

**Code:** `ui/WindowWidth.kt` (`contentGutter`), `inventory/InventoryScreen.kt`,
`notifications/NotificationsScreen.kt`, `personalinventory/PersonalInventoryScreen.kt`,
`personalinventory/PersonalBlueprintsScreen.kt`, `missions/OperationsScreen.kt`,
`missions/OperationDetailScreen.kt`, `orders/OrdersScreen.kt`, `bank/BankScreen.kt`,
`dashboard/DashboardScreen.kt`

### REQ-APP-UI-011 — One end-of-list, drawn by the design system

A finished list ends with `KrtEndOfList` — the hairline rule, the uppercase label, the rule again.
Mein Inventar and its Blueprints tab drew a bare centred `Text` instead, so two screens out of
eleven ended differently from the rest for no reason anyone had decided.

**Acceptance**

- [x] Every `*_end_of_list` string is rendered through `KrtEndOfList`; no screen builds its own.

**Code:** `personalinventory/PersonalInventoryScreen.kt`,
`personalinventory/PersonalBlueprintsScreen.kt`


### REQ-APP-UI-012 — A refused field is named in the server's own words

The backend answers a validation failure with RFC 7807 `fieldErrors`: a localised sentence per
offending field, naming the value and the rule it broke — „numerischer Wert außerhalb des gültigen
Bereichs (<3 Stellen>.<2 Stellen> erwartet)". Sixteen write surfaces threw all of it away and showed
„Konnte nicht gespeichert werden." instead. Design ch. 02 §6 draws the field error naming the fault
(„Menge muss größer als 0 sein."), which is what the server already sends.

`ApiError.fieldMessage()` reads the body in one place — the `fieldErrors` array, else the legacy
`errors` map, else `detail` — and a write surface renders `error.fieldMessage() ?: <its own copy>`.

**Only a validation refusal speaks for itself.** Every `ApiError` carries a problem body, and on a
403, a 409 or a 500 the server's prose is about the *request*; what the member should do next is the
screen's to say. `fieldMessage()` answers `null` on those by construction, so the screen's sentence
cannot be displaced by accident.

**A screen may still overrule the server, and five do.** Where the refusal is *known* and the
screen's copy carries a remedy the server's cannot — „Die Summe aller Staffeln darf den Bedarf nicht
übersteigen" (Zusagen), „Schließe die Zuordnung und öffne sie neu" (Zuordnung), „Lädt den Auftrag
neu" (Item-Übergabe), the Herstellung's coverage gate, and the booking request's two-field hint —
the screen maps `ApiError.Validation` deliberately and the server's text is not shown. Overruling is
a decision, so it has to be *taken*: what is forbidden is the third case, an `else` branch that
swallows a named refusal because nobody considered it.

**This is a wording rule, not a layout one.** The 409 still raises chapter 14's dialog
(`REQ-APP-UI-008`); the inline line under the form is what this requirement governs.

**Acceptance**

- [x] Eleven surfaces defer to the server; five overrule it on purpose. No site lets
  `ApiError.Validation` reach `R.string.write_failed` unconsidered — swept from the sources, so a
  new write surface inherits the guard (`WriteErrorWordingTest`).
- [x] The precedence inside one body is pinned, including the two shapes the backend sends for the
  same refusal — the array wins, so a sentence is not printed twice (`FieldMessageTest`).
- [x] A non-validation refusal answers `null`, so a screen's 403 and 409 wording stands
  (`FieldMessageTest`).
- [x] Rendered end to end at both kinds of site: „Mein Inventar" shows the server's sentence and
  drops the generic one, and the booking request keeps its own over a server that named a field
  (`PersonalInventoryScreenTest`, `BankRequestScreenTest`).

**Code:** `ui/FieldMessage.kt`, `missions/MissionDetailScreen.kt`, `missions/OperationDetailScreen.kt`,
`missions/OperationFormScreen.kt`, `bank/BankScreen.kt`, `hangar/FleetImportScreen.kt`,
`hangar/ShipEditorSheet.kt`, `inventory/BookingSheet.kt`, `orders/OrderHandoverSheet.kt`,
`orders/OrdersScreen.kt`, `personalinventory/PersonalBlueprintsEditor.kt`,
`personalinventory/PersonalInventoryEditor.kt`
