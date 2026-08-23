/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.greluc.krt.profit.basetool.android.auth.AuthContainer
import de.greluc.krt.profit.basetool.android.auth.CustomTabLauncher
import de.greluc.krt.profit.basetool.android.auth.LoginScreen
import de.greluc.krt.profit.basetool.android.auth.LoginViewModel
import de.greluc.krt.profit.basetool.android.bank.BankAccountViewModel
import de.greluc.krt.profit.basetool.android.bank.BankViewModel
import de.greluc.krt.profit.basetool.android.core.auth.SessionState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.dashboard.DashboardViewModel
import de.greluc.krt.profit.basetool.android.gate.AccountGate
import de.greluc.krt.profit.basetool.android.gate.AccountGateViewModel
import de.greluc.krt.profit.basetool.android.hangar.HangarViewModel
import de.greluc.krt.profit.basetool.android.inventory.BookingViewModel
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.lock.AppLockGate
import de.greluc.krt.profit.basetool.android.lock.AppLockViewModel
import de.greluc.krt.profit.basetool.android.lock.BiometricGate
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.navigation.BasetoolApp
import de.greluc.krt.profit.basetool.android.navigation.SettingsBindings
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.orgunit.OrgUnitViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalBlueprintsViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalInventoryViewModel
import de.greluc.krt.profit.basetool.android.settings.LanguageSetting
import de.greluc.krt.profit.basetool.android.terms.TermsGate
import de.greluc.krt.profit.basetool.android.terms.TermsGateViewModel
import kotlinx.coroutines.launch

/**
 * The single activity of the app.
 *
 * Single-activity by design: the navigation graph owns every screen, which is what lets the back
 * rules of the design specification hold — per-destination back stacks, back from a root returning
 * to Übersicht, and back on Übersicht simply finishing the activity.
 *
 * It also owns the auth gate, and the four session states are kept apart deliberately.
 * [SessionState.Unknown] is the moment before `restore()` has answered — showing a login screen
 * there would flash it in front of a member who is signed in. [SessionState.Stale] means a stored
 * session that could not be proven right now, a tunnel rather than a logout, so it must never ask
 * for a password (ADR-0004); today it falls to the login screen with its own message, and gets its
 * proper retry surface with the chapter-14 system states.
 *
 * A session is **not** admission, so [SessionState.SignedIn] hands off to [AccountGate] rather than
 * straight to the app: the backend refuses every gated endpoint while a registration is unapproved,
 * and a dashboard composed on top of that would fire a screenful of requests only to paint their
 * failures. The gate composes the app only once the member is cleared.
 *
 * Edge-to-edge is enabled before `super.onCreate` so the very first frame already draws behind the
 * system bars; at targetSdk 36 and above the platform enforces it anyway and there is no opt-out.
 *
 * It is an `AppCompatActivity` — a `FragmentActivity` (which `BiometricPrompt` needs) with
 * AppCompat's delegate around it. The delegate is what applies a per-app language below API 33,
 * where the platform has no `LocaleManager`; without it the Sprache setting would take effect on
 * Android 13+ and silently do nothing on the two versions above the minSdk floor (ADR-0007).
 */
class MainActivity : AppCompatActivity() {
    /**
     * The process-wide auth graph.
     *
     * Read from the application rather than built here: a second [AuthContainer] means a second
     * DataStore on the token file, which throws, and every activity recreation would build one.
     */
    private val container: AuthContainer
        get() = (application as BasetoolApplication).auth

    /**
     * The four view models, held by the **ViewModelStore** rather than by the activity instance.
     *
     * A configuration change recreates the activity but not its view-model store, so the lock stays
     * open, a login in flight stays in flight and the approval poll keeps its state. With plain
     * `by lazy` fields all four were rebuilt from scratch on every recreate — which nothing
     * exercised while the single activity was never recreated, and which the language switch turned
     * into an everyday event.
     */
    private val loginViewModel: LoginViewModel by viewModels { authViewModels(container) }
    private val gateViewModel: AccountGateViewModel by viewModels { authViewModels(container) }
    private val lockViewModel: AppLockViewModel by viewModels { authViewModels(container) }
    private val termsViewModel: TermsGateViewModel by viewModels { authViewModels(container) }
    private val orgUnitViewModel: OrgUnitViewModel by viewModels { authViewModels(container) }
    private val missionsViewModel: MissionsViewModel by viewModels { authViewModels(container) }

