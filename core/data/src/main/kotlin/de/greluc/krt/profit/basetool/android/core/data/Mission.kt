/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import java.time.Instant

/**
 * Where an Einsatz stands in its lifecycle.
 *
 * The backend stores a free-form string; a value this build does not know maps to [UNKNOWN].
 */
enum class MissionStatus {
    /** Scheduled, not yet running. */
    PLANNED,

    /** Running now. */
    ACTIVE,

    /** Finished. */
    COMPLETED,

    /** Called off. */
    CANCELLED,

    /**
     * A status this build does not know.
     *
     * Rendered as the raw server value rather than hidden: a member seeing an untranslated word is
     * a smaller failure than a member seeing no badge at all and assuming the Einsatz is fine.
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
        fun from(raw: String?): MissionStatus =
            entries.firstOrNull { it != UNKNOWN && it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * One Einsatz as the list needs it, projected from `MissionListDto`.
 *
 * Times are UTC instants; formatting belongs to the screen.
 *
 * @property id the mission's UUID, required for a row to be openable
 * @property name the Einsatz's title
 * @property status where it stands
 * @property rawStatus the untranslated server value, shown for [MissionStatus.UNKNOWN]
 * @property meetingTime when the squadron gathers in Teamspeak, or `null`
 * @property plannedStartTime the scheduled server-join time, or `null`; also the list's sort key
 * @property actualStartTime when it actually started, or `null`
 * @property plannedEndTime the scheduled end, or `null`
 * @property isInternal squadron-internal, so not offered to outsiders
 * @property operationName the umbrella Operation's name, or `null` when it stands alone
 * @property orgUnitName the owning unit's display name, or `null`
 * @property orgUnitShorthand the owning unit's short form, which the badge draws
 * @property meetingPoint the in-fiction gathering location, or `null`
 */
data class Mission(
    val id: String,
    val name: String,
    val status: MissionStatus,
    val rawStatus: String?,
    val meetingTime: Instant?,
    val plannedStartTime: Instant?,
    val actualStartTime: Instant?,
    val plannedEndTime: Instant?,
    val isInternal: Boolean,
    val operationName: String?,
    val orgUnitName: String?,
    val orgUnitShorthand: String?,
    val meetingPoint: String?,
    /**
     * One-line briefing, drawn beneath the name on the dashboard band.
     *
     * Defaulted because it is genuinely optional on the wire and absent for an outsider read
     * (ADR-0034) — a row without one is a row, not a defect.
     */
    val description: String? = null,
    /**
     * How many members have signed up, or `null` where the server omitted it.
     */
    val registeredCount: Int? = null,
) {
    /**
     * The instant this Einsatz is grouped and sorted by.
     *
     * @return the actual start, else the planned start, else the meeting time, or `null` when the
     *   server sent none.
     */
    val groupingTime: Instant? get() = actualStartTime ?: plannedStartTime ?: meetingTime
}

/**
 * One page of Einsätze with its total count (ADR-0104).
 *
 * [Page.rows] holds the rows on this page, in server order.
 */
typealias MissionPage = Page<Mission>
