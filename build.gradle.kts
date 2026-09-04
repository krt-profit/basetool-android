/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.api.variant.AndroidComponentsExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** JVM target detekt analyses against; see the comment at the task configuration below. */
val DETEKT_JVM_TARGET = "17"

/*
 * Build-time-only security pins — ADR-0019.
 *
 * Nine open Dependabot advisories sit on artifacts this project never declares: BouncyCastle,
 * jose4j, jdom2 and commons-lang3 arrive through AGP, plexus-utils through licensee, handlebars
 * through the OpenAPI generator, and bcprov 1.81 — the one critical — through Robolectric's
 * unit-test classpath. Dependabot cannot propose a fix for any of them, because there is no
 * declared version to bump, and no plugin release moves them: AGP 9.4.0, licensee 1.14.1,
 * openapi-generator 7.25.0 and Robolectric 4.16.1 are each the newest usable release.
 * Overriding the resolved graph is therefore the only mechanism left.
 *
 * CONSTRAINTS, not `force`. A constraint raises a resolution and never lowers one, so a future
 * upstream that already ships higher wins instead of being dragged back. `force` would do the
 * opposite, and an unscoped `force` on project configurations could one day silently DOWNGRADE a
 * BouncyCastle that had legitimately reached the APK.
 *
 * Measured no-op on what ships: the unsigned prodRelease APK is byte-identical (458 entries, none
 * differing) and the 819 generated OpenAPI sources are unchanged. Nothing here reaches
 * `prodReleaseRuntimeClasspath`.
 *
 * These entries are deleted when AGP, licensee and Robolectric ship patched transitives. Nothing
 * in the build will tell you when that day comes.
 */
