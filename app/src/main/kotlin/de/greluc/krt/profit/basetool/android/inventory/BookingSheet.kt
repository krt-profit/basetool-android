/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.BookOutKind
import de.greluc.krt.profit.basetool.android.core.data.GameItemOption
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.PickerOverflowNote
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.writeFailureText
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the booking sheet. */
const val BOOKING_SHEET_TAG: String = "booking-sheet"

/**
 * Test tag of the cSCU/µSCU hint beside the amount, whose text lives in a tooltip that is not in the
 * tree until long-pressed.
 */
const val BOOKING_SCU_HINT_TAG: String = "booking-scu-hint"

/** The Material/Item switch of a book-in. */
const val BOOKING_KIND_TAG: String = "booking-kind"

/** What the server calls a quantity counted in whole pieces. */
private const val PIECE_UNIT = "PIECE"

/** What the server calls a quantity measured in standard cargo units. */
private const val SCU_WIRE_UNIT = "SCU"

/** Test handle for the booking's save action. */
const val BOOKING_SAVE_TAG: String = "booking-save"

/** Test handle for the transfer's stock-merge opt-in. */
const val BOOKING_MERGE_TAG: String = "booking-merge"

/**
 * The Lager's booking form with the modes Ein, Aus and Notiz on one segment.
 *
 * The amount survives a mode change. Only usable modes are offered: `Ein` from the "Einbuchen"
 * action, `Aus` and `Notiz` from an entry.
 *
 * @param state what the form holds.
 * @param callbacks what it reports.
 */
@Composable
fun BookingSheet(
    state: BookingState,
    callbacks: BookingCallbacks,
) {
    KrtBottomSheet(
        onDismiss = callbacks.onDismiss,
        modifier = Modifier.testTag(BOOKING_SHEET_TAG),
        title = stringResource(state.actionRes()),
        centred = isWideWindow(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            if (!state.online) {
                OfflineBand()
            }
            state.entry?.let { entry ->
                Text(
                    text = entry.headline(),
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                )
                val modes = entry.modes()
                KrtSegmentedControl(
                    options = modes.map { stringResource(it.titleRes()) },
                    selectedIndex = modes.indexOf(state.mode).coerceAtLeast(0),
                    onSelect = { callbacks.onMode(modes[it]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                    stretch = true,
                )
            }

            if (state.mode == BookingMode.IN) {
                BookInFields(state = state, callbacks = callbacks)
            }

            if (state.mode == BookingMode.NOTE) {
                KrtTextField(
                    value = state.note,
                    onValueChange = callbacks.onNote,
                    label = stringResource(R.string.inventory_entry_note),
                    enabled = !state.saving,
                )
            } else if (state.mode != BookingMode.IN) {
                AmountField(state = state, onAmount = callbacks.onAmount)
            }

            if (state.mode == BookingMode.OUT) {
                OutKindField(state = state, callbacks = callbacks)
                HerkunftSection(
                    state = state,
                    onJobOrderShare = callbacks.onJobOrderShare,
                    onMissionShare = callbacks.onMissionShare,
                )
            }

            state.error?.let { error ->
                KrtFieldError(
                    text =
                        error.writeFailureText(
                            if (error is ApiError.OptimisticLock) {
                                R.string.conflict_inline
                            } else {
                                R.string.write_failed
                            },
                        ),
                )
            }
            ConflictOn(error = state.error, onReload = callbacks.onConflictReload)
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = callbacks.onDismiss,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    text = stringResource(state.actionRes()),
                    onClick = callbacks.onSave,
                    iconRes = state.actionIconRes(),
                    modifier =
                        Modifier
                            .testTag(BOOKING_SAVE_TAG)
                            .alpha(if (state.online) 1f else DISABLED_WRITE_ALPHA),
                    enabled = state.submittable && !state.saving && state.online,
                )
            }
        }
    }
}

