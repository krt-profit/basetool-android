# ADR-0019 — vulnerable build-time transitives are pinned from the root build script

- **Status:** Accepted
- **Date:** 2026-09-04
- **Deciders:** @greluc
- **Related:** ADR-0008 (generated wire models), `docs/ANDROID_APP_SECURITY.md` § 5.2,
  `docs/ANDROID_APP_DEV_CI.md` § 4

## Context

Nine open Dependabot advisories sat on artifacts this project does not declare anywhere. All nine
were attributed to `settings.gradle.kts`, which is the dependency-submission action's build-level
correlator — not a claim that any version is written there.

Measured provenance, resolved with `:buildEnvironment` and `:dependencies` per configuration:

| Advisory | Coordinate | Arrives through |
| --- | --- | --- |
| GHSA-574f-3g2m-x479 (**critical**) | `bcprov-jdk18on` 1.81 | **Robolectric 4.16.1**, unit-test classpath |
| GHSA-c3fc-8qff-9hwx | `bcprov-jdk18on` 1.80.2 | AGP 9.4.0 → sdk-common / builder / apkzlib |
| GHSA-wg6q-6289-32hp | `bcpkix-jdk18on` 1.80.2 | AGP 9.4.0 → the same |
| GHSA-2363-cqg2-863c | `jdom2` 2.0.6 | AGP 9.4.0 → jetifier-processor |
| GHSA-3677-xxcr-wjqv | `jose4j` 0.9.5 | AGP 9.4.0 → bundletool |
| GHSA-j288-q9x7-2f5v | `commons-lang3` 3.16.0 | AGP 9.4.0 → lint-gradle → commons-compress, in `androidLintTool` |
| GHSA-7r82-7xv7-xcpj | `httpclient` 4.5.6 | AGP 9.4.0 → sdklib → httpmime, in `androidLintTool` |
| GHSA-6fmv-xxpf-w3cw | `plexus-utils` 3.6.0 | app.cash.licensee 1.14.1 → maven-model-builder |
| GHSA-r4gv-qr8j-p3pg | `handlebars` 4.3.1 | org.openapi.generator 7.25.0 |

Two facts closed off the ordinary remedies. **Dependabot cannot propose anything**, because there
is no declared version for it to bump — which is why no PR for any of these ever appeared despite
the daily Gradle schedule. And **no plugin bump fixes them**: AGP 9.4.0, licensee 1.14.1,
openapi-generator 7.25.0 are each the newest release, and Robolectric's newest non-beta is 4.16.1
(4.17 exists only as a beta, already measured unusable on this toolchain — `docs/ANDROID_APP_DEV_CI.md`
§ 4).

None of the nine reaches the APK: `prodReleaseRuntimeClasspath` resolves none of these coordinates.
But "build-time only" is not the same as "does not run". bcprov signs the release APK through
apkzlib, commons-lang3 and httpclient run inside Android Lint, and handlebars is loaded by the
generator that writes 819 source files into this repository. Code that runs in CI, in a repository
whose release job holds a self-managed and unrecoverable signing key, is not code to leave
unpatched on the grounds that it is not shipped.

## Decision

**Ten dependency *constraints* are declared in `gradle/libs.versions.toml` and applied from the
root `build.gradle.kts`** — to the root buildscript classpath, to each subproject's buildscript,
and to every declarable project configuration.

**Constraints, not `force`.** A constraint raises a resolution and never lowers one, so the day AGP
or Robolectric ships something higher, the higher version wins instead of being dragged back.
`force` does the opposite: an unscoped `force` on project configurations could one day silently
*downgrade* a BouncyCastle that had legitimately reached the APK. The versions pinned are therefore
deliberately **advisory floors, not the newest releases**, and that is safe only because the
mechanism raises. It is not safe in the other direction — see the amendment in Consequences, where
a major bump past what the consumer supports broke the build on the day this landed.

