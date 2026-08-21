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

### REQ-APP-API-006 — The active org unit is a pin the app owns, and it is part of the session

Every request carries `X-Active-Org-Unit-Id` (`REQ-APP-API-001`); this requirement says where the
value comes from. The backend does not store a "current" org unit for a client — it answers what it
would pick (`GET /api/v1/me/active-org-unit`) and otherwise honours whatever the client pins. So the
pin is the app's, and getting it wrong has no error message: it shows a member the wrong scope's
data, or an empty screen, with nothing on it explaining why.

**The rule, in order:** a pin stored on this device wins; otherwise the server's answer, provided
the member is actually in that unit; otherwise their first membership. The last step matters for
the common case — a member with exactly one unit must never see an empty badge for a scope that was
never in doubt.

**A pin naming a unit the member no longer belongs to is dropped**, not kept. An administrator can
remove a membership, and a stale pin sends a header the backend refuses — which reads as "everything
is empty" rather than as "you are not in that unit any more".

**The pin dies with the session.** It is not a UI preference: a device handed on after a sign-out
must not leave the next member inside the first one's Staffel, so logout clears it and the backup
rules exclude its file from cloud backup **and** device transfer, alongside the token store.

**It is read synchronously, and that decides the storage.** The header interceptor runs on an OkHttp
dispatcher thread and cannot suspend, so the pin lives in `SharedPreferences` — the API whose
contract is a synchronous read — rather than in the token DataStore. Both ways of bridging that gap
were tried and both failed on a device:

- *Mirroring the DataStore value in memory* left the mirror empty until a suspending `load()` ran,
  and its only caller ran **after** the first requests of a cold start. Measured with a temporary
  log in the header provider: the first three requests of every launch went out with no header at
  all. Nothing visible broke, because every screen on that path is me-scoped — the first scoped read
  added to start-up would have shown the wrong scope with no error anywhere.
- *Seeding the mirror with `runBlocking` on first read* closed that hole and opened a worse one: it
  deadlocks whenever the caller's thread is the one DataStore's scope runs on. The first test
  written against it hung.

**The app never sends the header as an administrator.** The backend gives an admin a scope rule
this app has no screen for: no header means *all* org units, and a pin is honoured even for a unit
the admin does not belong to. Rather than teach the app to avoid that, the role is kept out of its
tokens entirely (main repo `REQ-SEC-035`) — an administrator using the app is a member in it, with
their member roles and nothing else. So the admin branch of the header contract is unreachable from
here by construction.

**The switcher offers a choice or nothing.** With a single membership there is nothing to switch to,
so the badge is not tappable — the same rule the web sidebar applies. With no membership at all the
badge is absent rather than showing a placeholder, because a placeholder would be a claim about a
scope the app does not have.

**Acceptance**

- [x] The three-step rule, the dropped stale pin and the refusal to pin a foreign unit are covered
  (`OrgUnitViewModelTest`).
- [x] The pin is readable synchronously **from a fresh instance with no priming** — the way the
  interceptor reads it on the first request of a cold start — survives a restart and is cleared by
  a wipe (`ActiveOrgUnitStoreTest`).
- [x] The options come from `GET /api/v1/users/me/memberships` — me-scoped by construction, so the
  public vhost never has to allow-list a path able to name another member (main repo `REQ-API-009`).
- [x] An org unit whose `kind` this build does not know is still offered; only its grouping is
  unknown (`OrgUnitRepositoryTest`).
- [x] Verified on a device against the test stack, with two real memberships in the throwaway DB
  (IRIDIUM + SK Vanguard): the badge names the member's Staffel, two memberships make it tappable,
  the sheet lists both, choosing the other one moves the badge, and each choice survives a cold
  start. 11 of 11 checks.
- [x] The header itself was measured rather than inferred: before the storage change the provider
  returned `null` for the first three requests of a cold start; after it, the very first request
  carries the pinned id.
- [x] The **admin** case is closed as *not applicable by design*, and enforced rather than assumed.
  The backend does honour an admin's pin differently — without a header it scopes an admin to every
  org unit at once, and with one it honours a pin to a unit they do not belong to — but no request
  from this app is ever an admin request. The Keycloak client's scope withholds the `Admin` realm
  role (main repo `REQ-SEC-035`), the backend derives a request's authorities from the roles the
  token carried, and its provisioning script fails verification if the role is ever added. Measured
  against the test stack's realm, Keycloak 26.7, 2026-08-21: the token the client would mint for an
  account holding `Admin` carries `['KRT Member', 'Officer']`. Building an admin path into the app
  would need that decision reversed first, which is the point of asserting it.
- [ ] A member whose memberships change while the app is open. **Open** — the list is read once per
  process; a change made by an administrator shows up on the next start.

### REQ-APP-API-005 — DTOs are generated from the backend's `openapi.json`

Hand-written DTOs let contract drift surface at runtime, on a device, in a version that cannot be
redeployed. Generated ones make it a compile error. `:core:contract` holds the backend's committed
document and the models the generator produces from it (ADR-0008); **models only** — no API
interfaces, because the repositories classify failures by the backend's stable problem `code`
rather than by HTTP status (`REQ-APP-API-002`), fold some refusals into successes and page-walk
catalogs, none of which a generated client does.

The one hand-written model that stays is [`ProblemDetail`] in `:core:network` — the error envelope,
which has to work when the contract does not, and which the document describes only as six
untyped fields.

**Generated models are not automatically safe to decode**, and three of the ways they are not were
found by decoding them:

- **Decimals.** kotlinx.serialization has no serializer for `BigDecimal`, so the generator marks
  those properties `@Contextual` — which describes the property's own type, never a type argument,
  and a `Map<String, BigDecimal>` therefore does not compile. `KrtDecimal` carries its own
  serializer and reads the JSON number's literal text, so a balance keeps every digit the ledger
  recorded. A `Double` would have compiled and been wrong by a cent.
- **Enums are strict.** kotlinx.serialization throws on a constant it does not know, and adding a
  constant is additive change the server is explicitly free to make (main repo `REQ-API-009`). One
  of those enums is read on the *login* path. `KrtJson` therefore sets `coerceInputValues`, which
  turns an unknown constant into `null` — restoring exactly the tolerance the hand-written DTOs had
  by reading the field as a plain `String`.
- **Almost nothing is `required`**, so every generated property is nullable with a `null` default.
  Absent is a state each repository has to give a meaning; the type system does not settle it.

**The document is a vendored copy**, refreshed by hand from the main repo together with the
`REQ-API-009` contract-set change that opens new endpoints to the app. The commit it came from is
recorded in `core/contract/src/main/openapi/README.md`.

**Acceptance**

- [x] A generator produces the resource DTOs from the committed `openapi.json`
  (`:core:contract:openApiGenerate`, 403 models).
- [x] The two hand-written resource DTO families are gone — the terms document and the registration
  status now decode into generated models, and the repository tests that covered them still pass
  unchanged.
- [x] Decimals keep full precision, in a field and inside a `Map`; an unknown enum constant decodes
  to `null` rather than throwing; an unknown field is ignored (`ContractDecodingTest`).
- [ ] Drift against the **live** backend fails something. **Open, and the honest limit of this
  requirement** — the build compiles against a vendored copy, so it catches drift the moment
  somebody refreshes that copy, and not before. A check that compares the committed document
  against the main repo's is the next step; both repositories are public, so it is reachable.
- [ ] Only the operations in the `REQ-API-009` contract set are consumed. **Open** — nothing
  enforces it on this side; the generated surface is every schema in the document.
