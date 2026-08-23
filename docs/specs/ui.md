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

**A screen adopts it with four pieces**, and the Bank accounts list is the worked example:

1. a `retryIn: Int?` on its state, set only while a countdown runs;
2. a `scheduleRetry(error, keepContent)` on the failure branch, which starts nothing unless the
   error is `ServiceUnavailable` or `RateLimited`, the screen is empty, and no timer is already
   running;
3. an `onRetry()` that cancels the timer and **resets the attempt count** — the member pressing the
   button is new information, and inheriting a thirty-second wait from an automatic attempt they did
   not make would punish them for having waited;
4. an `onRetryNow` callback on the composable, kept **separate from `onRefresh`**. A pull-to-refresh
   does not carry the reset, and collapsing the two would lose it silently.

**Acceptance**

- [x] The ladder, the `Retry-After` precedence and all three refusals (`RetryBackoffTest`, 6 tests).
- [x] The ring shows the number, clamps a negative one and reports the press
  (`KrtRetryCountdownTest`, 4 tests).
- [x] The Bank shows the countdown for a busy server and the ordinary empty state otherwise
  (`BankViewModel`, `BankScreen`).
- [ ] **The remaining screens' first-load paths** — outstanding (#67).
- [ ] **Walked on a device** — outstanding (#67).
