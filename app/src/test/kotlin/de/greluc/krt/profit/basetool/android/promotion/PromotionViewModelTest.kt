/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.promotion

import de.greluc.krt.profit.basetool.android.core.data.PromotionEvaluation
import de.greluc.krt.profit.basetool.android.core.data.PromotionSource
import de.greluc.krt.profit.basetool.android.core.data.PromotionStanding
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The member's own Beförderung record: two reads, two failures, one screen (REQ-APP-PROMO-002). */
@OptIn(ExperimentalCoroutinesApi::class)
class PromotionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `assessments are grouped by category, in the order the officers configured`() =
        runTest(dispatcher) {
            // Re-sorting would present their matrix in an arrangement nobody chose.
            val model =
                PromotionViewModel(
                    source(
                        evaluations =
                            listOf(
                                evaluation("Fliegerisch", "Abfangen"),
                                evaluation("Logistik", "Frachtplanung"),
                                evaluation("Fliegerisch", "Formation"),
                            ),
                    ),
                )
            model.loadOnce()
            advanceUntilIdle()

            val groups = model.state.value.categories
            assertEquals(listOf("Fliegerisch", "Logistik"), groups.map { it.name })
            assertEquals(2, groups.first().evaluations.size)
        }

    @Test
    fun `a failing standings read does not blank the assessments`() =
        runTest(dispatcher) {
            // The half the member came for survives the half that broke.
            val model =
                PromotionViewModel(
                    source(
                        evaluations = listOf(evaluation("Fliegerisch", "Abfangen")),
                        standingsResult = ApiResult.Failure(ApiError.Network(java.io.IOException())),
                    ),
                )
            model.loadOnce()
            advanceUntilIdle()

            val state = model.state.value
            assertEquals(1, state.categories.size)
            assertNull(state.evaluationsError)
            assertNotNull(state.standingsError)
        }

    @Test
    fun `a failing assessments read does not blank the standings`() =
        runTest(dispatcher) {
            val model =
                PromotionViewModel(
                    source(
                        evaluationsResult =
                            ApiResult.Failure(ApiError.Network(java.io.IOException())),
                        standings = listOf(standing(hasRules = true)),
                    ),
                )
            model.loadOnce()
            advanceUntilIdle()

            val state = model.state.value
            assertEquals(1, state.standings.size)
            assertNotNull(state.evaluationsError)
            assertNull(state.standingsError)
        }

    @Test
    fun `a step with no configured rules is kept, because that is an answer`() =
        runTest(dispatcher) {
            // Dropping it would leave the member with no row and no explanation; rendering an empty
            // requirement list would read as "not met", which nobody decided.
            val model = PromotionViewModel(source(standings = listOf(standing(hasRules = false))))
            model.loadOnce()
            advanceUntilIdle()

            val step = model.state.value.standings.single()
            assertTrue(step.checks.isEmpty())
            assertEquals(false, step.hasConfiguredRules)
        }

    @Test
    fun `loading twice reads once, and a refresh reads again`() =
        runTest(dispatcher) {
            val counting = CountingSource()
            val model = PromotionViewModel(counting)

            model.loadOnce()
            advanceUntilIdle()
            model.loadOnce()
            advanceUntilIdle()
            assertEquals(1, counting.reads)

            model.onRefresh()
            advanceUntilIdle()
            assertEquals(2, counting.reads)
        }

    private fun evaluation(
        category: String,
        topic: String,
    ) = PromotionEvaluation(categoryName = category, topicName = topic, level = "LEVEL_B")

    private fun standing(hasRules: Boolean) =
        PromotionStanding(
            fromRank = 2,
            toRank = 3,
            eligible = false,
            hasConfiguredRules = hasRules,
            checks = emptyList(),
        )

    private fun source(
        evaluations: List<PromotionEvaluation> = emptyList(),
        standings: List<PromotionStanding> = emptyList(),
        evaluationsResult: ApiResult<List<PromotionEvaluation>>? = null,
        standingsResult: ApiResult<List<PromotionStanding>>? = null,
    ) = object : PromotionSource {
        override suspend fun evaluations() =
            evaluationsResult ?: ApiResult.Success(evaluations)

        override suspend fun standings() = standingsResult ?: ApiResult.Success(standings)
    }

    /** Counts how often the pair of reads ran. */
    private class CountingSource : PromotionSource {
        var reads: Int = 0

        override suspend fun evaluations(): ApiResult<List<PromotionEvaluation>> {
            reads++
            return ApiResult.Success(emptyList())
        }

        override suspend fun standings(): ApiResult<List<PromotionStanding>> =
            ApiResult.Success(emptyList())
    }
}
