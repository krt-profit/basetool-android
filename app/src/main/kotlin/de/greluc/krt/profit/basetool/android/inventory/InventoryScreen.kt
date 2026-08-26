/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the tree. */
const val INVENTORY_TREE_TAG: String = "inventory-tree"

/** Width of the orange rail that marks a material group (design ch. 09). */
private val GROUP_RAIL = 4.dp

/** Width of the grey rail that marks a stack beneath it. */
private val STACK_RAIL = 2.dp

/** How far a stack is inset from its group. */
private val STACK_INSET = 16.dp

/** How far an entry row is inset, one level deeper than a stack. */
private val ENTRY_INSET = 32.dp

/** Test handle for the booking action. */
const val INVENTORY_BOOK_TAG: String = "inventory-book"

/** Height of a rail segment, matching a two-line row. */
private val RAIL_HEIGHT = 44.dp

/**
 * The Lager tree (design spec ch. 09 §1), read-only.
 *
 * Two levels: a **material group** with an orange rail, and the **stacks** inside it with a grey
 * one. The design's third level — the individual entry — is not drawn: it is where booking happens,
 * and booking is Phase 3.
 *
 * @param state what to draw.
 * @param onToggleGroup a group row was tapped.
 * @param onToggleStack a stack row was tapped.
 * @param onBookIn the booking action was taken.
 * @param onBookOut an entry's booking action was taken.
 * @param onAllocate an entry's Zuordnung was opened.
 * @param selection which rows are selected.
 * @param onToggleSelected a row was long-pressed, or tapped while selecting.
 * @param onWithStockOnlyChanged the "Nur mit Bestand" chip was tapped.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onToggleGroup: (String) -> Unit,
    onToggleStack: (String, InventoryStack) -> Unit,
    onBookIn: () -> Unit,
    onBookOut: (InventoryEntry) -> Unit,
    onAllocate: (InventoryEntry) -> Unit,
    selection: Set<String>,
    onToggleSelected: (String) -> Unit,
    onWithStockOnlyChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.online) {
                OfflineBand()
            }
            Row(modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md)) {
                KrtFilterChip(
                    text = stringResource(R.string.inventory_with_stock_only),
                    selected = state.withStockOnly,
                    onClick = { onWithStockOnlyChanged(!state.withStockOnly) },
                )
            }

            when (state.phase) {
                is InventoryPhase.Loading -> {
                    KrtLoadingIndicator(
                        text = stringResource(R.string.inventory_title),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is InventoryPhase.Failed -> {
                    // A busy server gets the countdown of chapter 14; anything else gets the ordinary
                    // empty state, because a countdown in front of a 403 promises a retry that will
                    // answer exactly the same.
                    val retryIn = state.retryIn
                    if (retryIn != null) {
                        KrtRetryCountdown(
                            secondsLeft = retryIn,
                            title = stringResource(R.string.retry_busy_title),
                            message = stringResource(R.string.retry_busy_message, retryIn),
                            retryLabel = stringResource(R.string.retry_now),
                            onRetry = onRetryNow,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                        )
                    } else {
                        KrtEmptyState(
                            iconRes = DesignR.drawable.ic_krt_crate,
                            title = stringResource(R.string.inventory_error_title),
                            message = stringResource(R.string.inventory_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                        )
                    }
                }

                is InventoryPhase.Ready -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.visibleGroups.isEmpty()) {
                            KrtRefreshableFill {
                                InventoryEmpty(
                                    filtered = state.withStockOnly && state.groups.isNotEmpty(),
                                )
                            }
                        } else {
                            InventoryTree(
                                state = state,
                                onToggleGroup = onToggleGroup,
                                onToggleStack = onToggleStack,
                                onBookOut = onBookOut,
                                onAllocate = onAllocate,
                                selection = selection,
                                onToggleSelected = onToggleSelected,
                                online = state.online,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }
        }
        KrtFab(
            iconRes = DesignR.drawable.ic_krt_plus,
            label = stringResource(R.string.booking_mode_in),
            onClick = onBookIn,
            enabled = state.online,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(KrtSpacing.lg)
                    .padding(bottom = LocalKrtBottomBarInset.current)
                    .testTag(INVENTORY_BOOK_TAG),
        )
    }
}

/**
 * The tree itself.
 *
 * @param state what to draw.
 * @param onToggleGroup a group was tapped.
 * @param onToggleStack a stack was tapped.
 * @param onBookOut an entry's booking action was taken.
 * @param onAllocate an entry's Zuordnung was opened.
 * @param selection which rows are selected.
 * @param onToggleSelected a row was long-pressed, or tapped while selecting.
 * @param online whether a booking can be sent at all.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun InventoryTree(
    state: InventoryState,
    onToggleGroup: (String) -> Unit,
    onToggleStack: (String, InventoryStack) -> Unit,
    onBookOut: (InventoryEntry) -> Unit,
    onAllocate: (InventoryEntry) -> Unit,
    selection: Set<String>,
    onToggleSelected: (String) -> Unit,
    online: Boolean,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(INVENTORY_TREE_TAG)) {
        state.visibleGroups.forEach { group ->
            val materialId = group.materialId
            item(key = "group-${materialId ?: group.name}") {
                GroupRow(
                    group = group,
                    // A group the server sent without a material id cannot be asked for, so it does
                    // not offer a tap that would do nothing.
                    onClick = materialId?.let { { onToggleGroup(it) } },
                )
            }
            // A group nobody opened contributes nothing, which is the point of loading one only
            // when it is asked for — so the whole block is skipped rather than branching on null.
            val opened = materialId?.let { state.opened[it] }
            if (opened != null) {
                when (opened) {
                    is StackPhase.Loading -> {
                        item(key = "stacks-loading-$materialId") {
                            StackNote(text = stringResource(R.string.inventory_title))
                        }
                    }

                    is StackPhase.Failed -> {
                        item(key = "stacks-failed-$materialId") {
                            StackNote(text = stringResource(R.string.inventory_stacks_failed))
                        }
                    }

                    is StackPhase.Ready -> {
                        if (opened.stacks.isEmpty()) {
                            item(key = "stacks-empty-$materialId") {
                                StackNote(text = stringResource(R.string.inventory_stacks_empty))
                            }
                        } else {
                            opened.stacks.forEachIndexed { index, stack ->
                                item(key = "stack-$materialId-$index") {
                                    StackRow(
                                        stack = stack,
                                        unit = group.unit,
                                        onClick = { onToggleStack(materialId, stack) },
                                    )
                                }
                                entryRows(
                                    phase = state.openedStacks[stackKey(materialId, stack)],
                                    keyPrefix = "$materialId-$index",
                                    unit = group.unit,
                                    online = online,
                                    onBookOut = onBookOut,
                                    onAllocate = onAllocate,
                                    selection = selection,
                                    onToggleSelected = onToggleSelected,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text =
                        pluralStringResource(
                            R.plurals.inventory_group_count,
                            state.total.toInt(),
                            state.groups.size,
                            state.total,
                        ),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.inventory_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * One material group.
 *
 * @param group the group.
 * @param onClick opens or closes it, or `null` when it cannot be opened.
 */
