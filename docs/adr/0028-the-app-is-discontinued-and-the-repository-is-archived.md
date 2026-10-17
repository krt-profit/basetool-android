# ADR-0028 — The app is discontinued and the repository is archived

- **Status:** Accepted
- **Date:** 2026-10-17
- **Deciders:** @greluc ([#247](https://github.com/krt-profit/basetool-android/issues/247))
- **Related:** main repo [#2638](https://github.com/krt-profit/basetool/issues/2638) (the backend API
  becomes internal-only again), main repo ADR-0135 (the public API vhost existed for this app) and
  ADR-0164 (the installable web app); [ADR-0026](0026-the-app-publishes-its-calls-and-rereads-the-version-policy.md),
  [ADR-0027](0027-the-rest-api-cut-reaches-the-app-as-one-forced-update.md)

## Context

The app put the Basetool on a phone, and it is the only reason `/api/v1` was ever reachable from
the internet: the public API vhost, the default-deny allow-list that names every admitted path, the
`basetool-android` Keycloak client with DPoP-bound refresh, the blackbox probes that watch the API
host, the app floor with its forced-update wall, and a self-managed release key that signs an APK
nobody but this project distributes — all of it exists for one client.

That client is no longer the only way onto a phone. The frontend is installable (main repo
ADR-0164): the Basetool sits on the home screen with its own icon and window, serves the whole
application including the administration this app deliberately never carried, and needs no API
access of its own because it is the server-rendered frontend talking to the backend over the
internal network.

So the app's remaining value is what it does better than that — the device lock, the unread badge,
live data while foregrounded — against the cost of keeping the backend's only internet-facing
attack surface open, a second UI to carry through every REST cut (the most recent of which,
[ADR-0027](0027-the-rest-api-cut-reaches-the-app-as-one-forced-update.md), rewrote 74 operations in
the app), and a release channel that cannot be rotated once the key is gone. The owner decided the
trade in #247, and the backend half is #2638.

## Decision

**The app is discontinued, v0.6.1 is its last release, and this repository is archived.**

- **Archived, not deleted.** The code is GPL-3.0 and stays readable, the releases stay downloadable
  and their attestations stay verifiable. Archiving makes the repository, its issues and its wiki
  read-only in one move, which is also why the handbook's farewell has to be written **before** the
  archive flag is set, not after.
- **No store listing is withdrawn, because none was ever opened.** Distribution was GitHub Releases
  plus Obtainium (plan Q1, never reopened); `docs/GOOGLE_PLAY_DISTRIBUTION_PLAN.md` stayed a
  proposal. Obtainium is not a store — it reads this repository's releases, and it stops offering
  updates when none appear.
- **No farewell build.** The last version stays the one that was released.
- **The documents stay as they were written.** The plan, the security and privacy analyses, the
  specs, the design handoff and the ADRs are the record of a built app, not a description of a live
  one; they are not rewritten into the past tense. The README says what happened, at the top, and
  that is the entry point every other document hangs under.
- **The handbook points at the web app.** This repository's wiki and the main handbook's
  `Android-App` page say the app is discontinued and send members to `Web-App-installieren`.

## Consequences

- An installed build keeps working until `api.profit-base.online` is switched off (#2638, step 4),
  and then cannot connect at all. Members are told this through the handbook and Discord rather than
  by the app, which has no channel of its own to tell them — there is no push, by decision.
- When the backend raises the app floor or retires a path, an installed build shows „Update
  erforderlich" and offers „Zur Download-Seite", which leads to a release page whose newest version
  is the one already installed. That dead end is accepted: it is the honest state of a discontinued
  app, and a build shipped to replace the wording would reach only the members who still update.
- The release key signs nothing further. It stays in the owner's offline backup rather than being
  destroyed, because an archived repository cannot announce a rotation and a verifiable certificate
  is what the published fingerprint is for.
- `core/contract/app-calls.txt` stops being maintained. The main repository reads it to know which
  operations a released build depends on; after the API is internal-only, nothing does.
- Reversing this costs an unarchive and nothing else. The decision is the owner's, and the cost of
  keeping the option is zero, which is why the repository is archived rather than deleted.

## Rejected

- *Keeping the app alive against an internal-only backend*: there would be nothing for it to call.
  Exposing the API through the frontend for the app's sake would rebuild the surface #2638 removes,
  with a second hop in front of it.
- *A final release that replaces the UI with a farewell screen*: it reaches only the members who
  update, needs a release cut from a repository that is being archived, and says in the app what the
  handbook and the announcement say to everyone.
- *Deleting the repository*: the app is GPL-3.0, its releases are installed on members' devices, and
  the published certificate fingerprint and the attestations have to stay checkable.
- *Leaving the repository open but idle*: an unarchived repository invites issues and pull requests
  nobody will answer, and keeps CI, Dependabot and CodeQL running against a dead app.
