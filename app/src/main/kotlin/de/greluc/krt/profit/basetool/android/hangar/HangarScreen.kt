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
 * The Hangar (design spec ch. 08 §1), read-only.
 *
 * **The three-number band of the design's org tab is absent.** "Schiffe 42 · Fitted 31 · LTI 24" is
 * an aggregate over the whole org unit, and the API offers no such total: the overview is paged, so
 * adding up what is loaded would state a number the page cannot know. The per-type rows carry their
 * own counts, which are the server's.
 *
 * **Only the member's own half is writable.** The org aggregate is a count per hull, not a list of
 * ships, and the ships behind it belong to other members — so the create action and the row taps
 * exist on `Meine Schiffe` and nowhere else. Importing stays in phase 4.
 *
 * @param state what to draw.
 * @param onSegmentSelected the segment was switched.
 * @param onSearchChanged a keystroke in the filter field.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param onCreate the add action was taken.
 * @param onEdit a ship was tapped.
 * @param onDelete a ship's delete action was taken.
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
    onDelete: (Ship) -> Unit,
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
                // The typed value, not the debounced one (REQ-APP-MIS-004).
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
                            onDelete = onDelete,
                            onTypeDrilldown = onTypeDrilldown,
                        )
                    }
                }
            }
        }
        // Design ch. 08 asks for a FAB here, and only on "Meine Schiffe" — the org overview is a
        // read of everybody's fleet and has nothing to create. It replaces the inline CTA that sat
        // in the header row: a list screen floats its primary action (ch. 00), and the header
        // button also pushed the search field down on every phone.
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
 * The card's delete affordance.
 *
 * @param online whether writes are possible.
 * @param onDelete asks to delete.
 */