buildscript {
    val pins = listOf(
        libs.pin.bcprov,
        libs.pin.bcpkix,
        libs.pin.bcutil,
        libs.pin.jose4j,
        libs.pin.jdom2,
        libs.pin.plexus.utils,
        libs.pin.commons.lang3,
        libs.pin.httpclient,
        libs.pin.httpmime,
        libs.pin.handlebars,
    ).map { it.get().toString() }
    dependencies {
        constraints {
            pins.forEach { add("classpath", it) }
        }
        // plexus-utils 4.x EXTRACTED org.codehaus.plexus.util.xml.* into plexus-xml. Maven
        // 3.9.x's maven-model still calls into it, so the artifact has to be ADDED (a constraint
        // would do nothing — it is not in the graph at all).
        add("classpath", libs.pin.plexus.xml.get().toString())
    }
    extra["securityPins"] = pins
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

/*
 * Robolectric's android-all runtime, resolved by Gradle rather than by Robolectric.
 *
 * Robolectric downloads the `android-all` jar for the SDK level under test the first time a test
 * class runs, straight from Maven Central and entirely on its own — outside Gradle's dependency
 * resolution, outside the Gradle cache CI restores, and outside dependency verification once that
 * lands (DEV_CI § 4). When that single request fails, every Robolectric class in the run dies at
 * `classMethod` with a `MavenArtifactFetcher` AssertionError wrapping an IOException, in modules
 * the change never touched. That is a network flake wearing the costume of a real regression, and
 * it cost a diagnosis every time (seen on PR #40).
 *
 * So the jar is declared as a dependency of this configuration, staged into a fixed directory, and
 * handed to Robolectric via its offline resolver. Gradle resolves it once, caches it like every
 * other artifact, and a failure to obtain it now says so at resolution time instead of surfacing
 * as red tests.
 *
 * Deliberately NOT `testImplementation`: `android-all-instrumented` is a complete Android
 * framework jar that Robolectric loads into its own sandbox classloader. On the ordinary test
 * classpath it would shadow the `android.jar` stubs AGP puts there, which is a different and much
 * worse problem than the one being fixed.
 */
val robolectricSdks = configurations.resolvable("robolectricSdks")

dependencies {
    add(robolectricSdks.name, libs.robolectric.android.all.instrumented)
}

/** Where the staged jars live; `robolectric.dependency.dir` points every test task here. */
val robolectricSdkDir = layout.buildDirectory.dir("robolectric-sdks")

/** Hoisted so the lazy block below captures a string instead of reaching back into the catalog. */
val robolectricSdkVersion = libs.versions.robolectricAndroidAll.get()

/**
 * Robolectric's `LocalDependencyResolver` looks for `<artifactId>-<version>.<type>` directly in
 * `robolectric.dependency.dir` — which is exactly the file name Gradle gives the artifact, so a
 * plain copy is all the staging that is needed. `Sync` rather than `Copy` so a version bump does
 * not leave the superseded 150 MB jar behind.
 */
val stageRobolectricSdks = tasks.register<Sync>("stageRobolectricSdks") {
    group = "verification"
    description = "Stages the Robolectric android-all runtime so tests never fetch it at runtime."
    from(robolectricSdks)
    into(robolectricSdkDir)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    // Type-safe accessors are not available for plugins applied inside `subprojects`,
    // hence the explicit `configure<…>` calls.
    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(rootProject.libs.versions.ktlint.get())
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(rootProject.libs.versions.ktlint.get())
        }
    }

    // Scoped to the hand-written sources on purpose: pointing detekt at a whole module
    // directory drags generated build output into its inputs and makes the task depend on
    // resource-processing tasks it has no business waiting for.
    configure<DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = DETEKT_JVM_TARGET
        // The `source` above scopes the plain `detekt` task, but NOT the type-resolving variant
        // tasks: those take their source from the Android variant (`variant.sources.kotlin.all`),
        // which includes generated directories. Without this, `:core:contract:detektMain` reports
        // 406 EmptyClassBlock findings against OpenAPI-generated DTOs — code no commit can fix.
        //
        // A path spec, not an `exclude("**/build/generated/**")` pattern: detekt hands the task a
        // flat FileCollection of .kt files, so every file is its own root and the relative path an
        // ant-style pattern is matched against is the bare file name. The pattern silently matches
        // nothing; only the absolute path carries the information.
        exclude { element -> "/build/generated/" in element.file.invariantSeparatorsPath }
    }

    /*
     * What the type-resolving detekt tasks need in order to resolve `BuildConfig`.
     *
     * `BuildConfig` is generated as JAVA and compiled by javac. AGP hands those sources to the
     * Kotlin compiler, but detekt only ever gets Kotlin sources plus a classpath, so every file
     * reading a `buildConfigField` fails to analyse. detekt reports that only as the one-line
     * "compiler errors found during analysis. This affects accuracy of reporting" — easy to read
     * past, and an analysis degraded in an unknown direction is not something to gate a build on.
     *
     * It has to be `setFrom`, not `from`. The plugin fills `classpath` with a Gradle *convention*
     * (`SharedTasks.kt`: `classpath.conventionCompat(compilation.output.classesDirs, libraries)`),
     * and `from()` on a collection holding a convention REPLACES it rather than adding to it.
     * Measured while getting this wrong: the 92-entry classpath collapsed to 1, and the run went
     * from 40 compiler errors to 14021 while inventing dozens of bogus UnreachableCode findings.
     * So the convention is rebuilt here from the same two sources, plus the javac output.
     *
     * That coupling is the maintenance cost, and detekt is pinned at a 2.x alpha. It is not a
     * silent risk though: a classpath this rebuild got wrong does not go quiet, it produces a flood
     * of nonsense findings, and `detektMain` is on `check`.
     */
    // Deferred until the Android plugin is actually applied: the module build scripts apply it
    // after this `subprojects` block runs, so a direct `findByType` here finds nothing and the
    // fix silently does not happen.
    pluginManager.withPlugin("com.android.base") {
        extensions.findByType(AndroidComponentsExtension::class.java)?.onVariants { variant ->
            val variantName = variant.name.replaceFirstChar { it.uppercase() }
            tasks.withType<Detekt>().matching { it.name == "detekt$variantName" }.configureEach {
                val kotlinCompile = tasks.named("compile${variantName}Kotlin", KotlinJvmCompile::class)
                classpath.setFrom(
                    kotlinCompile.map { it.libraries },
                    kotlinCompile.flatMap { it.destinationDirectory },
                    tasks.named("compile${variantName}JavaWithJavac", JavaCompile::class)
                        .flatMap { it.destinationDirectory },
                )
            }
        }
    }

    tasks.withType<Test>().configureEach {
        dependsOn(stageRobolectricSdks)
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", robolectricSdkDir.get().asFile.absolutePath)
        // The staged path never changes, so the two properties above would not notice a new
        // runtime version. This declares the one thing that actually varies about that directory,
        // without hashing a 150 MB jar on every test task.
        inputs.property("robolectricAndroidAll", robolectricSdkVersion)
    }
}

/*
 * The same pins, applied to every other classpath that resolves one of them — ADR-0019.
 *
 * The `buildscript` block at the top of this file covers the root project's own plugin classpath.
 * These two passes cover the rest: each subproject's buildscript, and the project configurations
 * where the remaining alerted resolutions actually live — `androidLintTool` (commons-lang3,
 * httpclient) and the unit-test buckets (Robolectric's bcprov).
 */
@Suppress("UNCHECKED_CAST")
val securityPins = extra["securityPins"] as List<String>

// `subprojects`, not `allprojects`: the root project's classpath is already constrained in the
// buildscript block above, and constraining it a second time throws "Cannot mutate the
// dependencies of configuration 'classpath' after the configuration was resolved".
subprojects {
    val project = this
    project.buildscript.configurations.configureEach {
        val configurationName = name
        securityPins.forEach { project.buildscript.dependencies.constraints.add(configurationName, it) }
    }
}

// Deliberately broad. Only `androidLintTool` and the unit-test buckets carry an alerted resolution
// today, but this is the form that was measured green end to end — `check`, both assemble tasks, a
// byte-identical release APK and a valid configuration-cache entry. Narrowing it to the two known
// configurations is an unmeasured optimisation of configuration time, not a correctness fix.
allprojects {
    val project = this
    project.configurations.configureEach {
        val configurationName = name
        if (isCanBeDeclared) {
            securityPins.forEach { project.dependencies.constraints.add(configurationName, it) }
        }
    }
}
