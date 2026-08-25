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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Width of the centred column, matching the other auth-adjacent screens. */
private val COLUMN_MAX_WIDTH = 480.dp

/** Size of the status glyph. */
private val STATUS_ICON = 40.dp

/**
 * Shown when the app is signed in but cannot find out whether the member is allowed in.
 *
 * This is **not** the approval-pending screen with a different sentence, and conflating the two
 * would be the actual bug: a member who is long since approved would be told their account is
 * waiting for an administrator, which is both false and impossible to act on. All this screen
 * claims is that the question could not be asked.
 *
 * Design chapter 14, artboard 3. The state is its own, not a borrowed 5xx: the member is
 * authenticated and their credentials are fine — the server that says whether their registration is
 * approved simply did not answer. **No status code appears in the copy**, deliberately, and the
 * tone is a statement rather than an accusation: „Your credentials remain valid" is the sentence
 * that does the work, because there is nothing here the member did or can fix.
 *
 * The app keeps asking on its own — 3 → 6 → 12 → 30 s — and a manual attempt resets that rhythm.
 * A screen whose only way forward is a button the member has to keep pressing turns a passing
 * outage into a chore.
 *
 * @param offline `true` when no response arrived at all, `false` when the server answered badly
 * @param accountName who is signed in, so the screen can say the session is intact rather than
 *   merely assert it; `null` when the ID token carried no username
 * @param secondsUntilRetry seconds until the next automatic attempt, or `null` while one is in
 *   flight
 * @param onRetry asks again, now
 * @param onLogout signs out
 * @param modifier layout modifier from the caller
 */
@Composable
fun GateUnavailableScreen(
    offline: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    accountName: String? = null,
    secondsUntilRetry: Int? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            KrtIcon(
                id = if (offline) DesignR.drawable.ic_krt_wifi_off else DesignR.drawable.ic_krt_warning,
                contentDescription = null,
                size = STATUS_ICON,
                tint = KrtPalette.TextMuted,
            )
            Spacer(Modifier.height(KrtSpacing.md))
            Text(
                text = stringResource(if (offline) R.string.gate_offline_title else R.string.gate_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = KrtPalette.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(KrtSpacing.md))
            Text(
                text = stringResource(if (offline) R.string.gate_offline_body else R.string.gate_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
                textAlign = TextAlign.Center,
            )
            accountName?.let { name ->
                Spacer(Modifier.height(KrtSpacing.sm))
                Text(
                    text = stringResource(R.string.gate_signed_in_as, name),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
            secondsUntilRetry?.let { seconds ->
                Spacer(Modifier.height(KrtSpacing.md))
                Text(
                    text = stringResource(R.string.gate_retry_in, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(KrtSpacing.xl))
            KrtOutlineButton(
                text = stringResource(R.string.gate_retry),
                onClick = onRetry,
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
    }
}

/**
 * Preview of the offline variant.
 */
@Preview(name = "Gate — offline", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun GateOfflinePreview() {
    KrtTheme {
        GateUnavailableScreen(
            offline = true,
            onRetry = {},
            onLogout = {},
            accountName = "GrafRotz",
            secondsUntilRetry = 6,
        )
    }
}

/**
 * Preview of the server-error variant.
 */
@Preview(name = "Gate — server error", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun GateErrorPreview() {
    KrtTheme {
        GateUnavailableScreen(offline = false, onRetry = {}, onLogout = {})
    }
}
