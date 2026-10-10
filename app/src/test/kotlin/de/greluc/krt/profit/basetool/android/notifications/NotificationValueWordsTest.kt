/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.core.data.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests how a coded parameter such as `changeCode` becomes the word the sentence needs. */
class NotificationValueWordsTest {
    private val words = mapOf("change.UPDATED" to "geändert", "reason.role_lost" to "keine Rolle mehr")

    private fun notification(params: Map<String, String>) =
        Notification(
            id = "n1",
            type = "REFINERY_ORDER_CHANGED_BY_OTHER",
            params = params,
            entityType = null,
            entityId = null,
            read = false,
            createdAt = null,
        )

    @Test
    fun `a coded parameter gives its name the localized word`() {
        val params = withValueWords(mapOf("changeCode" to "UPDATED", "actor" to "Ada"), words)

        assertEquals("geändert", params["change"])
        assertEquals("Ada", params["actor"])
        assertEquals("UPDATED", params["changeCode"])
    }

    @Test
    fun `a value without a word is shown as it is`() {
        assertEquals("NEW_THING", withValueWords(mapOf("changeCode" to "NEW_THING"), words)["change"])
    }

    @Test
    fun `a name the server already sent is kept`() {
        val params = withValueWords(mapOf("changeCode" to "UPDATED", "change" to "vom Server"), words)

        assertEquals("vom Server", params["change"])
    }

    @Test
    fun `parameters without a coded one are returned untouched`() {
        val params = mapOf("actor" to "Ada", "Code" to "X")

        assertSame(params, withValueWords(params, words))
    }

    @Test
    fun `a lower case code value finds its word`() {
        assertEquals("keine Rolle mehr", withValueWords(mapOf("reasonCode" to "role_lost"), words)["reason"])
    }

    @Test
    fun `the sentence reads the word where the template names the plain parameter`() {
        val sentence =
            notificationSentence(
                notification = notification(mapOf("order" to "ab12cd34", "actor" to "Ada", "changeCode" to "UPDATED")),
                template = "Auftrag {order}: {actor} hat ihn {change}.",
                generic = "Neue Benachrichtigung",
                words = words,
            )

        assertEquals("Auftrag ab12cd34: Ada hat ihn geändert.", sentence)
    }

    @Test
    fun `a sentence whose coded parameter is missing falls back to the generic wording`() {
        val sentence =
            notificationSentence(
                notification = notification(mapOf("order" to "ab12cd34", "actor" to "Ada")),
                template = "Auftrag {order}: {actor} hat ihn {change}.",
                generic = "Neue Benachrichtigung",
                words = words,
            )

        assertEquals("Neue Benachrichtigung", sentence)
    }

    @Test
    fun `every word key names a parameter and a value`() {
        assertTrue(VALUE_WORDS.isNotEmpty())
        VALUE_WORDS.keys.forEach { key ->
            val parts = key.split(".")
            assertEquals("malformed key $key", 2, parts.size)
            assertTrue("empty part in $key", parts.all { it.isNotBlank() })
        }
    }
}
