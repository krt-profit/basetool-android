/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtNavItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtNavigationRail
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSheetOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTopBar
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Whether the current window is wide enough for the navigation rail and list-detail layouts.
 *
 * The expanded breakpoint (840 dp) is where a tablet in landscape sits; below it the app uses the
 * bottom bar. Orientation is never locked above 600 dp — Android 16 ignores such requests anyway —
 * so this recomposes when the window changes and the shell swaps without losing state.
 *
 * @return `true` for expanded and wider windows.
 */
@Composable
private fun isExpandedWindow(): Boolean =
    currentWindowAdaptiveInfoV2()
        .windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * The application shell: top bar, navigation surface and the content of the active destination.
 *
 * Implements the back rules of the design spec, which are behavioural, not cosmetic:
 * re-tapping the active destination pops it to its root, back from any destination root returns to
 * Übersicht, and back on Übersicht leaves the app — there is deliberately no "press again to exit"
 * toast. Per-destination back stacks are preserved through `saveState`/`restoreState`, so switching
 * tabs and coming back keeps the scroll position and the open detail.
 *
 * @param modifier layout modifier.
 * @param navController the controller driving the graph; injected for tests and previews.
 */
@Composable
fun BasetoolApp(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    appLockEnabled: Boolean = false,
    appLockAvailable: Boolean = true,
    onAppLockChange: (Boolean) -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val current = destinationOf(currentRoute) ?: KrtDestination.Home
    val expanded = isExpandedWindow()
    var orgSwitcherOpen by rememberSaveable { mutableStateOf(false) }

    // TODO(feature:auth): the active org unit and the unread count come from the backend once
    // core:network and core:auth land; until then the shell renders with representative values.
    val activeOrgUnit = "Bereich Profit"
    val unreadCount = 3

    val destinations = if (expanded) TABLET_DESTINATIONS else PHONE_DESTINATIONS
    val selectedRoute = selectedTopLevelRoute(current, destinations)

    val onSelect: (KrtNavItem) -> Unit = { item ->
        if (item.route == selectedRoute) {
            // Re-tapping the active destination pops it back to its own root.
            navController.popBackStack(item.route, inclusive = false)
        } else {
            navController.navigateToTopLevel(item.route)
        }
    }

    val navItems =
        destinations.map { destination ->
            KrtNavItem(
                route = destination.route,
                label = destination.title,
                iconRes = destination.iconRes,
                badgeCount = if (destination == KrtDestination.Missions) 2 else null,
            )
        }

    // Back on a destination root returns to Übersicht; back on Übersicht falls through to the
    // system, which finishes the activity.
    BackHandler(enabled = current != KrtDestination.Home && navController.previousBackStackEntry == null) {
        navController.navigateToTopLevel(KrtDestination.Home.route)
    }

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (expanded) {
            KrtNavigationRail(
                items = navItems,
                selectedRoute = selectedRoute,
                onSelect = onSelect,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                footer = {
                    KrtIconButton(
                        iconRes = DesignR.drawable.ic_krt_gear,
                        label = KrtDestination.Settings.title,
                        onClick = { navController.navigateToTopLevel(KrtDestination.Settings.route) },
                    )
                },
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            KrtTopBar(
                title = current.title,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                onBack = if (isDetailRoute(current)) ({ navController.popBackStack() }) else null,
                orgBadge = {
                    KrtOrgBadge(text = activeOrgUnit, onClick = { orgSwitcherOpen = true })
                },
                notificationCount = unreadCount,
                onNotificationsClick = { navController.navigateToTopLevel(KrtDestination.Notifications.route) },
            )
            Box(modifier = Modifier.weight(1f)) {
                BasetoolNavHost(
                    navController = navController,
                    onOpenDestination = { navController.navigateToTopLevel(it.route) },
                    onLogout = onLogout,
                    appLockEnabled = appLockEnabled,
                    appLockAvailable = appLockAvailable,
                    onAppLockChange = onAppLockChange,
                )
            }
            if (!expanded) {
                KrtBottomBar(
                    items = navItems,
                    selectedRoute = selectedRoute,
                    onSelect = onSelect,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
    }

    if (orgSwitcherOpen) {
        KrtBottomSheet(
            onDismiss = { orgSwitcherOpen = false },
            title = "Aktive Org-Einheit",
        ) {
            // TODO(feature:auth): replace with the member's pickable org units from the backend.
            listOf("Bereich Profit", "SK VANGUARD", "Alle Org-Einheiten").forEach { unit ->
                KrtSheetOption(
                    text = unit,
                    selected = unit == activeOrgUnit,
                    onClick = { orgSwitcherOpen = false },
                )
            }
        }
    }
}

/**
 * Maps the current destination onto the navigation item that should appear selected.
 *
 * A destination reached through "Mehr" (Bank, Beförderung, …) keeps "Mehr" highlighted on a phone,
 * so the bar never claims the user is somewhere they are not. On a tablet the same destination may
 * have its own rail entry, in which case that one lights up instead.
 *
 * @param current the active destination.
 * @param destinations the navigation items of the current form factor.
 * @return the route to render as selected.
 */
private fun selectedTopLevelRoute(
    current: KrtDestination,
    destinations: List<KrtDestination>,
): String =
    when {
        destinations.contains(current) -> current.route
        MORE_DESTINATIONS.contains(current) -> KrtDestination.More.route
        else -> KrtDestination.Home.route
    }

/**
 * Whether a destination is a pushed detail rather than a navigation root.
 *
 * Only pushed screens get the back arrow in the top bar — on a root the arrow would suggest a
 * hierarchy that does not exist.
 *
 * @param destination the destination to classify.
 * @return `true` when the top bar should show the back arrow.
 */
private fun isDetailRoute(destination: KrtDestination): Boolean =
    destination == KrtDestination.Notifications || destination == KrtDestination.Settings

/**
 * Navigates to a top-level destination, preserving each destination's own back stack.
 *
 * `saveState`/`restoreState` are what make list scroll positions and open details survive a trip
 * through another tab, and `launchSingleTop` keeps repeated navigation from stacking duplicates.
 *
 * @param route the destination route.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
