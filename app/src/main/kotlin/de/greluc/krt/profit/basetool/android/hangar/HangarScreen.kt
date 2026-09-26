/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.HomeLocation
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTable
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableCell
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableColumn
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import kotlinx.coroutines.delay
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the hangar list. */
const val HANGAR_LIST_TAG: String = "hangar-list"

/** Test handle for the hangar's search field. */
const val HANGAR_SEARCH_TAG: String = "hangar-search"

/** Test handle for the Meine Schiffe / Org-Einheit segment. */
const val HANGAR_SEGMENT_TAG: String = "hangar-segment"

/** Test handle for the add action. */
const val HANGAR_ADD_TAG: String = "hangar-add"

/**
 * The Hangar, with the member's own ships and the org unit's per-type aggregate.
 *
 * Only „Meine Schiffe" is writable; the aggregate counts ships that belong to other members.
 *
 * @param state what to draw.
 * @param onSegmentSelected the segment was switched.
 * @param onSearchChanged a keystroke in the filter field.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param onCreate the add action was taken.
 * @param onEdit a ship was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarScreen(
    state: HangarState,
    onSegmentSelected: (HangarSegment) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Ship) -> Unit,
    onTypeDrilldown: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.online) {
                OfflineBand()
            }
            KrtSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.hangar_segment_mine),
                        stringResource(R.string.hangar_segment_org),
                    ),
                selectedIndex = state.segment.ordinal,
                onSelect = { onSegmentSelected(HangarSegment.entries[it]) },
                stretch = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = KrtSpacing.s12, end = KrtSpacing.s12, top = KrtSpacing.s12)
                        .testTag(HANGAR_SEGMENT_TAG),
            )
            KrtTextField(
                value = state.searchText,
                onValueChange = onSearchChanged,
                placeholder = stringResource(R.string.hangar_search_placeholder),
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12).testTag(HANGAR_SEARCH_TAG),
            )

            when (state.phase) {
                is HangarPhase.Loading -> {
                    KrtLoadingIndicator(
                        text = stringResource(R.string.hangar_title),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is HangarPhase.Failed -> {
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
                            iconRes = DesignR.drawable.ic_krt_ship,
                            title = stringResource(R.string.hangar_error_title),
                            message = stringResource(R.string.hangar_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                        )
                    }
                }

                is HangarPhase.Ready -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        HangarBody(
                            state = state,
                            onLoadMore = onLoadMore,
                            onEdit = onEdit,
                            onTypeDrilldown = onTypeDrilldown,
                        )
                    }
                }
            }
        }
        if (state.segment == HangarSegment.MINE) {
            KrtFab(
                iconRes = DesignR.drawable.ic_krt_plus,
                label = stringResource(R.string.hangar_add),
                onClick = onCreate,
                enabled = state.online,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(KrtSpacing.s16)
                        .padding(bottom = LocalKrtBottomBarInset.current)
                        .testTag(HANGAR_ADD_TAG),
            )
        }
    }
}

/**
 * The row's one action — the ✎ that opens the editor.
 *
 * @param online whether writes are possible.
 * @param onEdit opens the editor for this ship.
 */
@Composable
private fun ShipCardActions(
    online: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
    ) {
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_edit,
            label = stringResource(R.string.hangar_edit),
            onClick = onEdit,
            enabled = online,
        )
    }
}

/**
 * The list of whichever half is showing, or its empty state.
 *
 * @param state what to draw.
 * @param onLoadMore the next page was asked for.
 * @param onEdit a ship was tapped.
 * @param onTypeDrilldown an aggregate row was tapped; shows that type's ships.
 */
