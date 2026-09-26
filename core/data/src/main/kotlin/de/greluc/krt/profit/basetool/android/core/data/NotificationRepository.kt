/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationBulkResultDto
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationDto
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationUnreadCountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseNotificationDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.SseStream
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * The notification inbox, as a seam.
 *
 * Separate from its HTTP implementation so the badge's and the inbox's rules — when a re-read
 * happens, what a dead stream costs — can be exercised without a socket.
 */
interface NotificationSource {
    /**
     * Reads one page of the inbox, newest first.
     *
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun inbox(
        page: Int = 0,
        pageSize: Int = NotificationRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<NotificationPage>

    /**
     * Reads how many notifications are unread.
     *
     * Its own endpoint rather than counting the first page: the badge must be right about the
     * hundredth unread notification as well as the tenth, and the page is capped.
     *
     * @return the count, or a failure.
     */
    suspend fun unreadCount(): ApiResult<Long>

    /**
     * Signals that something changed, one emission per server-side event; a hint to re-read, not
     * data.
     *
     * @return a cold flow; collecting opens the connection, cancelling closes it, and it completes
     *   when the server closes the stream every thirty minutes.
     */
    fun changes(): Flow<NotificationSignal>

    /**
     * Marks one notification read.
     *
     * @param id the notification to mark.
     * @return success, or a failure the caller can roll its optimistic update back on.
     */
    suspend fun markRead(id: String): ApiResult<Unit>

    /**
     * Marks every unread notification of the caller read.
     *
     * @return how many rows changed and what the unread count is now, or a failure.
     */
    suspend fun markAllRead(): ApiResult<NotificationBulkResult>

    /**
     * Deletes one notification.
     *
     * @param id the notification to delete.
     * @return success, or a failure the caller can restore the row on.
     */
    suspend fun delete(id: String): ApiResult<Unit>

    /**
     * Deletes every already-read notification of the caller.
     *
     * @return how many rows went and what the unread count is now, or a failure.
     */
    suspend fun deleteRead(): ApiResult<NotificationBulkResult>
}

/**
 * What a bulk inbox action changed, including the new unread count so no follow-up read is needed.
 *
 * @property affected how many rows the action touched.
 * @property unreadCount how many notifications are unread now.
 */
data class NotificationBulkResult(
    val affected: Int,
    val unreadCount: Long,
)

/**
 * Reads the notification inbox from the backend, uncached.
 *
 * @property reader performs the calls and classifies their failures
 * @property stream opens the push channel
 */
class NotificationRepository(
    private val reader: ApiReader,
    private val stream: SseStream,
) : NotificationSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
        SseStream(httpClient = httpClient, baseUrl = baseUrl),
    )

    /**
     * Reads one page of the inbox, dropping rows without an id while passing the server's total
     * through.
     *
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun inbox(
        page: Int,
        pageSize: Int,
    ): ApiResult<NotificationPage> {
        val params =
            listOf(
                PAGE_PARAM to page.toString(),
                SIZE_PARAM to pageSize.toString(),
                SORT_PARAM to NEWEST_FIRST,
            )
        return reader.get(INBOX_PATH, params, PageResponseNotificationDto.serializer())
            .map { it.toModel(page) }
    }

    /**
     * Reads the unread count.
     *
     * @return the count, or the classified failure.
     */
    override suspend fun unreadCount(): ApiResult<Long> =
        reader.get(UNREAD_PATH, NotificationUnreadCountDto.serializer())
            .map { it.count ?: 0L }

    /**
     * Opens the push channel and emits once per `notification` event, filtering out `connected`,
     * `heartbeat` and `replaced`.
     *
     * @return the signal flow.
     */
    override fun changes(): Flow<NotificationSignal> =
        stream.events(STREAM_PATH)
            .filter { it.name == NOTIFICATION_EVENT }
            .map { NotificationSignal.parse(it.data) }

    /**
     * Marks one notification read, discarding the updated row the server answers with.
     *
     * @param id the notification to mark.
     * @return success, or the classified failure.
     */
    override suspend fun markRead(id: String): ApiResult<Unit> =
        reader.post("$INBOX_PATH/$id/read", NotificationDto.serializer())
            .map { }

    /**
     * Marks every unread notification read.
     *
     * @return the affected count and the new unread count, or the classified failure.
     */
    override suspend fun markAllRead(): ApiResult<NotificationBulkResult> =
        reader.post(READ_ALL_PATH, NotificationBulkResultDto.serializer())
            .map { it.toModel() }

    /**
     * Deletes one notification.
     *
     * @param id the notification to delete.
     * @return success, or the classified failure.
     */
    override suspend fun delete(id: String): ApiResult<Unit> = reader.delete("$INBOX_PATH/$id")

    /**
     * Deletes every already-read notification.
     *
     * @return the affected count and the new unread count, or the classified failure.
     */
    override suspend fun deleteRead(): ApiResult<NotificationBulkResult> =
        reader.delete(READ_PATH, NotificationBulkResultDto.serializer())
            .map { it.toModel() }

    companion object {
        /**
         * Rows per page.
         *
         * Fifty, which is the web app's own "newest 50 + Mehr laden" (REQ-NOTIF-019). Matching it
         * means the two clients truncate at the same place and a member comparing them sees the
         * same list.
         */
        const val DEFAULT_PAGE_SIZE: Int = 50

        /** Log subsystem. A notification's parameters can name a member and are never logged. */
        private const val LOG_TAG = "notifications"

        private const val INBOX_PATH = "/api/v1/notifications"
        private const val UNREAD_PATH = "/api/v1/notifications/unread-count"
        private const val STREAM_PATH = "/api/v1/notifications/stream"
        private const val READ_ALL_PATH = "/api/v1/notifications/read-all"
        private const val READ_PATH = "/api/v1/notifications/read"
        private const val NOTIFICATION_EVENT = "notification"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val SORT_PARAM = "sort"

        /**
         * Newest notification first; sent explicitly because the server sorts ascending by default.
         */
        private const val NEWEST_FIRST = "createdAt,desc"
    }
}

/**
 * Maps a page of wire rows onto the model.
 *
 * @param page the page index that was requested, used because the envelope's own is optional.
 * @return the page, without rows the server sent without an id.
 */
private fun PageResponseNotificationDto.toModel(page: Int): NotificationPage =
    NotificationPage(
        rows = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one wire row onto the model.
 *
 * A row without a `type` is kept: the screen has a generic sentence for exactly that case, and
 * dropping it would hide a notification the server thought worth raising.
 *
 * @return the notification, or `null` when it has no id.
 */
private fun NotificationDto.toModel(): Notification? {
    val rowId = id ?: return null
    return Notification(
        id = rowId,
        type = type.orEmpty(),
        params = params.orEmpty(),
        entityType = entityType,
        entityId = entityId,
        read = read == true,
        createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}

/**
 * Maps a bulk result off the wire, defaulting both numbers to zero.
 *
 * @return the model.
 */
private fun NotificationBulkResultDto.toModel(): NotificationBulkResult =
    NotificationBulkResult(affected = affected ?: 0, unreadCount = unreadCount ?: 0L)
