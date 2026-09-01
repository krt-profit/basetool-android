/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.exchange

import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.BoardEntry
import de.greluc.krt.profit.basetool.android.core.data.BoardPage
import de.greluc.krt.profit.basetool.android.core.data.BoardSide
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncEvent
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.MaterialBoardSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialLookup
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
import de.greluc.krt.profit.basetool.android.core.data.ReleasableStock
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The board's rules.
 *
 * The two worth pinning: a toggle replaces one row rather than re-reading the page — the member's
 * scroll position is the whole cost of getting that wrong — and a picked material is dropped the
 * moment the field is edited, because a request addresses its material by id and a typed name has
 * none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialBoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private companion object {
        /** How many pieces the item tests ask for. */
        const val ITEM_PIECES = 3

        /** The amount the edit test types. */
        const val EDITED_AMOUNT = 120.0

        /** Doubles compared to the cent. */
        const val TOLERANCE = 0.001

        /** Two members waiting, which is what makes the withdrawal ask. */
        const val TWO_WAITING = 2

        /**
         * Builds a row.
         *
         * @param id its id.
         * @param mine whether the caller posted it.
         * @param interested whether the caller has pledged.
         * @param side which half.
         * @return the row.
         */
        fun entry(
            id: String,
            mine: Boolean = false,
            interested: Boolean = false,
            side: BoardSide = BoardSide.OFFERS,
        ) = BoardEntry(
            id = id,
            side = side,
            materialName = "Quantainium",
            unitIsPiece = false,
            amount = "240.0",
            quality = 3,
            ownerName = "Vex",
            ownerOrgUnits = listOf("SK VG"),
            postedAt = "2026-08-20T10:00:00Z",
            remark = null,
            interestCount = if (interested) 1 else 0,
            interestedHandles = null,
            viewerInterested = interested,
            mine = mine,
            version = 1,
        )

        /**
         * One catalogue product for the item half.
         *
         * @return the product.
         */
        fun product() =
            BlueprintProduct(
                productKey = "gatling",
                name = "Ballistic Gatling",
                manufacturer = "Klaus & Werner",
                variantCount = 2,
                owned = true,
            )
    }

    /**
     * Answers the board and records what was asked and written.
     *
     * @property offers the offer half.
     * @property requests the request half.
     */
    private class RecordingSource(
        private val offers: List<BoardEntry> = emptyList(),
        private val requests: List<BoardEntry> = emptyList(),
        private val boardFailure: ApiError? = null,
        private val writeFailure: ApiError? = null,
        private val stock: List<ReleasableStock> = emptyList(),
    ) : MaterialBoardSource {
        val requestedSides = mutableListOf<BoardSide>()
        val interestCalls = mutableListOf<Pair<String, Boolean>>()
        val withdrawn = mutableListOf<String>()
        val createdRequests = mutableListOf<String>()
        val createdOffers = mutableListOf<String>()

        override suspend fun board(
            side: BoardSide,
            page: Int,
            pageSize: Int,
        ): ApiResult<BoardPage> {
            requestedSides += side
            boardFailure?.let { return ApiResult.Failure(it) }
            val rows = if (side == BoardSide.OFFERS) offers else requests
            return ApiResult.Success(
                BoardPage(entries = rows, page = page, totalPages = 1, totalElements = rows.size.toLong()),
            )
        }

        override suspend fun setInterest(
            entry: BoardEntry,
            interested: Boolean,
        ): ApiResult<BoardEntry> {
            interestCalls += entry.id to interested
            writeFailure?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(
                entry.copy(
                    viewerInterested = interested,
                    interestCount = if (interested) entry.interestCount + 1 else entry.interestCount - 1,
                ),
            )
        }

        override suspend fun withdraw(entry: BoardEntry): ApiResult<BoardEntry> {
            withdrawn += entry.id
            writeFailure?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(entry)
        }

        override suspend fun releasableStock(): ApiResult<List<ReleasableStock>> =
            ApiResult.Success(stock)

        override suspend fun createOffer(
            inventoryItemId: String,
            amount: Double,
            remark: String?,
        ): ApiResult<Unit> {
            createdOffers += inventoryItemId
            return writeFailure?.let { ApiResult.Failure(it) } ?: ApiResult.Success(Unit)
        }

        override suspend fun createRequest(
            materialId: String,
            amount: Double,
            minQuality: Int?,
            remark: String?,
        ): ApiResult<Unit> {
            createdRequests += materialId
            return writeFailure?.let { ApiResult.Failure(it) } ?: ApiResult.Success(Unit)
        }

        val createdItemOffers = mutableListOf<Pair<String, Int>>()
        val createdItemRequests = mutableListOf<Pair<String, Int>>()
        val updatedOffers = mutableListOf<Triple<String, Double, String?>>()
        val updatedRequests = mutableListOf<Triple<String, Double, Int?>>()
        var products: List<BlueprintProduct> = emptyList()

        override suspend fun searchProducts(query: String): ApiResult<List<BlueprintProduct>> =
            ApiResult.Success(products)

        override suspend fun createItemOffer(
            productKey: String,
            quantity: Int,
            remark: String?,
        ): ApiResult<Unit> {
            createdItemOffers += productKey to quantity
            return writeFailure?.let { ApiResult.Failure(it) } ?: ApiResult.Success(Unit)
        }

        override suspend fun createItemRequest(
            productKey: String,
            quantity: Int,
            minQuality: Int?,
            remark: String?,
        ): ApiResult<Unit> {
            createdItemRequests += productKey to quantity
            return writeFailure?.let { ApiResult.Failure(it) } ?: ApiResult.Success(Unit)
        }

        override suspend fun updateOffer(
            entry: BoardEntry,
            amount: Double,
            remark: String?,
        ): ApiResult<BoardEntry> {
            updatedOffers += Triple(entry.id, amount, remark)
            writeFailure?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(entry.copy(amount = amount.toString(), remark = remark))
        }

        override suspend fun updateRequest(
            entry: BoardEntry,
            amount: Double,
            minQuality: Int?,
            remark: String?,
        ): ApiResult<BoardEntry> {
            updatedRequests += Triple(entry.id, amount, minQuality)
            writeFailure?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(entry.copy(amount = amount.toString(), quality = minQuality))
        }
    }

    @Test
    fun `the item half of a create sheet posts a product key, not a material`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            source.products = listOf(product())
            val model = MaterialBoardViewModel(source, FixedLookup(emptyList()), null)
            model.loadOnce()
            advanceUntilIdle()

            model.onNewRequest()
            model.onRequestEdited { it.copy(kind = BoardKind.ITEM) }
            model.onProductQueryChanged("Gatling")
            advanceUntilIdle()
            model.onProductPicked(product())
            model.onRequestEdited { it.copy(amount = ITEM_PIECES.toString()) }

            assertTrue(model.state.value.sheet.let { it is BoardSheet.NewRequest && it.submittable })
            model.onRequestSubmitted()
            advanceUntilIdle()

            assertEquals(listOf("gatling" to ITEM_PIECES), source.createdItemRequests)
            // The material write is a different endpoint and must not have been touched.
            assertTrue(source.createdRequests.isEmpty())
        }

    @Test
    fun `an item offer names no stock row`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            source.products = listOf(product())
            val model = MaterialBoardViewModel(source, FixedLookup(emptyList()), null)
            model.loadOnce()
            advanceUntilIdle()

            model.onNewOffer()
            advanceUntilIdle()
            model.onOfferEdited { it.copy(kind = BoardKind.ITEM) }
            model.onProductPicked(product())
            model.onOfferEdited { it.copy(amount = "2") }
            model.onOfferSubmitted()
            advanceUntilIdle()

            assertEquals(listOf("gatling" to 2), source.createdItemOffers)
            assertTrue(source.createdOffers.isEmpty())
        }

    @Test
    fun `a typed product that was never picked cannot be sent`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            source.products = listOf(product())
            val model = MaterialBoardViewModel(source, FixedLookup(emptyList()), null)
            model.loadOnce()
            advanceUntilIdle()

            model.onNewRequest()
            model.onRequestEdited { it.copy(kind = BoardKind.ITEM) }
            model.onProductPicked(product())
            // Editing the text after a pick makes the key stale, and the wire needs the key.
            model.onProductQueryChanged("Gatl")
            model.onRequestEdited { it.copy(amount = ITEM_PIECES.toString()) }

            assertFalse(model.state.value.sheet.let { it is BoardSheet.NewRequest && it.submittable })
            model.onRequestSubmitted()
            advanceUntilIdle()
            assertTrue(source.createdItemRequests.isEmpty())
        }

    @Test
    fun `editing an own offer sends the amount and the remark`() =
        runTest(dispatcher) {
            val row = entry("o1", mine = true)
            val source = RecordingSource(offers = listOf(row))
            val model = MaterialBoardViewModel(source, FixedLookup(emptyList()), null)
            model.loadOnce()
            advanceUntilIdle()

            model.onEditEntry(row)
            model.onEntryEdited {
                it.copy(amount = EDITED_AMOUNT.toInt().toString(), remark = "Rest bleibt hier")
            }
            model.onEntrySubmitted()
            advanceUntilIdle()

            // The amount too, against the artboard's "only the remark" — the web edits both and the
            // wire requires the amount.
            assertEquals(1, source.updatedOffers.size)
            assertEquals("o1", source.updatedOffers.first().first)
            assertEquals(EDITED_AMOUNT, source.updatedOffers.first().second, TOLERANCE)
            assertEquals("Rest bleibt hier", source.updatedOffers.first().third)
            assertEquals(BoardSheet.None, model.state.value.sheet)
        }

    @Test
    fun `withdrawing asks only when somebody is waiting`() =
        runTest(dispatcher) {
            val alone = entry("o1", mine = true)
            val source = RecordingSource(offers = listOf(alone))
            val model = MaterialBoardViewModel(source, FixedLookup(emptyList()), null)
            model.loadOnce()
            advanceUntilIdle()

            model.onEditEntry(alone)
            model.onWithdrawRequested()
            advanceUntilIdle()
            assertEquals(listOf("o1"), source.withdrawn)

            val wanted = entry("o2", mine = true, interested = true).copy(interestCount = TWO_WAITING)
            val second = RecordingSource(offers = listOf(wanted))
            val other = MaterialBoardViewModel(second, FixedLookup(emptyList()), null)
            other.loadOnce()
            advanceUntilIdle()

            other.onEditEntry(wanted)
            other.onWithdrawRequested()
            advanceUntilIdle()
            // Nothing withdrawn yet: two members said they can help, so it asks first.
            assertTrue(second.withdrawn.isEmpty())
            assertTrue(other.state.value.sheet.let { it is BoardSheet.EditEntry && it.confirmingWithdrawal })

            other.onWithdraw(wanted)
            advanceUntilIdle()
            assertEquals(listOf("o2"), second.withdrawn)
        }

    /** Answers the catalogue with one fixed match. */
    private class FixedLookup(
        private val options: List<MaterialOption>,
    ) : MaterialLookup {
        var calls = 0

        override suspend fun materials(query: String): ApiResult<PickerPage<MaterialOption>> {
            calls++
            return ApiResult.Success(PickerPage(options))
        }
    }

    /** Records what a screen announced. */
    private class RecordingLiveSync : LiveSyncSource {
        val announced = mutableListOf<Pair<String, Set<String>>>()

        override fun observe(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent> = emptyFlow()

        override suspend fun publish(
            topic: LiveSyncTopic,
            sections: Set<String>,
        ): ApiResult<Unit> {
            announced += topic.toString() to sections
            return ApiResult.Success(Unit)
        }
    }

    private val lookup = FixedLookup(listOf(MaterialOption(id = "m1", name = "Quantainium", unit = "SCU")))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the segment decides which half is read`() =
        runTest(dispatcher) {
            val source = RecordingSource(offers = listOf(entry("o1")))
            val model = MaterialBoardViewModel(source, lookup, null)

            model.loadOnce()
            advanceUntilIdle()
            model.onSideChanged(BoardSide.REQUESTS)
            advanceUntilIdle()

            assertEquals(listOf(BoardSide.OFFERS, BoardSide.REQUESTS), source.requestedSides)
        }

    @Test
    fun `a toggle replaces one row and leaves the rest alone`() =
        runTest(dispatcher) {
            val source = RecordingSource(offers = listOf(entry("o1"), entry("o2")))
            val model = MaterialBoardViewModel(source, lookup, null)
            model.loadOnce()
            advanceUntilIdle()

            model.onSignalToggled(model.state.value.entries.first())
            advanceUntilIdle()

            // One row updated in place. Re-reading the page would scroll a member back to the top
            // on every tap, on a board whose whole interaction is tapping rows.
            assertEquals(listOf(BoardSide.OFFERS), source.requestedSides)
            assertTrue(model.state.value.entries[0].viewerInterested)
            assertFalse(model.state.value.entries[1].viewerInterested)
            assertNull(model.state.value.busyEntryId)
        }

    @Test
    fun `the caller's own row is never offered the toggle`() =
        runTest(dispatcher) {
            val source = RecordingSource(offers = listOf(entry("o1", mine = true)))
            val model = MaterialBoardViewModel(source, lookup, null)
            model.loadOnce()
            advanceUntilIdle()

            model.onSignalToggled(model.state.value.entries.single())
            advanceUntilIdle()

            // The server refuses it; sending it anyway would be an invitation to a 400.
            assertTrue(source.interestCalls.isEmpty())
        }

    @Test
    fun `a withdrawn row leaves the list`() =
        runTest(dispatcher) {
            val source = RecordingSource(offers = listOf(entry("o1", mine = true), entry("o2")))
            val model = MaterialBoardViewModel(source, lookup, null)
            model.loadOnce()
            advanceUntilIdle()

            model.onWithdraw(model.state.value.entries.first())
            advanceUntilIdle()

            // Dropped rather than replaced: a withdrawn row is no longer on the board, and leaving
            // it there would invite the member to withdraw it again.
            assertEquals(listOf("o2"), model.state.value.entries.map { it.id })
        }

    @Test
    fun `editing the material field drops the id that was picked`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val model = MaterialBoardViewModel(source, lookup, null)
            model.loadOnce()
            advanceUntilIdle()
            model.onNewRequest()
            model.onMaterialQueryChanged("Quant")
            advanceUntilIdle()
            model.onMaterialPicked(MaterialOption(id = "m1", name = "Quantainium", unit = "SCU"))

            assertEquals("m1", (model.state.value.sheet as BoardSheet.NewRequest).materialId)

            model.onMaterialQueryChanged("Quantaini")
            advanceUntilIdle()

            // The member is no longer describing what they picked. Submitting the stale id would
            // post a request for a material they did not choose.
            val sheet = model.state.value.sheet as BoardSheet.NewRequest
            assertNull(sheet.materialId)
            assertFalse(sheet.submittable)
        }

    @Test
    fun `a request is published only with a picked material and an amount`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val model = MaterialBoardViewModel(source, lookup, null)
            model.loadOnce()
            advanceUntilIdle()
            model.onNewRequest()
            model.onRequestEdited { it.copy(amount = "100") }

            model.onRequestSubmitted()
            advanceUntilIdle()
            // No material picked yet: nothing is sent rather than a request the server would refuse.
            assertTrue(source.createdRequests.isEmpty())

            model.onMaterialPicked(MaterialOption(id = "m1", name = "Quantainium", unit = "SCU"))
            model.onRequestSubmitted()
            advanceUntilIdle()

            assertEquals(listOf("m1"), source.createdRequests)
            assertTrue(model.state.value.sheet is BoardSheet.None)
        }

    @Test
    fun `a failed create keeps the sheet and what was typed`() =
        runTest(dispatcher) {
            val source = RecordingSource(writeFailure = ApiError.Validation())
            val model = MaterialBoardViewModel(source, lookup, null)
            model.loadOnce()
            advanceUntilIdle()
            model.onNewRequest()
            model.onMaterialPicked(MaterialOption(id = "m1", name = "Quantainium", unit = "SCU"))
            model.onRequestEdited { it.copy(amount = "100", remark = "bald") }

            model.onRequestSubmitted()
            advanceUntilIdle()

            val sheet = model.state.value.sheet as BoardSheet.NewRequest
            assertEquals("bald", sheet.remark)
            assertTrue(model.state.value.error is ApiError.Validation)
        }

    @Test
    fun `a write announces both halves of the room`() =
        runTest(dispatcher) {
            val source = RecordingSource(offers = listOf(entry("o1")))
            val liveSync = RecordingLiveSync()
            val model = MaterialBoardViewModel(source, lookup, null, liveSync)
            model.loadOnce()
            advanceUntilIdle()

            model.onSignalToggled(model.state.value.entries.single())
            advanceUntilIdle()

            // Both sections, always: a member switching segments has to see a change made on the
            // other half, and the frame carries no data so naming one would be cheaper by nothing.
            assertEquals(listOf("materialboard"), liveSync.announced.map { it.first })
            assertEquals(setOf("board", "requests"), liveSync.announced.single().second)
        }
}
