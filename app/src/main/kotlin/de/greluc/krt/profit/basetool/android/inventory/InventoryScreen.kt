/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BulkRebookResult
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialEntryPage
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLockBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLockToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectionCheckbox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.navigation.SelectionBar
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DENIAL_TOAST_MS
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.DenialToast
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.isLogistician
import de.greluc.krt.profit.basetool.android.ui.mayEditRowOf
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import kotlinx.coroutines.delay
import java.math.BigDecimal
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the tree. */
const val INVENTORY_TREE_TAG: String = "inventory-tree"

/** Width of the orange rail that marks a material group (design ch. 09). */
private val GROUP_RAIL = 4.dp

/** Width of the grey rail that marks a stack beneath it. */
private val STACK_RAIL = 2.dp

/**
 * The leaf row's darkening, straight from the artboard's `.tree-row--leaf`:
 * `background-color: rgba(0, 0, 0, 0.35)`.
 *
 * Laid **over** [KrtPalette.Gray4] rather than replacing it, because in the artboard the tree is a
 * table with its own `--color-bg-dark-gray` ground and the leaf only darkens it. Composing the two
 * the way CSS does keeps the pair honest if either token moves; a hard-coded `#0D0D0D` would not.
 */
private val TREE_LEAF_SHADE = Color.Black.copy(alpha = 0.35f)

/** How far a holder's heading is inset from its material. */
private val HOLDER_INSET = 8.dp

/** How far a stack is inset from its holder. */
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
 * @param denials where a tapped lock raises its refusal.
 * @param onWithStockOnlyChanged the "Nur mit Bestand" chip was tapped.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param modifier layout modifier.
 * @param pane what the tablet pane is showing, or `null` while nothing is selected.
 * @param paneActions what the tablet pane reports back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onToggleGroup: (String) -> Unit,
    onToggleStack: (String, InventoryStack) -> Unit,
    onToggleBranch: (String, InventoryStack?) -> Unit,
    onBookIn: () -> Unit,
    onBookOut: (InventoryEntry) -> Unit,
    onAllocate: (InventoryEntry) -> Unit,
    selection: Set<String>,
    onToggleSelected: (String) -> Unit,
    denials: DenialState,
    onWithStockOnlyChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    // Defaulted, and after the modifier because Android Lint requires that one to come first among
    // the optional parameters. The pane exists only on a tablet, and a test that draws the tree is
    // not asking about it; the route always passes all three.
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.online) {
                OfflineBand()
            }
            Row(modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12)) {
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
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                        )
                    } else {
                        KrtEmptyState(
                            iconRes = DesignR.drawable.ic_krt_crate,
                            title = stringResource(R.string.inventory_error_title),
                            message = stringResource(R.string.inventory_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
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
                            // No detail pane, at any width — design ch. 18 §3 (E9) settles the
                            // Lager on „rail + table": the tree IS the detail depth, and a fourth
                            // level to its right would tell the same indentation twice. What
                            // appears beside the tree on a tablet is the booking sheet, nothing
                            // else. The pane and its `/inventory/material/{id}` read are gone
                            // rather than hidden behind a flag.
                            InventoryTree(
                                state = state,
                                onToggleGroup = onToggleGroup,
                                onToggleStack = onToggleStack,
                                onToggleBranch = onToggleBranch,
                                onBookOut = onBookOut,
                                onAllocate = onAllocate,
                                selection = selection,
                                onToggleSelected = onToggleSelected,
                                denials = denials,
                                online = state.online,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }
        }
        // „FAB und Bottom-Nav weichen der Aktionsleiste" (design ch. 09, artboard 5). Two floating
        // affordances at the same corner is one too many, and the one that belongs to a mode wins.
        if (selection.isEmpty()) {
            KrtFab(
                // The download glyph, not „+". Chapter 05's „EINBUCHEN (LAGER)" tile draws exactly
                // this arrow, which settles what artboard 09.1 meant by it: ⤓ is Einbuchen. It was
                // read here as Ausbuchen once, and „+" put in its place.
                iconRes = DesignR.drawable.ic_krt_download,
                label = stringResource(R.string.booking_mode_in),
                onClick = onBookIn,
                enabled = state.online,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(KrtSpacing.s16)
                        .padding(bottom = LocalKrtBottomBarInset.current)
                        .testTag(INVENTORY_BOOK_TAG),
            )
        }
    }
}

