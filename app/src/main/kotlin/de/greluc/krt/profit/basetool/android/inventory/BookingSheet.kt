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
import de.greluc.krt.profit.basetool.android.core.data.BookOutKind
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
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the booking sheet. */
const val BOOKING_SHEET_TAG: String = "booking-sheet"

/** Test handle for the booking's save action. */
const val BOOKING_SAVE_TAG: String = "booking-save"

/** Test handle for the transfer's stock-merge opt-in. */
const val BOOKING_MERGE_TAG: String = "booking-merge"

/**
 * The Lager's booking form: Ein, Aus, Notiz (design ch. 09, Frame 2).
 *
 * One sheet with a segment rather than three sheets, because that is what the design draws and
 * because the amount — the field the moving modes share — then survives a change of mind.
 *
 * **A mode is only offered when it can be used.** `Aus` and `Notiz` act on an entry, so opening the
 * form from the "Einbuchen" action shows `Ein` alone; opening it from an entry shows the two that
 * apply to it. A segment whose other half cannot work is a control that lies.
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
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
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
                Picker(
                    label = stringResource(R.string.booking_field_material),
                    query = state.materialQuery,
                    chosen = state.material?.name,
                    options = state.materials.map { it.id to it.label() },
                    enabled = !state.saving,
                    onQuery = callbacks.onMaterialQuery,
                    onChosen = { id -> state.materials.firstOrNull { it.id == id }?.let(callbacks.onMaterial) },
                )
            }

            if (state.mode == BookingMode.NOTE) {
                KrtTextField(
                    value = state.note,
                    onValueChange = callbacks.onNote,
                    label = stringResource(R.string.inventory_entry_note),
                    enabled = !state.saving,
                )
            } else if (state.mode == BookingMode.IN) {
                // Design ch. 09 artboard 2 puts the amount and the quality on ONE row: they are
                // two readings of the same stack, and stacked full-width they read as two separate
                // decisions. Quality is the narrow one — it is three digits.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
                PlaceField(state = state, callbacks = callbacks)
            } else {
                AmountField(state = state, onAmount = callbacks.onAmount)
            }

            if (state.mode == BookingMode.OUT) {
                OutKindField(state = state, callbacks = callbacks)
                // Below the out-kind, because a sale's proceeds split depends on the mission
                // shares and a transfer carries the reduced tags with it — the plan is about the
                // amount, so it follows everything that decides what happens to the amount.
                HerkunftSection(
                    state = state,
                    onJobOrderShare = callbacks.onJobOrderShare,
                    onMissionShare = callbacks.onMissionShare,
                )
            }

            state.error?.let { error ->
                KrtFieldError(
                    text =
                        stringResource(
                            if (error is ApiError.OptimisticLock) {
                                R.string.conflict_inline
                            } else {
                                R.string.write_failed
                            },
                        ),
                )
            }
            ConflictOn(error = state.error, onReload = callbacks.onConflictReload)
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = callbacks.onDismiss,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    // The CTA names the move it makes — "Einbuchen", "Ausbuchen" — rather than the
                    // generic "Buchen" (artboard 09.2). On a form with three modes, a button that
                    // reads the same in all three is the one control that does not say which one
                    // is armed.
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
 * Everything the sheet reports back.
 *
 * A parameter object because the sheet has eleven of them, and eleven positional lambdas is a
 * signature nobody can call correctly twice.
 *
 * @property onMode the segment changed.
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtFieldLabel(
                text =
                    stringResource(
                        R.string.booking_field_amount,
                        state.unit() ?: stringResource(R.string.booking_unit_unknown),
                    ),
                enabled = !state.saving,
            )
            KrtHint(explanation = stringResource(R.string.booking_amount_hint))
        }
        // A stepper, not a bare field: the artboard's `− 120 +` is what a member uses when the
        // amount is one or two off, which on a booking form it usually is. Typing still works —
        // the steps are an addition, not a replacement.
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
}

/**
 * Which org-unit pool a transfer's moved row lands in.
 *
 * A plain list rather than a search field: the options are one member's memberships, which is a
 * handful, and a search box over four rows asks the member to type what they can already see.
 *
 * It is **not** shown when there is nothing to choose. A receiving member with no membership at
 * all leaves the row unpooled, which is the server's own outcome and not a field the form can fix;
 * a member with exactly one has no decision to make and the server resolves it.
 *
 * @param state the form.
 * @param onOrgUnit a pool was picked.
 */
