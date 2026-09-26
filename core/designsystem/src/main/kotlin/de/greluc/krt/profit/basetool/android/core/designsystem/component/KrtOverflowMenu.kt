/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Width of the open menu — design ch. 08, artboard 5 („rechtsbündig unter der Leiste, 268 dp"). */
private val MENU_WIDTH = 268.dp

/** How far below the trigger the menu hangs, matching the CSS's `top: calc(100% + 5px)`. */
private val MENU_DROP = 5.dp

/** Size of the glyph on a menu item. */
private val ITEM_ICON = 18.dp

/**
 * How far a row recedes when the caller cannot choose it — design ch. 08, artboard 5.
 *
 * The row itself is dimmed rather than its text recoloured, so the glyph, the label and the reason
 * fade together and the row still reads as one thing.
 */
private const val ROW_DISABLED_ALPHA = 0.45f

/** Size of a row's reason line, the design system's `--fs-2xs` rung in Light. */
private val ROW_REASON_SIZE = 11.sp

/** Its line height, the 1.4 ratio the whole scale uses. */
private val ROW_REASON_LINE = 15.sp

/**
 * One entry of an overflow menu.
 *
 * @property label what the entry does, in the member's language.
 * @property iconRes optional leading glyph.
 * @property danger whether the entry destroys something; drawn in the danger tint.
 * @property reason a line under the label — what the entry does, or why it cannot be chosen.
 * @property enabled whether it can be chosen; a disabled entry stays visible at 45 %.
 * @property locked whether the caller lacks the grant; drawn disabled with a lock glyph and
 *   answered on tap (`REQ-APP-AUTH-013`).
 * @property onClick what to run once the menu has closed.
 */
data class KrtMenuItem(
    val label: String,
    @param:DrawableRes val iconRes: Int? = null,
    val danger: Boolean = false,
    val reason: String? = null,
    val enabled: Boolean = true,
    val locked: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The top bar's `⋮` and the menu it opens, styled after `.assoc-pop`: 268 dp wide, orange frame,
 * hairlines between entries.
 *
 * The menu closes before the entry runs. Stateless: the caller owns whether it is open.
 *
 * @param items the entries, in order.
 * @param contentDescription what the trigger is called for TalkBack.
 * @param expanded whether the menu is showing.
 * @param onExpandedChange invoked when the trigger is tapped or the menu is dismissed.
 * @param modifier layout modifier.
 */
@Composable
fun KrtOverflowMenu(
    items: List<KrtMenuItem>,
    contentDescription: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        KrtIconButton(
            iconRes = R.drawable.ic_krt_more_v,
            label = contentDescription,
            onClick = { onExpandedChange(true) },
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        0,
                        with(androidx.compose.ui.platform.LocalDensity.current) {
                            (
                                KrtSpacing.touchTarget +
                                    MENU_DROP
                            ).roundToPx()
                        },
                    ),
                onDismissRequest = { onExpandedChange(false) },
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(MENU_WIDTH)
                            .background(KrtPalette.Gray4)
                            .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary),
                ) {
                    items.forEachIndexed { index, item ->
                        MenuRow(
                            item = item,
                            divider = index < items.lastIndex,
                            onChosen = {
                                onExpandedChange(false)
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of the open menu.
 *
 * @param item the entry.
 * @param divider whether a hairline closes it off — every row but the last.
 * @param onChosen invoked on tap, after the menu has been closed.
 */
@Composable
private fun MenuRow(
    item: KrtMenuItem,
    divider: Boolean,
    onChosen: () -> Unit,
) {
    val tint = if (item.danger) KrtTheme.colors.dangerText else KrtPalette.Gray1
    val dimmed = !item.enabled || item.locked
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .clickable(enabled = item.enabled || item.locked, role = Role.Button, onClick = onChosen)
                .then(if (dimmed) Modifier.alpha(ROW_DISABLED_ALPHA) else Modifier)
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.iconRes?.let { icon ->
            KrtIcon(id = icon, contentDescription = null, size = ITEM_ICON, tint = tint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label.krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
            item.reason?.let { reason ->
                Text(
                    text = reason,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = ROW_REASON_SIZE,
                            lineHeight = ROW_REASON_LINE,
                        ),
                    color = KrtPalette.TextMuted,
                )
            }
        }
        if (item.locked) {
            KrtIcon(
                id = R.drawable.ic_krt_lock,
                contentDescription = null,
                size = ITEM_ICON,
                tint = KrtPalette.TextMuted,
            )
        }
    }
    if (divider) {
        KrtHairlineRule()
    }
}

/** The menu as the Hangar uses it. */
@Preview(name = "Overflow menu", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun KrtOverflowMenuPreview() {
    KrtPreviewSurface {
        KrtOverflowMenu(
            items =
                listOf(
                    KrtMenuItem(label = "Home-Location setzen", iconRes = R.drawable.ic_krt_map_pin) {},
                    KrtMenuItem(label = "Fleetview-Import", iconRes = R.drawable.ic_krt_upload) {},
                    KrtMenuItem(label = "Hangar leeren", iconRes = R.drawable.ic_krt_trash, danger = true) {},
                ),
            contentDescription = "Weitere Aktionen",
            expanded = true,
            onExpandedChange = {},
        )
    }
}