/**
 * The tree itself.
 *
 * @param state what to draw.
 * @param onToggleGroup a group was tapped.
 * @param onToggleStack a stack was tapped.
 * @param onToggleBranch a group or stack was long-pressed; selects every entry under it.
 * @param onBookOut an entry's booking action was taken.
 * @param onAllocate an entry's Zuordnung was opened.
 * @param selection which rows are selected.
 * @param onToggleSelected a row was long-pressed, or tapped while selecting.
 * @param denials where a tapped lock raises its refusal.
 * @param online whether a booking can be sent at all.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun InventoryTree(
    state: InventoryState,
    onToggleGroup: (String) -> Unit,
    onToggleStack: (String, InventoryStack) -> Unit,
    onToggleBranch: (String, InventoryStack?) -> Unit,
    onBookOut: (InventoryEntry) -> Unit,
    onAllocate: (InventoryEntry) -> Unit,
    selection: Set<String>,
    onToggleSelected: (String) -> Unit,
    denials: DenialState,
    online: Boolean,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(INVENTORY_TREE_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        state.visibleGroups.forEach { group ->
            val materialId = group.materialId
            // Per group, because the unit is the group's; everything else in it is tree-wide.
            val entryRowContext =
                EntryRowContext(
                    unit = group.unit,
                    online = online,
                    selection = selection,
                    denials = denials,
                    onBookOut = onBookOut,
                    onAllocate = onAllocate,
                    onToggleSelected = onToggleSelected,
                )
            item(key = "group-${materialId ?: group.name}") {
                val (picked, known) = materialId?.let(state::selectionIn) ?: (0 to null)
                GroupRow(
                    group = group,
                    // A group the server sent without a material id cannot be asked for, so it does
                    // not offer a tap that would do nothing.
                    //
                    // One gesture does both on a tablet: opening a material and reading its full
                    // table beside the tree are the same intent, and a second affordance on the row
                    // would be a control whose only job is to say "and also over there".
                    onClick = materialId?.let { { onToggleGroup(it) } },
                    onLongClick = materialId?.let { { onToggleBranch(it, null) } },
                    selected = picked,
                    // Only while the group is open does „n/m" mean anything: a collapsed group's
                    // total is whatever was loaded before, not what it holds now.
                    total = known.takeIf { materialId in state.opened },
                )
            }
            // A group nobody opened contributes nothing, which is the point of loading one only
            // when it is asked for — so the whole block is skipped rather than branching on null.
            val openedId = materialId?.takeIf { state.opened.containsKey(it) }
            if (openedId != null) {
                openedGroup(
                    materialId = openedId,
                    phase = state.opened.getValue(openedId),
                    context =
                        OpenedGroupContext(
                            unit = group.unit,
                            openedStacks = state.openedStacks,
                            rows = entryRowContext,
                            onToggleStack = onToggleStack,
                            onToggleBranch = onToggleBranch,
                        ),
                )
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
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.inventory_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            }
        }
    }
}

/**
 * What the levels beneath an opened material need.
 *
 * A holder rather than seven parameters: the tree passes the same five values down two levels, and
 * threading them individually is how one of them ends up out of step with the others.
 *
 * @property unit the material's unit, which every figure beneath it is in.
 * @property openedStacks which stacks have their entries showing.
 * @property rows what an entry row needs.
 * @property onToggleStack a stack was tapped.
 * @property onToggleBranch a stack was long-pressed.
 */
private data class OpenedGroupContext(
    val unit: String?,
    val openedStacks: Map<String, EntriesPhase>,
    val rows: EntryRowContext,
    val onToggleStack: (String, InventoryStack) -> Unit,
    val onToggleBranch: (String, InventoryStack?) -> Unit,
)

/**
 * Everything under an opened material: the holder headings, their stacks, and any opened entries.
 *
 * Extracted from the tree's own loop, which had grown past what one function may branch on. The
 * split is where the tree's shape changes — above it a flat list of materials, below it three
 * nested levels — and not at an arbitrary line count.
 *
 * @param materialId which material was opened; its stacks are keyed by it.
 * @param phase where the stack read stands.
 * @param context everything the levels beneath the material need.
 */
