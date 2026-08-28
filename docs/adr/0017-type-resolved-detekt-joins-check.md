# ADR-0017 — the type-resolved detekt run joins `check`, for `:app` only

- **Status:** Accepted
- **Date:** 2026-08-28
- **Deciders:** @greluc
- **Related:** ADR-0008 (generated wire models), `docs/ANDROID_APP_DEV_CI.md` § 5

## Context

`./gradlew check` ran detekt, and CI runs `./gradlew build`, so detekt looked gated. It was — but
only the **plain `detekt` task**, which analyses without type resolution. Every rule that needs a
resolved type therefore ran nowhere, except when somebody typed `detektDevDebug` by hand.

Nobody did. Five unused private declarations had accumulated across four screens — `LinkLine`
(dashboard), `isDetailRoute` (navigation), `notificationTitle` (notifications), `JobOrder.parties()`
and `Section` (orders) — none of them reachable, none of them reported by anything that runs.

The gap is exact, and was measured rather than assumed. With one unused private function added to a
source file:

| Task | Result |
| --- | --- |
| `:app:detekt` (what `check` gated) | **passes** |
| `:app:detektMain` (type resolution) | fails, `UnusedPrivateFunction` |

Two things had to be fixed before that second task was fit to gate anything.

**`BuildConfig` could not be resolved — 40 "compiler errors" per run.** `BuildConfig` is generated
as *Java* and compiled by javac; AGP hands those sources to the Kotlin compiler, but detekt only
ever receives Kotlin sources plus a classpath. So every file reading a `buildConfigField` —
`AppOidc`, `AuthContainer`, `MainActivity`, `LicensesScreen` — failed to analyse. detekt says so
only as a single line, *"There were 41 compiler errors found during analysis. This affects accuracy
of reporting"*, which is easy to read past and which leaves the analysis degraded in an unknown
direction.

**The variant tasks analysed generated code.** The root build script scopes detekt to hand-written
sources (`source.setFrom(files("src/main/kotlin", "src/test/kotlin"))`), and that works for the
plain task. The type-resolving tasks ignore it: they take their source from the Android variant
(`variant.sources.kotlin.all`), generated directories included. `:core:contract:detektMain`
reported **406** `EmptyClassBlock` findings against OpenAPI-generated DTOs (ADR-0008) — code no
commit can fix.

## Decision

**`:app:check` depends on `:app:detektMain`.** `detektMain` rather than a single variant task,
because it fans out over all four production variants and so covers the `src/dev` and `src/prod`
flavour source sets as well as `src/main`.

Two build fixes make that run trustworthy. Both live in the root script, so every module benefits
even where the gate does not yet apply:

1. **The detekt classpath is rebuilt to include the javac output.** It has to be `setFrom`, not
   `from`: the plugin fills `classpath` with a Gradle *convention*
   (`SharedTasks.kt`: `classpath.conventionCompat(compilation.output.classesDirs, libraries)`), and
   `from()` on a collection holding a convention **replaces** it instead of adding to it. Measured
   while getting this wrong: the 92-entry classpath collapsed to 1, and the run went from 40
   compiler errors to **14021**, inventing dozens of bogus `UnreachableCode` findings on the way.
   The convention is therefore rebuilt by hand from the same two sources, plus the javac
   destination. The wiring is deferred behind `pluginManager.withPlugin("com.android.base")`,
   because the module scripts apply the Android plugin *after* the root `subprojects` block runs —
   a direct lookup there finds nothing and fails silently.
2. **Generated sources are excluded by a path spec, not an ant pattern.**
   `exclude("**/build/generated/**")` matches nothing here: detekt hands the task a flat
   `FileCollection` of `.kt` files, so every file is its own root and the relative path a pattern is
   matched against is the bare file name. Only the absolute path carries the information.

The three findings this exposed in `:app` are fixed rather than configured away:

