/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Notification

/**
 * Matches a named placeholder such as `{displayId}`.
 *
 * Named, not positional, because the server's parameter map is named and the two bundles order the
 * values differently — "Auftrag #{displayId} für {orgUnit}" and its English twin do not put them in
 * the same places, and a positional format would silently swap them.
 */
private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+)}""")

/**
 * The string resource that words a notification type.
 *
 * Mirrors the web app's `notifications.type.*` keys one for one. A type this build has never seen
 * falls to the generic wording — the server may add a notification rule at any time, and a member
 * must still be told that something happened.
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
 * Fills a template with a notification's parameters.
 *
 * **An unfilled placeholder falls back to the generic wording.** The alternative — printing
 * "Neuer Auftrag #{displayId}" with the braces showing — is a sentence that looks like a bug to the
 * member and hides which notification it was. This happens when the server renames a parameter,
 * which no schema check can catch: the contract freezes that `params` exists, not what is in it.
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
    var complete = true
    val filled =
        PLACEHOLDER.replace(template) { match ->
            val value = params[match.groupValues[1]]
            if (value.isNullOrBlank()) {
                complete = false
                match.value
            } else {
                value
            }
        }
    return if (complete) filled else fallback
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
