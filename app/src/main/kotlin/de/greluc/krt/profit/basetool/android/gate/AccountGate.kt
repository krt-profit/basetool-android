/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/**
 * Stands between a valid session and the app, and renders [content] only once the member is in.
 *
 * A token proves who somebody is; it does not prove they are allowed in. The backend refuses every
 * gated endpoint for a member whose registration is unapproved, so without this wrapper the first
 * screen would load, fire its requests, and paint a wall of failures that say nothing about the
 * actual reason.
 *
 * **[content] is not composed while the gate is closed**, which is the point of taking it as a
 * lambda rather than rendering it behind an overlay: a dashboard composed underneath would start
 * its own loads against endpoints that are guaranteed to answer 403.
 *
 * @param viewModel holds the gate state and owns the polling loop
 * @param accountName the member's login name from the ID token, shown while they wait
 * @param onLogout signs out; reachable from every closed state
 * @param content the app, composed once the member is cleared
 */
@Composable
fun AccountGate(
    viewModel: AccountGateViewModel,
    accountName: String?,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    when (val current = state) {
        AccountGateState.Checking -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KrtLoadingIndicator(text = stringResource(R.string.gate_checking))
            }
        }

        AccountGateState.Cleared -> {
            content()
        }

        is AccountGateState.Blocked -> {
            ApprovalPendingScreen(
                status = current.status,
                accountName = accountName,
                refreshing = current.refreshing,
                onRefresh = viewModel::refresh,
                onLogout = onLogout,
            )
        }

        is AccountGateState.Unavailable -> {
            GateUnavailableScreen(
                // Only a request that never reached the server may be called "offline". A 500 or a
                // malformed body means the server answered — telling the member to check their
                // connection there would send them chasing a fault that is not on their side.
                offline = current.error is ApiError.Network,
                onRetry = viewModel::refresh,
                onLogout = onLogout,
            )
        }
    }
}
