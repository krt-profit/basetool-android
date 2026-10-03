/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.greluc.krt.profit.basetool.android.core.data"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        disable += "AndroidGradlePluginVersion"
        disable += "GradleDependency"
        abortOnError = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<Test>().configureEach {
    inputs
        .file(layout.projectDirectory.file("../contract/app-calls.txt"))
        .withPropertyName("appCallList")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .file(layout.projectDirectory.file("../contract/src/main/openapi/openapi.json"))
        .withPropertyName("openApiDocument")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .dir(layout.projectDirectory.dir("src/main/kotlin"))
        .withPropertyName("callSiteSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .dir(layout.projectDirectory.dir("../../app/src/main/kotlin"))
        .withPropertyName("appSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    api(project(":core:network"))
    api(project(":core:contract"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
