/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Checks the sources so every DataStore is opened only by [BasetoolApplication], once per process.
 *
 * A second instance on the same file throws wherever the store is first read.
 */
class ProcessStoreOwnershipTest {
    private companion object {
        /**
         * The calls that open a store; any match outside [OWNER] is a defect. Matched as text.
         */
        val OPENERS =
            listOf(
                "ScreenCapturePreference.createStore(",
                "AuthDataStore.create(",
                "PreferenceDataStoreFactory.create",
            )

        /** The one place a store may be opened, plus the files that define the openers. */
        val ALLOWED =
            setOf(
                "BasetoolApplication.kt",
                "AuthContainer.kt",
                "AuthDataStore.kt",
                "ScreenCapturePreference.kt",
                "ActiveOrgUnitStore.kt",
            )

        /** Where a new store belongs, named in the failure so the fix is not a research task. */
        const val OWNER = "BasetoolApplication"
    }

    @Test
    fun `no source outside the application opens a DataStore`() {
        val offenders =
            sequenceOf(File("src/main/kotlin"), File("src/dev/kotlin"), File("src/prod/kotlin"), File("../core"))
                .filter { it.isDirectory }
                .flatMap { it.walkTopDown() }
                .filter { it.isFile && it.extension == "kt" && !it.path.contains("${File.separator}test") }
                .filter { file ->
                    file.name !in ALLOWED && OPENERS.any { file.readText().contains(it) }
                }
                .map { it.name }
                .toList()

        assertEquals(
            "these open a DataStore outside $OWNER; a second instance on one file throws and " +
                "kills the process — hold it on $OWNER and read it from there",
            emptyList<String>(),
            offenders,
        )
    }
}
