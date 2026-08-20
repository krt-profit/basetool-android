# ADR-0008 — Wire models are generated; API interfaces are not

- **Status:** Accepted
- **Date:** 2026-08-20
- **Deciders:** @greluc
- **Related:** ADR-0001 (which deferred exactly this decision) · `REQ-APP-API-005` ·
  main repo ADR-0136 and `REQ-API-009` (the frozen external contract set)

## Context

`CLAUDE.md` has said from the start that DTOs are generated from the backend's committed
`openapi.json` and that contract drift must fail compilation rather than runtime. Until now nothing
implemented it: the two repositories that exist hand-wrote their DTOs, and ADR-0001 recorded
Retrofit as *"belongs with the generated DTOs, not before them; adding it here would fix a
service-interface style before there is a single generated model to shape it around."*

This is that moment. Phase 2 consumes missions, notifications, hangar, inventory, orders and bank
reads, and hand-writing those DTOs would mean hand-tracking a document with 396 paths and 403
schemas against an app that sits on a member's phone for weeks between releases.

Two questions had to be answered, not one.

**How much of the generator's output to take.** It can emit API interfaces as well as models.

**What the models are actually worth once generated.** A generated model compiles by construction,
which says nothing about whether it decodes.

## Decision

**`:core:contract` holds the vendored document and the models generated from it. Models only. No
Retrofit.**

The generated client was rejected on what it would cost at the call site rather than on taste. The
app's repositories do three things a generated client does not: they classify failures by the
backend's stable problem `code` rather than by HTTP status (ADR-0001 — 403 covers pending approval,
the terms gate and a real authorisation failure, and a status-based client shows the wrong screen
for two of three), they *fold* some refusals into successes (`PENDING_APPROVAL` on the gate read is
the answer, not an error), and they page-walk paginated catalogs. Every one of those would sit on
top of a generated interface as an adapter, so the interface would buy path assembly and add a
dependency. The payload types are the part worth generating, and that is the part taken.

**Three problems stood between "compiles" and "decodes", and each is now solved in the module:**

- **Decimals.** kotlinx.serialization ships no serializer for `java.math.BigDecimal`, so the
  generator marks those properties `@Contextual` — and `@Contextual` describes the property's own
  type, never a type argument. `Map<String, BigDecimal>` therefore fails to compile outright, which
  is how this was found. `KrtDecimal` is a value class carrying its own serializer that reads the
  JSON number's literal text. `Double` would have compiled and been wrong by a cent in a
  double-entry ledger; `String` would have pushed a parse to every call site, and one of them would
  have forgotten.
- **Enums are strict.** kotlinx.serialization throws on a constant it does not know. Adding a
  constant is additive change, which `REQ-API-009` explicitly leaves free — and the app reads one
  of those enums on the **login** path, where a crash is least recoverable. `KrtJson` sets
  `coerceInputValues`, which turns an unknown constant into `null`. That restores exactly the
  tolerance the hand-written DTO had by reading the field as a plain `String`, and
  `ApprovalStatus.fromWire` still maps absent to "not cleared", so the safe outcome is the gate.
- **Spring's `ProblemDetail`.** The document describes it as six untyped fields, so the generator
  emits six `@Contextual kotlin.Any?` properties — a type that compiles and throws when decoded,
  under the same name as the typed envelope `:core:network` already has. A committed
  `openapi-generator-ignore` refuses that one file.

**Three Java types are mapped away**, each a decision rather than a default: `UUID` (327 uses) to
`kotlin.String`, because every id goes into a path segment or a comparison and `kotlin.uuid.Uuid`
is still an experimental opt-in that would be viral across every consumer; bare `number` to
`KrtDecimal`, while `number/format: double` keeps its own mapping and stays a `Double` — the
backend's doubles are ratios, its `BigDecimal`s are money, and flattening the two is the mistake
worth avoiding; `binary`/`file` to `kotlin.String`, one multipart request the app cannot send.

**The generated module is the one place where warnings are not errors** and Lint does not abort.
Nothing in it is written by a person, so a finding there is a message to the generator's authors,
and `allWarningsAsErrors` on generated code would let an upstream template change turn every branch
red for something no commit here caused. Every hand-written module keeps the strictness.

**The document is vendored, not fetched.** A copy in the repository, refreshed by hand alongside the
main repo's `REQ-API-009` change that opens new endpoints to the app, with the source commit
recorded next to it. Fetching at build time would make the build depend on the network and on
whatever the backend's `main` happens to be that minute.

## Consequences

**Drift fails the build — when the copy is refreshed.** That is the honest reach of this decision
and is recorded as such: nothing compares the vendored document against the main repo's, so a
backend change is invisible here until somebody copies the file across. Both repositories are
public, so an automated comparison is reachable, and `REQ-APP-API-005` carries it as the next step
rather than pretending it is done.

**Every generated property is nullable.** The document marks almost nothing `required`, so "absent"
is a state each repository gives a meaning to. The type system does not settle it, and the mapping
functions are where that decision is visible.

**403 model types for an app that uses a few dozen.** R8 strips the unused ones from the APK, and
the alternative — a hand-maintained allow-list of models to generate — was rejected: the generator
does not follow `$ref`s into an allow-list, so every feature slice would pay a build round trip for
a missing transitive model, in exchange for a tidiness the compiler already provides.

**The two existing repositories now decode into generated models**, and their tests passed
unchanged. That was the point of doing it in the same change rather than landing a pipeline nothing
consumes.

**A generator bump has to be read as a contract change, not as a dependency bump.** The models are
build output, so a new generator version can rename a field, change a nullability or restructure an
enum without a single line of this repository changing — and Dependabot opens that as a routine PR.
The check that answers it takes a minute: generate with both versions and diff the output **from
the `package` line down**, ignoring the `@file:Suppress` header the generator rewrites for its own
reasons. If nothing below that line moves, the bump is inert; if something does, it is an API change
arriving through the build file. The first bump (7.14 → 7.24) was inert across all 403 models,
which is the result to expect and not one to assume.
