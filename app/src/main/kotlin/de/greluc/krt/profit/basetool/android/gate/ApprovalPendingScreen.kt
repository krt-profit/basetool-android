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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.ApprovalStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Width of the centred column; the tablet reuses it rather than splitting (design ch. 04). */
private val COLUMN_MAX_WIDTH = 480.dp

/** Size of the status glyph above the headline. */
private val STATUS_ICON = 40.dp

/**
 * The headline, body and glyph the gate shows for one held state, resolved together.
 *
 * @property titleRes the headline, rendered uppercase
 * @property bodyRes the explanation under it
 * @property iconRes the glyph above the headline
 * @property tint the glyph's colour; danger only where the state is terminal
 */
private data class GateCopy(
    val titleRes: Int,
    val bodyRes: Int,
    val iconRes: Int,
    val tint: Color,
)

/**
 * Picks the copy for a held state.
 *
 * [ApprovalStatus.ACTIVE] never reaches this screen, and [ApprovalStatus.UNKNOWN] is shown as
 * pending.
 *
 * @param status why the member is being held
 * @return the strings and glyph for it
 */
private fun copyFor(status: ApprovalStatus): GateCopy =
    when (status) {
        ApprovalStatus.REJECTED -> {
            GateCopy(
                R.string.gate_rejected_title,
                R.string.gate_rejected_body,
                DesignR.drawable.ic_krt_warning,
                KrtPalette.DangerText,
            )
        }

        ApprovalStatus.NO_ROLE -> {
            GateCopy(
                R.string.gate_no_role_title,
                R.string.gate_no_role_body,
                DesignR.drawable.ic_krt_user_plus,
                KrtPalette.Orange,
            )
        }

        else -> {
            GateCopy(
                R.string.gate_pending_title,
                R.string.gate_pending_body,
                DesignR.drawable.ic_krt_user_plus,
                KrtPalette.Orange,
            )
        }
    }

/**
 * The screen a member meets between signing in and being let into the app.
 *
 * It has no primary action, only an outline re-check and sign-out. It shows neither a submission
 * time nor a rejection reason, since the server exposes neither; the account name comes from the ID
 * token's `preferred_username`.
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
    val copy = copyFor(status)

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
                    .padding(horizontal = KrtSpacing.s24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            KrtHudBox(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KrtIcon(
                        id = copy.iconRes,
                        contentDescription = null,
                        size = STATUS_ICON,
                        tint = copy.tint,
                    )
                    Spacer(Modifier.height(KrtSpacing.s12))
                    Text(
                        text = stringResource(copy.titleRes).krtUppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = KrtPalette.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(KrtSpacing.s12))
                    Text(
                        text = stringResource(copy.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KrtPalette.Gray1,
                        textAlign = TextAlign.Center,
                    )

                    accountName?.let { name ->
                        Spacer(Modifier.height(KrtSpacing.s16))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.gate_account).krtUppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = KrtPalette.TextMuted,
                            )
                            KrtChip(text = name, tone = KrtChipTone.Data)
                        }
                    }
                }
            }

            Spacer(Modifier.height(KrtSpacing.s24))

            KrtOutlineButton(
                text = stringResource(R.string.gate_refresh),
                onClick = onRefresh,
                enabled = !refreshing,
                iconRes = DesignR.drawable.ic_krt_reset,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KrtSpacing.s12))
            KrtQuietDangerButton(
                text = stringResource(R.string.logout),
                onClick = onLogout,
                iconRes = DesignR.drawable.ic_krt_logout,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = stringResource(R.string.gate_poll_hint),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.s24, vertical = KrtSpacing.s16),
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
 * Preview of the role-less state — the account is through approval and still held.
 */
@Preview(name = "Gate — no role", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ApprovalNoRolePreview() {
    KrtTheme {
        ApprovalPendingScreen(
            status = ApprovalStatus.NO_ROLE,
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
