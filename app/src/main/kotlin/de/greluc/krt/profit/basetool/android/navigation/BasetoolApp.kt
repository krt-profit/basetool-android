/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.bank.BankAccountViewModel
import de.greluc.krt.profit.basetool.android.bank.BankHolderViewModel
import de.greluc.krt.profit.basetool.android.bank.BankLifecycleViewModel
import de.greluc.krt.profit.basetool.android.bank.BankRequestsViewModel
import de.greluc.krt.profit.basetool.android.bank.BankStaffViewModel
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
import de.greluc.krt.profit.basetool.android.inventory.GameItemStockViewModel
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialDetailViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialMatrixViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialsViewModel
import de.greluc.krt.profit.basetool.android.materials.ProfitViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationFormViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.orders.MaterialDemandViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderCollectionViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderCreateViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderFormMode
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.orgunit.OrgUnitState
import de.greluc.krt.profit.basetool.android.orgunit.switcherLabel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalBlueprintsViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalInventoryViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryCreateViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryDetailViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryViewModel
import de.greluc.krt.profit.basetool.android.ui.CallerViewModel
import de.greluc.krt.profit.basetool.android.ui.LocalCaller
import de.greluc.krt.profit.basetool.android.ui.RootScrollSignals
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The application shell: top bar, navigation surface and the content of the active destination.
 *
 * Re-tapping the active destination pops it to its root, back from any root returns to Übersicht,
 * and back on Übersicht leaves the app. Per-destination back stacks survive tab switches.
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
 * @param bankRequests drives the Anträge tab and the request sheet.
 * @param bankStaff drives the Verwaltung scope.
 * @param bankLifecycle drives its Konten tab.
 * @param bankAccount builds a view model for one account.
 * @param bankHolder builds a view model for one holder's custody.
 * @param orders drives the Auftrag queue.
 * @param exchange drives the Materialbörse.
 * @param refinery drives the member's own Raffinerie orders.
 * @param refineryOrder builds a view model for one Raffinerie order.
 * @param refineryCreate builds the view model of the create form.
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
    bankRequests: BankRequestsViewModel,
    bankStaff: BankStaffViewModel,
    bankLifecycle: BankLifecycleViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    bankHolder: (String) -> BankHolderViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    exchange: MaterialBoardViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
    materials: MaterialsViewModel,
    materialDetail: (String) -> MaterialDetailViewModel,
    materialMatrix: () -> MaterialMatrixViewModel,
    materialProfit: () -> ProfitViewModel,
    refineryCreate: (String?) -> RefineryCreateViewModel,
    orderCreate: () -> OrderCreateViewModel,
    orderEdit: (String, OrderFormMode) -> OrderCreateViewModel,
    orderCollection: (String) -> OrderCollectionViewModel,
    operationForm: (String?) -> OperationFormViewModel,
    blueprints: BlueprintOverviewBindings,
    gameItems: () -> GameItemStockViewModel,
    materialDemand: () -> MaterialDemandViewModel,
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

    LaunchedEffect(Unit) { notifications.loadOnce() }
    val notificationState by notifications.state.collectAsStateWithLifecycle()
    val unreadCount = notificationState.unread.toInt()

    LifecycleResumeEffect(notifications) {
        notifications.onForeground()
        onPauseOrDispose { notifications.onBackground() }
    }

    UnknownLinkGuard(navController)

    val rootScroll = remember { RootScrollSignals() }

    val destinations = if (expanded) TABLET_DESTINATIONS else PHONE_DESTINATIONS
    val selectedRoute = selectedTopLevelRoute(root, destinations)

    val onSelect: (KrtNavItem) -> Unit = { item ->
        when {
            item.route == KrtDestination.More.route -> {
                navController.navigateToTopLevel(item.route, restoreState = false)
            }

            item.route == selectedRoute -> {
                navController.popBackStack(item.route, inclusive = false)
                rootScroll.request(item.route)
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
                label = stringResource(destination.navLabelRes),
                iconRes = destination.iconRes,
                badgeCount = null,
            )
        }

    BackHandler(enabled = current != KrtDestination.Home && navController.previousBackStackEntry == null) {
        navController.navigateToTopLevel(KrtDestination.Home.route)
    }

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
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                CompositionLocalProvider(
                    LocalScreenTopBar provides screenBar,
                    LocalCaller provides who,
                ) {
                    BasetoolNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        rootScroll = rootScroll,
                        onOpenDestination = { navController.navigateToTopLevel(it.route) },
                        onLogout = onLogout,
                        settings =
                            settings.copy(
                                orgUnitName =
                                    if (orgUnit.allChosen) {
                                        stringResource(R.string.org_switcher_all)
                                    } else {
                                        orgUnit.active?.name
                                    },
                                onSwitchOrgUnit = { orgSwitcherOpen = true },
                            ),
                        missions = missions,
                        missionDetail = missionDetail,
                        operations = operations,
                        operationDetail = operationDetail,
                        notifications = notifications,
                        dashboard = dashboard,
                        hangar = hangar,
                        fleetImport = fleetImport,
                        bank = bank,
                        bankRequests = bankRequests,
                        bankStaff = bankStaff,
                        bankLifecycle = bankLifecycle,
                        bankAccount = bankAccount,
                        bankHolder = bankHolder,
                        orders = orders,
                        orderDetail = orderDetail,
                        exchange = exchange,
                        refinery = refinery,
                        refineryOrder = refineryOrder,
                        materials = materials,
                        materialDetail = materialDetail,
                        materialMatrix = materialMatrix,
                        materialProfit = materialProfit,
                        refineryCreate = refineryCreate,
                        orderCreate = orderCreate,
                        orderEdit = orderEdit,
                        orderCollection = orderCollection,
                        operationForm = operationForm,
                        blueprints = blueprints,
                        gameItems = gameItems,
                        materialDemand = materialDemand,
                        inventory = inventory,
                        personalInventory = personalInventory,
                        personalBlueprints = personalBlueprints,
                        booking = booking,
                        memberName = settings.accountName,
                        orgUnitName = orgUnit.active?.name,
                    )
                }
            }
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
            KrtSheetOption(
                text =
                    stringResource(
                        if (who?.admin == true) {
                            R.string.org_switcher_all
                        } else {
                            R.string.org_switcher_all_mine
                        },
                    ),
                selected = orgUnit.allChosen,
                onClick = {
                    onSelectAllOrgUnits()
                    orgSwitcherOpen = false
                },
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
            }
        }
    }
}

