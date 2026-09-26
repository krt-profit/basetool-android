/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Every Material 3 shape slot is square (0 dp); rounding is reserved for [PillShape] and genuinely
 * circular controls.
 */
val KrtShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )

/**
 * The only rounded shape in the system — a full pill.
 *
 * Reserved for the squadron/org badge and the bottom-sheet drag handle. Using it anywhere else
 * (buttons, chips, cards) contradicts the square-first rule.
 */
val PillShape = RoundedCornerShape(percent = 50)
