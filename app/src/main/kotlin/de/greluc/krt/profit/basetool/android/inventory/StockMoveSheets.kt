/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.writeFailureText
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the „Mein Lager" move sheet. */
const val STOCK_MOVE_SHEET_TAG: String = "stock-move-sheet"

/** Test handle for its call to action. */
const val STOCK_MOVE_CONFIRM_TAG: String = "stock-move-confirm"

/** Test handle for the result step's close. */
const val STOCK_MOVE_DONE_TAG: String = "stock-move-done"

/** Test handle for the „Keine Einheit" option. */
const val STOCK_MOVE_NO_UNIT_TAG: String = "stock-move-no-unit"

/** Test handle for the way out of the org-unit refusal. */
const val STOCK_MOVE_DROP_SHARED_TAG: String = "stock-move-drop-shared"

/** Width of a callout's accent edge — artboards 6 and 7. */
private val CALLOUT_EDGE = 4.dp

/** How strongly a callout tints its ground. */
private const val CALLOUT_TINT = 0.12f

/** How strongly the chosen unit's row is tinted — artboard 4's `rgba(231,126,35,.10)`. */
private const val CHOSEN_TINT = 0.10f

/** Diameter of a unit row's radio — artboard 4. */
private val RADIO_SIZE = 18.dp

/** Diameter of its dot. */
private val RADIO_DOT = 8.dp

/**
 * What the move sheet reports.
 *
 * @property onAmount the amount was typed.
 * @property onAll „Alles" was pressed.
 * @property onUnit a unit was picked; `null` is „Keine Einheit".
 * @property onMerge the merge opt-in changed.
 * @property onMode a selection rebooking changed its mode: `true` personal, `false` shared, `null` the
 *   place/holder move.
 * @property onDropShared the shared rows are to leave the selection.
 * @property onConfirm the call to action.
 * @property onClose the sheet was closed, or its result acknowledged.
 * @property onConflictReload the conflict dialog's „Neu laden".
 */
data class StockMoveCallbacks(
    val onAmount: (String) -> Unit,
    val onAll: () -> Unit,
    val onUnit: (String?) -> Unit,
    val onMerge: (Boolean) -> Unit,
    val onMode: (Boolean?) -> Unit,
    val onDropShared: () -> Unit,
    val onConfirm: () -> Unit,
    val onClose: () -> Unit,
    val onConflictReload: () -> Unit,
)

/**
 * The „Mein Lager" move sheet: „Umbuchen" between personal and the shared Lager and „Einheit
 * ändern", each for one row or the selection (design ch. 19, artboards 4 to 7).
 *
 * @param move the open sheet.
 * @param count how many rows a selection holds.
 * @param callbacks what it reports.
 */
@Composable
fun StockMoveSheet(
    move: StockMoveState,
    count: Int,
    callbacks: StockMoveCallbacks,
) {
    KrtBottomSheet(
        onDismiss = callbacks.onClose,
        title = stringResource(move.titleRes()),
        centred = isWideWindow(),
        modifier = Modifier.testTag(STOCK_MOVE_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = KrtSpacing.s20, end = KrtSpacing.s20, bottom = KrtSpacing.s20),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Muted(
                move.entry?.let { entrySubtitle(it) }
                    ?: pluralStringResource(R.plurals.stock_move_selected, count, count),
            )
            when {
                move.finished -> {
                    MoveResult(move = move, onClose = callbacks.onClose)
                }

                move.refused -> {
                    SharedRefusal(move = move, callbacks = callbacks)
                }

                move.kind == StockMoveKind.REBOOK -> {
                    RebookFields(move = move, callbacks = callbacks)
                }

                else -> {
                    OrgUnitFields(move = move, callbacks = callbacks)
                }
            }
            if (!move.finished && !move.refused) {
                MoveButtons(move = move, callbacks = callbacks)
            }
        }
    }
}

/**
 * The refusal line, the conflict dialog and the two buttons of a move that can still be sent.
 *
 * @param move the sheet.
 * @param callbacks what it reports.
 */
@Composable
private fun MoveButtons(
    move: StockMoveState,
    callbacks: StockMoveCallbacks,
) {
    move.error?.let { error ->
        KrtFieldError(
            text =
                error.writeFailureText(
                    if (error is ApiError.OptimisticLock) R.string.conflict_inline else move.failureRes(),
                ),
        )
    }
    ConflictOn(error = move.error, onReload = callbacks.onConflictReload)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KrtGhostButton(
            text = stringResource(R.string.personal_inventory_cancel),
            onClick = callbacks.onClose,
            enabled = !move.saving,
            modifier = Modifier.weight(1f),
        )
        KrtCtaButton(
            text = stringResource(move.ctaRes()),
            onClick = callbacks.onConfirm,
            iconRes = move.ctaIconRes(),
            enabled = move.submittable,
            modifier = Modifier.weight(CTA_WEIGHT).testTag(STOCK_MOVE_CONFIRM_TAG),
        )
    }
}

