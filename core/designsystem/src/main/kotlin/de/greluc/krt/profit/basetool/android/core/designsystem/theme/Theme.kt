/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The press highlight of the whole app: white at 8 % in every interaction state, focus included.
 */
private val KrtRippleAlpha =
    RippleAlpha(
        pressedAlpha = KRT_RIPPLE_ALPHA,
        focusedAlpha = KRT_RIPPLE_ALPHA,
        draggedAlpha = KRT_RIPPLE_ALPHA,
        hoveredAlpha = KRT_RIPPLE_ALPHA,
    )

/**
 * The dark-only theme every Basetool screen is wrapped in, without dynamic colour.
 *
 * Also provides the white ripple ([KrtRippleAlpha]) and the device's motion duration
 * ([LocalKrtMotionMs]).
 *
 * @param content the screen content, styled by this theme.
 */
@Composable
fun KrtTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motionMs =
        remember(context) {
            val scale =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            if (scale == 0f) 0 else KRT_MOTION_MS
        }
    CompositionLocalProvider(
        LocalKrtColors provides KrtExtendedColors(),
        LocalIndication provides ripple(color = KrtPalette.White),
        LocalRippleConfiguration provides
            RippleConfiguration(color = KrtPalette.White, rippleAlpha = KrtRippleAlpha),
        LocalKrtMotionMs provides motionMs,
        LocalKrtBottomBarInset provides
            WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
    ) {
        MaterialTheme(
            colorScheme = KrtColorScheme,
            typography = KrtTypography,
            shapes = KrtShapes,
            content = content,
        )
    }
}

/**
 * The height of the system's bottom bar, measured at the app root for bottom sheets, whose window
 * reports no navigation-bar inset.
 */
val LocalKrtBottomBarInset: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { 0.dp }

/**
 * How long a colour or fade transition may take, in milliseconds — [KRT_MOTION_MS], or `0` when
 * `Settings.Global.ANIMATOR_DURATION_SCALE` asks for reduced motion.
 *
 * Read it as [KrtTheme.motionMs] and pass it to `tween(...)`. Spinners and the pull-to-refresh
 * ring do not consult it.
 */
val LocalKrtMotionMs: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { KRT_MOTION_MS }

/**
 * Accessors for the parts of the theme that Material 3 has no slot for.
 *
 * Mirrors the `MaterialTheme` pattern: `KrtTheme.colors.dangerText`, `KrtTheme.spacing.lg`. Both are
 * read-only and cheap, so they can be used freely inside composables.
 */
object KrtTheme {
    /** Brand colours without a Material 3 slot — semantic tints, department hues, glows. */
    val colors: KrtExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalKrtColors.current

    /** The spacing and metric scale. */
    val spacing: KrtSpacing
        get() = KrtSpacing

    /**
     * Duration of a colour or fade transition in milliseconds — `0` when the device asks for
     * reduced motion. See [LocalKrtMotionMs] for what sets it and what deliberately ignores it.
     */
    val motionMs: Int
        @Composable @ReadOnlyComposable
        get() = LocalKrtMotionMs.current
}

/**
 * Preview scaffold that wraps a component in [KrtTheme] on the black canvas with the standard
 * screen margin.
 *
 * @param content the component under preview.
 */
@Composable
internal fun KrtPreviewSurface(content: @Composable () -> Unit) {
    KrtTheme {
        Box(
            modifier =
                Modifier
                    .background(KrtPalette.Black)
                    .padding(KrtSpacing.s16),
        ) {
            content()
        }
    }
}
