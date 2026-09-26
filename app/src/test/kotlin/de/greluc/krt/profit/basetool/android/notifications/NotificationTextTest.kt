/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Notification
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests how a stored notification's `type` and named values become a localised sentence, including unknown types and
 * renamed parameters.
 */
class NotificationTextTest {
    private companion object {
        const val GENERIC = "Neue Benachrichtigung"
        const val ORDER_TEMPLATE = "Neuer Auftrag #{displayId} für {orgUnit}"
    }

    private fun notification(
        type: String = "JOB_ORDER_CREATED",
        params: Map<String, String> = emptyMap(),
    ) = Notification(
        id = "n1",
        type = type,
        params = params,
        entityType = null,
        entityId = null,
        read = false,
        createdAt = null,
    )

    @Test
    fun `named placeholders are filled from the server's map`() {
        val sentence =
            notificationSentence(
                notification = notification(params = mapOf("displayId" to "1042", "orgUnit" to "Staffel 1")),
                template = ORDER_TEMPLATE,
                generic = GENERIC,
            )

        assertEquals("Neuer Auftrag #1042 für Staffel 1", sentence)
    }

    @Test
    fun `a renamed parameter falls back to the generic wording`() {
        val sentence =
            notificationSentence(
                notification = notification(params = mapOf("orderId" to "1042", "orgUnit" to "Staffel 1")),
                template = ORDER_TEMPLATE,
                generic = GENERIC,
            )

        assertEquals(GENERIC, sentence)
    }

    @Test
    fun `a blank value counts as missing`() {
        val sentence =
            notificationSentence(
                notification = notification(params = mapOf("displayId" to "", "orgUnit" to "Staffel 1")),
                template = ORDER_TEMPLATE,
                generic = GENERIC,
            )

        assertEquals(GENERIC, sentence)
    }

    @Test
    fun `a template without placeholders is returned as it is`() {
        val sentence =
            notificationSentence(
                notification = notification(type = "SOMETHING_NEW"),
                template = GENERIC,
                generic = GENERIC,
            )

        assertEquals(GENERIC, sentence)
    }

    @Test
    fun `a literal brace in the wording is not a placeholder`() {
        assertEquals(
            "Fertig {}",
            notificationSentence(notification(), "Fertig {}", GENERIC),
        )
        assertEquals(
            "50 % von {12}",
            notificationSentence(notification(), "50 % von {12}", GENERIC),
        )
    }

    @Test
    fun `an unclosed brace is text, not a swallowed sentence`() {
        assertEquals(
            "Neuer Auftrag #{displayId",
            notificationSentence(notification(), "Neuer Auftrag #{displayId", GENERIC),
        )
    }

    @Test
    fun `two placeholders in a row are both filled`() {
        assertEquals(
            "1042/Staffel 1",
            notificationSentence(
                notification(params = mapOf("displayId" to "1042", "orgUnit" to "Staffel 1")),
                "{displayId}/{orgUnit}",
                GENERIC,
            ),
        )
    }

    @Test
    fun `an unknown type resolves to the generic resource`() {
        assertEquals(R.string.notifications_type_generic, notificationTypeRes("SOMETHING_NEW"))
        assertEquals(R.string.notifications_type_generic, notificationTypeRes(""))
    }

    @Test
    fun `every type the backend raises today has its own wording`() {
        val known =
            listOf(
                "JOB_ORDER_CREATED",
                "JOB_ORDER_UPDATED_BY_REQUESTER",
                "BANK_BOOKING_REQUEST_CREATED",
                "BANK_BOOKING_REQUEST_CONFIRMED",
                "BANK_BOOKING_REQUEST_REJECTED",
                "BANK_BOOKING_REQUEST_RESPONSIBLE_CONFIRMED",
                "BANK_BOOKING_REQUEST_RESPONSIBLE_REJECTED",
                "DISCORD_REGISTRATION_PENDING",
                "MATERIAL_EXCHANGE_INTEREST_REGISTERED",
                "MATERIAL_REQUEST_FULFILLMENT_SIGNALLED",
            )

        known.forEach { type ->
            assertEquals(
                "no wording for $type",
                false,
                notificationTypeRes(type) == R.string.notifications_type_generic,
            )
        }
    }
}
