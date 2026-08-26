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
 * Reads the announcement from the backend.
 *
 * **`204` is a result here, and that is the whole reason this uses the optional read.** A member
 * with nothing announced must see no banner, not an error where a banner would be; reading the
 * empty body through the ordinary path would fail to parse and surface as a broken contract.
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
     * Reads the current announcement.
     *
     * A blank `content` is treated as no announcement. The backend already suppresses blank ones
     * with a `204`, but the field is nullable on the wire and a banner made of whitespace would be
     * a visible defect for the sake of trusting a shape.
     *
     * @return the announcement, `null` when there is none, or the classified failure.
     */
    override suspend fun current(): ApiResult<Announcement?> =
        when (val result = reader.getOptional(ANNOUNCEMENT_PATH, AnnouncementDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value?.toModel())
        }

    /**
     * Reads which announcement the caller has already marked read.
     *
     * The flag lives on the member, not on the announcement -- `/users/me` carries
     * `lastReadAnnouncementId` -- so "is this unread" is a comparison between two reads rather
     * than a field. It is asked here rather than through `IdentityRepository`, which caches its
     * answer for the process on the grounds that a member's id cannot change while the app runs.
     * This value can, on the very next tap.
     *
     * @return the id, or `null` when the member has marked none.
     */
    override suspend fun lastRead(): ApiResult<String?> =
        when (val result = reader.get(ME_PATH, UserDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.lastReadAnnouncementId)
        }

    /**
     * Marks an announcement read for the caller.
     *
     * A bodyless `PUT` addressed entirely by its path, answering with the updated member. Note
     * what the server's model does and does not promise: **editing an announcement keeps its id**,
     * so a notice whose text is rewritten stays read for everyone who had read the old wording.
     * That is the backend's semantics, shared with the web app, and the band reports it rather
     * than inventing a second notion of freshness on the device.
     *
     * @param id the announcement's id.
     * @return the id the server now holds.
     */
    override suspend fun markRead(id: String): ApiResult<String?> =
        when (val result = reader.put("$READ_PATH/$id", UserDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.lastReadAnnouncementId)
        }

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