/**
 * Everything the booking sheet reports back.
 *
 * @property onMode the segment changed.
 * @property onKind a book-in switched between the material and the item catalogue.
 * @property onSplitAmount an earmark's amount was typed.
 * @property onSplitStep an earmark's stepper was used.
 * @property onSplitPicking an earmark picker was opened or closed.
 * @property onSplitAdd a target was earmarked.
 * @property onSplitRemove an earmark is to go.
 * @property onGameItemQuery the item search was typed into.
 * @property onGameItem an item was picked.
 * @property onAmount the amount changed.
 * @property onQuality the quality changed.
 * @property onMaterialQuery the material search changed.
 * @property onMaterial a material was picked.
 * @property onPlaceQuery the place search changed.
 * @property onPlace a place was picked.
 * @property onOutKind what happens on the way out changed.
 * @property onMemberQuery the member search changed.
 * @property onMember a recipient was picked.
 * @property onTerminal a terminal was picked.
 * @property onJobOrderShare how much of the deduction comes from an Auftrag earmark.
 * @property onMissionShare how much comes from an Einsatz earmark.
 * @property onOrgUnit an org-unit pool was picked for a transfer.
 * @property onMergeStock the stock-merge opt-in changed.
 * @property onSellAmount what the sale fetched changed.
 * @property onNote the entry's note changed.
 * @property onSave the save action was taken.
 * @property onDismiss the sheet was closed.
 */
data class BookingCallbacks(
    val onMode: (BookingMode) -> Unit,
    val onKind: (BookingCatalogKind) -> Unit,
    val onSplitAmount: (AllocationKind, String, String) -> Unit,
    val onSplitStep: (AllocationKind, String, Int) -> Unit,
    val onSplitPicking: (AllocationKind?) -> Unit,
    val onSplitAdd: (AllocationKind, AllocationTarget) -> Unit,
    val onSplitRemove: (AllocationKind, String) -> Unit,
    val onGameItemQuery: (String) -> Unit,
    val onGameItem: (GameItemOption) -> Unit,
    val onAmount: (String) -> Unit,
    val onQuality: (String) -> Unit,
    val onMaterialQuery: (String) -> Unit,
    val onMaterial: (MaterialOption) -> Unit,
    val onPlaceQuery: (String) -> Unit,
    val onPlace: (LocationOption) -> Unit,
    val onOutKind: (BookOutKind) -> Unit,
    val onMemberQuery: (String) -> Unit,
    val onMember: (MemberOption) -> Unit,
    val onTerminal: (TerminalOption) -> Unit,
    val onJobOrderShare: (String, String) -> Unit,
    val onMissionShare: (String, String) -> Unit,
    val onOrgUnit: (OrgUnitOption) -> Unit,
    val onMergeStock: (Boolean) -> Unit,
    val onSellAmount: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
    val onConflictReload: () -> Unit,
)

/**
 * The amount, with the unit it is counted in and the sub-unit note the web app carries.
 *
 * @param state the form.
 * @param onAmount the amount changed.
 */
@Composable
private fun AmountField(
    state: BookingState,
    onAmount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtFieldLabel(
                text =
                    stringResource(
                        R.string.booking_field_amount,
                        state.unitLabel() ?: stringResource(R.string.booking_unit_unknown),
                    ),
                enabled = !state.saving,
            )
            if (state.materialIsScu) {
                KrtHint(
                    explanation = stringResource(R.string.booking_amount_hint),
                    modifier = Modifier.testTag(BOOKING_SCU_HINT_TAG),
                )
            }
        }
        KrtStepperField(
            value = state.amount,
            onValueChange = onAmount,
            onDecrement = { onAmount(state.amount.step(-1)) },
            onIncrement = { onAmount(state.amount.step(1)) },
            enabled = !state.saving,
        )
        state.entry?.amount?.let { available ->
            Muted(stringResource(R.string.booking_available, formatAmount(available)))
        }
    }
}

