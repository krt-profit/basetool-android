/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "0.1.0-alpha01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // dev = local test stack + debug trust anchors, prod = production hosts.
    // See docs/ANDROID_APP_DEV_CI.md section 6.
    flavorDimensions += "backend"
    productFlavors {
        create("dev") {
            dimension = "backend"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // The device's OWN loopback, forwarded to the host by `adb reverse tcp:18080
            // tcp:18080`. Not 10.0.2.2, although that is the documented emulator route to the
            // host: measured on this machine, an app socket to 10.0.2.2:18080 times out after 10 s
            // while ICMP answers and the browser loads the same URL — so the app cannot rely on it.
            // adb reverse also matches the loopback redirect the realm already registers, and the
            // issuer must be the address the app calls, or the ID token's `iss` will not match.
            // Plain HTTP: `start-dev` serves no TLS. Cleartext to this one host is permitted by the
            // dev flavour's network security config and by nothing else.
            buildConfigField("String", "OIDC_ISSUER", "\"http://127.0.0.1:18080/realms/iri\"")
            // Registered on the test realm only, per the main repo's
            // scripts/provision-keycloak-mobile-client.py: a custom scheme is claimable by any
            // installed app, and PKCE stops code theft but not the confusion surface.
            buildConfigField("String", "OIDC_REDIRECT_URI", "\"de.kartell.basetool:/oauth2redirect\"")
            // Same value as the redirect on purpose — see the prod flavour below.
            buildConfigField("String", "OIDC_POST_LOGOUT_REDIRECT_URI", "\"de.kartell.basetool:/oauth2redirect\"")
            // The device's OWN loopback again, forwarded by `adb reverse tcp:11261 tcp:11261`, for
            // the same reason the issuer above uses it: a connection to 10.0.2.2 times out on this
            // setup even with the port published on all interfaces. Measured twice, once per
            // service — ping answers, the host's browser loads the URL, and OkHttp still reports
            // SocketTimeoutException after 10 s, which the app can only classify as "offline".
            // The root cause is not established; adb reverse routes around it reliably.
            //
            // The backend keeps its self-signed HTTPS, so this also needs the test stack's CA in the
            // device's user store (REQ-APP-AUTH-011); only Keycloak is plain HTTP locally.
            buildConfigField("String", "API_BASE_URL", "\"https://127.0.0.1:11261\"")
        }
        create("prod") {
            dimension = "backend"
            buildConfigField("String", "OIDC_ISSUER", "\"https://keycloak.profit-base.online/realms/iri\"")
            // A verified App Link: no other app can claim it, because the domain publishes this
            // app's signing-certificate digest in /.well-known/assetlinks.json. The realm registers
            // exactly this one URI in production (provision-keycloak-mobile-client.py).
            buildConfigField("String", "OIDC_REDIRECT_URI", "\"https://profit-base.online/app/callback\"")
            // The client sets post.logout.redirect.uris = "+", which in Keycloak means "the same
            // list as redirectUris". A separate /app/logout would therefore be refused with
            // "Invalid post logout redirect uri" — the two must stay equal, and a test pins that.
            buildConfigField("String", "OIDC_POST_LOGOUT_REDIRECT_URI", "\"https://profit-base.online/app/callback\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.profit-base.online\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The OIDC endpoints differ per flavor and must not be switchable at runtime: a server
        // switcher in a release build is an attacker-visible gift (DEV_CI section 6).
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Without this Robolectric never sees the merged manifest: it falls back to an empty
            // default package, every intent filter resolves to nothing, and a test asserting one
            // would fail for a reason that has nothing to do with the manifest.
            isIncludeAndroidResources = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // checkDependencies pulls :core:designsystem into this run, where its published
        // icon set has no consumer yet — see that module's lint block.
        disable += "UnusedResources"
        // Version-currency checks, disabled because warningsAsErrors turns them into a time bomb:
        // they compare the declared version against whatever is newest UPSTREAM, so a release by
        // Gradle or a library turns every branch red at once, for a change none of them made. That
        // happened on the Gradle 9.7.1 release. Keeping versions current is Dependabot's job here
        // and it does it; a check that fails a build for something no commit caused only teaches
        // people that red is normal.
        disable += "AndroidGradlePluginVersion"
        disable += "GradleDependency"
    }
}

// jvmTarget defaults to android.compileOptions.targetCompatibility with AGP's built-in Kotlin.
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.browser)
    // Pulls androidx.fragment in with it, which is why MainActivity is a FragmentActivity:
    // BiometricPrompt attaches to a fragment manager and has no ComponentActivity overload.
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

    // The only instrumented tests in the project, and they exist for one reason: the trust anchor
    // below cannot be proven anywhere else. Android applies the network security config to the
    // process, so nothing on the JVM or under Robolectric can tell whether a TLS handshake with
    // the test stack actually succeeds.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
