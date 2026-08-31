/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.exchange

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.BoardEntry
import de.greluc.krt.profit.basetool.android.core.data.BoardSide
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.ReleasableStock
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.ui.rememberRootGridState
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import java.time.Instant
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the board list. */
const val BOARD_LIST_TAG: String = "board-list"

/** Test handle for one board row. */
const val BOARD_ROW_TAG: String = "board-row"

/** Test handle for the „Ich kann liefern" toggle of a row. */
const val BOARD_SIGNAL_TAG: String = "board-signal"

/** Test handle for a row's withdraw action. */
const val BOARD_WITHDRAW_TAG: String = "board-withdraw"

/** Test handle for the Material/Item switch on a create sheet. */
const val BOARD_KIND_TAG: String = "board-kind"

/** Test handle for the item half's product picker. */
const val BOARD_PRODUCT_FIELD_TAG: String = "board-product-field"

/** Test handle for „Eintrag bearbeiten". */
const val BOARD_EDIT_SHEET_TAG: String = "board-edit-sheet"

/** Test handle for its amount field. */
const val BOARD_EDIT_AMOUNT_TAG: String = "board-edit-amount"

/** Test handle for the withdrawal confirmation. */
const val BOARD_WITHDRAW_MODAL_TAG: String = "board-withdraw-modal"

/** Test handle for the create action. */
const val BOARD_CREATE_TAG: String = "board-create"

/** Test handle for the open sheet. */
const val BOARD_SHEET_TAG: String = "board-sheet"

/** Test handle for the sheet's submit action. */
const val BOARD_SUBMIT_TAG: String = "board-submit"

/** Test handle for the privacy line chapter 10 makes part of the design. */
const val BOARD_PRIVACY_TAG: String = "board-privacy"

/**
 * How many card columns a tablet's board shows.
 *
 * Two, and **two at every width** — ratified by design ch. 18 §3 (E9): offers left, requests right,
 * 480 dp each with a 24 dp gutter, and past 1600 dp the columns grow rather than a third appearing.
 * A third at 1280 dp would put a card below the width its own row of name, figures and chips needs,
 * which is narrower than the phone's.
 */
private const val BOARD_WIDE_COLUMNS = 2

/** Separator between the parts of a row's second line. */
private const val SEPARATOR = " · "

/**
 * The Materialbörse (design spec ch. 10 §3–4).
 *
 * @param state what to draw.
 * @param onSideChanged the segment was switched.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry.
 * @param onLoadMore the next page was asked for.
 * @param onSignalToggled „Ich kann liefern" was tapped on a row.
 * @param onWithdraw a own row's Zurückziehen was tapped.
 * @param onCreate the create action was tapped.
 * @param sheet the sheet host, rendered above the list when one is open.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialBoardScreen(
    state: MaterialBoardState,
    onSideChanged: (BoardSide) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onSignalToggled: (BoardEntry) -> Unit,
    onWithdraw: (BoardEntry) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    sheet: @Composable () -> Unit = {},
) {
    when (state.phase) {
        is BoardPhase.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.board_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is BoardPhase.Failed -> {
            val retryIn = state.retryIn
            if (retryIn != null) {
                KrtRetryCountdown(
                    secondsLeft = retryIn,
                    title = stringResource(R.string.retry_busy_title),
                    message = stringResource(R.string.retry_busy_message, retryIn),
                    retryLabel = stringResource(R.string.retry_now),
                    onRetry = onRetryNow,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_swap,
                    title = stringResource(R.string.board_error_title),
                    message = stringResource(R.string.board_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            }
        }

        is BoardPhase.Ready -> {
            Box(modifier = modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!state.online) {
                        OfflineBand()
                    }
                    KrtSegmentedControl(
                        options =
                            listOf(
                                stringResource(R.string.board_side_offers),
                                stringResource(R.string.board_side_requests),
                            ),
                        selectedIndex = if (state.side == BoardSide.OFFERS) 0 else 1,
                        onSelect = {
                            onSideChanged(if (it == 0) BoardSide.OFFERS else BoardSide.REQUESTS)
                        },
                        stretch = true,
                        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
                    )
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.entries.isEmpty()) {
                            KrtRefreshableFill {
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_swap,
                                    title = stringResource(R.string.board_empty_title),
                                    message = stringResource(R.string.board_empty_message),
                                    modifier = Modifier.padding(KrtSpacing.s16),
                                )
                            }
                        } else if (isWideWindow()) {
                            BoardGrid(
                                state = state,
                                onSignalToggled = onSignalToggled,
                                onWithdraw = onWithdraw,
                                onLoadMore = onLoadMore,
                            )
                        } else {
                            BoardColumn(
                                state = state,
                                onSignalToggled = onSignalToggled,
                                onWithdraw = onWithdraw,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
                // The label follows the segment: on "Gesuche" the action creates a request, not
                // an offer, and a FAB that said "Angebot" there would be lying about what it does.
                KrtFab(
                    iconRes = DesignR.drawable.ic_krt_plus,
                    label =
                        stringResource(
                            if (state.side == BoardSide.OFFERS) {
                                R.string.board_new_offer
                            } else {
                                R.string.board_new_request
                            },
                        ),
                    onClick = onCreate,
                    enabled = state.writable,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(KrtSpacing.s16)
                            .padding(bottom = LocalKrtBottomBarInset.current)
                            .testTag(BOARD_CREATE_TAG),
                )
            }
            sheet()
        }
    }
}

/**
 * The board as one column of cards, which is what a phone gets.
 *
 * @param state what the screen holds.
 * @param onSignalToggled a row's interest was signalled or withdrawn.
 * @param onWithdraw a row's own offer or request is to be taken down.
 * @param onLoadMore the next page is wanted.
 */