@Composable
private fun GroupRow(
    group: InventoryGroup,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                // Design ch. 09 fills the group header rather than leaving it on the page ground:
                // it is what separates a group from the stacks underneath it in a long tree. The
                // orange rail beside it is that artboard's `border-left: 4px solid #E77E23`.
                .background(KrtPalette.SurfaceInput)
                .padding(end = KrtSpacing.md, top = KrtSpacing.sm, bottom = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(width = GROUP_RAIL, color = MaterialTheme.colorScheme.primary)
        // The chapter's group toggle turns a chevron; without one nothing says the row opens.
        if (onClick != null) {
            KrtIcon(
                id = DesignR.drawable.ic_krt_chevron_right,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The group row carries no quality in artboard 09.1 — a material's aggregate quality is an
        // average of stacks that may be far apart, and the number that matters is the one on the
        // stack a member is about to book. The gauge lives on the rows below.
        Amount(value = group.amount, unit = group.unit)
    }
}

/**
 * The entries of one open stack.
 *
 * A `LazyListScope` extension rather than a composable, so the rows stay siblings of the stack they
 * belong to: nesting a second list inside a lazy item is what makes a tree scroll like two.
 *
 * @param phase how far the read has got, or `null` when the stack is closed.
 * @param keyPrefix what makes the item keys unique within the tree.
 * @param unit the group's quantity unit.
 * @param online whether a booking can be sent at all.
 * @param onBookOut an entry's booking action was taken.
 */
private fun LazyListScope.entryRows(
    phase: EntriesPhase?,
    keyPrefix: String,
    unit: String?,
    online: Boolean,
    onBookOut: (InventoryEntry) -> Unit,
    onAllocate: (InventoryEntry) -> Unit,
    selection: Set<String>,
    onToggleSelected: (String) -> Unit,
) {
    when (phase) {
        // A closed stack contributes no rows at all.
        null -> {
            return
        }

        is EntriesPhase.Loading -> {
            item(key = "entries-loading-$keyPrefix") {
                StackNote(text = stringResource(R.string.inventory_entries_title))
            }
        }

        is EntriesPhase.Failed -> {
            item(key = "entries-failed-$keyPrefix") {
                StackNote(text = stringResource(R.string.inventory_stacks_failed))
            }
        }

        is EntriesPhase.Ready -> {
            if (phase.entries.isEmpty()) {
                item(key = "entries-empty-$keyPrefix") {
                    StackNote(text = stringResource(R.string.inventory_entries_none))
                }
            } else {
                phase.entries.forEachIndexed { index, entry ->
                    item(key = "entry-$keyPrefix-$index") {
                        EntryRow(
                            entry = entry,
                            unit = unit,
                            online = online,
                            onBookOut = { onBookOut(entry) },
                            onAllocate = { onAllocate(entry) },
                            selected = entry.id in selection,
                            selecting = selection.isNotEmpty(),
                            onToggleSelected = { onToggleSelected(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One entry — the thing a booking actually moves.
 *
 * @param entry the entry.
 * @param unit the group's quantity unit.
 * @param online whether a booking can be sent at all.
 * @param onBookOut opens the booking form on it.
 * @param selected whether this row is in the selection.
 * @param selecting whether the list is in selection mode at all.
 * @param onToggleSelected the row was long-pressed, or tapped while selecting.
 */
@Composable
private fun EntryRow(
    entry: InventoryEntry,
    unit: String?,
    online: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onBookOut: () -> Unit,
    onAllocate: () -> Unit,
    onToggleSelected: () -> Unit,
) {
    // Long-press starts selection mode and a plain tap continues it (design ch. 02 §4): once the
    // mode is on, having to keep long-pressing every further row makes selecting twelve stacks a
    // chore nobody finishes. A selected row wears the orange rail and a fill, as the artboard has it.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selecting) onToggleSelected() },
                    onLongClick = onToggleSelected,
                ).then(if (selected) Modifier.background(KrtPalette.SurfaceInput) else Modifier)
                .padding(
                    start = ENTRY_INSET,
                    end = KrtSpacing.md,
                    top = KrtSpacing.xs,
                    bottom = KrtSpacing.xs,
                ),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(
            width = if (selected) SELECT_RAIL else STACK_RAIL,
            color = if (selected) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
        )
        if (selecting && selected) {
            KrtIcon(
                id = DesignR.drawable.ic_krt_check,
                contentDescription = null,
                size = SELECT_MARK,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            // Design ch. 09 leads a stack entry with WHERE it is, behind a map pin — the amount is
            // the figure on the right. Only the amount and the note were drawn, so two entries of
            // the same material in different hangars read as duplicates of each other.
            entry.locationName?.takeIf { it.isNotBlank() }?.let { place ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KrtIcon(
                        id = DesignR.drawable.ic_krt_map_pin,
                        contentDescription = null,
                        tint = KrtPalette.TextMuted,
                    )
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Amount(value = entry.amount, unit = entry.unit ?: unit)
            entry.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        entry.quality?.let { quality ->
            QualityMark(quality = quality)
        }
        // Two actions, and one of them does not apply to every row: a personal entry carries no
        // allocation at all (design ch. 09 §3), so offering the split on one would be offering a
        // refusal. Whether the caller may split a SHARED entry is the server's call — the app holds
        // no role list on purpose — so the sheet opens and a 403 is reported in its own words.
        if (!entry.personal) {
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_target,
                label = stringResource(R.string.allocation_open),
                onClick = onAllocate,
                modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
                enabled = online,
            )
        }
        KrtGhostButton(
            text = stringResource(R.string.booking_open),
            onClick = onBookOut,
            modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
            enabled = online,
        )
    }
}

/**
 * A quality reading: the number, and a 44 dp bar showing where it sits on the 0–1000 scale.
 *
 * Design ch. 09: "Quality = value + 44 dp mini-gauge (0–1000)". The number alone is only meaningful
 * to somebody who already knows the scale — the bar makes "Q 874" readable as *high* at a glance,
 * which is the judgement a member makes when choosing which stack to book out.
 *
 * @param quality the reading, 0–1000.
 */
@Composable
private fun QualityMark(quality: String) {
    // The reading arrives as a string on the wire; a value the app cannot parse still shows its
    // number and simply draws no bar, rather than guessing a position on the scale.
    val share = quality.trim().toDoubleOrNull()?.div(QUALITY_MAX)?.toFloat()?.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = stringResource(R.string.inventory_quality, quality),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.Gray1,
        )
        share?.let { filled ->
            Box(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .width(QUALITY_GAUGE_WIDTH)
                        .height(QUALITY_GAUGE_HEIGHT)
                        .background(KrtPalette.Gray3),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(filled)
                            .height(QUALITY_GAUGE_HEIGHT)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * One stack inside a group.
 *
 * @param stack the stack.
 * @param unit the group's quantity unit, since a stack carries none of its own.
 * @param onClick opens its entries.
 */
@Composable
private fun StackRow(
    stack: InventoryStack,
    unit: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = STACK_INSET, end = KrtSpacing.md, top = KrtSpacing.xs, bottom = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(width = STACK_RAIL, color = KrtPalette.Gray3)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stack.title(),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.inventory_entry_count,
                        stack.entryCount,
                        stack.entryCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (stack.personal) {
            KrtChip(text = stringResource(R.string.inventory_personal), tone = KrtChipTone.Muted)
        }
        stack.quality?.let { QualityMark(quality = it) }
        Amount(value = stack.amount, unit = unit)
    }
}

/**
 * The stack's headline.
 *
 * @return the holder and the place, whichever of them the server attributed.
 */
private fun InventoryStack.title(): String =
    listOfNotNull(holder?.takeIf { it.isNotBlank() }, location?.takeIf { it.isNotBlank() })
        .joinToString(" · ")

/**
 * A quantity with its unit dimmed behind it, as the design has it.
 *
 * @param value the amount, unformatted.
 * @param unit the unit, or `null` when the server named none.
 */
@Composable
private fun Amount(
    value: String?,
    unit: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs), verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatAmount(value.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        unit?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The depth rail.
 *
 * @param width how wide it is; the design uses 4 dp for a group and 2 dp for a stack.
 * @param color its colour.
 */
@Composable
private fun Rail(
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
) {
    Box(modifier = Modifier.width(width).height(RAIL_HEIGHT).background(color))
}

/**
 * A muted line standing in for a group's stacks.
 *
 * @param text what to say.
 */
@Composable
private fun StackNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier =
            Modifier.padding(
                start = STACK_INSET,
                end = KrtSpacing.md,
                top = KrtSpacing.xs,
                bottom = KrtSpacing.xs,
            ),
    )
}

/**
 * The empty state, which differs by whether the chip is what emptied it.
 *
 * @param filtered whether "Nur mit Bestand" hid everything on this page.
 */
@Composable
private fun InventoryEmpty(filtered: Boolean) {
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_crate,
        title =
            stringResource(
                if (filtered) R.string.inventory_empty_filtered_title else R.string.inventory_empty_title,
            ),
        message =
            stringResource(
                if (filtered) {
                    R.string.inventory_empty_filtered_message
                } else {
                    R.string.inventory_empty_message
                },
            ),
        modifier = Modifier.padding(KrtSpacing.lg),
    )
}

/**
 * The Lager, bound to its view model.
 *
 * @param viewModel drives the tree.
 * @param modifier layout modifier.
 */
@Composable
fun InventoryRoute(
    viewModel: InventoryViewModel,
    onBookIn: () -> Unit,
    onBookOut: (InventoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InventoryScreen(
        state = state,
        onToggleGroup = viewModel::onToggleGroup,
        onToggleStack = viewModel::onToggleStack,
        onBookIn = onBookIn,
        onBookOut = onBookOut,
        onAllocate = viewModel::onAllocate,
        selection = state.selection,
        onToggleSelected = viewModel::onToggleSelected,
        onWithStockOnlyChanged = viewModel::onWithStockOnlyChanged,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        modifier = modifier,
    )

    // The bottom action bar of chapter 02 §4: it exists only while something is selected, which is
    // what makes the mode self-evident — nothing to leave, nothing to notice you are in.
    if (state.selection.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            KrtBottomCtaBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.inventory_selected,
                                state.selection.size,
                                state.selection.size,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = KrtPalette.White,
                        modifier = Modifier.weight(1f),
                    )
                    KrtGhostButton(
                        text = stringResource(R.string.inventory_selection_clear),
                        onClick = viewModel::onSelectionCleared,
                    )
                    KrtCtaButton(
                        text = stringResource(R.string.inventory_bulk_move),
                        onClick = viewModel::onBulkMoveRequested,
                        iconRes = DesignR.drawable.ic_krt_swap,
                        enabled = state.online,
                    )
                }
            }
        }
    }

    state.bulk?.let { bulk ->
        BulkMoveSheet(
            bulk = bulk,
            count = state.selection.size,
            onPlace = viewModel::onBulkMovePlace,
            onConfirm = viewModel::onBulkMoveConfirmed,
            onDismiss = viewModel::onBulkMoveDismissed,
        )
    }

    state.allocation?.let { allocation ->
        AllocationSheet(
            state = allocation,
            callbacks =
                AllocationCallbacks(
                    onAmount = viewModel::onAllocationAmount,
                    onStep = viewModel::onAllocationStep,
                    onAdd = viewModel::onAllocationAdd,
                    onPick = viewModel::onAllocationPick,
                    onSave = viewModel::onAllocationSave,
                    onDismiss = viewModel::onAllocationDismissed,
                ),
        )
    }
}

/** Width of the quality mini-gauge — 44 dp per design ch. 09. */
private val QUALITY_GAUGE_WIDTH = 44.dp

/** Height of the quality mini-gauge; the same flat band the attendance meter uses. */
private val QUALITY_GAUGE_HEIGHT = 4.dp

/** Top of the quality scale the gauge maps onto. */
private const val QUALITY_MAX = 1_000.0

/** Width of the orange inset bar on a selected row (design ch. 02 §4). */
private val SELECT_RAIL = 3.dp

/** Size of the check beside it. */
private val SELECT_MARK = 18.dp

/**
 * „Umbuchen" over a selection.
 *
 * One place for every selected row, because that is what the endpoint takes and what a member who
 * has just moved a hangar's worth of stock actually wants. The count is in the CTA: a bulk action
 * that does not say how much it will touch is one nobody should press.
 *
 * @param bulk the open sheet.
 * @param count how many rows are selected.
 * @param onPlace a place was picked.
 * @param onConfirm the move was confirmed.
 * @param onDismiss the sheet was closed.
 */
@Composable
private fun BulkMoveSheet(
    bulk: BulkMoveState,
    count: Int,
    onPlace: (LocationOption) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.inventory_bulk_move),
        modifier = Modifier.testTag(INVENTORY_BULK_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            Text(
                text = pluralStringResource(R.plurals.inventory_bulk_move_body, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
            KrtSelectField(
                value = bulk.place?.name ?: stringResource(R.string.inventory_bulk_move_pick),
                options = bulk.places.map { KrtOption(it.id, it.name) },
                onSelect = { option ->
                    bulk.places.firstOrNull { it.id == option.value }?.let(onPlace)
                    open = false
                },
                expanded = open,
                onExpandedChange = { open = it },
                label = stringResource(R.string.booking_field_place),
                selectedValue = bulk.place?.id,
                enabled = !bulk.saving,
            )
            bulk.error?.let { KrtFieldError(text = stringResource(R.string.write_failed)) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = onDismiss,
                    enabled = !bulk.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.inventory_bulk_move_confirm),
                    onClick = onConfirm,
                    iconRes = DesignR.drawable.ic_krt_swap,
                    enabled = bulk.place != null && !bulk.saving,
                    modifier = Modifier.testTag(INVENTORY_BULK_CONFIRM_TAG),
                )
            }
        }
    }
}

/** Test handle for the bulk-move sheet. */
const val INVENTORY_BULK_TAG = "inventory-bulk-move"

/** Test handle for its confirm button. */
const val INVENTORY_BULK_CONFIRM_TAG = "inventory-bulk-confirm"