/**
 * Maps the current destination onto the navigation item that should appear selected.
 *
 * A destination reached through „Mehr" keeps „Mehr" highlighted unless the form factor gives it
 * its own entry.
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
 * The bar above every screen — either the destination's own title, or a pushed screen's head.
 *
 * @param destination the active destination, for its static title and for the back arrow.
 * @param detail what a pushed screen published, or `null` on a root.
 * @param orgUnit the active org context, for the chip.
 * @param unreadCount unread notifications, for the bell's badge.
 * @param navigable whether the navigation itself offers this destination; decides who owns the
 *   bar's right-hand side.
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
    detail?.selection?.let { selecting ->
        KrtSelectionTopBar(
            label = pluralStringResource(R.plurals.inventory_selected, selecting.count, selecting.count),
            onClear = selecting.onClear,
            closeLabel = stringResource(R.string.inventory_selection_leave),
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
        return
    }
    val subject = detail?.title
    KrtTopBar(
        title = subject ?: stringResource(destination.titleRes),
        subject = subject != null,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        titleBadge = detail?.titleBadge,
        subtitle = detail?.subtitle,
        onBack = if (navigable) null else onBack,
        orgBadge =
            if (!navigable && !destination.orgScoped) {
                null
            } else {
                {
                    val label =
                        when {
                            orgUnit.allChosen -> stringResource(R.string.org_switcher_all_short)
                            else -> orgUnit.active?.name
                        }
                    label?.let { text ->
                        KrtOrgBadge(
                            text = text,
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

/**
 * Sends a `basetool://…` address the navigation graph does not match to the in-fiction 404 (design
 * ch. 03).
 *
 * The graph itself is asked whether the link resolves, rather than a separate route table.
 *
 * @param navController the graph to ask and, when it has no answer, to navigate.
 */
@Composable
private fun UnknownLinkGuard(navController: NavHostController) {
    val activity = LocalActivity.current
    LaunchedEffect(activity?.intent) {
        val link =
            activity?.intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
                ?: return@LaunchedEffect
        if (!navController.graph.hasDeepLink(link)) {
            navController.navigate(KrtDestination.NotFound.route)
        }
    }
}
