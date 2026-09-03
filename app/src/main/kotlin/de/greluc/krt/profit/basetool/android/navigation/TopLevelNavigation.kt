/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.navigation.NavHostController

/**
 * Navigates to a top-level destination, preserving each destination's own back stack.
 *
 * `saveState`/`restoreState` are what make list scroll positions and open details survive a trip
 * through another tab, and `launchSingleTop` keeps repeated navigation from stacking duplicates.
 *
 * > **Every** route in [PHONE_DESTINATIONS], [TABLET_DESTINATIONS] and [MORE_DESTINATIONS] is opened
 * > through here, wherever the tap comes from — the navigation bar, the "Mehr" list, the bell, or a
 * > shortcut on another screen. That is not a style preference. A top-level destination pushed with
 * > a bare `navigate` lands on the *current* tab's back stack, and the two schemes then disagree
 * > about where the member is: the navigation bar restores the saved stack it finds, which is the
 * > screen the member is trying to leave. The dashboard's four Schnellaktionen did exactly that, and
 * > "Übersicht" stopped returning to the dashboard until the member killed the app (2026-09-02).
 * > `TopLevelNavigationTest` holds the line.
 *
 * A *sub*-screen — a mission detail, an order's edit form, the fleet import — is a different case
 * and still belongs on the current tab's stack, so those keep their plain `navigate`.
 *
 * @param route the destination route.
 * @param restoreState whether to come back to where this tab was left. `false` lands on the tab's
 *   own root instead — what "Mehr" wants, since a menu that reopens on the page you were trying to
 *   leave is not a menu.
 */
internal fun NavHostController.navigateToTopLevel(
    route: String,
    restoreState: Boolean = true,
) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        this.restoreState = restoreState
    }
}
