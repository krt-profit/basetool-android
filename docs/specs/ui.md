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

