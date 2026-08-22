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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_MOTION_MS
import de.greluc.krt.profit.basetool.android.dashboard.DashboardScreen
import de.greluc.krt.profit.basetool.android.dashboard.DashboardViewModel
import de.greluc.krt.profit.basetool.android.hangar.HangarRoute
import de.greluc.krt.profit.basetool.android.hangar.HangarViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailRoute
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsRoute
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailRoute
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsRoute
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.NotificationsRoute
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.notificationDestination
import de.greluc.krt.profit.basetool.android.settings.AppLanguage
import de.greluc.krt.profit.basetool.android.settings.LicensesScreen
import de.greluc.krt.profit.basetool.android.settings.SettingsScreen
import de.greluc.krt.profit.basetool.android.ui.MoreScreen
import de.greluc.krt.profit.basetool.android.ui.PlaceholderScreen

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
 * @param memberName the signed-in member's name, for the dashboard greeting.
 * @param orgUnitName the active org unit's name, for the same line.
 * @param onLogout ends the session.
 * @param settings everything the Einstellungen screen needs that the graph cannot know.
 * @param modifier layout modifier.
 */
@Composable
fun BasetoolNavHost(
    navController: NavHostController,
    onOpenDestination: (KrtDestination) -> Unit,
    missions: MissionsViewModel,
    missionDetail: (String) -> MissionDetailViewModel,
    operations: OperationsViewModel,
    operationDetail: (String) -> OperationDetailViewModel,
    notifications: NotificationsViewModel,
    dashboard: DashboardViewModel,
    hangar: HangarViewModel,
    bank: BankViewModel,
    bankAccount: (String) -> BankAccountViewModel,
    memberName: String?,
    orgUnitName: String?,
    onLogout: () -> Unit,
    settings: SettingsBindings,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = KrtDestination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(KRT_MOTION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(KRT_MOTION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(KRT_MOTION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(KRT_MOTION_MS)) },
    ) {
        KrtDestination.entries.forEach { destination ->
            composable(
                route = destination.route,
                deepLinks = listOf(navDeepLink { uriPattern = destination.deepLink }),
            ) { backStackEntry ->
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
                        operations = operations,
                        notifications = notifications,
                        dashboard = dashboard,
                        hangar = hangar,
                        bank = bank,
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
                        onOpenDestination = onOpenDestination,
                        onLogout = onLogout,
                        settings = settings,
                    )
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
    operations: OperationsViewModel,
    notifications: NotificationsViewModel,
    dashboard: DashboardViewModel,
    hangar: HangarViewModel,
    bank: BankViewModel,
    memberName: String?,
    orgUnitName: String?,
): Boolean {
    when (destination) {
        KrtDestination.Home -> {
            LaunchedEffect(Unit) { dashboard.load() }
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
                onRefresh = dashboard::onRefresh,
                onOpenMission = { navController.navigate(missionDetailRoute(it)) },
                onOpenMissions = { navController.navigate(KrtDestination.Missions.route) },
                onOpenNotifications = { navController.navigate(KrtDestination.Notifications.route) },
            )
        }

        KrtDestination.Missions -> {
            LaunchedEffect(Unit) { missions.load() }
            MissionsRoute(
                viewModel = missions,
                onOpenMission = { navController.navigate(missionDetailRoute(it)) },
                // The segment navigates rather than toggling: both lists are their own destination,
                // and a local toggle would leave the navigation bar highlighting the one the member
                // is no longer looking at.
                onOpenOperations = { navController.navigate(KrtDestination.Operations.route) },
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
            HangarRoute(viewModel = hangar)
        }

        KrtDestination.Bank -> {
            LaunchedEffect(Unit) { bank.loadOnce() }
            BankAccountsRoute(
                viewModel = bank,
                onOpenAccount = { navController.navigate(bankAccountRoute(it)) },
            )
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
                language = settings.language,
                onLanguageChange = settings.onLanguageChange,
                appLockEnabled = settings.appLockEnabled,
                appLockAvailable = settings.appLockAvailable,
                onAppLockChange = settings.onAppLockChange,
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
 * @property appLockAvailable whether the device can prompt at all.
 * @property onAppLockChange arms or disarms the lock; arming raises the biometric prompt.
 * @property onOpenPrivacy opens the privacy policy in a browser.
 * @property onOpenImprint opens the imprint in a browser.
 * @property onOpenTerms opens the terms of use in a browser.
 * @property onOpenUrl opens an arbitrary URL in a browser; used by the open-source notice.
 * @property versionCode the app's build number, from `BuildConfig`.
 */
@Immutable
data class SettingsBindings(
    val accountName: String?,
    val language: AppLanguage,
    val onLanguageChange: (AppLanguage) -> Unit,
    val appLockEnabled: Boolean,
    val appLockAvailable: Boolean,
    val onAppLockChange: (Boolean) -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenImprint: () -> Unit,
    val onOpenTerms: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val versionCode: Int,
)
