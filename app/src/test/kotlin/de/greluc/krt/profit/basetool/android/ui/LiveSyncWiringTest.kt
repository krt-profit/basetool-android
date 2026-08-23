/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.lifecycle.ViewModel
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncEvent
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * How a screen is joined to the live-sync bridge (REQ-APP-SYNC-002, REQ-APP-SYNC-004).
 *
 * The two behaviours pinned here are the ones a screen would otherwise get wrong in a way nobody
 * notices: refreshing on a section it does not care about, and re-announcing a change that arrived
 * from a peer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSyncWiringTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a change hands the screen the sections that moved`() =
        runTest(dispatcher) {
            val bridge = RecordingLiveSync()
            val seen = mutableListOf<Set<String>>()
            val model = TestModel(bridge) { seen += it }
            // The collector is launched, so it is not running yet when the constructor returns.
            // Emitting before it starts would test the SharedFlow's buffer, not the wiring.
            runCurrent()

            bridge.emit(LiveSyncTopic.INVENTORY, setOf(LiveSyncSections.INVENTORY_STOCK))
            runCurrent()

            assertEquals(listOf(setOf(LiveSyncSections.INVENTORY_STOCK)), seen)
            model.close()
        }

    @Test
    fun `the acceptance list is not a change, so a screen does not refresh on connect`() =
        runTest(dispatcher) {
            // Otherwise every reconnect — and the server closes every stream after thirty minutes —
            // would cost every screen a full read for nothing.
            val bridge = RecordingLiveSync()
            val seen = mutableListOf<Set<String>>()
            val model = TestModel(bridge) { seen += it }
            // The collector is launched, so it is not running yet when the constructor returns.
            // Emitting before it starts would test the SharedFlow's buffer, not the wiring.
            runCurrent()

            bridge.emitSubscribed(setOf(LiveSyncTopic.INVENTORY))
            runCurrent()

            assertTrue(seen.isEmpty())
            model.close()
        }

    @Test
    fun `an announcement carries exactly the sections it was given`() =
        runTest(dispatcher) {
            val bridge = RecordingLiveSync()
            val model = TestModel(bridge) {}

            model.announce(LiveSyncSections.INVENTORY_STOCK)
            runCurrent()

            assertEquals(
                listOf(LiveSyncTopic.INVENTORY to setOf(LiveSyncSections.INVENTORY_STOCK)),
                bridge.published,
            )
            model.close()
        }

    @Test
    fun `nothing is announced when nothing changed`() =
        runTest(dispatcher) {
            val bridge = RecordingLiveSync()
            val model = TestModel(bridge) {}

            model.announce()
            runCurrent()

            assertTrue(bridge.published.isEmpty())
            model.close()
        }

    @Test
    fun `a screen built without the bridge still works and never subscribes`() =
        runTest(dispatcher) {
            // Live sync is an enhancement. A preview or a test double must not have to supply one.
            val model = TestModel(liveSync = null) {}

            model.announce(LiveSyncSections.INVENTORY_STOCK)
            runCurrent()

            model.close()
        }

    /** A minimal ViewModel that uses nothing but the two helpers under test. */
    private class TestModel(
        liveSync: LiveSyncSource?,
        onChanged: (Set<String>) -> Unit,
    ) : ViewModel() {
        private val bridge = liveSync

        init {
            observeLiveSync(liveSync, setOf(LiveSyncTopic.INVENTORY), onChanged)
        }

        fun announce(vararg sections: String) {
            publishLiveSync(bridge, LiveSyncTopic.INVENTORY, *sections)
        }

        /**
         * Ends the subscription, which a real screen does by being closed.
         *
         * Without it the collector outlives the test and `runTest` fails the run for the coroutine
         * it left behind — which is itself the proof that the subscription is bound to the screen.
         */
        fun close() {
            onCleared()
        }
    }

    /** A bridge that emits on demand and records what was published to it. */
    private class RecordingLiveSync : LiveSyncSource {
        private val events = MutableSharedFlow<LiveSyncEvent>(extraBufferCapacity = 8)

        val published = mutableListOf<Pair<LiveSyncTopic, Set<String>>>()

        override fun observe(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent> = events

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

        fun emitSubscribed(topics: Set<LiveSyncTopic>) {
            events.tryEmit(LiveSyncEvent.Subscribed(topics))
        }
    }
}
