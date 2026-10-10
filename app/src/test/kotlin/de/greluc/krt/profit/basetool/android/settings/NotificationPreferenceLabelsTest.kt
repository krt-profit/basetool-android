/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import de.greluc.krt.profit.basetool.android.core.contract.model.NotificationPreferenceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests that every notification type in the vendored contract has an area and a label, and that the
 * areas follow the type-name prefixes the web profile card uses.
 */
class NotificationPreferenceLabelsTest {
    /**
     * The types come from the vendored contract's preference enum, so refreshing `openapi.json` with
     * a new type fails here until the type has a label and an area.
     */
    @Test
    fun `every type the contract lists has a label`() {
        val known = NotificationPreferenceDto.Type.entries.map { it.value }

        known.forEach { type ->
            assertNotNull("no label for $type", notificationPreferenceLabelRes(type))
        }
    }

    @Test
    fun `every type the contract lists has an area of its own`() {
        val known = NotificationPreferenceDto.Type.entries.map { it.value }

        known.forEach { type ->
            assertNotEquals("no area for $type", NotificationArea.OTHER, NotificationArea.of(type))
        }
    }

    @Test
    fun `a type no prefix claims lands under Weitere without a label`() {
        assertEquals(NotificationArea.OTHER, NotificationArea.of("SOMETHING_THE_SERVER_ADDED_LATER"))
        assertNull(notificationPreferenceLabelRes("SOMETHING_THE_SERVER_ADDED_LATER"))
        assertEquals(NotificationArea.OTHER, NotificationArea.of(""))
    }

    @Test
    fun `the areas follow the prefixes of the web card`() {
        val expected =
            mapOf(
                "JOB_ORDER_CREATED" to NotificationArea.ORDERS,
                "BANK_BOOKING_REQUEST_CREATED" to NotificationArea.BANK,
                "BANK_ACCOUNT_RESPONSIBLE_ASSIGNED" to NotificationArea.BANK,
                "MATERIAL_REQUEST_FULFILLMENT_SIGNALLED" to NotificationArea.MARKET,
                "INVENTORY_TRANSFERRED_TO_USER" to NotificationArea.INVENTORY,
                "EXCHANGE_INSTALLATION_CONNECTED" to NotificationArea.CONNECTED_APPS,
                "EXCHANGE_BULK_UNDO_APPLIED" to NotificationArea.CONNECTED_APPS,
                "DISCORD_REGISTRATION_PENDING" to NotificationArea.ACCOUNT,
                "MISSION_REMINDER" to NotificationArea.MISSIONS,
                "OPERATION_COMPLETED" to NotificationArea.OPERATIONS,
                "REFINERY_ORDER_READY" to NotificationArea.REFINERY,
                "ORG_MEMBER_DEPARTED" to NotificationArea.ORGANISATION,
                "HANGAR_SHIP_ASSIGNED" to NotificationArea.HANGAR,
                "BLUEPRINT_PURGED_BY_ADMIN" to NotificationArea.BLUEPRINTS,
                "ACCOUNT_DELETION_REQUESTED" to NotificationArea.ACCOUNT,
                "ACCOUNT_DELETION_REQUEST_DECLINED" to NotificationArea.ACCOUNT,
            )

        expected.forEach { (type, area) -> assertEquals(type, area, NotificationArea.of(type)) }
    }

    @Test
    fun `no two types share a label`() {
        val labels = NotificationPreferenceDto.Type.entries.map { notificationPreferenceLabelRes(it.value) }

        assertEquals("two types would read the same on the switch", labels.size, labels.toSet().size)
    }
}
