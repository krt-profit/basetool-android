/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One row of the staff dashboard, with the two facts the row cannot work out for itself.
 *
 * @property account the account.
 * @property openRequests how many undecided requests stand against it, counted from the queue.
 *   Artboard 4's handoff is explicit that this is aggregated client-side and needs no DTO field.
 * @property viewable whether this caller could see the account **without** their staff role. A
 *   staff member sees every account of the unit; the ones they hold no view grant on are marked,
 *   because reading someone's balance by virtue of an office is a different act from reading one
 *   they were given sight of.
 */
data class BankStaffRow(
    val account: BankStaffAccount,
    val openRequests: Int,
    val viewable: Boolean,
)

/**
 * The Verwaltung scope's Übersicht tab.
 *
 * @property rows every account of the unit.
 * @property totals the KPI band.
 * @property management whether the **server** grants this caller Bank-Management.
 * @property openRequestTotal how many undecided requests the queue holds in total.
 * @property countsPartial whether the per-account counters are known to be incomplete — the queue
 *   is paged, and a queue longer than [MAX_COUNTED_PAGES] pages is not walked to the end. The
 *   number is then a floor, and the screen says so rather than showing a total that is quietly
 *   wrong (ADR-0104: no silent caps).
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 */
data class BankStaffState(
    val rows: List<BankStaffRow> = emptyList(),
    val totals: BankStaffTotals = BankStaffTotals(null, 0, 0),
    val management: Boolean = false,
    val openRequestTotal: Int = 0,
    val countsPartial: Boolean = false,
    val phase: BankPhase = BankPhase.Loading,
    val refreshing: Boolean = false,
)

/**
 * Drives the bank's Verwaltung scope.
 *
 * **A `Forbidden` here is an ordinary answer, not a defect.** The scope segment is drawn for every
 * member — locked for those without the role, per the design's chapter-09 pattern — and a member
 * who taps into it anyway is told what the server said rather than shown a crash.
 *
 * @property source the staff calls.
 * @property memberAccounts the member-visible account list, which is what makes the
 *   "ohne eigenen View-Grant" mark possible: an account on the staff list but not on this one is
 *   an account this caller reaches only through their office.
 * @property liveSync the peer bridge, or `null`.
 */
class BankStaffViewModel(
    private val source: BankStaffSource,
    private val memberAccounts: suspend () -> ApiResult<List<BankAccountSummary>>,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BankStaffState())

    /** What the tab draws. */
    val state: StateFlow<BankStaffState> = mutableState.asStateFlow()

    private var loadedOnce = false

    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.ORGUNIT_BANK)) { _ ->
            if (loadedOnce) {
                reload(keepContent = true)
            }
        }
    }

    /** Loads the dashboard, the first time the scope is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepContent = false)
    }

    /** Re-reads it, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepContent = true)
    }

    /**
     * Reads the dashboard, then the two things the rows are annotated from.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            when (val dashboard = source.staffDashboard()) {
                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "staff dashboard could not be read: ${dashboard.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = BankPhase.Failed(dashboard.error),
                            refreshing = false,
                        )
                }

                is ApiResult.Success -> {
                    val counts = countOpenRequests()
                    val viewable = readViewableIds()
                    mutableState.value =
                        BankStaffState(
                            rows =
                                dashboard.value.accounts.map { account ->
                                    BankStaffRow(
                                        account = account,
                                        openRequests = counts.perAccount[account.id] ?: 0,
                                        // Unknown means the member read failed; marking every row
                                        // as reached-by-office would be a louder claim than the app
                                        // can support, so nothing is marked.
                                        viewable = viewable == null || account.id in viewable,
                                    )
                                },
                            totals = dashboard.value.totals,
                            management = dashboard.value.management,
                            openRequestTotal = counts.total,
                            countsPartial = counts.partial,
                            phase = BankPhase.Ready,
                        )
                }
            }
        }
    }

    /**
     * What the per-account counters came to, and whether they are complete.
     *
     * @property perAccount how many undecided requests stand against each account.
     * @property total how many the queue holds altogether.
     * @property partial whether the walk stopped before the end of the queue.
     */
    private data class OpenRequestCounts(
        val perAccount: Map<String, Int>,
        val total: Int,
        val partial: Boolean,
    )

    /**
     * Walks the pending queue and counts it by account.
     *
     * Bounded on purpose. A queue deep enough to need more than [MAX_COUNTED_PAGES] pages says
     * something has gone badly wrong upstream, and spending that many round trips to decorate a
     * dashboard would be the wrong trade — but a truncated count is reported as truncated rather
     * than shown as if it were the whole (ADR-0104).
     *
     * @return the counts, and whether they are complete.
     */
    private suspend fun countOpenRequests(): OpenRequestCounts {
        val perAccount = mutableMapOf<String, Int>()
        var total = 0
        var page = 0
        var complete = false
        while (!complete && page < MAX_COUNTED_PAGES) {
            when (val result = source.requestQueue(page = page)) {
                is ApiResult.Failure -> {
                    // The dashboard still renders. A decoration that could not be read must not
                    // take the screen down with it — but it must not pretend to be complete.
                    KrtLog.w(LOG_TAG) { "request queue unavailable: ${result.error}" }
                    return OpenRequestCounts(perAccount, total, partial = true)
                }

                is ApiResult.Success -> {
                    result.value.requests.forEach { request ->
                        total++
                        request.accountId?.let { perAccount[it] = (perAccount[it] ?: 0) + 1 }
                    }
                    complete = !result.value.hasMore
                    page++
                }
            }
        }
        if (!complete) {
            KrtLog.w(LOG_TAG) { "queue walk stopped at $MAX_COUNTED_PAGES pages; counts are a floor" }
        }
        return OpenRequestCounts(perAccount, total, partial = !complete)
    }

    /**
     * Which accounts this caller could see without their staff role.
     *
     * @return the ids, or `null` when the member read failed — in which case nothing is marked.
     */
    private suspend fun readViewableIds(): Set<String>? =
        when (val result = memberAccounts()) {
            is ApiResult.Success -> {
                result.value.map { it.id }.toSet()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "member account list unavailable: ${result.error}" }
                null
            }
        }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"

        /** How many queue pages the counter walks before it settles for a floor. */
        const val MAX_COUNTED_PAGES = 20
    }
}