/**
 * The glyph beside the call to action: the exchange arrows for a rebooking, the check for a unit.
 *
 * @return the drawable resource.
 */
private fun StockMoveState.ctaIconRes(): Int =
    if (kind == StockMoveKind.REBOOK) DesignR.drawable.ic_krt_swap else DesignR.drawable.ic_krt_check

/** The call to action's share of the button row — artboard 4's `flex: 1.5`. */
private const val CTA_WEIGHT = 1.5f

/**
 * The fields of a rebooking: the mode, the amount of a single row, the pool when the stock goes into
 * the shared Lager, and the merge opt-in.
 *
 * @param move the sheet.
 * @param callbacks what it reports.
 */
@Composable
private fun RebookFields(
    move: StockMoveState,
    callbacks: StockMoveCallbacks,
) {
    ModeField(move = move, onMode = callbacks.onMode)
    if (move.bulk) {
        Muted(
            stringResource(
                if (move.toPersonal) R.string.stock_move_bulk_hint_personal else R.string.stock_move_bulk_hint_shared,
            ),
        )
    } else {
        AmountRow(move = move, callbacks = callbacks)
    }
    if (!move.toPersonal) {
        UnitList(move = move, withNone = false, onUnit = callbacks.onUnit)
        Muted(stringResource(R.string.stock_move_pool_note))
    }
    MergeField(move = move, onMerge = callbacks.onMerge)
}

/**
 * The org-unit change's fields: the picker with „Keine Einheit", the visibility notice and the merge
 * opt-in.
 *
 * @param move the sheet.
 * @param callbacks what it reports.
 */
@Composable
private fun OrgUnitFields(
    move: StockMoveState,
    callbacks: StockMoveCallbacks,
) {
    UnitList(move = move, withNone = true, onUnit = callbacks.onUnit)
    Callout(edge = KrtPalette.Info, iconRes = DesignR.drawable.ic_krt_info) {
        Text(
            text = stringResource(R.string.stock_move_visibility),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.Gray1,
        )
    }
    MergeField(move = move, onMerge = callbacks.onMerge)
}

/**
 * The refusal of an org-unit change over a selection that holds shared rows, drawn before the
 * picker, with the way out (artboard 7).
 *
 * @param move the sheet.
 * @param callbacks what it reports.
 */
