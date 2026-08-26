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
import de.greluc.krt.profit.basetool.android.inventory.InventoryRoute
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailRoute
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsRoute
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailRoute
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsRoute
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.NotificationsPhase
import de.greluc.krt.profit.basetool.android.notifications.NotificationsRoute
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.notificationDestination
import de.greluc.krt.profit.basetool.android.orders.OrderDetailRoute
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrdersRoute
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.MeinInventarRoute
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalBlueprintsViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalInventoryViewModel
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
 * @param bankAccount builds a view model for one account.
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
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    inventory: InventoryViewModel,
    exchange: MaterialBoardViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
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
                            notifications = notifications,
                            dashboard = dashboard,
                            hangar = hangar,
                            bank = bank,
                            bankAccount = bankAccount,
                            orders = orders,
                            orderDetail = orderDetail,
                            inventory = inventory,
                            exchange = exchange,
                            refinery = refinery,
                            refineryOrder = refineryOrder,
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
                            orderDetail = orderDetail,
                            refineryOrder = refineryOrder,
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
    notifications: NotificationsViewModel,
    dashboard: DashboardViewModel,
    hangar: HangarViewModel,
    bank: BankViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    inventory: InventoryViewModel,
    exchange: MaterialBoardViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
    personalInventory: PersonalInventoryViewModel,
    personalBlueprints: PersonalBlueprintsViewModel,
    booking: BookingViewModel,
    memberName: String?,
    orgUnitName: String?,
): Boolean {
    if (
        listDetailDestination(
            destination = destination,
            navController = navController,
            missions = missions,
            missionDetail = missionDetail,
            bank = bank,
            bankAccount = bankAccount,
            orders = orders,
            orderDetail = orderDetail,
            refinery = refinery,
            refineryOrder = refineryOrder,
        )
    ) {
        return true
    }
    var handled = true
    when (destination) {
        KrtDestination.Home -> {
            LaunchedEffect(Unit) { dashboard.load() }
            // The dashboard SHOWS the unread preview, so it is a consumer of the inbox and has to
            // ask for it. Without this the badge was live while the band beneath it said "Nichts
            // Ungelesenes" — found on a device, and exactly the disagreement one shared state was
            // supposed to rule out. `loadOnce` is idempotent, so opening the inbox afterwards
            // costs nothing.
            LaunchedEffect(Unit) { notifications.loadOnce() }
            val dashboardState by dashboard.state.collectAsStateWithLifecycle()
            val notificationState by notifications.state.collectAsStateWithLifecycle()
            DashboardScreen(
                state = dashboardState,
                memberName = memberName,
                orgUnitName = orgUnitName,
                // The same rows the inbox shows, filtered to the unread ones. Reading them from the
                // inbox's state rather than from a second endpoint keeps the preview and the list
                // from disagreeing.
                unread = notificationState.notifications.filterNot { it.read },
                // "Nichts Ungelesenes" is a claim, and it may only be made once the inbox has
                // actually answered. Before that the band shows nothing at all.
                unreadKnown = notificationState.phase is NotificationsPhase.Ready,
                onRefresh = dashboard::onRefresh,
                onOpenMission = { navController.navigate(missionDetailRoute(it)) },
                onOpenMissions = { navController.navigate(KrtDestination.Missions.route) },
                onOpenNotifications = { navController.navigate(KrtDestination.Notifications.route) },
                onQuickAction = { action -> navController.navigate(action.destination.route) },
            )
        }

        KrtDestination.Operations -> {
            LaunchedEffect(Unit) { operations.loadOnce() }
            OperationsRoute(
                viewModel = operations,
                onOpenOperation = { navController.navigate(operationDetailRoute(it)) },
                onOpenMissions = { navController.navigate(KrtDestination.Missions.route) },
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
    bankAccount: (String) -> BankAccountViewModel,
    orders: OrdersViewModel,
    orderDetail: (String) -> OrderDetailViewModel,
    refinery: RefineryViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
): Boolean {
    when (destination) {
        KrtDestination.Missions -> {
            LaunchedEffect(Unit) { missions.load() }
            // Design ch. 06: "Tablet 1280×800 — list-detail". On a wide window a tap selects
            // beside the list; on a phone it pushes the detail as its own screen, which is what
            // the back arrow in the top bar already expects.
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
                    // The segment navigates rather than toggling: both lists are their own
                    // destination, and a local toggle would leave the navigation bar highlighting
                    // the one the member is no longer looking at.
                    onOpenOperations = { navController.navigate(KrtDestination.Operations.route) },
                )
            }
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
                    onOpenAccount = {
                        if (wide) selected = it else navController.navigate(bankAccountRoute(it))
                    },
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
                            OrderDetailRoute(viewModel = detailModel)
                        }
                    },
            ) {
                OrdersRoute(
                    viewModel = orders,
                    onOpenOrder = {
                        if (wide) selected = it else navController.navigate(orderDetailRoute(it))
                    },
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
                            RefineryOrderDetailRoute(viewModel = detailModel)
                        }
                    },
            ) {
                RefineryOrdersRoute(
                    viewModel = refinery,
                    onOpenOrder = {
                        if (wide) selected = it else navController.navigate(refineryOrderRoute(it))
                    },
                )
            }
        }

        else -> {
            return false
        }
    }
    return true
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
 * @param orderDetail builds a view model for one order.
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
    orderDetail: (String) -> OrderDetailViewModel,
    refineryOrder: (String) -> RefineryDetailViewModel,
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
            )
        }

        KrtDestination.BankAccount -> {
            val accountId = backStackEntry.arguments?.getString(ACCOUNT_ID_ARG).orEmpty()
            val viewModel = remember(accountId) { bankAccount(accountId) }
            LaunchedEffect(accountId) { viewModel.load() }
            BankAccountRoute(viewModel = viewModel)
        }

        KrtDestination.OrderDetail -> {
            val orderId = backStackEntry.arguments?.getString(ORDER_ID_ARG).orEmpty()
            val viewModel = remember(orderId) { orderDetail(orderId) }
            LaunchedEffect(orderId) { viewModel.load() }
            OrderDetailRoute(viewModel = viewModel)
        }

        KrtDestination.RefineryOrder -> {
            val orderId = backStackEntry.arguments?.getString(REFINERY_ORDER_ID_ARG).orEmpty()
            val viewModel = remember(orderId) { refineryOrder(orderId) }
            RefineryOrderDetailRoute(viewModel = viewModel)
        }

        KrtDestination.More -> {
            MoreScreen(onOpen = onOpenDestination)
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

        KrtDestination.Licenses -> {
            LicensesScreen(onOpenUrl = settings.onOpenUrl)
        }

        KrtDestination.FleetImport -> {
            FleetImportRoute(viewModel = fleetImport)
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
)
