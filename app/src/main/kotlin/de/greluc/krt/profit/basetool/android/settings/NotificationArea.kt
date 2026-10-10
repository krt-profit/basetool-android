/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.annotation.StringRes
import de.greluc.krt.profit.basetool.android.R

/**
 * The area a notification type is listed under, taken from the type name's prefix exactly as the web
 * profile card does (`NotificationPreferenceGroups`).
 *
 * @property label string resource of the area's heading.
 */
enum class NotificationArea(
    @StringRes val label: Int,
) {
    /** Job orders. */
    ORDERS(R.string.notification_area_orders),

    /** The Kartellbank. */
    BANK(R.string.notification_area_bank),

    /** The Materialbörse. */
    MARKET(R.string.notification_area_market),

    /** The Lager. */
    INVENTORY(R.string.notification_area_inventory),

    /** Connected applications. */
    CONNECTED_APPS(R.string.notification_area_connected_apps),

    /** Account and administration. */
    ACCOUNT(R.string.notification_area_account),

    /** Any type no prefix claims, including one this build has never seen. */
    OTHER(R.string.notification_area_other),
    ;

    companion object {
        private val PREFIXES: List<Pair<String, NotificationArea>> =
            listOf(
                "JOB_ORDER_" to ORDERS,
                "BANK_" to BANK,
                "MATERIAL_" to MARKET,
                "INVENTORY_" to INVENTORY,
                "EXCHANGE_" to CONNECTED_APPS,
                "DISCORD_" to ACCOUNT,
                "ACCOUNT_DELETION_" to ACCOUNT,
            )

        /**
         * Sorts a notification type into its area.
         *
         * @param type the server's type constant.
         * @return the area its prefix names, [OTHER] when none does.
         */
        fun of(type: String): NotificationArea = PREFIXES.firstOrNull { type.startsWith(it.first) }?.second ?: OTHER
    }
}

/**
 * The label of a notification type on its switch, mirroring the web's
 * `profile.notifications.type.*` keys.
 *
 * @param type the server's type constant.
 * @return the label resource, or `null` for a type this build has no wording for, which the caller
 *   shows with [R.string.notification_pref_unknown].
 */
@StringRes
internal fun notificationPreferenceLabelRes(type: String): Int? = LABELS[type]

/** The label of every type the backend raises, keyed by its constant. */
private val LABELS: Map<String, Int> =
    mapOf(
        "JOB_ORDER_CREATED" to R.string.notification_pref_job_order_created,
        "JOB_ORDER_UPDATED_BY_REQUESTER" to R.string.notification_pref_job_order_updated_by_requester,
        "BANK_BOOKING_REQUEST_CREATED" to R.string.notification_pref_bank_booking_request_created,
        "BANK_BOOKING_REQUEST_CONFIRMED" to R.string.notification_pref_bank_booking_request_confirmed,
        "BANK_BOOKING_REQUEST_REJECTED" to R.string.notification_pref_bank_booking_request_rejected,
        "BANK_BOOKING_REQUEST_RESPONSIBLE_CONFIRMED" to
            R.string.notification_pref_bank_booking_request_responsible_confirmed,
        "BANK_BOOKING_REQUEST_RESPONSIBLE_REJECTED" to
            R.string.notification_pref_bank_booking_request_responsible_rejected,
        "BANK_BOOKING_REQUEST_UPDATED" to R.string.notification_pref_bank_booking_request_updated,
        "BANK_ACCOUNT_RESPONSIBLE_ASSIGNED" to R.string.notification_pref_bank_account_responsible_assigned,
        "DISCORD_REGISTRATION_PENDING" to R.string.notification_pref_discord_registration_pending,
        "MATERIAL_EXCHANGE_INTEREST_REGISTERED" to R.string.notification_pref_material_exchange_interest_registered,
        "MATERIAL_REQUEST_FULFILLMENT_SIGNALLED" to R.string.notification_pref_material_request_fulfillment_signalled,
        "ACCOUNT_DELETION_REQUESTED" to R.string.notification_pref_account_deletion_requested,
        "ACCOUNT_DELETION_REQUEST_DECLINED" to R.string.notification_pref_account_deletion_request_declined,
        "EXCHANGE_INSTALLATION_CONNECTED" to R.string.notification_pref_exchange_installation_connected,
        "EXCHANGE_BULK_UNDO_APPLIED" to R.string.notification_pref_exchange_bulk_undo_applied,
        "INVENTORY_TRANSFERRED_TO_USER" to R.string.notification_pref_inventory_transferred_to_user,
        "INVENTORY_TRANSFERRED_FROM_USER" to R.string.notification_pref_inventory_transferred_from_user,
    )
