/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.auth.AppLockKey
import de.greluc.krt.profit.basetool.android.core.auth.AuthenticatedCipher
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import kotlinx.coroutines.launch

/**
 * Seals the app behind the lock screen, and composes [content] only once it is open.
 *
 * The outermost gate of the app, ahead of the session and the account gate. That order is the point:
 * the lock protects what is **already on the device**, so it must not wait on a network round trip
 * to find out whether the member is approved — a locked app shows nothing while the account gate is
 * still asking.
 *
 * [content] is a lambda rather than something drawn underneath an overlay, for the same reason it is
 * in `AccountGate`: composed behind the lock it would start its loads, and a screen that renders
 * itself invisibly is one system-UI bug away from being visible.
 *
 * @param viewModel holds the locked/open decision across configuration changes
 * @param activity the host the system prompt attaches to
 * @param onSignOut the way out when the lock can no longer be satisfied
 * @param content the app, composed once unlocked
 */
@Composable
fun AppLockGate(
    viewModel: AppLockViewModel,
    activity: FragmentActivity,
    onSignOut: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    when (val current = state) {
        // Neither locked nor open until the armed state has been read. Rendering the app for that
        // one frame would flash its contents past exactly the person the lock exists to exclude.
        AppLockState.Unknown -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KrtLoadingIndicator(text = stringResource(R.string.lock_title))
            }
        }

        is AppLockState.Locked -> {
            LockScreen(
                messageRes = current.messageRes,
                onUnlock = {
                    scope.launch {
                        // Preparing first is what makes the prompt meaningful: a null here
                        // means the key is gone rather than that the member failed. A Deferred
                        // answer is neither -- it is API 29 saying its time-bound key cannot be
                        // initialised until the authentication exists.
                        viewModel.prepareUnlock()?.let { request ->
                            BiometricGate.prompt(
                                activity = activity,
                                cipher = (request as? AuthenticatedCipher.Bound)?.cipher,
                                useCryptoObject = request is AuthenticatedCipher.Bound,
                                onSuccess = viewModel::unlock,
                                onFailure = viewModel::onUnlockFailed,
                            )
                        }
                    }
                },
            )
        }

        AppLockState.Unsatisfiable -> {
            // No unlock button: a new biometric enrolment destroyed the key, so retrying can only
            // fail. Signing out is the documented route back (security concept §4), and offering
            // anything else would send the member round a loop with no exit.
            LockScreen(
                messageRes = R.string.lock_error_invalidated,
                onUnlock = null,
                onSignOut = onSignOut,
            )
        }

        AppLockState.Open -> {
            content()
        }
    }
}
