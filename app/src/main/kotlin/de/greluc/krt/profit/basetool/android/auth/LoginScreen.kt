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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import de.greluc.krt.profit.basetool.android.core.network.API_VERSION

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
 * Two entries from the design chapter are absent, for different reasons. **"Als Gast fortfahren"**
 * is gone for good: guest mode was dropped (owner decision, 2026-08-18) and every user signs in.
 * **"Mit Discord anmelden"** waits — the design chapter shows it only when the realm has the IdP
 * configured, and that is a capability answer the app has no endpoint for yet; a button that fails
 * after the tap is worse than one that is not there.
 *
 * @param state what the login is currently doing
 * @param onSignIn starts the Custom Tab flow
 * @param onOpenPrivacy opens the privacy policy in a browser
 * @param onOpenImprint opens the imprint
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
        BoxWithConstraints(
            modifier =
                Modifier
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxSize()
                    .padding(horizontal = KrtSpacing.xl),
        ) {
            // The PAGE scrolls as a whole — not the band, and the notice is never folded behind a
            // disclosure (design ch. 04 artboard 1). At the drawn 412×812 dp nothing scrolls: the
            // artboard's content measures exactly one viewport and the band sits above the fold.
            // The scroll exists for what the drawing cannot show — font scale 1.3, a shorter
            // display, a taller status bar.
            val viewport = maxHeight
            // What the legal block actually needs, measured rather than guessed: it is two
            // prescribed notices whose height depends on the font scale, the locale's line
            // breaking and the display width. The space above it is whatever is left of the
            // viewport, so the call to action reads as centred at the drawn size and moves up as
            // the band grows instead of being covered by it.
            var legalHeightPx by remember { mutableIntStateOf(0) }
            val legalHeight = with(LocalDensity.current) { legalHeightPx.toDp() }
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // One viewport's worth, holding the brand at the top and the call to action at
                // exactly half the height. Aligned children rather than spacers: a spacer's share
                // depends on what is above it, and the button's position is meant to be a fixed
                // fraction of the screen rather than a consequence of the brand's line count.
                //
                // `heightIn(min = ...)` and not `height(...)`: when the first section itself
                // outgrows the viewport — a long refusal message at font scale 1.3 — it has to be
                // allowed to grow rather than clip its own content.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = (viewport - legalHeight).coerceAtLeast(0.dp)),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(KrtSpacing.xxl))
                        Brand()
                    }

                    Column(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KrtCtaButton(
                            text = stringResource(R.string.login_sign_in),
                            onClick = onSignIn,
                            enabled = state !is LoginUiState.Working,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // The message occupies its own slot rather than replacing the button: a
                        // member whose login was refused still needs the button to try again.
                        state.messageRes?.let { message ->
                            Spacer(Modifier.height(KrtSpacing.md))
                            Text(
                                text = stringResource(message),
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (state is LoginUiState.Failed) {
                                        KrtPalette.DangerText
                                    } else {
                                        KrtPalette.TextMuted
                                    },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // In the flow, after the call to action — never over it. The Fan Kit band is a
                // legally coupled unit of three elements and is never folded behind a disclosure,
                // so when it does not fit the PAGE scrolls (design ch. 04 artboard 1).
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { legalHeightPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KrtFanKitBand()
                    Spacer(Modifier.height(KrtSpacing.lg))
                    Footer(onOpenPrivacy = onOpenPrivacy, onOpenImprint = onOpenImprint)
                    Spacer(Modifier.height(KrtSpacing.sm))
                    Version(versionName = versionName, versionCode = versionCode)
                    Spacer(Modifier.height(KrtSpacing.lg))
                }
            }
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
 * The two legal links the login screen has to carry — and it is exactly two.
 *
 * Privacy and imprint belong **here**, before the login, because that is where their duty lives:
 * the privacy notice has to be available before any processing begins, and processing begins with
 * the sign-in tap rather than after it; the imprint has to be permanently and immediately
 * reachable, which a link found only after logging in is not.
 *
 * The terms of use are deliberately **not** here (owner decision, 2026-08-18). They are a
 * contractual document whose binding moment is the acceptance gate — mandatory, versioned and with
 * an explicit checkbox (design spec ch. 04) — so a link in front of it is neither a legal
 * substitute nor practically useful, and the design chapter's third button is dropped with that
 * reasoning. There is no guest who could miss the gate: guest mode was dropped (owner decision,
 * 2026-08-18), so every user of this app passes it.
 *
 * @param onOpenPrivacy opens the privacy policy
 * @param onOpenImprint opens the imprint
 */
@Composable
private fun Footer(
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
) {
    // FlowRow, not Row: three equal shares of one line fit "Privacy / Imprint / Terms of use" and
    // tore "Nutzungsbedingungen" into "NUTZUN GSBEDIN GUNGEN". German is the sizing baseline here —
    // its compounds are the long ones, so a label that only fits in English is a defect waiting for
    // the locale to change. Each button now takes the width its own text needs and the row wraps
    // when they no longer fit beside each other.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        KrtGhostButton(text = stringResource(R.string.login_privacy), onClick = onOpenPrivacy)
        KrtGhostButton(text = stringResource(R.string.login_imprint), onClick = onOpenImprint)
    }
}

/**
 * The version footer.
 *
 * The design chapter pairs this with a "Server bereit" status. That half is deliberately not drawn:
 * the app has no health endpoint yet, and a status line that always says "ready" is worse than none
 * — it is the one element a member would trust during an outage. The API version, by contrast, needs
 * no signal at all: it is the contract this build was compiled against, so it is drawn.
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
        text = stringResource(R.string.login_version, versionName, versionCode, API_VERSION),
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
            versionName = "0.1.0-alpha01",
            versionCode = 1,
        )
    }
}
