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
