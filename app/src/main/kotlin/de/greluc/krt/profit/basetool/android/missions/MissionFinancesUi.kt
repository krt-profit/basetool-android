/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.common.formatSignedAmount
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinanceEntry
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDataValue
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import java.math.BigDecimal
import java.math.RoundingMode
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Finanzen: the totals band and the entries, on their own load state.
 *
 * @param phase how far the money has got.
 * @param onRetry retries just this tab.
 */
internal fun LazyListScope.financesTab(
    state: MissionDetailState,
    onRetry: () -> Unit,
    actions: MissionFinanceActions,
) {
    when (val phase = state.finances) {
        is MissionFinancesPhase.Idle, is MissionFinancesPhase.Loading -> {
            item { KrtLoadingIndicator(text = stringResource(R.string.mission_detail_tab_finances)) }
        }

        is MissionFinancesPhase.Failed -> {
            item {
                // A refusal here is ordinary — a member may see the Einsatz and not its books — so
                // it gets its own sentence rather than the generic outage copy.
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_bank,
                    title = stringResource(R.string.mission_detail_tab_finances),
                    message =
                        stringResource(
                            if (phase.error is ApiError.Forbidden) {
                                R.string.mission_detail_finance_forbidden
                            } else {
                                R.string.mission_detail_error_message
                            },
                        ),
                    actionText =
                        if (phase.error is ApiError.Forbidden) null else stringResource(R.string.missions_retry),
                    onAction = if (phase.error is ApiError.Forbidden) null else onRetry,
                )
            }
        }

        is MissionFinancesPhase.Ready -> {
            financeContent(phase.finances, state, actions)
        }
    }
}

/**
 * The Finanzen tab once it has loaded.
 *
 * @param finances the totals and the entries.
 * @param state the screen, for who the caller is and whether a write may be offered.
 * @param actions what the tab reports back.
 */
private fun LazyListScope.financeContent(
    finances: MissionFinances,
    state: MissionDetailState,
    actions: MissionFinanceActions,
) {
    item { FinanceBand(finances) }
    if (finances.entries.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_finances) }
    } else {
        item {
            // One flush card holding every booking and closing on the per-head line — the entries
            // and the share they add up to are one thing, and a gap between them would let the
            // share read as a fourth booking.
            KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
                finances.entries.forEach { entry ->
                    FinanceEntryRow(
                        entry = entry,
                        mine = entry.participantId != null && entry.participantId == state.mySignUp?.id,
                        writable = state.writable,
                        actions = actions,
                    )
                    KrtHairlineRule()
                }
                PerHeadShare(finances = finances, detail = state.detail)
            }
        }
    }
    item { FinanceAction(state = state, actions = actions) }
}

/**
 * The tab's opening band: what came in, what went out, what is left.
 *
 * Three tiles across, not three key/value rows — artboard 06-2 makes this the first thing read on
 * the tab, and the tones are what separate the two halves at a glance.
 *
 * @param finances the totals.
 */
@Composable
private fun FinanceBand(finances: MissionFinances) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        KrtFigureTile(
            label = stringResource(R.string.mission_detail_finance_income),
            value = formatSignedAmount(finances.incomeSum.orEmpty(), income = true),
            tone = KrtFigureTone.Success,
            modifier = Modifier.weight(1f),
            compact = true,
        )
        KrtFigureTile(
            label = stringResource(R.string.mission_detail_finance_expense),
            value = formatSignedAmount(finances.expenseSum.orEmpty(), income = false),
            tone = KrtFigureTone.Danger,
            modifier = Modifier.weight(1f),
            compact = true,
        )
        // The net carries no sign of its own: it is a balance, and a leading plus on a positive
        // result would read as a third booking rather than as the sum of the two above it.
        KrtFigureTile(
            label = stringResource(R.string.mission_detail_finance_net),
            value = formatAmount(finances.total.orEmpty()),
            tone = KrtFigureTone.Primary,
            modifier = Modifier.weight(1f),
            compact = true,
        )
    }
}

/**
 * Booking, and the reason it is not offered.
 *
 * A booking needs a participant to hang off, and the only one the app may name is the caller's
 * own — so a member who has not signed up is told that rather than shown a button that answers
 * 403.
 *
 * @param state the screen, for the caller's own row and the write gate.
 * @param actions what it reports back.
 */
@Composable
private fun FinanceAction(
    state: MissionDetailState,
    actions: MissionFinanceActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        if (state.mySignUp == null) {
            Text(
                text = stringResource(R.string.mission_detail_finance_needs_signup),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        } else {
            // Outline, not filled: the one filled button on this screen belongs to the sign-up bar
            // below it, and artboard 06-2 draws this action with an orange border.
            KrtOutlineButton(
                text = stringResource(R.string.mission_detail_finance_add),
                onClick = actions.onAdd,
                iconRes = DesignR.drawable.ic_krt_plus,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(MISSION_FINANCE_ADD_TAG)
                        .writeAlpha(state.bookingPossible),
                enabled = state.bookingPossible,
            )
        }
        state.error?.let { error -> SignUpError(error = error) }
    }
}

/**
 * „Anteil je Teilnehmer (14)" — the card's closing line.
 *
 * **Computed, and the divisor is on screen.** The wire carries no per-head figure, so this is the
 * net divided by the number of registered participants, exactly as artboard 06-2 draws it
 * (74.700 ÷ 14 = 5.335). It does **not** account for who has chosen the Org-Kasse over a payout —
 * which is why the count it divided by is named in the label rather than hidden behind it. The
 * question of whether the share should exclude donors is on the design gap list.
 *
 * @param finances the totals.
 * @param detail the Einsatz, for how many are registered.
 */
