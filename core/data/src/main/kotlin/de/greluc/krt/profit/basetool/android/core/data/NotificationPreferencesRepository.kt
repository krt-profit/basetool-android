/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationPreferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationPreferenceWriteRequest
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.ConsentRecovery
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient

/**
 * One notification type and whether the member receives it.
 *
 * @property type the server's type constant, kept as text so a type this build has never seen is
 *   still listed and can still be written back.
 * @property mutable whether the member may turn the type off; `false` for a legal deadline or a
 *   security notice.
 * @property muted whether the member has turned the type off.
 */
data class NotificationPreference(
    val type: String,
    val mutable: Boolean,
    val muted: Boolean,
)

/**
 * The member's per-type notification switches, as a seam.
 *
 * Separate from [NotificationSource] so the inbox's fakes stay as they are and the switches' rules —
 * what a failed write leaves behind — can be exercised without a socket.
 */
interface NotificationPreferencesSource {
    /**
     * Reads every notification type with the caller's switch.
     *
     * @return one entry per type the server knows, or the classified failure.
     */
    suspend fun preferences(): ApiResult<List<NotificationPreference>>

    /**
     * Mutes or unmutes one type; idempotent, and carries no version.
     *
     * @param type the type constant, as [preferences] returned it.
     * @param muted whether to stop receiving the type.
     * @return the stored entry, or the classified failure — `ApiError.Validation` for a type that
     *   cannot be muted.
     */
    suspend fun setMuted(
        type: String,
        muted: Boolean,
    ): ApiResult<NotificationPreference>
}

/**
 * Reads and writes the member's notification switches from `/api/v1/notifications/preferences`
 * (REQ-APP-NOTIF-017), uncached.
 *
 * @property reader performs the calls and classifies their failures.
 */
class NotificationPreferencesRepository(
    private val reader: ApiReader,
) : NotificationPreferencesSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers.
     * @param baseUrl the flavour's API origin.
     * @param consent waits for consent after a terms refusal, so the call is re-issued (ADR-0025).
     */
    constructor(
        httpClient: OkHttpClient,
        baseUrl: String,
        consent: ConsentRecovery = ConsentRecovery.None,
    ) : this(
        ApiReader(
            httpClient = httpClient,
            baseUrl = baseUrl,
            json = KrtJson,
            logTag = LOG_TAG,
            consent = consent,
        ),
    )

    override suspend fun preferences(): ApiResult<List<NotificationPreference>> =
        reader.get(PREFERENCES_PATH, PreferenceRows)

    override suspend fun setMuted(
        type: String,
        muted: Boolean,
    ): ApiResult<NotificationPreference> =
        reader.put(
            path = "$PREFERENCES_PATH/$type",
            body = NotificationPreferenceWriteRequest(muted = muted),
            bodySerializer = NotificationPreferenceWriteRequest.serializer(),
            deserializer = NotificationPreferenceDto.serializer(),
        ).map { it.toModel(type) }

    private companion object {
        /** Log subsystem. */
        const val LOG_TAG = "notification-prefs"

        /** The member's switches, and the prefix of the one-type write. */
        const val PREFERENCES_PATH = "/api/v1/notifications/preferences"
    }
}

/**
 * Maps a wire entry onto the model.
 *
 * An absent `mutable` reads as locked and an absent `muted` as not muted: the safe reading of a value
 * that did not arrive is not to offer a write the server may refuse, and not to claim a mute that was
 * never stored.
 *
 * @param type the type constant the entry belongs to.
 * @return the model.
 */
private fun NotificationPreferenceDto.toModel(type: String): NotificationPreference =
    NotificationPreference(type = type, mutable = mutable == true, muted = muted == true)

/**
 * Decodes the list of switches, reading each `type` as text.
 *
 * The contract declares `type` as an enum, and `KrtJson` decodes a constant it does not know as
 * `null`; a type this build has never seen would arrive without its name and could not be written
 * back. Each row is therefore decoded with the generated [NotificationPreferenceDto] for its flags
 * and with its raw `type` text beside it. A row without a type, or one that does not decode, is
 * dropped.
 */
private object PreferenceRows : KSerializer<List<NotificationPreference>> {
    private val wire = ListSerializer(NotificationPreferenceDto.serializer())

    override val descriptor: SerialDescriptor = wire.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<NotificationPreference>,
    ) {
        error("the preference list is only ever read")
    }

    override fun deserialize(decoder: Decoder): List<NotificationPreference> {
        val json = (decoder as JsonDecoder).json
        val rows = decoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val type = (row["type"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val flags =
                runCatching { json.decodeFromJsonElement(NotificationPreferenceDto.serializer(), row) }
                    .getOrNull()
            if (type == null || flags == null) null else flags.toModel(type)
        }
    }
}
