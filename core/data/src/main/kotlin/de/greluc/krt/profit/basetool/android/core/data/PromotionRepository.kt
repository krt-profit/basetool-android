/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MemberEvaluationResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PromotionEligibilityResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PromotionRequirementCheckResponse
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/**
 * One stored assessment of the member, in one topic.
 *
 * @property categoryName the category the topic belongs to, e.g. „Fliegerisches Können".
 * @property topicName the topic itself.
 * @property level the level the member holds, as the server spells it; never translated, since
 *   levels are configured per organisation.
 */
data class PromotionEvaluation(
    val categoryName: String,
    val topicName: String,
    val level: String,
)

/**
 * One requirement of one rank step, and how far the member is along it.
 *
 * @property topicName the topic the requirement is about.
 * @property categoryName its category.
 * @property minimumLevel the level that counts towards the requirement.
 * @property requiredCount how many topics at that level are needed.
 * @property achievedCount how many the member has.
 * @property satisfied whether this requirement is met.
 * @property description the officers' own wording, when they wrote one.
 */
data class PromotionCheck(
    val topicName: String,
    val categoryName: String,
    val minimumLevel: String,
    val requiredCount: Int,
    val achievedCount: Int,
    val satisfied: Boolean,
    val description: String?,
)

/**
 * The member's standing for one rank step.
 *
 * @property fromRank the rank held.
 * @property toRank the next one.
 * @property eligible whether the step is currently reachable.
 * @property hasConfiguredRules whether any rules are configured for this step; without rules the
 *   step is neither met nor failed.
 * @property checks the individual requirements, empty when there are no rules.
 */
data class PromotionStanding(
    val fromRank: Int,
    val toRank: Int,
    val eligible: Boolean,
    val hasConfiguredRules: Boolean,
    val checks: List<PromotionCheck>,
)

/** The member's own promotion record. Read-only: nobody assesses themselves. */
interface PromotionSource {
    /**
     * Reads the member's stored assessments.
     *
     * @return one entry per assessed topic, or the classified failure.
     */
    suspend fun evaluations(): ApiResult<List<PromotionEvaluation>>

    /**
     * Reads the member's standing for each rank step.
     *
     * @return one entry per step, or the classified failure.
     */
    suspend fun standings(): ApiResult<List<PromotionStanding>>
}

/**
 * Reads the member's own Beförderung record (REQ-APP-PROMO-001…003).
 *
 * Both paths end in `/my` and the server resolves the member from the token, so no other member can
 * be queried; the officers' endpoints are not reachable from here.
 *
 * @property reader performs the calls and classifies their failures.
 */
class PromotionRepository(
    private val reader: ApiReader,
) : PromotionSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers.
     * @param baseUrl the flavour's API origin.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /** {@inheritDoc} */
    override suspend fun evaluations(): ApiResult<List<PromotionEvaluation>> =
        reader.get(
            EVALUATIONS_PATH,
            ListSerializer(MemberEvaluationResponse.serializer()),
        )
            .map { loaded -> loaded.mapNotNull { it.toModel() } }

    /** {@inheritDoc} */
    override suspend fun standings(): ApiResult<List<PromotionStanding>> =
        reader.get(
            ELIGIBILITY_PATH,
            ListSerializer(PromotionEligibilityResponse.serializer()),
        )
            .map { loaded -> loaded.map { it.toModel() } }

    private companion object {
        /** Log subsystem. An evaluation is about a person and its content is never logged. */
        const val LOG_TAG = "promotion"

        const val EVALUATIONS_PATH = "/api/v1/promotion/evaluations/my"
        const val ELIGIBILITY_PATH = "/api/v1/promotion/eligibility/my"
    }
}

/**
 * Maps one stored assessment, dropping a row that lacks a topic or a level.
 *
 * @return the model, or `null` if the row carries nothing to show.
 */
private fun MemberEvaluationResponse.toModel(): PromotionEvaluation? {
    val topic = topicName?.takeIf { it.isNotBlank() }
    val level = assignedLevel?.value?.takeIf { it.isNotBlank() }
    return if (topic == null || level == null) {
        null
    } else {
        PromotionEvaluation(
            categoryName = categoryName?.takeIf { it.isNotBlank() }.orEmpty(),
            topicName = topic,
            level = level,
        )
    }
}

/**
 * Maps one rank step.
 *
 * Kept even when it carries no checks — a step with no configured rules is a real answer and the
 * screen has its own sentence for it.
 *
 * @return the model.
 */
private fun PromotionEligibilityResponse.toModel(): PromotionStanding =
    PromotionStanding(
        fromRank = fromRank ?: 0,
        toRank = toRank ?: 0,
        eligible = eligible == true,
        hasConfiguredRules = hasConfiguredRules == true,
        checks = checks.orEmpty().mapNotNull { it.toModel() },
    )

/**
 * Maps one requirement.
 *
 * @return the model, or `null` when the requirement names no topic to be about.
 */
private fun PromotionRequirementCheckResponse.toModel(): PromotionCheck? {
    val topic = topicName?.takeIf { it.isNotBlank() } ?: return null
    return PromotionCheck(
        topicName = topic,
        categoryName = categoryName?.takeIf { it.isNotBlank() }.orEmpty(),
        minimumLevel = minimumLevel?.value ?: minimumLevel?.toString().orEmpty(),
        requiredCount = requiredCount ?: 0,
        achievedCount = achievedCount ?: 0,
        satisfied = satisfied == true,
        description = description?.takeIf { it.isNotBlank() },
    )
}
