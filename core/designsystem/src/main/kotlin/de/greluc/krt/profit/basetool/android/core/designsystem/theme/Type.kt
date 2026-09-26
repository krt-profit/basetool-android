/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.core.designsystem.R

/**
 * Lato, the single typeface of the design system, bundled under the SIL Open Font License 1.1
 * (`core/designsystem/licenses/LATO_OFL.txt`) and never downloaded at runtime.
 *
 * Registers Light 300, Regular 400, Bold 700 and Black 900.
 */
val Lato =
    FontFamily(
        Font(R.font.lato_light, FontWeight.Light),
        Font(R.font.lato_regular, FontWeight.Normal),
        Font(R.font.lato_bold, FontWeight.Bold),
        Font(R.font.lato_black, FontWeight.Black),
    )

/**
 * Line-height behaviour shared by every style: the extra leading is distributed evenly around the
 * text instead of being trimmed, which keeps dense rows optically centred at any font scale.
 */
private val FlatLineHeight =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

/**
 * Builds one entry of the type scale, with `includeFontPadding` off so boxes match the mockups.
 *
 * @param weight the Lato cut to use.
 * @param size font size in sp.
 * @param line line height in sp.
 * @param track letter spacing in sp, converted from the design system's em values.
 * @return the configured [TextStyle].
 */
@Suppress("MagicNumber")
private fun lato(
    weight: FontWeight,
    size: Int,
    line: Int,
    track: Double = 0.0,
): TextStyle =
    TextStyle(
        fontFamily = Lato,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = track.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = FlatLineHeight,
    )

/**
 * The Material 3 type scale of the app, mapped from the design system's scale.
 *
 * Styles marked UPPERCASE do not transform text; call sites uppercase it (the `KrtText` helpers do).
 * Every style must survive a 1.3× font scale without truncation.
 */
@Suppress("MagicNumber")
val KrtTypography =
    Typography(
        displayMedium = lato(FontWeight.Black, 40, 44),
        displaySmall = lato(FontWeight.Black, 32, 36),
        headlineLarge = lato(FontWeight.Black, 32, 38, 1.6),
        headlineMedium = lato(FontWeight.Bold, 24, 30, 1.2),
        headlineSmall = lato(FontWeight.Bold, 19, 25, 0.95),
        titleLarge = lato(FontWeight.Bold, 19, 25),
        titleMedium = lato(FontWeight.Bold, 16, 22),
        titleSmall = lato(FontWeight.Bold, 14, 20, 0.7),
        bodyLarge = lato(FontWeight.Light, 16, 24),
        bodyMedium = lato(FontWeight.Light, 14, 21),
        bodySmall = lato(FontWeight.Light, 13, 20),
        labelLarge = lato(FontWeight.Bold, 13, 16, 0.39),
        labelMedium = lato(FontWeight.Bold, 11, 14, 0.55),
        labelSmall = lato(FontWeight.Bold, 11, 14, 1.65),
    )

/**
 * Font feature setting that switches Lato to tabular (fixed-width) figures.
 *
 * Apply it to every number that sits in a column or changes while on screen — amounts, quantities,
 * countdowns, balances — so digits do not jitter horizontally as values update.
 */
const val KRT_TABULAR_FIGURES = "tnum"

/**
 * The type ladder for numbers: three rungs, Black, tabular, and with no letter-spacing.
 *
 * @property total the one hero number of a screen — a KPI total, a balance hero, an attendance
 *   count.
 * @property card a figure inside a card or a list row — an account balance, a per-head share.
 * @property inline a figure beside a label — a section count, „3 / 3".
 */
object KrtFigure {
    val total: TextStyle =
        lato(FontWeight.Black, 32, 36).copy(fontFeatureSettings = KRT_TABULAR_FIGURES)

    val card: TextStyle =
        lato(FontWeight.Black, 20, 24).copy(fontFeatureSettings = KRT_TABULAR_FIGURES)

    val inline: TextStyle =
        lato(FontWeight.Black, 16, 20).copy(fontFeatureSettings = KRT_TABULAR_FIGURES)
}
