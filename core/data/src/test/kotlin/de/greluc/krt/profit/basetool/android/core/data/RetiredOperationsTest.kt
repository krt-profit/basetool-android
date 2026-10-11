/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app calls nothing the REST API cut retired (`REQ-APP-API-012`).
 *
 * `retired-operations.txt` is the main repository's list of the operations that answer
 * `410 APP_UPDATE_REQUIRED`; `declared-breaks.txt` is its ledger of every frozen operation the cut
 * removes or changes for app 19. Both are vendored unchanged.
 */
class RetiredOperationsTest {
    private val retired: Set<Operation> = operations(File("../contract/retired-operations.txt").readLines())
    private val ledger: List<String> = File("../contract/declared-breaks.txt").readLines().filter { it.isNotBlank() }
    private val removed: Set<Operation> = operations(ledger.filter { it.endsWith(REMOVED_SUFFIX) })
    private val called: Set<Operation> = operations(File("../contract/app-calls.txt").readLines())
    private val documented: Set<Operation> by lazy { documentedOperations() }

    /**
     * An HTTP verb and a documented path template.
     *
     * @property verb the upper-case verb.
     * @property path the path template.
     */
    private data class Operation(
        val verb: String,
        val path: String,
    )

    @Test
    fun `the app calls no retired operation`() {
        val problems = (called intersect retired).map { "${it.verb} ${it.path}" }
        assertTrue("retired operations still called:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the app calls no operation the ledger removed`() {
        val problems = (called intersect removed).map { "${it.verb} ${it.path}" }
        assertTrue("ledger operations still called:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the vendored document documents no retired operation`() {
        val problems = (documented intersect retired).map { "${it.verb} ${it.path}" }
        assertTrue("retired operations still documented:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every retired operation is in the ledger`() {
        assertTrue("retired but not in the ledger: ${retired - removed}", removed.containsAll(retired))
    }

    @Test
    fun `the only changed shapes in the ledger are the two refinery writes, which the app still calls`() {
        val shapes = ledger.filterNot { it.endsWith(REMOVED_SUFFIX) }.toSet()
        assertEquals(
            setOf(
                "POST /api/v1/refinery-orders request[application/json] 19",
                "PUT /api/v1/refinery-orders/{id} request[application/json] 19",
            ),
            shapes,
        )
        assertTrue(operations(shapes.toList()).all { it in called })
    }

    /**
     * Reads the verb and path of every `VERB PATH …` line.
     *
     * @param lines the lines; blank ones are skipped.
     * @return the operations.
     */
    private fun operations(lines: List<String>): Set<Operation> =
        lines.filter { it.isNotBlank() }.map { line -> line.split(' ').let { Operation(it[0], it[1]) } }.toSet()

    /**
     * Every operation the vendored document documents.
     *
     * @return the operations, with upper-case verbs.
     */
    private fun documentedOperations(): Set<Operation> {
        val document: JsonObject =
            Json.parseToJsonElement(File("../contract/src/main/openapi/openapi.json").readText()).jsonObject
        val paths = document.getValue("paths").jsonObject
        return paths.flatMap { (path, item) ->
            item.jsonObject.keys.filter { it in VERBS }.map { Operation(it.uppercase(), path) }
        }.toSet()
    }

    private companion object {
        const val REMOVED_SUFFIX = " - 19"
        val VERBS = setOf("get", "post", "put", "patch", "delete")
    }
}
