/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.auth.AuthorizationRequest
import de.greluc.krt.profit.basetool.android.core.auth.AuthorizationResponse
import de.greluc.krt.profit.basetool.android.core.auth.LoginResult
import de.greluc.krt.profit.basetool.android.core.auth.SecretCipherException
import de.greluc.krt.profit.basetool.android.core.auth.TokenResult
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one login attempt from the button tap to the session.
 *
 * [startLogin] saves the attempt before launching the browser, because the process may die before
 * the redirect arrives (REQ-APP-AUTH-008).
 *
 * @property container the auth graph
 */
class LoginViewModel(
    private val container: AuthContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    /** What the login screen renders. */
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    private val mutableOnline = MutableStateFlow(true)

    /**
     * Whether the device has a network connection, as opposed to whether the server is reachable.
     *
     * Without one the screen says up front that sign-in cannot start.
     */
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    init {
        viewModelScope.launch {
            container.connectivity.online.collect { mutableOnline.value = it }
        }
    }

    /**
     * Starts a login: mints an attempt, persists it, and opens the Custom Tab.
     *
     * @param context the activity starting the browser
     */
    fun startLogin(context: Context) {
        mutableState.value = LoginUiState.Working
        viewModelScope.launch {
            val request = container.authorizationRequests.create()
            val saved =
                try {
                    container.pendingAuthorization.save(request)
                    true
                } catch (unusable: SecretCipherException) {
                    KrtLog.e(LOG_TAG, unusable) { "login attempt could not be stored" }
                    false
                }
            mutableState.value =
                when {
                    !saved -> {
                        LoginUiState.Failed(R.string.login_error_device_key)
                    }

                    !CustomTabLauncher.launch(context, request.url) -> {
                        LoginUiState.Failed(R.string.login_error_no_browser)
                    }

                    else -> {
                        LoginUiState.Working
                    }
                }
        }
    }

    /**
     * Completes a login from the redirect the browser delivered.
     *
     * @param redirect the redirect URI, or `null` when the intent carried none
     */
    fun completeLogin(redirect: String?) {
        if (redirect == null) return
        viewModelScope.launch {
            val request = container.pendingAuthorization.peek()
            if (request == null) {
                KrtLog.d(LOG_TAG) { "redirect arrived with no pending attempt" }
                mutableState.value = LoginUiState.Idle
                return@launch
            }
            val response = request.readRedirect(redirect)
            if (response !is AuthorizationResponse.StateMismatch) {
                container.pendingAuthorization.clear()
            }
            mutableState.value =
                when (response) {
                    is AuthorizationResponse.Code -> redeem(request, response.code)
                    is AuthorizationResponse.Denied -> LoginUiState.Failed(R.string.login_error_denied)
                    AuthorizationResponse.StateMismatch -> LoginUiState.Failed(R.string.login_error_expired)
                    is AuthorizationResponse.Unusable -> LoginUiState.Failed(R.string.login_error_cancelled)
                }
        }
    }

    /**
     * Redeems the authorization code.
     *
     * @param request the attempt the code belongs to
     * @param code the authorization code
     * @return the state to publish; a granted session leaves the screen behind entirely
     */
    private suspend fun redeem(
        request: AuthorizationRequest,
        code: String,
    ): LoginUiState =
        when (val result = container.session.completeLogin(request, code)) {
            is LoginResult.SignedIn -> LoginUiState.Idle
            LoginResult.NonceMismatch -> LoginUiState.Failed(R.string.login_error_expired)
            is LoginResult.Failed -> LoginUiState.Failed(messageFor(result.reason))
        }

    /**
     * Maps a token-endpoint outcome onto something a member can act on.
     *
     * @param reason what the token endpoint answered
     * @return the string resource to show
     */
    private fun messageFor(reason: TokenResult): Int =
        when (reason) {
            is TokenResult.Unreachable -> R.string.login_error_unreachable
            is TokenResult.SessionEnded -> R.string.login_error_expired
            else -> R.string.login_error_config
        }

    private companion object {
        const val LOG_TAG = "auth"
    }
}
