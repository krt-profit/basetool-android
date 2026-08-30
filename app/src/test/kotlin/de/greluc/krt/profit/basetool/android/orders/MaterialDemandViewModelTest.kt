/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandGroup
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandRow
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandShare
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * „Materialbedarf" — design ch. 18 §1.
 *
 * The three things worth pinning are the ones the screen is *for*: the working chip must show only
 * what is still open, „Nach Menge" must sort by the outstanding amount rather than by name, and a
 * group whose rows all fall away must disappear with them — a heading over nothing reads as a
 * loading failure. Coverage is checked too, because it is the one figure the app computes itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialDemandViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** „Alle" shows everything, by name. */
    @Test
    fun `all shows every row by name`() =
        runTest(dispatcher) {
            val subject = viewModel(ApiResult.Success(listOf(group())))
            subject.loadOnce()
            advanceUntilIdle()

            assertEquals(
                listOf("Bexalit", "Laranite", "Quantainium"),
                subject.state.value.visible.single().rows.map { it.materialName },
            )
        }

    /** „Ungedeckt" is the working mode: only what still has something open. */
    @Test
    fun `uncovered drops what is already covered`() =
        runTest(dispatcher) {
            val subject = viewModel(ApiResult.Success(listOf(group())))
            subject.loadOnce()
            advanceUntilIdle()

            subject.onFilterChanged(MaterialDemandFilter.UNCOVERED)

            assertEquals(
                listOf("Bexalit", "Laranite"),
                subject.state.value.visible.single().rows.map { it.materialName },
            )
        }

    /** „Nach Menge" sorts by what is open, largest first — not by name. */
    @Test
    fun `by amount sorts on the outstanding amount`() =
        runTest(dispatcher) {
            val subject = viewModel(ApiResult.Success(listOf(group())))
            subject.loadOnce()
            advanceUntilIdle()

            subject.onFilterChanged(MaterialDemandFilter.BY_AMOUNT)

            assertEquals(
                listOf("Laranite", "Bexalit", "Quantainium"),
                subject.state.value.visible.single().rows.map { it.materialName },
            )
        }

    /** A group left with no rows disappears rather than printing its name over nothing. */
    @Test
    fun `a fully covered group is not drawn`() =
        runTest(dispatcher) {
            val covered = MaterialDemandGroup("s2", "Staffel 2", "S2", listOf(row("Agricium", 400.0, 400.0, 0.0)))
            val subject = viewModel(ApiResult.Success(listOf(covered)))
            subject.loadOnce()
            advanceUntilIdle()

            subject.onFilterChanged(MaterialDemandFilter.UNCOVERED)

            assertTrue(subject.state.value.visible.isEmpty())
        }

    /** Coverage is booked plus claimed over required, and never above one. */
    @Test
    fun `coverage counts booked and claimed and stops at one`() {
        assertEquals(
            HALF_COVERED,
            row("Laranite", required = 400.0, booked = 100.0, outstanding = 200.0, claimed = 100.0).coverage,
        )
        assertEquals(1f, row("Bexalit", required = 0.0, booked = 0.0, outstanding = 0.0).coverage)
        assertEquals(1f, row("Titan", required = 100.0, booked = 200.0, outstanding = 0.0).coverage)
    }

    /** The lead line counts what is drawn, not what came back. */
    @Test
    fun `the counts follow the filter`() =
        runTest(dispatcher) {
            val subject = viewModel(ApiResult.Success(listOf(group())))
            subject.loadOnce()
            advanceUntilIdle()

            assertEquals(MATERIALS_IN_GROUP, subject.state.value.materialCount)
            assertEquals(2, subject.state.value.uncoveredCount)

            subject.onFilterChanged(MaterialDemandFilter.UNCOVERED)
            assertEquals(2, subject.state.value.materialCount)
        }

    /** A failure is a state of its own, not an empty list. */
    @Test
    fun `a failure is reported`() =
        runTest(dispatcher) {
            val subject = viewModel(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            subject.loadOnce()
            advanceUntilIdle()

            assertTrue(subject.state.value.phase is MaterialDemandPhase.Failed)
        }

    /** Tapping a row opens it; tapping it again closes it. */
    @Test
    fun `a row toggles open and shut`() =
        runTest(dispatcher) {
            val subject = viewModel(ApiResult.Success(listOf(group())))
            subject.loadOnce()
            advanceUntilIdle()
            val first = subject.state.value.visible.single().rows.first()

            subject.onToggleExpanded(first)
            assertEquals(first.materialId, subject.state.value.expanded)

            subject.onToggleExpanded(first)
            assertEquals(null, subject.state.value.expanded)
        }

    private companion object {
        /** Laranite in the fixture: 400 required, 100 booked and 100 claimed. */
        const val HALF_COVERED = 0.5f

        /** How many materials the fixture's one group carries. */
        const val MATERIALS_IN_GROUP = 3
    }

    /**
     * Builds the view model over one fixed answer.
     *
     * @param answer what the read returns.
     * @return the subject.
     */
    private fun viewModel(answer: ApiResult<List<MaterialDemandGroup>>) =
        MaterialDemandViewModel(
            object : MaterialDemandSource {
                override suspend fun demand(): ApiResult<List<MaterialDemandGroup>> = answer
            },
        )

    /**
     * One org unit with three materials: two still open, one covered.
     *
     * @return the group.
     */
    private fun group() =
        MaterialDemandGroup(
            orgUnitId = "s1",
            orgUnitName = "Staffel 1",
            orgUnitShorthand = "S1",
            rows =
                listOf(
                    row("Quantainium", required = 240.0, booked = 240.0, outstanding = 0.0),
                    row("Laranite", required = 800.0, booked = 180.0, outstanding = 620.0),
                    row("Bexalit", required = 120.0, booked = 40.0, outstanding = 80.0),
                ),
        )

    /**
     * One material's line.
     *
     * @param name what it is called.
     * @param required how much the orders ask for.
     * @param booked how much is already handed over.
     * @param outstanding what is still open, as the server computes it.
     * @param claimed how much is promised and not yet handed over.
     * @return the row.
     */
    private fun row(
        name: String,
        required: Double,
        booked: Double,
        outstanding: Double,
        claimed: Double = 0.0,
    ) = MaterialDemandRow(
        materialId = name.lowercase(),
        materialName = name,
        unit = "SCU",
        qualityRequirement = null,
        required = required,
        booked = booked,
        claimed = claimed,
        outstanding = outstanding,
        orders =
            listOf(
                MaterialDemandShare(
                    jobOrderId = "o1",
                    displayId = "1042",
                    status = JobOrderStatus.OPEN,
                    required = required,
                    booked = booked,
                    claimed = claimed,
                ),
            ),
    )
}
