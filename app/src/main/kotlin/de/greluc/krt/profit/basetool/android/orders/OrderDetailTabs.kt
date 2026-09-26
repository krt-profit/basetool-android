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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemStock
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRailCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Positionen: the requester's note, then what was ordered and how much has arrived.
 *
 * @param order the order.
 * @param allowed whether the caller may book a production run; a hint only, the control is drawn
 *   either way and the server decides.
 * @param denials where a refused tap is announced.
 * @param onProduce open „Herstellung erfassen" for one item line.
 * @param onHandOver open „Übergabe erfassen" for one item line.
 */
internal fun LazyListScope.positionsTab(
    order: JobOrder,
    allowed: Boolean,
    denials: DenialState,
    items: ItemLineBindings,
) {
    val onProduce = items.onProduce
    val onHandOver = items.onHandOver
    val tree = items.tree
    val itemStock = items.itemStock
    val topLevel = if (tree.isEmpty()) order.items else tree.map { it.line }
    items(topLevel, key = { "item-" + (it.id ?: it.name.orEmpty()) }) { line ->
        Column(modifier = Modifier.padding(horizontal = KrtSpacing.s12)) {
            ItemLine(
                item = line,
                produce =
                    if (line.id != null && line.version != null && line.remaining > 0) {
                        ItemProduceGate(
                            allowed = allowed,
                            denials = denials,
                            onProduce = { onProduce(line) },
                        )
                    } else {
                        null
                    },
                handOver =
                    if (line.id != null && line.deliverable > 0) {
                        ItemProduceGate(
                            allowed = allowed,
                            denials = denials,
                            onProduce = { onHandOver(line) },
                        )
                    } else {
                        null
                    },
            )
            tree.firstOrNull { it.line.id == line.id }?.let { branch ->
                SubAssemblies(branch = branch, itemStock = itemStock)
            }
        }
    }
    if (order.materials.isEmpty()) {
        if (order.items.isEmpty()) {
            item(key = "materials-empty") {
                Body(text = stringResource(R.string.order_detail_materials_empty))
            }
        }
    } else {
        items(order.materials, key = { it.name }) { material ->
            Column(modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4)) {
                MaterialLine(material = material)
            }
        }
    }
    order.comment?.let { comment ->
        item(key = "comment") { CommentCard(comment = comment) }
    }
}

/**
 * The sub-assemblies of one ordered item and what each of them needs, display only.
 *
 * Built from the order's own lines, where a sub-assembly is an ordered line with a parent. Shows two
 * levels (assembly and its materials) and says so rather than truncating silently.
 *
 * @param branch the item and its sub-assemblies.
 * @param itemStock the earmarked stock per item, for the availability chip.
 */
@Composable
private fun SubAssemblies(
    branch: ItemBranch,
    itemStock: Map<String, JobOrderItemStock>,
) {
    if (branch.children.isEmpty()) {
        return
    }
    Column(
        modifier = Modifier.padding(start = KrtSpacing.s12, top = KrtSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        KrtSectionTitle(
            text = stringResource(R.string.order_detail_subassemblies),
            trailing = { Body(text = branch.children.size.toString()) },
        )
        branch.children.forEach { child ->
            SubAssembly(child = child, stock = itemStock[child.id])
        }
        if (branch.deeper) {
            KrtHint(explanation = stringResource(R.string.order_detail_subassembly_deeper))
        }
    }
}

/**
 * One sub-assembly: how many, whether the Auftrag already holds them, and what each needs.
 *
 * @param child the sub-assembly line.
 * @param stock what is earmarked of it, or `null` when the stock read has not landed.
 */
@Composable
private fun SubAssembly(
    child: JobOrderItem,
    stock: JobOrderItemStock?,
) {
    Column(
        modifier = Modifier.padding(start = KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = child.name ?: stringResource(R.string.order_detail_item_unnamed),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            Body(text = child.amount.toString())
            stock?.let { AvailabilityChip(stock = it) }
        }
        child.requirements.forEach { requirement ->
            Body(
                text =
                    stringResource(
                        R.string.order_detail_subassembly_material,
                        requirement.name,
                        requirement.requiredTotal.krtTrimmed(),
                        requirement.unit.orEmpty(),
                    ),
            )
        }
    }
}

/**
 * Whether the Auftrag already holds this sub-assembly.
 *
 * The same two words the craftability chip uses in Mein Inventar, so one reading carries across.
 *
 * @param stock what is earmarked of it.
 */
@Composable
private fun AvailabilityChip(stock: JobOrderItemStock) {
    if (stock.missing <= 0) {
        KrtChip(text = stringResource(R.string.order_detail_in_stock), tone = KrtChipTone.Success)
        return
    }
    KrtChip(
        text = stringResource(R.string.order_detail_missing, stock.missing),
        tone = KrtChipTone.Warning,
    )
}

/**
 * A quantity without a trailing `.0`.
 *
 * @receiver the quantity.
 * @return the plain decimal.
 */
private fun Double.krtTrimmed(): String =
    java.math.BigDecimal(this.toString()).stripTrailingZeros().toPlainString()

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
 * Übergaben: what has physically changed hands, and the action that records another handover.
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
    if (order.handovers.isEmpty() && order.itemHandovers.isEmpty()) {
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
    items(order.itemHandovers, key = { "item-" + it.id }) { handover ->
        val pieces = handover.lines.sumOf { line -> line.amount }
        Body(
            text =
                pluralStringResource(
                    R.plurals.order_detail_item_handover_row,
                    pieces,
                    handover.recipient.orEmpty(),
                    pieces,
                    handover.at?.relativeToNow().orEmpty(),
                ),
        )
    }
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
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
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
 * Says plainly that the order is only partly visible (REQ-ORDERS-023).
 *
 * Kept outside the facts bar, which draws nothing when a redacted order has no facts to show.
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
        modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
    )
}

/**
 * The requester's note, in a card with an orange accent rail.
 *
 * @param comment the note.
 */
@Composable
private fun CommentCard(comment: String) {
    KrtRailCard(modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12)) {
        Text(
            text = stringResource(R.string.order_detail_comment).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Text(
            text = comment,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
            modifier = Modifier.padding(top = KrtSpacing.s4),
        )
    }
}

/** Gap between a fact's key and its value in the order's facts bar. */
private val FACT_GAP = 5.dp
