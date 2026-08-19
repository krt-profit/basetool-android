/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.ApprovalStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Width of the centred column; the tablet reuses it rather than splitting (design ch. 04). */
private val COLUMN_MAX_WIDTH = 480.dp

/** Size of the status glyph above the headline. */
private val STATUS_ICON = 40.dp

/**
 * The wall a member meets between signing in and being let into the app.
 *
 * **There is no primary action here, and the button ladder says so.** The design chapter is
 * explicit that this screen carries no filled CTA: the member cannot do anything to be approved
 * faster, and an orange button would promise otherwise. What they get is an outline re-check and a
 * quiet way out.
 *
 * Two entries of the design frame are deliberately absent, both because the data does not exist
 * rather than because they were dropped:
 *
 * - **"Eingereicht: vor 2 Std. · via Discord".** `RegistrationStatusDto` carries the status and
 *   nothing else — no submission timestamp and no identity provider. Inventing a plausible-looking
 *   "vor 2 Std." would be the worst option of the three, so the row is gone until the server
 *   offers the field.
 * - **The rejection reason.** Administrators do record one (`RejectRegistrationRequest.reason`),
 *   but no endpoint exposes it to the rejected member, so the rejected copy names the consequence
 *   and where to ask instead of pretending to quote a reason.
 *
 * The account row survives because its value comes from the ID token's `preferred_username`, which
 * the app already holds — no request needed, and it works while every gated endpoint refuses.
 *
 * @param status why the member is being held
 * @param accountName the member's login name from the ID token, or `null` when the realm sent none
 * @param refreshing whether a manual re-check is currently in flight
 * @param onRefresh re-reads the approval status now
 * @param onLogout signs out, the one action always available here
 * @param modifier layout modifier from the caller
 */
@Composable
fun ApprovalPendingScreen(
    status: ApprovalStatus,
    accountName: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rejected = status == ApprovalStatus.REJECTED
    val titleRes = if (rejected) R.string.gate_rejected_title else R.string.gate_pending_title
    val bodyRes = if (rejected) R.string.gate_rejected_body else R.string.gate_pending_body

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxSize()
                    .padding(horizontal = KrtSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            KrtHudBox(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KrtIcon(
                        id = if (rejected) DesignR.drawable.ic_krt_warning else DesignR.drawable.ic_krt_user_plus,
                        contentDescription = null,
                        size = STATUS_ICON,
                        tint = if (rejected) KrtPalette.DangerText else KrtPalette.Orange,
                    )
                    Spacer(Modifier.height(KrtSpacing.md))
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = KrtPalette.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(KrtSpacing.md))
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KrtPalette.Gray1,
                        textAlign = TextAlign.Center,
                    )

                    // Only rendered when the realm actually sent a username: an empty value beside
                    // a bright label reads as data that failed to load rather than as data that
                    // was never promised.
                    accountName?.let { name ->
                        Spacer(Modifier.height(KrtSpacing.lg))
                        KrtKeyValueRow(label = stringResource(R.string.gate_account), value = name)
                    }
                }
            }

            Spacer(Modifier.height(KrtSpacing.xl))

            KrtOutlineButton(
                text = stringResource(R.string.gate_refresh),
                onClick = onRefresh,
                enabled = !refreshing,
                iconRes = DesignR.drawable.ic_krt_reset,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KrtSpacing.md))
            KrtQuietDangerButton(
                text = stringResource(R.string.logout),
                onClick = onLogout,
                iconRes = DesignR.drawable.ic_krt_logout,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The design frame promises "Automatische Prüfung alle 60 s — Push bei Freigabe." Only the
        // first half is true here and the second half is struck: the app has no push channel at all
        // (resolved decision Q2), so an approval reaches this screen through the poll or not at
        // all. Promising a notification that can never arrive would leave a member waiting on the
        // lock screen of their phone instead of tapping re-check.
        Text(
            text = stringResource(R.string.gate_poll_hint),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.Gray2,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.xl, vertical = KrtSpacing.lg),
        )
    }
}

/**
 * Preview of the waiting state.
 */
@Preview(name = "Gate — pending", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ApprovalPendingPreview() {
    KrtTheme {
        ApprovalPendingScreen(
            status = ApprovalStatus.PENDING,
            accountName = "GrafRotz",
            refreshing = false,
            onRefresh = {},
            onLogout = {},
        )
    }
}

/**
 * Preview of the refused state — same layout, different glyph and copy.
 */
@Preview(name = "Gate — rejected", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ApprovalRejectedPreview() {
    KrtTheme {
        ApprovalPendingScreen(
            status = ApprovalStatus.REJECTED,
            accountName = "GrafRotz",
            refreshing = false,
            onRefresh = {},
            onLogout = {},
        )
    }
}
