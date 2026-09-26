/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.core.data.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests which of design chapter 14's five channels a push is filed under, keeping each kind distinct and the mapping
 * consistent with `NotificationKind`.
 */
class NotificationChannelRoutingTest {
    @Test
    fun `every kind has its own channel`() {
        val channels = NotificationKind.entries.map { KrtNotificationChannels.channelFor(it) }

        assertEquals(
            "two kinds sharing a channel means one switch silences both",
            NotificationKind.entries.size,
            channels.toSet().size,
        )
    }

    @Test
    fun `the kinds the design names high map to their own channels`() {
        assertEquals(
            KrtNotificationChannels.CHANNEL_MISSIONS,
            KrtNotificationChannels.channelFor(NotificationKind.MISSION),
        )
        assertEquals(
            KrtNotificationChannels.CHANNEL_ORDERS,
            KrtNotificationChannels.channelFor(NotificationKind.ORDER),
        )
    }

    /**
     * A push this build cannot classify still lands somewhere a member can find it.
     *
     * The server adds notification rules without asking the app, and the payload degrades to a bare
     * refresh on several paths — both produce a kind of `SYSTEM`, which is where "something
     * happened and this build cannot say what" belongs.
     */
    @Test
    fun `an unknown or absent type lands on the system channel`() {
        assertEquals(
            KrtNotificationChannels.CHANNEL_SYSTEM,
            KrtNotificationChannels.channelFor(NotificationKind.from(null)),
        )
        assertEquals(
            KrtNotificationChannels.CHANNEL_SYSTEM,
            KrtNotificationChannels.channelFor(NotificationKind.from("SOMETHING_NEW_ENTIRELY")),
        )
    }

    /**
     * The shade entry and the inbox row resolve a notification to the same destination.
     */
    @Test
    fun `the shade and the inbox resolve the same destination`() {
        val id = "7f000001-0000-0000-0000-000000000001"

        val fromPair = notificationDestination(entityType = "JOB_ORDER", entityId = id)

        assertTrue("an Auftrag has a screen in this build", fromPair != null)
        assertEquals(null, notificationDestination(entityType = "JOB_ORDER", entityId = null))
        assertEquals(
            "an entity with no screen must not invent a route",
            null,
            notificationDestination(entityType = "DISCORD_REGISTRATION", entityId = id),
        )
    }
}
