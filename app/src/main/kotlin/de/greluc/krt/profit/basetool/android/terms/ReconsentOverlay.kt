/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * Asks for consent again, over whatever screen is open, when any call was refused for it (design
 * ch. 19 artboard 11, REQ-APP-AUTH-016, ADR-0025).
 *
 * Arms the broker while composed, so refused calls wait for the member's answer only while this can
 * be shown. Exactly two ways out: „Bestätigen" records consent and the refused calls are issued
 * again; „Abmelden" signs out. No close glyph, no back, no scrim tap.
 *
 * @param viewModel the overlay's state and actions.
 * @param onSignOut ends the session after the member chose „Abmelden".
 */
@Composable
fun ReconsentOverlay(
    viewModel: ReconsentViewModel,
    onSignOut: () -> Unit,
) {
    DisposableEffect(viewModel) {
        viewModel.arm()
        onDispose { viewModel.disarm() }
    }
    val state by viewModel.state.collectAsState()
    ReconsentModal(
        state = state,
        onConfirm = viewModel::confirm,
        onRetry = viewModel::retry,
        onSignOut = {
            viewModel.decline()
            onSignOut()
        },
    )
}

/**
 * The overlay's modal, drawn from its state alone.
 *
 * @param state what to draw; nothing is drawn while it is closed.
 * @param onConfirm records consent.
 * @param onRetry fetches the wording again.
 * @param onSignOut signs out instead.
 */
@Composable
internal fun ReconsentModal(
    state: ReconsentState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (!state.open) {
        return
    }
    val document = state.document
    KrtModal(
        title = stringResource(R.string.reconsent_title),
        confirmText = stringResource(R.string.reconsent_confirm),
        onConfirm = onConfirm,
        onDismiss = onSignOut,
        cancelText = stringResource(R.string.reconsent_sign_out),
        dismissible = false,
        confirmEnabled = state.confirmable,
        busy = state.accepting,
        modifier = Modifier.testTag(RECONSENT_TAG),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
            if (document != null) {
                Text(
                    text = stringResource(R.string.reconsent_lead, document.lastUpdated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.Gray1,
                )
                TermsDocumentColumn(
                    document = document,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = DOCUMENT_MAX_HEIGHT)
                            .background(KrtPalette.SurfaceInput)
                            .border(KrtSpacing.hairline, KrtPalette.Gray3),
                )
                Text(
                    text = stringResource(R.string.terms_version, document.version),
                    style = MaterialTheme.typography.labelSmall,
                    color = KrtPalette.TextMuted,
                )
            } else if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KrtSpinner() }
            }
            state.errorRes?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.DangerText,
                )
                if (document == null && !state.loading && !state.offline) {
                    KrtGhostButton(text = stringResource(R.string.reconsent_retry), onClick = onRetry)
                }
            }
            if (state.offline) {
                Text(
                    text = stringResource(R.string.reconsent_offline_reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            Text(
                text = stringResource(R.string.reconsent_resume),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/** Test tag of the re-consent overlay. */
const val RECONSENT_TAG: String = "reconsent-overlay"

/** How tall the wording may grow inside the modal before it scrolls. */
private val DOCUMENT_MAX_HEIGHT = 380.dp