@Composable
private fun BoardColumn(
    state: MaterialBoardState,
    onSignalToggled: (BoardEntry) -> Unit,
    onWithdraw: (BoardEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(BOARD_LIST_TAG),
        contentPadding = PaddingValues(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            BoardRow(
                entry = entry,
                busy = state.busyEntryId == entry.id,
                writable = state.writable,
                onSignalToggled = { onSignalToggled(entry) },
                onWithdraw = { onWithdraw(entry) },
            )
            KrtHairlineRule()
        }
        item(key = "privacy") { BoardPrivacyNote() }
        item(key = "footer") { BoardFooter(state = state, onLoadMore = onLoadMore) }
    }
}

/**
 * „Übergabe & Ort bleiben off-tool und privat — die Börse vermittelt nur Interesse."
 *
 * Part of the design, not decoration: chapter 10 states it as copy, because the board deliberately
 * carries no place and no handover and a member has to be able to tell that from the screen rather
 * than from its absence.
 *
 * **Under the cards**, where artboard 10-3 puts it — above them it pushed the first offer down by
 * two lines to say something about the offers nobody had read yet.
 */
@Composable
private fun BoardPrivacyNote() {
    Text(
        text = stringResource(R.string.board_privacy_note),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(top = KrtSpacing.s4).testTag(BOARD_PRIVACY_TAG),
    )
}

/**
 * The board as two columns of cards, which is what a tablet gets.
 *
 * A card is self-contained — the material, the member, the figures and the action — so two fit side
 * by side. Stretched to a tablet's full width a single column packed all of that into the left
 * quarter and pinned one chip at the right edge, leaving about three quarters of every card empty
 * (design round 8 §5, ruled 2026-08-28).
 *
 * No hairline between the cards here: a rule under one card of a pair reads as a divider across the
 * row it is not in. The card border is the separation a grid needs.
 *
 * @param state what the screen holds.
 * @param onSignalToggled a row's interest was signalled or withdrawn.
 * @param onWithdraw a row's own offer or request is to be taken down.
 * @param onLoadMore the next page is wanted.
 */
@Composable
private fun BoardGrid(
    state: MaterialBoardState,
    onSignalToggled: (BoardEntry) -> Unit,
    onWithdraw: (BoardEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(BOARD_WIDE_COLUMNS),
        state = rememberRootGridState(),
        modifier = Modifier.fillMaxSize().testTag(BOARD_LIST_TAG),
        contentPadding = PaddingValues(KrtSpacing.s12),
        // 10 dp down the column and a 24 dp gutter across it — design ch. 18 §3 (E9). They differ
        // on purpose: the gutter separates two columns, the other is the rhythm within one.
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s10),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s24),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            BoardRow(
                entry = entry,
                busy = state.busyEntryId == entry.id,
                writable = state.writable,
                onSignalToggled = { onSignalToggled(entry) },
                onWithdraw = { onWithdraw(entry) },
            )
        }
        // The note and the footer are one thing about the whole board, not about one column of it.
        item(key = "privacy", span = { GridItemSpan(maxLineSpan) }) { BoardPrivacyNote() }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            BoardFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

