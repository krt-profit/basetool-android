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
        abortOnError = false
        checkGeneratedSources = false
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(false)
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("src/main/openapi/openapi.json").asFile.toURI().toString())
    outputDir.set(generatedSources)
    modelPackage.set("de.greluc.krt.profit.basetool.android.core.contract.model")
    apiPackage.set("de.greluc.krt.profit.basetool.android.core.contract.api")
    packageName.set("de.greluc.krt.profit.basetool.android.core.contract")
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false"))
    ignoreFileOverride.set(layout.projectDirectory.file("openapi-generator-ignore").asFile.path)
    typeMappings.set(
        mapOf(
            "UUID" to "kotlin.String",
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
            "dateLibrary" to "string",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/main/kotlin",
        ),
    )
}

abstract class OpenApiSources : DefaultTask() {
    @get:InputDirectory
    abstract val generated: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun mirror() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        generated.get().asFile.copyRecursively(target, overwrite = true)
    }
}

tasks.openApiGenerate.configure {
    inputs
        .file(layout.projectDirectory.file("src/main/openapi/openapi.json"))
        .withPropertyName("openapiSpecFile")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .file(layout.projectDirectory.file("openapi-generator-ignore"))
        .withPropertyName("openapiIgnoreFile")
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
