/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * What an Operation's detail head states, beyond the row the list already had.
 *
 * @property id the Operation's id
 * @property name its title
 * @property status where it stands
 * @property rawStatus the untranslated server value, for [OperationStatus.UNKNOWN]
 * @property description the free text, or `null`
 * @property version the optimistic lock the edit echoes
 * @property payoutPreliminary whether the payout figures may still rebalance because some Einsatz
 *   has no actual end time yet; `null` when the server did not compute it
 */
data class OperationDetail(
    val id: String,
    val name: String,
    val status: OperationStatus,
    val rawStatus: String?,
    val description: String?,
    val payoutPreliminary: Boolean?,
    val version: Long? = null,
)

/**
 * One Einsatz's contribution to its Operation's result.
 *
 * @property missionId the Einsatz's id, so the row can open it
 * @property missionName its title
 * @property total the net result as the server rendered it, unrounded and unformatted
 */
data class OperationMissionResult(
    val missionId: String?,
    val missionName: String,
    val total: String,
)

/**
 * The Operation's Finanz-Rollup: the net result only, with no income/expense split.
 *
 * @property total the Operation's net result, as the server rendered it
 * @property truncated whether the per-Einsatz list is capped (ADR-0104)
 * @property missions the per-Einsatz results
 */
data class OperationRollup(
    val total: String?,
    val truncated: Boolean,
    val missions: List<OperationMissionResult>,
)

/**
 * What one participant is owed, or was paid.
 *
 * @property participantId the participant key, unique within the Operation
 * @property participantName the display name
 * @property donating whether they waived their share in favour of the org treasury
 * @property share the share of the Operation's result, as the server rendered it; zero for a
 *   donating participant
 * @property donated the amount contributed to the org, for a donating participant
 * @property payout what is actually transferred: reimbursement plus share minus the in-game
 *   transfer fee, rounded by the server to whole aUEC
 * @property paidOut whether a manager has marked this participant as paid
 * @property participationPercentage the share of the Operation this participant's attendance
 *   earned, as a percentage
 * @property personalExpenses the participant's own outlay, reimbursed inside [payout]
 * @property transferFee the in-game fee already deducted from [payout]
 * @property paidOutAt when a manager marked this paid, ISO-8601 UTC; `null` while it is open
 * @property paidOutByName who marked it paid; a member name, shown but never logged
 */
data class OperationPayout(
    val participantId: String?,
    val participantName: String,
    val donating: Boolean,
    val share: String?,
    val donated: String?,
    val payout: String?,
    val paidOut: Boolean,
    val participationPercentage: Double? = null,
    val personalExpenses: String? = null,
    val transferFee: String? = null,
    val paidOutAt: String? = null,
    val paidOutByName: String? = null,
) {
    /**
     * What attendance earned this participant, whether paid out or donated.
     */
    val earnedShare: String? get() = if (donating) donated else share
}

/**
 * The Auszahlungen tab.
 *
 * @property totalDonations everything donating participants contributed, shown centrally because
 *   the server never redistributes it to the others
 * @property rows one entry per participant across every Einsatz of the Operation
 */
data class OperationPayouts(
    val totalDonations: String?,
    val rows: List<OperationPayout>,
) {
    /**
     * How many people took part, counted from the payout rows so a member in two Einsätze counts once.
     */
    val participants: Int get() = rows.size

    /**
     * The smallest and the largest share earned, as the server wrote them; equal when attendance was
     * equal.
     *
     * `null` when there are no participants or any of them has no share figure.
     */
    val shareRange: Pair<String, String>?
        get() {
            val amounts = rows.mapNotNull { it.earnedShare?.toBigDecimalOrNull() }
            if (amounts.isEmpty() || amounts.size != rows.size) {
                return null
            }
            return amounts.min().toPlainString() to amounts.max().toPlainString()
        }
}
