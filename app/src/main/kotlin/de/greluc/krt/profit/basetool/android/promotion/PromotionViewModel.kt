/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.promotion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.PromotionEvaluation
import de.greluc.krt.profit.basetool.android.core.data.PromotionSource
import de.greluc.krt.profit.basetool.android.core.data.PromotionStanding
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One category with the member's assessed topics under it. */
data class PromotionCategoryGroup(
    val name: String,
    val evaluations: List<PromotionEvaluation>,
)

/** What the Beförderung screen draws. */
data class PromotionState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** Assessments, grouped by category. Empty and `error == null` means "nothing assessed yet". */
    val categories: List<PromotionCategoryGroup> = emptyList(),
    /** Rank steps. Its own half of the screen, with its own failure. */
    val standings: List<PromotionStanding> = emptyList(),
    val evaluationsError: ApiError? = null,
    val standingsError: ApiError? = null,
) {
    /**
     * The level the next rank step asks for on one topic.
     *
     * Design ch. 13 puts a goal beside every evaluation, and the goal lives on the **rank
     * requirements**, not on the evaluation: `MemberEvaluationResponse` carries no minimum. Several
     * steps can be open at once, so this reads the **first** one — the next rank the member is
     * working toward. Reading the highest instead would show a goal nobody is being measured
     * against yet, and merging them would invent a requirement no rule states.
     *
     * @param topicName the topic to look up.
     * @return the minimum level, or `null` when the next step names no requirement for that topic.
     */
    fun goalFor(topicName: String): String? =
        standings
            .firstOrNull()
            ?.checks
            ?.firstOrNull { it.topicName == topicName }
            ?.minimumLevel
}

/**
 * The member's own Beförderung record (REQ-APP-PROMO-001…003).
 *
 * **Two reads, two failures, one screen.** The assessments and the rank standings are separate
 * endpoints behind separate service logic, and one going down must not blank the other — the same
 * rule the Übersicht and the Hangar already follow. A member whose standings fail can still read
 * what they have been assessed on, which is the half they came for.
 *
 * Read-only: nobody assesses themselves, so there is no write, no version echo and no offline
 * disabling to do here.
 *
 * @property source where the record comes from.
 */
class PromotionViewModel(
    private val source: PromotionSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PromotionState())

    /** What the screen draws. */
    val state: StateFlow<PromotionState> = mutableState.asStateFlow()

    private var loadedOnce = false

    /** Loads both halves, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        mutableState.value = mutableState.value.copy(loading = true)
        read()
    }

    /** Re-reads both halves, keeping what is on screen while it runs. */
    fun onRefresh() {
        loadedOnce = true
        mutableState.value = mutableState.value.copy(refreshing = true)
        read()
    }

    /**
     * Runs both reads at once and files each answer on its own.
     *
     * Concurrent rather than sequential: they are unrelated, and making the member wait for the sum
     * of two round trips would be a cost with nothing bought.
     */
    private fun read() {
        viewModelScope.launch {
            val evaluations = async { source.evaluations() }
            val standings = async { source.standings() }
            val current = mutableState.value
            val evaluationsResult = evaluations.await()
            val standingsResult = standings.await()
            mutableState.value =
                current.copy(
                    loading = false,
                    refreshing = false,
                    categories =
                        (evaluationsResult as? ApiResult.Success)?.value?.let(::group)
                            ?: current.categories,
                    standings =
                        (standingsResult as? ApiResult.Success)?.value ?: current.standings,
                    evaluationsError = (evaluationsResult as? ApiResult.Failure)?.error,
                    standingsError = (standingsResult as? ApiResult.Failure)?.error,
                )
        }
    }

    /**
     * Folds the flat assessment list into its categories.
     *
     * Server order is kept inside a category and the categories appear in the order they first
     * occur: the officers configured that order and re-sorting it here would present their matrix
     * in an arrangement nobody chose. A row whose category the server left blank lands in one
     * unnamed group rather than being dropped — the assessment is real either way.
     *
     * @param evaluations the flat list.
     * @return the groups, in first-seen order.
     */
    private fun group(evaluations: List<PromotionEvaluation>): List<PromotionCategoryGroup> =
        evaluations
            .groupBy { it.categoryName }
            .map { (name, rows) -> PromotionCategoryGroup(name, rows) }
}
