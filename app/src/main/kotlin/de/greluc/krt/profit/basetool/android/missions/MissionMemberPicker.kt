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

/**
 * What the picker asks the server for.
 *
 * The notice used to repeat this number on **every** search — „3 von höchstens 25 Treffern" —
 * which states a cap that is not biting and says nothing about whether one is. The picker now
 * reports the server's own `totalElements` instead, so the line appears when members are actually
 * being withheld and stays away when they are not (ADR-0104).
 */
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
 * The one member picker behind the party lead, the managers and „Teilnehmer hinzufügen".
 *
 * > **This closes round 10's § 10e.** The question was whether chapter 12's remote combobox is the
 * > right control for naming a member, and it is: it is the drawn control for exactly this — type,
 * > the list narrows, a muted notice states what the cap hid. Nothing about a member list argues
 * > for a different shape. What is **not** drawn is where the three entry points sit on the
 * > Verwaltung tab, which round 11 asks for.
 *
 * The search is debounced and single-flight: a new keystroke cancels the request in flight, so a
 * slow answer to „Ma" can never land on top of a fresh answer to „Marc".
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
                        // Only if the picker is still open for the same target. A pick or a dismiss
                        // during the round trip must not repopulate a closed picker.
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
                        // An empty list rather than a refusal banner: the picker's own notice says
                        // how many matched, and „0" reads correctly for a lookup that could not
                        // run. The write the member is heading for reports its own failure.
                        write(read().copy(options = emptyList(), searching = false))
                    }
                }
            }
    }
}
