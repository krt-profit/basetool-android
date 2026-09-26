/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.dashboard.QuickAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opening a top-level destination must not strand the member on it.
 *
 * Top-level destinations go through [navigateToTopLevel], which gives each tab its own back stack; a
 * bare `navigate` pushes the tab onto the current tab's stack. Uses a two-route graph.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class TopLevelNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    /**
     * Builds the stand-in for the shell's graph, with Übersicht as the start.
     *
     * @param extraRoutes destinations to register beside [HOME] and [INVENTORY]; the quick-action
     *   walk passes the real enum's routes so the test cannot drift from what the dashboard offers.
     */
    private fun setUpGraph(extraRoutes: List<String> = emptyList()) {
        val routes = (listOf(INVENTORY) + extraRoutes).distinct()
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = HOME) {
                composable(HOME) { Text("home") }
                routes.forEach { route -> composable(route) { Text(route) } }
            }
        }
        composeRule.waitForIdle()
    }

    /** The production path: both hops through the helper, and Übersicht comes back. */
    @Test
    fun `the navigation bar returns to the dashboard after a shortcut opened a tab`() {
        setUpGraph()

        composeRule.runOnUiThread { nav.navigateToTopLevel(INVENTORY) }
        composeRule.waitForIdle()
        assertEquals("the shortcut should have opened the Lager", INVENTORY, nav.currentRoute())

        composeRule.runOnUiThread { nav.navigateToTopLevel(HOME) }
        composeRule.waitForIdle()

        assertEquals(
            "tapping Übersicht must land on the dashboard, not leave the member in the Lager",
            HOME,
            nav.currentRoute(),
        )
    }

    /**
     * Asserts the stranding outcome of a bare `navigate` to a top-level destination, so a Navigation release that
     * changes it fails this test.
     */
    @Test
    fun `a bare navigate to a top-level destination strands the member on it`() {
        setUpGraph()

        composeRule.runOnUiThread { nav.navigate(INVENTORY) }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { nav.navigateToTopLevel(HOME) }
        composeRule.waitForIdle()

        assertEquals(
            "a bare navigate is expected to strand the member — that is why the helper exists",
            INVENTORY,
            nav.currentRoute(),
        )
    }

    /**
     * Checks the source so no navigation-bar or „Mehr" route is handed to a bare `navigate(...)`; calls carrying
     * options are exempt.
     */
    @Test
    fun `no top-level destination is opened with a bare navigate`() {
        val topLevel =
            (PHONE_DESTINATIONS + TABLET_DESTINATIONS + MORE_DESTINATIONS + KrtDestination.Notifications)
                .toSet()

        val offenders =
            File(NAV_HOST).readLines().withIndex().flatMap { (index, line) ->
                topLevel
                    .filter { destination ->
                        val call = "navController.navigate(KrtDestination.${destination.name}.route)"
                        line.contains(call) && !line.contains("$call {")
                    }
                    .map { "${it.name} at line ${index + 1}" }
            }

        assertTrue(
            "these top-level destinations are opened with a bare navigate in $NAV_HOST, which " +
                "strands the member on them — route them through navigateToTopLevel: " +
                offenders.joinToString(),
            offenders.isEmpty(),
        )
    }

    /**
     * Walks every dashboard Schnellaktion and back, including `Exchange`, a „Mehr" destination that returns home
     * differently.
     */
    @Test
    fun `every dashboard shortcut can be left again`() {
        val routes = QuickAction.entries.map { it.destination.route }
        setUpGraph(routes)

        QuickAction.entries.forEach { action ->
            val route = action.destination.route

            composeRule.runOnUiThread { nav.navigateToTopLevel(route) }
            composeRule.waitForIdle()
            assertEquals("$action should open ${action.destination}", route, nav.currentRoute())

            composeRule.runOnUiThread { nav.navigateToTopLevel(HOME) }
            composeRule.waitForIdle()
            assertEquals(
                "after $action, Übersicht must lead back to the dashboard",
                HOME,
                nav.currentRoute(),
            )
        }
    }

    private fun NavHostController.currentRoute(): String? = currentDestination?.route

    private companion object {
        const val HOME = "home"
        const val INVENTORY = "inventory"
        const val NAV_HOST =
            "src/main/kotlin/de/greluc/krt/profit/basetool/android/navigation/BasetoolNavHost.kt"
    }
}
