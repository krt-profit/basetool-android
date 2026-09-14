# ADR-0022 — Kotlin is capped by what the CodeQL extractor can read

- **Status:** Accepted
- **Date:** 2026-09-13
- **Deciders:** @greluc
- **Related:** ADR-0020 (one CodeQL query is filtered out), ADR-0019 (pinning build-time
  transitives), `docs/ANDROID_APP_DEV_CI.md` § 4, `.github/workflows/codeql.yml`,
  `.github/dependabot.yml`

## Context

Dependabot's `chore(deps): Bump kotlin from 2.4.10 to 2.4.20` (#157, merged 2026-09-11 as
7c37002f) took the `Analyze (java-kotlin)` leg of CodeQL down and it has been down since. The last
green analysis of `main` is 5115a934 (2026-09-09); 34567020314, 34743764625 and 34744542737 all
failed, and every feature branch inherits it because the workflow runs on `pull_request` too.

It is not a degraded scan. The **Gradle build step itself exits 1**:

```
FAILURE: Build completed with 3 failures.
   > Kotlin version 2.4.20 is too recent. CodeQL currently supports versions below 2.4.20
Caused by: com.semmle.extractor.java.interceptors.KotlinInterceptor$KotlinVersionTooRecentError
```

**Why a compiler version is a hard ceiling on the analysis.** CodeQL has no Kotlin parser of its
own; its extractor hooks the Kotlin compiler and reads the IR the compiler produces. So the set of
Kotlin versions CodeQL can analyse is the set its extractor was built against, enumerated
literally in `java/kotlin-extractor/versions.bzl` in `github/codeql`. At `codeql-cli/v2.26.4` (the
bundle the pin shipped on the day of this decision) that list ends at **2.4.0**. At
`codeql-cli/v2.27.0` — released 2026-09-09, the newest bundle in existence — it still ends at
**2.4.0**. The ceiling the error reports is the next Kotlin *minor* above the highest entry, which
is why it reads "below 2.4.20".

Two facts decide the rest:

1. **`Analyze (java-kotlin)` is a required status check** in the repository's "Protect main"
   ruleset. A red java-kotlin leg is not only a lost scan, it is every pull request sitting at
   `BLOCKED` — the state that trains people to reach for an admin merge.
2. **Upstream has already fixed it, and we missed the cut by two days.** `github/codeql` commit
   47cc7d81 ("Kotlin: use 2.4.20 GA") landed on `main` on 2026-09-07, after the 2.27.0 release
   branch had been cut. It ships in the next CodeQL release, not in any bundle available today.

So there is no version of "upgrade our way out" available this week, and the gap is real work
merging past a dead SAST gate on an app whose whole job is moving tokens and member data between a
Keystore, a realm and an API — the sentence at the top of `codeql.yml` that explains why the
taint-tracking is there at all.

## Decision

**The catalog's `kotlin` version is held at 2.4.10 until a shipped CodeQL bundle can read
2.4.20**, and `.github/dependabot.yml` ignores `org.jetbrains.kotlin.plugin.*` at `>= 2.4.20` so
the bump cannot quietly re-land.

The exit is a **single three-part change**, and the parts are not independent:

1. Confirm the extractor's ceiling has moved — read it, do not infer it from a release date:
   `gh api "repos/github/codeql/contents/java/kotlin-extractor/versions.bzl?ref=codeql-cli/vX.Y.Z"`
2. Bump the `github/codeql-action` SHA pin in `codeql.yml` to a release carrying that bundle.
3. Raise `kotlin` in the catalog and **delete the Dependabot ignore** in the same commit.

**Update 2026-09-14 — the pin moved, the hold did not.** Dependabot #164 and #165 raised
`github/codeql-action/init` and `/analyze` in `codeql.yml` from v4.37.9 to v4.38.0 (SHA
`b96794f`), joining the `upload-sarif` pin #163 had already moved, so all four call sites sit on
one SHA again. That is **not** step 2 above. `versions.bzl` at `codeql-cli/v2.27.0` was re-read
that day and still ends at **2.4.0**; v4.38.0 is still the newest codeql-action release, and it is
the release whose default bundle *is* 2.27.0 — the one the runner's toolcache was already serving
under the old pin. Steps 1 and 3 remain open and `kotlin` stays at 2.4.10.

Both the catalog and `dependabot.yml` carry that procedure inline, because a pin whose reasoning
lives only in an ADR is a pin nobody re-checks — the same argument ADR-0020 made for putting the
query filter's justification in the config file.

