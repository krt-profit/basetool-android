/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.greluc.krt.profit.basetool.android.auth.LoginScreen
import de.greluc.krt.profit.basetool.android.auth.LoginUiState
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/**
 * Puts the login screen on a device before it is wired to anything.
 *
 * Compose previews render the tree; they do not answer what the screen is actually like at 412 dp
 * with the system bars in place, whether the bloom's falloff banded, or whether the footer still
 * fits above a gesture bar. That needs a device, and until the composition root exists there is no
 * route that reaches this screen — so the dev flavour gets its own launcher entry.
 *
 * **Dev flavour only.** A release build ships no development surfaces, and this one in particular
 * would be a login screen whose button logs nothing in.
 *
 * Tapping "Anmelden" cycles the states rather than starting a flow: what needs looking at here is
 * the resting layout, the disabled call to action while a login runs, and the error slot under it.
 */
class LoginPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KrtTheme {
                var state: LoginUiState by remember { mutableStateOf(LoginUiState.Idle) }
                LoginScreen(
                    state = state,
                    onSignIn = { state = next(state) },
                    onOpenPrivacy = {},
                    onOpenImprint = {},
                    onOpenTerms = {},
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            }
        }
    }

    /**
     * Cycles idle → working → refused → idle so every state can be seen with one thumb.
     *
     * @param current the state on screen
     * @return the next one to show
     */
    private fun next(current: LoginUiState): LoginUiState =
        when (current) {
            is LoginUiState.Idle -> LoginUiState.Working
            is LoginUiState.Working -> LoginUiState.Failed(R.string.login_error_denied)
            is LoginUiState.Failed -> LoginUiState.Idle
        }
}
