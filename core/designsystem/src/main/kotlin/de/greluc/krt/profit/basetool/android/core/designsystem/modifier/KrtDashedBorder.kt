/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The dash and the gap, in dp — the `.assoc-add` and empty-state rhythm. */
private val DASH = 4.dp

/**
 * A dashed square hairline around the content, marking a place something goes (`.assoc-add`, the
 * empty state).
 *
 * @param color the hairline's colour.
 * @param width its thickness; the design system's hairline is 1 dp everywhere.
 * @return the modifier.
 */
fun Modifier.krtDashedBorder(
    color: Color,
    width: Dp = 1.dp,
): Modifier =
    drawBehind {
        val stroke = width.toPx()
        val dash = DASH.toPx()
        drawRect(
            color = color,
            style =
                Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f),
                ),
        )
    }
