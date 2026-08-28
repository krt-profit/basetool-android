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
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
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
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
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
    val retryIn: Int? = null,
    val pendingDelete: PendingDelete? = null,
)

/**
 * A delete the member can still take back.
 *
 * The server has no way to un-delete a notification, so an undo offered *after* the call would be
 * a button that cannot do what it says. The row leaves the list at once and the call is what
 * waits: five seconds later it goes, and the undo cancels it before then. That makes the take-back
 * real, at the cost of the delete landing five seconds late — which nothing depends on.
 *
 * @property notification the row that vanished, kept so it can come back unchanged.
 * @property index where it was, so it returns to its place rather than to the top.
 */
data class PendingDelete(
    val notification: Notification,
    val index: Int,
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
    private val notifier: SystemNotifications? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationsState())

    /** What the inbox and the badge draw. */
    val state: StateFlow<NotificationsState> = mutableState.asStateFlow()

    /**
     * The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003).
     *
     * Shared rather than re-derived: the conditions under which a countdown is right are the same
     * on every screen.
     */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepRows = false) },
        )

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    private var watchJob: Job? = null
    private var undoJob: Job? = null
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
        flushPendingDelete()
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
     * Marks one notification read, optimistically.
     *
     * The row flips and the badge drops before the call goes out, because the member has already
     * seen the result they asked for. A failure restores the previous read flag rather than
     * setting "unread", so a race cannot invent an unread row out of one that was already read.
     *
     * @param id the notification to mark.
     */
    fun onMarkRead(id: String) {
        val before = mutableState.value
        val row = before.notifications.firstOrNull { it.id == id } ?: return
        if (row.read) {
            return
        }
        mutableState.value =
            before.copy(
                notifications =
                    before.notifications.map { if (it.id == id) it.copy(read = true) else it },
                unread = (before.unread - 1).coerceAtLeast(0),
            )
        viewModelScope.launch {
            // Only the failure path does anything: success is the state the list already shows.
            val result = source.markRead(id)
            if (result is ApiResult.Failure) {
                KrtLog.w(LOG_TAG) { "mark-read failed: ${result.error}" }
                val now = mutableState.value
                mutableState.value =
                    now.copy(
                        notifications =
                            now.notifications.map {
                                if (it.id == id) it.copy(read = row.read) else it
                            },
                        unread = before.unread,
                    )
            }
        }
    }

    /**
     * Marks every unread notification read.
     *
     * The badge takes the server's own number rather than assuming zero: another device may have
     * produced an unread row while this call was in flight, and claiming zero would hide it until
     * the next poll.
     */
    fun onMarkAllRead() {
        val before = mutableState.value
        if (before.unread == 0L) {
            return
        }
        mutableState.value =
            before.copy(
                notifications = before.notifications.map { it.copy(read = true) },
                unread = 0,
            )
        viewModelScope.launch {
            when (val result = source.markAllRead()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(unread = result.value.unreadCount)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "mark-all-read failed: ${result.error}" }
                    reload(keepRows = true)
                    refreshUnread()
                }
            }
        }
    }

    /**
     * Removes one notification from the list and schedules its delete.
     *
     * See [PendingDelete] for why the call waits. Starting a second delete commits the first at
     * once: two pending rows would mean two toasts competing for one corner, and the toast should
     * be about the member's most recent action.
     *
     * @param id the notification to delete.
     */
    fun onDelete(id: String) {
        flushPendingDelete()
        val before = mutableState.value
        val index = before.notifications.indexOfFirst { it.id == id }
        if (index < 0) {
            return
        }
        val row = before.notifications[index]
        mutableState.value =
            before.copy(
                notifications = before.notifications.filterNot { it.id == id },
                total = (before.total - 1).coerceAtLeast(0),
                unread = if (row.read) before.unread else (before.unread - 1).coerceAtLeast(0),
                pendingDelete = PendingDelete(row, index),
            )
        undoJob =
            viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                commitDelete(row.id)
            }
    }

    /** Puts the pending row back where it was and cancels its delete. */
    fun onUndoDelete() {
        undoJob?.cancel()
        undoJob = null
        val current = mutableState.value
        val pending = current.pendingDelete ?: return
        val rows = current.notifications.toMutableList()
        rows.add(pending.index.coerceIn(0, rows.size), pending.notification)
        mutableState.value =
            current.copy(
                notifications = rows,
                total = current.total + 1,
                unread = if (pending.notification.read) current.unread else current.unread + 1,
                pendingDelete = null,
            )
    }

    /**
     * Deletes every already-read notification.
     *
     * No undo window on this one, unlike a single row. It names exactly what it removes, it touches
     * nothing the member has not already seen, and holding an unbounded number of rows in memory
     * to offer a take-back would be a different feature. A failure reloads rather than guesses.
     */
    fun onDeleteRead() {
        flushPendingDelete()
        val before = mutableState.value
        val kept = before.notifications.filterNot { it.read }
        if (kept.size == before.notifications.size) {
            return
        }
        mutableState.value =
            before.copy(
                notifications = kept,
                total = (before.total - (before.notifications.size - kept.size)).coerceAtLeast(0),
            )
        viewModelScope.launch {
            when (val result = source.deleteRead()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(unread = result.value.unreadCount)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "delete-read failed: ${result.error}" }
                    reload(keepRows = true)
                    refreshUnread()
                }
            }
        }
    }

    /**
     * Sends a pending delete now instead of waiting out its window.
     *
     * Runs when another delete starts and when the screen stops being looked at. Without it a
     * member who deleted a row and immediately left would find it still there on return.
     */
    private fun flushPendingDelete() {
        val pending = mutableState.value.pendingDelete ?: return
        undoJob?.cancel()
        undoJob = null
        mutableState.value = mutableState.value.copy(pendingDelete = null)
        viewModelScope.launch { commitDelete(pending.notification.id) }
    }

    /**
     * Performs the delete and clears the undo state.
     *
     * A failure puts the row back. The list already said it was gone; leaving it gone while the
     * server still holds it would show a state that no reload agrees with.
     *
     * @param id the notification to delete.
     */
    private suspend fun commitDelete(id: String) {
        val pending = mutableState.value.pendingDelete
        when (val result = source.delete(id)) {
            is ApiResult.Success -> {
                if (pending?.notification?.id == id) {
                    mutableState.value = mutableState.value.copy(pendingDelete = null)
                }
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "delete failed: ${result.error}" }
                if (pending?.notification?.id == id) {
                    onUndoDelete()
                } else {
                    reload(keepRows = true)
                }
            }
        }
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
            source.changes().collect { signal ->
                received = true
                backoff = MIN_BACKOFF_MS
                refreshUnread()
                if (inboxLoaded) {
                    reload(keepRows = true)
                }
                // The shade half of chapter 14. Posted here rather than from the screen, because
                // the point is to reach a member who is NOT looking at the inbox; a screen-level
                // hook would fire exactly when it is least needed.
                //
                // The signal goes through whole: the notifier owns the wording, the channel and the
                // deep link, because all three are decided by the same two fields and splitting
                // them across two files would give the shade and the inbox a way to disagree about
                // what one push is.
                notifier?.notify(signal)
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
                    retry.onSuccess()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "notifications could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = NotificationsPhase.Failed(result.error),
                            loadingMore = false,
                            refreshing = false,
                        )
                    retry.onFailure(result.error, hasContent = keepRows)
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

        /**
         * How long the undo stays available, in milliseconds.
         *
         * Five seconds, from the design's swipe spec — and therefore also how late the delete
         * itself lands.
         */
        private const val UNDO_WINDOW_MS = 5_000L

        /** Log subsystem. A notification's parameters can name a member and are never logged. */
        const val LOG_TAG = "notifications"
    }
}
