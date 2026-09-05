/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import de.greluc.krt.profit.basetool.android.bank.BankAccountRoute
import de.greluc.krt.profit.basetool.android.bank.BankAccountViewModel
import de.greluc.krt.profit.basetool.android.bank.BankAccountsRoute
import de.greluc.krt.profit.basetool.android.bank.BankHolderRoute
import de.greluc.krt.profit.basetool.android.bank.BankHolderViewModel
import de.greluc.krt.profit.basetool.android.bank.BankLifecycleViewModel
import de.greluc.krt.profit.basetool.android.bank.BankRequestsViewModel
import de.greluc.krt.profit.basetool.android.bank.BankStaffViewModel
import de.greluc.krt.profit.basetool.android.bank.BankViewModel
import de.greluc.krt.profit.basetool.android.core.data.PayoutPreference
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.dashboard.DashboardScreen
import de.greluc.krt.profit.basetool.android.dashboard.DashboardViewModel
import de.greluc.krt.profit.basetool.android.dashboard.QuickAction
import de.greluc.krt.profit.basetool.android.exchange.MaterialBoardRoute
import de.greluc.krt.profit.basetool.android.exchange.MaterialBoardViewModel
import de.greluc.krt.profit.basetool.android.hangar.FleetImportRoute
import de.greluc.krt.profit.basetool.android.hangar.FleetImportViewModel
import de.greluc.krt.profit.basetool.android.hangar.HangarRoute
import de.greluc.krt.profit.basetool.android.hangar.HangarViewModel
import de.greluc.krt.profit.basetool.android.inventory.BookingHost
import de.greluc.krt.profit.basetool.android.inventory.BookingMode
import de.greluc.krt.profit.basetool.android.inventory.BookingViewModel
import de.greluc.krt.profit.basetool.android.inventory.GameItemStockRoute
import de.greluc.krt.profit.basetool.android.inventory.GameItemStockViewModel
import de.greluc.krt.profit.basetool.android.inventory.InventoryRoute
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialDetailRoute
import de.greluc.krt.profit.basetool.android.materials.MaterialDetailViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialMatrixRoute
import de.greluc.krt.profit.basetool.android.materials.MaterialMatrixViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialsRoute
import de.greluc.krt.profit.basetool.android.materials.MaterialsViewModel
import de.greluc.krt.profit.basetool.android.materials.ProfitRoute
import de.greluc.krt.profit.basetool.android.materials.ProfitViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailRoute
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsRoute
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailRoute
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationFormRoute
import de.greluc.krt.profit.basetool.android.missions.OperationFormViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsRoute
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.NotificationsPhase
import de.greluc.krt.profit.basetool.android.notifications.NotificationsRoute
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.notificationDestination
import de.greluc.krt.profit.basetool.android.orders.MaterialDemandRoute
import de.greluc.krt.profit.basetool.android.orders.MaterialDemandViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderCollectionRoute
import de.greluc.krt.profit.basetool.android.orders.OrderCollectionViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderCreateRoute
import de.greluc.krt.profit.basetool.android.orders.OrderCreateViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderDetailRoute
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderFormMode
import de.greluc.krt.profit.basetool.android.orders.OrdersRoute
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.BlueprintOverviewRoute
import de.greluc.krt.profit.basetool.android.personalinventory.BlueprintOverviewViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.MeinInventarRoute
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalBlueprintsViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalInventoryViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryCreateRoute
import de.greluc.krt.profit.basetool.android.refinery.RefineryCreateViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryDetailViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryOrderDetailRoute
import de.greluc.krt.profit.basetool.android.refinery.RefineryOrdersRoute
import de.greluc.krt.profit.basetool.android.refinery.RefineryViewModel
import de.greluc.krt.profit.basetool.android.settings.AppLanguage
import de.greluc.krt.profit.basetool.android.settings.LicensesScreen
import de.greluc.krt.profit.basetool.android.settings.MemberPreferencesState
import de.greluc.krt.profit.basetool.android.settings.SettingsScreen
import de.greluc.krt.profit.basetool.android.ui.KrtListDetail
import de.greluc.krt.profit.basetool.android.ui.LocalRootScrollTick
import de.greluc.krt.profit.basetool.android.ui.MoreScreen
import de.greluc.krt.profit.basetool.android.ui.PlaceholderScreen
import de.greluc.krt.profit.basetool.android.ui.RootScrollSignals
import de.greluc.krt.profit.basetool.android.ui.RouteNotFoundScreen
import de.greluc.krt.profit.basetool.android.ui.isWideWindow

/**
 * The navigation graph.
 *
 * Every destination registers the deep link that opens it, so a notification tap, a web link and an
 * in-app navigation converge on one address per screen. Transitions are a plain 200 ms cross-fade:
 * the design system allows colour and fade only — no slide-in stacks, no parallax — and a fade also
 * keeps the predictive-back preview honest, because the previous screen is shown as it really is.
 *
 * @param navController the controller driving the graph.
 * @param onOpenDestination invoked when a list entry opens another destination.
 * @param missions drives the Einsatz list.
 * @param missionDetail builds a view model for one Einsatz; the graph knows the id, the activity
 *   knows the dependencies, and this is where the two meet.
 * @param operations drives the Operationen list.
 * @param operationDetail builds a view model for one Operation, the same way [missionDetail] does.
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
 * @param orderDetail builds a view model for one order.
 * @param inventory drives the Lager tree.
 * @param exchange drives the Materialbörse.
 * @param refinery drives the member's own Raffinerie orders.
 * @param refineryOrder builds a view model for one Raffinerie order.
 * @param memberName the signed-in member's name, for the dashboard greeting.
 * @param orgUnitName the active org unit's name, for the same line.
 * @param onLogout ends the session.
 * @param settings everything the Einstellungen screen needs that the graph cannot know.
 * @param modifier layout modifier.
 */
