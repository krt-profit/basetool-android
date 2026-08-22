/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.data.NotificationSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the inbox has got. */
sealed interface NotificationsPhase {
    /** The first page is on its way. */
    data object Loading : NotificationsPhase

    /** A page arrived. It may be empty, which is a result and not a failure. */
    data object Ready : NotificationsPhase

    /**
     * The inbox could not be loaded.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : NotificationsPhase
}

/**
 * Everything the inbox draws.
 *
 * @property notifications every row loaded so far, newest first
 * @property total how many the member has in total on the server
 * @property unread how many are unread — the number the bell badge shows
 * @property phase how far the first page has got
 * @property page the zero-based index of the last page that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running over rows already on screen
 */
data class NotificationsState(
    val notifications: List<Notification> = emptyList(),
    val total: Long = 0,
    val unread: Long = 0,
    val phase: NotificationsPhase = NotificationsPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
)

/**
 * Drives the inbox and the bell badge.
 *
 * **One view model for both, deliberately.** The badge and the list are two views of the same
 * question, and two sources would let them disagree — a member seeing "3 neu" over a list whose top
 * three rows are already read has been told something false by the app itself.
 *
 * **Push and polling both run, which is what the design asks for.** The stream is best-effort: the
 * server closes it every thirty minutes, evicts the oldest connection when a member has six, and
 * any proxy in between may drop it. A badge that went stale in those cases would be worse than one
 * extra request a minute, so the poll runs regardless and the stream makes the common case
 * immediate rather than up-to-a-minute late.
 *
 * Both stop when the app leaves the foreground. Holding a socket open for a screen nobody is
 * looking at spends the member's battery to learn something they cannot see.
 *
 * @property source where the notifications come from
 */
class NotificationsViewModel(
    private val source: NotificationSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationsState())

    /** What the inbox and the badge draw. */
    val state: StateFlow<NotificationsState> = mutableState.asStateFlow()

    private var watchJob: Job? = null
    private var pollJob: Job? = null
    private var inboxLoaded = false

    /**
     * Starts the badge: the unread count, the poll and the push stream.
     *
     * Called when the app comes to the foreground. Safe to call again; a second call does not open
     * a second stream.
     */
    fun onForeground() {
        refreshUnread()
        if (pollJob?.isActive != true) {
            pollJob =
                viewModelScope.launch {
                    while (true) {
                        delay(POLL_INTERVAL_MS)
                        refreshUnread()
                    }
                }
        }
        if (watchJob?.isActive != true) {
            watchJob = viewModelScope.launch { watchStream() }
        }
    }

    /** Stops the poll and closes the stream. Called when the app leaves the foreground. */
    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * Loads the first page of the inbox, once.
     *
     * Called when the screen is opened. Coming back to a list that is already there shows it;
     * pull-to-refresh is how a member asks for fresh rows.
     */
    fun loadOnce() {
        if (inboxLoaded) {
            return
        }
        inboxLoaded = true
        reload(keepRows = false)
    }

    /** Re-reads the first page and the count, keeping the rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        inboxLoaded = true
        reload(keepRows = true)
        refreshUnread()
    }

    /**
     * Appends the next page.
     *
     * Ignored when one is already in flight or the server has no more.
     */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is NotificationsPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.inbox(page = current.page + 1)) {
                is ApiResult.Success -> {
                    val loaded = result.value
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            notifications = latest.notifications + loaded.notifications,
                            total = loaded.totalElements,
                            page = loaded.page,
                            hasMore = loaded.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of notifications failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Collects the push stream, reconnecting with a backoff when it ends.
     *
     * The stream ending is **ordinary**: the server closes it after thirty minutes and evicts the
     * oldest connection when a member opens a sixth. Treating that as an error would put a failure
     * on screen every half hour; treating it as "reconnect after a pause" is what it is.
     */
    private suspend fun watchStream() {
        var backoff = MIN_BACKOFF_MS
        while (true) {
            var received = false
            source.changes().collect {
                received = true
                backoff = MIN_BACKOFF_MS
                refreshUnread()
                if (inboxLoaded) {
                    reload(keepRows = true)
                }
            }
            // A stream that carried at least one event was working, so the next attempt starts
            // from the short delay; one that carried none may be refused outright — a 401 after a
            // sign-out, say — and must not be retried in a tight loop.
            if (!received) {
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
            delay(backoff)
        }
    }

    /** Re-reads the unread count. A failure leaves the previous number rather than showing zero. */
    private fun refreshUnread() {
        viewModelScope.launch {
            when (val result = source.unreadCount()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(unread = result.value)
                }

                is ApiResult.Failure -> {
                    // Zeroing the badge on a failed read would tell the member their inbox is
                    // clear, which is a claim about their notifications made out of an outage.
                    KrtLog.w(LOG_TAG) { "unread count could not be read: ${result.error}" }
                }
            }
        }
    }

    /**
     * Loads page 0 of the inbox.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = NotificationsPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.inbox(page = 0)) {
                is ApiResult.Success -> {
                    val loaded = result.value
                    mutableState.value =
                        mutableState.value.copy(
                            notifications = loaded.notifications,
                            total = loaded.totalElements,
                            page = loaded.page,
                            hasMore = loaded.hasMore,
                            phase = NotificationsPhase.Ready,
                            loadingMore = false,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "notifications could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = NotificationsPhase.Failed(result.error),
                            loadingMore = false,
                            refreshing = false,
                        )
                }
            }
        }
    }

    private companion object {
        /**
         * How often the badge is re-read while the app is in the foreground.
         *
         * A minute: often enough that a member who missed a push is not looking at a stale badge
         * for long, rare enough that an app left open is not a load on the server.
         */
        const val POLL_INTERVAL_MS = 60_000L

        /** First reconnect delay after the stream ends. */
        const val MIN_BACKOFF_MS = 2_000L

        /** Ceiling for the reconnect delay. */
        const val MAX_BACKOFF_MS = 60_000L

        /** Log subsystem. A notification's parameters can name a member and are never logged. */
        const val LOG_TAG = "notifications"
    }
}
