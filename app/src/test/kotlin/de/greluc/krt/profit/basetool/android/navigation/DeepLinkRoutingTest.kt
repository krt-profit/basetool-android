/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What the graph's addresses must satisfy for design chapter 03's deep-link rules to hold.
 *
 * The rules themselves — a known link opens its screen, an unknown one reaches the in-fiction 404 —
 * are verified on a device, because they are decided by Navigation's matcher rather than by
 * anything this project owns. This pins the two properties the app *does* own and that a future
 * change could quietly break.
 */
class DeepLinkRoutingTest {
    @Test
    fun `every destination has its own address`() {
        val duplicates =
            KrtDestination.entries
                .groupBy { it.deepLink }
                .filterValues { it.size > 1 }
                .keys

        assertEquals(
            "two destinations answering one link means whichever the matcher prefers wins, silently",
            emptySet<String>(),
            duplicates,
        )
    }

    /**
     * The 404 is somewhere a link lands, never somewhere a member navigates.
     *
     * It carries in-fiction failure copy. Reaching it by tapping would tell a member something
     * failed when nothing did — and it is the reason the placeholder screen exists separately.
     */
    @Test
    fun `the not-found screen is reachable only by a link`() {
        assertFalse("not a bar destination", KrtDestination.NotFound in PHONE_DESTINATIONS)
        assertFalse("not a rail destination", KrtDestination.NotFound in TABLET_DESTINATIONS)
        assertFalse("not in the Mehr list", KrtDestination.NotFound in MORE_DESTINATIONS)
    }
}
