/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

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
import de.greluc.krt.profit.basetool.android.gate.GateUnavailableScreen

/**
 * Holds the app until the Terms of Use in force have been accepted.
 *
 * Sits **after** the approval gate and before the app: the backend enforces the same order, and a
 * member whose registration is still pending has nothing to consent to yet. Like the gates around
 * it, [content] is a lambda rather than something rendered underneath — composed behind the
 * document it would fire loads against endpoints the consent filter is about to refuse.
 *
 * @param viewModel reads the status, fetches the wording and records consent
 * @param onDecline signs out; declining the terms means leaving the tool
 * @param content the app, composed once consent is on record
 */
@Composable
fun TermsGate(
    viewModel: TermsGateViewModel,
    onDecline: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    when (val current = state) {
        TermsGateState.Checking -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KrtLoadingIndicator(text = stringResource(R.string.terms_checking))
            }
        }

        TermsGateState.Cleared -> {
            content()
        }

        is TermsGateState.Required -> {
            TermsScreen(
                document = current.document,
                accepting = current.accepting,
                errorRes = current.errorRes,
                onAccept = viewModel::accept,
                onDecline = onDecline,
            )
        }

        is TermsGateState.Unavailable -> {
            GateUnavailableScreen(
                // Only a request that never reached the server counts as offline; a 500 means the
                // server answered, and telling the member to check their connection would send them
                // after a fault that is not on their side.
                offline = current.error is ApiError.Network,
                onRetry = viewModel::start,
                onLogout = onDecline,
            )
        }
    }
}
