/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.core.data.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a notification leads.
 *
 * The rule this pins is the negative one: a row whose subject has no screen in this build must lead
 * **nowhere**, so the screen can draw it unclickable. A tap that silently does nothing is how a
 * member concludes the app is broken.
 */
class NotificationDestinationsTest {
    private fun notification(
        entityType: String?,
        entityId: String? = "e1",
    ) = Notification(
        id = "n1",
        type = "X",
        params = emptyMap(),
        entityType = entityType,
        entityId = entityId,
        read = false,
        createdAt = null,
    )

    @Test
    fun `a job order opens the order`() {
        assertEquals("order/e1", notificationDestination(notification("JOB_ORDER")))
    }

    @Test
    fun `a booking request leads nowhere, because this build has no approvals screen`() {
        // Sending a member to the account instead would answer a question they did not ask.
        assertNull(notificationDestination(notification("BANK_BOOKING_REQUEST")))
    }

    @Test
    fun `the areas this build does not have lead nowhere`() {
        listOf("MATERIAL_EXCHANGE_OFFER", "MATERIAL_EXCHANGE_REQUEST", "DISCORD_REGISTRATION")
            .forEach { assertNull(it, notificationDestination(notification(it))) }
    }

    @Test
    fun `an entity type this build has never seen leads nowhere`() {
        assertNull(notificationDestination(notification("SOMETHING_NEW")))
    }

    @Test
    fun `a notification about nothing leads nowhere`() {
        assertNull(notificationDestination(notification(entityType = null)))
        assertNull(notificationDestination(notification("JOB_ORDER", entityId = null)))
    }

    @Test
    fun `a blank id is treated as no id`() {
        assertNull(notificationDestination(notification("JOB_ORDER", entityId = "  ")))
    }
}
