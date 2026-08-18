> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-08-18.
> **Owner area:** API · **Related:** [`../ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md),
> main repo `REQ-API-004`, `REQ-API-009`, `REQ-OBS-002`, `REQ-ORG-*`, `REQ-SEC-031`,
> ADR-0001 (this repo)

# API contract — how the app talks to the Basetool backend

The app owns no business logic. Everything it shows comes from `/api/v1`, so the rules that keep
that conversation honest are requirements, not conventions.

## Requirements

### REQ-APP-API-001 — Every API request carries four headers

`Authorization`, `X-Active-Org-Unit-Id`, `Accept-Language` and `X-Correlation-Id` are added in one
interceptor (`MandatoryHeadersInterceptor`), never per call site. Three of the four fail **silently**
when missing, which is why they are centralised:

- **`Authorization`** — omitted entirely when there is no session, rather than sent empty. An
  anonymous endpoint treats a malformed `Authorization` as a failed authentication and answers 401,
  turning a guest read into a login prompt.
- **`X-Active-Org-Unit-Id`** — the org-unit pin (main repo `REQ-ORG-*`). Omitted when nothing is
  pinned; the backend then falls back to the member's default, which is correct on a fresh install
  and wrong the moment the member switches context — a missing header shows another squadron's data
  without any error.
- **`Accept-Language`** — decides the language of the localised problem bodies. It follows the
  in-app language setting, not the device locale, because the app offers an explicit choice.
- **`X-Correlation-Id`** — one per request; the backend logs it and echoes it on error bodies
  (main repo `REQ-OBS-002`), which is what ties a member's screenshot to one server log line.

A header the caller set explicitly is never overwritten: a token exchange carries its own
`Authorization`, and a retry reuses the correlation id of the attempt it repeats.

**Acceptance**

- [x] All four are present on an authenticated call (`MandatoryHeadersInterceptorTest`).
- [x] `Authorization` is absent — not empty — without a session.
- [x] The org-unit pin is absent before one is chosen.
- [x] A caller-set header wins.

### REQ-APP-API-002 — Problem codes are app states, and the code decides — not the status

The backend's RFC 7807 `code` (main repo `REQ-API-004`) selects the state; the HTTP status is only
the fallback for responses that carry no problem body, which is what the edge produces.

This is not a style preference. The backend answers **403** for three unrelated situations —
`PENDING_APPROVAL`, the terms gate, and a genuine authorisation failure — and a client that branches
on the status shows the wrong screen for two of them.

`ApiErrorMapper` maps to a sealed `ApiError`: `Unauthenticated`, `PendingApproval`,
`TermsAcceptanceRequired`, `Forbidden`, `RateLimited` (with `Retry-After`), `OptimisticLock`,
`NotFound`, `Validation`, `ServiceUnavailable`, `Server`, `Network`. Sealed so a new state cannot be
forgotten by a `when` that stops compiling.

`Network` is deliberately distinct from `ServiceUnavailable`: the first means the server never
spoke, the second means it spoke and could not serve. Only the first may read as "you are offline".

An unparseable body never becomes a thrown error — the status still classifies the response, and
only the localised prose is lost. Unknown JSON fields are ignored, because the external contract set
(main repo `REQ-API-009`) explicitly permits additive change and a client that rejected it would
turn a permitted change into a break.

**Acceptance**

- [x] Each of the three 403 shapes maps to its own state (`ApiErrorMapperTest`).
- [x] `Retry-After` is carried on `RateLimited`, and its absence does not change the state.
- [x] An HTML edge error page classifies by status instead of throwing.
- [x] An unknown field in a problem body does not break parsing.

### REQ-APP-API-003 — The HTTP client has no disk cache

`OkHttpClient` installs none, deliberately (security concept §4). The read cache is meant to be the
**only** persistence layer for member data, because it is the one that is backup-excluded, wiped on
logout and clearable from settings. An HTTP cache would be a second copy of the same data outside
every wipe path. The server mirrors the intent with `no-store` on its sensitive reads (main repo
`REQ-SEC-031`).

**Acceptance**

- [x] `KrtHttpClient.create` installs no `Cache`.
- [ ] A test asserts the absence once a second client factory exists to confuse it with. **Open** —
  today the single factory is the whole surface.

### REQ-APP-API-004 — Proof timing follows server time, not the device clock

Keycloak accepts a DPoP proof lifetime of 10 s with 15 s of skew, which is tighter than ordinary
mobile clock drift; the desktop extractor records drift as its primary DPoP failure mode (main repo
`REQ-INGEST-012`). A phone whose clock is a minute off therefore cannot log in, and the failure
presents as "login is broken", not "your clock is wrong".

`ServerClock` learns the offset from the `Date` header of every response and is the time source for
DPoP proof `iat`. Before the first response it returns device time — the honest fallback, since
nothing better exists yet. A response without a usable `Date` leaves the offset untouched: the
header is advisory, and one odd response must not poison it.

**Acceptance**

- [x] The offset is learned in both directions and applied to `now()` (`ServerClockTest`).
- [x] A response without a `Date` header leaves the previous offset in place.
- [ ] Proof `iat` is actually taken from `ServerClock`. **Open** — lands with the token layer.

### REQ-APP-API-005 — DTOs are generated from the backend's `openapi.json`

Hand-written DTOs let contract drift surface at runtime, on a device, in a version that cannot be
redeployed. Generated ones make it a compile error. The generator pipeline is not in place yet; the
only hand-written model today is [`ProblemDetail`], which is the error envelope rather than a
resource DTO and exists in the layer that must work even when the contract does not.

**Acceptance**

- [ ] A generator produces the resource DTOs from the committed `openapi.json`, and drift fails the
  build. **Open** — own PR, tracked as the DTO pipeline ADR.