/**
 * What closes the board: the next page, or the end of it.
 *
 * @param state what the screen holds.
 * @param onLoadMore the next page is wanted.
 */
@Composable
private fun BoardFooter(
    state: MaterialBoardState,
    onLoadMore: () -> Unit,
) {
    if (state.hasMore) {
        KrtLoadMore(
            text =
                pluralStringResource(
                    R.plurals.board_load_more,
                    state.entries.size,
                    state.entries.size,
                ),
            onClick = onLoadMore,
            enabled = !state.loadingMore,
            modifier = Modifier.padding(KrtSpacing.s12),
        )
    } else {
        KrtEndOfList(
            text = stringResource(R.string.board_end_of_list),
            modifier = Modifier.padding(KrtSpacing.s12),
        )
    }
}

/**
 * One board row.
 *
 * @param entry the row.
 * @param busy whether a write on this row is in flight.
 * @param writable whether writes may be offered at all.
 * @param onSignalToggled „Ich kann liefern" was tapped.
 * @param onWithdraw Zurückziehen was tapped.
 */
@Composable
private fun BoardRow(
    entry: BoardEntry,
    busy: Boolean,
    writable: Boolean,
    onSignalToggled: () -> Unit,
    onWithdraw: () -> Unit,
) {
    // A card, not a padded Column: every design chapter draws its list items as bordered
    // tiles, and the app was drawing lines of text. See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(
        modifier = Modifier.fillMaxWidth().testTag(BOARD_ROW_TAG),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.materialName.ifBlank { stringResource(R.string.board_unnamed) },
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The amount belongs beside the name, right-aligned and loud, because it is what a
            // board is scanned for. It used to sit inside a grey run of three facts where the
            // quantity, the quality and the pledge count all read the same weight (artboard 3).
            BoardAmount(entry)
            entry.ownerOrgUnits.take(MAX_BADGES).forEach { badge ->
                KrtChip(text = badge, tone = KrtChipTone.Muted)
            }
        }
        Text(
            text = ownerLine(entry),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        detailLine(entry)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        entry.remark?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                maxLines = MAX_REMARK_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Owner-only, and the server decides it: the list is `null` for everybody else, so this
        // renders nothing rather than an empty „Zusagen" heading that would imply nobody answered.
        entry.interestedHandles?.takeIf { it.isNotEmpty() }?.let { handles ->
            KrtSectionTitle(text = stringResource(R.string.board_supporters))
            Text(
                text = handles.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        RowActions(
            entry = entry,
            enabled = writable && !busy,
            onSignalToggled = onSignalToggled,
            onWithdraw = onWithdraw,
        )
    }
}

/**
 * The row's action, which is a different one for the caller's own entries.
 *
 * @param entry the row.
 * @param enabled whether a write may be sent.
 * @param onSignalToggled „Ich kann liefern" was tapped.
 * @param onWithdraw Zurückziehen was tapped.
 */
@Composable
private fun RowActions(
    entry: BoardEntry,
    enabled: Boolean,
    onSignalToggled: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        if (entry.mine) {
            // Design ch. 17 artboard 3 moved both of the member's own actions into one sheet:
            // „Bisher gab es nur «Zurückziehen» und kein Update — beides liegt jetzt hier." So the
            // row opens the sheet, and the withdrawal lives inside it next to „Speichern" — where
            // the interested members it affects are also listed.
            KrtOutlineButton(
                text = stringResource(R.string.board_edit_entry),
                onClick = onWithdraw,
                enabled = enabled,
                iconRes = DesignR.drawable.ic_krt_edit,
                modifier = Modifier.fillMaxWidth().testTag(BOARD_WITHDRAW_TAG),
            )
        } else {
            val label =
                if (entry.viewerInterested) {
                    R.string.board_signal_off
                } else {
                    R.string.board_signal_on
                }
            // Outline when off, ghost when on: the design's toggle reads as pressed once the
            // member has committed, and a filled CTA on every row would make the list shout.
            // Full width, with the glyph: artboard 10.3 gives the signal the whole card foot,
            // because it is the only thing a member does on this screen and a button sized to its
            // own label reads as one option among several.
            if (entry.viewerInterested) {
                KrtGhostButton(
                    text = stringResource(label),
                    onClick = onSignalToggled,
                    enabled = enabled,
                    iconRes = DesignR.drawable.ic_krt_check,
                    modifier = Modifier.fillMaxWidth().testTag(BOARD_SIGNAL_TAG),
                )
            } else {
                KrtOutlineButton(
                    text = stringResource(label),
                    onClick = onSignalToggled,
                    enabled = enabled,
                    iconRes = DesignR.drawable.ic_krt_login,
                    modifier = Modifier.fillMaxWidth().testTag(BOARD_SIGNAL_TAG),
                )
            }
        }
    }
}

