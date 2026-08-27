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
 * The order in [startLogin] is the part that matters: the attempt is **saved before the browser is
 * launched**, because after the launch this process may not run again until the redirect arrives
 * (`REQ-APP-AUTH-008`). Saving afterwards would work on every device with memory to spare and fail
 * on the ones without.
 *
 * @property container the auth graph
 */
class LoginViewModel(
    private val container: AuthContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    /** What the login screen renders. */
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

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
                    // The device cannot encrypt right now. Launching anyway would open a browser
                    // whose redirect nothing could complete.
                    KrtLog.e(LOG_TAG, unusable) { "login attempt could not be stored" }
                    false
                }
            mutableState.value =
                when {
                    !saved -> {
                        // Not the same failure as a refused token exchange: this one the member can
                        // usually fix, because the commonest cause is a device with no screen lock,
                        // which leaves Keystore unable to create the key the refresh token is
                        // sealed with.
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
                // No attempt is pending: a stale redirect, or the app was reinstalled while the
                // browser was open. Nothing to complete, and nothing to report either.
                KrtLog.d(LOG_TAG) { "redirect arrived with no pending attempt" }
                mutableState.value = LoginUiState.Idle
                return@launch
            }
            // The attempt is consumed only once the redirect turns out to BE this attempt's.
            // A state mismatch means somebody else's intent reached the exported activity, and
            // discarding on that would let any installed app end a login in flight — the member's
            // own redirect would then find nothing pending. Consuming on a real answer keeps the
            // single-use property that matters: a code is redeemed at most once.
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

            // AccessTokenBound, Rejected and Malformed all mean the same thing to a member: this
            // is not their fault and trying again will not help.
            else -> R.string.login_error_config
        }

    private companion object {
        const val LOG_TAG = "auth"
    }
}
