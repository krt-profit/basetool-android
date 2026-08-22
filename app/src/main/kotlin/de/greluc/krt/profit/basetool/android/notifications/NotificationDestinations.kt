/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.navigation.orderDetailRoute

/**
 * Where a notification's subject lives in the app, when it lives anywhere yet.
 *
 * The backend raises notifications for five entity types. `JOB_ORDER` now opens the order — the
 * Aufträge slice gave it a screen. The other four still have none: a bank booking **request** is
 * not the account screen this build has, the Materialbörse arrives in phase 4, and the registration
 * queue is admin work that stays on the web permanently.
 *
 * Returning `null` is the honest answer for those, and the row is drawn unclickable rather than
 * swallowing the tap. A control that reacts to nothing is worse than one that does not offer
 * itself: the member repeats the tap, and concludes the app is broken rather than that the screen
 * does not exist.
 *
 * @param notification the notification whose subject is wanted.
 * @return the route to navigate to, or `null` when this build has no screen for it.
 */
fun notificationDestination(notification: Notification): String? {
    val id = notification.entityId ?: return null
    return when (notification.entityType) {
        // Filled in as each area's read-only screen lands. Written as an exhaustive-looking `when`
        // on purpose: the next slice adds a line here rather than discovering the mapping is
        // missing from a member's bug report.
        "JOB_ORDER" -> orderDetailRoute(id)

        // NOT the account screen: the notification is about a booking *request*, which is the
        // approvals surface this build does not have. Sending a member to the account instead
        // would answer a question they did not ask.
        "BANK_BOOKING_REQUEST" -> null

        "MATERIAL_EXCHANGE_OFFER", "MATERIAL_EXCHANGE_REQUEST" -> null

        "DISCORD_REGISTRATION" -> null

        else -> null
    }?.takeIf { id.isNotBlank() }
}
