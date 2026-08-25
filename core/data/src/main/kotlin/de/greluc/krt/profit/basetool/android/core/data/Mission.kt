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
 * The backend stores this as a free-form `String` rather than an enum column, and the four
 * constants below are the whole set the web app writes and translates. It is mapped here rather
 * than consumed as text so a screen can branch on it — and so a value this build has never heard of
 * lands on [UNKNOWN] instead of being rendered raw at a member.
 *
 * The design spec's list mock shows a fifth badge, "Briefing", between Geplant and Aktiv. It has no
 * counterpart anywhere in the backend or the web app — `mission.status.*` carries exactly four keys
 * and `briefing` appears only as a section heading ("Auftrag"). It is therefore treated as mock
 * copy, not as a state to invent; if a briefing phase is ever wanted it needs a backend change
 * first, not a client-side guess.
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
 * One Einsatz as the list needs it.
 *
 * A projection of the backend's `MissionListDto`, not a mirror of it: the fields the list screen
 * does not draw are dropped here rather than carried and ignored, so the screen's needs stay
 * readable from the model.
 *
 * **Times are instants, never pre-formatted strings.** The wire is UTC; the device zone decides
 * what a member reads, and the relative label ("in 2 Std.") has to be recomputed as the clock
 * moves. Formatting therefore belongs to the screen, and this type deliberately makes it
 * impossible to bake in.
 *
 * @property id the mission's UUID, the one thing that must be present for a row to be openable
 * @property name the Einsatz's title
 * @property status where it stands
 * @property rawStatus the untranslated server value, kept only so [MissionStatus.UNKNOWN] has
 *   something to show instead of an empty badge
 * @property meetingTime when the squadron gathers in Teamspeak ("TS 20:30"), or `null`
 * @property plannedStartTime the scheduled server-join time, or `null`; also the list's sort key
 * @property actualStartTime when it actually started, or `null` — what "seit 18:10" reads from
 * @property plannedEndTime the scheduled end, or `null`
 * @property isInternal squadron-internal, so not offered to outsiders
 * @property operationName the umbrella Operation's name, or `null` when it stands alone
 * @property orgUnitName the owning unit's display name, or `null`
 * @property orgUnitShorthand the owning unit's short form, which is what the badge draws
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
     * How many members have signed up.
     *
     * `null` on every list read: `MissionListDto` carries no participant count, so the design's
     * "{n} angemeldet" has nothing behind it there. Not faked — see the parity audit.
     */
    val registeredCount: Int? = null,
) {
    /**
     * The instant this Einsatz is grouped and sorted by.
     *
     * The list groups by day, and the day a member means is the one it starts on. A running Einsatz
     * whose actual start differs from the plan belongs under the day it actually began.
     *
     * @return the actual start when there is one, else the planned start, else the meeting time —
     *   or `null` when the server dated it not at all, which the screen groups separately rather
     *   than dropping.
     */
    val groupingTime: Instant? get() = actualStartTime ?: plannedStartTime ?: meetingTime
}

/**
 * One page of Einsätze, plus what the caller needs to ask for the next one.
 *
 * `totalElements` is carried because the screen states the count, and because a paginated list that
 * cannot say how much it is not showing is exactly the silent-truncation failure the main repo's
 * ADR-0104 forbids.
 *
 * @property missions the rows on this page, in server order
 * @property page the zero-based index of this page
 * @property totalPages how many pages the filter matches
 * @property totalElements how many Einsätze the filter matches in total
 */
data class MissionPage(
    val missions: List<Mission>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}