- `LongParameterList` on `LazyListScope.entryRows` (9 parameters). The config already exempts
  `@Composable`, and a `LazyListScope` builder cannot carry that annotation — but the exemption's
  stated reason is that *component APIs* are legitimately parameter-rich, and this is an internal
  list builder forwarding seven parameters it does not itself use. They become one named
  `EntryRowContext`, which also closes a KDoc gap: four of the nine were undocumented.
- `NoNameShadowing` in `RefineryViewModel` — `connectivity?.let { source -> … }` shadowed the
  class's own `source`.
- `InjectDispatcher` in `LicensesScreen` — `Dispatchers.Default` becomes a defaulted
  `parseDispatcher` parameter, which is the injection the rule asks for and a seam a test can use.

**Scope: `:app` only.** `:core:auth` and `:core:network` cannot be gated on a type-resolved run
today. They are kotlinx.serialization code; detekt does not load Kotlin compiler plugins (its
`pluginClasspath` is for detekt's own rule sets), so every `.serializer()` and every property of an
`@Serializable` class fails to resolve — 22 and 24 unresolved references respectively. The findings
that fall out are **partly false**: `TokenClient`'s two `UnreachableCode` reports are an expression
whose type could not be inferred, not dead code. Gating on a run that invents findings would teach
people to disbelieve it. `:app` declares no `@Serializable` of its own, resolves with **zero**
compiler errors, and is where the accumulated findings actually were.

## Consequences

- `check` — and therefore CI's `./gradlew build` — runs four type-resolved analyses of `:app`.
  They are incremental, and `build` already compiles all four variants, so the added task
  dependencies cost little beyond detekt's own time.
- A finding is reported once per production variant, so the same line appears four times. That is
  noisier than it is confusing, and collapsing it would mean gating one variant and losing a
  flavour source set.
- **The gate was verified by making it fail**, not by reading the wiring: an unused private function
  added to `LicensesScreen` passed `:app:detekt` and failed `:app:check`. It was then removed.
- The coupling to detekt's classpath convention is a real maintenance cost, and detekt is pinned at
  a 2.x alpha (it must be — 1.23's embedded IntelliJ library cannot parse the JDK 25 version
  string). It is not a *silent* risk: a rebuild that goes wrong does not go quiet, it produces a
  flood of nonsense findings on a task that now gates the build.
- **`detektTest` stays ungated**, and carries 12 findings: five `LongParameterList` on test-data
  builders — the idiomatic pattern, and arguably owed a scoped exemption rather than a refactor —
  four `DoubleMutabilityForCollection`, one `UseOrEmpty`, one `RedundantSuspendModifier`, and one
  `UnusedPrivateFunction` (`MissionDetailViewModelTest.mine`), which is the very class of finding
  this ADR exists to stop. Gating it needs a policy call on test builders first.
- `:core:auth` carries an unused `keyPair` property in `DpopProofFactory` — again the same class,
  found by the run that cannot yet gate it.

## Alternatives considered

- **Gate `detektDevDebug` alone.** Cheaper, one analysis instead of four, but it never looks at
  `src/prod`, so a finding in the production flavour's own sources would still reach `main`.
- **A detekt baseline file.** Would let every module be gated at once by freezing the current
  findings. Rejected on the repo's own terms: baseline files are forbidden without an owner
  decision, and the point here is that findings had accumulated unseen — a baseline is a
  sanctioned way to keep doing that.
- **Relax the rules that fired** (raise `LongParameterList`, disable `InjectDispatcher`). Rejected:
  all three were real, and two of the three fixes improved the code independently of the rule.
- **Pass the kotlinx.serialization compiler plugin through `freeCompilerArgs`** to unblock the core
  modules. Possible in principle — the task does forward free compiler args — but it means pinning
  a compiler-plugin jar path against a plugin that is itself in alpha. Not worth it to gate two
  modules; revisit if detekt gains real compiler-plugin support.
- **Leave it ungated and sweep periodically.** The status quo that produced this ADR.
