/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the list. */
const val PERSONAL_INVENTORY_LIST_TAG: String = "personal-inventory-list"

/** Test handle for the "new entry" action. */
const val PERSONAL_INVENTORY_CREATE_TAG: String = "personal-inventory-create"

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
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
                            )
                        }
                    }
                }
            }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(PERSONAL_INVENTORY_LIST_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
    ) {
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
 */
@Composable
private fun ItemRow(
    item: PersonalItem,
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // A card, not a padded Row: every design chapter draws its list items as bordered tiles.
    // See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit.takeIf { online }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
