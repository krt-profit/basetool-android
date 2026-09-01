/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionPeopleSource
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The one picker behind the party lead, the managers and „Teilnehmer hinzufügen". */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionMemberPickerTest {
    private companion object {
        /** Comfortably past the picker's debounce, so a scheduled lookup would have run. */
        const val PAST_THE_DEBOUNCE_MS = 1_000L
    }

    private val dispatcher = StandardTestDispatcher()

    /** Every query that reached the server. */
    private val queries = mutableListOf<String>()

    private var state = MissionMemberPickerState()
    private var picked: Pair<MissionMemberTarget, MemberOption>? = null
    private var answer: ApiResult<PickerPage<MemberOption>> =
        ApiResult.Success(PickerPage(listOf(MemberOption(id = "u9", name = "Rhea"))))

    private fun picker(scope: kotlinx.coroutines.CoroutineScope) =
        MissionMemberPicker(
            source = RecordingSource(),
            scope = scope,
            read = { state },
            write = { state = it },
            onPicked = { target, option -> picked = target to option },
        )

    @Test
    fun `opening primes the list without anything typed`() =
        runTest(dispatcher) {
            picker(this).open(MissionMemberTarget.PARTY_LEAD)
            advanceUntilIdle()

            assertEquals(MissionMemberTarget.PARTY_LEAD, state.target)
            assertEquals(listOf(""), queries)
            assertEquals(1, state.options.size)
        }

    /**
     * Debounced and single-flight. Typing four characters is one search, not four — and, more
     * importantly, a slow answer to „Ma" can never land on top of a fresh answer to „Marc".
     */
    @Test
    fun `typing collapses into one search`() =
        runTest(dispatcher) {
            val subject = picker(this)
            subject.open(MissionMemberTarget.MANAGER)
            advanceUntilIdle()
            queries.clear()

            subject.query("M")
            subject.query("Ma")
            subject.query("Mar")
            subject.query("Marc")
            advanceUntilIdle()

            assertEquals(listOf("Marc"), queries)
        }

    /** A pick closes the picker and hands the caller the target it was opened for. */
    @Test
    fun `a pick reports the target it was opened for`() =
        runTest(dispatcher) {
            val subject = picker(this)
            subject.open(MissionMemberTarget.PARTICIPANT)
            advanceUntilIdle()

            subject.pick(MemberOption(id = "u9", name = "Rhea"))

            assertEquals(MissionMemberTarget.PARTICIPANT to MemberOption("u9", "Rhea"), picked)
            assertFalse("the picker must close on a pick", state.open)
        }

    /**
     * A lookup already in flight when the picker closes must not repopulate it. Without the guard a
     * dismissed picker snaps back open holding whatever the server answered.
     */
    @Test
    fun `an answer arriving after a dismiss is dropped`() =
        runTest(dispatcher) {
            val subject = picker(this)
            subject.open(MissionMemberTarget.MANAGER)

            subject.dismiss()
            advanceUntilIdle()

            assertFalse(state.open)
            assertTrue(state.options.isEmpty())
        }

    /** A refused lookup reads as „no matches", not as a banner over the sheet. */
    @Test
    fun `a refused lookup empties the list rather than shouting`() =
        runTest(dispatcher) {
            answer = ApiResult.Failure(ApiError.Forbidden())
            val subject = picker(this)

            subject.open(MissionMemberTarget.PARTY_LEAD)
            advanceUntilIdle()

            assertTrue(state.open)
            assertTrue(state.options.isEmpty())
            assertFalse(state.searching)
        }

    /** Nothing is typed into a closed picker, and nothing is looked up for one. */
    @Test
    fun `a closed picker ignores typing`() =
        runTest(dispatcher) {
            val subject = picker(this)

            subject.query("Marc")
            advanceTimeBy(PAST_THE_DEBOUNCE_MS)
            advanceUntilIdle()

            assertTrue(queries.isEmpty())
        }

    /** Records what was asked for. */
    private inner class RecordingSource : MissionPeopleSource {
        override suspend fun members(query: String): ApiResult<PickerPage<MemberOption>> {
            queries.add(query)
            return answer
        }

        override suspend fun crewJobTypes(): ApiResult<List<MissionJobType>> =
            ApiResult.Success(listOf(MissionJobType("c1", "Turret")))
    }
}
