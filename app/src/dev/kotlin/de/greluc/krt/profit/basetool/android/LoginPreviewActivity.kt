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
 * Shows the login screen on a device without any wiring behind it, as a dev-flavour launcher entry.
 *
 * Tapping "Anmelden" cycles through the resting, in-progress and error states instead of starting a
 * flow. Dev flavour only.
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
