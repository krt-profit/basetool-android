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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.bank.BankAccountViewModel
import de.greluc.krt.profit.basetool.android.bank.BankViewModel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtNavItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtNavigationRail
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectionTopBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSheetOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTopBar
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.dashboard.DashboardViewModel
import de.greluc.krt.profit.basetool.android.exchange.MaterialBoardViewModel
import de.greluc.krt.profit.basetool.android.hangar.FleetImportViewModel
import de.greluc.krt.profit.basetool.android.hangar.HangarViewModel
import de.greluc.krt.profit.basetool.android.inventory.BookingViewModel
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.orgunit.OrgUnitState
import de.greluc.krt.profit.basetool.android.orgunit.switcherLabel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalBlueprintsViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalInventoryViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryDetailViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryViewModel
import de.greluc.krt.profit.basetool.android.ui.CallerViewModel
import de.greluc.krt.profit.basetool.android.ui.LocalCaller
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

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
 * @param caller who is signed in, for every screen that decides whether to offer an action.
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
 * @param exchange drives the Materialbörse.
 * @param refinery drives the member's own Raffinerie orders.
 * @param refineryOrder builds a view model for one Raffinerie order.
 * @param orderDetail builds a view model for one order.
 * @param inventory drives the Lager tree.
 * @param orgUnit the member's org units and the one currently active.
 * @param onSelectOrgUnit pins the chosen org unit; every later request carries it.
 * @param onSelectAllOrgUnits drops the pin, so requests go out unscoped and the backend answers
 *   with the union of the member's own units.
 * @param modifier layout modifier.
 * @param navController the controller driving the graph; injected for tests and previews.
 */
@Composable
fun BasetoolApp(
    onLogout: () -> Unit,
    settings: SettingsBindings,
    caller: CallerViewModel,
    missions: MissionsViewModel,
    missionDetail: (String) -> MissionDetailViewModel,
    operations: OperationsViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
    notifications: NotificationsViewModel,
    dashboard: DashboardViewModel,
    hangar: HangarViewModel,
    fleetImport: FleetImportViewModel,
    bank: BankViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    exchange: MaterialBoardViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
    inventory: InventoryViewModel,
    personalInventory: PersonalInventoryViewModel,
    personalBlueprints: PersonalBlueprintsViewModel,
    booking: BookingViewModel,
    orgUnit: OrgUnitState,
    onSelectOrgUnit: (String) -> Unit,
    onSelectAllOrgUnits: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val current = destinationOf(currentRoute) ?: KrtDestination.Home
    val root = rootOf(current)
    val expanded = isWideWindow()
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
        when {
            // "Mehr" is a menu, not a place. Every tap on it shows the menu — restoring the tab's
            // saved state instead would land a member back on whatever secondary screen they left
            // (Bank, Hangar, the licence register), and the one control that is supposed to get
            // them OUT of a secondary screen would look broken. The other tabs keep their state,
            // because those are places and coming back to where you were is the point.
            item.route == KrtDestination.More.route -> {
                navController.navigateToTopLevel(item.route, restoreState = false)
            }

            // Re-tapping the active destination goes back to that destination's own root.
            item.route == selectedRoute -> {
                navController.popBackStack(item.route, inclusive = false)
            }

            else -> {
                navController.navigateToTopLevel(item.route)
            }
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

    // What a pushed screen has published for the bar, if anything. A detail owns its head:
    // chapters 06/10/11/12 all put the subject's own name there rather than its category.
    val screenBar = remember { mutableStateOf<ScreenTopBar?>(null) }
    val who by caller.caller.collectAsStateWithLifecycle()
    val detail = screenBar.value

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
            AppTopBar(
                destination = current,
                detail = detail,
                navigable = current in destinations,
                orgUnit = orgUnit,
                unreadCount = unreadCount,
                onBack = { navController.popBackStack() },
                onSwitchOrg = { orgSwitcherOpen = true },
                onNotifications = { navController.navigateToTopLevel(KrtDestination.Notifications.route) },
            )
            // The content column caps at 1200 dp and centres; the top and bottom chrome keep
            // spanning the full width. Foundations ch. 01 § 5 asks for exactly this, and the
            // token existed while nothing applied it — on a 1280 dp tablet every list ran edge
            // to edge, which is the readability problem the cap is there to prevent.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                CompositionLocalProvider(
                    LocalScreenTopBar provides screenBar,
                    LocalCaller provides who,
                ) {
                    BasetoolNavHost(
                        modifier = Modifier.widthIn(max = KrtSpacing.contentMax).fillMaxSize(),
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
                        fleetImport = fleetImport,
                        bank = bank,
                        bankAccount = bankAccount,
                        orders = orders,
                        orderDetail = orderDetail,
                        exchange = exchange,
                        refinery = refinery,
                        refineryOrder = refineryOrder,
                        inventory = inventory,
                        personalInventory = personalInventory,
                        personalBlueprints = personalBlueprints,
                        booking = booking,
                        memberName = settings.accountName,
                        orgUnitName = orgUnit.active?.name,
                    )
                }
            }
            // While a selection runs, the foot of the screen belongs to its action bar — the
            // navigation would offer a way out that silently drops what was picked (design ch. 09,
            // artboard 5: „FAB und Bottom-Nav weichen der Aktionsleiste").
            if (!expanded && detail?.selection == null) {
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
                    text = unit.switcherLabel(),
                    selected = unit.id == orgUnit.activeId,
                    onClick = {
                        onSelectOrgUnit(unit.id)
                        orgSwitcherOpen = false
                    },
                )
            }
            // The row the app was missing: no pin at all, which the backend answers with the union
            // of the member's own units — never a unit they do not belong to (design ch. 02,
            // artboard 7, verified in docs/TENANCY_VERIFICATION.md).
            KrtSheetOption(
                text = stringResource(R.string.org_switcher_all),
                selected = orgUnit.allChosen,
                onClick = {
                    onSelectAllOrgUnits()
                    orgSwitcherOpen = false
                },
            )
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
 * @param restoreState whether to come back to where this tab was left. `false` lands on the tab's
 *   own root instead — what "Mehr" wants, since a menu that reopens on the page you were trying to
 *   leave is not a menu.
 */
