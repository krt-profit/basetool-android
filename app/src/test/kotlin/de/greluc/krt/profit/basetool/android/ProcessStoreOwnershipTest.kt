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
 * Every DataStore in this app is opened once per **process**, and only by the application.
 *
 * DataStore refuses a second instance on the same file by throwing, and the throw lands wherever
 * the store is first read — which is never where the mistake was made. The symptom is that the app
 * vanishes to the home screen, so it reads as a crash in whatever screen happened to be opening.
 *
 * This has now happened twice. The token store was moved to [BasetoolApplication] after a language
 * change killed the app; the settings store repeated it and killed the app on a notification tap,
 * because that intent carries `FLAG_ACTIVITY_NEW_TASK`, Navigation rebuilds the task, and the
 * replacement activity opened a second `krt_settings`.
 *
 * Both fixes were correct and neither was pinned, which is why the second one was possible. This
 * test is deliberately about the **class** of defect rather than either instance: it reads the
 * sources and fails if a store is opened anywhere but the application. A future store gets the same
 * guard for free — and the failure message says where to put it.
 */
class ProcessStoreOwnershipTest {
    private companion object {
        /**
         * The calls that open a store. Anything matching these outside [OWNER] is the defect.
         *
         * Matched as text on purpose: the question is "does this source file open a store", which
         * is a syntactic fact, and a reflective check could only see the store that was reached.
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
