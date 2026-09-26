/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * Where an Operation stands in its lifecycle.
 *
 * Mirrors the server's spelling `CANCELED` here (a mission uses `CANCELLED`), so the wire value
 * maps instead of falling to [UNKNOWN].
 */
enum class OperationStatus {
    /** Scheduled, no Einsatz running yet. */
    PLANNED,

    /** Running now. */
    ACTIVE,

    /** Finished. */
    COMPLETED,

    /** Called off. */
    CANCELED,

    /**
     * A status this build does not know.
     *
     * Rendered as the raw server value rather than hidden, for the reason [MissionStatus.UNKNOWN]
     * gives: an untranslated word is a smaller failure than no badge at all.
     */
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps a server status onto the enum.
         *
         * @param raw the wire value, in any case, possibly `null`.
         * @return the matching constant, or [UNKNOWN] for anything else including `null`.
         */
        fun from(raw: String?): OperationStatus =
            entries.firstOrNull { it != UNKNOWN && it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * One Operation as the list needs it; carries only what `OperationDto` sends, with counts left to
 * the detail.
 *
 * @property id the Operation's UUID, required for a row to be openable
 * @property name the Operation's title
 * @property status where it stands
 * @property rawStatus the untranslated server value, shown for [OperationStatus.UNKNOWN]
 * @property description the free-text description, or `null`
 */
data class Operation(
    val id: String,
    val name: String,
    val status: OperationStatus,
    val rawStatus: String?,
    val description: String?,
) {
    /**
     * Whether this Operation is still running, which is what the list groups by.
     *
     * @return `true` for a planned or active Operation.
     */
    val isRunning: Boolean
        get() = status == OperationStatus.PLANNED || status == OperationStatus.ACTIVE
}

/**
 * One page of Operationen, plus what the caller needs to ask for the next one.
 *
 * `totalElements` is carried for the reason [MissionPage] carries it: a paginated list that cannot
 * say how much it is not showing is the silent truncation the main repo's ADR-0104 forbids.
 *
 * [Page.rows] holds the rows on this page, in server order.
 */
typealias OperationPage = Page<Operation>
