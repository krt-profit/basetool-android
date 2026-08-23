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
