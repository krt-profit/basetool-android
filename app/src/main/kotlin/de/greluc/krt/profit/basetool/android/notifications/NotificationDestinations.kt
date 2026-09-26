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
 * Where a notification's subject lives in the app.
 *
 * Entity types with no screen in this build resolve to `null`, and their rows are drawn
 * unclickable.
 *
 * @param notification the notification whose subject is wanted.
 * @return the route to navigate to, or `null` when this build has no screen for it.
 */
fun notificationDestination(notification: Notification): String? =
    notificationDestination(
        entityType = notification.entityType,
        entityId = notification.entityId,
    )

/**
 * Resolves the destination from the entity type and id alone, shared by the inbox row and the
 * shade entry.
 *
 * @param entityType what the notification is about, e.g. `JOB_ORDER`.
 * @param entityId that thing's id.
 * @return the route to navigate to, or `null` when this build has no screen for it.
 */
fun notificationDestination(
    entityType: String?,
    entityId: String?,
): String? {
    val id = entityId ?: return null
    return when (entityType) {
        "JOB_ORDER" -> orderDetailRoute(id)
        "BANK_BOOKING_REQUEST" -> null
        "MATERIAL_EXCHANGE_OFFER", "MATERIAL_EXCHANGE_REQUEST" -> null
        "DISCORD_REGISTRATION" -> null
        else -> null
    }?.takeIf { id.isNotBlank() }
}
