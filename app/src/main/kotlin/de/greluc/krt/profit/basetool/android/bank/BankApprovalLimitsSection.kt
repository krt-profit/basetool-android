/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import de.greluc.krt.profit.basetool.android.core.data.BankApprovalLimits
import de.greluc.krt.profit.basetool.android.core.data.BankLimitTarget
import de.greluc.krt.profit.basetool.android.core.data.parseTypedDecimal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Test handle for the limits section. */
const val BANK_LIMITS_TAG: String = "bank-approval-limits"

/** Test handle for the „Setzen" sheet. */
const val BANK_LIMIT_SHEET_TAG: String = "bank-approval-limit-sheet"

/** Test handle for the removal confirmation. */
const val BANK_LIMIT_REMOVE_TAG: String = "bank-approval-limit-remove"

/**
 * „Freigabe-Limits" — design ch. 12 artboard 10.
 *
 * Up to the limit a booking may be requested **without a further approval**; above it the account's
 * owner has to release it. A **user** limit beats the tier limit. The chapter's own correction is
 * explicit that this is what the web has — limits per tier, not „approval steps by amount".
 *
 * > **Not a fifth tab.** The artboard makes it a tab of the Verwaltung, beside Grants. It cannot
 * > be one: every limit endpoint is `…/bank/accounts/{id}/approval-limit/…` and the current values
 * > ride on the **account's** settings, so a tab would have to make the member pick an account
 * > before it could show anything. It therefore lives in the account's own settings sheet, next to
 * > the visibility grants it resembles — same scope, same owner, same read. On the design gap list.
 *
 * The two actions are the artboard's words: **„Setzen"** and **„Entfernen"** — not „Speichern",
 * which would promise a form, and not „Löschen", which would promise a deletion.
 *
 * @param limits what the account has.
 * @param busy which limit is being written, or `null`.
 * @param onEdit „Setzen" was tapped on one row.
 * @param onRemove „Entfernen" was tapped on one row.
 */
@Composable
fun BankApprovalLimitsSection(
    limits: BankApprovalLimits,
    busy: BankLimitTarget?,
    onEdit: (BankLimitTarget, String, String?) -> Unit,
    onRemove: (BankLimitTarget, String, String?) -> Unit,
) {
    if (!limits.configurable) {
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        modifier = Modifier.fillMaxWidth().testTag(BANK_LIMITS_TAG),
    ) {
        KrtSectionTitle(text = stringResource(R.string.bank_limits_title))
        Text(
            text = stringResource(R.string.bank_limits_display_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        KrtSectionTitle(text = stringResource(R.string.bank_limits_tiers))
        // The roles first and „Alle Mitglieder" last, as the artboard stacks them: the narrower a
        // tier, the higher it sits, and everyone is the floor everything else is measured against.
        limits.availableRoleCodes.forEach { code ->
            LimitRow(
                label = code.tierLabel(),
                amount = limits.roleLimits[code],
                target = BankLimitTarget.Role(code),
                limits = limits,
                busy = busy,
                onEdit = onEdit,
                onRemove = onRemove,
            )
        }
        if (limits.areaMembersSupported) {
            LimitRow(
                label = stringResource(R.string.bank_limits_area_members),
                amount = limits.areaMembersLimit,
                target = BankLimitTarget.AreaMembers,
                limits = limits,
                busy = busy,
                onEdit = onEdit,
                onRemove = onRemove,
            )
        }
        if (limits.allMembersSupported) {
            LimitRow(
                label = stringResource(R.string.bank_limits_all_members),
                amount = limits.allMembersLimit,
                target = BankLimitTarget.AllMembers,
                limits = limits,
                busy = busy,
                onEdit = onEdit,
                onRemove = onRemove,
            )
        }
        if (limits.userLimits.isNotEmpty()) {
            KrtSectionTitle(text = stringResource(R.string.bank_limits_users))
            limits.userLimits.forEach { row ->
                LimitRow(
                    label = row.displayName,
                    amount = row.limit,
                    target = BankLimitTarget.User(row.userId),
                    limits = limits,
                    busy = busy,
                    onEdit = onEdit,
                    onRemove = onRemove,
                )
            }
        }
    }
}

/**
 * What a role code is called on the limits list.
 *
 * Design ch. 12 ab. 10 names the four tiers in German; the wire sends role codes. An unknown code
 * is shown as it came rather than swallowed — a tier nobody can name is still a tier somebody set
 * a limit on.
 *
 * @receiver the server's role code.
 * @return the label.
 */
@Composable
private fun String.tierLabel(): String =
    when (this) {
        "OFFICER" -> stringResource(R.string.bank_limits_tier_officer)
        "LOGISTICIAN" -> stringResource(R.string.bank_limits_tier_logistician)
        else -> this
    }

/**
 * One tier or one member, with what applies to them.
 *
 * @param label what to call it.
 * @param amount what it stands at, or `null` when no limit is set.
 * @param target which limit the two actions address.
 * @param limits the whole set, for the fallback the removal names.
 * @param busy which limit is being written.
 * @param onEdit „Setzen".
 * @param onRemove „Entfernen".
 */
@Composable
@Suppress("LongParameterList")
private fun LimitRow(
    label: String,
    amount: String?,
    target: BankLimitTarget,
    limits: BankApprovalLimits,
    busy: BankLimitTarget?,
    onEdit: (BankLimitTarget, String, String?) -> Unit,
    onRemove: (BankLimitTarget, String, String?) -> Unit,
) {
    val writing = busy == target
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = amount ?: stringResource(R.string.bank_limits_none),
                style = MaterialTheme.typography.bodyMedium,
                color = if (amount == null) KrtPalette.TextMuted else KrtPalette.White,
            )
        }
        if (limits.canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.bank_limits_set),
                    onClick = { onEdit(target, label, amount) },
                    enabled = !writing,
                )
                if (amount != null) {
                    KrtQuietDangerButton(
                        text = stringResource(R.string.bank_limits_remove),
                        onClick = { onRemove(target, label, limits.fallbackFor(target)) },
                        enabled = !writing,
                    )
                }
            }
        }
        KrtHairlineRule()
    }
}

