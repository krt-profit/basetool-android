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
 * What the server's `notification` event says arrived (REQ-NOTIF-021).
 *
 * A bare `new` payload, sent on every degraded server path, is a normal answer and maps to
 * [refreshOnly].
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
         * Reads an event's data; the literal `new`, an empty body or an unknown shape reads as
         * [refreshOnly].
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
