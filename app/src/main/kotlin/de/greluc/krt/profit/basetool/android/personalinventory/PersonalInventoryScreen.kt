/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectionCheckbox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the list. */
const val PERSONAL_INVENTORY_LIST_TAG: String = "personal-inventory-list"

/** Test handle for the "new entry" action. */
const val PERSONAL_INVENTORY_CREATE_TAG: String = "personal-inventory-create"

/** Test handle for the selection bar's delete action. */
const val PERSONAL_INVENTORY_BULK_TAG: String = "personal-inventory-bulk"

/** Test handle for its confirmation. */
const val PERSONAL_INVENTORY_BULK_MODAL_TAG: String = "personal-inventory-bulk-modal"

/**
 * "Mein Inventar" — the member's own stock, read and written (design ch. 09 § 4).
 *
 * **No segment yet.** The design pairs Items with Blueprints behind one; the Blueprints half is its
 * own slice (owner decision, 2026-08-23), and a segment with one reachable tab would be a control
 * that does nothing.
 *
 * @param state what to draw.
 * @param onQueryChanged the search box changed.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the next page was asked for.
 * @param onCreate the new-entry action was taken.
 * @param onEdit a row was tapped.
 * @param onDelete a row's delete action was taken.
 * @param modifier layout modifier.
 * @param selection the bulk actions of design ch. 17 artboard 4, or `null` where they are not
 *   wired — which leaves the list exactly as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun PersonalInventoryScreen(
    state: PersonalInventoryState,
    onQueryChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (PersonalItem) -> Unit,
    onDelete: (PersonalItem) -> Unit,
    modifier: Modifier = Modifier,
    selection: PersonalSelectionActions? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.online) {
                OfflineBand()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(R.string.personal_inventory_search),
                )
            }
            when (state.phase) {
                is PersonalInventoryPhase.Loading -> {
                    KrtLoadingIndicator(
                        text = stringResource(R.string.personal_inventory_title),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is PersonalInventoryPhase.Failed -> {
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
                            title = stringResource(R.string.personal_inventory_error_title),
                            message = stringResource(R.string.personal_inventory_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                        )
                    }
                }

                is PersonalInventoryPhase.Ready -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.items.isEmpty()) {
                            KrtRefreshableFill {
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_crate,
                                    title = stringResource(R.string.personal_inventory_empty_title),
                                    message =
                                        stringResource(
                                            if (state.query.isBlank()) {
                                                R.string.personal_inventory_empty_message
                                            } else {
                                                R.string.personal_inventory_empty_filtered_message
                                            },
                                        ),
                                    modifier = Modifier.padding(KrtSpacing.lg),
                                )
                            }
                        } else {
                            ItemList(
                                state = state,
                                onLoadMore = onLoadMore,
                                onEdit = onEdit,
                                onDelete = onDelete,
                                selection = selection,
                            )
                        }
                    }
                }
            }
        }
        // „FAB und Bottom-Nav weichen der Aktionsleiste" (design ch. 02 §4): while a selection
        // runs, the bar owns the bottom of the screen and the FAB steps aside.
        // The bar also survives a finished deletion, because it carries the one number a member
        // cannot reconstruct: how many rows were skipped („Leiste bleibt, Ergebnis nennt
        // gelöscht/übersprungen", artboard 4).
        if ((state.selecting || state.bulkResult != null) && selection != null) {
            SelectionActionBar(state = state, selection = selection)
            return@Box
        }
        // Disabled, not hidden: a member offline has to be able to see that the action exists
        // and why it cannot be taken, which a missing control cannot say.
        KrtFab(
            iconRes = DesignR.drawable.ic_krt_plus,
            label = stringResource(R.string.personal_inventory_create),
            onClick = onCreate,
            enabled = state.online,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(KrtSpacing.lg)
                    .padding(bottom = LocalKrtBottomBarInset.current)
                    .testTag(PERSONAL_INVENTORY_CREATE_TAG),
        )
    }
}

/**
 * The bulk actions of design ch. 17 artboard 4.
 *
 * A bag rather than four parameters: they travel together from the route to the list and the bar.
 *
 * @property onToggle a row was long-pressed, or tapped while the mode runs.
 * @property onSelectAll „Alles wählen".
 * @property onClear „Aufheben".
 * @property onDelete the bar's delete action.
 */
data class PersonalSelectionActions(
    val onToggle: (PersonalItem) -> Unit,
    val onSelectAll: () -> Unit,
    val onClear: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * The bottom bar the selection mode owns.
 *
 * It exists only while something is selected, which is what makes the mode self-evident — nothing
 * to leave, nothing to notice you are in (design ch. 02 §4, taken over unchanged from the Lager).
 *
 * @param state what the screen holds.
 * @param selection the four actions.
 */
@Composable
private fun BoxScope.SelectionActionBar(
    state: PersonalInventoryState,
    selection: PersonalSelectionActions,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        KrtBottomCtaBar {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val result = state.bulkResult
                if (result == null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.selection.size.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = KrtPalette.White,
                        )
                        Text(
                            text = stringResource(R.string.personal_inventory_selected_word),
                            style = MaterialTheme.typography.labelSmall,
                            color = KrtPalette.TextMuted,
                        )
                    }
                } else {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.personal_inventory_bulk_result,
                                result.deleted,
                                result.deleted,
                                result.skipped,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_select_all),
                    onClick = selection.onSelectAll,
                )
                KrtGhostButton(
                    text = stringResource(R.string.inventory_selection_clear),
                    onClick = selection.onClear,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_delete),
                    onClick = selection.onDelete,
                    iconRes = DesignR.drawable.ic_krt_trash,
                    enabled = state.online && !state.deleting,
                    modifier = Modifier.testTag(PERSONAL_INVENTORY_BULK_TAG),
                )
            }
        }
    }
}

