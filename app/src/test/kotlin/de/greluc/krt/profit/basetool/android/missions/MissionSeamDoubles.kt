/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.MissionAdminSource
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionObjectiveKind
import de.greluc.krt.profit.basetool.android.core.data.MissionPage
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionPeopleSource
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.data.MissionStructureSource
import de.greluc.krt.profit.basetool.android.core.data.MissionTimelineSource
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.time.Instant

// The doubles and fixtures the Einsatz-detail tests share.
//
// They were nested in MissionDetailViewModelTest until that class passed detekt's size cap. Shared
// is the better shape anyway: these interfaces are the same ones for every test of that screen, and
// a second copy of a fake is a second place for a signature change to be missed.

/**
 * The Kern patch as the badge's lifecycle sends it.
 *
 * @property name the title echoed back.
 * @property description the briefing echoed back.
 * @property meetingPoint the gathering place echoed back.
 * @property calendarLink the calendar entry echoed back.
 * @property status the new lifecycle status.
 * @property version the Kern counter it was written against.
 */
internal data class CorePatch(
    val name: String,
    val description: String?,
    val meetingPoint: String?,
    val calendarLink: String?,
    val status: String?,
    val version: Long,
)

/**
 * The Verwaltung seam.
 *
 * `patchCore` records rather than refusing: it is the call the status badge makes, and what it
 * carries is the whole point of F2 — a status, and every other Kern field echoed back. It
 * answers a failure so nothing downstream depends on a fabricated Einsatz.
 */
