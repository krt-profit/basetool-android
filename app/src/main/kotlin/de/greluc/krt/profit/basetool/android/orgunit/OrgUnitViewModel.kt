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
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the shell shows for the active org unit, and what the switcher offers.
 *
 * @property units the member's org units, in the order the server returned them.
 * @property activeId the unit currently pinned, or `null` while none is known or when [allChosen].
 * @property allChosen whether the member chose to act across all their units at once; distinct from
 *   no unit resolved yet, which renders no badge.
 * @property loaded whether the list has been read at least once; `false` is not the same as an
 *   empty list and renders differently.
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
 * Resolution order: a pin on this device, else the server's default
 * (`GET /api/v1/me/active-org-unit`), else the first membership; an administrator without a pin
 * defaults to „Alle Org-Einheiten" instead. „Alle Org-Einheiten" is stored as an explicit choice
 * and sends no header. A pin for a unit the member no longer belongs to is dropped. Read failures
 * keep the previous state and are logged.
 *
 * @property source reads the memberships and the server's default
 * @property store the pin, shared with the request interceptor
 * @property identity answers whether the caller is an admin; a failed read is treated as not an
 *   admin
 */
class OrgUnitViewModel(
    private val source: OrgUnitSource,
    private val store: ActiveOrgUnitStore,
    private val identity: IdentitySource,
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
                        mutableState.update { it.copy(loaded = true) }
                        return@launch
                    }
                }

            if (wantsAll) {
                mutableState.value = OrgUnitState(units = units, allChosen = true, loaded = true)
                return@launch
            }
            if (store.current() == null && isAdmin()) {
                store.pinAll()
                mutableState.value = OrgUnitState(units = units, allChosen = true, loaded = true)
                return@launch
            }
            val active = resolveActive(units)
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
        store.pin(orgUnitId)
        mutableState.update { it.copy(activeId = orgUnitId, allChosen = false) }
    }

    /**
     * Drops the pin so the member acts across every unit they belong to.
     *
     * Nothing widens: with no `X-Active-Org-Unit-Id` the backend answers with the union of the
     * caller's **own** memberships — verified against a two-Staffel member, see
     * `docs/archive/TENANCY_VERIFICATION.md`.
     */
    fun selectAll() {
        store.pinAll()
        mutableState.update { it.copy(activeId = null, allChosen = true) }
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

    /**
     * Whether the caller is an administrator.
     *
     * @return `true` only on a successful read that says so. A failed read answers `false` — the
     *   narrower of the two, which lands on the unchanged member path rather than widening the
     *   default scope on the strength of a request that did not come back.
     */
    private suspend fun isAdmin(): Boolean =
        when (val result = identity.me()) {
            is ApiResult.Success -> {
                result.value.admin
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the caller's identity could not be read: ${result.error}" }
                false
            }
        }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "orgunit"
    }
}
