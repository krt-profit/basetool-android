/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.auth.AuthContainer
import de.greluc.krt.profit.basetool.android.auth.CustomTabLauncher
import de.greluc.krt.profit.basetool.android.auth.LoginScreen
import de.greluc.krt.profit.basetool.android.auth.LoginViewModel
import de.greluc.krt.profit.basetool.android.core.auth.SessionState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.navigation.BasetoolApp
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
 * Edge-to-edge is enabled before `super.onCreate` so the very first frame already draws behind the
 * system bars; at targetSdk 36 and above the platform enforces it anyway and there is no opt-out.
 */
class MainActivity : ComponentActivity() {
    private val container by lazy { AuthContainer(this) }
    private val loginViewModel by lazy { LoginViewModel(container) }

    /**
     * Enables edge-to-edge drawing and installs the Compose content.
     *
     * @param savedInstanceState the recreation state, restored by the navigation graph itself.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The redirect routinely arrives on a COLD start: the process is killed behind the Custom
        // Tab often enough that handling it only in onNewIntent would lose every login on a device
        // under memory pressure.
        loginViewModel.completeLogin(CustomTabLauncher.redirectOf(intent)?.toString())
        setContent {
            KrtTheme {
                val session by container.session.state.collectAsState()
                val login by loginViewModel.state.collectAsState()
                val version = remember { packageManager.getPackageInfo(packageName, 0) }

                LaunchedEffect(Unit) { container.session.restore() }

                when (session) {
                    is SessionState.SignedIn -> {
                        val scope = rememberCoroutineScope()
                        BasetoolApp(
                            onLogout = {
                                scope.launch {
                                    // The local wipe happens inside logout() and does not depend on
                                    // the browser; the URL only ends the realm's SSO cookie, without
                                    // which the next login silently reuses the browser session.
                                    container.logout()?.let { endSession ->
                                        CustomTabLauncher.launch(this@MainActivity, endSession)
                                    }
                                }
                            },
                        )
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
