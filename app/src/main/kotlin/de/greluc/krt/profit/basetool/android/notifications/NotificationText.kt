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
internal fun notificationTypeRes(type: String): Int = TYPE_WORDING[type] ?: R.string.notifications_type_generic

/** The wording of every type the backend raises, keyed by its constant. */
private val TYPE_WORDING: Map<String, Int> =
    mapOf(
        "JOB_ORDER_CREATED" to R.string.notifications_type_job_order_created,
        "JOB_ORDER_UPDATED_BY_REQUESTER" to R.string.notifications_type_job_order_updated,
        "BANK_BOOKING_REQUEST_CREATED" to R.string.notifications_type_bank_request_created,
        "BANK_BOOKING_REQUEST_CONFIRMED" to R.string.notifications_type_bank_request_confirmed,
        "BANK_BOOKING_REQUEST_REJECTED" to R.string.notifications_type_bank_request_rejected,
        "BANK_BOOKING_REQUEST_RESPONSIBLE_CONFIRMED" to R.string.notifications_type_bank_responsible_confirmed,
        "BANK_BOOKING_REQUEST_RESPONSIBLE_REJECTED" to R.string.notifications_type_bank_responsible_rejected,
        "DISCORD_REGISTRATION_PENDING" to R.string.notifications_type_registration_pending,
        "MATERIAL_EXCHANGE_INTEREST_REGISTERED" to R.string.notifications_type_exchange_interest,
        "MATERIAL_REQUEST_FULFILLMENT_SIGNALLED" to R.string.notifications_type_exchange_fulfilment,
        "ACCOUNT_DELETION_REQUESTED" to R.string.notifications_type_deletion_requested,
        "ACCOUNT_DELETION_REQUEST_DECLINED" to R.string.notifications_type_deletion_declined,
        "EXCHANGE_INSTALLATION_CONNECTED" to R.string.notifications_type_installation_connected,
        "EXCHANGE_BULK_UNDO_APPLIED" to R.string.notifications_type_bulk_undo_applied,
        "INVENTORY_TRANSFERRED_TO_USER" to R.string.notifications_type_inventory_transferred_to_user,
        "INVENTORY_TRANSFERRED_FROM_USER" to R.string.notifications_type_inventory_transferred_from_user,
        "BANK_BOOKING_REQUEST_UPDATED" to R.string.notifications_type_bank_request_updated,
        "BANK_ACCOUNT_RESPONSIBLE_ASSIGNED" to R.string.notifications_type_bank_responsible_assigned,
        "MISSION_RESCHEDULED" to R.string.notifications_type_mission_rescheduled,
        "MISSION_CANCELLED" to R.string.notifications_type_mission_cancelled,
        "MISSION_DELETED" to R.string.notifications_type_mission_deleted,
        "MISSION_REMINDER" to R.string.notifications_type_mission_reminder,
        "MISSION_CHECKIN_OPEN" to R.string.notifications_type_mission_checkin_open,
        "MISSION_PARTICIPANT_ADDED_BY_OTHER" to R.string.notifications_type_mission_participant_added_by_other,
        "MISSION_PARTICIPANT_REMOVED_BY_OTHER" to R.string.notifications_type_mission_participant_removed_by_other,
        "MISSION_PARTICIPANT_LEFT" to R.string.notifications_type_mission_participant_left,
        "MISSION_NEVER_ENDED" to R.string.notifications_type_mission_never_ended,
        "MISSION_RESPONSIBILITY_ASSIGNED" to R.string.notifications_type_mission_responsibility_assigned,
        "OPERATION_PAYOUT_PAID_OUT" to R.string.notifications_type_operation_payout_paid_out,
        "OPERATION_COMPLETED" to R.string.notifications_type_operation_completed,
        "JOB_ORDER_REASSIGNED" to R.string.notifications_type_job_order_reassigned,
        "JOB_ORDER_FINISHED" to R.string.notifications_type_job_order_finished,
        "JOB_ORDER_ASSIGNED" to R.string.notifications_type_job_order_assigned,
        "JOB_ORDER_CLAIM_WITHDRAWN" to R.string.notifications_type_job_order_claim_withdrawn,
        "REFINERY_ORDER_READY" to R.string.notifications_type_refinery_order_ready,
        "REFINERY_ORDER_CHANGED_BY_OTHER" to R.string.notifications_type_refinery_order_changed_by_other,
        "MATERIAL_EXCHANGE_OFFER_UNAVAILABLE" to R.string.notifications_type_material_exchange_offer_unavailable,
        "MATERIAL_REQUEST_UNAVAILABLE" to R.string.notifications_type_material_request_unavailable,
        "INVENTORY_BOOKED_OUT_BY_OTHER" to R.string.notifications_type_inventory_booked_out_by_other,
        "BANK_BOOKING_REQUEST_APPROVED" to R.string.notifications_type_bank_booking_request_approved,
        "BANK_GRANT_CHANGED" to R.string.notifications_type_bank_grant_changed,
        "BANK_GRANT_REVOKED" to R.string.notifications_type_bank_grant_revoked,
        "BANK_PAYOUT_RECEIVED" to R.string.notifications_type_bank_payout_received,
        "BANK_HOLDER_TRANSFER_RECEIVED" to R.string.notifications_type_bank_holder_transfer_received,
        "BANK_ACCOUNT_DEBITED" to R.string.notifications_type_bank_account_debited,
        "BANK_HOLDER_DEACTIVATED_WITH_BALANCE" to R.string.notifications_type_bank_holder_deactivated_with_balance,
        "ORG_LEADERSHIP_ROLE_MISMATCH" to R.string.notifications_type_org_leadership_role_mismatch,
        "ORG_MEMBER_DEPARTED" to R.string.notifications_type_org_member_departed,
        "HANGAR_SHIP_ASSIGNED" to R.string.notifications_type_hangar_ship_assigned,
        "HANGAR_SHIP_REMOVED_FROM_UNIT" to R.string.notifications_type_hangar_ship_removed_from_unit,
        "HANGAR_FITTED_RESET" to R.string.notifications_type_hangar_fitted_reset,
        "HANGAR_CHANGED_BY_ADMIN" to R.string.notifications_type_hangar_changed_by_admin,
        "BLUEPRINT_CHANGED_BY_ADMIN" to R.string.notifications_type_blueprint_changed_by_admin,
        "BLUEPRINT_PURGED_BY_ADMIN" to R.string.notifications_type_blueprint_purged_by_admin,
        "EXCHANGE_CLIENT_SUSPENDED" to R.string.notifications_type_exchange_client_suspended,
        "EXCHANGE_CLIENT_ACTIVATED" to R.string.notifications_type_exchange_client_activated,
        "EXCHANGE_CLIENT_UPDATE_REQUIRED" to R.string.notifications_type_exchange_client_update_required,
        "EXCHANGE_CLIENT_CAPABILITY_REMOVED" to R.string.notifications_type_exchange_client_capability_removed,
        "EXCHANGE_SWITCHED_OFF" to R.string.notifications_type_exchange_switched_off,
    )

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
 * @param words the localized words of the coded parameters, keyed `name.VALUE`.
 * @return the sentence a member reads.
 */
internal fun notificationSentence(
    notification: Notification,
    template: String,
    generic: String,
    words: Map<String, String> = emptyMap(),
): String = fillTemplate(template, withValueWords(notification.params, words), generic)
