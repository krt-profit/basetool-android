/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncEvent
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The member's own rooms — their hangar and their blueprints — which a connected application's
 * writes reach too (REQ-APP-SYNC-006, main repo REQ-XCH-030).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnLiveSyncRoomTest {
    private companion object {
        const val MY_ID = "0B4C1D2E-0000-4000-8000-00000000A1A1"
    }

    /** A bridge that emits on demand and records what was joined and published. */
    private class RecordingLiveSync : LiveSyncSource {
        private val events = MutableSharedFlow<LiveSyncEvent>(extraBufferCapacity = 8)
        val joined = mutableListOf<Set<LiveSyncTopic>>()
        val published = mutableListOf<Pair<LiveSyncTopic, Set<String>>>()

        override fun observe(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent> {
            joined += topics
            return events
        }

        override suspend fun publish(
            topic: LiveSyncTopic,
            sections: Set<String>,
        ): ApiResult<Unit> {
            published += topic to sections
            return ApiResult.Success(Unit)
        }

        fun emit(
            topic: LiveSyncTopic,
            sections: Set<String>,
        ) {
            events.tryEmit(LiveSyncEvent.Changed(topic, sections))
        }
    }

    /**
     * An identity whose own id is [id], or unreadable when it is `null`.
     *
     * @property id the member's id.
     */
    private class FixedIdentity(
        private val id: String?,
    ) : IdentitySource {
        override suspend fun myUserId(): ApiResult<String> =
            id?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.NotFound())

        override suspend fun me(): ApiResult<Identity> = ApiResult.Failure(ApiError.NotFound())

        override fun forget() = Unit
    }

    private fun TestScope.hangarRoom(
        bridge: LiveSyncSource?,
        id: String?,
        onChanged: (() -> Unit)? = null,
    ) = OwnLiveSyncRoom(
        scope = backgroundScope,
        liveSync = bridge,
        identity = FixedIdentity(id),
        room = LiveSyncTopic::hangar,
        section = LiveSyncSections.HANGAR_SHIPS,
        onChanged = onChanged,
    )

    @Test
    fun `the room is named by the member's own id`() =
        runTest {
            val bridge = RecordingLiveSync()

            hangarRoom(bridge, MY_ID) {}
            runCurrent()

            assertEquals(listOf(setOf(LiveSyncTopic.hangar(MY_ID))), bridge.joined)
            assertEquals("hangar:${MY_ID.lowercase()}", bridge.joined.single().single().wire)
        }

    /** A connected application's ship write arrives as a change of the room and re-reads the list. */
    @Test
    fun `a change of the room's section re-reads, another section does not`() =
        runTest {
            val bridge = RecordingLiveSync()
            var reads = 0
            hangarRoom(bridge, MY_ID) { reads += 1 }
            runCurrent()

            bridge.emit(LiveSyncTopic.hangar(MY_ID), setOf(LiveSyncSections.HANGAR_SHIPS))
            bridge.emit(LiveSyncTopic.hangar(MY_ID), setOf("elsewhere"))
            runCurrent()

            assertEquals(1, reads)
        }

    /** A change that arrived is only re-read; announcing is for the screen's own writes. */
    @Test
    fun `an arriving change is never announced again`() =
        runTest {
            val bridge = RecordingLiveSync()
            hangarRoom(bridge, MY_ID) {}
            runCurrent()

            bridge.emit(LiveSyncTopic.hangar(MY_ID), setOf(LiveSyncSections.HANGAR_SHIPS))
            runCurrent()

            assertTrue(bridge.published.isEmpty())
        }

    @Test
    fun `an own write is announced into the room`() =
        runTest {
            val bridge = RecordingLiveSync()
            val room = hangarRoom(bridge, MY_ID)
            runCurrent()

            room.announce()
            runCurrent()

            assertEquals(
                listOf(LiveSyncTopic.hangar(MY_ID) to setOf(LiveSyncSections.HANGAR_SHIPS)),
                bridge.published,
            )
            assertTrue("a screen that only announces joins nothing", bridge.joined.isEmpty())
        }

    /** Without the member's id there is no room: nothing is joined and nothing announced. */
    @Test
    fun `an unreadable id joins and announces nothing`() =
        runTest {
            val bridge = RecordingLiveSync()
            val room = hangarRoom(bridge, id = null) {}
            runCurrent()

            room.announce()
            runCurrent()

            assertTrue(bridge.joined.isEmpty())
            assertTrue(bridge.published.isEmpty())
        }

    @Test
    fun `a screen built without the bridge still works`() =
        runTest {
            val room = hangarRoom(bridge = null, MY_ID) {}
            runCurrent()

            room.announce()
            runCurrent()
        }

    @Test
    fun `the two own rooms parse back from the wire`() {
        assertEquals(LiveSyncTopic.hangar(MY_ID), LiveSyncTopic.parse("hangar:${MY_ID.lowercase()}"))
        assertEquals(LiveSyncTopic.blueprints(MY_ID), LiveSyncTopic.parse("blueprints:${MY_ID.lowercase()}"))
    }
}
