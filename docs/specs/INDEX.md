# Spec Registry

Durable requirements for the Android app live here as docs-as-code, one file per area, using
ids `REQ-APP-<AREA>-NNN`. Every behaviour change updates its requirement in the same PR; a
change with no matching spec change is incomplete (see `CLAUDE.md`).

The first area has been extracted; for the rest the **concept documents in [`docs/`](../) remain
the binding source** (marked `Doc type: living plan`):

| Area (planned) | Will absorb |
|---|---|
| [`auth.md`](auth.md) (`REQ-APP-AUTH-*`) — **exists** (`001`–`006`) | login flow, token storage, DPoP, session states, app-lock |
| [`api-contract.md`](api-contract.md) (`REQ-APP-API-*`) — **exists** (`001`–`005`) | consumed endpoints, headers, problem-code handling, pagination, version echo |
| `privacy.md` (`REQ-APP-PRIV-*`) | dependency/data-flow gate, § 25 TDDDG storage table, permissions |
| `ui.md` (`REQ-APP-UI-*`) | KRT design tokens, adaptive layout rules, i18n + copy rules, Fan Kit compliance band (see `core/designsystem/fankit/`) — extracted from the binding design spec at `docs/design/android/` |
| `offline.md` (`REQ-APP-OFFLINE-*`) | read-cache scope, backup exclusion, wipe semantics |

Server-side requirements (API exposure, rate limits, monitoring) stay in the main `basetool`
repo's `docs/specs/` — never duplicated here.
