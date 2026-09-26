/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests who owns the top bar's two ends, decided by the single predicate `destination in destinations` on each form
 * factor.
 *
 * A navigation destination shows the org chip and the bell; a pushed screen shows a back arrow, its
 * title and only what it owns.
 */
class TopBarOwnershipTest {
    /** The bottom bar's five: Übersicht, Einsätze, Aufträge, Lager, Mehr. */
    private val phoneNavItems = 5

    private val pushedOnEveryFormFactor =
        listOf(
            KrtDestination.Notifications,
            KrtDestination.Settings,
            KrtDestination.Licenses,
            KrtDestination.FleetImport,
            KrtDestination.MissionDetail,
            KrtDestination.OperationDetail,
            KrtDestination.BankAccount,
            KrtDestination.OrderDetail,
            KrtDestination.RefineryOrder,
        )

    @Test
    fun `a phone offers five destinations and pushes everything else`() {
        assertEquals(phoneNavItems, PHONE_DESTINATIONS.size)
        pushedOnEveryFormFactor.forEach { destination ->
            assertFalse(
                "$destination must be pushed on a phone, so its bar keeps the back arrow and " +
                    "drops the org chip and the bell",
                destination in PHONE_DESTINATIONS,
            )
        }
    }

    @Test
    fun `a tablet offers the three areas a phone hides behind Mehr`() {
        listOf(KrtDestination.Hangar, KrtDestination.Refinery, KrtDestination.Exchange)
            .forEach { destination ->
                assertTrue(
                    "$destination has its own rail entry on a tablet, so there it is a root",
                    destination in TABLET_DESTINATIONS,
                )
                assertFalse(
                    "$destination is reached from the Mehr menu on a phone, so there it is pushed",
                    destination in PHONE_DESTINATIONS,
                )
            }
    }

    /**
     * The inbox is the sharpest case: its bar would carry a bell pointing at the screen it is on.
     */
    @Test
    fun `the inbox is never a navigable destination`() {
        assertFalse(KrtDestination.Notifications in PHONE_DESTINATIONS)
        assertFalse(KrtDestination.Notifications in TABLET_DESTINATIONS)
    }

    /** A destination the navigation offers is never also a sub-destination of another. */
    @Test
    fun `no destination is both offered and pushed`() {
        (PHONE_DESTINATIONS + TABLET_DESTINATIONS).forEach { destination ->
            assertFalse(
                "$destination cannot be both a navigation entry and something pushed onto one",
                destination in SUB_DESTINATIONS,
            )
        }
    }
}
