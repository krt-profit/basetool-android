# ADR-0023 — The transport never replays a write that may have reached the server

- **Status:** Accepted
- **Date:** 2026-09-22
- **Deciders:** @greluc
- **Related:** ADR-0001 (core module split and network layer), `REQ-APP-API-009`,
  improvement audit 2026-09 finding SIB-SEC-08

## Context

`KrtHttpClient` builds the API client with `retryOnConnectionFailure(true)`, which is also
OkHttp's default. With it on, OkHttp repeats a request by itself in two situations the caller never
sees:

1. an `IOException` **after** the request was written — a pooled HTTP/1.1 connection the server
   had already closed, a reset while the answer was on its way;
2. an HTTP `408 Request Timeout`.

OkHttp skips both replays only when the request body reports `isOneShot() == true`. Every body the
app builds — `toRequestBody` in `ApiReader` and `TermsRepository`, `FormBody` in `TokenClient`,
`MultipartBody` for the Fleetview upload — reports `false`. So a `POST` that the server had already
processed could be sent a second time, and nothing in the app would know: a second bank booking, a
second Auftrag, a second sign-up. On a versioned row the replay would at least be refused, as a
`409` on a save that actually succeeded, which the member then reads as somebody else's edit.

The audit verified the path twice against the OkHttp source; `WriteReplayTest` now reproduces it —
with the fix removed, a `POST` dropped after the server read it arrives **twice**.

## Decision

**Every `POST` and `PATCH` body is marked one-shot by an application interceptor,
`OneShotWriteInterceptor`, installed last on the API client and on the token client.**

- **An interceptor, not a change at each call site.** Write bodies are built in three modules —
  every write path of `ApiReader`, `TermsRepository`'s own body through `execute`, `TokenClient`'s
  forms. One interceptor covers all of them and every future one; a per-call-site wrapper is a rule
  the next repository method forgets.
- **Retry stays on.** OkHttp distinguishes "may have been sent" from "never sent" itself
  (`requestSendStarted = e !is ConnectionShutdownException`) and the one-shot flag vetoes only the
  first. A connection that fails before the request leaves — an HTTP/2 connection the server shut
  down, a route that would not connect — is still retried, because the server never saw the write.
- **`PUT` and `DELETE` keep the replay.** They are idempotent by definition, and a `PUT` of a
  versioned row is refused with `409` on replay rather than applied twice. Only `POST` and `PATCH`
  can take effect twice.
- **The token client gets it too.** Every token call is a `POST`; a refresh rotates the refresh
  token and its DPoP proof carries a `jti` Keycloak accepts once, so a replay of a refresh that
  landed can only be refused.
- **The app's own 401 retry is untouched.** `TokenRefreshInterceptor` re-sends a request the server
  refused before doing anything. One-shot is a flag OkHttp's recovery reads, not a property of the
  bytes — the wrapper still delegates to a replayable buffer and writes the same payload again,
  which `WriteReplayTest` asserts.

Measured with OkHttp 5.5.0 while writing the tests: a one-shot `POST` answered `503` with
`Retry-After: 0` is **not** replayed either, so the three automatic replays OkHttp has (`IOException`
after send, `408`, `503`/`Retry-After: 0`) are all closed for writes.

## Consequences

- A write that meets a stale pooled connection fails with `ApiError.Network`, and the member
  presses save again. That is the trade: a failure the member sees and can judge, instead of a
  duplicate nobody sees. Whether the first attempt landed is then answered the way it always is on
  this API — the next read shows it, and a versioned retry of a save that did land is a `409`.
- `WriteReplayTest` pins both halves — the writes that must not be replayed and the reads that must
  still be — and was checked to fail with the interceptor removed (four of eight tests red).

## Alternatives considered

- **`retryOnConnectionFailure(false)`.** Closes the defect and also stops every read from
  recovering from a stale pooled connection, so list screens would show an error banner for a
  network event the member could not have caused. Rejected.
- **Wrapping bodies at each call site in `ApiReader`.** Covers `ApiReader` and misses
  `TermsRepository`'s own body, the token client's forms and every future caller of `execute`.
- **An idempotency key per write.** The right server-side answer, and not available: the backend
  has no idempotency-key contract, and the app cannot add one alone (main repo `REQ-API-009`).