internal class RecordingMissionAdmin(
    private val patches: MutableList<CorePatch>,
) : MissionAdminSource {
    override suspend fun operationOptions(): List<Pair<String, String>> =
        listOf("op1" to "Bergung Hurston")

    override suspend fun patchCore(
        missionId: String,
        name: String,
        description: String?,
        meetingPoint: String?,
        calendarLink: String?,
        status: String?,
        operationId: String?,
        version: Long,
    ): ApiResult<MissionDetail> {
        patches.add(CorePatch(name, description, meetingPoint, calendarLink, status, version))
        return ApiResult.Failure(ApiError.Network(IOException("offline")))
    }

    override suspend fun patchSchedule(
        missionId: String,
        meetingTime: String?,
        plannedStartTime: String?,
        plannedEndTime: String?,
        actualStartTime: String?,
        version: Long,
    ): ApiResult<MissionDetail> = error("the Verwaltung has its own test")

    override suspend fun patchFlags(
        missionId: String,
        internal: Boolean,
        version: Long,
    ): ApiResult<MissionDetail> = error("the Verwaltung has its own test")

    override suspend fun setPartyLead(
        missionId: String,
        userId: String?,
        guestName: String?,
        version: Long,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun addManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun removeManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun addParticipant(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")
}

/** The structure seam; this class exercises the screen around it. */
internal object NoMissionStructure : MissionStructureSource {
    override suspend fun addFrequency(
        missionId: String,
        frequencyTypeId: String,
        value: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun addCustomFrequency(
        missionId: String,
        current: MissionDetail,
        name: String,
        value: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun removeFrequency(
        missionId: String,
        frequencyId: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun addUnit(
        missionId: String,
        name: String,
        highValue: Boolean,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun updateUnit(
        missionId: String,
        unitId: String,
        name: String,
        highValue: Boolean,
        version: Long,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun setCrewRoles(
        missionId: String,
        unitId: String,
        crewId: String,
        jobTypeIds: Set<String>,
        version: Long,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun removeUnit(
        missionId: String,
        unitId: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun addCrew(
        missionId: String,
        unitId: String,
        participantId: String,
        jobTypeIds: Set<String>,
    ): ApiResult<MissionDetail> = error("the structure has its own test")

    override suspend fun removeCrew(
        missionId: String,
        unitId: String,
        crewId: String,
    ): ApiResult<MissionDetail> = error("the structure has its own test")
}

/** Records every Ablauf and Ziele write, so the counter each one echoed can be asserted. */
private val timelineCalls = mutableListOf<Pair<String, Long>>()

/** The Ablauf and Ziele seam. Answers with the Einsatz it was handed, spliced as the real one is. */
internal object NoMissionTimeline : MissionTimelineSource {
    override suspend fun addStep(
        missionId: String,
        current: MissionDetail,
        title: String,
        meta: String?,
    ): ApiResult<MissionDetail> = step("add", current)

    override suspend fun updateStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
        title: String,
        meta: String?,
    ): ApiResult<MissionDetail> = step("update", current)

    override suspend fun toggleStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
        done: Boolean,
    ): ApiResult<MissionDetail> = step("toggle", current)

    override suspend fun removeStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
    ): ApiResult<MissionDetail> = step("remove", current)

    override suspend fun reorderSteps(
        missionId: String,
        current: MissionDetail,
        stepIds: List<String>,
    ): ApiResult<MissionDetail> = step("reorder", current)

    override suspend fun reorderObjectives(
        missionId: String,
        current: MissionDetail,
        objectiveIds: List<String>,
    ): ApiResult<MissionDetail> = objective("reorder", current)

    override suspend fun addObjective(
        missionId: String,
        current: MissionDetail,
        title: String,
        kind: MissionObjectiveKind,
    ): ApiResult<MissionDetail> = objective("add", current)

    override suspend fun updateObjective(
        missionId: String,
        current: MissionDetail,
        objectiveId: String,
        title: String,
        kind: MissionObjectiveKind,
    ): ApiResult<MissionDetail> = objective("update", current)

    override suspend fun removeObjective(
        missionId: String,
        current: MissionDetail,
        objectiveId: String,
    ): ApiResult<MissionDetail> = objective("remove", current)

    private fun step(
        what: String,
        current: MissionDetail,
    ): ApiResult<MissionDetail> {
        timelineCalls.add(what to current.stepsVersion)
        return ApiResult.Success(current.copy(stepsVersion = current.stepsVersion + 1))
    }

    private fun objective(
        what: String,
        current: MissionDetail,
    ): ApiResult<MissionDetail> {
        timelineCalls.add(what to current.objectivesVersion)
        return ApiResult.Success(current.copy(objectivesVersion = current.objectivesVersion + 1))
    }
}

/** What the member picker was asked for, and what it is answered with. */
private val memberQueries = mutableListOf<String>()
private var memberAnswer: ApiResult<PickerPage<MemberOption>> =
    ApiResult.Success(PickerPage(listOf(MemberOption(id = "u9", name = "Rhea"))))

/** The two catalogue lookups. */
internal class RecordingMissionPeople : MissionPeopleSource {
    override suspend fun members(query: String): ApiResult<PickerPage<MemberOption>> {
        memberQueries.add(query)
        return memberAnswer
    }

    override suspend fun crewJobTypes(): ApiResult<List<MissionJobType>> =
        ApiResult.Success(listOf(MissionJobType("c1", "Turret")))
}

/**
 * Answers with whatever is queued and counts what was asked for.
 *
 * @property detailAnswers responses for [detail], the last one repeating once exhausted.
 * @property financeAnswers responses for [finances], likewise.
 */
internal class RecordingSource(
    private val detailAnswers: MutableList<ApiResult<MissionDetail>> = mutableListOf(),
    private val financeAnswers: MutableList<ApiResult<MissionFinances>> = mutableListOf(),
) : MissionSource {
    var detailCalls = 0
    var financeCalls = 0

    fun queueDetail(answer: ApiResult<MissionDetail>) = detailAnswers.add(answer)

    fun queueFinances(answer: ApiResult<MissionFinances>) = financeAnswers.add(answer)

    override suspend fun search(
        query: MissionQuery,
        page: Int,
        pageSize: Int,
    ): ApiResult<MissionPage> = error("the detail screen never searches")

    override suspend fun detail(id: String): ApiResult<MissionDetail> {
        detailCalls++
        return if (detailAnswers.size > 1) detailAnswers.removeAt(0) else detailAnswers.first()
    }

    override suspend fun finances(missionId: String): ApiResult<MissionFinances> {
        financeCalls++
        return if (financeAnswers.size > 1) financeAnswers.removeAt(0) else financeAnswers.first()
    }

    /**
     * The caller's own row as this fake hands it back.
     *
     * Defined on the fake rather than on the test class: a nested class cannot reach the
     * outer one's helpers, and the row is the fake's own answer anyway.
     *
     * @param checkedIn whether it is checked in.
     * @param donating whether the share is donated.
     * @return the row.
     */
    fun row(
        checkedIn: Boolean = false,
        donating: Boolean? = null,
    ) = MissionParticipant(
        id = "p1",
        userId = "u1",
        name = "Rhea",
        role = null,
        checkedIn = checkedIn,
        comment = null,
        donating = donating,
    )

    val joins = mutableListOf<String>()
    val leaves = mutableListOf<Pair<String, String>>()
    val checkIns = mutableListOf<Pair<String, Boolean>>()
    val preferences = mutableListOf<Pair<String, Boolean>>()
    var writeAnswer: ApiResult<MissionParticipant>? = null
    var joinAnswer: ApiResult<MissionDetail>? = null
    var leaveAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

    var jobTypeAnswer: List<MissionJobType> = listOf(MissionJobType("j1", "Pilot"))
    val joinRequests = mutableListOf<Triple<String, String?, Boolean>>()

    override suspend fun jobTypes(): ApiResult<List<MissionJobType>> =
        ApiResult.Success(jobTypeAnswer)

    override suspend fun join(
        missionId: String,
        userId: String,
        desiredJobTypeId: String?,
        donate: Boolean,
    ): ApiResult<MissionDetail> {
        joins.add(missionId)
        joinRequests.add(Triple(userId, desiredJobTypeId, donate))
        return joinAnswer ?: detail(missionId)
    }

    override suspend fun leave(
        missionId: String,
        participantId: String,
    ): ApiResult<Unit> {
        leaves.add(missionId to participantId)
        return leaveAnswer
    }

    override suspend fun setCheckedIn(
        missionId: String,
        participantId: String,
        checkedIn: Boolean,
    ): ApiResult<MissionParticipant> {
        checkIns.add(participantId to checkedIn)
        return writeAnswer ?: ApiResult.Success(row(checkedIn = checkedIn))
    }

    override suspend fun setPlannedFunction(
        missionId: String,
        participant: MissionParticipant,
        jobTypeId: String?,
    ): ApiResult<MissionParticipant> = error("the manager's roster has its own test")

    override suspend fun setDonating(
        missionId: String,
        participantId: String,
        donating: Boolean,
    ): ApiResult<MissionParticipant> {
        preferences.add(participantId to donating)
        return writeAnswer ?: ApiResult.Success(row(donating = donating))
    }

    val booked = mutableListOf<List<Any?>>()
    val rewritten = mutableListOf<List<Any?>>()
    val removed = mutableListOf<String>()
    var bookAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun addFinanceEntry(
        missionId: String,
        participantId: String,
        income: Boolean,
        amount: String,
        note: String?,
    ): ApiResult<Unit> {
        booked.add(listOf(missionId, participantId, income, amount, note))
        return bookAnswer
    }

    override suspend fun updateFinanceEntry(
        entryId: String,
        income: Boolean,
        amount: String,
        note: String?,
        version: Long?,
    ): ApiResult<Unit> {
        rewritten.add(listOf(entryId, income, amount, note, version))
        return bookAnswer
    }

    override suspend fun deleteFinanceEntry(entryId: String): ApiResult<Unit> {
        removed.add(entryId)
        return bookAnswer
    }
}

/**
 * The caller, as the identity read answers.
 *
 * @property answer what to return.
 */
internal class FakeIdentity(
    private val answer: ApiResult<Identity>,
) : IdentitySource {
    override fun forget() = Unit

    override suspend fun myUserId(): ApiResult<String> =
        when (answer) {
            is ApiResult.Failure -> answer
            is ApiResult.Success -> ApiResult.Success(answer.value.userId)
        }

    override suspend fun me(): ApiResult<Identity> = answer
}

internal class FakeConnectivity(
    initial: Boolean = true,
) : Connectivity {
    val state = MutableStateFlow(initial)
    override val online: Flow<Boolean> get() = state
}

/** The Kern counter every lifecycle write must echo back. */
internal const val CORE_VERSION = 7L

/**
 * One Einsatz, with every Kern field filled so an echo that drops one is visible.
 *
 * @param name its title.
 * @param started whether it carries an actual start time.
 * @param canManage whether the reader may write to it.
 * @param status where it stands in its lifecycle.
 * @param roster the participant rows.
 * @return the Einsatz.
 */
internal fun missionDetail(
    name: String = "Vertikaler Abbau",
    started: Boolean = true,
    canManage: Boolean = false,
    status: MissionStatus = MissionStatus.PLANNED,
    vararg roster: MissionParticipant,
) = MissionDetail(
    id = "m1",
    name = name,
    // Filled rather than null so a Kern echo that drops a field is visible in the assertion.
    description = "Briefing",
    status = status,
    rawStatus = status.name,
    meetingTime = null,
    plannedStartTime = null,
    actualStartTime = if (started) Instant.parse("2026-08-23T12:00:00Z") else null,
    plannedEndTime = null,
    isInternal = false,
    meetingPoint = "ARC-L1",
    calendarLink = "https://calendar.example/e",
    operationId = null,
    operationName = null,
    orgUnitName = null,
    orgUnitShorthand = null,
    partyLeadName = null,
    registeredParticipants = roster.size,
    checkedInParticipants = roster.count { it.checkedIn },
    participants = roster.toList(),
    units = emptyList(),
    steps = emptyList(),
    objectives = emptyList(),
    frequencies = emptyList(),
    canManage = canManage,
    coreVersion = CORE_VERSION,
)