## Consequences

- **Full CodeQL coverage is restored, and it was measured rather than inferred from a green check.**
  The code-scanning API's `rules_count` on the `/language:java-kotlin` analysis reads **239** before
  the break (`c2d1195`, `cf6f035`, `5115a93`), **0** on every commit during it (`7c37002`,
  `f12c27a`, `822bf87`, `c56e07f`), and **239** again on both runs of this branch. Same suite, same
  count. `assembleDevDebug --no-build-cache` — the exact command the workflow runs — also compiles
  clean at 2.4.10 under `allWarningsAsErrors`, and the whole `check` gate is green.

- **A failed analysis still registers an analysis row, and that is the trap.** Each broken commit
  has a `/language:java-kotlin` entry with `results_count: 0` — so the Security tab showed a recent,
  clean-looking analysis throughout, and nothing on the page distinguished "found nothing" from
  "ran nothing". **`rules_count` is the discriminator, `results_count` is not**, and it is readable
  after the fact:

  ```
  gh api "repos/<owner>/<repo>/code-scanning/analyses?ref=refs/heads/main"
  # then: .[] | select(.category=="/language:java-kotlin") | .rules_count
  ```

- **CodeQL analyses exactly what ships.** Both the release build and the scanned build are the same
  compiler. That is a property the rejected init-script alternative below would have given up, and
  it matters more for a security scanner than for any other gate.
- **We do without Kotlin 2.4.20 for one release cycle.** Nothing in this repository was written
  against it: it was four days old, arrived as an automated bump, and the only commits after it
  (`bad6032` docs, `09d05fb`/`b4caf9a` dependency bumps) touch nothing language-level.
- **The KGP/Gradle pairing gap reopens, and it is a known quantity rather than a new risk.** KGP
  2.4.10 states a tested Gradle ceiling of 9.5.0 against this repository's 9.7.1 wrapper; 2.4.20
  had closed that to a single patch. It is the same two-minor gap `main` ran green on from
  2026-08-24 (when the wrapper reached 9.7.1) to 2026-09-11. It is nonetheless the second reason
  this pin is temporary, not a resting place.
- **An ignore entry outlives its reason unless something says so.** This one can only be removed by
  a human reading the comment; nothing in CI will notice when CodeQL catches up. That is stated at
  the entry itself, in the catalog, and here.

## Alternatives rejected

- **Bump `github/codeql-action` and hope.** The cheapest outcome if it worked, and it was checked
  first: v4.38.0 moves the *default* bundle from 2.26.4 to 2.27.0, and 2.27.0's extractor list is
  byte-identical to 2.26.4's where Kotlin is concerned — still ending at 2.4.0.

  Then the run logs settled it beyond argument. **The failing runs were already on 2.27.0.** Both
  the last red run (34744542737) and this fix's green one resolve
  `/opt/hostedtoolcache/CodeQL/2.27.0/x64`: the `ubuntu-latest` image ships a CodeQL bundle in its
  toolcache and the action prefers it over its own pinned default. So the pin bump was not merely
  unavailable — the newest bundle in existence was **already in effect and already failing**, and
  shipping v4.38.0 as "the fix" would have changed one number in a YAML file and nothing else.
- **`build-mode: none` for java-kotlin.** Rejected on this repository's own measurement, recorded
  in `codeql.yml`'s header from the first time it was tried: buildless analysis does not extract
  Kotlin, and with no `.java` file anywhere this produces *"CodeQL could not process any code
  written in Java/Kotlin"* — an empty database. It is worse than the current failure in the way
  that matters: the job goes **green** while analysing nothing, and the required check passes. That
  is the Android Lint baseline rule in a different costume, and it needs an owner decision it does
  not have.
- **Wait for the next CodeQL release.** Two to three weeks (2.26.x shipped roughly fortnightly) of
  no Kotlin SAST and every PR blocked, to avoid a version change that took one line and one build.
- **Keep 2.4.20 and force 2.4.10 onto the CodeQL job alone**, via a Gradle init script overriding
  the buildscript classpath. This is the tempting one and it fails on both counts: the scanner
  would analyse a build nobody ships, and the override is silent when it stops taking — a
  resolution that quietly fails to apply, or a Compose compiler plugin that no longer matches the
  compiler, surfaces as the same red job plus a file to debug. More moving parts, less fidelity,
  for a change that gets reverted next release.
