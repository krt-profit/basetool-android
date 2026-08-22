# Spec Registry

Durable requirements for the Android app live here as docs-as-code, one file per area, using
ids `REQ-APP-<AREA>-NNN`. Every behaviour change updates its requirement in the same PR; a
change with no matching spec change is incomplete (see `CLAUDE.md`).

The first area has been extracted; for the rest the **concept documents in [`docs/`](../) remain
the binding source** (marked `Doc type: living plan`):

| Area (planned) | Will absorb |
|---|---|
| [`auth.md`](auth.md) (`REQ-APP-AUTH-*`) — **exists** (`001`–`011`) | login flow, token storage, DPoP, session states, app-lock |
| [`api-contract.md`](api-contract.md) (`REQ-APP-API-*`) — **exists** (`001`–`005`) | consumed endpoints, headers, problem-code handling, pagination, version echo |
| [`settings.md`](settings.md) (`REQ-APP-SET-*`) — **exists** (`001`–`008`) | Einstellungen screen scope, per-app language, string-resource rule, legal links, the generated open-source notice |
| [`missions.md`](missions.md) (`REQ-APP-MIS-*`) — **exists** (`001`–`012`) | Einsatz list and detail: server-side filtering, the past bound, malformed-row tolerance, debouncing, paging, day grouping, the four list states; the detail's lazy Finanzen tab, the redacted answer, its three failure sentences and the required-enum fragility |
| [`operations.md`](operations.md) (`REQ-APP-OPS-*`) — **exists** (`001`–`011`) | Operationen list and detail: the navigating segment, lazy loading behind it, the two approved design deviations (thin row, no income/expense split), running-versus-finished grouping, the three-reads-one-outcome detail, identity-by-id for "Dein Anteil", and the capped/provisional caveats |
| [`notifications.md`](notifications.md) (`REQ-APP-NOTIF-*`) — **exists** (`001`–`008`) | Inbox, bell badge and push: one state for both, poll plus SSE, the hand-rolled stream reader and its framing rules, device-side wording from type + named params with an honest fallback, and why a row that leads nowhere is not clickable |
| [`dashboard.md`](dashboard.md) (`REQ-APP-DASH-*`) — **exists** (`001`–`007`) | Übersicht: the design's reading order, two independently failing reads, the 204 that means "no banner", the server-clocked seven-day window, and one source per greeting fact |
| `privacy.md` (`REQ-APP-PRIV-*`) | dependency/data-flow gate, § 25 TDDDG storage table, permissions |
| `ui.md` (`REQ-APP-UI-*`) | KRT design tokens, adaptive layout rules, i18n + copy rules, Fan Kit compliance band (see `core/designsystem/fankit/`) — extracted from the binding design spec at `docs/design/android/` |
| `offline.md` (`REQ-APP-OFFLINE-*`) | read-cache scope, backup exclusion, wipe semantics |

Server-side requirements (API exposure, rate limits, monitoring) stay in the main `basetool`
repo's `docs/specs/` — never duplicated here.
