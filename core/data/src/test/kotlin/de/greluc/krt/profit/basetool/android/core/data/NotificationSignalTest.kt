/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the server's `notification` event, including every degraded payload that must still
 * read as "something changed" rather than an error.
 */
class NotificationSignalTest {
    @Test
    fun `a full signal is read`() {
        val signal =
            NotificationSignal.parse(
                """
                {"type":"JOB_ORDER_CREATED","entityType":"JOB_ORDER",
                 "entityId":"7f000001-0000-0000-0000-000000000001",
                 "params":{"displayId":"1042"}}
                """.trimIndent(),
            )

        assertTrue(signal.describesNotification)
        assertEquals("JOB_ORDER_CREATED", signal.type)
        assertEquals("JOB_ORDER", signal.entityType)
        assertEquals("7f000001-0000-0000-0000-000000000001", signal.entityId)
        assertEquals(mapOf("displayId" to "1042"), signal.params)
    }

    @Test
    fun `the historic payload reads as a bare refresh`() {
        val signal = NotificationSignal.parse("new")

        assertFalse(signal.describesNotification)
        assertEquals(NotificationSignal.refreshOnly(), signal)
    }

    @Test
    fun `an unreadable payload reads as a bare refresh rather than failing`() {
        for (payload in listOf("", "   ", "{", "{\"type\":", "null", "[]")) {
            assertFalse(
                "payload <$payload> must degrade, not throw",
                NotificationSignal.parse(payload).describesNotification,
            )
        }
    }

    /**
     * A signal from a newer server.
     *
     * Fields this build does not know must not make the whole payload unreadable — the server adds
     * notification rules without asking the app first, and the kind and the entity are what this
     * app needs from it.
     */
    @Test
    fun `unknown fields do not cost the known ones`() {
        val signal =
            NotificationSignal.parse(
                """{"type":"BANK_BOOKING_REQUEST_CREATED","priority":"HIGH","entityType":"BANK"}""",
            )

        assertEquals("BANK_BOOKING_REQUEST_CREATED", signal.type)
        assertEquals("BANK", signal.entityType)
    }

    @Test
    fun `a blank type is no type`() {
        val signal = NotificationSignal.parse("""{"type":"","entityType":""}""")

        assertFalse(signal.describesNotification)
        assertEquals(null, signal.entityType)
    }
}
