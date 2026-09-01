/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionStructureSource
import de.greluc.krt.profit.basetool.android.core.data.MissionUnitFields
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a unit write carries — and what it must **not** silently drop.
 *
 * `PUT /missions/{id}/units/{unitId}` is a replace: `MissionStructureService.updateMissionUnit`
 * writes the ship type, the ship, the frequency, the responsible member and the note
 * unconditionally, so an omitted one is set to `null`. The app sent the name and the HVU mark
 * alone, which meant renaming a unit wiped all five — every one of them set from the web, gone as
 * the side effect of fixing a typo.
 *
 * That is the kind of defect no error message reports and no test catches by accident, so it is
 * pinned here as an **echo**: what the form does not edit has to come back out of the write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionUnitFieldsTest {
    private val dispatcher = StandardTestDispatcher()

    private var draft = MissionStructureDraft()
    private var detail: MissionDetail? = null

    private companion object {
        /** What the unit carries when the form opens on it, all of it set from the web. */
        val CARRIED =
            MissionUnitFields(
                shipTypeId = "st1",
                shipId = "s1",
                frequency = 121.5,
                responsibleUserId = "u1",
                note = "Vorauskommando",
            )
    }

    /** Records what each unit write actually carried. */
    private class RecordingStructure : MissionStructureSource by NoMissionStructure {
        val updates = mutableListOf<MissionUnitFields>()
        val adds = mutableListOf<MissionUnitFields>()

        override suspend fun addUnit(
            missionId: String,
            name: String,
            highValue: Boolean,
            fields: MissionUnitFields,
        ): ApiResult<MissionDetail> {
            adds.add(fields)
            return ApiResult.Failure(de.greluc.krt.profit.basetool.android.core.network.ApiError.Validation())
        }

        override suspend fun updateUnit(
            missionId: String,
            unitId: String,
            name: String,
            highValue: Boolean,
            version: Long,
            fields: MissionUnitFields,
        ): ApiResult<MissionDetail> {
            updates.add(fields)
            return ApiResult.Failure(de.greluc.krt.profit.basetool.android.core.network.ApiError.Validation())
        }
    }

    private fun structure(
        source: RecordingStructure,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = MissionStructure(
        missionId = "m1",
        structure = source,
        admin = RecordingMissionAdmin(mutableListOf()),
        scope = scope,
        read = { draft to detail },
        write = { d, m ->
            draft = d
            detail = m ?: detail
        },
    )

    @Test
    fun `renaming a unit sends back everything it was carrying`() =
        runTest(dispatcher) {
            val source = RecordingStructure()
            draft =
                MissionStructureDraft(
                    unitName = "Vorhut",
                    editingUnitId = "un1",
                    editingUnitHighValue = true,
                    unitFields = CARRIED,
                )

            structure(source, this).updateUnit("un1", "Vorhut Alpha", highValue = true, version = 3L, fields = CARRIED)
            advanceUntilIdle()

            // The whole set, unchanged. Sending the name alone cleared the ship, the frequency,
            // the responsible member and the note — an unrelated edit destroying four facts.
            assertEquals(CARRIED, source.updates.single())
        }

    @Test
    fun `a new unit carries what was picked for it`() =
        runTest(dispatcher) {
            val source = RecordingStructure()
            draft = MissionStructureDraft(unitName = "Nachhut", unitFields = CARRIED)

            structure(source, this).addUnit()
            advanceUntilIdle()

            assertEquals(CARRIED, source.adds.single())
        }
}
