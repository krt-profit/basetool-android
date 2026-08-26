/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orgunit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.auth.ActiveOrgUnitStore
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the shell shows for the active org unit, and what the switcher offers.
 *
 * @property units the member's org units, in the order the server returned them.
 * @property activeId the unit currently pinned, or `null` while none is known or when [allChosen].
 * @property allChosen whether the member deliberately chose to act across all their units at once
 *   (design ch. 02, artboard 7). Distinct from "no unit resolved yet", which renders no badge.
 * @property loaded whether the list has been read at least once — `false` is "not asked yet",
 *   which is a different thing from "asked and there are none" and must not render the same way.
 */
data class OrgUnitState(
    val units: List<OrgUnit> = emptyList(),
    val activeId: String? = null,
    val allChosen: Boolean = false,
    val loaded: Boolean = false,
) {
    /** The active unit, or `null` when none is pinned or the pin names a unit that is gone. */
    val active: OrgUnit? get() = units.firstOrNull { it.id == activeId }

    /**
     * Whether a switcher is worth showing.
     *
     * One unit is not a choice, and a control that cannot change anything is noise — the same rule
     * the web sidebar applies.
     */
    val switchable: Boolean get() = units.size > 1
}

/**
 * Decides which org unit the app acts in, and remembers it.
 *
 * The rule has three steps and each exists for a case that happens:
 *
 * 1. **A pin on this device wins.** It is the member's own choice and it survives restarts.
 * 2. **Otherwise the server decides** (`GET /api/v1/me/active-org-unit`) — a member who has never
 *    touched the switcher gets the same scope the web app would give them, rather than whichever
 *    unit happens to sort first.
 * 3. **Otherwise the first membership**, so a member with exactly one unit never sees an empty
 *    badge for a scope that was never in doubt.
 *
 * „Alle Org-Einheiten" sits outside those three: it sends no header, which the backend answers with
 * the union of the member's own units. It has to be *remembered as a choice* rather than stored as
 * the absence of one, or step 2 would quietly resolve a single unit again on the next cold start.
 *
 * A pin naming a unit the member no longer belongs to is dropped rather than kept: an administrator
 * can remove a membership, and a stale pin would send `X-Active-Org-Unit-Id` for a unit the backend
 * will refuse, which reads as "everything is empty" rather than as "you are no longer in that unit".
 *
 * Failures are not fatal here. The switcher is part of the frame around every screen, so a failed
 * read leaves the previous state and logs; blocking the shell on it would turn one failed request
 * into an unusable app.
 *
 * @property source reads the memberships and the server's default
 * @property store the pin, shared with the request interceptor
 */
class OrgUnitViewModel(
    private val source: OrgUnitSource,
    private val store: ActiveOrgUnitStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrgUnitState())

    /** The switcher's state. */
    val state: StateFlow<OrgUnitState> = mutableState.asStateFlow()

    /**
     * Loads the memberships and settles which unit is active.
     *
     * Idempotent by intent rather than by guard: calling it again re-reads, which is what a manual
     * refresh would want.
     */
    fun load() {
        viewModelScope.launch {
            val wantsAll = store.isAllChosen()
            val units =
                when (val result = source.memberships()) {
                    is ApiResult.Success -> {
                        result.value
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "org units could not be read: ${result.error}" }
                        mutableState.value = mutableState.value.copy(loaded = true)
                        return@launch
                    }
                }

            if (wantsAll) {
                mutableState.value = OrgUnitState(units = units, allChosen = true, loaded = true)
                return@launch
            }
            val active = resolveActive(units)
            // Written back so the interceptor and the next cold start agree with what is on screen.
            // Resolving to a unit and NOT storing it would leave the header absent while the badge
            // claimed a scope — the two must not be able to disagree.
            if (active != null && active != store.current()) {
                store.pin(active)
            }
            mutableState.value = OrgUnitState(units = units, activeId = active, loaded = true)
        }
    }

    /**
     * Pins the unit the member chose.
     *
     * @param orgUnitId the unit to act in; ignored when it is not one of the member's own, because
     *   a pin the backend would refuse is worse than no pin.
     */
    fun select(orgUnitId: String) {
        if (mutableState.value.units.none { it.id == orgUnitId }) {
            KrtLog.w(LOG_TAG) { "ignored a pin for an org unit that is not one of the member's" }
            return
        }
        // No coroutine: the store is synchronous now, and the badge should move on the tap
        // rather than a frame later.
        store.pin(orgUnitId)
        mutableState.value = mutableState.value.copy(activeId = orgUnitId, allChosen = false)
    }

    /**
     * Drops the pin so the member acts across every unit they belong to.
     *
     * Nothing widens: with no `X-Active-Org-Unit-Id` the backend answers with the union of the
     * caller's **own** memberships — verified against a two-Staffel member, see
     * `docs/TENANCY_VERIFICATION.md`.
     */
    fun selectAll() {
        store.pinAll()
        mutableState.value = mutableState.value.copy(activeId = null, allChosen = true)
    }

    /**
     * Applies the three-step rule to the freshly read list.
     *
     * @param units the member's org units.
     * @return the id to act in, or `null` when the member has no units at all.
     */
    private suspend fun resolveActive(units: List<OrgUnit>): String? {
        val known = units.map { it.id }.toSet()

        val stored = store.current()
        if (stored != null) {
            if (stored in known) {
                return stored
            }
            // The membership is gone. Dropping the pin here rather than at the next request means
            // the badge and the header change together.
            KrtLog.w(LOG_TAG) { "the pinned org unit is no longer a membership; dropping the pin" }
            store.clear()
        }

        val serverDefault =
            when (val result = source.serverDefault()) {
                is ApiResult.Success -> {
                    result.value
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the server's active org unit could not be read: ${result.error}" }
                    null
                }
            }
        return serverDefault?.takeIf { it in known } ?: units.firstOrNull()?.id
    }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "orgunit"
    }
}
