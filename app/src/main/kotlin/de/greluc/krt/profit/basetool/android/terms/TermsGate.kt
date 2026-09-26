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
 * Sits after the approval gate. [content] is composed only once consent is on record.
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
                offline = current.error is ApiError.Network,
                onRetry = viewModel::start,
                onLogout = onDecline,
            )
        }
    }
}