**The root build script, not `settings.gradle.kts`.** A settings-level pin runs at
`gradle.beforeProject` time, where `VersionCatalogsExtension` does not yet exist, so it cannot read
`libs.versions.toml` and would have to hardcode ten versions outside the catalog. That is a rule
violation with no repair available in that placement.

A tenth entry, `bcutil-jdk18on`, carries no advisory of its own. It exists so the BouncyCastle trio
moves as a set: bcpkix 1.84 meeting bcutil 1.80.2 is a `NoSuchMethodError` at signing time.

## Consequences

- Dependabot alerts 1, 8, 12, 14, 17, 18, 19, 36 and 38 close once `main` re-submits its dependency
  graph. They will still read *open* on the pull request — the `dependency-graph` job in `ci.yml` is
  gated to pushes on `main`, so no PR can show a Dependabot alert closing.
- **What ships is unchanged, measured rather than argued:** the unsigned `prodRelease` APK is
  byte-identical (458 entries, none differing) and the 819 generated OpenAPI sources are unchanged.
- Ten new catalog entries mean Dependabot will now offer to bump them. Patch and minor bumps
  re-run the same gate, which is the point.

  **Amended 2026-09-04, the day this landed.** The sentence above originally ended "Each bump
  re-runs the same gate, which is the point", and that was wrong about major bumps in a way that
  turned `main` red within the hour. Dependabot proposed `plexus-utils` 3.6.2 → **4.1.0**, the bump
  was merged, and the build failed with `NoClassDefFoundError:
  org/codehaus/plexus/util/xml/pull/XmlPullParserException` — plexus-utils 4.x dropped the bundled
  `org.codehaus.plexus.util.xml.pull` package, and app.cash.licensee's maven-model-builder still
  calls into it.

  The gate did work; it simply ran after the merge. The real defect was in the reasoning: **a pin
  closes a security alert and says nothing whatsoever about the API its consumer needs.** Declaring
  these coordinates made them visible to Dependabot for the first time, and a major line is exactly
  where a library drops the API a plugin still calls. All ten pins are therefore closed to major
  bumps in `.github/dependabot.yml` (owner decision, 2026-09-04); patch and minor stay automated.
  3.6.2 already clears GHSA-6fmv-xxpf-w3cw, so nothing was given up by staying on the 3.x line.

  The BouncyCastle side of the same event is the counter-example worth keeping: the shared
  `version.ref` did its job, and one bump moved bcprov, bcpkix and bcutil to 1.85 together.
- The pins sit below the newest available versions. That is only tenable because Lint's
  `GradleDependency` and `AndroidGradlePluginVersion` checks are disabled in `:app` and every
  `core:*`; re-enabling either turns these floors into a red build.
- Root `build.gradle.kts` is covered by neither Spotless nor detekt — both are applied inside
  `subprojects {}`, so `kotlinGradle { target("*.gradle.kts") }` never sees the root scripts. The
  new code is hand-formatted. Widening the formatters to the root scripts is correct and is tracked
  as separate work, because it rewrites files this change does not touch.
- **Deletion condition:** every entry goes away when AGP, licensee and Robolectric ship patched
  transitives. Nothing in the build will tell you when that day arrives; the catalog comment says so.

### Rejected

- **Bumping the plugins.** No release exists that moves any of these. Re-checked 2026-09-04.
- **`dependency-graph-exclude-configurations` on the submission action.** It would close all nine
  alerts by making them undetectable, and it would degrade the release SBOM that `release.yml`
  exports into every release. Hiding a finding is not fixing it.
- **Dismissing all nine as build-time-only.** Legitimate and zero-cost, and rejected because these
  libraries do run — see Context. If pinning is ever backed out, per-alert dismissal with the
  measured justification is the correct fallback, never graph filtering.
- **`force` in `settings.gradle.kts`.** Downgrades, cannot read the version catalog at that point in
  the lifecycle, and its only motive rested on misreading `manifest_path` as a statement about where
  versions are declared.
