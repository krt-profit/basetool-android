/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the tree has got. */
sealed interface InventoryPhase {
    /** The first page is on its way. */
    data object Loading : InventoryPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : InventoryPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : InventoryPhase
}

/**
 * How far one opened group has got.
 *
 * A group is its own little screen: it loads on the tap that opened it, and it can fail on its own
 * without the tree around it failing.
 */
sealed interface StackPhase {
    /** The stacks are on their way. */
    data object Loading : StackPhase

    /**
     * They arrived.
     *
     * @property stacks the holdings inside this group.
     */
    data class Ready(
        val stacks: List<InventoryStack>,
    ) : StackPhase

    /** They did not. The group stays open and says so rather than closing itself. */
    data object Failed : StackPhase
}

/**
 * Everything the Lager tree draws.
 *
 * @property groups the material rows loaded so far
 * @property total how many materials the org unit holds in total
 * @property phase how far the first page has got
 * @property page the last page index that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property opened the state of each opened group, keyed by material id
 * @property withStockOnly whether groups holding nothing are hidden
 */
data class InventoryState(
    val groups: List<InventoryGroup> = emptyList(),
    val total: Long = 0,
    val phase: InventoryPhase = InventoryPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val opened: Map<String, StackPhase> = emptyMap(),
    val withStockOnly: Boolean = false,
) {
    /**
     * The rows the tree actually shows.
     *
     * "Nur mit Bestand" is applied **on the device**, and that is deliberate rather than an
     * oversight: the endpoint has no such parameter, and the alternative would be to leave the
     * chip out. What makes it safe is that the chip hides rows from a page the member already has —
     * it never claims to have filtered the whole warehouse, and the count below the list keeps
     * stating the server's total.
     */
    val visibleGroups: List<InventoryGroup>
        get() =
            if (withStockOnly) {
                groups.filter { (it.amount?.toDoubleOrNull() ?: 0.0) > 0.0 }
            } else {
                groups
            }
}

/**
 * Drives the Lager tree.
 *
 * **A group's stacks are fetched when it is opened, never before.** The tree's first level is one
 * request; fetching every group's holdings up front would pull the whole warehouse to draw a dozen
 * headings, most of which a member never opens.
 *
 * Closing a group **keeps** what was loaded, so re-opening it is instant. The Lager changes slowly
 * enough that a member re-opening a group within one visit expects what they just saw; pull-to-
 * refresh is how they ask for more.
 *
 * @property source where the Lager comes from
 */
class InventoryViewModel(
    private val source: InventorySource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryState())

    /** What the screen draws. */
    val state: StateFlow<InventoryState> = mutableState.asStateFlow()

    private var loadedOnce = false

    /** Loads the first page, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /** Re-reads the first page and drops every loaded group, since their contents may have moved. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true, opened = emptyMap())
        loadedOnce = true
        reload(keepRows = true)
    }

    /**
     * Shows or hides the groups that hold nothing.
     *
     * @param enabled whether to hide them.
     */
    fun onWithStockOnlyChanged(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(withStockOnly = enabled)
    }

    /**
     * Opens or closes one group, loading its stacks the first time.
     *
     * @param materialId the group that was tapped.
     */
    fun onToggleGroup(materialId: String) {
        val opened = mutableState.value.opened
        if (materialId in opened) {
            mutableState.value = mutableState.value.copy(opened = opened - materialId)
            return
        }
        mutableState.value = mutableState.value.copy(opened = opened + (materialId to StackPhase.Loading))
        viewModelScope.launch {
            val phase =
                when (val result = source.stacks(materialId)) {
                    is ApiResult.Success -> {
                        StackPhase.Ready(result.value)
                    }

                    is ApiResult.Failure -> {
                        // The group stays open and says so. Closing it would look like the tap did
                        // not register, and the member would try again.
                        KrtLog.w(LOG_TAG) { "stacks could not be read: ${result.error}" }
                        StackPhase.Failed
                    }
                }
            // Only if the group is still open: a member who closed it while the read was in flight
            // must not have it spring open again.
            val current = mutableState.value
            if (materialId in current.opened) {
                mutableState.value = current.copy(opened = current.opened + (materialId to phase))
            }
        }
    }

    /** Appends the next page of groups. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is InventoryPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.groups(page = current.page + 1)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            groups = latest.groups + result.value.groups,
                            total = result.value.totalElements,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of the Lager failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Loads page 0.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = InventoryPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.groups(page = 0)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            groups = result.value.groups,
                            total = result.value.totalElements,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            phase = InventoryPhase.Ready,
                            loadingMore = false,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the Lager could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = InventoryPhase.Failed(result.error),
                            loadingMore = false,
                            refreshing = false,
                        )
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. A holder's name is member data and never reaches the log. */
        const val LOG_TAG = "inventory"
    }
}
