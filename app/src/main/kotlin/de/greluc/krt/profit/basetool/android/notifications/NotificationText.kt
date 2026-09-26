/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Notification

/** Opens a named placeholder such as `{displayId}`. */
private const val OPEN = '{'

/** Closes it. */
private const val CLOSE = '}'

/**
 * Whether a character may appear inside a placeholder's name.
 *
 * @return `true` for the letters, digits and underscore the server's parameter keys use.
 */
private fun Char.isPlaceholderName(): Boolean = isLetterOrDigit() || this == '_'

/**
 * Whether a brace's contents name a parameter: non-empty, starting with a letter or underscore, so
 * `{12}` stays literal.
 *
 * @return `true` when this is a parameter name rather than text that happens to sit in braces.
 */
private fun String.isPlaceholder(): Boolean =
    isNotEmpty() && (this[0].isLetter() || this[0] == '_') && all { it.isPlaceholderName() }

/**
 * The string resource that words a notification type, mirroring the web's `notifications.type.*`
 * keys; an unknown type gets the generic wording.
 *
 * @param type the server's type constant.
 * @return the resource id.
 */
internal fun notificationTypeRes(type: String): Int =
    when (type) {
        "JOB_ORDER_CREATED" -> {
            R.string.notifications_type_job_order_created
        }

        "JOB_ORDER_UPDATED_BY_REQUESTER" -> {
            R.string.notifications_type_job_order_updated
        }

        "BANK_BOOKING_REQUEST_CREATED" -> {
            R.string.notifications_type_bank_request_created
        }

        "BANK_BOOKING_REQUEST_CONFIRMED" -> {
            R.string.notifications_type_bank_request_confirmed
        }

        "BANK_BOOKING_REQUEST_REJECTED" -> {
            R.string.notifications_type_bank_request_rejected
        }

        "BANK_BOOKING_REQUEST_RESPONSIBLE_CONFIRMED" -> {
            R.string.notifications_type_bank_responsible_confirmed
        }

        "BANK_BOOKING_REQUEST_RESPONSIBLE_REJECTED" -> {
            R.string.notifications_type_bank_responsible_rejected
        }

        "DISCORD_REGISTRATION_PENDING" -> {
            R.string.notifications_type_registration_pending
        }

        "MATERIAL_EXCHANGE_INTEREST_REGISTERED" -> {
            R.string.notifications_type_exchange_interest
        }

        "MATERIAL_REQUEST_FULFILLMENT_SIGNALLED" -> {
            R.string.notifications_type_exchange_fulfilment
        }

        else -> {
            R.string.notifications_type_generic
        }
    }

/**
 * Fills a template's `{name}` placeholders with a notification's parameters.
 *
 * Uses a character scan rather than a regex, which Android's ICU engine would parse differently. A
 * brace that does not enclose a valid name is copied through; any unfilled placeholder makes the
 * result fall back to [fallback].
 *
 * @param template the resource text, containing `{name}` placeholders.
 * @param params the values to substitute.
 * @param fallback the generic wording, used when a placeholder cannot be filled.
 * @return the finished sentence.
 */
internal fun fillTemplate(
    template: String,
    params: Map<String, String>,
    fallback: String,
): String {
    val out = StringBuilder(template.length)
    var index = 0
    while (index < template.length) {
        val char = template[index]
        val close = if (char == OPEN) template.indexOf(CLOSE, index + 1) else -1
        val name = if (close > index) template.substring(index + 1, close) else ""
        if (name.isPlaceholder()) {
            val value = params[name]
            if (value.isNullOrBlank()) {
                return fallback
            }
            out.append(value)
            index = close + 1
        } else {
            out.append(char)
            index++
        }
    }
    return out.toString()
}

/**
 * Everything needed to word one notification, resolved outside a composable so it can be tested.
 *
 * @param notification the notification.
 * @param template the already-resolved template text for its type.
 * @param generic the already-resolved generic wording.
 * @return the sentence a member reads.
 */
internal fun notificationSentence(
    notification: Notification,
    template: String,
    generic: String,
): String = fillTemplate(template, notification.params, generic)
