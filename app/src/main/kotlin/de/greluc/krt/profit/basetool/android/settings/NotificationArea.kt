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
    /** Einsätze. */
    MISSIONS(R.string.notification_area_missions),

    /** Operationen. */
    OPERATIONS(R.string.notification_area_operations),

    /** Job orders. */
    ORDERS(R.string.notification_area_orders),

    /** The Raffinerie. */
    REFINERY(R.string.notification_area_refinery),

    /** The Kartellbank. */
    BANK(R.string.notification_area_bank),

    /** The Materialbörse. */
    MARKET(R.string.notification_area_market),

    /** The Lager. */
    INVENTORY(R.string.notification_area_inventory),

    /** The Hangar. */
    HANGAR(R.string.notification_area_hangar),

    /** Blueprints. */
    BLUEPRINTS(R.string.notification_area_blueprints),

    /** Leadership and membership. */
    ORGANISATION(R.string.notification_area_organisation),

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
                "MISSION_" to MISSIONS,
                "OPERATION_" to OPERATIONS,
                "REFINERY_" to REFINERY,
                "ORG_" to ORGANISATION,
                "HANGAR_" to HANGAR,
                "BLUEPRINT_" to BLUEPRINTS,
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
        "MISSION_RESCHEDULED" to R.string.notification_pref_mission_rescheduled,
        "MISSION_CANCELLED" to R.string.notification_pref_mission_cancelled,
        "MISSION_DELETED" to R.string.notification_pref_mission_deleted,
        "MISSION_REMINDER" to R.string.notification_pref_mission_reminder,
        "MISSION_CHECKIN_OPEN" to R.string.notification_pref_mission_checkin_open,
        "MISSION_PARTICIPANT_ADDED_BY_OTHER" to R.string.notification_pref_mission_participant_added_by_other,
        "MISSION_PARTICIPANT_REMOVED_BY_OTHER" to R.string.notification_pref_mission_participant_removed_by_other,
        "MISSION_PARTICIPANT_LEFT" to R.string.notification_pref_mission_participant_left,
        "MISSION_NEVER_ENDED" to R.string.notification_pref_mission_never_ended,
        "MISSION_RESPONSIBILITY_ASSIGNED" to R.string.notification_pref_mission_responsibility_assigned,
        "OPERATION_PAYOUT_PAID_OUT" to R.string.notification_pref_operation_payout_paid_out,
        "OPERATION_COMPLETED" to R.string.notification_pref_operation_completed,
        "JOB_ORDER_REASSIGNED" to R.string.notification_pref_job_order_reassigned,
        "JOB_ORDER_FINISHED" to R.string.notification_pref_job_order_finished,
        "JOB_ORDER_ASSIGNED" to R.string.notification_pref_job_order_assigned,
        "JOB_ORDER_CLAIM_WITHDRAWN" to R.string.notification_pref_job_order_claim_withdrawn,
        "REFINERY_ORDER_READY" to R.string.notification_pref_refinery_order_ready,
        "REFINERY_ORDER_CHANGED_BY_OTHER" to R.string.notification_pref_refinery_order_changed_by_other,
        "MATERIAL_EXCHANGE_OFFER_UNAVAILABLE" to R.string.notification_pref_material_exchange_offer_unavailable,
        "MATERIAL_REQUEST_UNAVAILABLE" to R.string.notification_pref_material_request_unavailable,
        "INVENTORY_BOOKED_OUT_BY_OTHER" to R.string.notification_pref_inventory_booked_out_by_other,
        "BANK_BOOKING_REQUEST_APPROVED" to R.string.notification_pref_bank_booking_request_approved,
        "BANK_GRANT_CHANGED" to R.string.notification_pref_bank_grant_changed,
        "BANK_GRANT_REVOKED" to R.string.notification_pref_bank_grant_revoked,
        "BANK_PAYOUT_RECEIVED" to R.string.notification_pref_bank_payout_received,
        "BANK_HOLDER_TRANSFER_RECEIVED" to R.string.notification_pref_bank_holder_transfer_received,
        "BANK_ACCOUNT_DEBITED" to R.string.notification_pref_bank_account_debited,
        "BANK_HOLDER_DEACTIVATED_WITH_BALANCE" to R.string.notification_pref_bank_holder_deactivated_with_balance,
        "ORG_LEADERSHIP_ROLE_MISMATCH" to R.string.notification_pref_org_leadership_role_mismatch,
        "ORG_MEMBER_DEPARTED" to R.string.notification_pref_org_member_departed,
        "HANGAR_SHIP_ASSIGNED" to R.string.notification_pref_hangar_ship_assigned,
        "HANGAR_SHIP_REMOVED_FROM_UNIT" to R.string.notification_pref_hangar_ship_removed_from_unit,
        "HANGAR_FITTED_RESET" to R.string.notification_pref_hangar_fitted_reset,
        "HANGAR_CHANGED_BY_ADMIN" to R.string.notification_pref_hangar_changed_by_admin,
        "BLUEPRINT_CHANGED_BY_ADMIN" to R.string.notification_pref_blueprint_changed_by_admin,
        "BLUEPRINT_PURGED_BY_ADMIN" to R.string.notification_pref_blueprint_purged_by_admin,
        "EXCHANGE_CLIENT_SUSPENDED" to R.string.notification_pref_exchange_client_suspended,
        "EXCHANGE_CLIENT_ACTIVATED" to R.string.notification_pref_exchange_client_activated,
        "EXCHANGE_CLIENT_UPDATE_REQUIRED" to R.string.notification_pref_exchange_client_update_required,
        "EXCHANGE_CLIENT_CAPABILITY_REMOVED" to R.string.notification_pref_exchange_client_capability_removed,
        "EXCHANGE_SWITCHED_OFF" to R.string.notification_pref_exchange_switched_off,
    )
