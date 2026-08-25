/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.promotion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.PromotionCheck
import de.greluc.krt.profit.basetool.android.core.data.PromotionEvaluation
import de.greluc.krt.profit.basetool.android.core.data.PromotionStanding
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Beförderung — the member's own assessments and how far they are from the next rank
 * (REQ-APP-PROMO-001…003).
 *
 * **Recorded deviation:** the design handoff has no Beförderung chapter. The layout below is built
 * from the DAS KARTELL design system's own components (`KrtCard`, `KrtKeyValueRow`, the section
 * heading style the other screens use) and from what the web page shows, rather than invented — but
 * it is not a chapter being followed, and that is written down here rather than left to be
 * discovered.
 *
 * Read-only by nature: nobody assesses themselves, so this screen has no action, no version echo
 * and nothing to disable when the device is offline.
 *
 * @param viewModel the screen's state holder.
 * @param modifier layout modifier from the scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionScreen(
    viewModel: PromotionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            // Always scrollable, even while empty: a pull-to-refresh box whose child does not
            // scroll swallows the gesture, which is the defect phase 2 already paid for once.
            modifier = Modifier.fillMaxSize().testTag("promotion-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeading(stringResource(R.string.promotion_evaluations_title)) }

            if (state.evaluationsError != null) {
                item {
                    KrtEmptyState(
                        iconRes = DesignR.drawable.ic_krt_rank,
                        title = stringResource(R.string.promotion_error_title),
                        message = stringResource(R.string.promotion_evaluations_error_message),
                        modifier = Modifier.testTag("promotion-evaluations-error"),
                    )
                }
            }

            if (state.evaluationsError == null && state.categories.isEmpty() && !state.loading) {
                item {
                    KrtEmptyState(
                        iconRes = DesignR.drawable.ic_krt_rank,
                        title = stringResource(R.string.promotion_evaluations_empty_title),
                        message = stringResource(R.string.promotion_evaluations_empty_message),
                    )
                }
            }

            items(state.categories, key = { it.name }) { group ->
                KrtCard {
                    SectionHeading(
                        group.name.ifBlank { stringResource(R.string.promotion_category_unnamed) },
                    )
                    MatrixHeader()
                    group.evaluations.forEach { evaluation ->
                        MatrixRow(
                            evaluation = evaluation,
                            goal = state.goalFor(evaluation.topicName),
                        )
                    }
                }
            }

            item { SectionHeading(stringResource(R.string.promotion_standings_title)) }

            if (state.standingsError != null) {
                item {
                    KrtEmptyState(
                        iconRes = DesignR.drawable.ic_krt_rank,
                        title = stringResource(R.string.promotion_error_title),
                        message = stringResource(R.string.promotion_standings_error_message),
                        modifier = Modifier.testTag("promotion-standings-error"),
                    )
                }
            }

            if (state.standingsError == null && state.standings.isEmpty() && !state.loading) {
                item {
                    KrtEmptyState(
                        iconRes = DesignR.drawable.ic_krt_rank,
                        title = stringResource(R.string.promotion_standings_empty_title),
                        message = stringResource(R.string.promotion_standings_empty_message),
                    )
                }
            }

            items(state.standings, key = { "${it.fromRank}-${it.toRank}" }) { standing ->
                StandingCard(standing)
            }
        }
    }
}

/**
 * One rank step and its requirements.
 *
 * @param standing the step.
 */
@Composable
private fun StandingCard(standing: PromotionStanding) {
    KrtCard(modifier = Modifier.testTag("promotion-standing-${standing.toRank}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.promotion_rank_step,
                        standing.fromRank,
                        standing.toRank,
                    ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    stringResource(
                        if (standing.eligible) {
                            R.string.promotion_eligible
                        } else {
                            R.string.promotion_not_eligible
                        },
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = if (standing.eligible) KrtPalette.Success else KrtPalette.TextMuted,
            )
        }

        if (!standing.hasConfiguredRules) {
            // "No rules configured" is not "you failed". Rendering an empty requirement list would
            // read as the second, and the organisation never made that judgement.
            Text(
                text = stringResource(R.string.promotion_no_rules),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.padding(top = 8.dp).testTag("promotion-no-rules"),
            )
            return@KrtCard
        }

        standing.checks.forEach { check -> CheckRow(check) }
    }
}

/**
 * One requirement, with the member's progress along it.
 *
 * @param check the requirement.
 */
@Composable
private fun CheckRow(check: PromotionCheck) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        KrtKeyValueRow(
            label = check.topicName,
            value =
                stringResource(
                    R.string.promotion_check_progress,
                    check.achievedCount,
                    check.requiredCount,
                    check.minimumLevel,
                ),
        )
        check.description?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The matrix's column heads.
 *
 * Design ch. 13 artboard 1, as corrected on 2026-08-25: three columns, not five. The earlier draft
 * had "Selbst" and "Leitung" beside them, describing an assessment the tool does not record —
 * `MemberEvaluationResponse` carries one `assignedLevel` per topic and nothing else. The owner
 * decided the columns come out rather than the feature going in.
 */
@Composable
private fun MatrixHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.promotion_matrix_topic),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.promotion_matrix_level),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MATRIX_COLUMN),
        )
        Text(
            text = stringResource(R.string.promotion_matrix_goal),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MATRIX_COLUMN),
        )
    }
    KrtHairlineRule()
}

/**
 * One topic: where the member stands and what the next rank asks for.
 *
 * The level turns warning-yellow when it is **below** the goal, which is the whole point of putting
 * the two numbers on one line — a member should be able to run down the column and see what is
 * missing without doing the comparison themselves. Read-only by design: evaluations are maintained
 * in the web tool's Bewertungsverwaltung.
 *
 * @param evaluation the member's level for this topic.
 * @param goal the minimum the next rank step asks for, `null` when that step names no requirement
 *   for this topic — in which case there is nothing to fall short of and nothing to colour.
 */
@Composable
private fun MatrixRow(
    evaluation: PromotionEvaluation,
    goal: String?,
) {
    val short = goal != null && isBelow(evaluation.level, goal)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = evaluation.topicName,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = evaluation.level,
            style = MaterialTheme.typography.bodyMedium,
            color = if (short) KrtPalette.Warning else KrtPalette.Gray1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MATRIX_COLUMN),
        )
        Text(
            text = goal?.let { stringResource(R.string.promotion_matrix_goal_value, it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MATRIX_COLUMN),
        )
    }
}

/**
 * Whether a level falls short of a goal.
 *
 * Both arrive as strings on the wire. Compared as numbers when they are numbers — the scale is
 * 1..5 — and otherwise not compared at all: an unparseable pair means the server sent a scale this
 * build does not know, and colouring it yellow would be a claim rather than a reading.
 *
 * @param level where the member stands.
 * @param goal what the next rank asks for.
 * @return `true` only when both parse and the level is the smaller one.
 */
private fun isBelow(
    level: String,
    goal: String,
): Boolean {
    val have = level.trim().toIntOrNull()
    val need = goal.trim().toIntOrNull()
    return have != null && need != null && have < need
}

/**
 * A section heading in the app's own style.
 *
 * @param text the heading.
 */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Width of the two numeric matrix columns, so the digits line up down the card. */
private val MATRIX_COLUMN = 64.dp
