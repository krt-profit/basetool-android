/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.greluc.krt.profit.basetool.android.core.designsystem"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        // Version-currency checks: see app/build.gradle.kts. warningsAsErrors would turn an
        // upstream release into a failing build on every branch at once, for a change no commit
        // made. Dependabot keeps versions current here.
        disable += "AndroidGradlePluginVersion"
        disable += "GradleDependency"
        abortOnError = true
        // The icon set and the Fan Kit artwork are this module's published API: the design
        // handoff defines the complete sprite, and feature modules consume it as they land.
        // "Unused" here means "no consumer yet", not "dead resource".
        disable += "UnusedResources"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// jvmTarget defaults to android.compileOptions.targetCompatibility with AGP's built-in Kotlin.
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    val composeBom = platform(libs.compose.bom)
    api(composeBom)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    // Window size classes drive the phone/tablet split; versioned outside the Compose BOM.
    api(libs.compose.material3.adaptive)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