@Composable
private fun OrgUnitField(
    state: BookingState,
    onOrgUnit: (OrgUnitOption) -> Unit,
) {
    // Hidden only for a membershipless target: that row is unpooled and there is nothing to
    // choose. A single membership is still shown — preset and read-only in effect — because the
    // member is entitled to see which pool their stock is about to land in.
    if (state.orgUnits.isEmpty()) {
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
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
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = KrtPalette.DangerText)
                .padding(KrtSpacing.sm),
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
 * The whole row is the target, not the 24 dp control: `KrtToggle` deliberately carries no label
 * and no gesture of its own, because a bare toggle cannot reach the 48 dp minimum without being
 * inflated out of its drawn size.
 *
 * @param state the form.
 * @param onMergeStock the opt-in changed.
 */
@Composable
private fun MergeStockField(
    state: BookingState,
    onMergeStock: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(BOOKING_MERGE_TAG)
                    .clickable(enabled = !state.saving) { onMergeStock(!state.mergeStock) }
                    .padding(vertical = KrtSpacing.xs),
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
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
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
                    // Both targets show the row's own value with „— unverändert" rather than
                    // sitting empty (artboard 15/16). Empty is what the app sent for „keep it",
                    // but on screen it read as a field nobody had filled in.
                    chosen = state.member?.name ?: state.entry?.holder?.let { unchanged(it) },
                    options = state.members.map { it.id to it.name },
                    enabled = !state.saving,
                    onQuery = callbacks.onMemberQuery,
                    onChosen = { id -> state.members.firstOrNull { it.id == id }?.let(callbacks.onMember) },
                )
                Picker(
                    label = stringResource(R.string.booking_field_place_transfer),
                    query = state.placeQuery,
                    chosen = state.place?.name ?: state.entry?.locationName?.let { unchanged(it) },
                    options = state.places.map { it.id to it.name },
                    enabled = !state.saving,
                    onQuery = callbacks.onPlaceQuery,
                    onChosen = { id -> state.places.firstOrNull { it.id == id }?.let(callbacks.onPlace) },
                )
                // The refusal speaks about the two pickers directly above, so it stays with them.
                // The pool and the merge option are a separate decision about where the moved row
                // lands, and a rule about the targets read underneath them looks like a rule about
                // the checkbox.
                if (!state.transferMoves) {
                    TransferRefusal()
                }
                OrgUnitField(state = state, onOrgUnit = callbacks.onOrgUnit)
                if (state.materialIsScu) {
                    // Offered only for an SCU material: the server merges a PIECE transfer into an
                    // identical target stack regardless, so a toggle there would be a control that
                    // does nothing — and the member could not tell that from one that does.
                    MergeStockField(state = state, onMergeStock = callbacks.onMergeStock)
                } else {
                    // Not silence: an absent control reads as a missing feature, where the frame
                    // draws a line saying the server already does it (artboard 16).
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
                                .padding(vertical = KrtSpacing.sm),
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
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
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
 * What the save action is called, which is not always what the mode is called.
 *
 * A transfer is „Umbuchen" and a sale is „Verkaufen" — both reached through the Ausbuchen mode,
 * both different events in the ledger from a discard. The button is the last thing a member reads
 * before committing one, so it names the move the form will actually make rather than the segment
 * they used to get here.
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
 * The modes this entry can be booked in.
 *
 * Booking out and editing the note, in that order. Rebooking private stock is deliberately absent:
 * the Lager reads exclude private stock entirely, so no entry that could be rebooked ever reaches
 * this sheet, and a segment half that can only refuse is a control that lies.
 *
 * @return the modes, in the order the segment draws them.
 */
private fun InventoryEntry.modes(): List<BookingMode> = listOf(BookingMode.OUT, BookingMode.NOTE)

/**
 * The unit the amount is counted in.
 *
 * @return the entry's unit when booking out, the picked material's when booking in, or `null`.
 */
internal fun BookingState.unit(): String? = entry?.unit ?: material?.unit

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
 * One step up or down from the amount currently typed.
 *
 * Whole units, because the sub-unit precision the Lager allows (cSCU, µSCU) is something a member
 * types rather than steps to. A value that is not a number at all steps from zero rather than
 * refusing — the field is mid-edit, and a dead button in that moment reads as broken.
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