/**
 * Everything a book-in asks for: which catalogue, what from it, how much, and where.
 *
 * @param state the form.
 * @param callbacks what it reports.
 */
@Composable
private fun BookInFields(
    state: BookingState,
    callbacks: BookingCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.booking_kind_material),
                    stringResource(R.string.booking_kind_item),
                ),
            selectedIndex = if (state.kind == BookingCatalogKind.ITEM) 1 else 0,
            onSelect = {
                callbacks.onKind(
                    if (it == 1) BookingCatalogKind.ITEM else BookingCatalogKind.MATERIAL,
                )
            },
            modifier = Modifier.fillMaxWidth().testTag(BOOKING_KIND_TAG),
            enabled = !state.saving,
            stretch = true,
        )
        if (state.kind == BookingCatalogKind.MATERIAL) {
            Picker(
                label = stringResource(R.string.booking_field_material),
                query = state.materialQuery,
                chosen = state.material?.name,
                options = state.materials.map { it.id to it.label() },
                enabled = !state.saving,
                onQuery = callbacks.onMaterialQuery,
                onChosen = { id ->
                    state.materials.firstOrNull { it.id == id }?.let(callbacks.onMaterial)
                },
            )
            PickerOverflowNote(more = state.moreMaterials)
        } else {
            Picker(
                label = stringResource(R.string.booking_field_item),
                query = state.gameItemQuery,
                chosen = state.gameItem?.name,
                options = state.gameItems.map { it.id to it.name },
                enabled = !state.saving,
                onQuery = callbacks.onGameItemQuery,
                onChosen = { id ->
                    state.gameItems.firstOrNull { it.id == id }?.let(callbacks.onGameItem)
                },
            )
            PickerOverflowNote(more = state.moreGameItems)
        }

        if (state.kind == BookingCatalogKind.ITEM) {
            AmountField(state = state, onAmount = callbacks.onAmount)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.Bottom,
            ) {
                AmountField(
                    state = state,
                    onAmount = callbacks.onAmount,
                    modifier = Modifier.weight(1f),
                )
                KrtTextField(
                    value = state.quality,
                    onValueChange = callbacks.onQuality,
                    label = stringResource(R.string.booking_field_quality),
                    enabled = !state.saving,
                    modifier = Modifier.width(QUALITY_FIELD_WIDTH),
                )
            }
        }
        PlaceField(state = state, callbacks = callbacks)
        BookInSplits(state = state, callbacks = callbacks)
    }
}

/**
 * Where the amount being booked in is promised to go, entered with the booking.
 *
 * The earmarks are sent in the same request, so the server checks them in the transaction that
 * creates the row. The Einsatz split is absent in item mode (REQ-INV-031).
 *
 * @param state the form.
 * @param callbacks what it reports.
 */
@Composable
private fun BookInSplits(
    state: BookingState,
    callbacks: BookingCallbacks,
) {
    val actions =
        SplitActions(
            onAmount = callbacks.onSplitAmount,
            onStep = callbacks.onSplitStep,
            onPick = callbacks.onSplitPicking,
            onAdd = callbacks.onSplitAdd,
            onRemove = callbacks.onSplitRemove,
        )
    val dimensions =
        if (state.kind == BookingCatalogKind.ITEM) {
            listOf(AllocationKind.JOB_ORDER)
        } else {
            listOf(AllocationKind.JOB_ORDER, AllocationKind.MISSION)
        }
    dimensions.forEach { kind ->
        Split(
            kind = kind,
            pane =
                SplitPane(
                    rows = state.split(kind),
                    offerable = state.offerable(kind),
                    rest = state.rest(kind),
                    picking = state.picking == kind,
                    enabled = !state.saving,
                    removable = true,
                ),
            actions = actions,
        )
    }
}

/**
 * The place field, used by booking in and by a transfer.
 *
 * @param state the form.
 * @param callbacks what it reports.
 */
