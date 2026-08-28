/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.MaterialDetailSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialEntryPage
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Lager's tablet pane.
 *
 * The rule with teeth is the **stale answer**: the pane reads one material at a time and a member
 * moves down the tree faster than a page arrives, so a read that lands after the selection moved on
 * must be dropped. Filling the pane with the previous material's entries under the current
 * material's heading is the kind of wrong that looks right.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialPaneLoaderTest {
    private companion object {
        /** How many entries the fixture page claims to hold in total. */
        const val TOTAL = 3L
    }

    private fun entry(id: String) =
        InventoryEntry(
            id = id,
            materialName = "Quantainium",
            materialId = "m1",
            unit = "SCU",
            locationName = "ARC-L1",
            locationId = "l1",
            holder = "Rhea",
            holderId = "u1",
            amount = "442",
            quality = "880",
            personal = false,
            note = null,
            version = 1,
        )

    private fun page(id: String) = MaterialEntryPage(listOf(entry(id)), 0, 1, TOTAL)

    /** Answers each read from a queue, so a test can control what lands and when. */
    private class QueuedSource(
        private val answers: MutableList<ApiResult<MaterialEntryPage>> = mutableListOf(),
    ) : MaterialDetailSource {
        val asked = mutableListOf<Pair<String, Int>>()

        fun push(answer: ApiResult<MaterialEntryPage>) {
            answers += answer
        }

        override suspend fun materialEntries(
            materialId: String,
            page: Int,
        ): ApiResult<MaterialEntryPage> {
            asked += materialId to page
            return answers.removeFirstOrNull()
                ?: ApiResult.Failure(ApiError.Server(status = 500))
        }
    }

    @Test
    fun `selecting a material reads its first page`() =
        runTest {
            val source = QueuedSource().apply { push(ApiResult.Success(page("e1"))) }
            val loader = MaterialPaneLoader(source, this)

            loader.select("m1", "Quantainium", "SCU")
            advanceUntilIdle()

            assertEquals(listOf("m1" to 0), source.asked)
            val pane = loader.state.value
            assertEquals("Quantainium", pane?.name)
            assertEquals("SCU", pane?.unit)
            assertEquals(TOTAL, (pane?.phase as MaterialPanePhase.Ready).page.totalElements)
        }

    @Test
    fun `re-selecting the material already shown does not read again`() =
        runTest {
            // Toggling a group shut and open again is one gesture a member repeats; it must not
            // cost a round trip, and it must not blank the pane they are reading.
            val source = QueuedSource().apply { push(ApiResult.Success(page("e1"))) }
            val loader = MaterialPaneLoader(source, this)

            loader.select("m1", "Quantainium", "SCU")
            advanceUntilIdle()
            loader.select("m1", "Quantainium", "SCU")
            advanceUntilIdle()

            assertEquals(1, source.asked.size)
            assertTrue(loader.state.value?.phase is MaterialPanePhase.Ready)
        }

    @Test
    fun `a failed read leaves the pane on its material, ready to retry`() =
        runTest {
            val source =
                QueuedSource().apply {
                    push(ApiResult.Failure(ApiError.Server(status = 500)))
                    push(ApiResult.Success(page("e1")))
                }
            val loader = MaterialPaneLoader(source, this)

            loader.select("m1", "Quantainium", "SCU")
            advanceUntilIdle()
            assertTrue(loader.state.value?.phase is MaterialPanePhase.Failed)

            loader.retry()
            advanceUntilIdle()

            assertTrue(loader.state.value?.phase is MaterialPanePhase.Ready)
            assertEquals(listOf("m1" to 0, "m1" to 0), source.asked)
        }

    @Test
    fun `an answer for a material the pane has left is dropped`() =
        runTest {
            // The first read is held open while the member moves to another material. When it
            // finally lands it must not overwrite the pane, which is now about something else.
            val held = CompletableDeferred<ApiResult<MaterialEntryPage>>()
            val source =
                object : MaterialDetailSource {
                    var second = false

                    override suspend fun materialEntries(
                        materialId: String,
                        page: Int,
                    ): ApiResult<MaterialEntryPage> =
                        if (materialId == "m1") {
                            held.await()
                        } else {
                            second = true
                            ApiResult.Success(MaterialEntryPage(listOf(entry("e2")), 0, 1, 1))
                        }
                }
            val loader = MaterialPaneLoader(source, this)

            loader.select("m1", "Quantainium", "SCU")
            loader.select("m2", "Laranite", "SCU")
            advanceUntilIdle()
            held.complete(ApiResult.Success(page("e1")))
            advanceUntilIdle()

            val pane = loader.state.value
            assertEquals("m2", pane?.materialId)
            assertEquals("Laranite", pane?.name)
            assertEquals("e2", (pane?.phase as MaterialPanePhase.Ready).page.entries.single().id)
        }

    @Test
    fun `nothing is selected to begin with`() =
        runTest {
            assertNull(MaterialPaneLoader(QueuedSource(), this).state.value)
        }
}
