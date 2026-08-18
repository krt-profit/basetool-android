/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFanKitBand
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHeading
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Width of the centred column; the tablet layout is the same column, not a split (design ch. 04). */
private val COLUMN_MAX_WIDTH = 480.dp

/** Radial bloom size, fixed by the design spec. The bloom is allowed on auth screens only. */
private val BLOOM_WIDTH = 440.dp
private val BLOOM_HEIGHT = 260.dp

/** Bloom opacity at the centre — rgba(231,126,35,.25) in the spec. */
private const val BLOOM_ALPHA = 0.25f

/**
 * The one screen a member sees before they have a session.
 *
 * Layout is a single centred column on every form factor: the design spec makes the tablet the same
 * 480 dp column rather than a split, because there is nothing here to put beside it and a
 * half-empty split reads as a broken layout.
 *
 * The [KrtFanKitBand] above the footer is **mandatory and coupled** — artwork and CIG notice are a
 * legal unit and neither may be moved or dropped on its own (Fan Kit Guidelines §2/§2b/§3). It sits
 * here and on the settings screen, nowhere else.
 *
 * Two entries from the design chapter are deliberately absent for now, because rendering them would
 * be a promise the app cannot keep: **"Mit Discord anmelden"** is shown only when the realm has the
 * IdP configured, and **"Als Gast fortfahren"** only when guest browsing is enabled — both are
 * capability answers the app has no endpoint for yet. A button that fails after the tap is worse
 * than one that is not there.
 *
 * @param state what the login is currently doing
 * @param onSignIn starts the Custom Tab flow
 * @param onOpenPrivacy opens the privacy policy in a browser
 * @param onOpenImprint opens the imprint
 * @param onOpenTerms opens the terms of use
 * @param versionName the app's version, shown in the footer
 * @param versionCode the build number beside it
 * @param modifier layout modifier from the caller
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onSignIn: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
    onOpenTerms: () -> Unit,
    versionName: String,
    versionCode: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .topBloom()
                // Drawn edge to edge so the bloom reaches the top of the display, but the content
                // is inset: without this the org line sits on the status bar clock, which a
                // preview cannot show because it renders no system bars.
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
        ) {
            // The upper block takes the free height and scrolls when there is not enough of it;
            // the legal block below stays at the bottom edge, where the design puts it. The weight
            // lives here, on a column that is NOT scrollable — inside a scrolling parent it would
            // have no finite height to take a share of.
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(KrtSpacing.xxl))
                Brand()
                Spacer(Modifier.height(KrtSpacing.xxl))

                KrtCtaButton(
                    text = stringResource(R.string.login_sign_in),
                    onClick = onSignIn,
                    enabled = state !is LoginUiState.Working,
                    modifier = Modifier.fillMaxWidth(),
                )

                // The message occupies its own slot rather than replacing the button: a member
                // whose login was refused still needs the button to try again.
                state.messageRes?.let { message ->
                    Spacer(Modifier.height(KrtSpacing.md))
                    Text(
                        text = stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state is LoginUiState.Failed) KrtPalette.DangerText else KrtPalette.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            KrtFanKitBand()
            Spacer(Modifier.height(KrtSpacing.lg))
            Footer(onOpenPrivacy = onOpenPrivacy, onOpenImprint = onOpenImprint, onOpenTerms = onOpenTerms)
            Spacer(Modifier.height(KrtSpacing.sm))
            Version(versionName = versionName, versionCode = versionCode)
            Spacer(Modifier.height(KrtSpacing.lg))
        }
    }
}

/**
 * Organisation line and product name.
 */
@Composable
private fun Brand() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.login_org),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Spacer(Modifier.height(KrtSpacing.sm))
        KrtHeading(text = stringResource(R.string.app_name))
    }
}

/**
 * The three legal links the login screen has to carry.
 *
 * @param onOpenPrivacy opens the privacy policy
 * @param onOpenImprint opens the imprint
 * @param onOpenTerms opens the terms of use
 */
@Composable
private fun Footer(
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
    onOpenTerms: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtGhostButton(text = stringResource(R.string.login_privacy), onClick = onOpenPrivacy)
        KrtGhostButton(text = stringResource(R.string.login_imprint), onClick = onOpenImprint)
        KrtGhostButton(text = stringResource(R.string.login_terms), onClick = onOpenTerms)
    }
}

/**
 * The version footer.
 *
 * The design chapter pairs this with a "Server bereit" status. That half is deliberately not drawn:
 * the app has no health endpoint yet, and a status line that always says "ready" is worse than none
 * — it is the one element a member would trust during an outage.
 *
 * @param versionName the app's version name
 * @param versionCode the build number
 */
@Composable
private fun Version(
    versionName: String,
    versionCode: Int,
) {
    Text(
        text = stringResource(R.string.login_version, versionName, versionCode),
        style = MaterialTheme.typography.labelSmall,
        color = KrtPalette.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Draws the orange radial bloom at the top centre.
 *
 * Allowed on auth screens **only** (design spec ch. 04) — elsewhere it would compete with the
 * single orange call to action a KRT screen is permitted.
 *
 * @return the modifier with the bloom behind the content
 */
private fun Modifier.topBloom(): Modifier =
    drawBehind {
        val width = BLOOM_WIDTH.toPx()
        val height = BLOOM_HEIGHT.toPx()
        // A rectangle, not an oval, and the gradient makes the shape. Drawing an oval clipped the
        // gradient at the oval's edge while it was still around 40 % opaque along the short axis,
        // which put a hard arc across the top of the screen — visible on a device, invisible in a
        // preview. Over a rectangle whose corners lie outside the gradient radius, the falloff
        // reaches transparent on its own and there is no edge to see.
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(KrtPalette.Orange.copy(alpha = BLOOM_ALPHA), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = width / 2f,
                ),
            topLeft = Offset(size.width / 2f - width / 2f, 0f),
            size = Size(width, height),
        )
    }

/**
 * What the login is doing, as the screen needs to know it.
 *
 * @property messageRes a string to show under the button, or `null`
 */
sealed class LoginUiState(
    val messageRes: Int?,
) {
    /** Nothing has happened yet. */
    data object Idle : LoginUiState(null)

    /** The browser is open, or the code is being redeemed. */
    data object Working : LoginUiState(R.string.login_signing_in)

    /**
     * The attempt ended without a session.
     *
     * @param reason which message to show
     */
    class Failed(
        reason: Int,
    ) : LoginUiState(reason)
}

/**
 * Preview of the resting state.
 */
@Preview(name = "Login — idle", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LoginScreenPreview() {
    KrtTheme {
        LoginScreen(
            state = LoginUiState.Idle,
            onSignIn = {},
            onOpenPrivacy = {},
            onOpenImprint = {},
            onOpenTerms = {},
            versionName = "0.1.0-alpha01",
            versionCode = 1,
        )
    }
}

/**
 * Preview of a refused login — the button stays available.
 */
@Preview(name = "Login — refused", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LoginScreenFailedPreview() {
    KrtTheme {
        LoginScreen(
            state = LoginUiState.Failed(R.string.login_error_denied),
            onSignIn = {},
            onOpenPrivacy = {},
            onOpenImprint = {},
            onOpenTerms = {},
            versionName = "0.1.0-alpha01",
            versionCode = 1,
        )
    }
}
