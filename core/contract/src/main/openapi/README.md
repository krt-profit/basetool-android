# The backend's OpenAPI document, vendored

`openapi.json` is a **copy** of the main repository's
`backend/src/main/resources/api/openapi.json`, which that repo keeps in sync with its controllers
(`REQ-API-007`). Nothing in this repository edits it.

| | |
|---|---|
| Source | [`krt-profit/basetool`](https://github.com/krt-profit/basetool) · `backend/src/main/resources/api/openapi.json` |
| Copied from commit | `f4d94d871504b77e2e9276383182d7ed9a95d077` (2026-09-03) — PR krt-profit/basetool#1765, **merged**; ships to production with backend 1.6.18 |
| Document | OpenAPI 3.1.0 · 402 paths · 409 schemas |

> The copy is ahead of the backend's `main` on purpose: it carries the **optional request body of
> `POST /api/v1/missions/{id}/join`** (`JoinMissionRequest`), which the sign-up sheet sends and which
> lands with basetool#1765. **This repository must not release a build that sends it before that PR
> is merged and deployed** — an older backend ignores an unknown body, so the sign-up would succeed
> while silently dropping the desired Funktion and the payout preference, which is worse than
> failing.
>
> (The previous note here tracked `GET /api/v1/users/me/memberships` and basetool#1613, merged
> 2026-08-20.)

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
