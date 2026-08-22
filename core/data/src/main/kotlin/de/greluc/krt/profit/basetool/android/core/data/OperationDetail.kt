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
 * @property payoutPreliminary whether the payout figures may still rebalance because some Einsatz
 *   of this Operation has no actual end time yet; `null` when the server did not compute it, which
 *   the screen treats as "do not claim either way" rather than as "final"
 */
data class OperationDetail(
    val id: String,
    val name: String,
    val status: OperationStatus,
    val rawStatus: String?,
    val description: String?,
    val payoutPreliminary: Boolean?,
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
 * The Operation's Finanz-Rollup.
 *
 * **Net only, no income/expense split.** The server's roll-up carries one figure per Einsatz and
 * one for the Operation; the split the design mock shows exists nowhere in the API, and deriving it
 * would mean summing every entry of every Einsatz on the device — a per-Einsatz round trip and a
 * money figure this client computed. The web page shows the same net-plus-donations pair.
 *
 * @property total the Operation's net result, as the server rendered it
 * @property truncated whether the per-Einsatz list is capped — surfaced, never swallowed
 *   (main repo ADR-0104)
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
 * @property share the share of the Operation's result, as the server rendered it
 * @property donated the amount contributed to the org, for a donating participant
 * @property payout what is actually transferred: reimbursement plus share minus the in-game
 *   transfer fee, already rounded by the server to whole aUEC
 * @property paidOut whether a manager has marked this participant as paid
 */
data class OperationPayout(
    val participantId: String?,
    val participantName: String,
    val donating: Boolean,
    val share: String?,
    val donated: String?,
    val payout: String?,
    val paidOut: Boolean,
)

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
     * How many people took part, which is what the head states and what the per-head share divides
     * by.
     *
     * Read from the payout rows rather than counted across the Einsätze: a member who took part in
     * two Einsätze of the same Operation is one participant here, and summing the Einsätze's own
     * counts would count them twice.
     */
    val participants: Int get() = rows.size
}
