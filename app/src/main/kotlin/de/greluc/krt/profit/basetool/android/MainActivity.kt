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
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.InitializerViewModelFactoryBuilder
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.greluc.krt.profit.basetool.android.auth.AuthContainer
import de.greluc.krt.profit.basetool.android.auth.CustomTabLauncher
import de.greluc.krt.profit.basetool.android.auth.LoginScreen
import de.greluc.krt.profit.basetool.android.auth.LoginViewModel
import de.greluc.krt.profit.basetool.android.bank.BankAccountViewModel
import de.greluc.krt.profit.basetool.android.bank.BankHolderViewModel
import de.greluc.krt.profit.basetool.android.bank.BankLifecycleViewModel
import de.greluc.krt.profit.basetool.android.bank.BankRequestsViewModel
import de.greluc.krt.profit.basetool.android.bank.BankStaffSeams
import de.greluc.krt.profit.basetool.android.bank.BankStaffViewModel
import de.greluc.krt.profit.basetool.android.bank.BankViewModel
import de.greluc.krt.profit.basetool.android.core.auth.SessionState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.dashboard.DashboardViewModel
import de.greluc.krt.profit.basetool.android.exchange.MaterialBoardViewModel
import de.greluc.krt.profit.basetool.android.gate.AccountGate
import de.greluc.krt.profit.basetool.android.gate.AccountGateViewModel
import de.greluc.krt.profit.basetool.android.gate.UpdateGate
import de.greluc.krt.profit.basetool.android.gate.UpdateGateViewModel
import de.greluc.krt.profit.basetool.android.hangar.FleetImportViewModel
import de.greluc.krt.profit.basetool.android.hangar.HangarViewModel
import de.greluc.krt.profit.basetool.android.inventory.BookingViewModel
import de.greluc.krt.profit.basetool.android.inventory.GameItemStockViewModel
import de.greluc.krt.profit.basetool.android.inventory.InventoryViewModel
import de.greluc.krt.profit.basetool.android.lock.AppLockGate
import de.greluc.krt.profit.basetool.android.lock.AppLockViewModel
import de.greluc.krt.profit.basetool.android.lock.BiometricGate
import de.greluc.krt.profit.basetool.android.materials.MaterialDetailViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialMatrixViewModel
import de.greluc.krt.profit.basetool.android.materials.MaterialsViewModel
import de.greluc.krt.profit.basetool.android.materials.ProfitViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.MissionSeams
import de.greluc.krt.profit.basetool.android.missions.MissionsViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationDetailViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationFormViewModel
import de.greluc.krt.profit.basetool.android.missions.OperationsViewModel
import de.greluc.krt.profit.basetool.android.navigation.BasetoolApp
import de.greluc.krt.profit.basetool.android.navigation.BlueprintOverviewBindings
import de.greluc.krt.profit.basetool.android.navigation.SettingsBindings
import de.greluc.krt.profit.basetool.android.notifications.NotificationsViewModel
import de.greluc.krt.profit.basetool.android.notifications.RequestNotificationPermissionOnce
import de.greluc.krt.profit.basetool.android.orders.MaterialDemandViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderCollectionViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderCreateViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderDetailSources
import de.greluc.krt.profit.basetool.android.orders.OrderDetailViewModel
import de.greluc.krt.profit.basetool.android.orders.OrderFormMode
import de.greluc.krt.profit.basetool.android.orders.OrdersViewModel
import de.greluc.krt.profit.basetool.android.orgunit.OrgUnitViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.BlueprintOverviewViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalBlueprintsViewModel
import de.greluc.krt.profit.basetool.android.personalinventory.PersonalInventoryViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryCreateViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryDetailSeams
import de.greluc.krt.profit.basetool.android.refinery.RefineryDetailViewModel
import de.greluc.krt.profit.basetool.android.refinery.RefineryViewModel
import de.greluc.krt.profit.basetool.android.settings.LanguageSetting
import de.greluc.krt.profit.basetool.android.settings.MemberPreferencesViewModel
import de.greluc.krt.profit.basetool.android.settings.ScreenCapturePreference
import de.greluc.krt.profit.basetool.android.terms.TermsGate
import de.greluc.krt.profit.basetool.android.terms.TermsGateViewModel
import de.greluc.krt.profit.basetool.android.ui.CallerViewModel
import kotlinx.coroutines.launch
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The single activity of the app; the navigation graph owns every screen.
 *
 * It hosts the auth gate: [SessionState.Unknown] shows no login screen, [SessionState.Stale] never
 * asks for a password (ADR-0004), and [SessionState.SignedIn] hands off to [AccountGate]. Edge-to-edge
 * is enabled before `super.onCreate`. An `AppCompatActivity` so `BiometricPrompt` works and the
 * per-app language applies below API 33 (ADR-0007).
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
     * The four view models, held by the ViewModelStore so they survive an activity recreate.
     */
    private val loginViewModel: LoginViewModel by viewModels { authViewModels(container) }
    private val gateViewModel: AccountGateViewModel by viewModels { authViewModels(container) }

    /** Decides whether this build may run at all. */
    private val updateGateViewModel: UpdateGateViewModel by viewModels {
        authViewModels(container)
    }

    /**
     * The member's screen-capture choice.
     *
     * Read from the application rather than built here, for the same reason [container] is: a
     * second store on `krt_settings` throws, and every activity recreation would build one. The
     * *flag* it drives is per-window; the store behind it is per-process.
     */
    private val screenCapturePreference: ScreenCapturePreference
        get() = (application as BasetoolApplication).screenCapture

    private val lockViewModel: AppLockViewModel by viewModels { authViewModels(container) }
    private val termsViewModel: TermsGateViewModel by viewModels { authViewModels(container) }
    private val orgUnitViewModel: OrgUnitViewModel by viewModels { authViewModels(container) }

    /** The two Einstellungen rows that live on the server (design ch. 13, artboard 2). */
    private val memberPreferencesViewModel: MemberPreferencesViewModel by viewModels {
        authViewModels(container)
    }
    private val missionsViewModel: MissionsViewModel by viewModels { authViewModels(container) }

    private val operationsViewModel: OperationsViewModel by viewModels { authViewModels(container) }

    private val notificationsViewModel: NotificationsViewModel by
        viewModels { authViewModels(container) }

    private val dashboardViewModel: DashboardViewModel by viewModels { authViewModels(container) }

    private val callerViewModel: CallerViewModel by viewModels { authViewModels(container) }
    private val hangarViewModel: HangarViewModel by viewModels { authViewModels(container) }
    private val fleetImportViewModel: FleetImportViewModel by viewModels { authViewModels(container) }

    private val bankViewModel: BankViewModel by viewModels { authViewModels(container) }
    private val bankRequestsViewModel: BankRequestsViewModel by viewModels {
        authViewModels(container)
    }
    private val bankStaffViewModel: BankStaffViewModel by viewModels { authViewModels(container) }
    private val bankLifecycleViewModel: BankLifecycleViewModel by viewModels {
        authViewModels(container)
    }

    private val ordersViewModel: OrdersViewModel by viewModels { authViewModels(container) }

    /** The member's own Raffinerie orders; activity-scoped like every other list. */
    private val refineryViewModel: RefineryViewModel by viewModels { authViewModels(container) }

    /** „Handel" — the material catalogue with its UEX prices. */
    private val materialsViewModel: MaterialsViewModel by viewModels { authViewModels(container) }

    /** The Materialbörse. */
    private val exchangeViewModel: MaterialBoardViewModel by viewModels {
        authViewModels(container)
    }

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
        followScreenCapturePreference()
        lockViewModel.start()
        loginViewModel.completeLogin(CustomTabLauncher.redirectOf(intent)?.toString())
        setContent {
            KrtTheme {
                Content()
            }
        }
    }

    /**
     * Everything the activity renders, as the gate chain lock, session, approval, terms, app.
     *
     * Each gate composes the next as a lambda, so a blocked stage never composes the one behind it.
     */
    @Composable
    private fun Content() {
        val session by container.session.state.collectAsState()
        val login by loginViewModel.state.collectAsState()
        val loginOnline by loginViewModel.online.collectAsState()
        val version = remember { packageManager.getPackageInfo(packageName, 0) }

        val scope = rememberCoroutineScope()
        val signOut: () -> Unit = {
            scope.launch {
                container.logout()?.let { endSession ->
                    CustomTabLauncher.launch(this@MainActivity, endSession)
                }
            }
        }

        var language by remember { mutableStateOf(LanguageSetting.current()) }

        val lockArmed by container.appLockArmed.collectAsState(initial = false)
        val captureBlocked by screenCapturePreference.blocked.collectAsState(initial = true)
        val lockAvailable = remember { BiometricGate.isAvailable(this@MainActivity) }

        UpdateGate(
            viewModel = updateGateViewModel,
            onOpenReleases = { CustomTabLauncher.launch(this@MainActivity, it) },
            onExit = { finish() },
        ) {
            AppLockGate(
                viewModel = lockViewModel,
                activity = this@MainActivity,
                onSignOut = signOut,
            ) {
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
                            TermsGate(viewModel = termsViewModel, onDecline = signOut) {
                                LaunchedEffect(Unit) { orgUnitViewModel.load() }
                                RequestNotificationPermissionOnce()
                                val orgUnit by orgUnitViewModel.state.collectAsState()
                                val memberPreferences by
                                    memberPreferencesViewModel.state.collectAsState()
                                LaunchedEffect(Unit) { memberPreferencesViewModel.loadOnce() }
                                BasetoolApp(
                                    orgUnit = orgUnit,
                                    missions = missionsViewModel,
                                    missionDetail = {
                                        MissionDetailViewModel(
                                            MissionSeams(
                                                read = container.missions,
                                                admin = container.missions,
                                                structure = container.missionStructure,
                                                timeline = container.missionTimeline,
                                                people = container.missionTimeline,
                                            ),
                                            container.identity,
                                            container.connectivity,
                                            it,
                                            container.liveSync,
                                        )
                                    },
                                    operations = operationsViewModel,
                                    notifications = notificationsViewModel,
                                    dashboard = dashboardViewModel,
                                    caller = callerViewModel,
                                    hangar = hangarViewModel,
                                    fleetImport = fleetImportViewModel,
                                    bank = bankViewModel,
                                    bankRequests = bankRequestsViewModel,
                                    bankStaff = bankStaffViewModel,
                                    bankLifecycle = bankLifecycleViewModel,
                                    bankAccount = {
                                        BankAccountViewModel(
                                            container.bank,
                                            container.connectivity,
                                            it,
                                            container.liveSync,
                                            staff =
                                                BankStaffSeams(
                                                    reversals = container.bankStaff,
                                                    account = container.bankStaff,
                                                    reports = container.bankStaff,
                                                ),
                                            throughTheOffice = {
                                                container.identity.known?.bankEmployee == true
                                            },
                                        )
                                    },
                                    bankHolder = {
                                        BankHolderViewModel(
                                            container.bankStaff,
                                            container.bankStaff,
                                            it,
                                        )
                                    },
                                    orders = ordersViewModel,
                                    orderDetail = {
                                        OrderDetailViewModel(
                                            OrderDetailSources(
                                                orders = container.orders,
                                                work = container.orderWork,
                                                bookIn = container.inventory,
                                                claims = container.orderClaims,
                                                orgUnits = container.orgUnits,
                                                identity = container.identity,
                                                liveSync = container.liveSync,
                                            ),
                                            container.connectivity,
                                            it,
                                        )
                                    },
                                    inventory = inventoryViewModel,
                                    exchange = exchangeViewModel,
                                    refinery = refineryViewModel,
                                    materials = materialsViewModel,
                                    materialDetail = {
                                        MaterialDetailViewModel(
                                            container.materialCatalog,
                                            it,
                                            container.connectivity,
                                        )
                                    },
                                    materialMatrix = {
                                        MaterialMatrixViewModel(container.materialCatalog)
                                    },
                                    materialProfit = { ProfitViewModel(container.materialCatalog) },
                                    refineryCreate = { editedRun ->
                                        RefineryCreateViewModel(container.refinery, editedRun)
                                    },
                                    orderCreate = {
                                        OrderCreateViewModel(container.orders, container.orgUnits)
                                    },
                                    gameItems = { GameItemStockViewModel(container.inventory) },
                                    materialDemand = { MaterialDemandViewModel(container.materialDemand) },
                                    blueprints =
                                        BlueprintOverviewBindings(
                                            allowed =
                                                container.identity.known?.blueprintOverview == true,
                                            build = {
                                                BlueprintOverviewViewModel(
                                                    container.personalBlueprints,
                                                )
                                            },
                                        ),
                                    operationForm = { editedOperation ->
                                        OperationFormViewModel(container.operations, editedOperation)
                                    },
                                    orderCollection = { collectionId ->
                                        OrderCollectionViewModel(
                                            container.orderCollection,
                                            container.orders,
                                            collectionId,
                                        )
                                    },
                                    orderEdit = { editedId, editMode ->
                                        OrderCreateViewModel(
                                            source = container.orders,
                                            orgUnits = container.orgUnits,
                                            orders = container.orders,
                                            orderId = editedId,
                                            mode = editMode,
                                        )
                                    },
                                    refineryOrder = {
                                        RefineryDetailViewModel(
                                            container.refinery,
                                            container.connectivity,
                                            it,
                                            container.liveSync,
                                            RefineryDetailSeams(
                                                store = container.refinery,
                                                roster = container.inventory,
                                                delete = container.refinery,
                                                identity = container.identity,
                                            ),
                                        )
                                    },
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
                                    onSelectAllOrgUnits = orgUnitViewModel::selectAll,
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
                                            screenCaptureAllowed = !captureBlocked,
                                            onScreenCaptureChange = { allowed ->
                                                lifecycleScope.launch {
                                                    screenCapturePreference.set(blocked = !allowed)
                                                }
                                            },
                                            onAppLockChange = { wanted ->
                                                if (wanted) {
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
                                            preferences = memberPreferences,
                                            onPayout = memberPreferencesViewModel::onPayout,
                                            onSharing = memberPreferencesViewModel::onSharing,
                                            onRetryPreferences = memberPreferencesViewModel::refresh,
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

                    is SessionState.Stale -> {
                        LaunchedEffect(Unit) {
                            container.connectivity.online.collect { online ->
                                if (online) {
                                    container.session.restore()
                                }
                            }
                        }
                        Box(
                            Modifier.fillMaxSize().padding(KrtSpacing.s16),
                            contentAlignment = Alignment.Center,
                        ) {
                            KrtEmptyState(
                                iconRes = DesignR.drawable.ic_krt_wifi_off,
                                title = stringResource(R.string.session_stale_title),
                                message = stringResource(R.string.session_stale_message),
                                actionText = stringResource(R.string.session_stale_retry),
                                onAction = { scope.launch { container.session.restore() } },
                            )
                        }
                    }

                    else -> {
                        LoginScreen(
                            state = login,
                            onSignIn = { loginViewModel.startLogin(this@MainActivity) },
                            onOpenPrivacy = { openWebPage(PRIVACY_PATH) },
                            onOpenImprint = { openWebPage(IMPRINT_PATH) },
                            versionName = version.versionName.orEmpty(),
                            versionCode = BuildConfig.VERSION_CODE,
                            online = loginOnline,
                        )
                    }
                }
            }
        }
    }

    /**
     * Opens one of the web app's public pages in a Custom Tab, so the app shows the same documents as
     * the web.
     *
     * @param path the page's path, including the leading slash
     */
    private fun openWebPage(path: String) {
        CustomTabLauncher.launch(this, BuildConfig.WEB_BASE_URL + path)
    }

    /**
     * Applies the member's screen-capture choice to this window, app-wide.
     *
     * It also covers the recents thumbnail. This is hardening, not a guarantee.
     *
     * @param blocked `true` to set `FLAG_SECURE`, `false` to clear it.
     */
    private fun applyScreenCapture(blocked: Boolean) {
        if (blocked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Sets `FLAG_SECURE` immediately and relaxes it only once the stored choice allows it.
     *
     * A slow or failed read therefore leaves capture blocked. The preference is collected for the
     * activity's lifetime, so a change applies at once.
     */
    private fun followScreenCapturePreference() {
        applyScreenCapture(blocked = true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                screenCapturePreference.blocked.collect(::applyScreenCapture)
            }
        }
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
         * Registers the bank's three view models: member list, requests and staff surface.
         *
         * @param container the auth object graph.
         */
        private fun InitializerViewModelFactoryBuilder.bankViewModels(container: AuthContainer) {
            initializer { BankViewModel(container.bank, container.liveSync) }
            initializer {
                BankLifecycleViewModel(
                    container.bankStaff,
                    container.bankStaff,
                    container.bankStaff,
                    container.activeOrgUnit::current,
                )
            }
            initializer {
                BankStaffViewModel(
                    container.bankStaff,
                    container.bank::balances,
                    container.liveSync,
                    container.bankStaff,
                    container.orgUnits,
                )
            }
            initializer {
                BankRequestsViewModel(
                    container.bank,
                    container.bank::balances,
                    container.connectivity,
                    container.liveSync,
                )
            }
        }

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
                initializer {
                    UpdateGateViewModel(container.appVersion, BuildConfig.VERSION_CODE)
                }
                initializer { AppLockViewModel(container.appLock) }
                initializer { TermsGateViewModel(container.terms) }
                initializer {
                    OrgUnitViewModel(container.orgUnits, container.activeOrgUnit, container.identity)
                }
                initializer { MemberPreferencesViewModel(container.memberPreferences) }
                initializer { MissionsViewModel(container.missions, container.liveSync) }
                initializer { OperationsViewModel(container.operations) }
                initializer {
                    NotificationsViewModel(container.notifications, container.systemNotifications)
                }
                initializer { CallerViewModel(container.identity) }
                initializer { HangarViewModel(container.hangar, container.connectivity) }
                initializer { FleetImportViewModel(container.hangar, container.connectivity) }
                bankViewModels(container)
                initializer { OrdersViewModel(container.orders, container.liveSync, container.connectivity) }
                initializer { RefineryViewModel(container.refinery, container.identity, container.liveSync) }
                initializer { MaterialsViewModel(container.materialCatalog, container.connectivity) }
                initializer {
                    MaterialBoardViewModel(
                        container.materialBoard,
                        container.inventory,
                        container.connectivity,
                        container.liveSync,
                    )
                }
                initializer {
                    InventoryViewModel(
                        container.inventory,
                        container.connectivity,
                        container.liveSync,
                    )
                }
                initializer { BookingViewModel(container.inventory, container.connectivity) }
                initializer {
                    PersonalInventoryViewModel(container.personalInventory, container.connectivity)
                }
                initializer {
                    PersonalBlueprintsViewModel(
                        container.personalBlueprints,
                        container.personalBlueprints,
                        container.connectivity,
                    )
                }
                initializer {
                    DashboardViewModel(
                        container.missions,
                        container.announcements,
                        container.notifications,
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
