/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import de.greluc.krt.profit.basetool.android.R

/** The suffix that marks a parameter as a code with a localized word. */
private const val CODE_SUFFIX = "Code"

/** The localized word of every coded parameter value the backend raises, keyed `name.VALUE`. */
internal val VALUE_WORDS: Map<String, Int> =
    mapOf(
        "action.DISCARDED" to R.string.notifications_value_action_discarded,
        "action.SOLD" to R.string.notifications_value_action_sold,
        "canDeposit.NO" to R.string.notifications_value_can_deposit_no,
        "canDeposit.YES" to R.string.notifications_value_can_deposit_yes,
        "canTransfer.NO" to R.string.notifications_value_can_transfer_no,
        "canTransfer.YES" to R.string.notifications_value_can_transfer_yes,
        "canWithdraw.NO" to R.string.notifications_value_can_withdraw_no,
        "canWithdraw.YES" to R.string.notifications_value_can_withdraw_yes,
        "change.ADDED" to R.string.notifications_value_change_added,
        "change.CANCELED" to R.string.notifications_value_change_canceled,
        "change.DELETED" to R.string.notifications_value_change_deleted,
        "change.IMPORTED" to R.string.notifications_value_change_imported,
        "change.STORED" to R.string.notifications_value_change_stored,
        "change.STORED_TO_YOU" to R.string.notifications_value_change_stored_to_you,
        "change.UPDATED" to R.string.notifications_value_change_updated,
        "debit.REVERSAL" to R.string.notifications_value_debit_reversal,
        "debit.TRANSFER" to R.string.notifications_value_debit_transfer,
        "debit.WITHDRAWAL" to R.string.notifications_value_debit_withdrawal,
        "grantChange.CREATED" to R.string.notifications_value_grant_change_created,
        "grantChange.UPDATED" to R.string.notifications_value_grant_change_updated,
        "mismatch.MISSING_OFFICER" to R.string.notifications_value_mismatch_missing_officer,
        "mismatch.SURPLUS_OFFICER" to R.string.notifications_value_mismatch_surplus_officer,
        "payout.DONATE" to R.string.notifications_value_payout_donate,
        "payout.PAYOUT" to R.string.notifications_value_payout_payout,
        "rank.BEREICHSKOORDINATOR" to R.string.notifications_value_rank_bereichskoordinator,
        "rank.BEREICHSLEITER" to R.string.notifications_value_rank_bereichsleiter,
        "rank.BEREICHSOPERATOR" to R.string.notifications_value_rank_bereichsoperator,
        "rank.ENSIGN" to R.string.notifications_value_rank_ensign,
        "rank.KOMMANDOLEITER" to R.string.notifications_value_rank_kommandoleiter,
        "rank.OL_MEMBER" to R.string.notifications_value_rank_ol_member,
        "rank.SK_LEAD" to R.string.notifications_value_rank_sk_lead,
        "rank.STAFFELLEITER" to R.string.notifications_value_rank_staffelleiter,
        "rank.STELLV_KOMMANDOLEITER" to R.string.notifications_value_rank_stellv_kommandoleiter,
        "reason.DE_ESCALATED" to R.string.notifications_value_reason_de_escalated,
        "reason.ORDER_CHANGED" to R.string.notifications_value_reason_order_changed,
        "reason.STOCK_GONE" to R.string.notifications_value_reason_stock_gone,
        "reason.WITHDRAWN" to R.string.notifications_value_reason_withdrawn,
        "reason.disabled" to R.string.notifications_value_reason_disabled,
        "reason.removed" to R.string.notifications_value_reason_removed,
        "reason.role_lost" to R.string.notifications_value_reason_role_lost,
        "role.MANAGER" to R.string.notifications_value_role_manager,
        "role.OWNER" to R.string.notifications_value_role_owner,
        "role.PARTY_LEAD" to R.string.notifications_value_role_party_lead,
        "role.UNIT_RESPONSIBLE" to R.string.notifications_value_role_unit_responsible,
        "seat.APPOINTED" to R.string.notifications_value_seat_appointed,
        "seat.REMOVED" to R.string.notifications_value_seat_removed,
        "status.COMPLETED" to R.string.notifications_value_status_completed,
        "status.DELETED" to R.string.notifications_value_status_deleted,
        "status.REJECTED" to R.string.notifications_value_status_rejected,
        "vacancy.NO" to R.string.notifications_value_vacancy_no,
        "vacancy.YES" to R.string.notifications_value_vacancy_yes,
    )

/**
 * Adds the word of every coded parameter beside its code.
 *
 * A parameter named `<name>Code` gives `<name>` the localized word of its value when [words] has one
 * and the raw value otherwise; a parameter the server already sent under that name is kept.
 *
 * @param params the notification's parameters.
 * @param words the resolved words keyed `name.VALUE`.
 * @return the parameters with the words added.
 */
internal fun withValueWords(
    params: Map<String, String>,
    words: Map<String, String>,
): Map<String, String> {
    if (params.keys.none { it.endsWith(CODE_SUFFIX) && it.length > CODE_SUFFIX.length }) {
        return params
    }
    val out = LinkedHashMap(params)
    params.forEach { (key, value) ->
        if (key.endsWith(CODE_SUFFIX) && key.length > CODE_SUFFIX.length) {
            val name = key.removeSuffix(CODE_SUFFIX)
            if (name !in out) {
                out[name] = words["$name.$value"] ?: value
            }
        }
    }
    return out
}

/**
 * Resolves every coded-parameter word in the current language.
 *
 * @param context the context whose resources and locale word them.
 * @return the words keyed `name.VALUE`.
 */
internal fun valueWords(
    context: Context,
): Map<String, String> = VALUE_WORDS.mapValues { (_, id) -> context.getString(id) }

/**
 * The coded-parameter words, resolved once per configuration for a composable.
 *
 * @return the words keyed `name.VALUE`.
 */
@Composable
internal fun rememberValueWords(): Map<String, String> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { valueWords(context) }
}