@Composable
private fun HangarBody(
    state: HangarState,
    onLoadMore: () -> Unit,
    onEdit: (Ship) -> Unit,
    onTypeDrilldown: (String) -> Unit,
) {
    val empty =
        if (state.segment == HangarSegment.MINE) state.ships.isEmpty() else state.types.isEmpty()
    if (empty) {
        HangarEmpty(segment = state.segment, narrowed = state.isNarrowed)
        return
    }
    val wide = isWideWindow()
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(HANGAR_LIST_TAG),
        contentPadding = PaddingValues(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        if (state.segment == HangarSegment.MINE && wide) {
            item(key = "ships-table") {
                ShipTable(
                    ships = state.ships,
                    online = state.online,
                    onEdit = onEdit,
                )
            }
        } else if (state.segment == HangarSegment.MINE) {
            items(state.ships, key = { it.id }) { ship ->
                ShipCard(
                    ship = ship,
                    online = state.online,
                    onEdit = { onEdit(ship) },
                )
            }
        } else {
            item(key = "org-figures") { ShipTypeFigures(types = state.types) }
            item(key = "org-table") {
                ShipTypeTable(types = state.types, onPick = onTypeDrilldown)
            }
            item(key = "org-note") {
                Text(
                    text = stringResource(R.string.hangar_org_note),
                    modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text = state.countLabel(),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.hangar_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            }
        }
    }
}

/**
 * How many of how many the showing half has loaded.
 *
 * @return the label, pluralised for the half's own unit — ships or ship types.
 */
@Composable
private fun HangarState.countLabel(): String =
    if (segment == HangarSegment.MINE) {
        pluralStringResource(R.plurals.hangar_ship_count, total.toInt(), ships.size, total)
    } else {
        pluralStringResource(R.plurals.hangar_type_count, total.toInt(), types.size, total)
    }

/** Column index of the manufacturer lettermark in the ship table. */
private const val MANUFACTURER_COLUMN = 0

/** Column index of the ship type. */
private const val TYPE_COLUMN = 1

/** Column index of the member's own name for the ship. */
private const val NAME_COLUMN = 2

/** Column index of the insurance. */
private const val INSURANCE_COLUMN = 3

/** Column index of the location. */
private const val LOCATION_COLUMN = 4

/**
 * The tablet's dense ship table, with the web app's columns in the web app's order.
 *
 * The trailing column holds the row's action, kept apart from the data cells.
 *
 * @param ships the rows.
 * @param online whether writes are possible; the actions disable with the rest of the screen.
 * @param onEdit opens the editor for a ship, the row's only action.
 */
@Composable
private fun ShipTable(
    ships: List<Ship>,
    online: Boolean,
    onEdit: (Ship) -> Unit,
) {
    val columns =
        listOf(
            KrtTableColumn(stringResource(R.string.hangar_column_manufacturer), weight = 0.5f),
            KrtTableColumn(stringResource(R.string.hangar_column_type), weight = 1.4f),
            KrtTableColumn(stringResource(R.string.hangar_column_name), weight = 1.2f),
            KrtTableColumn(stringResource(R.string.hangar_column_insurance), weight = 0.9f),
            KrtTableColumn(stringResource(R.string.hangar_column_location), weight = 1.2f),
            KrtTableColumn(stringResource(R.string.hangar_column_fitted), weight = 0.6f),
            KrtTableColumn(stringResource(R.string.hangar_column_actions), weight = 0.8f),
        )
    val fittedYes = stringResource(R.string.hangar_fitted_yes)
    val fittedNo = stringResource(R.string.hangar_fitted_no)
    val unknown = stringResource(R.string.hangar_value_unknown)

    KrtTable(
        columns = columns,
        rowCount = ships.size,
        onRowClick = { onEdit(ships[it]) },
    ) { row, column ->
        val ship = ships[row]
        if (column == columns.lastIndex) {
            ShipCardActions(
                online = online,
                onEdit = { onEdit(ship) },
            )
        } else if (column == MANUFACTURER_COLUMN) {
            ManufacturerMark(ship.manufacturerAbbreviation, ship.manufacturerName)
        } else {
            KrtTableCell(
                text =
                    when (column) {
                        TYPE_COLUMN -> ship.typeName
                        NAME_COLUMN -> ship.name ?: unknown
                        INSURANCE_COLUMN -> ship.insuranceLabel()
                        LOCATION_COLUMN -> ship.locationName ?: unknown
                        else -> if (ship.fitted) fittedYes else fittedNo
                    },
                column = columns[column],
                emphasis = column == TYPE_COLUMN,
            )
        }
    }
}

/**
 * One ship as a card, headed by its type with the member's own name beside it in quotes.
 *
 * @param ship the ship.
 * @param online whether writes are possible.
 * @param onEdit opens the editor, which is also where the ship is deleted.
 */
@Composable
private fun ShipCard(
    ship: Ship,
    online: Boolean,
    onEdit: () -> Unit,
) {
    KrtCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit.takeIf { online },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManufacturerMark(ship.manufacturerAbbreviation, ship.manufacturerName)
            Column(modifier = Modifier.weight(1f)) {
                ShipCardBody(ship = ship)
            }
            ShipCardActions(online = online, onEdit = onEdit)
        }
    }
}

