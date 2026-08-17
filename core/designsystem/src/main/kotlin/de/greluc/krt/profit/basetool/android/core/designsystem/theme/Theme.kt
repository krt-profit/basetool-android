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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier

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
