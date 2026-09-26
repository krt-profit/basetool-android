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
import androidx.compose.ui.draw.alpha
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
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand

/** Width of the centred column; the tablet layout is the same column, not a split (design ch. 04). */
private val COLUMN_MAX_WIDTH = 480.dp

/** Radial bloom size, fixed by the design spec. The bloom is allowed on auth screens only. */
private val BLOOM_WIDTH = 440.dp
private val BLOOM_HEIGHT = 260.dp

/** Bloom opacity at the centre — rgba(231,126,35,.25) in the spec. */
private const val BLOOM_ALPHA = 0.25f

/**
 * The login screen, shown before a member has a session.
 *
 * A single centred column on every form factor. The [KrtFanKitBand] above the footer is a mandatory
 * legal unit and must not be moved or dropped (Fan Kit Guidelines §2/§2b/§3). There is no guest
 * entry and no Discord sign-in button.
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
    online: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .topBloom()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxSize()
                    .padding(horizontal = KrtSpacing.s24),
        ) {
            val viewport = maxHeight
            var legalHeightPx by remember { mutableIntStateOf(0) }
            val legalHeight = with(LocalDensity.current) { legalHeightPx.toDp() }
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                        Spacer(Modifier.height(KrtSpacing.s32))
                        Brand()
                    }

                    Column(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!online) {
                            OfflineBand()
                            Spacer(Modifier.height(KrtSpacing.s12))
                        }
                        KrtCtaButton(
                            text = stringResource(R.string.login_sign_in),
                            onClick = onSignIn,
                            enabled = online && state !is LoginUiState.Working,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
                        )

                        state.messageRes?.let { message ->
                            Spacer(Modifier.height(KrtSpacing.s12))
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

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { legalHeightPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KrtFanKitBand()
                    Spacer(Modifier.height(KrtSpacing.s16))
                    Footer(onOpenPrivacy = onOpenPrivacy, onOpenImprint = onOpenImprint)
                    Spacer(Modifier.height(KrtSpacing.s8))
                    Version(versionName = versionName, versionCode = versionCode)
                    Spacer(Modifier.height(KrtSpacing.s16))
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
        Spacer(Modifier.height(KrtSpacing.s8))
        KrtHeading(text = stringResource(R.string.app_name))
    }
}

/**
 * The login screen's two legal links, privacy policy and imprint.
 *
 * They must be reachable before the sign-in tap starts any processing. The terms of use are not
 * linked here; they are accepted at the acceptance gate.
 *
 * @param onOpenPrivacy opens the privacy policy
 * @param onOpenImprint opens the imprint
 */
@Composable
private fun Footer(
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
) {
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
 * The version footer, showing the app version and the API version it was compiled against.
 *
 * No server status is drawn, since the app has no health signal for it.
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
 * An interface rather than a sealed class so the Compose compiler's `$stable` field does not shadow a
 * parent field (ADR-0020).
 */
sealed interface LoginUiState {
    /** A string to show under the button, or `null` when there is nothing to say. */
    val messageRes: Int?

    /** Nothing has happened yet. */
    data object Idle : LoginUiState {
        override val messageRes: Int? = null
    }

    /** The browser is open, or the code is being redeemed. */
    data object Working : LoginUiState {
        override val messageRes: Int = R.string.login_signing_in
    }

    /**
     * The attempt ended without a session.
     *
     * @property messageRes which message to show
     */
    class Failed(
        override val messageRes: Int,
    ) : LoginUiState
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
