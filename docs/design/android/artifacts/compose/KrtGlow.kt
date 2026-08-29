/*
 * Basetool Android — DAS KARTELL / Bereich Profit design system.
 * GENERATED FROM THE DESIGN SPEC (docs/design/android, chapters 00–17).
 *
 * Rule for whoever implements this: every value here is decided. Do not tune, round or
 * "improve" one. If something you need is missing, it is a spec gap — raise it, do not invent it.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ───────────────────────────── GLOW ─────────────────────────────
 * The system has NO drop shadows for depth — depth is hairlines and corner brackets. The only
 * light effect is a restrained orange bloom, and "restrained" is a number, not a feeling:
 *
 *   HARD CAPS — nothing in the app may exceed either of these:
 *       radius <= 12.dp        alpha <= 0.10
 *
 * Three sizes, and only these three. A surface that wants a fourth does not need a glow.
 *   Focus     6.dp / 0.10   input focus, focused chip or tab — sits ON the border
 *   Emphasis 12.dp / 0.07   a bar carrying the one primary action (CTA bar, selection bar)
 *   Overlay  12.dp / 0.10   a surface floating over content (bottom sheet, modal, toast)
 *
 * Elevation stays 0.dp on every Material container: no shadowElevation, no tonalElevation, and
 * never two blooms on one screen — that reads as a bug, not as depth.
 */
object KrtGlow {
    val focusRadius: Dp = 6.dp
    const val FOCUS_ALPHA = 0.10f

    val emphasisRadius: Dp = 12.dp
    const val EMPHASIS_ALPHA = 0.07f

    val overlayRadius: Dp = 12.dp
    const val OVERLAY_ALPHA = 0.10f

    /** Absolute ceilings — assert against these in a unit test so a later tweak cannot pass. */
    val MAX_RADIUS: Dp = 12.dp
    const val MAX_ALPHA = 0.10f
}

/**
 * Draws the bloom outside the composable bounds, behind it. Square by definition (radius 0
 * everywhere), so there is no corner-radius parameter.
 */
fun Modifier.krtGlow(
    radius: Dp,
    alpha: Float,
    color: Color = KrtPalette.Primary,
): Modifier = drawBehind {
    if (alpha <= 0f || radius <= 0.dp) return@drawBehind
    require(alpha <= KrtGlow.MAX_ALPHA) { "glow alpha exceeds the 0.10 cap" }
    require(radius <= KrtGlow.MAX_RADIUS) { "glow radius exceeds the 12.dp cap" }
    val spread = radius.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color.copy(alpha = alpha)
            style = PaintingStyle.Fill
        }
        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(spread, BlurMaskFilter.Blur.NORMAL)
        canvas.nativeCanvas.drawRect(
            -spread / 2f,
            -spread / 2f,
            size.width + spread / 2f,
            size.height + spread / 2f,
            paint.asFrameworkPaint(),
        )
    }
}

/** Input focus / focused chip. Applied only while focused — never at rest. */
fun Modifier.krtFocusGlow(active: Boolean, color: Color = KrtPalette.Primary): Modifier =
    if (active) krtGlow(KrtGlow.focusRadius, KrtGlow.FOCUS_ALPHA, color) else this

/** A bar that carries the one primary action (bottom CTA bar, selection bar). */
fun Modifier.krtEmphasisGlow(color: Color = KrtPalette.Primary): Modifier =
    krtGlow(KrtGlow.emphasisRadius, KrtGlow.EMPHASIS_ALPHA, color)

/** A surface floating over content: bottom sheet, modal, toast. */
fun Modifier.krtOverlayGlow(color: Color = KrtPalette.Primary): Modifier =
    krtGlow(KrtGlow.overlayRadius, KrtGlow.OVERLAY_ALPHA, color)
