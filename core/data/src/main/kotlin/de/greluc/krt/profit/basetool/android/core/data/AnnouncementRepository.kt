/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AnnouncementDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UserDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * The org-wide announcement shown at the top of the dashboard.
 *
 * @property id the row's id, which is what "have I read this" is answered against
 * @property content the announcement text as an admin wrote it
 * @property updatedAt when it was last changed, in UTC; `null` when the server sent none
 */
data class Announcement(
    val id: String,
    val content: String,
    val updatedAt: Instant?,
)

/**
 * The announcement read, as a seam.
 */
interface AnnouncementSource {
    /**
     * Reads the current announcement.
     *
     * @return the announcement, or `null` when there is none — which is an ordinary answer, not a
     *   failure: the server says so with `204 No Content`.
     */
    suspend fun current(): ApiResult<Announcement?>

    /**
     * Reads which announcement the caller has already marked read.
     *
     * @return the id, or `null` when the member has marked none.
     */
    suspend fun lastRead(): ApiResult<String?>

    /**
     * Marks an announcement read for the caller.
     *
     * @param id the announcement's id.
     * @return the id the server now holds, which is the value the band re-reads its state from.
     */
    suspend fun markRead(id: String): ApiResult<String?>
}

/**
 * Reads the announcement from the backend; a `204` means no announcement and is read through the optional path.
 *
 * @property reader performs the call and classifies its failure
 */
class AnnouncementRepository(
    private val reader: ApiReader,
) : AnnouncementSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads the current announcement; a blank `content` counts as none.
     *
     * @return the announcement, `null` when there is none, or the classified failure.
     */
    override suspend fun current(): ApiResult<Announcement?> =
        reader.getOptional(ANNOUNCEMENT_PATH, AnnouncementDto.serializer())
            .map { it?.toModel() }

    /**
     * Reads which announcement the caller has already marked read, from `lastReadAnnouncementId` on `/users/me`.
     *
     * Read fresh rather than through the process-cached `IdentityRepository`, since it changes on the
     * next tap.
     *
     * @return the id, or `null` when the member has marked none.
     */
    override suspend fun lastRead(): ApiResult<String?> =
        reader.get(ME_PATH, UserDto.serializer())
            .map { it.lastReadAnnouncementId }

    /**
     * Marks an announcement read for the caller with a bodyless `PUT`.
     *
     * An edited announcement keeps its id, so it stays read for members who read the old wording.
     *
     * @param id the announcement's id.
     * @return the id the server now holds.
     */
    override suspend fun markRead(id: String): ApiResult<String?> =
        reader.put("$READ_PATH/$id", UserDto.serializer())
            .map { it.lastReadAnnouncementId }

    private companion object {
        /** Log subsystem. The announcement text is org content and is never logged. */
        const val LOG_TAG = "dashboard"

        /** The caller's own record, read for `lastReadAnnouncementId` alone. */
        const val ME_PATH = "/api/v1/users/me"

        /** The bodyless mark-read edge; the announcement id is appended. */
        const val READ_PATH = "/api/v1/users/me/read-announcement"

        /**
         * The public announcement.
         *
         * Not `/api/v1/announcement/admin`, which is the same row read by an admin form and would
         * return the last saved text even when it has been blanked.
         */
        const val ANNOUNCEMENT_PATH = "/api/v1/announcement"
    }
}

/**
 * Maps the wire announcement onto the model.
 *
 * @return the announcement, or `null` when its content is blank.
 */
private fun AnnouncementDto.toModel(): Announcement? {
    val text = content?.trim().orEmpty()
    val key = id.orEmpty()
    if (text.isEmpty() || key.isEmpty()) {
        return null
    }
    return Announcement(
        id = key,
        content = text,
        updatedAt = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}