@Composable
private fun PlaceField(
    state: BookingState,
    callbacks: BookingCallbacks,
) {
    Picker(
        label = stringResource(R.string.booking_field_place),
        query = state.placeQuery,
        chosen = state.place?.name,
        options = state.places.map { it.id to it.name },
        enabled = !state.saving,
        onQuery = callbacks.onPlaceQuery,
        onChosen = { id -> state.places.firstOrNull { it.id == id }?.let(callbacks.onPlace) },
    )
    PickerOverflowNote(more = state.morePlaces)
}

/**
 * Which org-unit pool a transfer's moved row lands in, as a plain list of the recipient's
 * memberships.
 *
 * Not shown when there is nothing to choose: with no membership the row stays unpooled and with
 * exactly one the server resolves it.
 *
 * @param state the form.
 * @param onOrgUnit a pool was picked.
 */
@Composable
private fun OrgUnitField(
    state: BookingState,
    onOrgUnit: (OrgUnitOption) -> Unit,
) {
    if (state.orgUnits.isEmpty()) {
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        KrtSelectField(
            value =
                state.orgUnit?.let { unit ->
                    if (unit.id == state.entry?.owningOrgUnitId) {
                        stringResource(R.string.booking_org_unit_preset, unit.label())
                    } else {
                        unit.label()
                    }
                }.orEmpty(),
            options = state.orgUnits.map { KrtOption(value = it.id, label = it.label()) },
            onSelect = { option ->
                state.orgUnits.firstOrNull { it.id == option.value }?.let(onOrgUnit)
                open = false
            },
            expanded = open,
            onExpandedChange = { open = it },
            label = stringResource(R.string.booking_field_org_unit),
            selectedValue = state.orgUnit?.id,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        Muted(stringResource(R.string.booking_org_unit_note))
    }
}

/**
 * How an org unit reads in the pool picker.
 *
 * @return the name with its shorthand where the server sent one.
 */
private fun OrgUnitOption.label(): String = shorthand?.let { "$name · $it" } ?: name

/**
 * What a target picker shows when the member has not changed it.
 *
 * @param current the row's own value.
 * @return the value marked as unchanged.
 */
@Composable
private fun unchanged(current: String): String =
    stringResource(R.string.booking_target_unchanged, current)

/**
 * The refusal a transfer that moves nothing earns.
 *
 * Drawn as a bordered band rather than a line of red text (artboard 16): it is a data rule the
 * member can fix in the two fields above it, and the server's own backstop is quoted underneath so
 * the message and the 400 are recognisably the same thing.
 */
@Composable
private fun TransferRefusal() {
    Column(
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = KrtPalette.DangerText)
                .padding(KrtSpacing.s8),
    ) {
        Text(
            text = stringResource(R.string.booking_transfer_unchanged),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.DangerText,
        )
        Muted(stringResource(R.string.booking_transfer_unchanged_detail))
    }
}

/**
 * Whether the server may fold the moved amount into an identical entry at the target.
 *
 * The whole row is the tap target, since `KrtToggle` carries no label or gesture of its own.
 *
 * @param state the form.
 * @param onMergeStock the opt-in changed.
 */
@Composable
private fun MergeStockField(
    state: BookingState,
    onMergeStock: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(BOOKING_MERGE_TAG)
                    .clickable(enabled = !state.saving) { onMergeStock(!state.mergeStock) }
                    .padding(vertical = KrtSpacing.s4),
        ) {
            KrtToggle(checked = state.mergeStock, enabled = !state.saving)
            Text(
                text = stringResource(R.string.booking_merge_stock),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
        }
        Muted(stringResource(R.string.booking_merge_stock_note))
    }
}

/**
 * What happens to the material on the way out, and the field the choice requires.
 *
 * @param state the form.
 * @param callbacks what it reports.
 */
