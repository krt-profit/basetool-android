# ADR-0001 — `core:common` and `core:network`, and what the network layer decides

- **Status:** Proposed
- **Date:** 2026-08-18
- **Related:** [`docs/specs/api-contract.md`](../specs/api-contract.md) (`REQ-APP-API-001…005`) ·
  [`docs/ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §4 ·
  main repo `REQ-API-004`, `REQ-API-009`, `REQ-OBS-002`, `REQ-ORG-*`, `REQ-SEC-031`,
  ADR-0136 (external contract set)

## Context

Phase 1 needs a place for the HTTP conversation before it can have a login screen. The repo had two
Gradle modules — `:app` and `:core:designsystem` — and empty `core/auth`, `core/data`,
`core/network` directories from the scaffolding commit.

Four questions had to be answered before any of it could be written, and each has a wrong answer
that only shows up later.

## Decision

### Two modules, not one, and not the placeholder set

- **`core:common`** — the logging facade, and nothing else for now. It exists because `CLAUDE.md`
  requires a single facade (`android.util.Log` is CI-forbidden) and because `core:network` must not
  become the home of every cross-cutting utility by accident.
- **`core:network`** — the HTTP client, the mandatory-header interceptor, the problem-detail model
  and the error mapper, plus the small interfaces it needs (`AccessTokenProvider`,
  `CorrelationIdFactory`, `LanguageTagProvider`, `ActiveOrgUnitProvider`).

`core:auth` will **depend on** `core:network` and implement `AccessTokenProvider`. Defining that
interface in `core:network` rather than importing an auth type is what keeps the dependency
one-directional; the inverse would be a cycle the moment the token layer needs to make an HTTP call.

`core:common` is not in the scaffolding's directory list. It is added rather than folding the logger
into `core:network` because "the module everything depends on" and "the module that speaks HTTP" are
different jobs, and merging them is the kind of shortcut that is hard to undo once five modules
depend on it.

### The token provider is synchronous

`AccessTokenProvider.currentAccessToken()` does not suspend. It is called from an OkHttp
interceptor, which is synchronous, so a suspending signature would mean `runBlocking` on a network
thread. The auth layer keeps the current access token in memory anyway (security concept §4 — the
access token never touches disk), so the read is a field access and refresh happens out of band.

### The stable `code` classifies errors, never the status

Detailed in `REQ-APP-API-002`. The forcing case: the backend answers 403 for a pending registration,
for unaccepted terms, and for a real authorisation failure. A client that switches on the status
shows the wrong screen for two of three, and the mistake is invisible in a happy-path test.

The status remains the fallback for responses with no problem body — an edge 404, an HTML 502 —
because those are exactly the cases where there is nothing else to go on.

### No HTTP cache, and a server-time clock

Both are recorded in the spec (`REQ-APP-API-003`, `-004`) and both are decisions rather than
defaults: an OkHttp disk cache would be a second copy of member data outside every wipe path, and
DPoP proof timing cannot use the device clock at Keycloak's 10 s / 15 s tolerances.

## Consequences

**Hilt modules do not ship in this change.** Every class here is constructor-injectable with no
globals — the property `CLAUDE.md` is actually protecting — but the `@Module` wiring lands with
`core:auth`, where there is a graph worth wiring. The logging facade is an `object`; that is the
sanctioned exception, since the same rules mandate a single facade.

**The dependency inventory grows by three approved entries.** OkHttp, kotlinx.serialization and the
coroutines runtime are all in the plan's §7 baseline. None of them stores anything on device, and
this change adds no persistence at all, so the § 25 TDDDG storage analysis is untouched — the first
change that needs it is the token store.

**Version pins follow Android Lint.** `NewerVersionAvailable` runs as an error, so the catalog
carries the newest versions at the time of writing rather than the ones that were current when the
code was drafted. That gate is why the pins moved twice in one sitting.

**What this deliberately leaves out:** authentication, DTO generation, and any Retrofit-style
service interface. The next change is `core:auth` (Keystore-backed token store, DPoP proof factory,
AppAuth login), which is the first consumer of every seam defined here.

## Alternatives considered

*One `core:network` module holding the logger too.* Fewer modules, and it makes the logging facade a
transitive dependency of anything that speaks HTTP — including, eventually, modules that do not.

*Suspending token provider with `runBlocking` in the interceptor.* Reads better at the call site and
blocks an OkHttp dispatcher thread on every request that needs a token, which is every request.

*Classify errors by HTTP status, with the code as a detail.* Simpler, and wrong for the 403 family —
the one place where the app's behaviour differs most.

*Retrofit now.* It belongs with the generated DTOs, not before them; adding it here would fix a
service-interface style before there is a single generated model to shape it around.
