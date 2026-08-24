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
private val CTA_BLOOM = 20.dp

/** Opacity of a disabled action — the design system's one disabled treatment. */
private const val DISABLED_ALPHA = 0.45f

/**
 * The square floating action button: the primary action of a **list** screen.
 *
 * Square, because the design system is square-first and grants its rounded exceptions only to pill
 * badges and genuinely circular controls — a circular FAB would be the most visible contradiction
 * of that rule on the screen. 56 dp, orange fill, black glyph, and the 20 dp CTA bloom.
 *
 * **One per screen context.** It is the filled-orange action, and the ladder allows exactly one; a
 * screen that already spends its filled CTA elsewhere must not also carry a FAB.
 *
 * A list screen's *empty state* keeps its own single action instead (see [KrtEmptyState]) — that
 * action is part of the empty state's own anatomy and naming both would offer the same thing twice.
 *
 * @param iconRes the glyph, usually a plus.
 * @param label spoken description and tooltip — mandatory, because the glyph carries the whole
 *   meaning.
 * @param onClick invoked on tap.
 * @param modifier layout modifier; the caller positions it, typically bottom-end with a 16 dp
 *   margin.
 * @param enabled whether it reacts to input; disabled renders at 45 % and nothing else changes.
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
 * The bottom-anchored action bar of a **form or detail** context.
 *
 * The counterpart of [KrtFab] in design ch. 00: a list screen floats its primary action, a form or
 * a detail pins it to the bottom edge at the full 48 dp button height. Mission detail is the
 * canonical case — "ONE filled CTA, bottom-anchored" (ch. 06).
 *
 * Renders the surface fill with a hairline top rule so it separates from the content scrolling
 * underneath, and carries the CTA bloom for the same reason a modal does: it is the thing the
 * member is meant to reach for.
 *
 * The caller supplies the buttons and is responsible for the one-filled-CTA rule — a ghost cancel
 * beside one filled action is the shape this was built for.
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
                .padding(KrtSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm, Alignment.End),
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
