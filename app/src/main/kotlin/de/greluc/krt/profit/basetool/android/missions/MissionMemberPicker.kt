/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.MissionPeopleSource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Log tag for the member lookup. */
private const val LOG_TAG = "MissionMemberPicker"

/** How long typing must pause before a search goes out. */
private const val DEBOUNCE_MS = 300L

/** The page size the member picker requests from the server (ADR-0104). */
const val MEMBER_PICKER_CAP: Int = 50

/**
 * Which of the three member-shaped writes a pick is for.
 *
 * They share one picker because they ask one question — *which member?* — and differ only in what
 * happens next. One open picker at a time is also what keeps the screen honest: three comboboxes
 * stacked on the Verwaltung tab would each be a field the member has to work out the purpose of.
 */
enum class MissionMemberTarget {
    /** „Einsatzleitung" — who leads the Einsatz. Section-locked, so it echoes `partyLeadVersion`. */
    PARTY_LEAD,

    /** Grants somebody the right to manage this Einsatz. */
    MANAGER,

    /** Puts a member on the roster who has not signed themselves up. */
    PARTICIPANT,
}

/**
 * What the member picker is showing right now.
 *
 * @property target what the pick is for, or `null` while the picker is closed.
 * @property query what has been typed.
 * @property options what the server last answered, at most [MEMBER_PICKER_CAP] of them.
 * @property moreOptions whether the roster holds members this page does not carry.
 * @property searching whether a lookup is in flight.
 */
data class MissionMemberPickerState(
    val target: MissionMemberTarget? = null,
    val query: String = "",
    val options: List<MemberOption> = emptyList(),
    val moreOptions: Boolean = false,
    val searching: Boolean = false,
) {
    /** Whether the picker is on screen. */
    val open: Boolean
        get() = target != null
}

/**
 * The one member picker (chapter 12's remote combobox) behind the party lead, the managers and
 * „Teilnehmer hinzufügen".
 *
 * The search is debounced and single-flight: a new keystroke cancels the request in flight.
 *
 * @property source where the lookup goes.
 * @property scope the view model's scope.
 * @property read the picker as it stands.
 * @property write reports it back.
 * @property onPicked a member was chosen; the caller runs the write the target asks for.
 */
class MissionMemberPicker(
    private val source: MissionPeopleSource,
    private val scope: CoroutineScope,
    private val read: () -> MissionMemberPickerState,
    private val write: (MissionMemberPickerState) -> Unit,
    private val onPicked: (MissionMemberTarget, MemberOption) -> Unit,
) {
    /** The lookup in flight, cancelled by the next keystroke. */
    private var search: Job? = null

    /**
     * Opens the picker for one of the three writes, and primes it with the first page.
     *
     * @param target what the pick is for.
     */
    fun open(target: MissionMemberTarget) {
        write(MissionMemberPickerState(target = target))
        lookup("")
    }

    /** Closes it, discarding what was typed and cancelling any lookup in flight. */
    fun dismiss() {
        search?.cancel()
        search = null
        write(MissionMemberPickerState())
    }

    /**
     * Records what was typed and schedules the lookup.
     *
     * @param query the new text.
     */
    fun query(query: String) {
        val current = read()
        if (!current.open) {
            return
        }
        write(current.copy(query = query))
        lookup(query)
    }

    /**
     * A member was chosen.
     *
     * @param option who.
     */
    fun pick(option: MemberOption) {
        val target = read().target ?: return
        dismiss()
        onPicked(target, option)
    }

    /**
     * Runs the debounced, single-flight lookup.
     *
     * @param query what to search for.
     */
    private fun lookup(query: String) {
        search?.cancel()
        search =
            scope.launch {
                delay(DEBOUNCE_MS)
                write(read().copy(searching = true))
                when (val result = source.members(query)) {
                    is ApiResult.Success -> {
                        val current = read()
                        if (current.open) {
                            write(
                                current.copy(
                                    options = result.value.rows,
                                    moreOptions = result.value.more,
                                    searching = false,
                                ),
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the member lookup failed: ${result.error}" }
                        write(read().copy(options = emptyList(), searching = false))
                    }
                }
            }
    }
}
