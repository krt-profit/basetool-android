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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.bank.BankAccountViewModel
import de.greluc.krt.profit.basetool.android.bank.BankViewModel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtNavItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtNavigationRail
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSheetOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTopBar
import de.greluc.krt.profit.basetool.android.dashboard.DashboardViewModel
import de.greluc.krt.profit.basetool.android.hangar.HangarViewModel
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.orgunit.OrgUnitState
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
 * @param onLogout ends the session; the caller opens the realm's end-session URL.
 * @param settings what the Einstellungen screen needs from the activity.
 * @param missions drives the Einsatz list.
 * @param missionDetail builds a view model for one Einsatz.
 * @param operations drives the Operationen list.
 * @param operationDetail builds a view model for one Operation.
 * @param notifications drives the inbox and the bell badge.
 * @param dashboard drives the Übersicht.
 * @param hangar drives the Hangar.
 * @param bank drives the Konten list.
 * @param bankAccount builds a view model for one account.
 * @param orders drives the Auftrag queue.
 * @param orderDetail builds a view model for one order.
 * @param inventory drives the Lager tree.
 * @param orgUnit the member's org units and the one currently active.
 * @param onSelectOrgUnit pins the chosen org unit; every later request carries it.
 * @param modifier layout modifier.
 * @param navController the controller driving the graph; injected for tests and previews.
 */
@Composable
fun BasetoolApp(
    onLogout: () -> Unit,
    settings: SettingsBindings,
    missions: MissionsViewModel,
    missionDetail: (String) -> MissionDetailViewModel,
    operations: OperationsViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
    notifications: NotificationsViewModel,
    dashboard: DashboardViewModel,
    hangar: HangarViewModel,
    bank: BankViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    inventory: InventoryViewModel,
    orgUnit: OrgUnitState,
    onSelectOrgUnit: (String) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val current = destinationOf(currentRoute) ?: KrtDestination.Home
    val root = rootOf(current)
    val expanded = isExpandedWindow()
    var orgSwitcherOpen by rememberSaveable { mutableStateOf(false) }

    // The badge and the inbox read one state, so they cannot disagree — a member seeing "3 neu"
    // over a list whose top rows are already read has been told something false by the app itself.
    val notificationState by notifications.state.collectAsStateWithLifecycle()
    val unreadCount = notificationState.unread.toInt()

    // The push stream and the poll behind the badge run only while the app is in the foreground.
    // Holding a socket open for a screen nobody is looking at spends the member's battery to learn
    // something they cannot see.
    LifecycleResumeEffect(notifications) {
        notifications.onForeground()
        onPauseOrDispose { notifications.onBackground() }
    }

    val destinations = if (expanded) TABLET_DESTINATIONS else PHONE_DESTINATIONS
    val selectedRoute = selectedTopLevelRoute(root, destinations)

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
                label = stringResource(destination.titleRes),
                iconRes = destination.iconRes,
                // No badge on any navigation entry. The Einsätze one carried a hardcoded 2 from
                // the shell — a permanent claim that two of something were waiting, which no
                // endpoint backs and which a device run found still on screen. The one real count
                // in the app is the unread one, and it lives on the bell in the top bar; nothing in
                // the API offers a "pending Einsätze" figure for this one to show instead.
                badgeCount = null,
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
                        label = stringResource(KrtDestination.Settings.titleRes),
                        onClick = { navController.navigateToTopLevel(KrtDestination.Settings.route) },
                    )
                },
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            KrtTopBar(
                title = stringResource(current.titleRes),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                onBack = if (isDetailRoute(current)) ({ navController.popBackStack() }) else null,
                orgBadge = {
                    // No badge at all until the scope is known. A placeholder would be a claim
                    // about which unit the member is acting in, and the header that scopes every
                    // request would disagree with it.
                    orgUnit.active?.let { active ->
                        KrtOrgBadge(
                            text = active.name,
                            // Not tappable with a single membership: the sheet would offer the
                            // choice the member is already in. Same rule as the web sidebar.
                            onClick = if (orgUnit.switchable) ({ orgSwitcherOpen = true }) else null,
                        )
                    }
                },
                notificationCount = unreadCount,
                onNotificationsClick = { navController.navigateToTopLevel(KrtDestination.Notifications.route) },
            )
            Box(modifier = Modifier.weight(1f)) {
                BasetoolNavHost(
                    navController = navController,
                    onOpenDestination = { navController.navigateToTopLevel(it.route) },
                    onLogout = onLogout,
                    settings = settings,
                    missions = missions,
                    missionDetail = missionDetail,
                    operations = operations,
                    operationDetail = operationDetail,
                    notifications = notifications,
                    dashboard = dashboard,
                    hangar = hangar,
                    bank = bank,
                    bankAccount = bankAccount,
                    orders = orders,
                    orderDetail = orderDetail,
                    inventory = inventory,
                    memberName = settings.accountName,
                    orgUnitName = orgUnit.active?.name,
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
            title = stringResource(R.string.org_switcher_title),
        ) {
            orgUnit.units.forEach { unit ->
                KrtSheetOption(
                    text = unit.name,
                    selected = unit.id == orgUnit.activeId,
                    onClick = {
                        onSelectOrgUnit(unit.id)
                        orgSwitcherOpen = false
                    },
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
 * @param current the active destination, already resolved to its navigation root.
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
    destination == KrtDestination.Notifications ||
        destination == KrtDestination.Settings ||
        destination in SUB_DESTINATIONS

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
