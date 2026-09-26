/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtBloom
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtHairline
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Edge length of the floating action button — square, per design ch. 00. */
private val FAB_SIZE = 56.dp

/** Size of the glyph inside it. */
private val FAB_ICON = 24.dp

/** Radius of the CTA bloom, shared by the FAB and the bottom bar. */
private val CTA_BLOOM = KrtSpacing.glowOverlay

/** Opacity of a disabled action — the design system's one disabled treatment. */
private const val DISABLED_ALPHA = 0.45f

/**
 * The square floating action button: the primary action of a list screen.
 *
 * 56 dp, orange fill, black glyph, overlay glow. One per screen context, and not on a screen whose
 * empty state ([KrtEmptyState]) already offers the action.
 *
 * @param iconRes the glyph, usually a plus.
 * @param label spoken description and tooltip; mandatory, since the glyph carries the meaning.
 * @param onClick invoked on tap.
 * @param modifier layout modifier; the caller positions it, typically bottom-end with a 16 dp
 *   margin.
 * @param enabled whether it reacts to input; disabled renders at 45 %.
 */
@Composable
fun KrtFab(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(FAB_SIZE)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .krtBloom(KrtTheme.colors.glowPrimaryLg, CTA_BLOOM)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        KrtIcon(
            id = iconRes,
            contentDescription = label,
            size = FAB_ICON,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * The bottom-anchored action bar of a form or detail context, the counterpart of [KrtFab].
 *
 * Draws the surface fill with a hairline top rule and the CTA bloom; the caller supplies the
 * buttons and keeps to one filled CTA.
 *
 * @param modifier layout modifier; the caller anchors it, typically to the bottom of a Box.
 * @param content the buttons, laid out end-aligned in a row.
 */
@Composable
fun KrtBottomCtaBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .krtBloom(KrtTheme.colors.glowPrimaryLg, CTA_BLOOM)
                .background(KrtPalette.Gray4)
                .krtHairline()
                .padding(KrtSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Preview(name = "Actions", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ActionsPreview() {
    KrtPreviewSurface {
        Box {
            KrtFab(
                iconRes = R.drawable.ic_krt_plus,
                label = "Schiff hinzufügen",
                onClick = {},
            )
        }
    }
}