    private val operationsViewModel: OperationsViewModel by viewModels { authViewModels(container) }

    private val notificationsViewModel: NotificationsViewModel by
        viewModels { authViewModels(container) }

    private val dashboardViewModel: DashboardViewModel by viewModels { authViewModels(container) }

    private val hangarViewModel: HangarViewModel by viewModels { authViewModels(container) }

    private val bankViewModel: BankViewModel by viewModels { authViewModels(container) }

    private val ordersViewModel: OrdersViewModel by viewModels { authViewModels(container) }

    private val inventoryViewModel: InventoryViewModel by viewModels { authViewModels(container) }
    private val personalInventoryViewModel: PersonalInventoryViewModel by
        viewModels { authViewModels(container) }
    private val personalBlueprintsViewModel: PersonalBlueprintsViewModel by
        viewModels { authViewModels(container) }
    private val bookingViewModel: BookingViewModel by viewModels { authViewModels(container) }

    /**
     * Enables edge-to-edge drawing and installs the Compose content.
     *
     * @param savedInstanceState the recreation state, restored by the navigation graph itself.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        blockScreenCapture()
        lockViewModel.start()
        // The redirect routinely arrives on a COLD start: the process is killed behind the Custom
        // Tab often enough that handling it only in onNewIntent would lose every login on a device
        // under memory pressure.
        loginViewModel.completeLogin(CustomTabLauncher.redirectOf(intent)?.toString())
        setContent {
            KrtTheme {
                Content()
            }
        }
    }

    /**
     * Everything the activity renders, extracted so `onCreate` stays a lifecycle method.
     *
     * The gate order is the shape of the app and is easiest to read in one place: **lock → session
     * → approval → terms → app**. Each gate takes the next as a lambda rather than drawing it
     * underneath, so a blocked stage never composes the one behind it and starts loads against
     * endpoints that are about to refuse them.
     */
    @Composable
    private fun Content() {
        val session by container.session.state.collectAsState()
        val login by loginViewModel.state.collectAsState()
        val version = remember { packageManager.getPackageInfo(packageName, 0) }

        val scope = rememberCoroutineScope()
        val signOut: () -> Unit = {
            scope.launch {
                // The local wipe happens inside logout() and does not depend on the
                // browser; the URL only ends the realm's SSO cookie, without which the
                // next login silently reuses the browser session.
                container.logout()?.let { endSession ->
                    CustomTabLauncher.launch(this@MainActivity, endSession)
                }
            }
        }

        // Held in state so the segmented control moves on tap rather than a frame later: the
        // platform recreates the activity right after, and the recreated one reads the store.
        var language by remember { mutableStateOf(LanguageSetting.current()) }

        val lockArmed by container.appLockArmed.collectAsState(initial = false)
        // Queried once per composition rather than per frame: enrolling a fingerprint
        // takes the member out of the app, so the answer cannot change under them.
        val lockAvailable = remember { BiometricGate.isAvailable(this@MainActivity) }

        // Outermost gate: the lock protects what is already on the device, so it runs
        // ahead of the session and the account gate rather than waiting on either.
        AppLockGate(
            viewModel = lockViewModel,
            activity = this@MainActivity,
            onSignOut = signOut,
        ) {
            // Inside the gate, not above it: the stored refresh token is sealed by the lock, so a
            // restore attempted while locked reads nothing and settles the session on "signed out"
            // — leaving a member with a perfectly good session staring at the login screen after
            // every unlock. Composed here it runs once the lock is open, and with no lock armed
            // this content composes immediately, so nothing changes for anyone who has not enabled
            // it.
            //
            // Guarded on Unknown so a background re-lock does not spend a refresh round trip (and
            // a rotation of the realm's refresh token) every time the member comes back.
            LaunchedEffect(Unit) {
                if (container.session.state.value is SessionState.Unknown) {
                    container.session.restore()
                }
            }

            when (val current = session) {
                is SessionState.SignedIn -> {
                    AccountGate(
                        viewModel = gateViewModel,
                        accountName = current.claims?.preferredUsername,
                        onLogout = signOut,
                    ) {
                        // After the approval gate, before the app: the backend enforces the
                        // same order, and a member still awaiting approval has nothing to consent
                        // to yet.
                        TermsGate(viewModel = termsViewModel, onDecline = signOut) {
                            // Behind the terms gate on purpose: the memberships read needs a
                            // cleared account, and asking earlier would spend a refused request
                            // on every start for a member who is still waiting for approval.
                            LaunchedEffect(Unit) { orgUnitViewModel.load() }
                            val orgUnit by orgUnitViewModel.state.collectAsState()
                            BasetoolApp(
                                orgUnit = orgUnit,
                                missions = missionsViewModel,
                                missionDetail = {
                                    MissionDetailViewModel(
                                        container.missions,
                                        container.identity,
                                        container.connectivity,
                                        it,
                                        container.liveSync,
                                    )
                                },
                                operations = operationsViewModel,
                                notifications = notificationsViewModel,
                                dashboard = dashboardViewModel,
                                hangar = hangarViewModel,
                                bank = bankViewModel,
                                bankAccount = {
                                    BankAccountViewModel(
                                        container.bank,
                                        container.connectivity,
                                        it,
                                        container.liveSync,
                                    )
                                },
                                orders = ordersViewModel,
                                orderDetail = {
                                    OrderDetailViewModel(
                                        container.orders,
                                        container.identity,
                                        container.connectivity,
                                        it,
                                        container.liveSync,
                                    )
                                },
                                inventory = inventoryViewModel,
                                personalInventory = personalInventoryViewModel,
                                personalBlueprints = personalBlueprintsViewModel,
                                booking = bookingViewModel,
                                operationDetail = {
                                    OperationDetailViewModel(
                                        container.operations,
                                        container.identity,
                                        container.connectivity,
                                        it,
                                        container.liveSync,
                                    )
                                },
                                onSelectOrgUnit = orgUnitViewModel::select,
                                onLogout = signOut,
                                settings =
                                    SettingsBindings(
                                        accountName = current.claims?.preferredUsername,
                                        language = language,
                                        onLanguageChange = { chosen ->
                                            language = chosen
                                            LanguageSetting.apply(chosen)
                                        },
                                        appLockEnabled = lockArmed,
                                        appLockAvailable = lockAvailable,
                                        onAppLockChange = { wanted ->
                                            if (wanted) {
                                                // Arming raises the same prompt as unlocking: the
                                                // key is auth-per-use, so Keystore will not encrypt
                                                // with it unattended. It also means a lock is only
                                                // ever armed by somebody who just proved they can
                                                // open it.
                                                scope.launch {
                                                    lockViewModel.prepareArm()?.let { cipher ->
                                                        BiometricGate.prompt(
                                                            activity = this@MainActivity,
                                                            cipher = cipher,
                                                            onSuccess = lockViewModel::completeArm,
                                                            onFailure = { },
                                                        )
                                                    }
                                                }
                                            } else {
                                                lockViewModel.setEnabled(false)
                                            }
                                        },
                                        onOpenPrivacy = { openWebPage(PRIVACY_PATH) },
                                        onOpenImprint = { openWebPage(IMPRINT_PATH) },
                                        onOpenTerms = { openWebPage(TERMS_PATH) },
                                        onOpenUrl = { url ->
                                            CustomTabLauncher.launch(this@MainActivity, url)
                                        },
                                        versionCode = BuildConfig.VERSION_CODE,
                                    ),
                            )
                        }
                    }
                }

                SessionState.Unknown -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KrtLoadingIndicator(text = stringResource(R.string.login_signing_in))
                    }
                }

                else -> {
                    LoginScreen(
                        state = login,
                        onSignIn = { loginViewModel.startLogin(this@MainActivity) },
                        // Both were empty stubs until the settings chapter gave the app a
                        // place to put the same documents. They matter MORE here than there: the
                        // privacy notice has to be reachable before any processing starts, and
                        // processing starts with the sign-in tap.
                        onOpenPrivacy = { openWebPage(PRIVACY_PATH) },
                        onOpenImprint = { openWebPage(IMPRINT_PATH) },
                        versionName = version.versionName.orEmpty(),
                        versionCode = BuildConfig.VERSION_CODE,
                    )
                }
            }
        }
    }

    /**
     * Opens one of the web app's public pages in a Custom Tab.
     *
     * These three documents are served by the web frontend without a session and are the SAME
     * texts the web app shows, which is the point: a member reading the privacy notice in the app
     * and one reading it in a browser must not be reading two different documents that drift apart.
     * A Custom Tab rather than a WebView, for the reasons in [CustomTabLauncher].
     *
     * @param path the page's path, including the leading slash
     */
    private fun openWebPage(path: String) {
        CustomTabLauncher.launch(this, BuildConfig.WEB_BASE_URL + path)
    }

    /**
     * Turns off screenshots, screen recording and casting for the whole app.
     *
     * App-wide, not only on authenticated screens: the design chapter fixes it that way and the
     * security concept repeats it, because the capture that matters is the one nobody takes
     * deliberately — the recents thumbnail the system grabs when the app leaves the foreground,
     * which then sits in the launcher. Called before `setContent` so it covers the very first
     * frame.
     *
     * Google's own figures put its effectiveness near 70 % at API 30 and below, so this is
     * hardening rather than a guarantee, and nothing else may be justified by its presence.
     */
    private fun blockScreenCapture() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    /**
     * Starts the background clock the re-lock rule measures against.
     *
     * `onStop`, not `onPause`: `onPause` also fires for a dialog or the permission sheet, and
     * re-locking behind those would make the app unusable.
     */
    override fun onStop() {
        super.onStop()
        lockViewModel.onBackgrounded(SystemClock.elapsedRealtime())
    }

    /**
     * Re-locks when the app was away longer than the grace period.
     */
    override fun onStart() {
        super.onStart()
        lockViewModel.onForegrounded(SystemClock.elapsedRealtime())
    }

    /**
     * Receives the authorization redirect when the process survived the browser.
     *
     * @param intent the intent `AuthRedirectActivity` forwarded
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loginViewModel.completeLogin(CustomTabLauncher.redirectOf(intent)?.toString())
    }

    private companion object {
        /**
         * Builds the four view models from the process-wide auth graph.
         *
         * @param container the auth object graph
         * @return a factory the `viewModels()` delegates share
         */
        fun authViewModels(container: AuthContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LoginViewModel(container) }
                initializer { AccountGateViewModel(container.accountGate) }
                initializer { AppLockViewModel(container.appLock) }
                initializer { TermsGateViewModel(container.terms) }
                initializer { OrgUnitViewModel(container.orgUnits, container.activeOrgUnit) }
                initializer { MissionsViewModel(container.missions, container.liveSync) }
                initializer { OperationsViewModel(container.operations) }
                initializer { NotificationsViewModel(container.notifications) }
                initializer { HangarViewModel(container.hangar, container.connectivity) }
                initializer { BankViewModel(container.bank, container.liveSync) }
                initializer { OrdersViewModel(container.orders, container.liveSync) }
                initializer {
                    InventoryViewModel(container.inventory, container.connectivity, container.liveSync)
                }
                initializer { BookingViewModel(container.inventory, container.connectivity) }
                initializer {
                    PersonalInventoryViewModel(container.personalInventory, container.connectivity)
                }
                initializer {
                    PersonalBlueprintsViewModel(container.personalBlueprints, container.connectivity)
                }
                initializer {
                    DashboardViewModel(
                        container.missions,
                        container.announcements,
                        container.serverClock,
                    )
                }
            }

        /** Path of the privacy notice on the web frontend; `permitAll` there, hence linkable. */
        const val PRIVACY_PATH = "/privacy"

        /** Path of the imprint. */
        const val IMPRINT_PATH = "/impressum"

        /** Path of the terms of use. */
        const val TERMS_PATH = "/terms"
    }
}
