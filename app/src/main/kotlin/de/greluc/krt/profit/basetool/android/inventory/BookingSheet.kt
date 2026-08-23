/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand

/** Test handle for the booking sheet. */
const val BOOKING_SHEET_TAG: String = "booking-sheet"

/** Test handle for the booking's save action. */
const val BOOKING_SAVE_TAG: String = "booking-save"

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
        title = stringResource(state.mode.titleRes()),
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
            } else {
                AmountField(state = state, onAmount = callbacks.onAmount)
            }

            if (state.mode == BookingMode.IN) {
                KrtTextField(
                    value = state.quality,
                    onValueChange = callbacks.onQuality,
                    label = stringResource(R.string.booking_field_quality),
                    enabled = !state.saving,
                )
                PlaceField(state = state, callbacks = callbacks)
            }

            if (state.mode == BookingMode.OUT) {
                OutKindField(state = state, callbacks = callbacks)
            }

            state.error?.let { error ->
                KrtFieldError(
                    text =
                        stringResource(
                            if (error is ApiError.OptimisticLock) {
                                R.string.conflict_body
                            } else {
                                R.string.write_failed
                            },
                        ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = callbacks.onDismiss,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.booking_save),
                    onClick = callbacks.onSave,
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
    val onSellAmount: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
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
        KrtTextField(
            value = state.amount,
            onValueChange = onAmount,
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
                    chosen = state.member?.name,
                    options = state.members.map { it.id to it.name },
                    enabled = !state.saving,
                    onQuery = callbacks.onMemberQuery,
                    onChosen = { id -> state.members.firstOrNull { it.id == id }?.let(callbacks.onMember) },
                )
                PlaceField(state = state, callbacks = callbacks)
                if ((state.member != null || state.place != null) && !state.transferMoves) {
                    KrtFieldError(text = stringResource(R.string.booking_transfer_unchanged))
                } else {
                    Muted(stringResource(R.string.booking_transfer_note))
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
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtTextField(value = query, onValueChange = onQuery, label = label, enabled = enabled)
        chosen?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        options.forEach { (id, text) ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onChosen(id) }
                        .padding(vertical = KrtSpacing.sm),
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
private fun Muted(text: String) {
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
private fun BookingState.unit(): String? = entry?.unit ?: material?.unit

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