@Composable
private fun OutKindField(
    state: BookingState,
    callbacks: BookingCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        KrtFieldLabel(text = stringResource(R.string.booking_field_out_kind), enabled = !state.saving)
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.booking_out_discard),
                    stringResource(R.string.booking_out_transfer),
                    stringResource(R.string.booking_out_sell),
                ),
            selectedIndex = state.outKind.ordinal,
            onSelect = { callbacks.onOutKind(BookOutKind.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
            stretch = true,
        )
        when (state.outKind) {
            BookOutKind.DISCARD -> {
                Muted(stringResource(R.string.booking_out_discard_note))
            }

            BookOutKind.TRANSFER -> {
                Picker(
                    label = stringResource(R.string.booking_field_member),
                    query = state.memberQuery,
                    chosen = state.member?.name ?: state.entry?.holder?.let { unchanged(it) },
                    options = state.members.map { it.id to it.name },
                    enabled = !state.saving,
                    onQuery = callbacks.onMemberQuery,
                    onChosen = { id -> state.members.firstOrNull { it.id == id }?.let(callbacks.onMember) },
                )
                PickerOverflowNote(more = state.moreMembers)
                Picker(
                    label = stringResource(R.string.booking_field_place_transfer),
                    query = state.placeQuery,
                    chosen = state.place?.name ?: state.entry?.locationName?.let { unchanged(it) },
                    options = state.places.map { it.id to it.name },
                    enabled = !state.saving,
                    onQuery = callbacks.onPlaceQuery,
                    onChosen = { id -> state.places.firstOrNull { it.id == id }?.let(callbacks.onPlace) },
                )
                PickerOverflowNote(more = state.morePlaces)
                if (!state.transferMoves) {
                    TransferRefusal()
                }
                OrgUnitField(state = state, onOrgUnit = callbacks.onOrgUnit)
                if (state.materialIsScu) {
                    MergeStockField(state = state, onMergeStock = callbacks.onMergeStock)
                } else {
                    Muted(stringResource(R.string.booking_merge_piece))
                }
            }

            BookOutKind.SELL -> {
                if (state.terminals.isEmpty()) {
                    Muted(stringResource(R.string.booking_terminals_none))
                }
                state.terminals.forEach { terminal ->
                    Text(
                        text = terminal.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (state.terminal?.id == terminal.id) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                KrtPalette.White
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.saving) { callbacks.onTerminal(terminal) }
                                .padding(vertical = KrtSpacing.s8),
                    )
                }
                KrtTextField(
                    value = state.sellAmount,
                    onValueChange = callbacks.onSellAmount,
                    label = stringResource(R.string.booking_field_sell_amount),
                    enabled = !state.saving,
                )
            }
        }
    }
}

/**
 * A search-and-pick field.
 *
 * @param label what it is for.
 * @param query what the member typed.
 * @param chosen what is already picked, or `null`.
 * @param options the matches, as id-to-label pairs.
 * @param enabled whether it accepts input.
 * @param onQuery the search changed.
 * @param onChosen a row was picked, by id.
 */
@Composable
private fun Picker(
    label: String,
    query: String,
    chosen: String?,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onQuery: (String) -> Unit,
    onChosen: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        KrtCombobox(
            query = query,
            onQueryChange = {
                onQuery(it)
                open = true
            },
            options = options.map { (id, text) -> KrtOption(id, text) },
            onSelect = { option ->
                onChosen(option.value)
                open = false
            },
            expanded = open && options.isNotEmpty(),
            onExpandedChange = { open = it },
            label = label,
            enabled = enabled,
        )
        chosen?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A quiet line.
 *
 * @param text what it says.
 */
@Composable
internal fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
}

/**
 * The sheet's title for this mode.
 *
 * @return the string resource.
 */
private fun BookingMode.titleRes(): Int =
    when (this) {
        BookingMode.IN -> R.string.booking_mode_in
        BookingMode.OUT -> R.string.booking_mode_out
        BookingMode.NOTE -> R.string.booking_mode_note
    }

