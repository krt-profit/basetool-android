# The backend's OpenAPI document, vendored

`openapi.json` is a **copy** of the main repository's
`backend/src/main/resources/api/openapi.json`, which that repo keeps in sync with its controllers
(`REQ-API-007`). Nothing in this repository edits it.

| | |
|---|---|
| Source | [`krt-profit/basetool`](https://github.com/krt-profit/basetool) · `backend/src/main/resources/api/openapi.json` |
| Copied from commit | `4996db77c` (2026-10-11), branch `claude/api-cut` of the main repository: the REST API cut (draft PR krt-profit/basetool#2475), the document the app 19 is built against |
| Document | OpenAPI 3.1.0 · 427 paths · 455 schemas once the transition below has ended |

> The copy carries what „Mein Lager" and the „gestohlen" marker read and write (basetool
> REQ-INV-007/-036/-046/-052/-053: `stolen` on stacks and rows, `canMarkStolen` in
> `/api/v1/me/capabilities`, the personal rebooking, the org-unit change and the marking) and the
> profile's RSI handle (REQ-SEC-072), for krt-profit/basetool#2097. The 2026-09-28 refresh adds
> nothing the app reads beyond `source` and `sourceClientId` on its own blueprints (basetool
> REQ-INV-054) and the two exchange notification types in the rule enum; the rest is the exchange's
> admin and member surface, which stays web-only. The next refresh the same day adds
> `sourceClientName` to a personal blueprint (basetool #2231), the client's registered display name.
>
> **A full copy, since the REST API cut.** The earlier splices (notification switches and enum lists of
> 2026-10-10) are part of the backend document now, and so are the quality tiers (ADR-0241): the claim
> and demand schemas carry `qualityRequirement` as a plain string beside a `qualityTier` object,
> which `MaterialClaimRepository` and `MaterialDemandRepository` read as the string. The document no longer
> lists the exchange relay operations; the app never called them.
>
> **Transition.** The cut retires 74 operations the app called (`410 APP_UPDATE_REQUIRED` on the old
> paths) and the app moves to the new ones one repository class at a time. `AppCallListTest` requires
> every listed operation to exist in the document, so while classes are still on the old paths the file is the
> backend document **plus** those old operations (and the schemas only they use). Each class pull request
> deletes the old operations it stops calling; the last one leaves the plain copy. The splice is a script of
> the cut's handover (`and-splice.py`); it keeps the backend's formatting and, given nothing pending,
> reproduces the backend file byte for byte.
>
> **Release order.** The three `…/inventory/my-inventory/…` reads reach production only once their
> API-vhost rules are live (krt-profit/basetool#2171); every write this copy
> adds is admitted already. A build that reads „Mein Lager" must not be released before that edge
> change is deployed, or the screen answers „Signal Lost" exactly as the Raffinerie did on
> 2026-09-08.

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
