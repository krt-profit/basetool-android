/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLockToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.DENIAL_TOAST_MS
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import kotlinx.coroutines.delay
import java.math.BigDecimal
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Zuordnung sheet. */
const val ALLOCATION_SHEET_TAG = "allocation-sheet"

/** Test handle for its save button. */
const val ALLOCATION_SAVE_TAG = "allocation-save"

/** Width of the stepper inside an allocation row. */
private val STEPPER_WIDTH = 148.dp

/** Width of the coloured rail that tells the two splits apart. */
private val SPLIT_RAIL = 3.dp

/** What the sheet reports back. */
data class AllocationCallbacks(
    val onAmount: (AllocationKind, String, String) -> Unit,
    val onStep: (AllocationKind, String, Int) -> Unit,
    val onAdd: (AllocationKind, AllocationTarget) -> Unit,
    val onPick: (AllocationKind?) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „Zuordnung" — splitting one stock entry across Aufträge and Einsätze (design ch. 09 §3).
 *
 * The two splits are drawn apart and reconciled apart, because that is what the server does: the
 * same 642 SCU can be promised to an Auftrag **and** to an Einsatz, and a single shared rest would
 * be wrong in both directions. Each split therefore carries its own rest figure, and the artboard's
 * three states are exactly the three answers that figure can have — fully allocated, something
 * left, or more promised than exists.
 *
 * Overbooking is refused here rather than at the server. The endpoint answers it with a 422, but a
 * member who has typed their way past the entry's amount should see the sum turn red as they do it,
 * not after a round trip.
 *
 * @param state the open sheet.
 * @param callbacks what it reports back.
 * @param saveGate whether the caller may commit the split — a caller without the Logistiker role
 *   still opens the sheet and reads the numbers, and finds out at the CTA (design ch. 09,
 *   artboard 13: „Werte sichtbar, Editoren gedimmt — der CTA erklärt beim Antippen, was fehlt").
 * @param denials where that CTA raises its refusal.
 */
@Composable
fun AllocationSheet(
    state: AllocationSheetState,
    callbacks: AllocationCallbacks,
    saveGate: Gate,
    denials: DenialState,
) {
    KrtBottomSheet(
        onDismiss = callbacks.onDismiss,
        title = stringResource(R.string.allocation_title),
        modifier = Modifier.testTag(ALLOCATION_SHEET_TAG),
        // The Lager's other booking surface, and the same reason (design ch. 18 §3, E9).
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
            Text(
                text = state.subjectLine(),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            // Read-only, not hidden: the numbers are the reason to open this sheet at all, so a
            // caller without the grant still sees them — only the editors recede (artboard 13).
            Box(modifier = Modifier.alpha(if (saveGate.allowed) 1f else LOCKED_EDITOR_ALPHA)) {
                Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
                    val actions =
                        SplitActions(
                            onAmount = callbacks.onAmount,
                            onStep = callbacks.onStep,
                            onPick = callbacks.onPick,
                            onAdd = callbacks.onAdd,
                        )
                    listOf(AllocationKind.JOB_ORDER, AllocationKind.MISSION).forEach { kind ->
                        Split(
                            kind = kind,
                            pane =
                                SplitPane(
                                    rows = state.rows(kind),
                                    offerable = state.addable(kind),
                                    rest = state.rest(kind),
                                    picking = state.picking == kind,
                                    enabled = !state.saving,
                                ),
                            actions = actions,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.allocation_model_note),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            state.error?.let { error -> KrtFieldError(text = state.errorText(error)) }
            // A full-width CTA has no corner for a badge, so the lock leads the label instead, and
            // the button keeps its tap target so it can name the missing grant (artboard 13).
            val (dim, click) = rememberGated(saveGate, callbacks.onSave, denials)
            KrtCtaButton(
                text = stringResource(R.string.allocation_save),
                onClick = click,
                iconRes =
                    if (saveGate.allowed) DesignR.drawable.ic_krt_save else DesignR.drawable.ic_krt_lock,
                enabled = state.submittable || !saveGate.allowed,
                modifier = dim.fillMaxWidth().testTag(ALLOCATION_SAVE_TAG),
            )
            // The sheet is a window of its own, so the screen's toast would raise itself *behind*
            // it. Same holder, same single refusal — a second view of it, at the foot of whichever
            // surface the member is actually looking at („gleiches Bild in Zeile, Sheet, Menü und
            // Aktionsleiste", design ch. 09, artboard 14).
            denials.current?.let { denial ->
                LaunchedEffect(denial.serial) {
                    delay(DENIAL_TOAST_MS)
                    denials.clear()
                }
                KrtLockToast(title = denial.title, detail = denial.detail)
            }
        }
    }
}

/**
 * What one split shows, independent of which surface is showing it.
 *
 * Split out so the Zuordnung sheet and the book-in form draw the **same** thing: the split of an
 * amount across targets is one interaction, and two renderings of it would drift on the day one of
 * them gains a state the other does not.
 *
 * @property rows what is promised so far.
 * @property offerable what the add-picker may still offer.
 * @property rest what is not promised yet.
 * @property picking whether this split's add-picker is open.
 * @property enabled whether it may still be changed.
 * @property removable whether a row may be taken away.
 *
 *   The Zuordnung sheet says no: there a row **exists on the server**, and un-promising it means
 *   writing a zero, which is what the stepper already does. A book-in's rows exist only in the form
 *   until it is sent, so removing one is the only way to take it back.
 */
data class SplitPane(
    val rows: List<AllocationRow>,
    val offerable: List<AllocationTarget>,
    val rest: BigDecimal,
    val picking: Boolean,
    val enabled: Boolean,
    val removable: Boolean = false,
)

/**
 * What a split reports back.
 *
 * @property onAmount a row's amount was typed.
 * @property onStep a row's stepper was used.
 * @property onPick the add-picker was opened, or closed with `null`.
 * @property onAdd a target was picked.
 * @property onRemove a row is to go; never called unless [SplitPane.removable].
 */
data class SplitActions(
    val onAmount: (AllocationKind, String, String) -> Unit,
    val onStep: (AllocationKind, String, Int) -> Unit,
    val onPick: (AllocationKind?) -> Unit,
    val onAdd: (AllocationKind, AllocationTarget) -> Unit,
    val onRemove: (AllocationKind, String) -> Unit = { _, _ -> },
)

/**
 * One of the two splits: its heading, its rest, its rows and its add row.
 *
 * @param kind which split.
 * @param pane what it shows.
 * @param actions what it reports back.
 */
@Composable
internal fun Split(
    kind: AllocationKind,
    pane: SplitPane,
    actions: SplitActions,
) {
    val rail = kind.rail()
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(kind.headingRes()).krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = rail,
            )
            KrtHairlineRule(modifier = Modifier.weight(1f))
            RestChip(rest = pane.rest)
        }
        pane.rows.forEach { row ->
            AllocationRowView(
                row = row,
                rail = rail,
                enabled = pane.enabled,
                onAmount = { value -> actions.onAmount(kind, row.targetId, value) },
                onStep = { by -> actions.onStep(kind, row.targetId, by) },
                onRemove = if (pane.removable) ({ actions.onRemove(kind, row.targetId) }) else null,
            )
        }
        val addable = pane.offerable
        if (pane.picking) {
            addable.forEach { target ->
                TargetRow(target = target, rail = rail, onPick = { actions.onAdd(kind, target) })
            }
            KrtGhostButton(
                text = stringResource(R.string.allocation_add_cancel),
                onClick = { actions.onPick(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            KrtGhostButton(
                text = stringResource(kind.addRes()),
                onClick = { actions.onPick(kind) },
                iconRes = DesignR.drawable.ic_krt_plus,
                enabled = addable.isNotEmpty() && pane.enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The split's rest, in the one of three states it is in.
 *
 * @param rest what is not promised yet.
 */
@Composable
private fun RestChip(rest: BigDecimal) {
    when {
        rest.signum() < 0 -> {
            KrtChip(text = stringResource(R.string.allocation_overbooked), tone = KrtChipTone.Danger)
        }

        rest.signum() == 0 -> {
            KrtChip(text = stringResource(R.string.allocation_rest_none), tone = KrtChipTone.Success)
        }

        else -> {
            KrtChip(
                text = stringResource(R.string.allocation_rest_free, rest.stripTrailingZeros().toPlainString()),
                tone = KrtChipTone.Muted,
            )
        }
    }
}

/**
 * One promise, with the stepper that changes it.
 *
 * @param row the promise.
 * @param rail the split's colour.
 * @param enabled whether it can still be changed.
 * @param onAmount the field was typed into.
 * @param onStep a step was taken.
 * @param onRemove takes the row away, or `null` where rows are not removed.
 */
@Composable
private fun AllocationRowView(
    row: AllocationRow,
    rail: Color,
    enabled: Boolean,
    onAmount: (String) -> Unit,
    onStep: (Int) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, rail)
                .padding(KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label.krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = rail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        KrtStepperField(
            value = row.amount,
            onValueChange = onAmount,
            onDecrement = { onStep(-1) },
            onIncrement = { onStep(1) },
            enabled = enabled,
            modifier = Modifier.width(STEPPER_WIDTH),
        )
        onRemove?.let { remove ->
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_close,
                label = stringResource(R.string.allocation_remove),
                onClick = remove,
                enabled = enabled,
            )
        }
    }
}

/**
 * A target the member can still add to this split.
 *
 * @param target the Auftrag or Einsatz.
 * @param rail the split's colour.
 * @param onPick adds it at zero, ready to be stepped up.
 */
@Composable
private fun TargetRow(
    target: AllocationTarget,
    rail: Color,
    onPick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .clickable(onClick = onPick)
                .padding(KrtSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.label,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            target.subtitle?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        Box(rail = rail)
    }
}

/**
 * The split's colour as a small square, so a target row says which list it will join.
 *
 * @param rail the split's colour.
 */
@Composable
private fun Box(rail: Color) {
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .width(SPLIT_RAIL)
                .background(rail)
                .padding(vertical = KrtSpacing.s12),
    )
}

/**
 * The colour that tells the two splits apart.
 *
 * Orange for Aufträge because that is the app's own accent and an Auftrag is work this org took on;
 * the informational blue for Einsätze, which are scheduled rather than owed.
 *
 * @return the split's tint.
 */
@Composable
private fun AllocationKind.rail(): Color =
    when (this) {
        AllocationKind.JOB_ORDER -> MaterialTheme.colorScheme.primary
        AllocationKind.MISSION -> KrtTheme.colors.infoText
    }

/**
 * The split's heading.
 *
 * @return the string resource.
 */
private fun AllocationKind.headingRes(): Int =
    when (this) {
        AllocationKind.JOB_ORDER -> R.string.allocation_orders
        AllocationKind.MISSION -> R.string.allocation_missions
    }

/**
 * The split's add row.
 *
 * @return the string resource.
 */
private fun AllocationKind.addRes(): Int =
    when (this) {
        AllocationKind.JOB_ORDER -> R.string.allocation_add_order
        AllocationKind.MISSION -> R.string.allocation_add_mission
    }

/**
 * The line under the title: what is being split, and where it is.
 *
 * @return "Quantainium · Eintrag 642 SCU · Rhea · ARC-L1", with whatever parts the entry has.
 */
@Composable
private fun AllocationSheetState.subjectLine(): String =
    listOfNotNull(
        entry.materialName.takeIf { it.isNotBlank() },
        entry.amount?.let {
            stringResource(R.string.allocation_subject_amount, it, entry.unit.orEmpty()).trim()
        },
        entry.holder,
        entry.locationName,
    ).joinToString(" · ")

/**
 * What a refused save is called.
 *
 * The 422 is the server's own overbooking guard, and reaching it means the entry changed under the
 * member while the sheet was open — the local guard covers everything else.
 *
 * @param error the refusal.
 * @return the sentence to show.
 */
@Composable
private fun AllocationSheetState.errorText(error: ApiError): String =
    if (partial > 0) {
        pluralStringResource(R.plurals.allocation_error_partial, partial, partial)
    } else {
        stringResource(
            when (error) {
                is ApiError.Forbidden -> R.string.allocation_error_forbidden
                is ApiError.Validation -> R.string.allocation_error_amount
                is ApiError.OptimisticLock -> R.string.conflict_inline
                is ApiError.Conflict -> R.string.refused_inline
                else -> R.string.write_failed
            },
        )
    }

/** How far the editors recede when the caller may look but not save (design ch. 09, artboard 13). */
private const val LOCKED_EDITOR_ALPHA = 0.55f
