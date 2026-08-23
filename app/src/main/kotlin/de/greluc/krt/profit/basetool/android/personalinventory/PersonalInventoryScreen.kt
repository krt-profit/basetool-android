/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the list. */
const val PERSONAL_INVENTORY_LIST_TAG: String = "personal-inventory-list"

/** Test handle for the "new entry" action. */
const val PERSONAL_INVENTORY_CREATE_TAG: String = "personal-inventory-create"

/** How faded a write action is while there is no network (design ch. 14). */
private const val DISABLED_WRITE_ALPHA = 0.45f

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
 * @param onLoadMore the next page was asked for.
 * @param onCreate the new-entry action was taken.
 * @param onEdit a row was tapped.
 * @param onDelete a row's delete action was taken.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInventoryScreen(
    state: PersonalInventoryState,
    onQueryChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (PersonalItem) -> Unit,
    onDelete: (PersonalItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md),
            horizontalArrangement = Arrangement.End,
        ) {
            // Disabled, not hidden: a member offline has to be able to see that the action exists
            // and why it cannot be taken, which a missing button cannot say.
            KrtCtaButton(
                text = stringResource(R.string.personal_inventory_create),
                onClick = onCreate,
                modifier =
                    Modifier
                        .testTag(PERSONAL_INVENTORY_CREATE_TAG)
                        .alpha(if (state.online) 1f else DISABLED_WRITE_ALPHA),
                enabled = state.online,
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
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_crate,
                    title = stringResource(R.string.personal_inventory_error_title),
                    message = stringResource(R.string.personal_inventory_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
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
                        )
                    }
                }
            }
        }
    }
}

/**
 * The band that says why the write actions are greyed out.
 */
@Composable
private fun OfflineBand() {
    Text(
        text = stringResource(R.string.offline_writes_disabled),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
    )
}

/**
 * The rows.
 *
 * @param state what to draw.
 * @param onLoadMore the next page was asked for.
 * @param onEdit a row was tapped.
 * @param onDelete a row's delete action was taken.
 */
@Composable
private fun ItemList(
    state: PersonalInventoryState,
    onLoadMore: () -> Unit,
    onEdit: (PersonalItem) -> Unit,
    onDelete: (PersonalItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(PERSONAL_INVENTORY_LIST_TAG)) {
        items(state.items, key = { it.id }) { item ->
            ItemRow(
                item = item,
                online = state.online,
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
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
                Text(
                    text = stringResource(R.string.personal_inventory_end_of_list),
                    style = MaterialTheme.typography.labelSmall,
                    color = KrtPalette.TextMuted,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = KrtSpacing.md),
                )
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
 */
@Composable
private fun ItemRow(
    item: PersonalItem,
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = online, onClick = onEdit)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
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
        Text(
            text = item.quantity.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        KrtGhostButton(
            text = stringResource(R.string.personal_inventory_delete),
            onClick = onDelete,
            modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
            enabled = online,
        )
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

/**
 * "Mein Inventar", bound to its view model.
 *
 * The three overlays live here rather than inside [PersonalInventoryScreen] so the screen itself
 * stays a pure function of its state and can be rendered in a test without a view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun PersonalInventoryRoute(
    viewModel: PersonalInventoryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    PersonalInventoryScreen(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        onCreate = viewModel::onCreate,
        onEdit = viewModel::onEdit,
        onDelete = viewModel::onDeleteRequested,
        modifier = modifier,
    )

    (state.editor as? EditorState.Open)?.let { editor ->
        PersonalInventoryEditor(
            editor = editor,
            locations = state.locations,
            onName = viewModel::onNameChanged,
            onQuantity = viewModel::onQuantityChanged,
            onStep = viewModel::onQuantityStepped,
            onNote = viewModel::onNoteChanged,
            onLocationQuery = viewModel::onLocationQueryChanged,
            onLocationChosen = viewModel::onLocationChosen,
            onSave = viewModel::onSave,
            onDismiss = viewModel::onEditorDismissed,
        )
    }

    state.pendingDelete?.let { item ->
        PersonalInventoryDeleteModal(
            item = item,
            deleting = state.deleting,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }
}
