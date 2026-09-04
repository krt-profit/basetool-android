# ADR-0020 — one CodeQL query is filtered out, and the other false positive is fixed at source

- **Status:** Accepted
- **Date:** 2026-09-04
- **Deciders:** @greluc
- **Related:** ADR-0017 (type-resolved detekt joins `check`),
  `docs/ANDROID_APP_DEV_CI.md` § 4, `.github/codeql/codeql-config.yml`

## Context

CodeQL runs `security-and-quality`, and thirty-one of its open findings were on the two *quality*
rules below. Both are false positives in the ordinary sense — no human wrote the code they report —
but they are false in two different ways, and they get two different answers.

**`java/local-variable-is-never-read`, 30 alerts.** Every one names a variable that exists nowhere
in this repository:

```
Variable 'Loading tmp0_other_with_cast' is never read.
```

`tmp0_other_with_cast` is the Kotlin compiler's temporary. Compiling a `data object` synthesises
`equals`, and the IR generator names the cast result `tmp<n>_<hint>` with the hint
`other_with_cast` (`org.jetbrains.kotlin.ir.util.DataClassMembersGenerator$MemberFunctionBuilder`).
A `data object` has no properties, so after the cast there is nothing left to compare: the
temporary is written and never read. The extractor works off that IR and reports the finding at
line 1 column 1 — the position an element with no source location gets, and the tell that the code
is not ours.

All 70 variable names across the 30 alerts matched that shape, and every type named was a
`data object`. Data classes carrying properties were never reported, because there the generated
`equals` does read the temporary. Counting the one alert of this rule that had already closed, it
has produced 31 findings on this repository and every one was compiler-generated. There is no edit
to any source file that satisfies it, short of abandoning `data object` — the idiom 80 declarations
here are built on.

**`java/field-masks-super-field`, 1 alert.** `LoginUiState` in `LoginScreen.kt` was the only
`sealed class` in `app/src/main` and `core/*/src/main`, against 46 sealed interfaces. The Compose
compiler puts a `$stable` marker field on every class it touches, so the parent got one and each
member got one — real shadowing, confirmed with `javap -p`, and generated rather than written. But
unlike the first rule, this one *does* have a source fix: an interface carries no such field.

## Decision

**`java/local-variable-is-never-read` is excluded** in a new `.github/codeql/codeql-config.yml`,
named from the init step's `config-file:` input. The file carries the full reasoning, because a
filter whose justification lives only in an ADR is a filter nobody re-checks.

**`java/field-masks-super-field` is NOT excluded.** `LoginUiState` became a `sealed interface`
instead. The query was right about the shape even though the shadowing was generated, and the rule
stays armed so it catches the next sealed class that reintroduces it.

## Consequences

- Code-scanning alerts 12, 13, 14, 17, 19, 22, 25–37, 39–43 and 46–51 close on the next analysis of
  `main`; alert 15 closes from the source fix. Both are verifiable on the pull request, because
  `codeql.yml` runs on `pull_request`.
- **The exclusion costs real coverage, and this is the honest accounting.** A genuinely unused local
  in hand-written Kotlin now has no gate at all in `core:*`. That was measured, not assumed: under
  K2 `allWarningsAsErrors` does **not** catch it — a file containing `val x = 42` compiles clean
  with `-Werror`, because `UNUSED_VARIABLE` is an IDE inspection under K2 rather than a compiler
  warning. detekt's `style>UnusedVariable` does catch it, but only in the type-resolving tasks,
  which ADR-0017 gates for `:app` and deliberately not for `core:*`. So this widens a gap ADR-0017
  already leaves open and already names a live instance of.
- Weighed against that: 30 permanent note-level findings, plus one more for every screen added, on
  a rule that has never once been right here. A security tab that is permanently noisy is a
  security tab nobody reads — which is the cost the vault's own rule about dismissals is guarding.
- No lint rule enforces "Compose state hierarchies are sealed interfaces, never sealed classes".
  The CodeQL rule itself is that detector, and it is deliberately left armed.
- **Revisit when** the Kotlin extractor stops emitting locals with no source range, or when
  `UnreadLocal.ql` gains a compiler-generated guard — upstream it excludes catch-clause variables,
  Kotlin range for-loop variables and try-with-resources, and nothing else. Nothing in this repo
  will notice when that changes.

### Rejected

- **`security-extended` instead of `security-and-quality`.** A strict downgrade, measured:
  `java-security-and-quality.qls` applies `security-extended-selectors.yml` *plus* a 122-id include
  list. Switching would drop 122 quality queries — among them `java/unreleased-lock`,
  `java/database-resource-leak`, `java/dereferenced-value-is-always-null`,
  `java/inconsistent-equals-and-hashcode` and `java/unsafe-double-checked-locking` — to silence one.
- **Per-alert dismissal.** Dismissals are per-location, so every new `data object` reopens the rule.
  It converts a one-time decision into permanent manual work.
- **Converting the 80 `data object` declarations to plain `object`.** Removes the synthesised
  `equals` and therefore all 30 alerts at zero cost to the analysis surface — and rejected because
  losing `toString()` is a real loss across 80 declarations, and rewriting them to satisfy a query
  is the tail wagging the dog.
- **Inline `// codeql[...]` suppression.** The alerts anchor at line 1 column 1, so it would need
  one comment per file, forever, and would cover no file added later.
- **The `quality-queries` init input.** It adds a quality suite rather than subtracting one query;
  wrong instrument.
