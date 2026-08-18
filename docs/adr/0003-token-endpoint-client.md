# ADR-0003 — The token endpoint client: its own HTTP client, and outcomes as states

- **Status:** Proposed
- **Date:** 2026-08-18
- **Related:** [ADR-0001](0001-core-module-split-and-network-layer.md) ·
  [ADR-0002](0002-refresh-token-at-rest.md) ·
  [`docs/specs/auth.md`](../specs/auth.md) (`REQ-APP-AUTH-003`, `-005`, `-006`) ·
  [`docs/ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §3–4 ·
  main repo ADR-0131 · RFC 9449 · RFC 6749 §5

## Context

ADR-0002 put a refresh token on the device. Something now has to spend it, and everything about
that exchange is decided by a realm the app does not control: Keycloak issues the tokens, judges
the DPoP proofs, and answers refusals in a vocabulary where two very different situations share one
HTTP status.

Three constraints come in from outside and are not negotiable here:

1. The realm binds **only the refresh token** (main repo ADR-0131). A proof sent on any other
   request makes Keycloak bind the access token too, and the backend's bearer filter rejects a
   bound access token — so an over-eager proof breaks every API call *after* the next login.
2. The realm does not rotate refresh tokens, which is *why* the binding exists.
3. Keycloak answers HTTP 500 at `/userinfo` for a client under this policy, so profile claims must
   come from the ID token.

## Decision

**Token traffic gets its own `OkHttpClient`, derived from the API client.** Not a style choice: the
API client carries `MandatoryHeadersInterceptor`, and an `Authorization` header on Keycloak's token
endpoint is read as client authentication and answered with `invalid_client`. Reusing the API
client would produce an app that logs in successfully and can then never refresh — a defect whose
first symptom appears five minutes after a successful login, and which no login test would catch.
`KrtHttpClient.createTokenClient` keeps the connection pool, dispatcher and timeouts, drops the
interceptors, and re-adds `ServerTimeInterceptor` alone. That last part matters more here than on
the API client: the clock a proof must agree with is Keycloak's, and this is the only traffic that
observes it directly.

**Endpoints are derived from the issuer, not discovered.** Keycloak's URL layout is fixed, so
discovery would buy a round trip on the login path and one more offline failure mode. It also makes
constraint 3 structural: `OidcConfiguration` has no `userinfo` property, so the endpoint that
answers 500 cannot be called by accident.

**Outcomes are states, not exceptions — and the ones that look alike are kept apart.**
`TokenResult` distinguishes `SessionEnded` (`invalid_grant`) from `Rejected` (any other OAuth
error) because both arrive as HTTP 400 and only the first means "show the login screen"; treating a
misconfigured realm as a dead session yields a login loop that reads as the member's fault.
`AccessTokenBound` is a *successful* response singled out: `token_type` other than `Bearer` means
the per-client "Require DPoP bound tokens" switch overrode the client policy, and its natural
symptom — every API call 401s — accuses the wrong component. `Malformed` keeps a captive portal's
200-with-HTML from parsing into a session with no tokens in it, and `Unreachable` is the only state
allowed to read as "you are offline".

**The RFC 9449 nonce dance is implemented although the realm does not use it.** The last
`DPoP-Nonce` is remembered and echoed; a `use_dpop_nonce` refusal is retried exactly once. The cost
is a claim and one branch; the alternative is that a server-side setting change becomes a
client-breaking one. Exactly once, because a realm that rejects the nonce it just issued is broken
and a loop there would turn one device into a load generator on the token endpoint.

**Revocation is best-effort and carries no proof.** It answers `false` and logs rather than
failing; a logout has to complete on a phone with no connectivity, and what protects the device is
the local wipe. No proof, because RFC 9449 binds token *issuance* — a proof on a revocation would
assert something the realm does not check.

## Consequences

- The proof factory has exactly one caller, and the "never send a proof elsewhere" rule is enforced
  by construction rather than by convention: the client that could carry an interceptor is not the
  client that talks to the realm.
- A realm misconfiguration that would otherwise surface as a 401 storm in the API layer is named at
  the moment it is knowable, in the component that can see the cause.
- `MockWebServer` covers the whole surface — proof placement, the error taxonomy, the nonce retry,
  the expiry stamp — without a device or a live realm.
- Two things remain unverified against the real realm and are marked open in `REQ-APP-AUTH-003`:
  that the proof is accepted under the refresh-only policy from *this* client (the concept verified
  it against a throwaway Keycloak), and the end-to-end logout sequence, which needs the session
  state the login flow owns.
- Adding a second API surface later (a second host, a second audience) means deciding again which
  headers belong on it. That is the intended consequence of making the header set explicit per
  client rather than global.

## Alternatives rejected

- **One client for everything, with a per-request header override.** The failure mode is silent and
  the fix is a negative assertion at every call site; a client that structurally cannot send the
  header is cheaper to keep correct.
- **AppAuth for the token exchange.** It has no DPoP support, so the proof would have to be
  injected into its request pipeline anyway — inheriting a dependency's flow control for the sake
  of the part it does not implement.
- **Throwing on refusal.** `invalid_grant` is an ordinary event in the life of a session, not an
  exception; making the caller remember to catch it is how a login loop gets written.
- **Discovery at startup.** See above — and it would have made `/userinfo` available exactly where
  it must not be.
