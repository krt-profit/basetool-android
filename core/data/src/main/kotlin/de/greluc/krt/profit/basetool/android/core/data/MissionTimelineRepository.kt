/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AddMissionObjectiveRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddMissionStepRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionObjectiveDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionStepDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseJobTypeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseUserDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ReorderMissionObjectivesRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.ReorderMissionStepsRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.ToggleMissionStepRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateMissionObjectiveRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateMissionStepRequest
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/** How many rows a picker asks for; the notice says what the cap hid. */
private const val PICKER_SIZE = 25

/** The whole CREW catalogue in one read — it is admin-maintained Stammdaten and small. */
private const val CATALOGUE_SIZE = 200

/**
 * What a Ziel is for.
 *
 * The server's own three-valued enum, kept as a type rather than a string so a write cannot invent
 * a fourth. `MissionObjective.kind` stays a raw string on the read side deliberately — an
 * unrecognised kind must still be shown rather than hidden.
 *
 * @property wire what goes on the wire.
 */
enum class MissionObjectiveKind(
    val wire: String,
) {
    /** What the Einsatz exists for. */
    PRIMARY("PRIMARY"),

    /** Worth doing if the primary allows. */
    SECONDARY("SECONDARY"),

    /** Explicitly not a goal — drawn so nobody pursues it by accident. */
    NON_GOAL("NON_GOAL"),
}

/**
 * The Ablauf and the Ziele, as a manager writes them.
 *
 * > **Every one of these endpoints answers with the LIST, never with the Einsatz.** There is no
 * > plain variant to reach for — `/slim` is all there is — so each method takes the Einsatz as last
 * > read and splices the answer onto it, which keeps the caller's contract identical to the
 * > structure writes.
 * >
 * > That has a second consequence the callers must not have to think about: the answer carries no
 * > new section counter. The server bumps it by exactly one per accepted write
 * > (`bumpStepsVersionIfMatches`: `SET steps_version = steps_version + 1 WHERE steps_version = ?`),
 * > so the splice advances the local counter by one. Leaving it stale would make the *second* edit
 * > in a sitting fail with a `409` the member did nothing to cause.
 */
interface MissionTimelineSource {
    /**
     * Appends one Ablauf step.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read, for its `stepsVersion` and everything the slim
     *   answer does not carry.
     * @param title what happens.
     * @param meta the time-and-place line beneath it, or `null`.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addStep(
        missionId: String,
        current: MissionDetail,
        title: String,
        meta: String?,
    ): ApiResult<MissionDetail>

    /**
     * Rewrites one Ablauf step.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param stepId which step.
     * @param title what happens.
     * @param meta the line beneath it, or `null` to clear it.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun updateStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
        title: String,
        meta: String?,
    ): ApiResult<MissionDetail>

    /**
     * Ticks one Ablauf step off, or back on.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param stepId which step.
     * @param done the state it is to be in — sent explicitly rather than as a toggle, so two
     *   managers tapping at once converge instead of cancelling each other out.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun toggleStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
        done: Boolean,
    ): ApiResult<MissionDetail>

    /**
     * Removes one Ablauf step.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param stepId which step.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun removeStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
    ): ApiResult<MissionDetail>

    /**
     * Reorders the whole Ablauf.
     *
     * The request carries **every** id in the order they are to hold — the server rejects a set
     * that is not exactly the Einsatz's own steps, which is what keeps a reorder from silently
     * dropping a step somebody else added while this screen was open.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param stepIds every step id, in the new order.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun reorderSteps(
        missionId: String,
        current: MissionDetail,
        stepIds: List<String>,
    ): ApiResult<MissionDetail>

    /**
     * Reorders the whole Ziele list, under the same whole-set rule.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param objectiveIds every Ziel id, in the new order.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun reorderObjectives(
        missionId: String,
        current: MissionDetail,
        objectiveIds: List<String>,
    ): ApiResult<MissionDetail>

    /**
     * Appends one Ziel.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read, for its `objectivesVersion`.
     * @param title what is to be achieved.
     * @param kind whether it is primary, secondary, or explicitly not a goal.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addObjective(
        missionId: String,
        current: MissionDetail,
        title: String,
        kind: MissionObjectiveKind,
    ): ApiResult<MissionDetail>

    /**
     * Rewrites one Ziel.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param objectiveId which Ziel.
     * @param title what is to be achieved.
     * @param kind its classification.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun updateObjective(
        missionId: String,
        current: MissionDetail,
        objectiveId: String,
        title: String,
        kind: MissionObjectiveKind,
    ): ApiResult<MissionDetail>

    /**
     * Removes one Ziel.
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read.
     * @param objectiveId which Ziel.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun removeObjective(
        missionId: String,
        current: MissionDetail,
        objectiveId: String,
    ): ApiResult<MissionDetail>
}

/**
 * The two lookups a manager's pickers need: who exists, and what a crew slot can be.
 *
 * Its own interface rather than three more methods on [MissionStructureSource], which is at the
 * cap; and the split is honest anyway — these read catalogues, they write nothing.
 */
interface MissionPeopleSource {
    /**
     * Members whose name matches, for the party-lead, manager and „Teilnehmer hinzufügen" pickers.
     *
     * @param query what was typed; blank returns the first page unfiltered.
     * @return at most [PICKER_SIZE] matches, or the classified failure. The caller states the cap
     *   in the picker's notice — a filtered list must always say what it is hiding.
     */
    suspend fun members(query: String): ApiResult<List<MemberOption>>