private fun NavHostController.navigateToTopLevel(
    route: String,
    restoreState: Boolean = true,
) {
    val target = route
    navigate(target) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        this.restoreState = restoreState
    }
}

/**
 * The bar above every screen — either the destination's own title, or a pushed screen's head.
 *
 * Its own composable because assembling it is three decisions (which title, whether the org chip
 * belongs there, whether the bell does) and folding them into `BasetoolApp` pushed that function
 * past detekt's complexity cap. The cap was right: the bar is a thing, not a detail of the shell.
 *
 * @param destination the active destination, for its static title and for the back arrow.
 * @param detail what a pushed screen published, or `null` on a root.
 * @param orgUnit the active org context, for the chip.
 * @param unreadCount unread notifications, for the bell's badge.
 * @param navigable whether the navigation itself offers this destination — the bottom bar's five on
 *   a phone, the rail's eight on a tablet. It decides who owns the bar's right-hand side.
 * @param onBack pops the back stack.
 * @param onSwitchOrg opens the org switcher.
 * @param onNotifications opens the inbox.
 */
@Composable
private fun AppTopBar(
    destination: KrtDestination,
    detail: ScreenTopBar?,
    orgUnit: OrgUnitState,
    unreadCount: Int?,
    navigable: Boolean,
    onBack: () -> Unit,
    onSwitchOrg: () -> Unit,
    onNotifications: () -> Unit,
) {
    // A running selection replaces the bar outright rather than decorating it: while a member is
    // picking rows, the org chip and the bell offer a change of subject they did not ask for
    // (design ch. 09, artboard 5). Checked first, because it outranks both other shapes.
    detail?.selection?.let { selecting ->
        KrtSelectionTopBar(
            label = pluralStringResource(R.plurals.inventory_selected, selecting.count, selecting.count),
            onClear = selecting.onClear,
            closeLabel = stringResource(R.string.inventory_selection_leave),
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
        return
    }
    // A published TITLE always names a thing; a destination title always names a section. A screen
    // that publishes only actions — the Hangar's overflow — keeps its section bar, badge and bell.
    val subject = detail?.title
    KrtTopBar(
        title = subject ?: stringResource(destination.titleRes),
        subject = subject != null,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        subtitle = detail?.subtitle,
        // The back arrow and the right-hand side answer the same question and used to be asked
        // differently: the arrow from the destination, the chip and the bell from whether a screen
        // happened to publish a title. So every pushed screen that publishes none — the inbox, the
        // settings, the licences, the Fleetview import, and everything reached from „Mehr" — got a
        // back arrow AND the chip AND the bell. Artboards 07.1, 08.4, 13.1 and 15.1 all draw the
        // same head: back arrow, title, and whatever that screen owns on the right. Nothing else.
        onBack = if (navigable) null else onBack,
        // The org chip and the bell are for choosing what to look at, so they belong to the
        // destinations the navigation itself offers. On anything pushed they compete with the thing
        // being looked at — and on the inbox the bell would point at the screen it is on.
        orgBadge =
            if (!navigable) {
                null
            } else {
                {
                    // No badge at all until the scope is known. A placeholder would be a
                    // claim about which unit the member is acting in, and the header that
                    // scopes every request would disagree with it. "All units" is a known
                    // scope, not an unknown one, so it gets a badge of its own — dropping it
                    // there would read as "no scope resolved" for a scope the member chose.
                    // „Alle Einheiten" is the component sheet's own badge value (ch. 02 §3, which
                    // lists it beside „Bereich Profit" and „SK VANGUARD"), not a short form invented
                    // here for a chip that had to fit.
                    val label =
                        when {
                            orgUnit.allChosen -> stringResource(R.string.org_switcher_all_short)
                            else -> orgUnit.active?.name
                        }
                    label?.let { text ->
                        KrtOrgBadge(
                            text = text,
                            // Not tappable with a single membership: the sheet would offer
                            // the choice the member is already in. Same rule as the web
                            // sidebar.
                            onClick =
                                if (orgUnit.switchable) onSwitchOrg else null,
                        )
                    }
                }
            },
        notificationCount = unreadCount.takeIf { navigable },
        onNotificationsClick = onNotifications,
        actions = detail?.actions,
    )
}
