/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import kotlinx.serialization.Serializable

/**
 * What the server's `notification` event says arrived.
 *
 * The event carried the literal string `new` until the backend's REQ-NOTIF-021. That is enough to
 * refetch a badge and not enough to file a shade entry under the right channel or open the screen
 * the message is about — the two things design chapters 14 and 03 ask of this app.
 *
 * **Absence is a normal answer, not a failure.** A push whose inbox was only *cleared* still carries
 * the historic `new`, and so does every degraded path on the server: an unserialisable signal, a
 * peer replica on an older build, a notification type it does not know. [refreshOnly] is that case,
 * and it is what this app must keep working with — it is exactly the payload it had before.
 *
 * @property type the server's type constant, e.g. `JOB_ORDER_CREATED`; `null` for a bare refresh.
 * @property entityType what the message is about, e.g. `JOB_ORDER`; `null` for a bare refresh.
 * @property entityId that thing's id; `null` for a bare refresh.
 * @property params the named values the wording substitutes; empty for a bare refresh.
 */
data class NotificationSignal(
    val type: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val params: Map<String, String> = emptyMap(),
) {
    /** Whether this push describes a message, as opposed to "your inbox changed". */
    val describesNotification: Boolean get() = !type.isNullOrBlank()

    companion object {
        /** The push that says only that something changed. */
        fun refreshOnly(): NotificationSignal = NotificationSignal()

        /**
         * Reads an event's data.
         *
         * Anything that is not a signal — the literal `new`, an empty body, a shape this build does
         * not understand — reads as [refreshOnly]. That is not defensive coding for its own sake:
         * the server deliberately degrades to `new` on several paths, and a client that treated an
         * unreadable payload as an error would drop a push it was meant to act on.
         *
         * @param data the event's `data:` lines, newline-joined.
         * @return the signal, or [refreshOnly] when the payload carries none.
         */
        fun parse(data: String): NotificationSignal {
            val trimmed = data.trim()
            val wire =
                trimmed
                    .takeIf { it.startsWith("{") }
                    ?.let { runCatching { KrtJson.decodeFromString<WireSignal>(it) }.getOrNull() }
                    ?: return refreshOnly()
            return NotificationSignal(
                type = wire.type?.takeIf { it.isNotBlank() },
                entityType = wire.entityType?.takeIf { it.isNotBlank() },
                entityId = wire.entityId?.takeIf { it.isNotBlank() },
                params = wire.params.orEmpty(),
            )
        }
    }
}

/**
 * The signal as it travels.
 *
 * @property type the server's type constant.
 * @property entityType what the message is about.
 * @property entityId that thing's id.
 * @property params the named values the wording substitutes.
 */
@Serializable
private data class WireSignal(
    val type: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val params: Map<String, String>? = null,
)