/**
 * What applies once one limit is gone.
 *
 * A user falls back to the tiers, and a tier to „Alle Mitglieder"; the artboard's removal
 * confirmation names it, because removing a limit is not the same as setting it to zero.
 *
 * @receiver the whole set.
 * @param target the limit being removed.
 * @return the figure that then applies, or `null` when none does.
 */
private fun BankApprovalLimits.fallbackFor(target: BankLimitTarget): String? =
    when (target) {
        // Nothing sits under „everyone", so its removal leaves no limit at all — which the
        // confirmation says in its own words rather than naming a figure.
        BankLimitTarget.AllMembers -> null

        else -> allMembersLimit
    }

/**
 * The „Setzen" sheet.
 *
 * @param draft which limit and what has been typed.
 * @param saving whether the write is in flight.
 * @param onAmount the field changed.
 * @param onConfirm „Setzen".
 * @param onDismiss the sheet was closed.
 */
@Composable
fun BankLimitSheet(
    draft: BankLimitDraft,
    saving: Boolean,
    onAmount: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = draft.label,
        modifier = Modifier.testTag(BANK_LIMIT_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            KrtTextField(
                value = draft.amount,
                onValueChange = onAmount,
                label = stringResource(R.string.bank_limits_field),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.bank_limits_set_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtCtaButton(
                text = stringResource(R.string.bank_limits_set),
                onClick = onConfirm,
                enabled = !saving && parseTypedDecimal(draft.amount) != null,
                modifier = Modifier.fillMaxWidth(),
            )
            KrtGhostButton(
                text = stringResource(R.string.personal_inventory_cancel),
                onClick = onDismiss,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The „Entfernen" confirmation, which names what applies afterwards.
 *
 * @param draft which limit, and the fallback.
 * @param saving whether the write is in flight.
 * @param onConfirm it was accepted.
 * @param onDismiss it was dismissed.
 */
@Composable
fun BankLimitRemoveModal(
    draft: BankLimitDraft,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.bank_limits_remove_title),
        confirmText = stringResource(R.string.bank_limits_remove),
        onConfirm = { if (!saving) onConfirm() },
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        modifier = Modifier.testTag(BANK_LIMIT_REMOVE_TAG),
    ) {
        Text(
            text =
                draft.fallback?.let { stringResource(R.string.bank_limits_remove_body, draft.label, it) }
                    ?: stringResource(R.string.bank_limits_remove_body_none, draft.label),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}
