/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the queue has got. */
sealed interface OrdersPhase {
    /** The first page is on its way. */
    data object Loading : OrdersPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : OrdersPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : OrdersPhase
}

/**
 * Everything the queue draws.
 *
 * @property statuses which statuses are selected; empty means all of them
 * @property orders the rows loaded so far
 * @property total how many the filter matches on the server
 * @property phase how far the first page has got
 * @property page the last page index that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property expanded which orders have their material list open, by id
 */
data class OrdersState(
    val statuses: Set<JobOrderStatus> = emptySet(),
    val orders: List<JobOrder> = emptyList(),
    val total: Long = 0,
    val phase: OrdersPhase = OrdersPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val expanded: Set<String> = emptySet(),
)

/**
 * Drives the Auftrag queue.
 *
 * **The status filter is server-side**, like every other list in this app: filtering a page the
 * server already truncated would make the stated count wrong.
 *
 * The material list of a row collapses by default, mirroring the web app. The open/closed set lives
 * in this state rather than in each row's composable so it survives a scroll — a `LazyColumn`
 * disposes what leaves the viewport, and a member who opened three rows would find them shut on the
 * way back.
 *
 * @property source where the orders come from
 */
class OrdersViewModel(
    private val source: JobOrderSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrdersState())

    /** What the screen draws. */
    val state: StateFlow<OrdersState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedOnce = false

    /** Loads the first page, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /**
     * Narrows to a set of statuses, or widens to all of them when [statuses] is empty.
     *
     * @param statuses the statuses to show.
     */
    fun onStatusesChanged(statuses: Set<JobOrderStatus>) {
        if (statuses == mutableState.value.statuses) {
            return
        }
        loadedOnce = true
        mutableState.value = mutableState.value.copy(statuses = statuses)
        reload(keepRows = false)
    }

    /**
     * Opens or closes one row's material list.
     *
     * @param orderId the row that was tapped.
     */
    fun onToggleMaterials(orderId: String) {
        val open = mutableState.value.expanded
        mutableState.value =
            mutableState.value.copy(
                expanded = if (orderId in open) open - orderId else open + orderId,
            )
    }

    /** Re-reads the first page while keeping the rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepRows = true)
    }

    /** Appends the next page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is OrdersPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.queue(current.statuses, page = current.page + 1)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            orders = latest.orders + result.value.orders,
                            total = result.value.totalElements,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of orders failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Loads page 0 for the current filter.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        loadJob?.cancel()
        val statuses = mutableState.value.statuses
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = OrdersPhase.Loading)
        }
        loadJob =
            viewModelScope.launch {
                when (val result = source.queue(statuses, page = 0)) {
                    is ApiResult.Success -> {
                        mutableState.value =
                            mutableState.value.copy(
                                orders = result.value.orders,
                                total = result.value.totalElements,
                                page = result.value.page,
                                hasMore = result.value.hasMore,
                                phase = OrdersPhase.Ready,
                                loadingMore = false,
                                refreshing = false,
                            )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "orders could not be read: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = OrdersPhase.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                    }
                }
            }
    }

    private companion object {
        /** Log subsystem. A comment is member input and never reaches the log. */
        const val LOG_TAG = "orders"
    }
}

/** How far one order has got. */
sealed interface OrderDetailPhase {
    /** In flight. */
    data object Loading : OrderDetailPhase

    /** It arrived. */
    data object Ready : OrderDetailPhase

    /**
     * It did not.
     *
     * @property error what went wrong; `Forbidden` and `NotFound` are ordinary answers.
     */
    data class Failed(
        val error: ApiError,
    ) : OrderDetailPhase
}

/**
 * One order in full.
 *
 * @property orderId which order, known before anything has loaded
 * @property order the order once it arrives
 * @property phase how far the read has got
 * @property refreshing whether a pull-to-refresh is running
 */
data class OrderDetailState(
    val orderId: String,
    val order: JobOrder? = null,
    val phase: OrderDetailPhase = OrderDetailPhase.Loading,
    val refreshing: Boolean = false,
)

/**
 * Drives one order.
 *
 * @property source where the order comes from
 * @property orderId which order to load
 */
class OrderDetailViewModel(
    private val source: JobOrderSource,
    private val orderId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrderDetailState(orderId = orderId))

    /** What the screen draws. */
    val state: StateFlow<OrderDetailState> = mutableState.asStateFlow()

    /** Loads the order. */
    fun load() {
        reload(keepContent = false)
    }

    /** Re-reads it, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
    }

    /**
     * Reads the order.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = OrderDetailPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.detail(orderId)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            order = result.value,
                            phase = OrderDetailPhase.Ready,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "order could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = OrderDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. */
        const val LOG_TAG = "orders"
    }
}
