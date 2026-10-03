# ADR-0026 — The app publishes the calls it makes and re-reads the version policy

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** @greluc
- **Related:** main repo `docs/DOMAIN_MODULARISATION_PLAN.md` D-04, D-11, §5.10 and step 0.7;
  `docs/modularisation/rest-api-cut.md` (*The forced update*, *Contract machinery*, G-23); main
  repo REQ-API-009, REQ-API-010; `REQ-APP-UI-004`, `REQ-APP-API-010`, `REQ-APP-API-011`

## Context

The backend will re-cut `/api/v1` per domain in waves. Each wave is a **hard cut** (D-04): the
paths an older app calls disappear, a new app release ships first, and the minimum `versionCode`
in `GET /api/v1/app/version-policy` is raised so older builds show „Update erforderlich". Two things
on the app side stood in the way:

1. **The wall came too late.** The gate read the policy once per process and failed open. Every app
   open during a deploy, and every app started while the backend restarted, kept running against
   retired paths until its next cold start; each screen failed on its own with „Signal Lost".
2. **Nobody knew exactly what the app calls.** The backend's frozen contract set was assembled by
   hand. It missed eleven operations today's app calls, so a wave could break them without a
   declared break — and an edge allow-list generated from that set would refuse them.

The owner decided D-11 on 2026-10-01: a release-bound floor on the server, plus the app re-reading
the policy on resume and after an unexpected `404`, shipped before the first cut, plus retired paths
answering `APP_UPDATE_REQUIRED`. G-23 asks for the app's call list before anything is generated from
the frozen set.

## Decision

**The gate re-reads the policy on every activity resume and after any `404` on an API path, at most
once a minute, and walls off at once on `APP_UPDATE_REQUIRED`.**

- *Every* `404` counts. A retired route and a missing row both answer `NOT_FOUND`, and the edge's
  refusal is a bare `404`; the app cannot tell an expected one from an unexpected one, and guessing
  per call site would rot. The one-minute interval makes the cost one small anonymous read.
- The signal travels from an OkHttp interceptor in `core:network` through a process-wide bus to the
  activity's gate. The response is untouched; no new `ApiError` state exists for it.
- `APP_UPDATE_REQUIRED` is final for the gate's lifetime: the server has said this build calls
  something it no longer serves. A policy read only refreshes the release link.
- Fail-open is kept: a failed first read runs the app; a failed re-read keeps the last verdict.

**The app commits `core/contract/app-calls.txt`, one line per backend operation it calls — verb,
documented path template, query parameters, readable response fields, call sites — and
`AppCallListTest` keeps it exact.**

- The test fingerprints every `ApiReader` / `SseStream` call together with the constants and
  `…Path` / `…Params` helpers it reaches, so a changed call, path, verb or parameter fails `check`
  until the line is updated. It validates each line against the vendored `openapi.json`.
- Fields are derived, not hand-written: every documented response property, two levels deep, whose
  name occurs in `core:data`'s code. That over-states rather than under-states, which is the safe
  direction for a freeze.
- A hand-curated, per-call field list was rejected: 247 call sites by hand would drift on the first
  edit. A runtime recorder driving every repository method was rejected: synthesising arguments for
  each would cover one branch of each and still miss the rest.

## Consequences

- A member who returns to the app after a wave meets the wall instead of failing screens; a member
  in the middle of using it meets it after the first call to a retired path.
- The policy endpoint sees at most one read per app per minute while the app is used, instead of one
  per cold start.
- Every change to a call site updates `app-calls.txt` in the same commit; the test prints what to
  write. The main repo can commit the file per release and assert its frozen set covers it.
- The scan reads one shape of code. The guard in the same test rejects the shapes it cannot read —
  an `ApiReader` under another name, an extension on it, `with(reader)`, `newCall` or a wire model
  outside `core:data` — rather than letting them through silently.
