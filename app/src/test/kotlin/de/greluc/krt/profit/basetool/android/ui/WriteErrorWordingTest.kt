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
 * No screen answers a validation refusal with „Konnte nicht gespeichert werden." and nothing else.
 *
 * The backend already sends the sentence a member needs — RFC 7807 `fieldErrors` names the field
 * and the rule it broke — and sixteen screens were throwing all of it away for one generic line.
 * Found on the device: adding a frequency to an Einsatz failed, the server had said exactly which
 * value it rejected and why, and the app showed a sentence that named neither.
 *
 * A screen may still overrule the server, and five do: the refusal is *known* there and the
 * screen's own copy carries a remedy the server's cannot — „Die Summe aller Staffeln darf den
 * Bedarf nicht übersteigen", „Schließe die Zuordnung und öffne sie neu". Overruling is a decision,
 * so this test asks that it be *taken*: map `ApiError.Validation` deliberately, or let the server
 * speak. What it forbids is the third case — the `else` branch that swallows a named refusal
 * because nobody thought about it.
 *
 * The check is per **file** rather than per call site, the same granularity `ProcessStoreOwnershipTest`
 * uses: it is a guard against forgetting, not a proof. A file whose two error renderers disagree
 * would pass, and it would still be the only file that could.
 */
class WriteErrorWordingTest {
    private companion object {
        /** The generic sentence. A file that renders it is a write-failure site. */
        const val GENERIC = "R.string.write_failed"

        /**
         * Taking the server's words.
         *
         * `writeFailureText` is the preferred shape and the reason it counts: it calls
         * [fieldMessage] itself and appends the status and correlation id when the server named
         * nothing, so a site that uses it cannot forget either half. The bare `fieldMessage()` still
         * counts — a screen that reads the server's sentence and renders it its own way is doing the
         * thing this test is about.
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
        // Without this, renaming the string resource would disarm the test above rather than fail
        // it: it would sweep zero files and pass, and the next screen would go back to swallowing
        // the server's sentence.
        val sites = writeFailureSites()

        assertTrue("no source renders $GENERIC any more — has it been renamed?", sites.isNotEmpty())
    }
}
