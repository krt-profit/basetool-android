/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Test handle for the deduct-from section. */
const val HERKUNFT_TAG: String = "booking-herkunft"

/** Width of a share field — three digits and a separator, never a full-width input. */
private val SHARE_FIELD_WIDTH = 96.dp

/**
 * „Herkunft der Menge" — where the deducted quantity comes from (design ch. 09 artboards 18–19).
 *
 * **Rows, not a table.** The web frontend lays this out as a grid of tag against amount, which
 * needs horizontal room a phone does not have. The artboards answer with a row per earmark and,
 * beneath each dimension, always the „Vom Rest" line — so the two numbers a member has to reconcile
 * sit above one another rather than across a scroll.
 *
 * The section is drawn only when there is something to decide. An entry with no earmarks in either
 * dimension has one possible answer, and a heading over two empty lists is a question with no
 * question in it.
 *
 * @param state the form.
 * @param onJobOrderShare a share of an Auftrag earmark changed.
 * @param onMissionShare a share of an Einsatz earmark changed.
 */
@Composable
fun HerkunftSection(
    state: BookingState,
    onJobOrderShare: (String, String) -> Unit,
    onMissionShare: (String, String) -> Unit,
) {
    val jobOrders = state.jobOrderDimension
    val missions = state.missionDimension
    if (jobOrders.tags.isEmpty() && missions.tags.isEmpty()) {
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        modifier = Modifier.fillMaxWidth().testTag(HERKUNFT_TAG),
    ) {
        KrtFieldLabel(text = stringResource(R.string.herkunft_title), enabled = !state.saving)
        Muted(stringResource(R.string.herkunft_explainer))

        Dimension(
            titleRes = R.string.herkunft_from_orders,
            kind = AllocationKind.JOB_ORDER,
            dimension = jobOrders,
            state = state,
            typed = state.jobOrderPlan,
            onShare = onJobOrderShare,
        )
        Dimension(
            titleRes = R.string.herkunft_from_missions,
            kind = AllocationKind.MISSION,
            dimension = missions,
            state = state,
            typed = state.missionPlan,
            onShare = onMissionShare,
        )
    }
}

/**
 * One dimension: its heading, its status chip, a row per earmark and the rest line.
 *
 * @param titleRes the dimension's heading.
 * @param kind which of the two it is, which decides the accent colour.
 * @param dimension what the plan currently adds up to.
 * @param state the form.
 * @param typed what the member put in this dimension's fields.
 * @param onShare a share changed.
 */
@Composable
private fun Dimension(
    titleRes: Int,
    kind: AllocationKind,
    dimension: HerkunftDimension,
    state: BookingState,
    typed: Map<String, String>,
    onShare: (String, String) -> Unit,
) {
    if (dimension.tags.isEmpty()) {
        return
    }
    // Auftrag is the app's orange, Einsatz its info blue — the same pairing the allocation sheet
    // uses, so a member reads the two dimensions apart without a legend.
    val accent =
        when (kind) {
            AllocationKind.JOB_ORDER -> MaterialTheme.colorScheme.primary
            AllocationKind.MISSION -> KrtTheme.colors.infoText
        }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            StatusChip(dimension = dimension, deducted = state.deducted)
        }

        dimension.tags.forEach { tag ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(KrtSpacing.hairline, if (dimension.valid) accent else KrtPalette.DangerText)
                        .background(KrtPalette.SurfaceInput)
                        .padding(KrtSpacing.s8),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tag.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dimension.valid) accent else KrtPalette.DangerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Muted(
                        stringResource(
                            R.string.herkunft_tag_assigned,
                            // Through the app's own formatter: the server sends "3.0", and the
                            // artboard writes "zugeordnet 200 SCU". Every other quantity on this
                            // screen is grouped and trimmed, and one raw value reads as a bug.
                            formatAmount(tag.amount),
                            state.unit().orEmpty(),
                        ),
                    )
                }
                KrtTextField(
                    value =
                        if (dimension.locked) {
                            formatAmount(state.amount)
                        } else {
                            typed[tag.targetId].orEmpty()
                        },
                    onValueChange = { onShare(tag.targetId, it) },
                    // Locked, not hidden: the member is entitled to see the number that will be
                    // sent, and this shape has only one possible value (artboard 18).
                    enabled = !state.saving && !dimension.locked,
                    modifier = Modifier.width(SHARE_FIELD_WIDTH),
                )
            }
        }

        // Always drawn beneath the tags, never only when non-zero: it is the line that tells a
        // member where the difference is going, and a line that appears and disappears reads as a
        // warning rather than as arithmetic.
        if (!dimension.locked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Muted(stringResource(R.string.herkunft_from_rest))
                Text(
                    text =
                        stringResource(
                            R.string.herkunft_rest_amount,
                            formatAmount(dimension.fromRest.trimmed()),
                            state.unit().orEmpty(),
                            formatAmount(dimension.free.trimmed()),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dimension.valid) KrtPalette.White else KrtPalette.DangerText,
                )
            }
        }

        DimensionNote(dimension = dimension, deducted = state.deducted)
    }
}