@Composable
fun BasetoolNavHost(
    navController: NavHostController,
    rootScroll: RootScrollSignals,
    onOpenDestination: (KrtDestination) -> Unit,
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
    inventory: InventoryViewModel,
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
    personalInventory: PersonalInventoryViewModel,
    personalBlueprints: PersonalBlueprintsViewModel,
    booking: BookingViewModel,
    memberName: String?,
    orgUnitName: String?,
    onLogout: () -> Unit,
    settings: SettingsBindings,
    modifier: Modifier = Modifier,
) {
    // Captured outside the transition lambdas: they are not composable, so the value has to be
    // read here. Zero on a device asking for reduced motion, which makes the fade a cut.
    val motionMs = KrtTheme.motionMs
    NavHost(
        navController = navController,
        startDestination = KrtDestination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(motionMs)) },
        exitTransition = { fadeOut(animationSpec = tween(motionMs)) },
        popEnterTransition = { fadeIn(animationSpec = tween(motionMs)) },
        popExitTransition = { fadeOut(animationSpec = tween(motionMs)) },
    ) {
        KrtDestination.entries.forEach { destination ->
            composable(
                route = destination.route,
                deepLinks = listOf(navDeepLink { uriPattern = destination.deepLink }),
            ) { backStackEntry ->
                // Each destination sees only its own re-tap counter, so a re-tap on „Lager" cannot
                // scroll „Einsätze" -- and a root screen obeys the rule without knowing its route.
                CompositionLocalProvider(
                    LocalRootScrollTick provides rootScroll.ticksFor(destination.route),
                ) {
                    // Two functions rather than one: nine areas hang off this graph, and the split
                    // follows a real seam — a LIST destination is one a member reaches from the bar or
                    // from "Mehr" and that loads itself; a PUSHED one is opened with an id from
                    // somewhere else. Anything neither handles is still a screen this build does not
                    // have, and says so.
                    val handled =
                        listDestination(
                            destination = destination,
                            navController = navController,
                            missions = missions,
                            missionDetail = missionDetail,
                            operations = operations,
                            operationDetail = operationDetail,
                            notifications = notifications,
                            dashboard = dashboard,
                            hangar = hangar,
                            bank = bank,
                            bankRequests = bankRequests,
                            bankStaff = bankStaff,
                            bankLifecycle = bankLifecycle,
                            bankAccount = bankAccount,
                            orders = orders,
                            orderDetail = orderDetail,
                            inventory = inventory,
                            exchange = exchange,
                            refinery = refinery,
                            refineryOrder = refineryOrder,
                            materials = materials,
                            materialDetail = materialDetail,
                            personalInventory = personalInventory,
                            personalBlueprints = personalBlueprints,
                            booking = booking,
                            memberName = memberName,
                            orgUnitName = orgUnitName,
                        )
                    if (!handled) {
                        PushedDestination(
                            destination = destination,
                            backStackEntry = backStackEntry,
                            navController = navController,
                            missionDetail = missionDetail,
                            operationDetail = operationDetail,
                            bankAccount = bankAccount,
                            bankHolder = bankHolder,
                            bankStaff = bankStaff,
                            orderDetail = orderDetail,
                            refineryOrder = refineryOrder,
                            material =
                                MaterialBindings(
                                    detail = materialDetail,
                                    matrix = materialMatrix,
                                    profit = materialProfit,
                                ),
                            refineryCreate = refineryCreate,
                            orderCreate = orderCreate,
                            orderEdit = orderEdit,
                            orderCollection = orderCollection,
                            operationForm = operationForm,
                            blueprints = blueprints,
                            gameItems = gameItems,
                            materialDemand = materialDemand,
                            fleetImport = fleetImport,
                            onOpenDestination = onOpenDestination,
                            onLogout = onLogout,
                            settings = settings,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders a destination a member navigates **to**, and reports whether it did.
 *
 * Each of these loads itself when it is shown. The dashboard reloads on every visit — it is the
 * screen a member returns to between other things, and its whole subject is what changed while they
 * were away — while the lists load once and offer pull-to-refresh, because coming back to a list
 * should show it rather than re-fetch it.
 *
 * @param destination the destination being composed.
 * @param navController the controller, for the rows that open something.
 * @param missions drives the Einsatz list.
 * @param operations drives the Operationen list.
 * @param notifications drives the inbox and the badge.
 * @param dashboard drives the Übersicht.
 * @param hangar drives the Hangar.
 * @param bank drives the Konten list.
 * @param bankRequests drives the Anträge tab and the request sheet.
 * @param bankStaff drives the Verwaltung scope.
 * @param bankLifecycle drives its Konten tab.
 * @param orders drives the Auftrag queue.
 * @param inventory drives the Lager tree.
 * @param memberName the member's name, for the greeting.
 * @param orgUnitName the active org unit's name, for the same line.
 * Named in lower case on purpose: it returns a value, and Compose's own naming rule reserves the
 * capitalised form for functions that only emit.
 *
 * @return `true` when this function rendered the destination.
 */
@Composable
@Suppress("LongParameterList")
private fun listDestination(
    destination: KrtDestination,
    navController: NavHostController,
    missions: MissionsViewModel,
    missionDetail: (String) -> MissionDetailViewModel,
    operations: OperationsViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
    notifications: NotificationsViewModel,
    dashboard: DashboardViewModel,
    hangar: HangarViewModel,
    bank: BankViewModel,
    bankRequests: BankRequestsViewModel,
    bankStaff: BankStaffViewModel,
    bankLifecycle: BankLifecycleViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    inventory: InventoryViewModel,
    exchange: MaterialBoardViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
    materials: MaterialsViewModel,
    materialDetail: (String) -> MaterialDetailViewModel,
    personalInventory: PersonalInventoryViewModel,
    personalBlueprints: PersonalBlueprintsViewModel,
    booking: BookingViewModel,
    memberName: String?,
    orgUnitName: String?,
): Boolean {
    if (
        materialsDestination(
            destination = destination,
            navController = navController,
            materials = materials,
            materialDetail = materialDetail,
        ) ||
        listDetailDestination(
            destination = destination,
            navController = navController,
            missions = missions,
            missionDetail = missionDetail,
            bank = bank,
            bankRequests = bankRequests,
            bankStaff = bankStaff,
            bankLifecycle = bankLifecycle,
            bankAccount = bankAccount,
            orders = orders,
            orderDetail = orderDetail,
            refinery = refinery,
            refineryOrder = refineryOrder,
            operations = operations,
            operationDetail = operationDetail,
        )
    ) {
        return true
    }
    var handled = true
    when (destination) {
        KrtDestination.Home -> {
            LaunchedEffect(Unit) { dashboard.load() }
            val dashboardState by dashboard.state.collectAsStateWithLifecycle()
            DashboardScreen(
                state = dashboardState,
                memberName = memberName,
                orgUnitName = orgUnitName,
                onMarkAnnouncementRead = dashboard::onAnnouncementRead,
                onRefresh = dashboard::onRefresh,
                // A mission detail belongs on this tab's stack; the other three open a
                // top-level destination and must go through the shell's helper, or the
                // navigation bar can no longer get back here (see TopLevelNavigation.kt).
                onOpenMission = { navController.navigate(missionDetailRoute(it)) },
                onOpenMissions = { navController.navigateToTopLevel(KrtDestination.Missions.route) },
                onQuickAction = { action -> navController.navigateToTopLevel(action.destination.route) },
                onOpenInbox = { navController.navigateToTopLevel(KrtDestination.Notifications.route) },
            )
        }

        KrtDestination.Notifications -> {
            // The badge is already live from the shell, so this only adds the list.
            LaunchedEffect(Unit) { notifications.loadOnce() }
            NotificationsRoute(
                viewModel = notifications,
                onOpen = { notification ->
                    notificationDestination(notification)?.let(navController::navigate)
                },
            )
        }

        KrtDestination.Hangar -> {
            LaunchedEffect(Unit) { hangar.loadOnce() }
            HangarRoute(
                viewModel = hangar,
                // A plain push, NOT navigateToTopLevel: the import is a sub-page of the Hangar, so
                // back has to return here rather than to Übersicht.
                onOpenImport = { navController.navigate(KrtDestination.FleetImport.route) },
            )
        }

        KrtDestination.PersonalInventory -> {
            MeinInventarRoute(items = personalInventory, blueprints = personalBlueprints)
        }

        KrtDestination.Exchange -> {
            LaunchedEffect(Unit) { exchange.loadOnce() }
            MaterialBoardRoute(viewModel = exchange)
        }

        KrtDestination.Inventory -> {
            LaunchedEffect(Unit) { inventory.loadOnce() }
            InventoryRoute(
                viewModel = inventory,
                onBookIn = { booking.openBookIn(inventory::onBookingSaved) },
                onBookOut = { entry ->
                    booking.openForEntry(entry, BookingMode.OUT, inventory::onBookingSaved)
                },
            )
            BookingHost(viewModel = booking)
        }

        else -> {
            handled = false
        }
    }
    return handled
}

/**
 * „Handel" — the material list beside one material's prices.
 *
 * Its own function rather than another branch of [listDetailDestination]: that switch is already at
 * the complexity the project's static analysis allows, and a screen is a poor reason to raise a
 * limit that exists to keep this file readable.
 *
 * @param destination the route being drawn.
 * @param navController for the phone's push.
 * @param materials the catalogue list.
 * @param materialDetail builds a view model for one material.
 * @return `true` when this function drew the destination.
 */
@Composable
private fun materialsDestination(
    destination: KrtDestination,
    navController: NavHostController,
    materials: MaterialsViewModel,
    materialDetail: (String) -> MaterialDetailViewModel,
): Boolean {
    if (destination != KrtDestination.Materials) {
        return false
    }
    // Design ch. 16: „Tablet 1280×800 — Liste (480 dp) + Detail". The same split as the Einsätze
    // and the Aufträge: beside the list on a wide window, pushed on a phone.
    val wide = isWideWindow()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    KrtListDetail(
        detail =
            selected?.let { id ->
                {
                    MaterialDetailRoute(viewModel = remember(id) { materialDetail(id) })
                }
            },
    ) {
        MaterialsRoute(
            viewModel = materials,
            onOpen = {
                if (wide) selected = it else navController.navigate(materialDetailRoute(it))
            },
            // A plain push on both form factors: neither is a row of the list, so neither belongs
            // in the detail pane beside it.
            onOpenMatrix = { navController.navigate(KrtDestination.MaterialMatrix.route) },
            onOpenProfit = { navController.navigate(KrtDestination.MaterialProfit.route) },
        )
    }
    return true
}

/**
 * The four destinations that show a list beside its detail on a wide window.
 *
 * Split out of [listDestination] rather than living beside the others, and not only to keep that
 * function under the complexity gate: these four are the same shape four times over — hold a
 * selection, build a detail view model for it, hand the list a tap handler that either selects or
 * navigates — and reading them together is what makes that shape visible. A fifth one belongs
 * here too.
 *
 * Each keeps its own selection rather than sharing one, because the selections are unrelated:
 * picking an Auftrag says nothing about which Konto should be open.
 *
 * @param destination which destination to draw.
 * @param navController used for the phone's push navigation and for sibling destinations.
 * @param missions the Einsatz list.
 * @param missionDetail builds a view model for one Einsatz.
 * @param bank the Konten list.
 * @param bankAccount builds a view model for one Konto.
 * @param orders the Auftrag queue.
 * @param orderDetail builds a view model for one Auftrag.
 * @param refinery the Raffinerie orders.
 * @param refineryOrder builds a view model for one Raffinerie-Order.
 * @return `true` when this function drew the destination.
 */
@Composable
@Suppress("LongParameterList")
private fun listDetailDestination(
    destination: KrtDestination,
    navController: NavHostController,
    missions: MissionsViewModel,
    missionDetail: (String) -> MissionDetailViewModel,
    bank: BankViewModel,
    bankRequests: BankRequestsViewModel,
    bankStaff: BankStaffViewModel,
    bankLifecycle: BankLifecycleViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
    operations: OperationsViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
): Boolean {
    when (destination) {
        KrtDestination.Missions -> {
            MissionsListDetail(
                navController = navController,
                missions = missions,
                missionDetail = missionDetail,
            )
        }

        KrtDestination.Bank -> {
            LaunchedEffect(Unit) { bank.loadOnce() }
            // Design ch. 12: "Tablet 1280×800 — Konten + Detail".
            val wide = isWideWindow()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            KrtListDetail(
                detail =
                    selected?.let { id ->
                        {
                            val detailModel = remember(id) { bankAccount(id) }
                            LaunchedEffect(id) { detailModel.load() }
                            BankAccountRoute(viewModel = detailModel)
                        }
                    },
            ) {
                BankAccountsRoute(
                    viewModel = bank,
                    requestsViewModel = bankRequests,
                    staffViewModel = bankStaff,
                    lifecycleViewModel = bankLifecycle,
                    onOpenAccount = {
                        if (wide) selected = it else navController.navigate(bankAccountRoute(it))
                    },
                    // A full screen rather than the detail pane: the holder register is a section
                    // of the Konten tab, not a list of its own, so there is nothing to keep beside
                    // it.
                    onOpenHolder = { navController.navigate(bankHolderRoute(it)) },
                )
            }
        }

        KrtDestination.Orders -> {
            LaunchedEffect(Unit) { orders.loadOnce() }
            // Design ch. 10: "Tablet 1280×800 — Queue + Detail".
            val wide = isWideWindow()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            KrtListDetail(
                detail =
                    selected?.let { id ->
                        {
                            val detailModel = remember(id) { orderDetail(id) }
                            LaunchedEffect(id) { detailModel.load() }
                            OrderDetailRoute(
                                viewModel = detailModel,
                                onEditOrder = {
                                    edited,
                                    mode,
                                    ->
                                    navController.navigate(orderEditRoute(edited, mode.name))
                                },
                                onOpenCollection = {
                                    navController.navigate(orderCollectionRoute(it))
                                },
                            )
                        }
                    },
            ) {
                OrdersRoute(
                    viewModel = orders,
                    onOpenOrder = {
                        if (wide) selected = it else navController.navigate(orderDetailRoute(it))
                    },
                    onCreate = { navController.navigate(KrtDestination.OrderCreate.route) },
                    onOpenDemand = { navController.navigate(KrtDestination.MaterialDemand.route) },
                )
            }
        }

        KrtDestination.Refinery -> {
            LaunchedEffect(Unit) { refinery.loadOnce() }
            // Design ch. 11: "Tablet 1280×800 — Orders + Detail". No load() here, matching the
            // standalone destination — this view model reads on construction.
            val wide = isWideWindow()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            KrtListDetail(
                detail =
                    selected?.let { id ->
                        {
                            val detailModel = remember(id) { refineryOrder(id) }
                            RefineryOrderDetailRoute(
                                viewModel = detailModel,
                                onEdit = { navController.navigate(refineryEditRoute(id)) },
                                // The pane's subject is gone; the list beside it stays.
                                onDeleted = { selected = null },
                            )
                        }
                    },
            ) {
                RefineryOrdersRoute(
                    viewModel = refinery,
                    onOpenOrder = {
                        if (wide) selected = it else navController.navigate(refineryOrderRoute(it))
                    },
                    onCreate = {
                        navController.navigate(KrtDestination.RefineryCreate.route)
                    },
                )
            }
        }

        KrtDestination.Operations -> {
            OperationsListDetail(
                navController = navController,
                operations = operations,
                operationDetail = operationDetail,
            )
        }

        else -> {
            return false
        }
    }
    return true
}

/**
 * Einsätze — the list and, beside it on a tablet, the Einsatz a member picked.
 *
 * Design ch. 06: „Tablet 1280×800 — list-detail". On a wide window a tap selects beside the list;
 * on a phone it pushes the detail as its own screen, which is what the back arrow in the top bar
 * already expects.
 *
 * @param navController for the phone's pushed detail and the other half's route.
 * @param missions the list's view model.
 * @param missionDetail builds one Einsatz's view model.
 */
@Composable
private fun MissionsListDetail(
    navController: NavHostController,
    missions: MissionsViewModel,
    missionDetail: (String) -> MissionDetailViewModel,
) {
    LaunchedEffect(Unit) { missions.load() }
    val wide = isWideWindow()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    KrtListDetail(
        detail =
            selected?.let { id ->
                {
                    val detailModel = remember(id) { missionDetail(id) }
                    LaunchedEffect(id) { detailModel.load() }
                    MissionDetailRoute(viewModel = detailModel)
                }
            },
    ) {
        MissionsRoute(
            viewModel = missions,
            onOpenMission = {
                if (wide) selected = it else navController.navigate(missionDetailRoute(it))
            },
            // The segment navigates rather than toggling: the two halves are their own routes, and
            // a local toggle would leave the rail highlighting the one no longer on screen. Both
            // now map to EINSÄTZE, which is what makes them one surface (S30).
            onOpenOperations = { navController.navigate(KrtDestination.Operations.route) },
        )
    }
}

/**
 * Operationen — the **second half of the Einsätze surface** (round 14 · S30).
 *
 * Same entry, same answer: list beside detail on a tablet, a pushed detail on a phone. It used to
 * be a full-width list reached from „Mehr", which made one segment behave like two screens — one
 * half with a pane and one without.
 *
 * @param navController for the phone's pushed detail and the edit form.
 * @param operations the list's view model.
 * @param operationDetail builds one Operation's view model.
 */
@Composable
private fun OperationsListDetail(
    navController: NavHostController,
    operations: OperationsViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
) {
    LaunchedEffect(Unit) { operations.loadOnce() }
    val wide = isWideWindow()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    KrtListDetail(
        detail =
            selected?.let { id ->
                {
                    val detailModel = remember(id) { operationDetail(id) }
                    LaunchedEffect(id) { detailModel.load() }
                    OperationDetailRoute(
                        viewModel = detailModel,
                        onOpenMission = { navController.navigate(missionDetailRoute(it)) },
                        onEdit = { navController.navigate(operationEditRoute(id)) },
                    )
                }
            },
    ) {
        OperationsRoute(
            viewModel = operations,
            onOpenOperation = {
                if (wide) selected = it else navController.navigate(operationDetailRoute(it))
            },
            // Einsätze is a navigation-bar destination, so it goes through the shell's helper
            // rather than onto the Operationen stack (see TopLevelNavigation.kt).
            onOpenMissions = { navController.navigateToTopLevel(KrtDestination.Missions.route) },
        )
    }
}

/**
 * The view models the Handel area's three pushed screens are built from.
 *
 * @property detail builds a view model for one material.
 * @property matrix builds the price matrix.
 * @property profit builds the profit calculation.
 */
private data class MaterialBindings(
    val detail: (String) -> MaterialDetailViewModel,
    val matrix: () -> MaterialMatrixViewModel,
    val profit: () -> ProfitViewModel,
)

/**
 * The pushed screens that need nothing from the route but, at most, one id.
 *
 * Grouped rather than given a branch each: `PushedDestination`'s switch is at the complexity the
 * project's static analysis allows, and these four are the same shape — read at most one argument,
 * build one view model, draw one route.
 *
 * @param destination which of them.
 * @param backStackEntry carries the id where there is one.
 * @param refineryOrder builds a view model for one Raffinerie order.
 * @param material the three Handel view models.
 * @param orderCollection builds the view model for one Auftrag's linked stock rows.
 */
@Composable
private fun SimplePushedDestination(
    destination: KrtDestination,
    backStackEntry: NavBackStackEntry,
    navController: NavHostController,
    refineryOrder: (String) -> RefineryDetailViewModel,
    material: MaterialBindings,
    orderCollection: (String) -> OrderCollectionViewModel,
) {
    when (destination) {
        KrtDestination.OrderCollection -> {
            val collectionOrderId = backStackEntry.arguments?.getString(ORDER_ID_ARG).orEmpty()
            OrderCollectionRoute(
                viewModel = remember(collectionOrderId) { orderCollection(collectionOrderId) },
            )
        }

        KrtDestination.MaterialMatrix -> {
            MaterialMatrixRoute(viewModel = remember { material.matrix() })
        }

        KrtDestination.MaterialProfit -> {
            ProfitRoute(viewModel = remember { material.profit() })
        }

        KrtDestination.MaterialDetail -> {
            val materialId = backStackEntry.arguments?.getString(MATERIAL_ID_ARG).orEmpty()
            MaterialDetailRoute(viewModel = remember(materialId) { material.detail(materialId) })
        }

        else -> {
            val orderId = backStackEntry.arguments?.getString(REFINERY_ORDER_ID_ARG).orEmpty()
            RefineryOrderDetailRoute(
                viewModel = remember(orderId) { refineryOrder(orderId) },
                onEdit = { navController.navigate(refineryEditRoute(orderId)) },
                onDeleted = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Renders a destination that is **pushed** from another screen, plus the two settings pages.
 *
 * Each detail view model is keyed on its id and scoped to this back-stack entry, so opening a second
 * record builds a second view model rather than showing the first one's content under the second
 * one's title.
 *
 * @param destination the destination being composed.
 * @param backStackEntry the entry carrying the route's arguments.
 * @param navController the controller.
 * @param missionDetail builds a view model for one Einsatz.
 * @param operationDetail builds a view model for one Operation.
 * @param bankAccount builds a view model for one account.
 * @param bankHolder builds a view model for one holder's custody.
 * @param bankHolder builds a view model for one holder's custody.
 * @param bankStaff answers whether the caller may move custody.
 * @param orderDetail builds a view model for one order.
 * @param material the Handel area's three pushed view models.
 * @param blueprints the org-wide blueprint availability: whether it may be opened, and how to
 *   build it.
 * @param gameItems builds the game-item stock's view model.
 * @param materialDemand builds the cross-order material demand's view model.
 * @param onOpenDestination invoked from the "Mehr" list.
 * @param onLogout ends the session.
 * @param settings what the Einstellungen screen needs from the activity.
 */

@Composable
@Suppress("LongParameterList")
private fun PushedDestination(
    destination: KrtDestination,
    backStackEntry: NavBackStackEntry,
    navController: NavHostController,
    missionDetail: (String) -> MissionDetailViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    bankHolder: (String) -> BankHolderViewModel,
    bankStaff: BankStaffViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
    material: MaterialBindings,
    refineryCreate: (String?) -> RefineryCreateViewModel,
    orderCreate: () -> OrderCreateViewModel,
    orderEdit: (String, OrderFormMode) -> OrderCreateViewModel,
    orderCollection: (String) -> OrderCollectionViewModel,
    operationForm: (String?) -> OperationFormViewModel,
    blueprints: BlueprintOverviewBindings,
    gameItems: () -> GameItemStockViewModel,
    materialDemand: () -> MaterialDemandViewModel,
    fleetImport: FleetImportViewModel,
    onOpenDestination: (KrtDestination) -> Unit,
    onLogout: () -> Unit,
    settings: SettingsBindings,
) {
    when (destination) {
        KrtDestination.MissionDetail -> {
            val missionId = backStackEntry.arguments?.getString(MISSION_ID_ARG).orEmpty()
            val viewModel = remember(missionId) { missionDetail(missionId) }
            LaunchedEffect(missionId) { viewModel.load() }
            MissionDetailRoute(viewModel = viewModel)
        }

        KrtDestination.OperationDetail -> {
            val operationId = backStackEntry.arguments?.getString(OPERATION_ID_ARG).orEmpty()
            val viewModel = remember(operationId) { operationDetail(operationId) }
            LaunchedEffect(operationId) { viewModel.load() }
            OperationDetailRoute(
                viewModel = viewModel,
                onOpenMission = { navController.navigate(missionDetailRoute(it)) },
                onEdit = { navController.navigate(operationEditRoute(operationId)) },
            )
        }

        KrtDestination.BankAccount, KrtDestination.BankHolder -> {
            BankPushedDestination(
                destination = destination,
                backStackEntry = backStackEntry,
                bankAccount = bankAccount,
                bankHolder = bankHolder,
                bankStaff = bankStaff,
            )
        }

        KrtDestination.RefineryCreate,
        KrtDestination.RefineryEdit,
        KrtDestination.OrderCreate,
        KrtDestination.OrderEdit,
        KrtDestination.OperationCreate,
        KrtDestination.OperationEdit,
        -> {
            CreateFormDestination(
                destination = destination,
                backStackEntry = backStackEntry,
                navController = navController,
                refineryCreate = refineryCreate,
                orderCreate = orderCreate,
                orderEdit = orderEdit,
                operationForm = operationForm,
            )
        }

        KrtDestination.OrderDetail -> {
            val orderId = backStackEntry.arguments?.getString(ORDER_ID_ARG).orEmpty()
            val viewModel = remember(orderId) { orderDetail(orderId) }
            LaunchedEffect(orderId) { viewModel.load() }
            OrderDetailRoute(
                viewModel = viewModel,
                onEditOrder = { id, mode -> navController.navigate(orderEditRoute(id, mode.name)) },
                onOpenCollection = { navController.navigate(orderCollectionRoute(it)) },
            )
        }

        KrtDestination.RefineryOrder,
        KrtDestination.MaterialDetail,
        KrtDestination.MaterialMatrix,
        KrtDestination.MaterialProfit,
        KrtDestination.OrderCollection,
        -> {
            SimplePushedDestination(
                destination = destination,
                backStackEntry = backStackEntry,
                navController = navController,
                refineryOrder = refineryOrder,
                material = material,
                orderCollection = orderCollection,
            )
        }

        KrtDestination.More -> {
            MoreScreen(
                onOpen = onOpenDestination,
                blueprintOverview = blueprints.allowed,
            )
        }

        KrtDestination.Settings -> {
            val version =
                LocalContext.current.let { context ->
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            SettingsScreen(
                accountName = settings.accountName,
                orgUnitName = settings.orgUnitName,
                onSwitchOrgUnit = settings.onSwitchOrgUnit,
                preferences = settings.preferences,
                onPayout = settings.onPayout,
                onSharing = settings.onSharing,
                onRetryPreferences = settings.onRetryPreferences,
                language = settings.language,
                onLanguageChange = settings.onLanguageChange,
                appLockEnabled = settings.appLockEnabled,
                appLockAvailable = settings.appLockAvailable,
                onAppLockChange = settings.onAppLockChange,
                screenCaptureAllowed = settings.screenCaptureAllowed,
                onScreenCaptureChange = settings.onScreenCaptureChange,
                onOpenPrivacy = settings.onOpenPrivacy,
                onOpenImprint = settings.onOpenImprint,
                onOpenTerms = settings.onOpenTerms,
                // A plain push, NOT navigateToTopLevel: the notice is a sub-page of this screen, so
                // back has to return here rather than to Übersicht.
                onOpenLicenses = { navController.navigate(KrtDestination.Licenses.route) },
                onLogout = onLogout,
                versionName = version.versionName.orEmpty(),
                versionCode = settings.versionCode,
            )
        }

        KrtDestination.BlueprintOverview,
        KrtDestination.GameItems,
        KrtDestination.Licenses,
        KrtDestination.FleetImport,
        -> {
            LeafDestination(
                destination = destination,
                blueprints = blueprints,
                fleetImport = fleetImport,
                onOpenUrl = settings.onOpenUrl,
                gameItems = gameItems,
                materialDemand = materialDemand,
            )
        }

        KrtDestination.NotFound -> {
            RouteNotFoundScreen(
                onBackToBase = {
                    // Back to the Übersicht that is already at the bottom of the stack, not a
                    // second copy on top of it. Popping only this screen and pushing Home leaves
                    // two, and then back on Übersicht lands on Übersicht instead of leaving the
                    // app -- which is the one thing ch. 03 says back on Übersicht must do.
                    navController.navigate(KrtDestination.Home.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        else -> {
            PlaceholderScreen(destination = destination)
        }
    }
}

/**
 * The org-wide blueprint availability, as the shell hands it over.
 *
 * Two values rather than two parameters, because they travel together through four signatures and
 * the host already carries every argument detekt allows.
 *
 * @property allowed whether the caller may open it — `canSeeBlueprintOverview` from
 *   `/me/capabilities`. `false` draws the „Mehr" row locked with its reason rather than hiding it.
 * @property build builds the view model, on first navigation.
 */
data class BlueprintOverviewBindings(
    val allowed: Boolean,
    val build: () -> BlueprintOverviewViewModel,
)

/**
 * Everything the Einstellungen screen needs from outside the navigation graph.
 *
 * Bundled into one holder rather than threaded through as ten parameters, because every one of them
 * would otherwise have to be declared twice more — on [BasetoolNavHost] and on `BasetoolApp` — for
 * a screen that is the only consumer. The activity owns all of it: the session, the Keystore-backed
 * lock, and the browser hand-off.
 *
 * @property accountName the signed-in member's username from the ID token, or `null` while unknown.
 * @property language the language currently on screen.
 * @property onLanguageChange pins a language; the platform then recreates the activity.
 * @property appLockEnabled whether a lock is armed.
 * @property screenCaptureAllowed whether screenshots and screen recording are permitted.
 * @property onScreenCaptureChange permits or forbids them.
 * @property appLockAvailable whether the device can prompt at all.
 * @property onAppLockChange arms or disarms the lock; arming raises the biometric prompt.
 * @property onOpenPrivacy opens the privacy policy in a browser.
 * @property onOpenImprint opens the imprint in a browser.
 * @property onOpenTerms opens the terms of use in a browser.
 * @property onOpenUrl opens an arbitrary URL in a browser; used by the open-source notice.
 *   Returns `false` when nothing on the device handled it, which is what turns the licence
 *   action into a copy (design ch. 15).
 * @property versionCode the app's build number, from `BuildConfig`.
 * @property orgUnitName the active org unit, as the top bar's chip names it, or `null` while the
 *   scope is unknown. The settings row shows the same value rather than reading it again — two
 *   copies of a scope are two things that can disagree.
 * @property onSwitchOrgUnit opens the org switcher, the same sheet the chip opens.
 * @property preferences the two standing choices that live on the server.
 * @property onPayout sets where the member's share goes by default.
 * @property onSharing shares or unshares the member's blueprints with the organisation.
 * @property onRetryPreferences re-reads the two account values after a failed read.
 */
@Immutable
data class SettingsBindings(
    val accountName: String?,
    val language: AppLanguage,
    val onLanguageChange: (AppLanguage) -> Unit,
    val appLockEnabled: Boolean,
    val screenCaptureAllowed: Boolean,
    val onScreenCaptureChange: (Boolean) -> Unit,
    val appLockAvailable: Boolean,
    val onAppLockChange: (Boolean) -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenImprint: () -> Unit,
    val onOpenTerms: () -> Unit,
    val onOpenUrl: (String) -> Boolean,
    val versionCode: Int,
    // Filled by `BasetoolApp`, not by the activity: the active scope and the switcher sheet both
    // live in the shell, and a second copy in the activity would be a second thing to keep in step.
    val orgUnitName: String? = null,
    val onSwitchOrgUnit: () -> Unit = {},
    val preferences: MemberPreferencesState,
    val onPayout: (PayoutPreference) -> Unit,
    val onSharing: (Boolean) -> Unit,
    val onRetryPreferences: () -> Unit,
)

/**
 * The create and edit forms, behind one branch.
 *
 * Grouped the way the two bank details are: each form is its own composable below, and the host's
 * `when` stays under detekt's complexity limit without any branch being suppressed away.
 *
 * @param destination which of the two forms.
 * @param navController where to go afterwards.
 * @param refineryCreate builds the Raffinerie form's view model.
 * @param backStackEntry carries the edited id, where there is one.
 * @param orderCreate builds the Auftrag form's view model.
 * @param orderEdit builds it for an existing Auftrag.
 * @param operationForm builds the Operation form's view model, editing when given an id.
 */
@Composable
@Suppress("LongParameterList")
private fun CreateFormDestination(
    destination: KrtDestination,
    backStackEntry: NavBackStackEntry,
    navController: NavHostController,
    refineryCreate: (String?) -> RefineryCreateViewModel,
    orderCreate: () -> OrderCreateViewModel,
    orderEdit: (String, OrderFormMode) -> OrderCreateViewModel,
    operationForm: (String?) -> OperationFormViewModel,
) {
    when (destination) {
        KrtDestination.OperationCreate, KrtDestination.OperationEdit -> {
            // One screen for both, so the id decides which write happens rather than which layout.
            val edited =
                backStackEntry.arguments?.getString(OPERATION_ID_ARG).takeIf {
                    destination == KrtDestination.OperationEdit
                }
            OperationFormDestination(
                navController = navController,
                build = { operationForm(edited) },
            )
        }

        KrtDestination.OrderCreate -> {
            OrderCreateDestination(navController = navController, build = orderCreate)
        }

        KrtDestination.OrderEdit -> {
            // The same screen as the create, so it lands in the same destination — design ch. 10
            // artboard 10 is explicit that there is no second layout.
            val editedId = backStackEntry.arguments?.getString(ORDER_ID_ARG).orEmpty()
            // An unreadable mode falls back to the requester's narrower form rather than the
            // Logistician's: the narrow one refuses what the caller may not write, the wide one
            // would offer it and be refused by the server after the member had typed it.
            val editMode =
                backStackEntry.arguments?.getString(ORDER_EDIT_MODE_ARG)?.let { raw ->
                    OrderFormMode.entries.firstOrNull { it.name == raw }
                } ?: OrderFormMode.EDIT_AS_REQUESTER
            OrderCreateDestination(
                navController = navController,
                build = { orderEdit(editedId, editMode) },
            )
        }

        else -> {
            // The edit is the same form pre-filled, so it lands in the same destination; only the
            // id decides which of the two writes the CTA performs.
            val edited =
                backStackEntry.arguments?.getString(REFINERY_ORDER_ID_ARG).takeIf {
                    destination == KrtDestination.RefineryEdit
                }
            RefineryCreateDestination(
                navController = navController,
                build = { refineryCreate(edited) },
            )
        }
    }
}

/**
 * The three destinations that are one composable each and carry no argument of their own.
 *
 * Grouped for the reason the two bank details and the create forms are: the host's `when` stays
 * under detekt's complexity ceiling without any branch being suppressed away.
 *
 * @param destination which of the three.
 * @param blueprints the org-wide blueprint availability.
 * @param fleetImport the Fleetview import.
 * @param onOpenUrl opens a licence's URL.
 * @param gameItems builds the game-item stock's view model.
 * @param materialDemand builds the cross-order material demand's view model.
 */
@Composable
private fun LeafDestination(
    destination: KrtDestination,
    blueprints: BlueprintOverviewBindings,
    gameItems: () -> GameItemStockViewModel,
    materialDemand: () -> MaterialDemandViewModel,
    fleetImport: FleetImportViewModel,
    onOpenUrl: (String) -> Boolean,
) {
    when (destination) {
        KrtDestination.BlueprintOverview -> {
            BlueprintOverviewRoute(viewModel = remember { blueprints.build() })
        }

        KrtDestination.GameItems -> {
            GameItemStockRoute(viewModel = remember { gameItems() })
        }

        KrtDestination.MaterialDemand -> {
            MaterialDemandRoute(viewModel = remember { materialDemand() })
        }

        KrtDestination.FleetImport -> {
            FleetImportRoute(viewModel = fleetImport)
        }

        else -> {
            LicensesScreen(onOpenUrl = onOpenUrl)
        }
    }
}

/**
 * The Operation form as a pushed destination.
 *
 * Same shape as the order form's: the form's job ends when the Operation exists or has changed, and
 * the member wants to look at the Operation rather than at a form they are finished with.
 *
 * @param navController where to go afterwards.
 * @param build builds the view model.
 */
@Composable
private fun OperationFormDestination(
    navController: NavHostController,
    build: () -> OperationFormViewModel,
) {
    val viewModel = remember { build() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        state.saved?.let {
            navController.popBackStack()
            navController.navigate(operationDetailRoute(it))
        }
    }
    OperationFormRoute(viewModel = viewModel)
}

/**
 * The „Neuer Auftrag" form as a pushed destination.
 *
 * Same shape as [RefineryCreateDestination] and for the same reason: the form's job ends when the
 * order exists, and the member wants to look at the order rather than at an emptied form. The
 * created form is popped first so „back" from the detail returns to the queue.
 *
 * @param navController where to go afterwards.
 * @param build builds the view model.
 */
@Composable
private fun OrderCreateDestination(
    navController: NavHostController,
    build: () -> OrderCreateViewModel,
) {
    val viewModel = remember { build() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) {
        state.created?.let {
            navController.popBackStack()
            // An edit reports the id it rewrote, so this lands on the order either way — for a
            // create the one that now exists, for an edit the one the member came from.
            navController.navigate(orderDetailRoute(it))
        }
    }
    OrderCreateRoute(viewModel = viewModel)
}

/**
 * The „Neuer Raffinerieauftrag" form as a pushed destination.
 *
 * Its own composable so the host's `when` stays under detekt's complexity limit, and because the
 * navigation on success is a rule of its own: the form's job ends when the order exists, and the
 * member wants to look at the order, not at an emptied form.
 *
 * @param navController where to go afterwards.
 * @param build builds the view model.
 */
@Composable
private fun RefineryCreateDestination(
    navController: NavHostController,
    build: () -> RefineryCreateViewModel,
) {
    val viewModel = remember { build() }
    LaunchedEffect(Unit) { viewModel.loadOnce() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) {
        state.created?.let {
            navController.popBackStack()
            navController.navigate(refineryOrderRoute(it))
        }
    }
    RefineryCreateRoute(viewModel = viewModel)
}

/**
 * The two bank records that are pushed rather than paned.
 *
 * Split out of the host's `when` so it stays under detekt's complexity limit; the two belong
 * together anyway, since both read through the office when the caller has one.
 *
 * @param destination which of the two.
 * @param backStackEntry carries the record's id.
 * @param bankAccount builds the account detail's view model.
 * @param bankHolder builds the holder detail's view model.
 * @param bankStaff answers whether the caller may move custody.
 */
@Composable
private fun BankPushedDestination(
    destination: KrtDestination,
    backStackEntry: NavBackStackEntry,
    bankAccount: (String) -> BankAccountViewModel,
    bankHolder: (String) -> BankHolderViewModel,
    bankStaff: BankStaffViewModel,
) {
    when (destination) {
        KrtDestination.BankHolder -> {
            val holderId = backStackEntry.arguments?.getString(HOLDER_ID_ARG).orEmpty()
            val viewModel = remember(holderId) { bankHolder(holderId) }
            LaunchedEffect(holderId) { viewModel.loadOnce() }
            // Whether custody may be moved is the staff dashboard's answer, not a role this screen
            // works out — the same source the rest of the Verwaltung scope draws from.
            val staff by bankStaff.state.collectAsStateWithLifecycle()
            BankHolderRoute(viewModel = viewModel, management = staff.management)
        }

        else -> {
            val accountId = backStackEntry.arguments?.getString(ACCOUNT_ID_ARG).orEmpty()
            val viewModel = remember(accountId) { bankAccount(accountId) }
            LaunchedEffect(accountId) { viewModel.load() }
            BankAccountRoute(viewModel = viewModel)
        }
    }
}