private fun LazyListScope.openedGroup(
    materialId: String,
    phase: StackPhase,
    context: OpenedGroupContext,
) {
    when (phase) {
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
            if (phase.stacks.isEmpty()) {
                item(key = "stacks-empty-$materialId") {
                    StackNote(text = stringResource(R.string.inventory_stacks_empty))
                }
            } else {
                var index = 0
                byHolder(phase.stacks).forEach { holder ->
                    item(key = "holder-$materialId-${holder.key}") {
                        HolderRow(holder = holder, unit = context.unit)
                    }
                    holder.stacks.forEach { stack ->
                        // Counted across holders, not within one: the key has to stay stable when
                        // a stack moves between holders, which a per-holder index would not.
                        val at = index++
                        item(key = "stack-$materialId-$at") {
                            StackRow(
                                stack = stack,
                                unit = context.unit,
                                onClick = { context.onToggleStack(materialId, stack) },
                                onLongClick = { context.onToggleBranch(materialId, stack) },
                            )
                        }
                        entryRows(
                            phase = context.openedStacks[stackKey(materialId, stack)],
                            keyPrefix = "$materialId-$at",
                            rows = context.rows,
                        )
                    }
                }
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
    selected: Int = 0,
    total: Int? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    // Long-press on a branch is shorthand for its leaves (artboard 5). The plain
                    // tap keeps opening and closing it, because collapsing is a change of view and
                    // must stay reachable while a selection runs.
                    if (onClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                )
                // Design ch. 09 fills the group header rather than leaving it on the page ground:
                // it is what separates a group from the stacks underneath it in a long tree. The
                // orange rail beside it is that artboard's `border-left: 4px solid #E77E23`.
                .background(KrtPalette.SurfaceInput)
                .padding(end = KrtSpacing.s12, top = KrtSpacing.s8, bottom = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
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
        // What this group contributes to a running selection. „1/3" while it is open, „1" once it
        // is collapsed — collapsing is a change of view, not of selection, and the count has to
        // keep saying so or a member loses track of rows they can no longer see (artboard 5).
        if (selected > 0) {
            KrtChip(
                text =
                    if (total != null) {
                        pluralStringResource(R.plurals.inventory_group_selected_of, total, selected, total)
                    } else {
                        pluralStringResource(R.plurals.inventory_selected, selected, selected)
                    },
                tone = KrtChipTone.Primary,
            )
        }
        // The group row carries no quality in artboard 09.1 — a material's aggregate quality is an
        // average of stacks that may be far apart, and the number that matters is the one on the
        // stack a member is about to book. The gauge lives on the rows below.
        Amount(value = group.amount, unit = group.unit)
    }
}

/**
 * Everything an entry row needs that the tree decides rather than the row.
 *
 * These seven travelled as seven forwarded parameters through [entryRows], which does nothing with
 * them but hand them on. Naming the bundle says the true thing about them: they are one decision
 * the tree makes per group, identical for every entry under it, and adding an eighth is a change to
 * that decision rather than to the plumbing between two functions.
 *
 * @property unit the group's quantity unit.
 * @property online whether a booking can be sent at all.
 * @property selection which entry ids are currently selected.
 * @property denials where a tapped lock raises its refusal.
 * @property onBookOut an entry's booking action was taken.
 * @property onAllocate an entry's Zuordnung was opened.
 * @property onToggleSelected a row was long-pressed, or tapped while selecting.
 */
private data class EntryRowContext(
    val unit: String?,
    val online: Boolean,
    val selection: Set<String>,
    val denials: DenialState,
    val onBookOut: (InventoryEntry) -> Unit,
    val onAllocate: (InventoryEntry) -> Unit,
    val onToggleSelected: (String) -> Unit,
)

/**
 * The entries of one open stack.
 *
 * A `LazyListScope` extension rather than a composable, so the rows stay siblings of the stack they
 * belong to: nesting a second list inside a lazy item is what makes a tree scroll like two.
 *
 * @param phase how far the read has got, or `null` when the stack is closed.
 * @param keyPrefix what makes the item keys unique within the tree.
 * @param rows what every entry under this stack is drawn with.
 */
private fun LazyListScope.entryRows(
    phase: EntriesPhase?,
    keyPrefix: String,
    rows: EntryRowContext,
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
                            unit = rows.unit,
                            online = rows.online,
                            onBookOut = { rows.onBookOut(entry) },
                            onAllocate = { rows.onAllocate(entry) },
                            selected = entry.id in rows.selection,
                            selecting = rows.selection.isNotEmpty(),
                            onToggleSelected = { rows.onToggleSelected(entry.id) },
                            denials = rows.denials,
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
 * @param denials where a tapped lock raises its refusal.
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
    denials: DenialState,
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
                ).background(KrtPalette.Gray4)
                .background(TREE_LEAF_SHADE)
                .then(if (selected) Modifier.background(KrtPalette.SurfaceInput) else Modifier)
                .padding(
                    start = ENTRY_INSET,
                    end = KrtSpacing.s12,
                    top = KrtSpacing.s4,
                    bottom = KrtSpacing.s4,
                ),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(
            width = if (selected) SELECT_RAIL else STACK_RAIL,
            color = if (selected) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
        )

        Column(modifier = Modifier.weight(1f)) {
            // Design ch. 09 leads a stack entry with WHERE it is, behind a map pin — the amount is
            // the figure on the right. Only the amount and the note were drawn, so two entries of
            // the same material in different hangars read as duplicates of each other.
            entry.locationName?.takeIf { it.isNotBlank() }?.let { place ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
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
        // Selection mode replaces the row's own actions with its checkbox: with both on screen a
        // tap would mean two things at once (design ch. 09, artboard 5: „Buchen/Zuordnen inaktiv").
        if (selecting) {
            KrtSelectionCheckbox(checked = selected)
        } else {
            EntryActions(
                entry = entry,
                online = online,
                onBookOut = onBookOut,
                onAllocate = onAllocate,
                denials = denials,
            )
        }
    }
}

/**
 * The two writes a stock row offers, each behind the lock that actually governs it.
 *
 * *Buchen* asks whether the row is the caller's — own row, or edit rights on its org unit.
 * *Zuordnen* asks for the Logistiker grant and stays locked even on the caller's own row (design
 * ch. 09, artboard 11: „Buchen: eigene Zeile → aktiv; Zuordnen: Rolle Logistiker → gesperrt").
 * Two locks, the same picture, different copy — only the refusal's wording separates them.
 *
 * Neither is `enabled = false`: both keep a live tap target so the refusal can name the grant to
 * ask for, instead of arriving as a 403 after the write was already attempted (ADR-0011).
 *
 * A **personal** entry carries no allocation at all (design ch. 09 §3), so the split is not offered
 * on one — offering it would be offering a refusal that no grant could ever lift.
 *
 * @param entry the row.
 * @param online whether writes are possible at all right now.
 * @param onBookOut opens the booking form.
 * @param onAllocate opens the Zuordnung.
 * @param denials where a tapped lock raises its refusal.
 */
@Composable
private fun EntryActions(
    entry: InventoryEntry,
    online: Boolean,
    onBookOut: () -> Unit,
    onAllocate: () -> Unit,
    denials: DenialState,
) {
    val roleGate =
        Gate(
            allowed = isLogistician(),
            reason = stringResource(R.string.gate_role_logistician),
            detail = stringResource(R.string.gate_role_logistician_detail),
        )
    val rowGate =
        Gate(
            allowed = mayEditRowOf(entry.holderId),
            reason = stringResource(R.string.gate_own_row),
            detail = stringResource(R.string.gate_own_row_detail),
        )
    if (!entry.personal) {
        val (dim, click) = rememberGated(roleGate, onAllocate, denials)
        // The badge sits on the button's corner and is never dimmed with it: alpha alone reads as
        // "loading", and the lock is what makes it read as "you may not" (artboard 14).
        Box {
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_target,
                label = stringResource(R.string.allocation_open),
                onClick = click,
                modifier = dim.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
                enabled = online,
            )
            if (!roleGate.allowed) {
                KrtLockBadge(modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
    }
    val (bookDim, bookClick) = rememberGated(rowGate, onBookOut, denials)
    KrtGhostButton(
        text = stringResource(R.string.booking_open),
        onClick = bookClick,
        iconRes = if (rowGate.allowed) null else DesignR.drawable.ic_krt_lock,
        modifier = bookDim.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
        enabled = online,
    )
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
 * @param onLongClick selects every entry in it — a stack row carries no selection of its own
 *   (design ch. 09, artboard 5).
 */
@Composable
private fun StackRow(
    stack: InventoryStack,
    unit: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(KrtPalette.Gray4)
                .padding(start = STACK_INSET, end = KrtSpacing.s12, top = KrtSpacing.s4, bottom = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
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
 * One holder's stacks inside a material, and what they add up to.
 *
 * @property key stable across recompositions; the holder's id where the server sent one, and their
 *   name otherwise, because a tree keyed on a list position re-animates every row when one opens.
 * @property name whose stock it is, or `null` for stock the server did not attribute.
 * @property stacks their stacks, in the order the server sent them.
 * @property subtotal what those stacks add up to, or `null` when they cannot be added.
 */
private data class HolderStacks(
    val key: String,
    val name: String?,
    val stacks: List<InventoryStack>,
    val subtotal: String?,
)

/**
 * Splits a material's stacks by whose they are, which is the level artboard 1 draws.
 *
 * The wire has no holder level — `/inventory/all/grouped` answers stacks keyed by
 * (holder, place, quality) — so the app builds it. A member holding one material at two places was
 * two unrelated rows, and nothing on the screen said how much they held in total.
 *
 * **The subtotal is the sum of the rows directly beneath it, and nothing more.** That is what a
 * subtotal is, and it is not the invented arithmetic this app refuses elsewhere: no figure here is
 * derived from anything the member cannot also see. It is summed as `BigDecimal`, from the strings
 * the server sent, so a quarter-SCU does not drift; and if **any** of the stacks carries an amount
 * this build cannot parse, the whole subtotal is dropped rather than shown short. A total that is
 * quietly missing one of its parts is worse than no total.
 *
 * Order is the server's, first-seen: re-sorting would move rows a member had just looked at.
 *
 * @param stacks the material's stacks, as the server sent them.
 * @return one entry per holder.
 */
private fun byHolder(stacks: List<InventoryStack>): List<HolderStacks> =
    stacks
        .groupBy { it.holderId ?: it.holder.orEmpty() }
        .map { (key, held) ->
            val amounts = held.map { it.amount?.trim()?.takeIf { a -> a.isNotEmpty() }?.toBigDecimalOrNull() }
            HolderStacks(
                key = key.ifEmpty { "unattributed" },
                name = held.firstNotNullOfOrNull { it.holder?.takeIf { n -> n.isNotBlank() } },
                stacks = held,
                subtotal =
                    if (amounts.any { it == null }) {
                        null
                    } else {
                        amounts.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add).toPlainString()
                    },
            )
        }

/**
 * The holder level of the tree: whose stock, and how much of it in total.
 *
 * It does not open or close. The stacks beneath it are already visible — it is a heading with a
 * figure, and a chevron on it would promise a fourth thing to unfold that does not exist.
 *
 * @param holder whose stacks these are.
 * @param unit the material's unit.
 */
@Composable
private fun HolderRow(
    holder: HolderStacks,
    unit: String?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .padding(start = HOLDER_INSET, end = KrtSpacing.s12, top = KrtSpacing.s4, bottom = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(width = STACK_RAIL, color = KrtPalette.Gray2)
        Text(
            text = holder.name ?: stringResource(R.string.inventory_holder_unattributed),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // No subtotal rather than a wrong one: see byHolder.
        holder.subtotal?.let { Amount(value = it, unit = unit) }
    }
}

/**
 * The stack's headline.
 *
 * The holder moved up to [HolderRow], so this names the place alone. Repeating the holder on every
 * stack under their own name was the earlier form and it made two stacks of one member read as two
 * members.
 *
 * @return the place, or the holder when the server attributed no place — a row has to say something.
 */
private fun InventoryStack.title(): String =
    (location?.takeIf { it.isNotBlank() } ?: holder?.takeIf { it.isNotBlank() }).orEmpty()

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
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4), verticalAlignment = Alignment.Bottom) {
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
                end = KrtSpacing.s12,
                top = KrtSpacing.s4,
                bottom = KrtSpacing.s4,
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
        modifier = Modifier.padding(KrtSpacing.s16),
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
    // One refusal at a time, at the foot of the screen — the design settled the open question of
    // round 3 on the bracket toast in the warning tint (design ch. 09, artboards 12 and 14).
    val denials = rememberDenialState()
    // While rows are being picked the whole head becomes „✕ n gewählt" and the bottom navigation
    // steps aside for the action bar (design ch. 09, artboard 5). Published rather than drawn here,
    // because both surfaces belong to the shell.
    ProvideScreenTopBar(
        selection =
            state.selection
                .takeIf { it.isNotEmpty() }
                ?.let { SelectionBar(count = it.size, onClear = viewModel::onSelectionCleared) },
    )
    // Two ways out and no third: the ✕ in the head, and the system back gesture. Deselecting the
    // last row also ends the mode, but nobody leaves twelve rows one tap at a time.
    BackHandler(enabled = state.selection.isNotEmpty(), onBack = viewModel::onSelectionCleared)
    InventoryScreen(
        state = state,
        onToggleGroup = viewModel::onToggleGroup,
        onToggleStack = viewModel::onToggleStack,
        onToggleBranch = viewModel::onToggleBranch,
        onBookIn = onBookIn,
        onBookOut = onBookOut,
        onAllocate = viewModel::onAllocate,
        selection = state.selection,
        onToggleSelected = viewModel::onToggleSelected,
        denials = denials,
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The artboard splits the count in two: the figure heavy and white, the word
                    // small, muted and uppercase. One `Text` would have to pick one of them.
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(SELECTION_COUNT_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.selection.size.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = KrtPalette.White,
                        )
                        Text(
                            text = stringResource(R.string.inventory_selected_word).krtUppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = KrtPalette.TextMuted,
                        )
                    }
                    KrtGhostButton(
                        text = stringResource(R.string.inventory_selection_clear),
                        onClick = viewModel::onSelectionCleared,
                    )
                    // A selection may span rows that are not the caller's, and the same lock the
                    // individual rows wear applies to the batch (design ch. 09, artboard 5:
                    // „Enthält die Auswahl fremde Zeilen, rendert Umbuchen im Gesperrt-Stil …
                    // die Auswahl bleibt bestehen"). Refusing does not clear what was picked.
                    val ownsEveryRow = state.selectedEntries().all { mayEditRowOf(it.holderId) }
                    val bulkGate =
                        Gate(
                            allowed = ownsEveryRow,
                            reason = stringResource(R.string.gate_own_row),
                            detail = stringResource(R.string.gate_own_row_detail),
                        )
                    val (bulkDim, bulkClick) =
                        rememberGated(bulkGate, viewModel::onBulkMoveRequested, denials)
                    // The same gate: the endpoint refuses a foreign row and takes the whole call
                    // down with it, so a selection that spans someone else's stock cannot be
                    // booked out either.
                    val (outDim, outClick) =
                        rememberGated(bulkGate, viewModel.checkoutActions::request, denials)
                    KrtGhostButton(
                        text = stringResource(R.string.inventory_bulk_checkout),
                        onClick = outClick,
                        iconRes =
                            if (bulkGate.allowed) {
                                DesignR.drawable.ic_krt_upload
                            } else {
                                DesignR.drawable.ic_krt_lock
                            },
                        enabled = state.online,
                        modifier = outDim.testTag(INVENTORY_CHECKOUT_TAG),
                    )
                    KrtCtaButton(
                        text = stringResource(R.string.inventory_bulk_move),
                        onClick = bulkClick,
                        iconRes =
                            if (bulkGate.allowed) {
                                DesignR.drawable.ic_krt_swap
                            } else {
                                DesignR.drawable.ic_krt_lock
                            },
                        enabled = state.online,
                        modifier = bulkDim,
                    )
                }
            }
        }
    }

    // The drawn refusal, shared with every other screen that locks a control (design ch. 09,
    // artboards 12 and 14). It was inlined here until the Einsatz roster needed the same thing —
    // two copies of one artboard drift, so it moved into GatedAction beside the gate it belongs to.
    DenialToast(state = denials)

    state.checkout?.let { checkout ->
        BulkCheckoutSheet(
            checkout = checkout,
            entries = state.selectedEntries(),
            onConfirm = viewModel.checkoutActions::confirm,
            onDismiss = viewModel.checkoutActions::close,
            onFinished = viewModel.checkoutActions::close,
        )
    }

    state.bulk?.let { bulk ->
        BulkMoveSheet(
            bulk = bulk,
            count = state.selection.size,
            onPlace = viewModel::onBulkMovePlace,
            onConfirm = viewModel::onBulkMoveConfirmed,
            onDismiss = viewModel::onBulkMoveDismissed,
            onFinished = viewModel::onBulkMoveFinished,
        )
    }

    state.allocation?.let { allocation ->
        // Design ch. 14's conflict dialog: a refused save must not be a line under a
        // scrolled form. „Neu laden" closes the form and makes the screen re-read.
        ConflictOn(
            error = allocation.error,
            onReload = {
                viewModel.onAllocationDismissed()
                viewModel.onRefresh()
            },
        )
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
            saveGate =
                Gate(
                    allowed = isLogistician(),
                    reason = stringResource(R.string.gate_role_logistician),
                    detail = stringResource(R.string.gate_role_logistician_detail),
                ),
            denials = denials,
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

/**
 * „Sammel-Ausbuchen" — design ch. 09 artboard 20.
 *
 * **Whole rows only**, which is what the endpoint does: every listed row is deleted in full and its
 * earmarks cascade away with it. A member who needs a part of a stack uses the single book-out,
 * which is the call that carries an amount.
 *
 * > **Three things the artboard draws that `POST /inventory/bulk-checkout` cannot carry.**
 * > It takes the ids and nothing else — no „Grund" („Verbraucht" / „Verworfen"), no note, and no
 * > per-row Herkunft planner. And it is **all or nothing**: a foreign row or an unknown id refuses
 * > the whole call, so there is no „ausgebucht / übersprungen" outcome to draw the way the bulk
 * > rebooking beside it has one. All on the design gap list.
 *
 * @param checkout what the sheet holds.
 * @param entries the rows it is about, so the member can see what they picked.
 * @param onConfirm the CTA.
 * @param onDismiss the sheet was closed before it ran.
 * @param onFinished the result step was acknowledged.
 */
@Composable
private fun BulkCheckoutSheet(
    checkout: BulkCheckoutState,
    entries: List<InventoryEntry>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    KrtBottomSheet(
        onDismiss = if (checkout.done) onFinished else onDismiss,
        title = stringResource(R.string.inventory_bulk_checkout),
        modifier = Modifier.testTag(INVENTORY_CHECKOUT_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            if (checkout.done) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.inventory_bulk_checkout_done,
                            checkout.count,
                            checkout.count,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                )
                KrtCtaButton(
                    text = stringResource(R.string.inventory_bulk_move_close),
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth().testTag(INVENTORY_CHECKOUT_CLOSE_TAG),
                )
                return@KrtBottomSheet
            }
            // The artboard's own sentence, and the reason this is not a delete: the rows leave the
            // stock, the audit log keeps the event.
            Text(
                text = stringResource(R.string.inventory_bulk_checkout_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.materialName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KrtPalette.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // „vollständig" per row, because the endpoint knows no partial amount and a
                    // member who expected one has to be told before the CTA, not after.
                    KrtChip(text = stringResource(R.string.inventory_bulk_checkout_full))
                }
            }
            checkout.error?.let {
                Text(
                    text = stringResource(R.string.inventory_bulk_checkout_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
            KrtCtaButton(
                text =
                    pluralStringResource(
                        R.plurals.inventory_bulk_checkout_cta,
                        checkout.count,
                        checkout.count,
                    ),
                onClick = onConfirm,
                enabled = !checkout.saving,
                modifier = Modifier.fillMaxWidth().testTag(INVENTORY_CHECKOUT_CONFIRM_TAG),
            )
        }
    }
}

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
    onFinished: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    KrtBottomSheet(
        // A finished batch is dismissed by finishing it, not by swiping past its own result: the
        // skipped count is the one figure the tree cannot show afterwards.
        onDismiss = if (bulk.result == null) onDismiss else onFinished,
        title =
            if (bulk.result == null) {
                stringResource(R.string.inventory_bulk_move)
            } else {
                stringResource(R.string.inventory_bulk_move_result)
            },
        modifier = Modifier.testTag(INVENTORY_BULK_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            if (bulk.result != null) {
                // No "n entries will be moved" over a batch that already ran — the tiles below say
                // what happened, and the line above them would still be promising it.
                BulkMoveOutcome(result = bulk.result, place = bulk.place?.name, onFinished = onFinished)
                return@KrtBottomSheet
            }
            Text(
                text = pluralStringResource(R.plurals.inventory_bulk_move_body, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
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
            // Said before the write rather than after it: a member told afterwards that four rows
            // were skipped reads it as four failures (design ch. 09, artboard 6).
            Text(
                text = stringResource(R.string.inventory_bulk_move_skip_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            // A refusal keeps the sheet open and the selection standing: nothing was changed, and
            // re-picking twelve rows to retry punishes the member for the server's answer
            // (artboard 10).
            bulk.error?.let { KrtFieldError(text = stringResource(R.string.inventory_bulk_move_refused)) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = onDismiss,
                    enabled = !bulk.saving,
                )
                KrtCtaButton(
                    text = pluralStringResource(R.plurals.inventory_bulk_move_confirm, count, count),
                    onClick = onConfirm,
                    iconRes = DesignR.drawable.ic_krt_swap,
                    enabled = bulk.place != null && !bulk.saving,
                    modifier = Modifier.testTag(INVENTORY_BULK_CONFIRM_TAG),
                )
            }
        }
    }
}

/**
 * What the batch did — its own step in the sheet, rather than a toast on the way out.
 *
 * Two figures and a sentence (design ch. 09, artboard 9). The skipped one is **not** an error: a row
 * already standing at the target needs no move, and saying so in words is the difference between a
 * member reading "1" as a failure and reading it as nothing to do. A toast is too fleeting to carry
 * that sentence, which is why the result is a step and not a notification.
 *
 * @param result the counts the server returned.
 * @param place where the rows were sent, for the sentence.
 * @param onFinished closes the batch: ends selection mode and re-reads the tree.
 */
@Composable
private fun BulkMoveOutcome(
    result: BulkRebookResult,
    place: String?,
    onFinished: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
        KrtFigureTile(
            label = stringResource(R.string.inventory_bulk_move_rebooked),
            value = result.rebooked.toString(),
            tone = KrtFigureTone.Success,
            modifier = Modifier.weight(1f),
        )
        KrtFigureTile(
            label = stringResource(R.string.inventory_bulk_move_skipped),
            value = result.skipped.toString(),
            tone = KrtFigureTone.Neutral,
            modifier = Modifier.weight(1f),
        )
    }
    if (result.skipped > 0 && place != null) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.inventory_bulk_move_skipped_note,
                    result.skipped,
                    result.skipped,
                    place,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
    KrtCtaButton(
        text = stringResource(R.string.inventory_bulk_move_close),
        onClick = onFinished,
        modifier = Modifier.fillMaxWidth().testTag(INVENTORY_BULK_CLOSE_TAG),
    )
}

/** Test handle for the bulk-move sheet. */
const val INVENTORY_BULK_TAG = "inventory-bulk-move"

/** Test handle for its confirm button. */
const val INVENTORY_BULK_CONFIRM_TAG = "inventory-bulk-confirm"

/** Gap between the action bar's figure and the word beside it — design ch. 09, artboard 5. */
private val SELECTION_COUNT_GAP = 10.dp

/** Test handle for the result step's closing button. */
const val INVENTORY_BULK_CLOSE_TAG = "inventory-bulk-close"

/** Test handle for the selection bar's Ausbuchen action. */
const val INVENTORY_CHECKOUT_TAG = "inventory-bulk-checkout"

/** Test handle for its sheet. */
const val INVENTORY_CHECKOUT_SHEET_TAG = "inventory-bulk-checkout-sheet"

/** Test handle for its CTA. */
const val INVENTORY_CHECKOUT_CONFIRM_TAG = "inventory-bulk-checkout-confirm"

/** Test handle for the result step's close. */
const val INVENTORY_CHECKOUT_CLOSE_TAG = "inventory-bulk-checkout-close"
