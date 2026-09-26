/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.navigation.NavHostController

/**
 * Navigates to a top-level destination with `saveState`, `restoreState` and `launchSingleTop`,
 * preserving each destination's own back stack.
 *
 * Every route in [PHONE_DESTINATIONS], [TABLET_DESTINATIONS] and [MORE_DESTINATIONS] must be opened
 * through here; sub-screens use a plain `navigate`.
 *
 * @param route the destination route.
 * @param restoreState whether to return to where this tab was left; `false` lands on the tab's own
 *   root.
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
