/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

/** Where the generator writes; nothing in here is committed or edited by hand. */
val generatedSources = layout.buildDirectory.dir("generated/openapi")

android {
    namespace = "de.greluc.krt.profit.basetool.android.core.contract"
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
        // Deliberately quieter than every other module, and only here. Nothing in this module is
        // written by a person, so a lint finding is a message to the generator's authors rather
        // than to this repository — and `abortOnError` on generated code would let an upstream
        // template change break a build nobody can fix except by pinning a different generator.
        abortOnError = false
        checkGeneratedSources = false
    }
}

kotlin {
    compilerOptions {
        // The one module where warnings are NOT errors. Every other module keeps the flag, and
        // that is the point of the split: hand-written code stays strict, and a deprecation the
        // generator emits does not turn every branch red for a warning no commit here caused.
        allWarningsAsErrors.set(false)
    }
}

// Generates the wire models from the backend's committed OpenAPI document.
//
// MODELS ONLY (`globalProperties = models`). The generator can also emit API interfaces, and they
// are deliberately not taken: the app's repositories classify failures by the backend's stable
// problem `code` rather than by HTTP status (ADR-0001), fold some refusals into successes, and
// page-walk catalogs — none of which a generated client does. What the generation is for is the
// type of the payload, so that a field the server renames stops this build instead of surfacing
// as a blank screen on a member's phone (ADR-0008).
openApiGenerate {
    generatorName.set("kotlin")
    // A file: URI, not a path. The generator parses this as a URI, and a Windows path fails its
    // validation with "Illegal character in opaque part at index 2" — the drive-letter colon.
    inputSpec.set(layout.projectDirectory.file("src/main/openapi/openapi.json").asFile.toURI().toString())
    // A directory, not a path string: `outputDir` became a `DirectoryProperty` in generator
    // 7.24 and no longer accepts the `Provider<String>` that 7.14 wanted.
    outputDir.set(generatedSources)
    modelPackage.set("de.greluc.krt.profit.basetool.android.core.contract.model")
    apiPackage.set("de.greluc.krt.profit.basetool.android.core.contract.api")
    packageName.set("de.greluc.krt.profit.basetool.android.core.contract")
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false"))
    // Committed beside the contract rather than inside the output directory, which is wiped.
    ignoreFileOverride.set(layout.projectDirectory.file("openapi-generator-ignore").asFile.path)
    // Three Java types leak out of the generator's defaults, and each one is a decision:
    //
    //   UUID (327 uses)  -> kotlin.String. Every id the app touches goes into a path segment or a
    //                       comparison; it never does UUID arithmetic. kotlin.uuid.Uuid would be
    //                       the typed alternative and is still an opt-in experimental API whose
    //                       opt-in is viral across every consumer, for a distinction this app
    //                       does not make.
    //   BigDecimal (81)  -> KrtDecimal, which carries its own serializer. Money precision is the
    //                       whole point; see that class for why java.math.BigDecimal cannot be
    //                       used directly.
    //   binary (1)       -> kotlin.String. One multipart import request the app cannot send;
    //                       java.io.File would compile and then have no serializer at runtime.
    typeMappings.set(
        mapOf(
            "UUID" to "kotlin.String",
            // "number", not "BigDecimal": the key is the OPENAPI type, and a bare `type: number`
            // is what the generator turns into java.math.BigDecimal. `type: number, format:
            // double` keeps its own key and stays a Double, which is right — the backend's
            // doubles are ratios and percentages, its BigDecimals are money and quantities, and
            // flattening the two would be the mistake this mapping exists to avoid.
            "number" to "KrtDecimal",
            "binary" to "kotlin.String",
            "file" to "kotlin.String",
        ),
    )
    importMappings.set(
        mapOf("KrtDecimal" to "de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal"),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            // Plain `String` rather than java.time: the wire format is the contract, and mapping
            // it to an instant is a decision for the layer that displays it in the member's zone
            // (REQ-APP-API-004). kotlinx.serialization has no built-in serializer for java.time
            // either, so the alternative is generated code carrying custom serializers this
            // project would have to keep working across generator versions.
            "dateLibrary" to "string",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/main/kotlin",
        ),
    )
}

/**
 * Republishes the generator's output as a directory AGP will accept as a source root.
 *
 * AGP 9 refuses a `Provider`-based `srcDir` outright ("You cannot add Provider instances to the
 * Android SourceSet API") and points at `addGeneratedSourceDirectory`, which needs a task exposing
 * its output as a `DirectoryProperty`. The generator's own `outputDir` is a `Property<String>`, so
 * one task has to stand between the two. The copy is what makes the dependency explicit rather
 * than a directory that appears out of nowhere between builds.
 */
abstract class OpenApiSources : DefaultTask() {
    /** The generator's Kotlin output. */
    @get:InputDirectory
    abstract val generated: DirectoryProperty

    /** The source root handed to the variant. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** Mirrors [generated] into [outputDirectory], dropping anything a previous run left behind. */
    @TaskAction
    fun mirror() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        generated.get().asFile.copyRecursively(target, overwrite = true)
    }
}

// The generator takes its spec as a URI STRING, so Gradle never sees the file -- the task's cache
// key does not include the contract at all. On this machine that is invisible, because a local
// build regenerates anyway; on CI it served a FROM-CACHE result that predated a new endpoint and
// the compile failed on a model that should have existed. Any contract change could have gone the
// same way. Declaring the file as an input is what makes the cache key honest.
tasks.openApiGenerate.configure {
    inputs
        .file(layout.projectDirectory.file("src/main/openapi/openapi.json"))
        .withPropertyName("openapiSpecFile")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

val openApiSources =
    tasks.register<OpenApiSources>("openApiSources") {
        description = "Mirrors the generated wire models into a source root AGP accepts."
        generated.set(generatedSources.map { it.dir("src/main/kotlin") })
        outputDirectory.set(layout.buildDirectory.dir("generated/openapi-sources"))
        dependsOn(tasks.openApiGenerate)
    }

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(openApiSources, OpenApiSources::outputDirectory)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