/**
 * The bulk deletion's confirmation.
 *
 * A danger modal naming the count, and **no undo** — unlike the inbox, whose undo hangs on a
 * server row that is gone here (design ch. 17 artboard 4).
 *
 * @param count how many rows.
 * @param busy whether the deletion is running.
 * @param onConfirm it was accepted.
 * @param onDismiss it was dismissed.
 */
@Composable
fun PersonalBulkDeleteModal(
    count: Int,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.personal_inventory_bulk_delete_title),
        confirmText = stringResource(R.string.personal_inventory_delete),
        onConfirm = { if (!busy) onConfirm() },
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        modifier = Modifier.testTag(PERSONAL_INVENTORY_BULK_MODAL_TAG),
    ) {
        Text(
            text = pluralStringResource(R.plurals.personal_inventory_bulk_delete_body, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

/**
 * The rows.
 *
 * @param state what to draw.
 * @param onLoadMore the next page was asked for.
 * @param onEdit a row was tapped.
 * @param onDelete a row's delete action was taken.
 * @param selection the bulk actions, or `null` where they are not wired.
 */
@Composable
private fun ItemList(
    state: PersonalInventoryState,
    onLoadMore: () -> Unit,
    onEdit: (PersonalItem) -> Unit,
    onDelete: (PersonalItem) -> Unit,
    selection: PersonalSelectionActions? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(PERSONAL_INVENTORY_LIST_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        items(state.items, key = { it.id }) { item ->
            ItemRow(
                item = item,
                online = state.online,
                // While the mode runs a tap picks rather than edits: two meanings for one tap is
                // how a member deletes the row they meant to open.
                onEdit = {
                    if (state.selecting && selection != null) {
                        selection.onToggle(item)
                    } else {
                        onEdit(item)
                    }
                },
                onDelete = { onDelete(item) },
                selected = item.id in state.selection,
                onLongPress = selection?.let { { it.onToggle(item) } },
            )
            KrtHairlineRule()
        }
        if (state.hasMore) {
            item(key = "more") {
                LaunchedEffect(state.items.size) { onLoadMore() }
                KrtLoadingIndicator(
                    text = stringResource(R.string.personal_inventory_title),
                    modifier = Modifier.fillMaxWidth().heightIn(min = LOADING_ROW_HEIGHT),
                )
            }
        } else {
            item(key = "end") {
                KrtEndOfList(text = stringResource(R.string.personal_inventory_end_of_list))
            }
        }
    }
}

/** How tall the "loading the next page" row is. */
private val LOADING_ROW_HEIGHT = 64.dp

/**
 * One entry.
 *
 * The whole row opens the editor; deleting has its own action, because a mis-tap that edits is
 * recoverable and a mis-tap that deletes is not.
 *
 * @param item the entry.
 * @param online whether writes are possible.
 * @param onEdit opens the editor.
 * @param onDelete asks to delete.
 * @param selected whether the row is in the selection.
 * @param onLongPress starts or extends the selection, or `null` where it is not wired.
 */
@Composable
private fun ItemRow(
    item: PersonalItem,
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    selected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    // A card, not a padded Row: every design chapter draws its list items as bordered tiles.
    // See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = online,
                    onClick = onEdit,
                    // The long press is what starts the mode (design ch. 02 §4); the card's own
                    // `onClick` cannot carry it, so the whole gesture moves onto the modifier.
                    onLongClick = onLongPress,
                ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                KrtSelectionCheckbox(checked = true)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Amount and unit, as artboard 09.4 sets them: the figure bright, the unit dimmed
            // beside it. A bare "24" leaves a member to guess whether it is pieces or SCU, and on
            // an item list it is always pieces — which is exactly why saying so costs nothing.
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = item.quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                )
                Text(
                    text = stringResource(R.string.personal_inventory_unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            // Icon buttons, not a labelled "LÖSCHEN": the artboard's row ends in a 44 dp pencil,
            // and a wide destructive label made deletion the loudest thing on every row of a list
            // whose usual action is a correction.
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_edit,
                label = stringResource(R.string.personal_inventory_edit),
                onClick = onEdit,
                enabled = online,
                modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
            )
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_trash,
                label = stringResource(R.string.personal_inventory_delete),
                onClick = onDelete,
                enabled = online,
                modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
            )
        }
    }
}

/**
 * The row's second line: where it is, and the note when there is one.
 *
 * @return the line, never empty — a place the server could not resolve reads as a dash rather than
 *   leaving the row looking half-rendered.
 */
@Composable
private fun PersonalItem.subtitle(): String {
    val place = locationName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.value_absent)
    return note?.takeIf { it.isNotBlank() }?.let { "$place · $it" } ?: place
}
