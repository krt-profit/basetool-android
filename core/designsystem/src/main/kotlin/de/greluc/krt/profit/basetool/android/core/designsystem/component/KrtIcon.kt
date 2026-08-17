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
 * Renders one glyph of the in-house KRT icon set.
 *
 * The icons live as VectorDrawables in this module (`ic_krt_*`), generated from the design system's
 * sprite: 24 dp viewport, stroke-only 2 dp, round caps and joins. They are drawn in white and
 * recoloured here, so a glyph always adopts the surrounding content colour unless told otherwise —
 * icons are never multi-coloured. No third-party icon library is used anywhere in the app, and
 * emoji are not used as icons.
 *
 * Pass `contentDescription = null` only when the icon is decorative and an adjacent label already
 * names the action; icon-only buttons must always pass a description.
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