/**
 * The line under a dimension, where its shape needs one.
 *
 * `COVERED` and `FROM_REST` are the two states that need no explanation — the chip has already said
 * it and the rest line has already shown the number. A function that returns is how that is said;
 * a `when` branch evaluating `Unit` says the same thing and reads as an oversight.
 *
 * @param dimension what the plan adds up to.
 * @param deducted how much is leaving the entry.
 */
@Composable
private fun DimensionNote(
    dimension: HerkunftDimension,
    deducted: Double,
) {
    when (dimension.status) {
        HerkunftStatus.AUTOMATIC -> {
            Muted(stringResource(R.string.herkunft_automatic_note))
        }

        HerkunftStatus.OVERALLOCATED -> {
            Text(
                text =
                    stringResource(
                        R.string.herkunft_overallocated,
                        formatAmount(dimension.assigned.trimmed()),
                        formatAmount(deducted.trimmed()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.DangerText,
            )
        }

        HerkunftStatus.REST_TOO_SMALL -> {
            Text(
                text =
                    stringResource(
                        R.string.herkunft_rest_too_small,
                        formatAmount(dimension.fromRest.trimmed()),
                        formatAmount(dimension.free.trimmed()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.DangerText,
            )
        }

        HerkunftStatus.COVERED, HerkunftStatus.FROM_REST -> {
            return
        }
    }
}

/**
 * The chip that names a dimension's shape at a glance.
 *
 * The chip states a relationship, not a quantity, so it carries no unit — that keeps it on one
 * line at font scale 1.3, which is how the artboards draw it.
 *
 * @param dimension what the plan adds up to.
 * @param deducted how much is leaving the entry.
 */
@Composable
private fun StatusChip(
    dimension: HerkunftDimension,
    deducted: Double,
) {
    val (text, tone) =
        when (dimension.status) {
            HerkunftStatus.COVERED -> {
                stringResource(R.string.herkunft_status_covered) to KrtChipTone.Success
            }

            HerkunftStatus.FROM_REST -> {
                stringResource(
                    R.string.herkunft_status_from_rest,
                    formatAmount(dimension.fromRest.trimmed()),
                ) to KrtChipTone.Muted
            }

            HerkunftStatus.OVERALLOCATED -> {
                stringResource(
                    R.string.herkunft_status_overallocated,
                    formatAmount(dimension.overshoot(deducted).trimmed()),
                ) to KrtChipTone.Danger
            }

            HerkunftStatus.REST_TOO_SMALL -> {
                stringResource(R.string.herkunft_status_rest_too_small) to KrtChipTone.Danger
            }

            HerkunftStatus.AUTOMATIC -> {
                stringResource(R.string.herkunft_status_automatic) to KrtChipTone.Muted
            }
        }
    KrtChip(text = text, tone = tone)
}

/**
 * Renders a quantity the way the rest of the sheet does: whole where it is whole.
 *
 * @return the value without a trailing `.0`.
 */
private fun Double.trimmed(): String =
    if (this == Math.floor(this)) toLong().toString() else toString()
