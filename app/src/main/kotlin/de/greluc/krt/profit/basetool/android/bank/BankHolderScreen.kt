/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BankHolderBooking
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTotalTile
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import java.time.Instant
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The holder detail, for the tests that read it. */
const val BANK_HOLDER_TAG: String = "bank-holder-detail"

/**
 * What the holder detail reports back.
 *
 * @property onTransfer the custody transfer sheet is to open.
 * @property onPage a different page of postings was asked for.
 * @property onLocked a locked control was tapped without Bank-Management.
 */
data class BankHolderActions(
    val onTransfer: () -> Unit,
    val onPage: (Int) -> Unit,
    val onLocked: () -> Unit,
)

/**
 * One holder's custody — design chapter 12, artboard 8.
 *
 * **Custody is kept at org-unit level, with no allocation to individual accounts.** The screen says
 * so under the total rather than leaving the reader to wonder which account the figure belongs to:
 * the handoff's correction of 27.08.2026 exists because that is exactly the wrong assumption to
 * make here.
 *
 * @param state what the screen holds.
 * @param management whether the caller may move custody.
 * @param actions what the screen reports back.
 * @param modifier layout modifier.
 */
@Composable
fun BankHolderScreen(
    state: BankHolderState,
    management: Boolean,
    actions: BankHolderActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(BANK_HOLDER_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
    ) {
        item(key = "total") {
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                KrtTotalTile(
                    label = stringResource(R.string.bank_holder_total_label),
                    value = formatAmount(state.holder?.totalHeld.orEmpty()),
                    unit = stringResource(R.string.bank_total_unit),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.bank_holder_unit_level),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        item(key = "cta") {
            KrtCtaButton(
                text = stringResource(R.string.bank_holder_transfer),
                onClick = { if (management) actions.onTransfer() else actions.onLocked() },
                modifier = Modifier.fillMaxWidth(),
                iconRes = if (management) null else DesignR.drawable.ic_krt_lock,
            )
        }
        if (state.bookings.isEmpty()) {
            item(key = "empty") {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_bank,
                    title = stringResource(R.string.bank_holder_empty_title),
                    message = stringResource(R.string.bank_holder_empty_message),
                    modifier = Modifier.padding(KrtSpacing.lg),
                )
            }
        }
        items(state.bookings, key = { it.id }) { booking ->
            HolderBookingRow(booking)
        }
        if (state.totalPages > 1) {
            item(key = "pager") {
                PostingPager(state = state, onPage = actions.onPage)
            }
        }
    }
}

/**
 * One posting against the holder's custody.
 *
 * @param booking what to draw.
 */
@Composable
private fun HolderBookingRow(booking: BankHolderBooking) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatAmount(booking.amount.orEmpty()),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = KrtPalette.White,
                )
                booking.type?.let {
                    KrtChip(
                        text = bankTypeLabel(it),
                        tone = if (booking.reversed) KrtChipTone.Danger else KrtChipTone.Muted,
                    )
                }
            }
            // No account column: a holder-to-holder move touches none, and printing an empty one
            // would suggest the custody figure is account-scoped, which is the misreading the
            // handoff corrected.
            val counter = booking.counterHolder ?: booking.counterAccount
            counter?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            booking.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Gray1,
                )
            }
            booking.createdAt?.let {
                // The wire is UTC; the reader is not. `relativeToNow()` is what the ledger beside
                // this screen already shows, and a raw instant is not a time anyone reads.
                Text(
                    text =
                        runCatching { Instant.parse(it) }.getOrNull()?.relativeToNow() ?: it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
    }
}

/**
 * Walks the pages of postings.
 *
 * States the total rather than only offering arrows: a page count with no total reads as "there
 * might be more", which ADR-0104 asks screens not to do.
 *
 * @param state what page is showing and how many there are.
 * @param onPage asks for another page.
 */
@Composable
private fun PostingPager(
    state: BankHolderState,
    onPage: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtOutlineButton(
            text = stringResource(R.string.bank_holder_prev),
            onClick = { onPage(state.page - 1) },
            enabled = state.page > 0,
        )
        Text(
            text =
                stringResource(
                    R.string.bank_holder_page,
                    state.page + 1,
                    state.totalPages,
                    pluralStringResource(
                        R.plurals.bank_holder_postings,
                        state.totalElements.toInt(),
                        state.totalElements,
                    ),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(horizontal = KrtSpacing.sm),
        )
        KrtOutlineButton(
            text = stringResource(R.string.bank_holder_next),
            onClick = { onPage(state.page + 1) },
            enabled = state.page + 1 < state.totalPages,
        )
    }
}

/**
 * The holder detail, bound to its view model.
 *
 * @param viewModel drives it.
 * @param management whether the caller may move custody, which the staff dashboard decides.
 * @param modifier layout modifier.
 */
@Composable
fun BankHolderRoute(
    viewModel: BankHolderViewModel,
    management: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var lockToast by remember { mutableStateOf(false) }
    BankHolderScreen(
        state = state,
        management = management,
        actions =
            BankHolderActions(
                onTransfer = viewModel::onTransfer,
                onPage = viewModel::onPage,
                onLocked = { lockToast = true },
            ),
        modifier = modifier,
    )
    state.draft?.let { draft ->
        BankCustodySheet(
            draft = draft,
            peers = state.peers,
            saving = state.saving,
            error = state.error,
            actions =
                BankCustodyActions(
                    onDraftChanged = viewModel::onDraftChanged,
                    onConfirm = viewModel::onConfirmTransfer,
                    onDismiss = viewModel::onDismissTransfer,
                ),
        )
    }
    if (lockToast) {
        KrtToast(
            title = stringResource(R.string.bank_holder_transfer_title),
            message = stringResource(R.string.bank_staff_grants_locked),
            actionLabel = stringResource(R.string.action_ok),
            onAction = { lockToast = false },
        )
    }
}
