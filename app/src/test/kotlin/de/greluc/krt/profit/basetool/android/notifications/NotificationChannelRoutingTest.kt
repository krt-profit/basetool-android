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
 * Which of design chapter 14's five channels a push is filed under.
 *
 * The chapter's point is that a member can silence one kind and keep another, which only works if
 * the five are actually distinct in practice — a mapping that collapsed two kinds onto one channel
 * would look right in the settings list and silence more than the member asked for.
 *
 * The classification itself is `NotificationKind`, which the inbox already uses to pick a row's
 * glyph. One classification, two uses; these tests pin that the channel mapping does not quietly
 * grow a second opinion.
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
     * The shade entry and the inbox row must agree on where a notification leads.
     *
     * They resolve it through the same function now; this pins that the pair-taking overload the
     * shade uses answers what the row-taking one does.
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
