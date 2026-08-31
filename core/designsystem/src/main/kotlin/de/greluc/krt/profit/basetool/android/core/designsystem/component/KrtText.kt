/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_TABULAR_FIGURES
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/**
 * Uppercases a string for the styles the design system marks UPPERCASE.
 *
 * Compose has no `text-transform`, so the transformation happens at the call site. It uses the
 * **current UI locale** rather than the default one, which matters for the app's two locales and
 * for any user whose device locale uppercases differently (the classic Turkish dotted-i trap).
 *
 * @return the uppercased string.
 */
@Composable
fun String.krtUppercase(): String {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty) uppercase() else uppercase(locales[0])
}

/**
 * A screen or section heading: uppercase, orange, tracked.
 *
 * Orange carries both action and identity in this system, so headings are the one place where a
 * large orange text block is correct. Only one heading of a given level per section.
 *
 * @param text the heading; uppercased for display.
 * @param modifier layout modifier.
 * @param style the type scale entry; `headlineMedium` is the section default, `headlineLarge` the
 *   screen title.
 * @param color heading colour; orange by default.
 */
@Composable
fun KrtHeading(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.krtUppercase(),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * A quiet section title with a hairline rule running to the end of the row.
 *
 * Deliberately neutral grey rather than orange: within a screen the orange budget belongs to the
 * single primary action, so structural labels stay muted. The rule fills the remaining width, which
 * is what separates a section title from an ordinary bold label.
 *
 * @param text the title; uppercased for display.
 * @param modifier layout modifier.
 * @param trailing optional content pinned after the rule, e.g. a count or an action.
 */
@Composable
fun KrtSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.TextMuted,
        )
        KrtHairlineRule(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = KrtSpacing.s12),
        )
        trailing?.invoke(this)
    }
}

/**
 * The 1 dp horizontal rule used to extend section titles and to separate list segments.
 *
 * @param modifier layout modifier; give it a width or a weight, the height is fixed.
 * @param color rule colour; the neutral hairline by default.
 */
@Composable
fun KrtHairlineRule(
    modifier: Modifier = Modifier,
    color: Color = KrtPalette.Gray3,
) {
    Box(
        modifier =
            modifier
                .height(KrtSpacing.hairline)
                .background(color),
    )
}

/**
 * A bright numeric readout — the brightest element on a screen.
 *
 * Uses tabular figures so digits keep their column when a value updates, which is the reason the
 * design system insists on them for amounts, balances and countdowns.
 *
 * @param text the formatted number, including its thousands separators.
 * @param modifier layout modifier.
 * @param style the figure's rung on [KrtFigure] — `total` for a screen's hero number, `card`
 *   for one inside a card or row, `inline` beside a label. A number never names a heading
 *   style: those carry letter-spacing, which on digits reads as spaced-out numerals
 *   (round 15 · R2, R3).
 * @param color value colour; white by default, semantic tints for deltas and prices.
 */
@Composable
fun KrtDataValue(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = KrtTheme.colors.dataValue,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontFeatureSettings = KRT_TABULAR_FIGURES),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
