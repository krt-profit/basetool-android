/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that no screen answers a validation refusal with only „Konnte nicht gespeichert werden.".
 *
 * Each file either defers to the server's `fieldErrors` or maps `ApiError.Validation` deliberately.
 * The check is per file, a guard against forgetting rather than a proof.
 */
class WriteErrorWordingTest {
    private companion object {
        /** The generic sentence. A file that renders it is a write-failure site. */
        const val GENERIC = "R.string.write_failed"

        /**
         * The calls that take the server's words: `writeFailureText`, which calls [fieldMessage] and appends status and
         * correlation id, and a bare `fieldMessage()`.
         */
        val DEFERS = listOf("writeFailureText(", "fieldMessage()")

        /** Overruling them on purpose, with a sentence of the screen's own. */
        const val DECIDES = "is ApiError.Validation ->"

        /** Where the screens live. */
        val SOURCES = listOf("src/main/kotlin", "src/dev/kotlin", "src/prod/kotlin")
    }

    /** Every source file that renders the generic write-failure sentence. */
    private fun writeFailureSites(): List<File> =
        SOURCES
            .asSequence()
            .map(::File)
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(GENERIC) }
            .toList()

    @Test
    fun `every write-failure site either takes the server's words or overrules them on purpose`() {
        val offenders =
            writeFailureSites()
                .filterNot { file ->
                    val source = file.readText()
                    DEFERS.any { source.contains(it) } || source.contains(DECIDES)
                }.map { it.name }
                .sorted()

        assertEquals(
            "these let a validation refusal fall through to $GENERIC, which throws away the field " +
                "name and the rule the server already sent — prefer `error.writeFailureText(…)`, or map " +
                "`$DECIDES` to a sentence of this screen's own if it knows better",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the sweep still finds the sites it is meant to guard`() {
        val sites = writeFailureSites()

        assertTrue("no source renders $GENERIC any more — has it been renamed?", sites.isNotEmpty())
    }
}