/**
 * The manufacturer as a lettermark square at the head of the row.
 *
 * Screen readers still hear the full manufacturer name.
 *
 * @param abbreviation the maker's own short form, preferred over anything derived from the name.
 * @param maker the manufacturer's name, the fallback when the catalogue carries no short form.
 */
@Composable
private fun ManufacturerMark(
    abbreviation: String?,
    maker: String?,
) {
    val spoken = maker?.takeIf { it.isNotBlank() }
    Box(
        modifier =
            Modifier
                .size(MARK_SIZE)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .semantics { spoken?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = abbreviation.markOrNull() ?: spoken.lettermark(),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.White,
        )
    }
}

/**
 * The catalogue's own short form as a mark, capped at four characters and uppercased.
 *
 * @return the mark, or `null` to fall back to [lettermark].
 */
private fun String?.markOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.take(MARK_MAX_ABBREVIATION)?.uppercase()

/**
 * The initials a manufacturer is abbreviated to.
 *
 * One letter per word for a multi-word maker ("RSI"), the first two letters of a single word ("DR"),
 * capped at three; an unknown maker gets an em dash.
 *
 * @return the mark's text.
 */
private fun String?.lettermark(): String {
    val words = this?.trim()?.split(Regex("""\s+"""))?.filter { it.isNotBlank() }.orEmpty()
    return when {
        words.isEmpty() -> "—"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> words.take(MARK_MAX_LETTERS).joinToString("") { it.first().uppercase() }
    }
}

/**
 * Everything the row says about the ship itself.
 *
 * @param ship the ship.
 */