@Composable
private fun SharedRefusal(
    move: StockMoveState,
    callbacks: StockMoveCallbacks,
) {
    Callout(edge = KrtPalette.Danger, iconRes = DesignR.drawable.ic_krt_warning) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.stock_move_shared_refusal,
                    move.sharedInSelection,
                    move.sharedInSelection,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.Gray1,
        )
    }
    Muted(stringResource(R.string.stock_move_shared_refusal_kept))
    KrtOutlineButton(
        text = stringResource(R.string.stock_move_drop_shared),
        onClick = callbacks.onDropShared,
        iconRes = DesignR.drawable.ic_krt_filter,
        modifier = Modifier.fillMaxWidth().testTag(STOCK_MOVE_DROP_SHARED_TAG),
    )
    KrtGhostButton(
        text = stringResource(R.string.personal_inventory_cancel),
        onClick = callbacks.onClose,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * „Umbuchungsart": a single row names its one direction; a selection chooses between the place or
 * holder move and the two personal ones.
 *
 * @param move the sheet.
 * @param onMode a mode was chosen.
 */
@Composable
private fun ModeField(
    move: StockMoveState,
    onMode: (Boolean?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val personal = stringResource(R.string.stock_move_mode_personal)
    val shared = stringResource(R.string.stock_move_mode_shared)
    val place = stringResource(R.string.stock_move_mode_location)
    val options =
        if (move.bulk) {
            listOf(KrtOption(MODE_LOCATION, place), KrtOption(MODE_PERSONAL, personal), KrtOption(MODE_SHARED, shared))
        } else if (move.toPersonal) {
            listOf(KrtOption(MODE_PERSONAL, personal))
        } else {
            listOf(KrtOption(MODE_SHARED, shared))
        }
    KrtSelectField(
        value = if (move.toPersonal) personal else shared,
        options = options,
        onSelect = { option ->
            open = false
            when (option.value) {
                MODE_LOCATION -> onMode(null)
                MODE_PERSONAL -> onMode(true)
                else -> onMode(false)
            }
        },
        expanded = open,
        onExpandedChange = { open = it },
        label = stringResource(R.string.stock_move_mode),
        selectedValue = if (move.toPersonal) MODE_PERSONAL else MODE_SHARED,
        enabled = !move.saving,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The mode values of [ModeField]. */
private const val MODE_LOCATION = "location"
private const val MODE_PERSONAL = "personal"
private const val MODE_SHARED = "shared"

/**
 * The amount of a single rebooking with „Alles" beside it.
 *
 * @param move the sheet.
 * @param callbacks what it reports.
 */
@Composable
private fun AmountRow(
    move: StockMoveState,
    callbacks: StockMoveCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        KrtFieldLabel(
            text = stringResource(R.string.booking_field_amount, move.entry?.unit.unitWord()),
            enabled = !move.saving,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtTextField(
                value = move.amount,
                onValueChange = callbacks.onAmount,
                enabled = !move.saving,
                modifier = Modifier.weight(1f),
            )
            KrtGhostButton(
                text = stringResource(R.string.stock_move_all),
                onClick = callbacks.onAll,
                enabled = !move.saving,
            )
        }
    }
}

/**
 * The unit picker as a radio list, each unit tagged with its kind so four kinds of one name stay
 * apart (artboard 4).
 *
 * @param move the sheet.
 * @param withNone whether „Keine Einheit" leads the list, as only the org-unit change allows.
 * @param onUnit a unit was picked.
 */
@Composable
private fun UnitList(
    move: StockMoveState,
    withNone: Boolean,
    onUnit: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        KrtFieldLabel(text = stringResource(R.string.stock_move_unit), enabled = !move.saving)
        if (!move.unitsLoaded) {
            Muted(stringResource(R.string.stock_move_units_loading))
            return@Column
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary)
                    .background(KrtPalette.SurfaceInput),
        ) {
            if (withNone) {
                UnitRow(
                    label = stringResource(R.string.lager_no_unit),
                    kind = null,
                    selected = move.unitId == null,
                    muted = true,
                    enabled = !move.saving,
                    onClick = { onUnit(null) },
                    modifier = Modifier.testTag(STOCK_MOVE_NO_UNIT_TAG),
                )
            }
            move.units.forEachIndexed { index, unit ->
                if (withNone || index > 0) {
                    KrtHairlineRule()
                }
                UnitRow(
                    label = unit.name,
                    kind = unit.kind,
                    selected = move.unitId == unit.id,
                    muted = false,
                    enabled = !move.saving,
                    onClick = { onUnit(unit.id) },
                )
            }
        }
        if (move.units.isEmpty()) {
            Muted(stringResource(R.string.stock_move_units_none))
        }
    }
}

/**
 * One row of the unit picker.
 *
 * @param label the unit's name.
 * @param kind its kind, or `null` for „Keine Einheit".
 * @param selected whether it is chosen.
 * @param muted whether the label is drawn muted.
 * @param enabled whether it can be chosen.
 * @param onClick it was chosen.
 * @param modifier layout modifier.
 */
@Composable
@Suppress("LongParameterList")
private fun UnitRow(
    label: String,
    kind: OrgUnitKind?,
    selected: Boolean,
    muted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (selected) primary.copy(alpha = CHOSEN_TINT) else Color.Transparent)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = KrtSpacing.s12)
                .height(KrtSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(RADIO_SIZE)
                    .height(RADIO_SIZE)
                    .border(
                        KrtSpacing.hairline,
                        if (selected) primary else KrtPalette.Gray2,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .width(RADIO_DOT)
                            .height(RADIO_DOT)
                            .background(primary, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) KrtPalette.TextMuted else KrtPalette.White,
            modifier = Modifier.weight(1f),
        )
        kind?.let { KrtChip(text = stringResource(it.labelRes()), tone = KrtChipTone.Muted) }
    }
}

/**
 * The merge opt-in; only offered when every row is an `SCU` material — the server merges pieces
 * anyway, and a control that changes nothing is worse than none.
 *
 * @param move the sheet.
 * @param onMerge the opt-in changed.
 */
