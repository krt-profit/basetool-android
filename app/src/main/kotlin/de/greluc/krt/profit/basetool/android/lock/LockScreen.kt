/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtCornerBrackets
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Width of the centred column, matching the other auth-adjacent screens. */
private val COLUMN_MAX_WIDTH = 480.dp

/** The bracketed square that holds the fingerprint glyph (design ch. 04: 96 dp). */
private val EMBLEM_SIZE = 96.dp

/** The fingerprint glyph inside it. */
private val EMBLEM_ICON = 44.dp

/** The KRT mark above the emblem. */
private val BRAND_MARK = 64.dp

/**
 * What covers the app while it is locked.
 *
 * **This screen shows nothing.** No counts, no names, no last-seen mission — the design chapter is
 * explicit ("No data hints on this screen"), and the reason is that the lock exists precisely for
 * the moment somebody else is holding the phone. A badge with an unread count would leak exactly
 * the kind of thing the lock is there to withhold.
 *
 * **It carries no input of its own either.** Authentication happens in the platform's
 * `BiometricPrompt`, which draws above this process; an app-rendered PIN pad would be a credential
 * this app could read. So the screen is a backdrop with one action: ask the system to prompt again.
 * The design's second button, "Gerätesperre verwenden", is not drawn — the prompt already offers
 * the device credential as its own fallback (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`), so a second
 * button would open the same sheet and only suggest that the first one had not.
 *
 * @param messageRes a message from the previous attempt, or `null`
 * @param onUnlock asks the system to prompt, or `null` when the lock can no longer be satisfied and
 *   a retry could only fail
 * @param onSignOut the way out, offered only when there is nothing left to retry
 * @param modifier layout modifier from the caller
 */
@Composable
fun LockScreen(
    messageRes: Int?,
    onUnlock: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null,
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
                    .fillMaxSize()
                    .padding(horizontal = KrtSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            KrtIcon(
                id = DesignR.drawable.krt_basetool_logo,
                contentDescription = null,
                size = BRAND_MARK,
                tint = KrtPalette.Orange,
            )
            Spacer(Modifier.height(KrtSpacing.xl))

            Box(
                modifier =
                    Modifier
                        .size(EMBLEM_SIZE)
                        .background(KrtPalette.Gray4)
                        .border(KrtSpacing.hairline, KrtPalette.Gray3)
                        .krtCornerBrackets(),
                contentAlignment = Alignment.Center,
            ) {
                KrtIcon(
                    id = DesignR.drawable.ic_krt_fingerprint,
                    contentDescription = null,
                    size = EMBLEM_ICON,
                    tint = KrtPalette.Orange,
                )
            }

            Spacer(Modifier.height(KrtSpacing.xl))
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleLarge,
                color = KrtPalette.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(KrtSpacing.sm))
            Text(
                text = stringResource(R.string.lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.TextMuted,
                textAlign = TextAlign.Center,
            )

            messageRes?.let { message ->
                Spacer(Modifier.height(KrtSpacing.md))
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.DangerText,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.xxl, vertical = KrtSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            onUnlock?.let { unlock ->
                KrtCtaButton(
                    text = stringResource(R.string.lock_unlock),
                    onClick = unlock,
                    iconRes = DesignR.drawable.ic_krt_fingerprint,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            onSignOut?.let { signOut ->
                Spacer(Modifier.height(KrtSpacing.md))
                KrtQuietDangerButton(
                    text = stringResource(R.string.logout),
                    onClick = signOut,
                    iconRes = DesignR.drawable.ic_krt_logout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Preview of the resting locked state.
 */
@Preview(name = "Lock — locked", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LockScreenPreview() {
    KrtTheme {
        LockScreen(messageRes = null, onUnlock = {})
    }
}

/**
 * Preview after too many failed attempts.
 */
@Preview(name = "Lock — locked out", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LockScreenLockedOutPreview() {
    KrtTheme {
        LockScreen(messageRes = R.string.lock_error_lockout, onUnlock = {})
    }
}

/**
 * Preview of the state a new fingerprint leaves behind: no retry, only a way out.
 */
@Preview(name = "Lock — invalidated", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LockScreenInvalidatedPreview() {
    KrtTheme {
        LockScreen(messageRes = R.string.lock_error_invalidated, onUnlock = null, onSignOut = {})
    }
}
