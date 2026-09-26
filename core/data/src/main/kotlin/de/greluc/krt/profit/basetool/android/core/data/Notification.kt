/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import java.time.Instant

/**
 * Which part of the tool a notification came from, which is what decides its icon.
 *
 * Derived from the notification's `type` prefix rather than from a field: the server sends no such
 * classification, and the design's rule is stated in exactly those terms ("Einsatz/target,
 * Auftrag/clipboard-list, Bank/bank, Börse/swap, System/info").
 */
enum class NotificationKind {
    /** An Einsatz or Operation. */
    MISSION,

    /** A job order. */
    ORDER,

    /** The org bank. */
    BANK,

    /** The Materialbörse. */
    EXCHANGE,

    /**
     * Anything else, including a type this build has never seen.
     *
     * Not a failure: the server may add a rule at any time, and a notification whose icon this
     * build cannot classify is still one a member must be able to read.
     */
    SYSTEM,
    ;

    companion object {
        /**
         * Classifies a notification type.
         *
         * @param type the server's type constant, e.g. `JOB_ORDER_CREATED`.
         * @return the matching kind, or [SYSTEM] when nothing matches.
         */
        fun from(type: String?): NotificationKind {
            val upper = type?.uppercase().orEmpty()
            return when {
                upper.startsWith("MISSION") || upper.startsWith("OPERATION") -> MISSION
                upper.startsWith("JOB_ORDER") || upper.startsWith("ORDER") -> ORDER
                upper.startsWith("BANK") -> BANK
                upper.startsWith("MATERIAL") -> EXCHANGE
                else -> SYSTEM
            }
        }
    }
}

/**
 * One notification, carried as a type and named parameters; the sentence is assembled on the
 * device from `notifications.type.<TYPE>`.
 *
 * @property id the notification's id
 * @property type the server's type constant, which selects the sentence
 * @property params the named values substituted into it; may name members, so it is never logged
 * @property entityType what the notification is about, e.g. `JOB_ORDER`; `null` when it is about
 *   nothing openable
 * @property entityId which one, `null` likewise
 * @property read whether the member has already read it
 * @property createdAt when it was raised, in UTC
 */
data class Notification(
    val id: String,
    val type: String,
    val params: Map<String, String>,
    val entityType: String?,
    val entityId: String?,
    val read: Boolean,
    val createdAt: Instant?,
) {
    /** Which icon the row draws. */
    val kind: NotificationKind get() = NotificationKind.from(type)
}

/**
 * One page of the inbox.
 *
 * [Page.rows] holds the rows on this page, newest first.
 */
typealias NotificationPage = Page<Notification>
