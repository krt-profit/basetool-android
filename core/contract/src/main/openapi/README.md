# The backend's OpenAPI document, vendored

`openapi.json` is a **copy** of the main repository's
`backend/src/main/resources/api/openapi.json`, which that repo keeps in sync with its controllers
(`REQ-API-007`). Nothing in this repository edits it.

| | |
|---|---|
| Source | [`krt-profit/basetool`](https://github.com/krt-profit/basetool) · `backend/src/main/resources/api/openapi.json` |
| Copied from commit | `553600bac` (2026-09-22) — PR krt-profit/basetool#1999, **open** at the time of copying |
| Document | OpenAPI 3.1.0 · 400 paths · 417 schemas |

> The copy is ahead of the backend's `main` on purpose: it carries the **manager-only add-by-id**
> `POST /api/v1/missions/{id}/participants/by-id/slim` (`AddParticipantByIdRequest`, basetool
> REQ-MISSION-020), which „Teilnehmer hinzufügen" sends and which lands with basetool#1999.
> **This repository must not release a build that sends it before that PR is merged and deployed**
> — together with its API-vhost rule — or the manager's add fails at the edge exactly as it has
> since 2026-09-07.
>
> The copy also drops the seventeen deprecated mission endpoints the backend deleted early on
> 2026-09-22 (basetool#1996); the app called none of them any more.
>
> (The previous note here tracked the optional body of `POST …/join` and basetool#1765, merged
> 2026-09-03.)

## Refreshing it

```bash
cp ../basetool/backend/src/main/resources/api/openapi.json core/contract/src/main/openapi/openapi.json
./gradlew :core:contract:build
```

Then update the commit above, and **read the compiler output rather than skimming it**: a model
that stopped compiling is the pipeline doing its job. A field that vanished from the document is a
field this app was reading, and the fix is a conversation with the backend rather than a `?.` at
the call site — the operations the app consumes are frozen against exactly that (main repo
`REQ-API-009`, ADR-0136).

## When to refresh

**Together with the main repo's contract-set change that opens new endpoints to the app.** Each app
phase extends `REQ-API-009`'s enumerated set, the API vhost's allow-list and this copy; the three
are one decision seen from three sides. Refreshing this file on its own only imports schema churn
from endpoints the app cannot reach.

## What is not checked

Nothing verifies that this copy still matches the source. The build compiles against what is here,
so drift is caught the moment somebody refreshes it — and not before. Both repositories are public,
which makes an automated comparison reachable; it is recorded as open in `REQ-APP-API-005` rather
than implied by this file's existence.
