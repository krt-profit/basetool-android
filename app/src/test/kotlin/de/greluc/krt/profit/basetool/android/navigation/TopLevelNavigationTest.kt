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
 * The shell moves between the navigation bar's destinations through [navigateToTopLevel], whose
 * `popUpTo(start) { saveState } / launchSingleTop / restoreState` triple gives each tab its own back
 * stack. A screen that opens a *top-level* destination with a bare `navigate` instead pushes that
 * tab onto the **current** tab's stack, and the two schemes then disagree about where the member is.
 *
 * That is not theory. The dashboard's four Schnellaktionen opened Lager, Einsätze, Aufträge and the
 * Materialbörse with a bare `navigate`; afterwards "Übersicht" no longer returned to the dashboard,
 * and nothing short of killing the app got the member out (reported 2026-09-02).
 *
 * The graph here is deliberately tiny — two routes, no view models. What is under test is the
 * interaction between the two navigation styles; the real graph would only add setup the assertions
 * never read.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class TopLevelNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    /** Builds the two-route stand-in for the shell's graph, with Übersicht as the start. */
    private fun setUpGraph() {
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = HOME) {
                composable(HOME) { Text("home") }
                composable(INVENTORY) { Text("inventory") }
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
     * Why the rule exists, kept executable.
     *
     * This asserts the **broken** outcome on purpose: it is the behaviour that produced the
     * 2026-09-02 report, and pinning it means the rationale in [navigateToTopLevel] cannot quietly
     * become folklore. Should a Navigation release make a bare `navigate` survive this trip, this
     * test fails and someone re-reads that rule rather than inheriting it forever.
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
     * The call sites, guarded where the behavioural tests cannot reach.
     *
     * The graph wires around twenty screens and cannot be composed in a unit test, so the rule is
     * checked against the source: a route belonging to the navigation bar or the "Mehr" list must
     * never be handed to a bare `navigate(...)`. Options-carrying calls are exempt — those state
     * their own back-stack intent, which is what the `NotFound` screen's "back to base" does.
     */
    @Test
    fun `no top-level destination is opened with a bare navigate`() {
        val topLevel =
            (PHONE_DESTINATIONS + TABLET_DESTINATIONS + MORE_DESTINATIONS + KrtDestination.Notifications)
                .toSet()

        // Line by line, not a whole-file contains: a destination opened correctly in one place and
        // bare in another would otherwise exempt itself.
        val offenders =
            File(NAV_HOST).readLines().withIndex().flatMap { (index, line) ->
                topLevel
                    .filter { destination ->
                        val call = "navController.navigate(KrtDestination.${destination.name}.route)"
                        // A trailing " {" is the options block — such a call states its own
                        // back-stack intent and is exempt, which is how the NotFound screen's
                        // "back to base" reaches Übersicht without saveState/restoreState.
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

    private fun NavHostController.currentRoute(): String? = currentDestination?.route

    private companion object {
        const val HOME = "home"
        const val INVENTORY = "inventory"
        const val NAV_HOST =
            "src/main/kotlin/de/greluc/krt/profit/basetool/android/navigation/BasetoolNavHost.kt"
    }
}
