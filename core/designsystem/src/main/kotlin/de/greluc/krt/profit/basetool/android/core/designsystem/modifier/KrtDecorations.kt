/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.modifier

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * Draws the HUD corner brackets on top of the content: two L-shaped strokes of [leg] length in
 * diagonally opposite corners.
 *
 * @param color bracket colour; orange for standard containers, danger red for the danger modal.
 * @param leg length of each bracket leg (10 dp on containers, 13 dp on modals).
 * @param stroke stroke width of the bracket (2 dp on containers, 3 dp on modals).
 * @param corners which diagonal pair to draw.
 * @return the decorated modifier.
 */
fun Modifier.krtCornerBrackets(
    color: Color = KrtPalette.Orange,
    leg: Dp = KrtSpacing.bracket,
    stroke: Dp = 2.dp,
    corners: KrtBracketCorners = KrtBracketCorners.TopLeftBottomRight,
): Modifier =
    this.drawWithContent {
        drawContent()
        val legPx = leg.toPx()
        val strokePx = stroke.toPx()
        val inset = strokePx / 2f
        val w = size.width
        val h = size.height

        fun l(
            from: Offset,
            to: Offset,
        ) = drawLine(color = color, start = from, end = to, strokeWidth = strokePx)

        when (corners) {
            KrtBracketCorners.TopLeftBottomRight -> {
                l(Offset(0f, inset), Offset(legPx, inset))
                l(Offset(inset, 0f), Offset(inset, legPx))
                l(Offset(w - legPx, h - inset), Offset(w, h - inset))
                l(Offset(w - inset, h - legPx), Offset(w - inset, h))
            }

            KrtBracketCorners.TopRightBottomLeft -> {
                l(Offset(w - legPx, inset), Offset(w, inset))
                l(Offset(w - inset, 0f), Offset(w - inset, legPx))
                l(Offset(0f, h - inset), Offset(legPx, h - inset))
                l(Offset(inset, h - legPx), Offset(inset, h))
            }
        }
    }

/**
 * Paints the orange bloom, the only permitted glow, as concentric strokes outside the bounds with
 * quadratically falling alpha.
 *
 * Marks focus and the primary action; never use it for elevation.
 *
 * @param color the glow colour including its alpha; the alpha is the peak value at the edge.
 * @param radius how far the glow reaches beyond the component bounds.
 * @param layers number of stacked strokes; more layers are smoother and cost more per frame.
 * @return the decorated modifier.
 */
fun Modifier.krtBloom(
    color: Color,
    radius: Dp,
    layers: Int = DEFAULT_BLOOM_LAYERS,
): Modifier =
    this.drawBehind {
        val radiusPx = radius.toPx()
        val step = radiusPx / layers
        repeat(layers) { index ->
            val spread = step * (index + 1)
            val distance = (index + 1).toFloat() / layers
            val alpha = color.alpha * (1f - distance) * (1f - distance)
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                style = Stroke(width = step),
            )
        }
    }

/**
 * Number of stacked strokes approximating a CSS blur radius.
 *
 * Twelve is where the steps stop being visible at the 12 dp overlay glow; fewer layers band, more
 * cost draw calls for no perceptible gain.
 */
private const val DEFAULT_BLOOM_LAYERS = 12

/**
 * The 1 dp hairline that carries depth throughout the app.
 *
 * @param color border colour; defaults to the neutral hairline, pass orange for accented states.
 * @return the decorated modifier.
 */
@Composable
fun Modifier.krtHairline(color: Color = KrtPalette.Gray3): Modifier = this.border(KrtSpacing.hairline, color)
