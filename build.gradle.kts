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

val DETEKT_JVM_TARGET = "17"

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

val robolectricSdks = configurations.resolvable("robolectricSdks")

dependencies {
    add(robolectricSdks.name, libs.robolectric.android.all.instrumented)
}

val robolectricSdkDir = layout.buildDirectory.dir("robolectric-sdks")

val robolectricSdkVersion = libs.versions.robolectricAndroidAll.get()

val stageRobolectricSdks = tasks.register<Sync>("stageRobolectricSdks") {
    group = "verification"
    description = "Stages the Robolectric android-all runtime so tests never fetch it at runtime."
    from(robolectricSdks)
    into(robolectricSdkDir)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

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

    configure<DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = DETEKT_JVM_TARGET
        exclude { element -> "/build/generated/" in element.file.invariantSeparatorsPath }
    }

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
        inputs.property("robolectricAndroidAll", robolectricSdkVersion)
    }
}

@Suppress("UNCHECKED_CAST")
val securityPins = extra["securityPins"] as List<String>

subprojects {
    val project = this
    project.buildscript.configurations.configureEach {
        val configurationName = name
        securityPins.forEach { project.buildscript.dependencies.constraints.add(configurationName, it) }
    }
}

allprojects {
    val project = this
    project.configurations.configureEach {
        val configurationName = name
        if (isCanBeDeclared) {
            securityPins.forEach { project.dependencies.constraints.add(configurationName, it) }
        }
    }
}