@Composable
private fun MergeField(
    move: StockMoveState,
    onMerge: (Boolean) -> Unit,
) {
    if (!move.scu) {
        return
    }
    KrtCheckboxRow(
        checked = move.merge,
        onCheckedChange = onMerge,
        label = stringResource(R.string.stock_move_merge),
        enabled = !move.saving,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The result step of a selection move: two figures, one sentence, and „Fertig" (artboards 5 and 7).
 *
 * @param move the finished sheet.
 * @param onClose closes it and ends the selection.
 */
@Composable
private fun MoveResult(
    move: StockMoveState,
    onClose: () -> Unit,
) {
    val rebooked = move.rebooked
    val changed = move.changed
    val done = rebooked?.rebooked ?: changed?.changed ?: 0
    val skipped = rebooked?.skipped ?: changed?.skipped ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
        KrtFigureTile(
            label =
                stringResource(
                    if (rebooked !=
                        null
                    ) {
                        R.string.inventory_bulk_move_rebooked
                    } else {
                        R.string.stock_move_changed
                    },
                ),
            value = done.toString(),
            tone = KrtFigureTone.Success,
            modifier = Modifier.weight(1f),
        )
        KrtFigureTile(
            label = stringResource(R.string.inventory_bulk_move_skipped),
            value = skipped.toString(),
            tone = KrtFigureTone.Neutral,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text =
            if (rebooked != null) {
                pluralStringResource(R.plurals.stock_move_rebook_result, done, done, skipped)
            } else {
                pluralStringResource(R.plurals.stock_move_unit_result, done, done, skipped)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.Gray1,
    )
    if (rebooked != null) {
        Muted(stringResource(R.string.stock_move_whole_rows))
    }
    KrtCtaButton(
        text = stringResource(R.string.stock_move_done),
        onClick = onClose,
        iconRes = DesignR.drawable.ic_krt_check,
        modifier = Modifier.fillMaxWidth().testTag(STOCK_MOVE_DONE_TAG),
    )
}

/**
 * A notice with a coloured edge and a tinted ground, as artboards 6 and 7 draw them.
 *
 * @param edge the accent.
 * @param iconRes the glyph beside the text.
 * @param content the text.
 */
@Composable
private fun Callout(
    edge: Color,
    iconRes: Int,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(edge.copy(alpha = CALLOUT_TINT)),
    ) {
        Box(modifier = Modifier.width(CALLOUT_EDGE).fillMaxHeight().background(edge))
        Row(
            modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KrtIcon(id = iconRes, contentDescription = null, tint = edge)
            content()
        }
    }
}

/**
 * The line under a single move's title: what the row is, where, its grade and amount, and whether it
 * is personal (artboard 4).
 *
 * @param entry the row.
 * @return the line.
 */
@Composable
private fun entrySubtitle(entry: InventoryEntry): String =
    listOfNotNull(
        entry.materialName.takeIf { it.isNotBlank() },
        entry.locationName,
        entry.quality?.let { stringResource(R.string.inventory_quality, it) },
        entry.amount?.let { "${formatAmount(it)} ${entry.unit.unitWord()}".trim() },
        stringResource(if (entry.personal) R.string.stock_move_personal_word else R.string.stock_move_shared_word),
    ).joinToString(" · ")

/**
 * The localized word for a wire unit.
 *
 * @receiver the unit, or `null`.
 * @return the word, or an empty string for no unit.
 */
@Composable
internal fun String?.unitWord(): String =
    when {
        this == null -> ""
        isScu() -> stringResource(R.string.materials_unit_scu)
        equals("PIECE", ignoreCase = true) -> stringResource(R.string.materials_unit_piece)
        else -> this
    }

/**
 * The label of an org-unit kind in the picker.
 *
 * @return the string resource.
 */
private fun OrgUnitKind.labelRes(): Int =
    when (this) {
        OrgUnitKind.SQUADRON -> R.string.stock_move_kind_squadron
        OrgUnitKind.SPECIAL_COMMAND -> R.string.stock_move_kind_sk
        OrgUnitKind.BEREICH -> R.string.stock_move_kind_bereich
        OrgUnitKind.ORGANISATIONSLEITUNG -> R.string.stock_move_kind_ol
        OrgUnitKind.UNKNOWN -> R.string.stock_move_kind_unknown
    }

/**
 * The sheet's title.
 *
 * @return the string resource.
 */
private fun StockMoveState.titleRes(): Int =
    when {
        kind == StockMoveKind.REBOOK && bulk -> R.string.stock_move_title_bulk_rebook
        kind == StockMoveKind.REBOOK -> R.string.stock_move_title_rebook
        bulk -> R.string.stock_move_title_bulk_unit
        else -> R.string.stock_move_title_unit
    }

/**
 * The call to action's label.
 *
 * @return the string resource.
 */
private fun StockMoveState.ctaRes(): Int =
    if (kind == StockMoveKind.REBOOK) R.string.stock_move_title_rebook else R.string.stock_move_unit_cta

/**
 * What a refusal says when the server's own sentence is not about a field.
 *
 * @return the string resource.
 */
private fun StockMoveState.failureRes(): Int =
    if (kind == StockMoveKind.REBOOK) R.string.stock_move_rebook_failed else R.string.stock_move_unit_failed
