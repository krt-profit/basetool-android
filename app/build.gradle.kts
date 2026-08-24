/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.api.variant.Variant

/**
 * Names of the four environment variables that carry the release signing material.
 *
 * The key never lives in the repository, in a `keystore.properties`, or in a Gradle property —
 * it arrives as a base64 secret that the workflow decodes to a runner-local file and shreds
 * afterwards (DEV_CI § 4, "Release signing"). Reading it from the environment is what keeps a
 * key out of every file a build could accidentally publish.
 */
val signingVariables =
    listOf(
        "KRT_SIGNING_KEYSTORE",
        "KRT_SIGNING_STORE_PASSWORD",
        "KRT_SIGNING_KEY_ALIAS",
        "KRT_SIGNING_KEY_PASSWORD",
    )

/** The values present in this build's environment; `null` for each one that is not set. */
val signingEnvironment: Map<String, String?> =
    signingVariables.associateWith { name ->
        providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    }

/**
 * Whether a release key was supplied at all.
 *
 * With none of the four set, the release build stays **unsigned** — which is what a contributor's
 * `./gradlew build` and the ordinary CI gate produce, and it is a state the project wants rather
 * than tolerates: nothing on a developer machine or on a PR runner should be able to emit an APK
 * that installs as the real app.
 */
val signingRequested = signingEnvironment.values.any { it != null }

/**
 * A partial configuration is an error, not a fallback.
 *
 * Three of four variables set is exactly how a release day produces an APK nobody can install as
 * an update — AGP would simply leave it unsigned, the workflow would attach it to the release,
 * and the first report would come from a member whose Obtainium refuses it. Failing here costs a
 * red build; the alternative costs a release.
 */
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
            // The web frontend, for the legal pages the app links out to. Forwarded by
            // `adb reverse tcp:18081 tcp:18081` like the two above, and plain HTTP because these
            // open in the BROWSER, which does not share this app's debug trust anchor.
            buildConfigField("String", "WEB_BASE_URL", "\"http://127.0.0.1:18081\"")
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
            // The web frontend. Its /privacy, /impressum and /terms are the SAME documents the web
            // app serves and are reachable without a session, which is what lets the login screen
            // link to them before anyone has signed in.
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

                // v1 is JAR signing, which Android needs only below API 24; the floor is 30
                // (ADR-0006), so switching it off drops the scheme that Janus (CVE-2017-13156)
                // attacks and shortens the APK by a signature nothing reads.
                enableV1Signing = false
                enableV2Signing = true
                // v3 carries the rotation lineage the release strategy depends on: a rotated key
                // is only accepted by Android when the APK's v3 block proves the succession
                // (DEV_CI § 4). Enabling it at the first signed build means the lineage exists
                // before it is ever needed — it cannot be added retroactively to shipped APKs.
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null when no key was supplied, which leaves the APK unsigned rather than falling
            // back to the debug keystore — a debug-signed "release" is the one outcome that looks
            // finished and installs on the wrong lineage.
            signingConfig = signingConfigs.findByName("release")
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

    // Only for AppCompatDelegate.setApplicationLocales: on API 30-32 there is no platform
    // LocaleManager, and this is the backport (see ADR-0007). It arrives transitively anyway,
    // pulled in by androidx.biometric — declaring it makes the version a decision rather than a
    // side effect of another library's dependency graph.
    implementation(libs.androidx.appcompat)
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
    // Compose rendering tests under Robolectric. The same pair `:core:designsystem` already
    // wires; the app module needs it for the screen tests of the feature slices.
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // The instrumented tests, which exist for the properties the JVM cannot see at all. Two so
    // far: Android applies the network security config to the process, so nothing on the JVM or
    // under Robolectric can tell whether a TLS handshake with the test stack actually succeeds --
    // and StrictMode is a runtime facility, so the main-thread rule of REQ-APP-API-006 is
    // unobservable off a device. It took a device walk and a crash to learn the second one.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(project(":core:contract"))
    androidTestImplementation(project(":core:network"))
}

/*
 * The open-source notice, and the supply-chain gate that keeps it honest.
 *
 * `allow` is not a formality: an artifact whose licence is not on this list FAILS the build, and
 * the task runs as part of `check`, so a transitive dependency that arrives under a copyleft or an
 * unknown licence is a red build rather than a silent shipping decision. Adding an identifier here
 * is therefore a deliberate act — read the licence first, and only allow what the app may actually
 * redistribute under GPL-3.0-only.
 *
 * Every identifier listed here must also have a name and a canonical URL in `OssLicense`, because
 * the screen renders one group per licence; `OssLicensesTest` fails when the two lists disagree.
 */
licensee {
    allow("Apache-2.0")
    allow("BSD-3-Clause")
}

/**
 * Copies the Licensee report into the variant's resources as `raw/oss_licenses.json`.
 *
 * A separate task type rather than a `Copy`, because AGP's `addGeneratedSourceDirectory` needs a
 * task that exposes its output as a `DirectoryProperty` — which is also what makes the wiring
 * dependency-correct instead of relying on task ordering.
 */
abstract class OssLicensesResource : DefaultTask() {
    /** The `artifacts.json` Licensee writes for this variant. */
    @get:InputFile
    abstract val report: RegularFileProperty

    /** The generated resource directory handed to AGP. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** Writes the report into `raw/` under the output directory. */
    @TaskAction
    fun generate() {
        val raw = outputDirectory.get().asFile.resolve("raw")
        raw.mkdirs()
        report.get().asFile.copyTo(raw.resolve("oss_licenses.json"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant: Variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val generate =
            tasks.register<OssLicensesResource>("generate${name}OssLicenses") {
                description = "Turns the Licensee report for $name into a bundled resource."
                // Each variant lists its OWN dependencies: a debug build ships the tooling
                // libraries a release build shrinks away, and a notice that names artifacts the
                // binary does not contain is as wrong as one that omits some.
                report.set(
                    layout.buildDirectory.file("reports/licensee/android$name/artifacts.json"),
                )
                dependsOn("licenseeAndroid$name")
            }
        variant.sources.res?.addGeneratedSourceDirectory(generate, OssLicensesResource::outputDirectory)
    }
}
