/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.api.variant.Variant

val signingVariables =
    listOf(
        "KRT_SIGNING_KEYSTORE",
        "KRT_SIGNING_STORE_PASSWORD",
        "KRT_SIGNING_KEY_ALIAS",
        "KRT_SIGNING_KEY_PASSWORD",
    )

val signingEnvironment: Map<String, String?> =
    signingVariables.associateWith { name ->
        providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    }

val signingRequested = signingEnvironment.values.any { it != null }

val missingSigningVariables = signingEnvironment.filterValues { it == null }.keys
check(!signingRequested || missingSigningVariables.isEmpty()) {
    "Release signing is half configured: ${missingSigningVariables.sorted()} " +
        "${if (missingSigningVariables.size == 1) "is" else "are"} missing. " +
        "Set all of $signingVariables, or none of them for an unsigned build."
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.licensee)
}

android {
    namespace = "de.greluc.krt.profit.basetool.android"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "de.greluc.krt.profit.basetool.android"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode = 16
        versionName = "0.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LICENSEE_VERSION", "\"${libs.versions.licensee.get()}\"")
    }

    flavorDimensions += "backend"
    productFlavors {
        create("dev") {
            dimension = "backend"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "OIDC_ISSUER", "\"http://127.0.0.1:18080/auth/realms/iri\"")
            buildConfigField("String", "OIDC_REDIRECT_URI", "\"de.kartell.basetool:/oauth2redirect\"")
            buildConfigField("String", "OIDC_POST_LOGOUT_REDIRECT_URI", "\"de.kartell.basetool:/oauth2redirect\"")
            buildConfigField("String", "API_BASE_URL", "\"https://127.0.0.1:11261\"")
            buildConfigField("String", "WEB_BASE_URL", "\"http://127.0.0.1:18081\"")
        }
        create("prod") {
            dimension = "backend"
            buildConfigField("String", "OIDC_ISSUER", "\"https://profit-base.online/auth/realms/iri\"")
            buildConfigField("String", "OIDC_REDIRECT_URI", "\"https://profit-base.online/app/callback\"")
            buildConfigField("String", "OIDC_POST_LOGOUT_REDIRECT_URI", "\"https://profit-base.online/app/callback\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.profit-base.online\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://profit-base.online\"")
        }
    }

    signingConfigs {
        if (signingRequested) {
            create("release") {
                storeFile = file(signingEnvironment.getValue("KRT_SIGNING_KEYSTORE")!!)
                storePassword = signingEnvironment.getValue("KRT_SIGNING_STORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("KRT_SIGNING_KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("KRT_SIGNING_KEY_PASSWORD")

                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }

        managedDevices {
            allDevices {
                register<com.android.build.api.dsl.ManagedVirtualDevice>("api31") {
                    device = "Pixel 2"
                    sdkVersion =
                        libs.versions.minSdk
                            .get()
                            .toInt()
                    systemImageSource = "aosp"
                }
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable += "UnusedResources"
        disable += "AndroidGradlePluginVersion"
        disable += "GradleDependency"
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(project(":core:contract"))
    androidTestImplementation(project(":core:network"))
}

licensee {
    allow("Apache-2.0")
    allow("BSD-3-Clause")
}

abstract class OssLicensesResource : DefaultTask() {
    @get:InputFile
    abstract val report: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val raw = outputDirectory.get().asFile.resolve("raw")
        raw.mkdirs()
        report.get().asFile.copyTo(raw.resolve("oss_licenses.json"), overwrite = true)
    }
}

tasks.named("check") { dependsOn("detektMain") }

androidComponents {
    beforeVariants(selector().withFlavor("backend", "dev").withBuildType("release")) { variant ->
        variant.enable = false
    }

    onVariants { variant: Variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val generate =
            tasks.register<OssLicensesResource>("generate${name}OssLicenses") {
                description = "Turns the Licensee report for $name into a bundled resource."
                report.set(
                    layout.buildDirectory.file("reports/licensee/android$name/artifacts.json"),
                )
                dependsOn("licenseeAndroid$name")
            }
        variant.sources.res?.addGeneratedSourceDirectory(generate, OssLicensesResource::outputDirectory)
    }
}
