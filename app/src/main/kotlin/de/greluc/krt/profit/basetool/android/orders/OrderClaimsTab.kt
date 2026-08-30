/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.ClaimBucket
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtAssocAdd
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Zusagen list. */
const val ORDER_CLAIMS_TAG: String = "order-claims"

/** Test handle for the claim sheet's CTA. */
const val ORDER_CLAIM_SUBMIT_TAG: String = "order-claim-submit"

/**
 * Zusagen — which Staffel has signed up for how much of each material (design ch. 10 artboard 13).
 *
 * > **Only on a Spezialkommando order.** The server refuses a claim on anything else, so the tab
 * > is not offered on a Staffel's own order at all.
 *
 * A claim is an **intention**, never a booking: nothing moves in the Lager, delivery is the
 * Übergabe, and withdrawing one therefore needs no confirmation.
 *
 * @param state the buckets and the pledges.
 * @param actions what the rows report.
 */
internal fun LazyListScope.claimsTab(
    state: ClaimsState,
    actions: ClaimActions,
) {
    val notice =
        when {
            state.loading -> null
            state.error != null -> R.string.order_claims_error
            state.buckets.isEmpty() -> R.string.order_claims_empty
            else -> null
        }
    if (state.loading || notice != null) {
        item(key = "claims-notice") {
            if (notice == null) KrtSpinner() else Body(text = stringResource(notice))
        }
        return
    }
    items(state.buckets, key = { it.materialId + "-" + it.quality.name }) { bucket ->
        Column(modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.xs)) {
            ClaimBucketRow(bucket = bucket, state = state, actions = actions)
        }
    }
    if (state.units.isEmpty()) {
        item(key = "claims-no-unit") {
            // Not a permission toast: the caller may simply belong to no profit-eligible Staffel,
            // which is a fact about their memberships rather than a missing grant.
            Body(text = stringResource(R.string.order_claims_no_unit))
        }
    }
}

/**
 * One material demand: what it needs, who signed up, and what is still open.
 *
 * @param bucket the demand.
 * @param state the tab, for the caller's own Staffel.
 * @param actions what the row reports.
 */
@Composable
private fun ClaimBucketRow(
    bucket: ClaimBucket,
    state: ClaimsState,
    actions: ClaimActions,
) {
    val mine = state.defaultUnit?.let { bucket.claimOf(it.id) }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bucket.materialName,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.order_claims_progress,
                        bucket.claimed?.toPlainString().orEmpty(),
                        bucket.required?.toPlainString().orEmpty(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (bucket.claims.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bucket.claims.forEach { claim ->
                    // The caller's own pledge carries the brand tone; another Staffel's is a data
                    // chip and is not tappable, because there is nothing here to do with it.
                    KrtChip(
                        text =
                            stringResource(
                                R.string.order_claims_chip,
                                claim.orgUnitName,
                                claim.amount?.toPlainString().orEmpty(),
                            ),
                        tone = if (claim.id == mine?.id) KrtChipTone.Primary else KrtChipTone.Data,
                    )
                }
            }
        }
        Body(
            text =
                stringResource(
                    R.string.order_claims_open,
                    bucket.open?.toPlainString().orEmpty(),
                ),
        )
        state.defaultUnit?.let { unit ->
            KrtAssocAdd(
                text =
                    stringResource(
                        if (mine == null) R.string.order_claims_add else R.string.order_claims_change,
                    ),
                onClick = { actions.onOpen(bucket, unit) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
        }
    }
}

/**
 * The sheet that sets, changes and withdraws one Staffel's pledge.
 *
 * One sheet for all three, which is what the artboard draws: setting and changing are the same
 * upsert on the wire, and withdrawing is the quiet danger action inside it rather than a screen of
 * its own.
 *
 * @param actions the draft and what it reports.
 */
@Composable
fun OrderClaimSheet(actions: ClaimActions) {
    val draft = actions.draft ?: return
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.order_claims_sheet_title, draft.materialName),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            Body(
                text =
                    stringResource(
                        R.string.order_claims_sheet_open,
                        draft.open?.toPlainString().orEmpty(),
                        draft.required?.toPlainString().orEmpty(),
                    ),
            )
            KrtTextField(
                value = draft.amount,
                onValueChange = { v -> actions.onChange { it.copy(amount = v) } },
                label = stringResource(R.string.order_claims_amount, draft.orgUnitName),
                enabled = !draft.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                tabularFigures = true,
            )
            // The sentence that keeps a pledge from being read as a delivery.
            KrtHint(explanation = stringResource(R.string.order_claims_intent_hint))
            draft.error?.let { ClaimError(error = it) }
            KrtCtaButton(
                text = stringResource(R.string.order_claims_cta),
                onClick = actions.onSubmit,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(ORDER_CLAIM_SUBMIT_TAG),
                enabled = draft.submittable,
            )
            if (draft.claimId != null) {
                // No confirmation: a claim books nothing, so this is undone by pledging again.
                KrtQuietDangerButton(
                    text = stringResource(R.string.order_claims_withdraw),
                    onClick = actions.onWithdraw,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !draft.saving,
                    iconRes = DesignR.drawable.ic_krt_trash,
                )
            }
        }
    }
}

/**
 * What the last write returned.
 *
 * A `400` on this edge is almost always the overclaim guard — the sum across Staffeln may not
 * exceed what the bucket needs — so it says that rather than „ungültige Eingabe".
 *
 * @param error the refusal.
 */
@Composable
private fun ClaimError(error: ApiError) {
    KrtFieldError(
        text =
            stringResource(
                when (error) {
                    is ApiError.Forbidden -> R.string.order_claims_not_allowed
                    is ApiError.Validation -> R.string.order_claims_overclaim
                    else -> R.string.write_failed
                },
            ),
    )
}

/**
 * What the Zusagen report back.
 *
 * @property draft the open sheet, or `null`.
 * @property onOpen set or change one Staffel's pledge on a bucket.
 * @property onChange a field changed.
 * @property onSubmit send it.
 * @property onWithdraw take the pledge back.
 * @property onDismiss close the sheet.
 */
data class ClaimActions(
    val draft: ClaimDraft?,
    val onOpen: (ClaimBucket, de.greluc.krt.profit.basetool.android.core.data.OrgUnit) -> Unit,
    val onChange: ((ClaimDraft) -> ClaimDraft) -> Unit,
    val onSubmit: () -> Unit,
    val onWithdraw: () -> Unit,
    val onDismiss: () -> Unit,
)
