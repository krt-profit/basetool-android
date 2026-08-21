/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

/** JVM target detekt analyses against; see the comment at the task configuration below. */
val DETEKT_JVM_TARGET = "17"

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

    tasks.withType<Detekt>().configureEach { jvmTarget = DETEKT_JVM_TARGET }

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