@Composable
private fun ShipCardBody(ship: Ship) {
    Column {
        Text(
            text = ship.headlineText(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        ) {
            val insurance = ship.insuranceLabel()
            KrtChip(
                text = insurance,
                tone = if (ship.insuranceIsTerm()) KrtChipTone.Muted else KrtChipTone.Primary,
            )
            KrtChip(
                text =
                    stringResource(
                        if (ship.fitted) R.string.hangar_fitted else R.string.hangar_not_fitted,
                    ),
                tone = if (ship.fitted) KrtChipTone.Success else KrtChipTone.Muted,
            )
            ship.locationName?.takeIf { it.isNotBlank() }?.let { place ->
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
                        color = KrtPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The card's headline: the type bright, the member's own name a step back.
 *
 * @return the styled headline.
 */
@Composable
private fun Ship.headlineText(): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = KrtPalette.White)) { append(typeName) }
        name?.takeIf { it.isNotBlank() }?.let { own ->
            withStyle(SpanStyle(color = KrtPalette.TextMuted)) { append(" „$own\"") }
        }
    }

/**
 * The band over the aggregate: how many ships the org unit has and how many are fitted.
 *
 * There is no LTI figure, because the aggregate endpoint carries no insurance data.
 *
 * @param types the aggregate rows, which are also what the figures are summed from.
 */
@Composable
private fun ShipTypeFigures(types: List<ShipTypeSummary>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        KrtFigureTile(
            label = stringResource(R.string.hangar_figure_ships),
            value = types.sumOf { it.count }.toString(),
            tone = KrtFigureTone.Primary,
            modifier = Modifier.weight(1f),
        )
        KrtFigureTile(
            label = stringResource(R.string.hangar_figure_fitted),
            value = types.sumOf { it.fittedCount }.toString(),
            tone = KrtFigureTone.Success,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The aggregate table: ship type, count and fitted count.
 *
 * Tapping a row filters „Meine Schiffe" by that type.
 *
 * @param types the rows.
 * @param onPick the type whose ships to show.
 */
@Composable
private fun ShipTypeTable(
    types: List<ShipTypeSummary>,
    onPick: (String) -> Unit,
) {
    val columns =
        listOf(
            KrtTableColumn(stringResource(R.string.hangar_column_ship_type), weight = 2f),
            KrtTableColumn(stringResource(R.string.hangar_column_count), weight = 0.7f, numeric = true),
            KrtTableColumn(stringResource(R.string.hangar_figure_fitted), weight = 0.7f, numeric = true),
        )
    KrtTable(
        columns = columns,
        rowCount = types.size,
        onRowClick = { onPick(types[it].typeName) },
    ) { row, column ->
        val type = types[row]
        when (column) {
            0 -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ManufacturerMark(type.manufacturerAbbreviation, type.manufacturerName)
                    KrtTableCell(text = type.typeName, column = columns[0], emphasis = true)
                }
            }

            1 -> {
                KrtTableCell(
                    text = type.count.toString(),
                    column = columns[1],
                    emphasis = true,
                )
            }

            else -> {
                Text(
                    text = type.fittedCount.toString(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KrtSpacing.s8, vertical = KrtSpacing.s4),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtTheme.colors.successText,
                )
            }
        }
    }
}

/**
 * The empty state, which differs by half and by whether a filter is applied.
 *
 * @param segment which half is showing.
 * @param narrowed whether a filter is applied.
 */
@Composable
private fun HangarEmpty(
    segment: HangarSegment,
    narrowed: Boolean,
) {
    val title =
        when {
            narrowed -> R.string.hangar_empty_filtered_title
            segment == HangarSegment.MINE -> R.string.hangar_empty_mine_title
            else -> R.string.hangar_empty_org_title
        }
    val message =
        when {
            narrowed -> R.string.hangar_empty_filtered_message
            segment == HangarSegment.MINE -> R.string.hangar_empty_mine_message
            else -> R.string.hangar_empty_org_message
        }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_ship,
        title = stringResource(title),
        message = stringResource(message),
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
    )
}

/**
 * The Hangar, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun HangarRoute(
    viewModel: HangarViewModel,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.hangar_actions)
    val offlineReason = if (state.online) null else stringResource(R.string.hangar_menu_reason_offline)
    val fleetReason =
        offlineReason
            ?: stringResource(R.string.hangar_menu_reason_empty).takeIf { state.ships.isEmpty() }
    val bulkLabel = stringResource(R.string.hangar_bulk_home_location)
    val importLabel = stringResource(R.string.fleet_import_title)
    val clearLabel = stringResource(R.string.hangar_clear)
    ProvideScreenTopBar(
        actions = {
            KrtOverflowMenu(
                contentDescription = actionsLabel,
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                items =
                    listOf(
                        KrtMenuItem(
                            label = bulkLabel,
                            iconRes = DesignR.drawable.ic_krt_map_pin,
                            reason = fleetReason ?: stringResource(R.string.hangar_bulk_home_location_reason),
                            enabled = state.online && state.ships.isNotEmpty(),
                            onClick = viewModel::onBulkHomeLocationRequested,
                        ),
                        KrtMenuItem(
                            label = clearLabel,
                            iconRes = DesignR.drawable.ic_krt_trash,
                            danger = true,
                            reason = fleetReason,
                            enabled = state.online && state.ships.isNotEmpty(),
                            onClick = viewModel::onClearRequested,
                        ),
                        KrtMenuItem(
                            label = importLabel,
                            iconRes = DesignR.drawable.ic_krt_upload,
                            reason = offlineReason,
                            enabled = state.online,
                            onClick = onOpenImport,
                        ),
                    ),
            )
        },
    )
    HangarScreen(
        state = state,
        onSegmentSelected = viewModel::onSegmentSelected,
        onSearchChanged = viewModel::onSearchChanged,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onCreate = viewModel::onCreate,
        onEdit = viewModel::onEdit,
        onTypeDrilldown = viewModel::onTypeDrilldown,
        modifier = modifier,
    )

    (state.editor as? ShipEditor.Open)?.let { editor ->
        ConflictOn(
            error = editor.error,
            onReload = {
                viewModel.onEditorDismissed()
                viewModel.onRefresh()
            },
        )
        ShipEditorSheet(
            editor = editor,
            hulls = state.hulls,
            places = state.places,
            onName = viewModel::onShipNameChanged,
            onHullQuery = viewModel::onHullQueryChanged,
            onHull = viewModel::onHullChosen,
            onLti = viewModel::onInsuranceLtiChanged,
            onMonths = viewModel::onInsuranceMonthsChanged,
            onPlace = viewModel::onPlaceChosen,
            onFitted = viewModel::onFittedChanged,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDeleteRequested,
            onDismiss = viewModel::onEditorDismissed,
        )
    }
    state.pendingDelete?.let { ship ->
        ShipDeleteModal(
            ship = ship,
            deleting = state.deleting,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }
    if (state.clearRequested) {
        HangarClearModal(
            count = state.ships.size,
            onConfirm = viewModel::onClearConfirmed,
            onDismiss = viewModel::onClearDismissed,
        )
    }
    state.homeLocationSet?.let { affected ->
        LaunchedEffect(affected) {
            delay(CLEARED_TOAST_MS)
            viewModel.onHomeLocationSetAcknowledged()
        }
        Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.BottomCenter) {
            KrtToast(
                title = stringResource(R.string.hangar_bulk_home_location),
                message = pluralStringResource(R.plurals.hangar_bulk_home_location_done, affected, affected),
                modifier =
                    Modifier
                        .padding(horizontal = KrtSpacing.s16)
                        .padding(bottom = KrtSpacing.s16 + LocalKrtBottomBarInset.current),
            )
        }
    }
    state.cleared?.let { emptied ->
        LaunchedEffect(emptied) {
            delay(CLEARED_TOAST_MS)
            viewModel.onClearedAcknowledged()
        }
        Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.BottomCenter) {
            KrtToast(
                title = stringResource(R.string.hangar_clear_title),
                message = pluralStringResource(R.plurals.hangar_clear_done, emptied, emptied),
                modifier =
                    Modifier
                        .padding(horizontal = KrtSpacing.s16)
                        .padding(bottom = KrtSpacing.s16 + LocalKrtBottomBarInset.current),
            )
        }
    }
    state.bulkHomeLocation?.let { bulk ->
        BulkHomeLocationSheet(
            bulk = bulk,
            places = state.places,
            count = state.ships.size,
            onChosen = viewModel::onBulkHomeLocationChosen,
            onApply = viewModel::onBulkHomeLocationApplied,
            onDismiss = viewModel::onBulkHomeLocationDismissed,
        )
    }
}

/**
 * „Alle N Schiffe löschen?": the danger modal that empties the hangar, naming the count.
 *
 * @param count how many ships would go.
 * @param onConfirm empties the hangar.
 * @param onDismiss leaves it alone.
 */
@Composable
private fun HangarClearModal(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.hangar_clear_title),
        confirmText = pluralStringResource(R.plurals.hangar_clear_confirm, count, count),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        cancelText = stringResource(R.string.personal_inventory_cancel),
        modifier = Modifier.testTag(HANGAR_CLEAR_TAG),
    ) {
        Text(
            text = pluralStringResource(R.plurals.hangar_clear_body, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
}

/**
 * The bulk home-location picker, which sets one place for the whole fleet.
 *
 * @param bulk what the sheet holds.
 * @param places the org's home locations.
 * @param count how many ships it would touch, taken from the loaded list.
 * @param onChosen a place was picked.
 * @param onApply the CTA was pressed.
 * @param onDismiss the sheet was closed.
 */
@Composable
private fun BulkHomeLocationSheet(
    bulk: BulkHomeLocation,
    places: List<HomeLocation>,
    count: Int,
    onChosen: (HomeLocation) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.hangar_bulk_home_location_title),
        modifier = Modifier.testTag(HANGAR_BULK_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            KrtSelectField(
                value = bulk.place?.name ?: stringResource(R.string.hangar_location_none),
                options = places.map { KrtOption(it.id, it.name) },
                onSelect = { option ->
                    places.firstOrNull { it.id == option.value }?.let(onChosen)
                    open = false
                },
                expanded = open,
                onExpandedChange = { open = it },
                label = stringResource(R.string.hangar_field_location),
                selectedValue = bulk.place?.id,
                enabled = !bulk.saving,
            )
            Text(
                text = pluralStringResource(R.plurals.hangar_bulk_home_location_scope, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            bulk.error?.let {
                KrtFieldError(text = stringResource(R.string.hangar_bulk_home_location_refused))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = onDismiss,
                    enabled = !bulk.saving,
                )
                KrtCtaButton(
                    text = pluralStringResource(R.plurals.hangar_bulk_home_location_apply, count, count),
                    onClick = onApply,
                    iconRes = DesignR.drawable.ic_krt_map_pin,
                    enabled = bulk.place != null && !bulk.saving,
                    modifier = Modifier.testTag(HANGAR_BULK_APPLY_TAG),
                )
            }
        }
    }
}

/** Edge of the manufacturer lettermark square (design ch. 08). */
private val MARK_SIZE = 44.dp

/** Most initials a multi-word manufacturer is abbreviated to. */
private const val MARK_MAX_LETTERS = 3

/** How much of the catalogue's own short form the mark square holds. */
private const val MARK_MAX_ABBREVIATION = 4

/**
 * Whether the policy is a plain term rather than a named one.
 *
 * A month count and „keine" are both neutral facts; anything else the catalogue passes through —
 * „LTI" above all — is a standing policy and carries the accent.
 *
 * @return `true` for a term or an absent policy.
 */
private fun Ship.insuranceIsTerm(): Boolean {
    val raw = insurance?.trim().orEmpty()
    return raw.isBlank() || raw.toIntOrNull() != null
}

/**
 * The insurance chip's text.
 *
 * "LTI" stays as is, a numeric value gets its month unit, anything else passes through unchanged,
 * and a ship without a policy says so.
 *
 * @return the chip caption.
 */
@Composable
private fun Ship.insuranceLabel(): String {
    val raw = insurance?.trim().orEmpty()
    return when {
        raw.isBlank() -> stringResource(R.string.hangar_no_insurance)
        raw.toIntOrNull() != null -> stringResource(R.string.hangar_insurance_months_value, raw)
        else -> raw
    }
}

/** Test handle for the empty-the-hangar modal. */
const val HANGAR_CLEAR_TAG = "hangar-clear"

/** Test handle for the bulk home-location sheet. */
const val HANGAR_BULK_TAG = "hangar-bulk-home-location"

/** Test handle for its apply button. */
const val HANGAR_BULK_APPLY_TAG = "hangar-bulk-apply"

/** How long the "hangar emptied" confirmation stands before it goes by itself. */
private const val CLEARED_TOAST_MS = 4_000L