@Composable
private fun ShipCardActions(
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Design ch. 08 gives the row 44 dp icon buttons, not a labelled button. A ghost button reading
    // "LÖSCHEN" is the widest, loudest thing on a card whose subject is a ship, and it made the
    // destructive action the most prominent one on every row.
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
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_trash,
            label = stringResource(R.string.hangar_delete),
            onClick = onDelete,
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
 * @param onDelete a ship's delete action was taken.
 * @param onTypeDrilldown an aggregate row was tapped; shows that type's ships.
 */
@Composable
private fun HangarBody(
    state: HangarState,
    onLoadMore: () -> Unit,
    onEdit: (Ship) -> Unit,
    onDelete: (Ship) -> Unit,
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
            // Design ch. 08: the tablet gets the web app's full table, the phone the cards.
            // One item rather than one per ship — a table is a single grid whose columns have to
            // line up, and a LazyColumn of table rows would size each one on its own.
            item(key = "ships-table") {
                ShipTable(
                    ships = state.ships,
                    online = state.online,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        } else if (state.segment == HangarSegment.MINE) {
            items(state.ships, key = { it.id }) { ship ->
                ShipCard(
                    ship = ship,
                    online = state.online,
                    onEdit = { onEdit(ship) },
                    onDelete = { onDelete(ship) },
                )
            }
        } else {
            // A three-column aggregate stays a table on the phone as well. Design ch. 08,
            // artboard 11 is explicit that the collapse to cards is about WIDTH, not about tables:
            // „Schmale Aggregate … bleiben auch auf dem Telefon Tabelle."
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

/**
 * The tablet's dense ship table — the web app's columns, per design ch. 08.
 *
 * Carries the same five facts the card does, in the order the web table uses, so a member who
 * knows one recognises the other. The trailing column holds the row's two actions rather than a
 * value; giving them a column of their own keeps them off the data cells, where a mis-tap would
 * be a deletion.
 *
 * @param ships the rows.
 * @param online whether writes are possible; the actions disable with the rest of the screen.
 * @param onEdit opens the editor for a ship.
 * @param onDelete asks to delete one.
 * @param onTypeDrilldown an aggregate row was tapped; shows that type's ships.
 */
@Composable
private fun ShipTable(
    ships: List<Ship>,
    online: Boolean,
    onEdit: (Ship) -> Unit,
    onDelete: (Ship) -> Unit,
) {
    val columns =
        listOf(
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

    // The row opens the editor, exactly as the card does — a member who learned the phone layout
    // does not have to learn a second gesture on the tablet.
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
                onDelete = { onDelete(ship) },
            )
        } else {
            KrtTableCell(
                text =
                    when (column) {
                        0 -> ship.typeName
                        1 -> ship.name ?: unknown
                        2 -> ship.insurance ?: unknown
                        3 -> ship.locationName ?: unknown
                        else -> if (ship.fitted) fittedYes else fittedNo
                    },
                column = columns[column],
                emphasis = column == 0,
            )
        }
    }
}

/**
 * One ship, as the design's card.
 *
 * The type is the headline because it is what identifies a ship at a glance; the member's own name
 * for it, when they gave one, sits beside it in quotes as the web app writes it.
 *
 * The card opens the editor; deleting has its own action, because a mis-tap that edits is
 * recoverable and a mis-tap that deletes is not.
 *
 * @param ship the ship.
 * @param online whether writes are possible.
 * @param onEdit opens the editor.
 * @param onDelete asks to delete.
 */
@Composable
private fun ShipCard(
    ship: Ship,
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // A card, not a padded Column: every design chapter draws its list items as bordered
    // tiles, and the app was drawing lines of text. See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit.takeIf { online },
    ) {
        // The actions sit ON the row, not under it. Artboard 08-1 draws the card one row high with
        // the pencil at its trailing edge; stacked below the chips they made every ship two rows
        // tall and put the destructive one at the bottom of the card rather than beside its ship.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManufacturerMark(ship.manufacturerAbbreviation, ship.manufacturerName)
            Column(modifier = Modifier.weight(1f)) {
                ShipCardBody(ship = ship)
            }
            ShipCardActions(online = online, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/**
 * The manufacturer as a lettermark square at the head of the row.
 *
 * Design ch. 08 leads each row with the maker rather than burying it in a subtitle: a fleet is read
 * by running down one column, and a subtitle forces the eye to read every second line to do it. The
 * square is an abbreviation of a fact already on screen, so screen readers still hear the full
 * name — a visual shorthand must not remove information from anyone.
 *
 * Clean manufacturer vectors do not exist yet (the upstream SVGs embed rasters), so the handoff's
 * lettermark placeholder **is** the design here, not a stand-in for it.
 *
 * @param abbreviation the maker's own short form, preferred over anything derived from the legal
 *   name: initials of "Musashi Industrial and Starflight Concern" counted the "and" and produced
 *   „MIA", which is not what anyone calls MISC.
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
            // White on a hairline square, as artboard 08-1 draws it — not orange. The maker is
            // what the ship IS, in the same weight as its type beside it; orange is the app's
            // action colour and made a label look like something to press.
            text = abbreviation.markOrNull() ?: spoken.lettermark(),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.White,
        )
    }
}

/**
 * The catalogue's own short form as a mark, or `null` when it carries none.
 *
 * Capped at four characters because the square is drawn for four and the catalogue's short forms
 * are short *names* rather than codes — „Crusader" becomes „CRUS". A visible truncation of the
 * maker's own word beats a correct-looking abbreviation the app invented.
 *
 * @return the mark, or `null` to fall back to [lettermark].
 */
private fun String?.markOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.take(MARK_MAX_ABBREVIATION)?.uppercase()

/**
 * The initials a manufacturer is abbreviated to.
 *
 * One letter per word for a multi-word maker ("Roberts Space Industries" -> "RSI"), the first two
 * for a single word ("Drake" -> "DR"), capped at three so the square never has to shrink its type.
 * An unknown maker gets an em dash rather than an empty square, which would read as a rendering
 * fault.
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
        // Two tones in one line, as artboard 08-1 sets it: the type is the catalogue's word and
        // stays bright, the member's own name for the ship is theirs and sits back a step. Both in
        // white, the sentence read as one long product name.
        Text(
            text = ship.headlineText(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // FlowRow, not Row: with the two icon buttons beside it the line has about 190 dp, and a
        // Row squeezed the location out of existence — „Everus Harbor" rendered as „E…" and then as
        // nothing. Wrapping costs a second line only on the cards that need one.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        ) {
            // Insurance first, then fitted — the artboard's order, and the useful one: the policy
            // is the fact that expires.
            val insurance = ship.insuranceLabel()
            KrtChip(
                text = insurance,
                // A month count is neutral; a named policy („LTI") is the one worth seeing from
                // across the card, which is what the artboard's orange is for. Info blue appears on
                // no chip in this chapter.
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
                    // Design ch. 08: "Ort with map-pin". A bare place name beside two chips reads
                    // as a third chip's caption; the glyph says what kind of fact it is.
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
 * The band over the aggregate: how many ships the org unit has, and how many are ready.
 *
 * Design ch. 08, artboard 1 draws three figures — Schiffe, Fitted and LTI. Two of them ship: the
 * aggregate endpoint carries `count` and `fittedCount` and **no insurance at all**
 * (`SquadronShipDetailDto` has owner, location and fitted), so an LTI figure here would have to be
 * invented. It is named as a gap in the spec rather than filled with a dash, because a KPI tile
 * showing „—" claims the number exists and is merely missing today.
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
 * The aggregate itself — ship type, how many, how many fitted.
 *
 * Three columns, so it stays a table on the phone (design ch. 08, artboard 11). Tapping a row is
 * the artboard's own affordance: „Zeile antippen → gefilterte Schiffsliste" — it puts that type in
 * the filter and moves to „Meine Schiffe", which is the list the member was reaching for.
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
            // Both figures are numeric, so they sit right-aligned under their own headers — the
            // artboard's columns line up, and two counts that drift left of their titles read as
            // belonging to the name beside them instead.
            KrtTableColumn(stringResource(R.string.hangar_column_count), weight = 0.7f, numeric = true),
            // „FITTED", not the ship table's „Ausgebaut": the aggregate counts a state, the ship
            // table names one. Artboard 1 writes them differently and so does this.
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

            // The fitted figure is the one the eye is looking for, and the artboard states it in
            // the success tint rather than as another neutral number.
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
 * "You own no ship" and "your filter matches none" are different facts, and so are "you own none"
 * and "the org unit has none".
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
    // Design ch. 08 gives the Hangar a `⋮` with exactly these three: the bulk home location, the
    // Fleetview import, and emptying the hangar. All three act on the fleet rather than on a row,
    // which is why none of them belongs beside a ship.
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.hangar_actions)
    // A dimmed row without a reason reads as a broken menu (design ch. 08, artboard 5).
    // Two things can dim these entries and they are not the same answer.
    val offlineReason = if (state.online) null else stringResource(R.string.hangar_menu_reason_offline)
    val fleetReason =
        offlineReason
            ?: stringResource(R.string.hangar_menu_reason_empty).takeIf { state.ships.isEmpty() }
    val bulkLabel = stringResource(R.string.hangar_bulk_home_location)
    val importLabel = stringResource(R.string.fleet_import_title)
    val clearLabel = stringResource(R.string.hangar_clear)
    // Only the actions: the Hangar is a top-level destination, so its bar keeps the section title,
    // the org badge and the bell rather than turning into a subject bar.
    ProvideScreenTopBar(
        actions = {
            KrtOverflowMenu(
                contentDescription = actionsLabel,
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                items =
                    listOf(
                        // The order is the artboards' own: Home-Location, Hangar leeren, Import.
                        // Destructive in the middle rather than last looks wrong by habit and is
                        // what chapters 08.4 and 08.5 both draw, so it is followed rather than
                        // tidied.
                        KrtMenuItem(
                            label = bulkLabel,
                            iconRes = DesignR.drawable.ic_krt_map_pin,
                            // The one entry that carries its purpose even when it can be used: a
                            // member has to know it means the WHOLE fleet before tapping it.
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
        onDelete = viewModel::onDeleteRequested,
        onTypeDrilldown = viewModel::onTypeDrilldown,
        modifier = modifier,
    )

    (state.editor as? ShipEditor.Open)?.let { editor ->
        // Design ch. 14's conflict dialog: a refused save must not be a line under a
        // scrolled form. „Neu laden" closes the form and makes the screen re-read.
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
        // Success is a toast, not a step: nothing is left to read afterwards, and the empty state
        // behind it is the rest of the answer (design ch. 08, artboard 6).
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
 * „Alle N Schiffe löschen?" - the danger modal behind the overflow's last entry.
 *
 * The count is in the question because it is the only thing that distinguishes a member emptying a
 * hangar of three from one emptying a hangar of ninety. Design ch. 08 spells that wording out.
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
    // Three guards, none of them a typing hurdle (design ch. 08, artboard 6, which resolves 08.1's
    // „type-safe" against 08.3): the menu entry is already red, the modal names the count, the
    // consequence AND the way back, and the confirm repeats the count. Chapter 02 §7 reserves the
    // typing hurdle for irreversible admin actions on organisation-wide data — a personal hangar is
    // the member's own and comes back from another import, and spending the hurdle here would blunt
    // it where it is meant to bite.
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
 * The bulk home-location picker.
 *
 * One place for the whole fleet, which is what the endpoint does: a member who moves base moves
 * every hull with it, and setting thirty ships one at a time is the workflow the chapter puts in
 * the overflow to avoid.
 *
 * @param bulk what the sheet holds.
 * @param places the org's home locations.
 * @param count how many ships it would touch — the length of the loaded list, which is where the
 *   figure comes from; there is no API field for it (design ch. 08, artboard 10).
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
            // The scope is stated here rather than behind a confirmation dialog. Nothing is lost —
            // the write sets a location and can be repeated at will — so a second confirmation on
            // top of a sheet would be a ceremony without a risk (design ch. 08, artboard 10).
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
                    // The place marker, not the disk: the artboard's CTA carries the glyph of the
                    // thing being set rather than the generic act of saving (design ch. 08, 7–10).
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
 * The API sends a bare string: "LTI" for a lifetime policy, otherwise a month count. A chip reading
 * "6" says nothing — six of what — so a numeric value gets its unit. Anything else is passed through
 * unchanged rather than guessed at, and a ship with no policy says so.
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
