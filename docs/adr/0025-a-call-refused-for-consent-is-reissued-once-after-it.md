# ADR-0025 — A call refused for missing consent is issued once more after the member consents

- **Status:** Accepted
- **Date:** 2026-09-27
- **Deciders:** @greluc
- **Related:** ADR-0023 (the transport never replays a write), design ch. 19 artboard 11 (N7),
  `REQ-APP-AUTH-009` (the first-run terms gate), `REQ-APP-AUTH-016`, main repo REQ-SEC-028,
  krt-profit/basetool#2097

## Context

The server refuses every `/api/v1/**` call with `403 TERMS_NOT_ACCEPTED` while the member has
not accepted the Terms of Use in force. The app asked for consent only at start-up: the first-run
gate reads the status once and opens the app when consent is on record. When the terms change while
the app runs, every later call is refused, and each screen showed that refusal as its own error — a
member who had just filled in a form lost it to a message that did not say what to do.

Design round 18 ratified the fix (artboard 11): the first-run wording as a modal over whatever
screen is open, triggered by **any** call that answers `TERMS_NOT_ACCEPTED`, one overlay for
any number of parallel refusals, exactly two ways out — „Bestätigen" or „Abmelden" — and after
„Bestätigen" **the refused call is repeated**, which the modal says so nobody thinks their input
lost. The owner decided on 2026-09-27 to build that retry, with this record explaining why it does
not contradict ADR-0023.

> [!warning] Corrected 2026-09-27 — the app listened for a code the server never sends
> Until this decision the app mapped `TERMS_ACCEPTANCE_REQUIRED`, the name design chapter 19 and
> this repository's docs used. The server's `TermsAcceptanceAccessFilter` refuses with
> **`TERMS_NOT_ACCEPTED`** and has no other code for it, so every such refusal fell through to a plain
> `ApiError.Forbidden` — found on the device walk for this requirement, where the first refused call
> showed „Signal Lost" instead of the overlay. The mapping now follows the server; the Kotlin type
> keeps its name `ApiError.TermsAcceptanceRequired`.

## Decision

**`ApiReader` re-issues a call refused with `TERMS_NOT_ACCEPTED` exactly once, after a
`ConsentRecovery` answers that consent is now on record.**

- **Why this is not a replay.** ADR-0023 forbids the *transport* from repeating a write that **may**
  have reached the server: after an `IOException` nobody knows whether it was applied. A
  `TERMS_NOT_ACCEPTED` refusal is the opposite case — a definite answer from the server's
  request filter, given before any handler ran, so nothing was applied. Issuing the call again after
  consent is a new request the member asked for by pressing „Bestätigen", not a guess that the first
  one failed. `OneShotWriteInterceptor` stays in place and still blocks OkHttp's own replays of the
  second request.
- **In `ApiReader`, not in an OkHttp interceptor.** An interceptor would have to *block* its thread
  until the member answers. OkHttp runs at most five calls per host; five screens' worth of refused
  calls blocked in interceptors would hold every slot, and the acceptance `POST` would queue behind
  them forever. `ApiReader` is suspending, so a waiting call costs no thread, is cancelled with its
  screen, and the acceptance goes through untouched.
- **Once.** A second refusal is returned to the caller as it is. No loop, whatever the server does.
- **One overlay.** `ReconsentBroker` (one per process, in `AuthContainer`) is the `ConsentRecovery`.
  The first refusal opens the overlay; later ones join the same wait; the answer releases them all.
- **Only while it can be answered.** The overlay arms the broker while it is composed, which is only
  after the first-run gates have cleared. Before that, and after sign-out, a refusal is returned at
  once — a call must never wait for an overlay nobody can see. The two gates' own repositories
  (`TermsRepository`, `AccountGateRepository`) are built without a recovery at all.
- **„Abmelden" keeps the refusal.** Every waiting call returns `TERMS_NOT_ACCEPTED`, and the
  session ends as it does from Einstellungen.
- **The wording, not a summary.** The overlay renders the first-run gate's document column from
  `GET /api/v1/terms/document`, and consent is recorded through the same `POST`. The artboard's
  stamp „Version 4 · 26.09.2026" becomes the server's own „Stand …" line in the lead sentence and the
  first-run „Fassung …" stamp under the text: `version` is a content digest, and there is no
  version number to show. The overlay carries no checkbox; „Bestätigen" is the consent, as
  artboard 11 draws it.

## Consequences

- A member whose terms changed mid-session keeps their screen and their input; the refused call
  completes after „Bestätigen" as if nothing had happened.
- Every repository but the two gates takes a `consent` parameter, and `AuthContainer` passes the
  broker to each — twenty-seven call sites, visible in one place as ADR-0001 wants.
- `KrtModal` gains a non-dismissible mode (no close glyph, no back, no scrim tap), a dimmed CTA and
  a CTA spinner. Offline the CTA is dimmed with its reason, and the wording is fetched again when the
  connection returns.
- Calls outside `ApiReader` — the token client, the live-sync stream — are not held. The stream
  reconnects by itself, and the token endpoint is not behind the terms filter.

## Alternatives considered

- **An OkHttp interceptor that waits and re-sends.** One place for every client, and a deadlock on
  the per-host limit described above. Rejected.
- **No retry: close the overlay and let each screen reload.** Simpler, and exactly what artboard 11
  rules out — a half-filled form would be lost, and the modal's promise could not be kept.
- **Re-issue only reads.** Would leave the write — the case the member cares about most — refused
  after they confirmed. The refusal proves the write was not applied, so there is nothing to protect.