/** At most this many affiliation badges fit a phone row. */
private const val MAX_BADGES = 2

/** A remark is a note, not an essay; the rest is on the web. */
private const val MAX_REMARK_LINES = 2

/**
 * The row's „von X · wann" line.
 *
 * @param entry the row.
 * @return the line.
 */
@Composable
private fun ownerLine(entry: BoardEntry): String {
    val who =
        entry.ownerName.ifBlank { stringResource(R.string.board_unknown_owner) }
    val prefix =
        if (entry.side == BoardSide.REQUESTS) {
            stringResource(R.string.board_requested_by, who)
        } else {
            who
        }
    return listOfNotNull(prefix, entry.postedAt?.relativeToNow()).joinToString(SEPARATOR)
}

/**
 * How long ago an ISO timestamp is, on the ladder every screen in the app shares.
 *
 * The value arrives as a string here rather than as an `Instant`, so the parse happens on the way
 * in. An unparseable value is shown as it came rather than dropped: a server that changed its
 * format is something to see, not to hide.
 *
 * @return the timestamp, or the raw string when it does not parse.
 */
@Composable
private fun String.relativeToNow(): String {
    val instant = runCatching { Instant.parse(this) }.getOrNull() ?: return this
    return instant.relativeToNow()
}

/**
 * The row's quantity, as the header's right-hand figure.
 *
 * The figure carries the weight and the unit stays quiet beside it: „240" is the thing being
 * compared across rows, „SCU" only says what kind of 240 it is.
 *
 * @param entry the row.
 */
@Composable
private fun BoardAmount(entry: BoardEntry) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = formatAmount(entry.amount),
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.White,
            maxLines = 1,
        )
        Text(
            text =
                stringResource(
                    if (entry.unitIsPiece) R.string.board_unit_piece else R.string.board_unit_scu,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            maxLines = 1,
        )
    }
}

/**
 * What is left of the row's facts once the quantity has moved into the header.
 *
 * **Never a hardcoded SCU.** An item counted in pieces and labelled „SCU" is a quantity a member
 * would act on — the one thing on this screen that could cause a wrong handover off-tool.
 *
 * @param entry the row.
 * @return the line.
 */
@Composable
private fun detailLine(entry: BoardEntry): String? {
    val quality =
        entry.quality?.let {
            if (entry.side == BoardSide.REQUESTS) {
                stringResource(R.string.board_min_quality, it)
            } else {
                stringResource(R.string.board_quality, it)
            }
        }
    val supporters =
        pluralStringResource(R.plurals.board_interest_count, entry.interestCount, entry.interestCount)
    return listOfNotNull(quality, supporters).joinToString(SEPARATOR).takeIf { it.isNotEmpty() }
}

/**
 * „Gesuch erstellen" (design spec ch. 10 §4).
 *
 * @param sheet what the member has typed so far.
 * @param saving whether the create is in flight.
 * @param onEdit a field changed.
 * @param onQueryChanged the material field changed.
 * @param onPicked a search result was taken.
 * @param onSubmit publish was pressed.
 * @param onDismiss the sheet was closed.
 * @param onKind the Material/Item switch was moved.
 * @param onProductQuery the item field changed.
 * @param onProductPicked a product was taken.
 */
