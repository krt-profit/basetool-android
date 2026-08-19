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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import de.greluc.krt.profit.basetool.android.auth.AuthContainer
import de.greluc.krt.profit.basetool.android.auth.CustomTabLauncher
import de.greluc.krt.profit.basetool.android.auth.LoginScreen
import de.greluc.krt.profit.basetool.android.auth.LoginViewModel
import de.greluc.krt.profit.basetool.android.core.auth.AppLockKey
import de.greluc.krt.profit.basetool.android.core.auth.AuthenticatedCipher
import de.greluc.krt.profit.basetool.android.core.auth.SessionState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.gate.AccountGate
import de.greluc.krt.profit.basetool.android.gate.AccountGateViewModel
import de.greluc.krt.profit.basetool.android.lock.AppLockGate
import de.greluc.krt.profit.basetool.android.lock.AppLockViewModel
import de.greluc.krt.profit.basetool.android.lock.BiometricGate
import de.greluc.krt.profit.basetool.android.navigation.BasetoolApp
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
 */
class MainActivity : FragmentActivity() {
    private val container by lazy { AuthContainer(this) }
    private val loginViewModel by lazy { LoginViewModel(container) }
    private val gateViewModel by lazy { AccountGateViewModel(container.accountGate) }
    private val lockViewModel by lazy { AppLockViewModel(container.appLock) }
    private val termsViewModel by lazy { TermsGateViewModel(container.terms) }

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
                            BasetoolApp(
                                onLogout = signOut,
                                appLockEnabled = lockArmed,
                                appLockAvailable = lockAvailable,
                                onAppLockChange = { wanted ->
                                    if (wanted) {
                                        // Arming raises the same prompt as unlocking: the key
                                        // will not encrypt unattended either. It also means a lock
                                        // is only ever armed by somebody who just proved they can
                                        // open it. Whether the prompt carries a CryptoObject is the
                                        // platform's answer, not an assumption.
                                        scope.launch {
                                            lockViewModel.prepareArm()?.let { request ->
                                                BiometricGate.prompt(
                                                    activity = this@MainActivity,
                                                    cipher =
                                                        (request as? AuthenticatedCipher.Bound)
                                                            ?.cipher,
                                                    useCryptoObject =
                                                        request is AuthenticatedCipher.Bound,
                                                    onSuccess = lockViewModel::completeArm,
                                                    onFailure = { },
                                                )
                                            }
                                        }
                                    } else {
                                        lockViewModel.setEnabled(false)
                                    }
                                },
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
                        onOpenPrivacy = {},
                        onOpenImprint = {},
                        versionName = version.versionName.orEmpty(),
                        versionCode = BuildConfig.VERSION_CODE,
                    )
                }
            }
        }
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
}
