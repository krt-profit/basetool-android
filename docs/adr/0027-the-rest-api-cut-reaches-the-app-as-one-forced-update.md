# ADR-0027 — The REST API cut reaches the app as one forced update

- **Status:** Accepted
- **Date:** 2026-10-11
- **Deciders:** @greluc (owner answers of 2026-10-10: Q-3, Q-4, Q-5, Q-9)
- **Related:** [ADR-0026](0026-the-app-publishes-its-calls-and-rereads-the-version-policy.md); main repo
  `docs/modularisation/api-cut-waves.md` (waves 2 to 6, shape changes S-1 to S-9), D-04, D-11, D-24;
  `REQ-APP-API-005`, `REQ-APP-API-011`, `REQ-APP-API-012`

## Context

The backend re-cuts `/api/v1` per domain in six waves and ships them as one release. 74 operations the
app calls move or disappear; four shapes change (`GET /missions` takes `q`, the game-item catalogue is
folded, the job-order thresholds are one typed read, the refinery writes bind their own records); the
Fleetview import is deleted. Every retired path answers `410 APP_UPDATE_REQUIRED` ahead of
authentication, so an older build meets the update wall of ADR-0026 on its first call.

## Decision

**The app calls the new paths only, in `versionCode` 19, with no fallback to the old ones.**

- One forced update, not one per wave (owner, Q-5). The app has no dual-path code: a retired path
  answers `410`, so trying it first or second would only reach the wall faster or slower.
- The work lands as one pull request per repository class into the long-lived branch
  `claude/api-cut`, merged on a green CI of the exact head (owner, Q-3). Nothing reaches `main` before
  the backend release is ready; the merge of the branch into `main`, the tag and the release are the
  owner's. CI runs for those pull requests through an addition to three workflow filters that the
  closing pull request removes again.
- **The vendored `openapi.json` is the cut's document.** While classes are still on their old paths it
  also carries those old operations, because `AppCallListTest` requires every listed operation to
  exist in it; each class pull request removes its own. The closing pull request leaves the plain
  copy, which `and-splice.py` of the cut's handover reproduces byte for byte.
- **The retired operations are vendored** (`core/contract/retired-operations.txt`,
  `declared-breaks.txt`) and `RetiredOperationsTest` fails the build when the call list names one,
  when the document still documents a retired one, or when the ledger's field-level lines lose
  their test.
- **The Fleetview import is removed** with the operation (owner, Q-4). The cut's plan assumed a
  ship import through `POST /hangar/import/ships`; that operation is web-only and was never in the
  app's list, so the Hangar has no import afterwards.

## Consequences

- A member on an older build sees „Update erforderlich" after the backend release, as designed; a
  member on 19 against the old backend would meet `404`s, so the build is not releasable ahead of it.
- The quality-tier fields of the claim and demand schemas (`qualityRequirement` as a string, a
  `qualityTier` object) are read as the old two values; a third tier shows as „no requirement" until
  the app learns it.
- The call list `app-calls.txt` after the closing pull request is the app's real list for the
  backend's `19.txt`.

## Rejected

- *Per-wave releases*: five forced updates for one cut.
- *Falling back to the old path on `404`*: the old paths answer `410`, and a client-side retry
  ladder is exactly the code the hard cut removes.
- *A single pull request*: 74 operations and four shape changes are not reviewable as one diff.
