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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import kotlinx.coroutines.launch

/**
 * Seals the app behind the lock screen and composes [content] only once it is open.
 *
 * The outermost gate after the update gate, ahead of the session and account gates, so the lock never
 * waits on the network.
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
                        viewModel.prepareUnlock()?.let { cipher ->
                            BiometricGate.prompt(
                                activity = activity,
                                cipher = cipher,
                                onSuccess = viewModel::unlock,
                                onFailure = viewModel::onUnlockFailed,
                            )
                        }
                    }
                },
            )
        }

        AppLockState.Unsatisfiable -> {
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
