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
    fun `an Einsatz, an Operation and a Raffinerie order open their screens`() {
        assertEquals("mission/e1", notificationDestination(notification("MISSION")))
        assertEquals("operation/e1", notificationDestination(notification("OPERATION")))
        assertEquals("refinery-order/e1", notificationDestination(notification("REFINERY_ORDER")))
    }

    @Test
    fun `a hangar notice opens the hangar and a unit notice leads nowhere`() {
        assertEquals("hangar", notificationDestination(notification("HANGAR")))
        assertNull(notificationDestination(notification("MISSION_UNIT")))
    }

    @Test
    fun `a lager transfer opens the lager`() {
        assertEquals("inventory", notificationDestination(notification("INVENTORY_ITEM")))
        assertNull(notificationDestination(notification("INVENTORY_ITEM", entityId = null)))
    }

    @Test
    fun `a booking request leads nowhere, because this build has no approvals screen`() {
        assertNull(notificationDestination(notification("BANK_BOOKING_REQUEST")))
    }

    @Test
    fun `an account responsibility notice leads nowhere, like every bank notice on the web`() {
        assertNull(notificationDestination(notification("BANK_ACCOUNT")))
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
