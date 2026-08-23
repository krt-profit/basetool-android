/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationDto
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationUnreadCountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseNotificationDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.SseStream
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
     * Signals that something changed, one emission per server-side event.
     *
     * The stream carries no payload — the server sends the bare word "new" — so this is a **hint to
     * re-read**, not data. Modelling it as `Unit` keeps that honest: a client that tried to render
     * the event would be rendering nothing.
     *
     * @return a cold flow; collecting opens the connection, cancelling closes it, and the flow
     *   completes when the server closes the stream, which it does every thirty minutes by design.
     */
    fun changes(): Flow<Unit>
}

/**
 * Reads the notification inbox from the backend.
 *
 * Nothing is cached. The inbox is the one list whose whole purpose is to be current, and the badge
 * beside it would be a lie a moment later.
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
     * Reads one page of the inbox.
     *
     * A row without an id is dropped — it cannot be marked read or opened — but the server's total
     * is passed through untouched, because that total is what the screen states and lowering it
     * quietly would hide the fault.
     *
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun inbox(
        page: Int,
        pageSize: Int,
    ): ApiResult<NotificationPage> {
        val params = listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString())
        return when (val result = reader.get(INBOX_PATH, params, PageResponseNotificationDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    /**
     * Reads the unread count.
     *
     * @return the count, or the classified failure.
     */
    override suspend fun unreadCount(): ApiResult<Long> =
        when (val result = reader.get(UNREAD_PATH, NotificationUnreadCountDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.count ?: 0L)
        }

    /**
     * Opens the push channel and emits once per `notification` event.
     *
     * `connected`, `heartbeat` and `replaced` are filtered out here rather than passed on. They are
     * the stream's own bookkeeping, and a caller that re-read the inbox on every heartbeat would
     * poll every twenty seconds while believing it was using push.
     *
     * @return the signal flow.
     */
    override fun changes(): Flow<Unit> =
        stream.events(STREAM_PATH)
            .filter { it.name == NOTIFICATION_EVENT }
            .map { }

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
        private const val NOTIFICATION_EVENT = "notification"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
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
        notifications = content.orEmpty().mapNotNull { it.toModel() },
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
