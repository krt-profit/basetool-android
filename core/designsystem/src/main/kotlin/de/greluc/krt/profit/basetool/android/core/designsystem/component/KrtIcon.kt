/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders one glyph of the in-house KRT icon set (`ic_krt_*` VectorDrawables), tinted to a single
 * colour.
 *
 * Pass `contentDescription = null` only when an adjacent label already names the action.
 *
 * @param id the drawable resource, e.g. `R.drawable.ic_krt_check`.
 * @param contentDescription spoken description for TalkBack, or `null` when purely decorative.
 * @param modifier layout modifier for the glyph.
 * @param size edge length of the glyph; 24 dp is the canon, 16 dp inside buttons, 18–22 dp in rows.
 * @param tint colour of the glyph; defaults to the inherited content colour.
 */
@Composable
fun KrtIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
