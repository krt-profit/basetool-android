/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRailCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Positionen: the requester's note, then what was ordered and how much has arrived.
 *
 * @param order the order.
 */
internal fun LazyListScope.positionsTab(order: JobOrder) {
    order.comment?.let { comment ->
        item(key = "comment") { CommentCard(comment = comment) }
    }
    items(order.items, key = { "item-" + (it.id ?: it.name.orEmpty()) }) { line ->
        Column(modifier = Modifier.padding(horizontal = KrtSpacing.md)) {
            ItemLine(item = line)
        }
    }
    if (order.materials.isEmpty()) {
        // Only when the order carries nothing at all. An item order has no materials of its own —
        // the server derives them from the blueprint — so saying "no materials" under its items
        // would read as a defect.
        if (order.items.isEmpty()) {
            item(key = "materials-empty") {
                Body(text = stringResource(R.string.order_detail_materials_empty))
            }
        }
    } else {
        items(order.materials, key = { it.name }) { material ->
            Column(modifier = Modifier.padding(horizontal = KrtSpacing.md)) {
                MaterialLine(material = material)
            }
        }
    }
}

/**
 * Zuständig: who has taken the order on, and their own notes.
 *
 * @param order the order.
 * @param state the screen, for the caller's identity and whether writes are possible.
 * @param actions what the rows report back.
 */
internal fun LazyListScope.assigneesTab(
    order: JobOrder,
    state: OrderDetailState,
    actions: OrderDetailActions,
) {
    if (order.assignees.isEmpty()) {
        item(key = "assignees-empty") {
            Body(text = stringResource(R.string.order_detail_assignees_empty))
        }
    } else {
        items(order.assignees, key = { it.userId }) { assignee ->
            AssigneeRow(
                assignee = assignee,
                mine = assignee.userId == state.me?.userId,
                writable = state.writable,
                onEditNote = actions.onEditNote,
            )
        }
    }
}

/**
 * Übergaben: what has physically changed hands — and the action that adds to it.
 *
 * Read-only until 2026-08-29. In the web the handover is what **closes** an Auftrag, so an app that
 * could take one on and never record a delivery could never finish one either — the round-8 parity
 * programme's heaviest item (design ch. 10 artboard 14).
 *
 * One entry per material line, because a handover is booked against a line and its stock rows.
 *
 * @param order the order.
 * @param onRecord open the sheet for one material line.
 */
internal fun LazyListScope.handoversTab(
    order: JobOrder,
    onRecord: (JobOrderMaterial) -> Unit,
) {
    if (order.handovers.isEmpty()) {
        item(key = "handovers-empty") {
            Body(text = stringResource(R.string.order_detail_handovers_empty))
        }
    } else {
        items(order.handovers, key = { it.id }) { handover ->
            Body(
                text =
                    stringResource(
                        R.string.order_detail_handover_row,
                        handover.recipient.orEmpty(),
                        handover.at?.relativeToNow().orEmpty(),
                    ),
            )
        }
    }
    // A line the server sent without a material id cannot be handed over — the write is addressed
    // by it — so it is not offered rather than offered and refused.
    val recordable = order.materials.filter { it.materialId != null }
    if (recordable.isNotEmpty()) {
        item(key = "handover-record") {
            KrtSectionTitle(text = stringResource(R.string.order_handover_record_title))
        }
        items(recordable, key = { "record-${'$'}{it.materialId}" }) { material ->
            KrtGhostButton(
                text = stringResource(R.string.order_handover_record_for, material.name),
                onClick = { onRecord(material) },
                iconRes = DesignR.drawable.ic_krt_bookout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The head's facts strip: who the order is for, who works it, and who has taken it on.
 *
 * `.facts-bar`, the same band the Einsatz detail uses — key and value on one line, on the input
 * surface. It replaces a muted sentence that ran the three together and could not be scanned.
 *
 * @param order the order.
 */
@Composable
internal fun OrderFactsBar(order: JobOrder) {
    val facts =
        buildList {
            order.requestingOrgUnit?.takeIf { it.isNotBlank() }?.let {
                add(stringResource(R.string.order_detail_fact_for) to it)
            }
            order.responsibleOrgUnit?.takeIf { it.isNotBlank() }?.let {
                add(stringResource(R.string.order_detail_fact_by) to it)
            }
            order.assignees
                .mapNotNull { it.name }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
                ?.let { add(stringResource(R.string.order_detail_fact_assignee) to it) }
        }
    if (facts.isEmpty()) {
        return
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        facts.forEach { (label, value) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(FACT_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = KrtPalette.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Says plainly that the order is only partly visible.
 *
 * Its own composable, and NOT inside the facts bar: a redacted order can easily have no facts to
 * show — that is what redaction does — and the bar returns early when it has none. The notice would
 * then vanish exactly on the orders it exists for. A member reading a reduced order as a complete
 * one is the failure REQ-ORDERS-023 is there to prevent.
 *
 * @param order the order.
 */
@Composable
internal fun RedactionNotice(order: JobOrder) {
    if (!order.redacted) {
        return
    }
    Text(
        text = stringResource(R.string.orders_redacted),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.Warning,
        modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.xs),
    )
}

/**
 * The requester's note, in the accented card artboard 10.2 gives it.
 *
 * An orange rail rather than a plain section: it is the one piece of prose on a screen of numbers,
 * and it is the piece that says what the numbers are for.
 *
 * @param comment the note.
 */
@Composable
private fun CommentCard(comment: String) {
    KrtRailCard(modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md)) {
        Text(
            text = stringResource(R.string.order_detail_comment).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Text(
            text = comment,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
            modifier = Modifier.padding(top = KrtSpacing.xs),
        )
    }
}

/** Gap between a fact's key and its value in the order's facts bar. */
private val FACT_GAP = 5.dp