    /**
     * The **CREW** Funktionen: the roles somebody holds aboard an Einheit.
     *
     * > The second catalogue, and it shares its names with the first. `job_type.archetype` is
     * > `MISSION` or `CREW`; a participant's Funktion must be a `MISSION` type and a crew role must
     * > be a `CREW` one. Reading either unfiltered offers the wrong names and the backend refuses
     * > the write with *"is not of archetype …"* — a `400` that looks right on screen and fails only
     * > on save, because the two sets read identically.
     *
     * @return the catalogue, or the classified failure.
     */
    suspend fun crewJobTypes(): ApiResult<List<MissionJobType>>
}

/**
 * The Ablauf, the Ziele and the two lookups, over HTTP.
 *
 * @property reader the shared API reader.
 */
class MissionTimelineRepository(
    private val reader: ApiReader,
) : MissionTimelineSource,
    MissionPeopleSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client, so the bearer, the correlation id and the org pin are
     *   already on every request.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "MissionTimeline"),
    )

    override suspend fun addStep(
        missionId: String,
        current: MissionDetail,
        title: String,
        meta: String?,
    ): ApiResult<MissionDetail> =
        withSteps(
            current,
            reader.post(
                "${missionPath(missionId)}/steps/slim",
                AddMissionStepRequest(title = title, stepsVersion = current.stepsVersion, meta = meta),
                AddMissionStepRequest.serializer(),
                ListSerializer(MissionStepDto.serializer()),
            ),
        )

    override suspend fun updateStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
        title: String,
        meta: String?,
    ): ApiResult<MissionDetail> =
        withSteps(
            current,
            reader.put(
                "${missionPath(missionId)}/steps/$stepId/slim",
                UpdateMissionStepRequest(title = title, stepsVersion = current.stepsVersion, meta = meta),
                UpdateMissionStepRequest.serializer(),
                ListSerializer(MissionStepDto.serializer()),
            ),
        )

    override suspend fun toggleStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
        done: Boolean,
    ): ApiResult<MissionDetail> =
        withSteps(
            current,
            // PATCH has no named method on the reader; `send` is the sanctioned escape hatch for
            // exactly the verbs `post`/`put`/`delete` do not cover.
            reader.send(
                "${missionPath(missionId)}/steps/$stepId/done/slim",
                "PATCH",
                ToggleMissionStepRequest(done = done, stepsVersion = current.stepsVersion),
                ToggleMissionStepRequest.serializer(),
                ListSerializer(MissionStepDto.serializer()),
            ),
        )

    override suspend fun removeStep(
        missionId: String,
        current: MissionDetail,
        stepId: String,
    ): ApiResult<MissionDetail> =
        withSteps(
            current,
            reader.delete(
                "${missionPath(missionId)}/steps/$stepId/slim",
                listOf("stepsVersion" to current.stepsVersion.toString()),
                ListSerializer(MissionStepDto.serializer()),
            ),
        )

    override suspend fun reorderSteps(
        missionId: String,
        current: MissionDetail,
        stepIds: List<String>,
    ): ApiResult<MissionDetail> =
        withSteps(
            current,
            reader.put(
                "${missionPath(missionId)}/steps/reorder/slim",
                ReorderMissionStepsRequest(stepIds = stepIds, stepsVersion = current.stepsVersion),
                ReorderMissionStepsRequest.serializer(),
                ListSerializer(MissionStepDto.serializer()),
            ),
        )

    override suspend fun reorderObjectives(
        missionId: String,
        current: MissionDetail,
        objectiveIds: List<String>,
    ): ApiResult<MissionDetail> =
        withObjectives(
            current,
            reader.put(
                "${missionPath(missionId)}/objectives/reorder/slim",
                ReorderMissionObjectivesRequest(
                    objectiveIds = objectiveIds,
                    objectivesVersion = current.objectivesVersion,
                ),
                ReorderMissionObjectivesRequest.serializer(),
                ListSerializer(MissionObjectiveDto.serializer()),
            ),
        )

    override suspend fun addObjective(
        missionId: String,
        current: MissionDetail,
        title: String,
        kind: MissionObjectiveKind,
    ): ApiResult<MissionDetail> =
        withObjectives(
            current,
            reader.post(
                "${missionPath(missionId)}/objectives/slim",
                AddMissionObjectiveRequest(
                    title = title,
                    kind = AddMissionObjectiveRequest.Kind.valueOf(kind.name),
                    objectivesVersion = current.objectivesVersion,
                ),
                AddMissionObjectiveRequest.serializer(),
                ListSerializer(MissionObjectiveDto.serializer()),
            ),
        )

    override suspend fun updateObjective(
        missionId: String,
        current: MissionDetail,
        objectiveId: String,
        title: String,
        kind: MissionObjectiveKind,
    ): ApiResult<MissionDetail> =
        withObjectives(
            current,
            reader.put(
                "${missionPath(missionId)}/objectives/$objectiveId/slim",
                UpdateMissionObjectiveRequest(
                    title = title,
                    kind = UpdateMissionObjectiveRequest.Kind.valueOf(kind.name),
                    objectivesVersion = current.objectivesVersion,
                ),
                UpdateMissionObjectiveRequest.serializer(),
                ListSerializer(MissionObjectiveDto.serializer()),
            ),
        )

    override suspend fun removeObjective(
        missionId: String,
        current: MissionDetail,
        objectiveId: String,
    ): ApiResult<MissionDetail> =
        withObjectives(
            current,
            reader.delete(
                "${missionPath(missionId)}/objectives/$objectiveId/slim",
                listOf("objectivesVersion" to current.objectivesVersion.toString()),
                ListSerializer(MissionObjectiveDto.serializer()),
            ),
        )

    override suspend fun members(query: String): ApiResult<List<MemberOption>> =
        when (
            val result =
                reader.get(
                    "/api/v1/users/search",
                    listOf(
                        "query" to query.trim(),
                        "page" to "0",
                        "size" to PICKER_SIZE.toString(),
                    ),
                    PageResponseUserDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.content.orEmpty().mapNotNull { dto ->
                        val id = dto.id ?: return@mapNotNull null
                        val name = dto.effectiveName ?: dto.displayName ?: dto.username
                        name?.takeIf { it.isNotBlank() }?.let { MemberOption(id = id, name = it) }
                    },
                )
            }
        }

    override suspend fun crewJobTypes(): ApiResult<List<MissionJobType>> =
        when (
            val result =
                reader.get(
                    "/api/v1/job-types",
                    listOf(
                        "archetype" to "CREW",
                        "page" to "0",
                        "size" to CATALOGUE_SIZE.toString(),
                    ),
                    PageResponseJobTypeDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.content.orEmpty().mapNotNull { dto ->
                        dto.id?.let { MissionJobType(id = it, name = dto.name) }
                    },
                )
            }
        }

    /**
     * Splices a step answer onto the Einsatz, and advances the section counter with it.
     *
     * @param current the Einsatz as last read.
     * @param result what the write answered.
     * @return the Einsatz as it now stands, or the failure unchanged.
     */
    private fun withSteps(
        current: MissionDetail,
        result: ApiResult<List<MissionStepDto>>,
    ): ApiResult<MissionDetail> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    current.copy(
                        steps =
                            result.value.mapNotNull { dto ->
                                dto.id?.let {
                                    MissionStep(
                                        id = it,
                                        title = dto.title.orEmpty(),
                                        meta = dto.meta?.takeIf { m -> m.isNotBlank() },
                                        done = dto.done ?: false,
                                    )
                                }
                            },
                        stepsVersion = current.stepsVersion + 1,
                    ),
                )
            }
        }

    /**
     * Splices an objective answer onto the Einsatz, and advances the section counter with it.
     *
     * @param current the Einsatz as last read.
     * @param result what the write answered.
     * @return the Einsatz as it now stands, or the failure unchanged.
     */
    private fun withObjectives(
        current: MissionDetail,
        result: ApiResult<List<MissionObjectiveDto>>,
    ): ApiResult<MissionDetail> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    current.copy(
                        objectives =
                            result.value.mapNotNull { dto ->
                                dto.id?.let {
                                    MissionObjective(
                                        id = it,
                                        title = dto.title.orEmpty(),
                                        kind = dto.kind?.value,
                                    )
                                }
                            },
                        objectivesVersion = current.objectivesVersion + 1,
                    ),
                )
            }
        }
}
