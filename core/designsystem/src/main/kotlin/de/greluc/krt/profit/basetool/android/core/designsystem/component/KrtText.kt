/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_TABULAR_FIGURES
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * How long a travelling label rests at each end, in milliseconds.
 *
 * Long enough to read the end it has just reached before it sets off again.
 */
private const val MARQUEE_PAUSE_MS = 1200L

/**
 * How long a travelling label takes per pixel, in milliseconds.
 *
 * A speed rather than a duration: 60 px/s, slow enough to read a name as it passes.
 */
private const val MS_PER_PX = 1000.0 / 60.0

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
                    .padding(horizontal = KrtSpacing.md),
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
 * @param style type scale entry; `titleMedium` for row values, `displaySmall` for KPI heroes.
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

/**
 * A one-line label that travels back and forth when it does not fit, instead of being cut off.
 *
 * Ellipsis is the right answer for prose that a member can open to read in full. It is the wrong one
 * for a **name they have to recognise**: „ARC-L1 Wide Forest Station" and „ARC-L1 Wide Forest Depot"
 * both arrive as „ARC-L1 Wide Fores…", and a row that cannot be told from its neighbour is worse
 * than one that takes a moment to read.
 *
 * **It travels and returns** rather than looping like a news ticker. A loop wraps the end of the
 * text around to its beginning, which for a place name reads as a different, wrong name every time
 * it crosses the seam. Out and back always shows the text in its own order.
 *
 * **It moves at a constant speed**, not over a fixed duration, so a long name is not raced past
 * faster than a short one — and rows of different lengths in one list stay legible together rather
 * than each animating at its own rate.
 *
 * **It only moves when it has to.** A label that fits is drawn as plain text and animates nothing,
 * which is what keeps a list of twenty rows still.
 *
 * **Reduced motion turns it off.** With `KrtTheme.motionMs` at zero — the accessibility toggle under
 * *Einstellungen → Bedienungshilfen → Animationen entfernen* — it falls back to the ellipsis. This
 * is decoration in the sense that rule is about: nothing is lost that the member cannot get by
 * opening the row, so unlike the loading spinner it does yield.
 *
 * @param text the label.
 * @param style how to draw it.
 * @param color what colour to draw it in.
 * @param modifier layout modifier; give it the width it may use, usually a `weight`.
 */
@Composable
fun KrtMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Two widths, not one: the text reports what it wants and the box reports what it has, and the
    // difference is what there is to travel. Folding them into a single figure made one measurement
    // overwrite the other, and which of the two won depended on the order the callbacks happened to
    // arrive in.
    var textWidth by remember(text) { mutableIntStateOf(0) }
    var boxWidth by remember { mutableIntStateOf(0) }
    val overflow = (textWidth - boxWidth).coerceAtLeast(0)
    val offset = remember { Animatable(0f) }
    // Hoisted: KrtTheme.motionMs is a composable read and the effect below is not a composition.
    val motionMs = KrtTheme.motionMs

    LaunchedEffect(overflow, motionMs) {
        if (overflow <= 0 || motionMs <= 0) {
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        // Constant velocity: the further it has to travel, the longer it takes.
        val travelMs = (overflow * MS_PER_PX).toInt().coerceAtLeast(motionMs)
        while (true) {
            delay(MARQUEE_PAUSE_MS)
            offset.animateTo(-overflow.toFloat(), tween(travelMs, easing = LinearEasing))
            delay(MARQUEE_PAUSE_MS)
            offset.animateTo(0f, tween(travelMs, easing = LinearEasing))
        }
    }

    Box(modifier = modifier.clipToBounds().onSizeChanged { boxWidth = it.width }) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            // Ellipsis is what a member sees while reduced motion is on, and for the frame before
            // the first measurement lands.
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    // Unbounded, so the text may report the width it actually wants rather than the
                    // width it was given — which is the whole measurement this depends on.
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .onSizeChanged { textWidth = it.width },
        )
    }
}