@Composable
@Suppress("LongParameterList")
fun NewRequestSheet(
    sheet: BoardSheet.NewRequest,
    saving: Boolean,
    onEdit: ((BoardSheet.NewRequest) -> BoardSheet.NewRequest) -> Unit,
    onQueryChanged: (String) -> Unit,
    onPicked: (MaterialOption) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onKind: (BoardKind) -> Unit = {},
    onProductQuery: (String) -> Unit = {},
    onProductPicked: (BlueprintProduct) -> Unit = {},
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.board_new_request),
        modifier = Modifier.testTag(BOARD_SHEET_TAG),
    ) {
        val kind = sheet.kind
        // The switch stands at the very top and changes only the middle fields; the frame —
        // remark and CTA — is the same on both halves (design ch. 17, artboards 1 and 2).
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.board_kind_material),
                    stringResource(R.string.board_kind_item),
                ),
            selectedIndex = if (kind == BoardKind.ITEM) 1 else 0,
            onSelect = { onKind(if (it == 1) BoardKind.ITEM else BoardKind.MATERIAL) },
            // Stretched, like every other two-option switch in the app: without it each segment is
            // the fixed 52 dp box, and „MATERIAL" broke across two lines inside a control one line
            // high — found on a device in the Gesuch- and the Angebot-Sheet.
            stretch = true,
            modifier = Modifier.fillMaxWidth().testTag(BOARD_KIND_TAG),
        )
        Text(
            text = stringResource(R.string.board_new_request_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        if (kind == BoardKind.ITEM) {
            ProductField(
                shown = sheet.productName,
                selectedKey = sheet.productKey,
                products = sheet.products,
                onQuery = onProductQuery,
                onPicked = onProductPicked,
            )
        }
        var open by rememberSaveable { mutableStateOf(false) }
        if (kind == BoardKind.MATERIAL) {
            KrtCombobox(
                query = sheet.materialName,
                onQueryChange = {
                    onQueryChanged(it)
                    open = true
                },
                options = sheet.matches.map { KrtOption(it.id, it.name) },
                onSelect = { option ->
                    sheet.matches.firstOrNull { it.id == option.value }?.let(onPicked)
                    open = false
                },
                expanded = open && sheet.matches.isNotEmpty(),
                onExpandedChange = { open = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.board_field_material),
                placeholder = stringResource(R.string.board_field_material_hint),
                selectedValue = sheet.materialId,
            )
        }
        // Menge and Min. Qualitaet share a row (artboard 10.4): both are short numbers about the
        // same stack, and full width each they pushed the CTA off the sheet on a phone.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.Bottom,
        ) {
            KrtTextField(
                value = sheet.amount,
                onValueChange = { value -> onEdit { it.copy(amount = value) } },
                label = stringResource(R.string.board_field_amount),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            KrtTextField(
                value = sheet.minQuality,
                onValueChange = { value -> onEdit { it.copy(minQuality = value) } },
                label = stringResource(R.string.board_field_min_quality),
                placeholder = stringResource(R.string.board_field_min_quality_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        KrtTextField(
            value = sheet.remark,
            onValueChange = { value -> onEdit { it.copy(remark = value) } },
            label = stringResource(R.string.board_field_remark),
            placeholder = stringResource(R.string.board_field_remark_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        SheetActions(
            submit = stringResource(R.string.board_publish_request),
            enabled = sheet.submittable && !saving,
            saving = saving,
            onSubmit = onSubmit,
            onDismiss = onDismiss,
        )
    }
}

/**
 * „Angebot erstellen" — the same sheet, picking from the caller's own stock.
 *
 * @param sheet what the member has typed so far.
 * @param saving whether the create is in flight.
 * @param onEdit a field changed.
 * @param onSubmit publish was pressed.
 * @param onDismiss the sheet was closed.
 * @param onKind the Material/Item switch was moved.
 * @param onProductQuery the item field changed.
 * @param onProductPicked a product was taken.
 */
@Composable
@Suppress("LongParameterList")
fun NewOfferSheet(
    sheet: BoardSheet.NewOffer,
    saving: Boolean,
    onEdit: ((BoardSheet.NewOffer) -> BoardSheet.NewOffer) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onKind: (BoardKind) -> Unit = {},
    onProductQuery: (String) -> Unit = {},
    onProductPicked: (BlueprintProduct) -> Unit = {},
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.board_new_offer),
        modifier = Modifier.testTag(BOARD_SHEET_TAG),
    ) {
        val kind = sheet.kind
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.board_kind_material),
                    stringResource(R.string.board_kind_item),
                ),
            selectedIndex = if (kind == BoardKind.ITEM) 1 else 0,
            onSelect = { onKind(if (it == 1) BoardKind.ITEM else BoardKind.MATERIAL) },
            // Stretched, like every other two-option switch in the app: without it each segment is
            // the fixed 52 dp box, and „MATERIAL" broke across two lines inside a control one line
            // high — found on a device in the Gesuch- and the Angebot-Sheet.
            stretch = true,
            modifier = Modifier.fillMaxWidth().testTag(BOARD_KIND_TAG),
        )
        Text(
            text =
                stringResource(
                    if (kind == BoardKind.ITEM) {
                        R.string.board_new_offer_item_hint
                    } else {
                        R.string.board_new_offer_hint
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        if (kind == BoardKind.ITEM) {
            ProductField(
                shown = sheet.productName,
                selectedKey = sheet.productKey,
                products = sheet.products,
                onQuery = onProductQuery,
                onPicked = onProductPicked,
            )
        }
        when {
            kind == BoardKind.ITEM -> {
                // An item offer binds no stock row: items live in the personal inventory and the
                // endpoint takes a product key. Nothing to pick from here.
            }

            sheet.loadingStock -> {
                Text(
                    text = stringResource(R.string.board_stock_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }

            sheet.stock.isEmpty() -> {
                Text(
                    text = stringResource(R.string.board_stock_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }

            else -> {
                sheet.stock.forEach { StockRow(it, sheet.picked, onEdit) }
            }
        }
        KrtTextField(
            value = sheet.amount,
            onValueChange = { value -> onEdit { it.copy(amount = value) } },
            label = stringResource(R.string.board_field_amount),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        KrtTextField(
            value = sheet.remark,
            onValueChange = { value -> onEdit { it.copy(remark = value) } },
            label = stringResource(R.string.board_field_remark),
            placeholder = stringResource(R.string.board_field_remark_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        SheetActions(
            submit = stringResource(R.string.board_publish_offer),
            enabled = sheet.submittable && !saving,
            saving = saving,
            onSubmit = onSubmit,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The item half's product picker.
 *
 * A picker rather than free text, for the reason the material field gives: the two item writes
 * address a product by its **key**, and a typed name carries none.
 *
 * @param shown what is in the field.
 * @param selectedKey the picked product's key, or `null`.
 * @param products the candidates.
 * @param onQuery a search was typed.
 * @param onPicked a result was taken.
 */
@Composable
private fun ProductField(
    shown: String,
    selectedKey: String?,
    products: List<BlueprintProduct>,
    onQuery: (String) -> Unit,
    onPicked: (BlueprintProduct) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    KrtCombobox(
        query = shown,
        onQueryChange = {
            onQuery(it)
            open = true
        },
        // The catalogue's own products, the caller's blueprints first: „Vorschläge zeigen erst
        // deinen Bestand" (artboard 1). The manufacturer rides along because two products can
        // share a name across makers.
        // The variant count rides in the label, because the artboard's variant *select* has no wire
        // field: a product key already identifies the product. Naming the count is what lets a
        // member put the variant in the remark, which the help text below says in so many words.
        options =
            products.map { product ->
                KrtOption(
                    value = product.productKey,
                    label =
                        listOfNotNull(
                            product.name,
                            product.manufacturer,
                            product.variantCount
                                .takeIf { it > 1 }
                                ?.let { variantLabel(it) },
                        ).joinToString(SEPARATOR),
                )
            },
        onSelect = { option ->
            products.firstOrNull { it.productKey == option.value }?.let(onPicked)
            open = false
        },
        expanded = open && products.isNotEmpty(),
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth().testTag(BOARD_PRODUCT_FIELD_TAG),
        label = stringResource(R.string.board_field_item),
        placeholder = stringResource(R.string.board_field_item_hint),
        selectedValue = selectedKey,
    )
    // Ch. 17 ab. 1: „Der Ort der Übergabe und — falls nötig — die Variante gehören hierher: der
    // Vertrag hat für beides kein Feld." A remark that stands in for missing structure has to be
    // explained, or it reads as a free-text box nobody fills.
    Text(
        text = stringResource(R.string.board_field_item_help),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/**
 * „n Varianten" for a product that has more than one.
 *
 * @param count how many.
 * @return the label.
 */
@Composable
private fun variantLabel(count: Int): String =
    pluralStringResource(R.plurals.board_item_variants, count, count)

/**
 * „Eintrag bearbeiten" — design ch. 17 artboard 3.
 *
 * One sheet for an offer and a request. What is fixed is drawn locked with its reason rather than
 * removed: the material or item behind a row cannot change, because changing it would make the row
 * a different entry with the same id and the same interested members.
 *
 * @param sheet the row and what has been typed.
 * @param saving whether the write is in flight.
 * @param onEdit a field changed.
 * @param onSubmit „Speichern" was pressed.
 * @param onWithdraw „Zurückziehen" was pressed.
 * @param onDismiss the sheet was closed.
 */
@Composable
@Suppress("LongParameterList")
fun EditEntrySheet(
    sheet: BoardSheet.EditEntry,
    saving: Boolean,
    onEdit: ((BoardSheet.EditEntry) -> BoardSheet.EditEntry) -> Unit,
    onSubmit: () -> Unit,
    onWithdraw: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = sheet.entry
    KrtBottomSheet(
        onDismiss = onDismiss,
        title =
            stringResource(
                if (entry.side == BoardSide.OFFERS) {
                    R.string.board_edit_offer
                } else {
                    R.string.board_edit_request
                },
            ),
        modifier = Modifier.testTag(BOARD_EDIT_SHEET_TAG),
    ) {
        Text(
            text = entry.materialName,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        Text(
            text = stringResource(R.string.board_edit_fixed),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        KrtTextField(
            value = sheet.amount,
            onValueChange = { value -> onEdit { it.copy(amount = value) } },
            label = stringResource(R.string.board_field_amount),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(BOARD_EDIT_AMOUNT_TAG),
        )
        if (sheet.hasQuality) {
            KrtTextField(
                value = sheet.minQuality,
                onValueChange = { value -> onEdit { it.copy(minQuality = value) } },
                label = stringResource(R.string.board_field_min_quality),
                placeholder = stringResource(R.string.board_field_min_quality_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KrtTextField(
            value = sheet.remark,
            onValueChange = { value -> onEdit { it.copy(remark = value) } },
            label = stringResource(R.string.board_field_remark),
            placeholder = stringResource(R.string.board_field_remark_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        // The interested members belong in this sheet because withdrawing affects them — which is
        // the artboard's own reason for putting them here rather than on the row.
        entry.interestedHandles?.takeIf { it.isNotEmpty() }?.let { handles ->
            Text(
                text = stringResource(R.string.board_edit_interested, handles.joinToString(SEPARATOR)),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        KrtQuietDangerButton(
            text = stringResource(R.string.board_withdraw),
            onClick = onWithdraw,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().testTag(BOARD_WITHDRAW_TAG),
        )
        SheetActions(
            submit = stringResource(R.string.board_save),
            enabled = sheet.submittable && !saving,
            saving = saving,
            onSubmit = onSubmit,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The two buttons that close a Boerse sheet.
 *
 * Both artboards of chapter 10 end in „ABBRECHEN" beside the publish CTA, and it is the same pair
 * the Buchen and Schiff sheets already use. Publishing is org-wide and cannot be undone quietly, so
 * the way out is a button and not only a swipe a member has to know about.
 *
 * @param submit label of the publish button.
 * @param enabled whether the form may be submitted.
 * @param saving whether a create is in flight - both buttons rest while it is.
 * @param onSubmit publish was pressed.
 * @param onDismiss abort was pressed.
 */
@Composable
private fun SheetActions(
    submit: String,
    enabled: Boolean,
    saving: Boolean,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        KrtGhostButton(
            text = stringResource(R.string.personal_inventory_cancel),
            onClick = onDismiss,
            enabled = !saving,
        )
        KrtCtaButton(
            text = submit,
            onClick = onSubmit,
            enabled = enabled,
            iconRes = DesignR.drawable.ic_krt_save,
            modifier = Modifier.testTag(BOARD_SUBMIT_TAG),
        )
    }
}

/**
 * One of the caller's own stacks, offered as the stock suggestion of chapter 10.
 *
 * The place is shown because two stacks of the same material at different stations are otherwise
 * indistinguishable — and it stays on this sheet: nothing about a place reaches the board.
 *
 * @param stock the entry.
 * @param picked which entry is currently chosen.
 * @param onEdit picks this one and pre-fills the amount.
 */
@Composable
private fun StockRow(
    stock: ReleasableStock,
    picked: ReleasableStock?,
    onEdit: ((BoardSheet.NewOffer) -> BoardSheet.NewOffer) -> Unit,
) {
    val unit =
        stringResource(if (stock.unitIsPiece) R.string.board_unit_piece else R.string.board_unit_scu)
    val chosen = picked?.inventoryItemId == stock.inventoryItemId
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !stock.alreadyReleased) {
                    onEdit { it.copy(picked = stock, amount = stock.amount) }
                }
                .padding(vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                listOfNotNull(
                    stock.materialName,
                    stock.quality?.let { stringResource(R.string.board_quality, it) },
                    stock.locationName.takeIf { it.isNotBlank() },
                ).joinToString(SEPARATOR),
            style = MaterialTheme.typography.bodyMedium,
            color = if (stock.alreadyReleased) KrtPalette.TextMuted else KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatAmount(stock.amount)} $unit",
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.TextMuted,
        )
        if (chosen) {
            KrtChip(text = stringResource(R.string.board_picked), tone = KrtChipTone.Primary)
        }
    }
}

/**
 * „Zurückziehen" with interested members waiting.
 *
 * With nobody waiting the withdrawal happens straight away; this asks only when somebody would be
 * left standing, and names them — the artboard's own rule, and the reason the interested members
 * are listed in the sheet in the first place.
 *
 * @param entry the row.
 * @param busy whether the write is in flight.
 * @param onConfirm it was accepted.
 * @param onDismiss it was dismissed.
 */
@Composable
private fun WithdrawConfirmation(
    entry: BoardEntry,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.board_withdraw_title),
        confirmText = stringResource(R.string.board_withdraw),
        onConfirm = { if (!busy) onConfirm() },
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        modifier = Modifier.testTag(BOARD_WITHDRAW_MODAL_TAG),
    ) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.board_withdraw_body,
                    entry.interestCount,
                    entry.interestCount,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        entry.interestedHandles?.takeIf { it.isNotEmpty() }?.let { handles ->
            Text(
                text = handles.joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The board, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialBoardRoute(
    viewModel: MaterialBoardViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialBoardScreen(
        state = state,
        onSideChanged = viewModel::onSideChanged,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onSignalToggled = viewModel::onSignalToggled,
        // The own-row button opens the sheet; the withdrawal itself now lives inside it.
        onWithdraw = viewModel::onEditEntry,
        onCreate = {
            if (state.side == BoardSide.OFFERS) viewModel.onNewOffer() else viewModel.onNewRequest()
        },
        modifier = modifier,
    ) {
        when (val sheet = state.sheet) {
            // No sheet is the ordinary case and draws nothing. An empty block rather than `Unit`:
            // the `when` is a statement here, so a bare `Unit` is an unused expression and the
            // module compiles warnings as errors.
            is BoardSheet.None -> {}

            is BoardSheet.NewRequest -> {
                NewRequestSheet(
                    sheet = sheet,
                    saving = state.saving,
                    onEdit = viewModel::onRequestEdited,
                    onQueryChanged = viewModel::onMaterialQueryChanged,
                    onPicked = viewModel::onMaterialPicked,
                    onSubmit = viewModel::onRequestSubmitted,
                    onDismiss = viewModel::onSheetDismissed,
                    onKind = { picked -> viewModel.onRequestEdited { it.copy(kind = picked) } },
                    onProductQuery = viewModel::onProductQueryChanged,
                    onProductPicked = viewModel::onProductPicked,
                )
            }

            is BoardSheet.NewOffer -> {
                NewOfferSheet(
                    sheet = sheet,
                    saving = state.saving,
                    onEdit = viewModel::onOfferEdited,
                    onSubmit = viewModel::onOfferSubmitted,
                    onDismiss = viewModel::onSheetDismissed,
                    onKind = { picked -> viewModel.onOfferEdited { it.copy(kind = picked) } },
                    onProductQuery = viewModel::onProductQueryChanged,
                    onProductPicked = viewModel::onProductPicked,
                )
            }

            is BoardSheet.EditEntry -> {
                EditEntrySheet(
                    sheet = sheet,
                    saving = state.saving,
                    onEdit = viewModel::onEntryEdited,
                    onSubmit = viewModel::onEntrySubmitted,
                    onWithdraw = viewModel::onWithdrawRequested,
                    onDismiss = viewModel::onSheetDismissed,
                )
                if (sheet.confirmingWithdrawal) {
                    WithdrawConfirmation(
                        entry = sheet.entry,
                        busy = state.saving,
                        onConfirm = { viewModel.onWithdraw(sheet.entry) },
                        onDismiss = {
                            viewModel.onEntryEdited { it.copy(confirmingWithdrawal = false) }
                        },
                    )
                }
            }
        }
    }
}