@Composable
private fun PerHeadShare(
    finances: MissionFinances,
    detail: MissionDetail?,
) {
    val heads = detail?.registeredParticipants ?: 0
    val net = finances.total?.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it) }.getOrNull() }
    if (heads <= 0 || net == null) {
        return
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .defaultMinSize(minHeight = KrtSpacing.controlHeight)
                .padding(horizontal = KrtSpacing.s14, vertical = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.mission_detail_finance_per_head, heads).krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.Gray1,
            modifier = Modifier.weight(1f),
        )
        KrtDataValue(
            // Rounded DOWN: a share is what everybody can actually be paid, and rounding up would
            // promise, across fourteen people, money the Einsatz did not make.
            text =
                stringResource(
                    R.string.mission_detail_finance_per_head_value,
                    formatAmount(net.divide(BigDecimal(heads), 0, RoundingMode.DOWN).toPlainString()),
                ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * One booking, with the two actions the caller has on their own.
 *
 * Someone else's booking is theirs to change: the server refuses an edit by anyone but the owner
 * or an admin, and the app does not offer what it knows will be refused.
 *
 * @param entry the booking.
 * @param mine whether it hangs off the caller's own sign-up.
 * @param writable whether a write may be offered at all.
 * @param actions what the row reports back.
 */
@Composable
private fun FinanceEntryRow(
    entry: MissionFinanceEntry,
    mine: Boolean,
    writable: Boolean,
    actions: MissionFinanceActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = KrtSpacing.denseRow)
                .padding(horizontal = KrtSpacing.s14, vertical = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.note.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
            )
            entry.participantName?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
            }
        }
        // The amount as tinted figures, not as a chip: artboard 06-2 draws the money itself in the
        // success or danger tint, and a chip around every row's number turns a ledger into a wall
        // of boxes.
        Text(
            text = formatSignedAmount(entry.amount, entry.income),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (entry.income) KrtTheme.colors.successText else KrtTheme.colors.dangerText,
        )
        if (mine) {
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_edit,
                label = stringResource(R.string.mission_detail_finance_edit),
                onClick = { actions.onEdit(entry) },
                modifier = Modifier.testTag(MISSION_FINANCE_EDIT_TAG).writeAlpha(writable),
                enabled = writable,
            )
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_trash,
                label = stringResource(R.string.mission_detail_finance_delete),
                onClick = { actions.onDelete(entry) },
                modifier = Modifier.testTag(MISSION_FINANCE_DELETE_TAG).writeAlpha(writable),
                enabled = writable,
            )
        }
    }
}

/**
 * The booking form.
 *
 * The direction is a segment rather than a signed amount: a minus typed into a number field is a
 * character a member can lose, and the sign is what decides whether the Einsatz earned or spent.
 *
 * @param draft what the form holds.
 * @param state the screen, for the save gate and the last refusal.
 * @param actions what it reports back.
 */
@Composable
internal fun FinanceEntrySheet(
    draft: FinanceEntryDraft,
    state: MissionDetailState,
    actions: MissionFinanceActions,
) {
    // Artboard 06.4 gives the entry one tone throughout: green for an Einnahme, red for an Ausgabe,
    // on the chosen segment and on the amount alike. The sign is never typed - it IS the segment -
    // and the hint under the field says so, which is why it changes with the choice.
    val tone = if (draft.income) KrtPalette.Success else KrtPalette.Danger
    val amountTone = if (draft.income) KrtPalette.SuccessText else KrtPalette.DangerText
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        modifier = Modifier.testTag(MISSION_FINANCE_SHEET_TAG),
        title = stringResource(R.string.mission_detail_finance_title),
    ) {
        // The sheet scrolls: with the keyboard up on a small phone the form is taller than what is
        // left of the screen, and a save button that cannot be reached is a form that cannot be
        // submitted.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            KrtFieldLabel(text = stringResource(R.string.mission_detail_finance_type))
            KrtSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.mission_detail_finance_type_income),
                        stringResource(R.string.mission_detail_finance_type_expense),
                    ),
                selectedIndex = if (draft.income) 0 else 1,
                onSelect = { actions.onIncome(it == 0) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
                stretch = true,
                activeColor = tone,
                activeContentColor = KrtPalette.White,
            )
            KrtTextField(
                value = draft.amount,
                onValueChange = actions.onAmount,
                label = stringResource(R.string.mission_detail_finance_amount),
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textAlign = TextAlign.End,
                tabularFigures = true,
                valueStyle = MaterialTheme.typography.headlineSmall.copy(color = amountTone),
            )
            Text(
                text =
                    stringResource(
                        if (draft.income) {
                            R.string.mission_detail_finance_amount_hint_income
                        } else {
                            R.string.mission_detail_finance_amount_hint_expense
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = draft.note,
                onValueChange = actions.onNote,
                label = stringResource(R.string.mission_detail_finance_note),
                placeholder = stringResource(R.string.mission_detail_finance_note_hint),
                enabled = !state.saving,
            )
            state.error?.let { error -> SignUpError(error = error) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = actions.onDismiss,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = actions.onSave,
                    iconRes = DesignR.drawable.ic_krt_save,
                    modifier = Modifier.testTag(MISSION_FINANCE_SAVE_TAG),
                    enabled = draft.submittable && state.writable,
                )
            }
        }
    }
}
