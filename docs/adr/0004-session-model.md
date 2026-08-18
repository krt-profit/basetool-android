# ADR-0004 — The session model: a stale session is not a signed-out one

- **Status:** Proposed
- **Date:** 2026-08-18
- **Related:** [ADR-0001](0001-core-module-split-and-network-layer.md) ·
  [ADR-0002](0002-refresh-token-at-rest.md) · [ADR-0003](0003-token-endpoint-client.md) ·
  [`docs/specs/auth.md`](../specs/auth.md) (`REQ-APP-AUTH-003`, `-005`, `-007`) ·
  [`docs/ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §3–4 ·
  RFC 7636 · RFC 8252 · RFC 9449 §10 · OIDC Core §3.1.3.7

## Context

ADR-0002 stored a refresh token, ADR-0003 built the client that spends it. What was still missing is
the thing that decides *when* to spend it, what to do when spending fails, and what the UI is
supposed to show meanwhile.

Two constraints shape it. `AccessTokenProvider` is **synchronous** (ADR-0001) because an OkHttp
interceptor cannot suspend — so the token has to be a field, and keeping it fresh has to be somebody
else's job. And the app runs on a phone, where a failed request usually means a tunnel rather than a
revoked grant.

## Decision

**A failed refresh has two meanings, and the session model keeps them apart.** `SessionState.Stale`
says "a stored session exists and could not be proven right now"; `SignedOut` says "there is no
session". Only `invalid_grant` produces the second. Collapsing them is the obvious implementation
and it is wrong in the expensive direction: it turns a tunnel into a logout and asks the member for a
password they never needed. The token client already separates `Unreachable` from `SessionEnded`
(ADR-0003) precisely so this layer can.

**Refreshing is single-flight.** A mutex plus a re-check inside it. Without it, several screens
loading at once each notice the expiry and each start a refresh — several token requests where one
belongs, each carrying a DPoP proof for the realm to verify, and all but one result discarded. The
test that asserts this was verified to fail when the lock is removed, because a concurrency test that
passes vacuously is worse than none.

**A refresh response with no `refresh_token` keeps the stored one.** The realm does not rotate them
(main repo REQ-SEC-012), so omission is legitimate — and taking it as "there is none now" would throw
away the only way back into the session.

**The three per-attempt login secrets live in one object.** Verifier, `state` and `nonce` are minted
together in `AuthorizationRequest` and consumed together. Keeping them in one place is what makes
dropping one a compile error rather than a silently weaker login.

**`state` is checked before anything else in a redirect is read**, so a redirect the app did not
start cannot steer the flow, not even into an error screen of its choosing. **The `nonce` is
verified against the ID token** — otherwise the parameter is decoration; a token whose nonce does not
match was not minted for this login, and nothing is stored.

**The redirect is parsed at string level.** Production redirects to a verified App Link, but the dev
realm registers `de.kartell.basetool:/oauth2redirect`, and an HTTP URL parser refuses a custom scheme
outright. A URL parser here would break every login on the build the flow is developed against.

**The ID token's signature is not verified,** and that is a decision rather than an omission. OIDC
Core §3.1.3.7 permits skipping it when the token is received directly from the token endpoint over
TLS, which is the only way this app obtains one — it never travels through the browser. Verifying it
would mean fetching, caching and rotating JWKS to re-prove what TLS already gives.

**`deleteKey()` moved into the `SecretCipher` contract.** A logout that deletes the ciphertext but
leaves the key behind still leaves a key that can decrypt any copy of that ciphertext which escaped
the device. A cipher that cannot be wiped cannot back a logout, so the wipe is part of the interface
rather than a capability of one implementation.

## Consequences

- The UI gets a fourth state to render (`Unknown`, `SignedOut`, `SignedIn`, `Stale`). That is the
  point: an offline start-up and a revoked session look nothing alike to a member and should not
  look alike in the app.
- `refreshIfNeeded()` has to be called by the layer that makes API calls, before it makes them. That
  is the price of a synchronous `AccessTokenProvider`, and it is visible rather than hidden inside an
  interceptor doing `runBlocking`.
- Everything here is tested on a JVM against `MockWebServer` — including the wipe, the single-flight
  refresh and the nonce check — with no device and no live realm.
- Still open, and marked so in `REQ-APP-AUTH-007`: the Custom Tab launch, the redirect activity, and
  the screens. `logout()` returns the end-session URL rather than opening it, for the same reason.

## Alternatives rejected

- **One `SignedOut` state for every refresh failure.** Simpler, and it logs members out of a working
  session whenever the network hiccups.
- **Refreshing inside the OkHttp interceptor** (an `Authenticator` or a blocking interceptor). It
  would make the provider self-maintaining at the cost of `runBlocking` on a network thread and a
  refresh storm under concurrency — the problem the mutex exists to prevent, moved somewhere it
  cannot be solved.
- **Verifying the ID token signature.** JWKS fetching, caching and key rotation to re-prove a
  property TLS already gives on the only path the token ever takes.
- **Skipping `nonce` because PKCE already prevents code injection.** True as far as it goes, and it
  leaves the ID token unbound to the attempt; the check costs one comparison.