/**
 * The save action's label, naming the actual move: „Umbuchen" for a transfer, „Verkaufen" for a
 * sale.
 *
 * @return the string resource for the call to action.
 */
private fun BookingState.actionRes(): Int =
    if (mode == BookingMode.OUT) {
        when (outKind) {
            BookOutKind.DISCARD -> R.string.booking_mode_out
            BookOutKind.TRANSFER -> R.string.booking_out_transfer
            BookOutKind.SELL -> R.string.booking_out_sell
        }
    } else {
        mode.titleRes()
    }

/**
 * The icon beside the call to action.
 *
 * A transfer moves stock sideways rather than out, so it takes the exchange glyph the design system
 * uses wherever something changes hands.
 *
 * @return the drawable resource.
 */
private fun BookingState.actionIconRes(): Int =
    if (mode == BookingMode.OUT && outKind == BookOutKind.TRANSFER) {
        DesignR.drawable.ic_krt_swap
    } else {
        mode.iconRes()
    }

/**
 * The modes an entry can be booked in: booking out and editing the note, in that order.
 *
 * @return the modes, in the order the segment draws them.
 */
private fun InventoryEntry.modes(): List<BookingMode> = listOf(BookingMode.OUT, BookingMode.NOTE)

/**
 * The wire unit the amount is counted in; a game item is always whole pieces.
 *
 * @return the entry's unit when booking out, the picked material's or the item's fixed one when
 *   booking in, or `null` when nothing is picked yet.
 */
internal fun BookingState.unit(): String? =
    if (kind == BookingCatalogKind.ITEM) PIECE_UNIT else entry?.unit ?: material?.unit

/**
 * The localized label of [unit], for display only.
 *
 * @return the localized word, or `null` when nothing is picked yet.
 */
@Composable
internal fun BookingState.unitLabel(): String? =
    when (unit()) {
        PIECE_UNIT -> stringResource(R.string.materials_unit_piece)
        SCU_WIRE_UNIT -> stringResource(R.string.materials_unit_scu)
        else -> unit()
    }

/**
 * How an entry reads at the top of the sheet.
 *
 * @return the material with where it is, which is what identifies one entry among several.
 */
private fun InventoryEntry.headline(): String =
    listOfNotNull(materialName.takeIf { it.isNotBlank() }, locationName).joinToString(" · ")

/**
 * How a material reads in the picker.
 *
 * @return the name with its unit, so the amount field's label is no surprise.
 */
private fun MaterialOption.label(): String =
    listOfNotNull(name.takeIf { it.isNotBlank() }, unit).joinToString(" · ")

/**
 * How a terminal reads in the list.
 *
 * @return the terminal with what it pays, which is the reason to pick one over another.
 */
@Composable
private fun TerminalOption.label(): String =
    listOfNotNull(name.takeIf { it.isNotBlank() }, price?.let { formatAmount(it) })
        .joinToString(" · ")

/**
 * One whole-unit step up or down from the amount currently typed; a non-numeric value steps from
 * zero.
 *
 * @param by `+1` or `-1`.
 * @return the new value, never below zero.
 */
private fun String.step(by: Int): String {
    val current = trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    val next = (current + by).coerceAtLeast(0.0)
    return if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
}

/** Width of the quality field beside the amount — three digits and its label. */
private val QUALITY_FIELD_WIDTH = 104.dp

/**
 * The glyph the CTA carries for each mode.
 *
 * @return the icon that shows which way the material moves.
 */
private fun BookingMode.iconRes(): Int =
    when (this) {
        BookingMode.IN -> DesignR.drawable.ic_krt_download
        BookingMode.OUT -> DesignR.drawable.ic_krt_upload
        BookingMode.NOTE -> DesignR.drawable.ic_krt_edit
    }
