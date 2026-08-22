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
 * The same four states an Einsatz has, and **not** the same spelling: the backend writes
 * `CANCELED` here and `CANCELLED` on a mission. That is a server-side inconsistency this client
 * mirrors rather than tidies — matching on the wire value is what keeps a badge from silently
 * falling to [UNKNOWN], and "fixing" it here would break exactly the case the mapping exists for.
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
 * One Operation as the list needs it.
 *
 * **Deliberately thin.** The design's list row shows "2 Einsätze · 18 Teilnehmer" and a payout
 * chip; the backend's `OperationDto` carries none of it, and its own documentation says why — the
 * bulk endpoints "have no reason to spend the extra count query". Widening them was put to the
 * repository owner and declined (2026-08-22), so the counts live on the detail, where they are
 * loaded anyway. This type carries what the server actually sends rather than nullable fields that
 * would be empty on every row.
 *
 * @property id the Operation's UUID, the one thing a row needs to be openable
 * @property name the Operation's title
 * @property status where it stands
 * @property rawStatus the untranslated server value, kept only so [OperationStatus.UNKNOWN] has
 *   something to show instead of an empty badge
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
     * The design splits the list into "Laufend" and "Abgeschlossen" rather than by date: an
     * Operation has no start time of its own — that lives on its Einsätze — so a date grouping
     * would have to invent one.
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
 * @property operations the rows on this page, in server order
 * @property page the zero-based index of this page
 * @property totalPages how many pages the filter matches
 * @property totalElements how many Operationen the filter matches in total
 */
data class OperationPage(
    val operations: List<Operation>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}
