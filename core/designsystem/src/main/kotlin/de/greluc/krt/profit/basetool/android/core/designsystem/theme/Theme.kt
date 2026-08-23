/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The theme every Basetool screen is wrapped in.
 *
 * Dark only, on purpose: there is no light scheme and dynamic colour is never applied, because the
 * brand hues are fixed by the corporate design manual. The theme also replaces the default Material
 * ripple with the design system's white ripple, so presses read as a flat highlight instead of a
 * coloured wash.
 *
 * @param content the screen content, styled by this theme.
 */
@Composable
fun KrtTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalKrtColors provides KrtExtendedColors(),
        LocalIndication provides ripple(color = KrtPalette.White),
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
 * How tall the system's bottom bar is, measured at the app root.
 *
 * A bottom sheet cannot read this for itself: it is drawn in a window that reports no
 * navigation-bar inset, so `navigationBarsPadding()` inside one resolves to nothing while the sheet
 * still paints under the gesture bar. Its action row then ends up in the system's gesture region,
 * where a tap never reaches the button — measured on a device, 2026-08-23. Captured here, at the
 * one place that is inside the activity window and above every screen that consumes insets.
 */
val LocalKrtBottomBarInset: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { 0.dp }

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
}

/**
 * Preview scaffold for this library's component previews.
 *
 * Wraps the content in [KrtTheme] and paints the flat black page canvas with the standard screen
 * margin, so a preview shows the component as it will appear on a screen rather than on Compose's
 * default white background.
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
                    .padding(KrtSpacing.lg),
        ) {
            content()
        }
    }
}
