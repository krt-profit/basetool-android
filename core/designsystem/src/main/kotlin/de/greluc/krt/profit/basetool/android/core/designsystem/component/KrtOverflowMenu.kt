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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Width of the open menu, from `.assoc-pop` in the handoff CSS. */
private val MENU_WIDTH = 260.dp

/** How far below the trigger the menu hangs, matching the CSS's `top: calc(100% + 5px)`. */
private val MENU_DROP = 5.dp

/** Size of the glyph on a menu item. */
private val ITEM_ICON = 18.dp

/**
 * One entry of an overflow menu.
 *
 * @property label what the entry does, in the member's language.
 * @property iconRes optional leading glyph.
 * @property danger whether the entry destroys something — it is then stated in the danger tint so
 *   it cannot be mistaken for the routine entry above it.
 * @property enabled whether it can be chosen at all; a disabled entry stays visible so the menu
 *   does not change shape between openings.
 * @property onClick what to run once the menu has closed.
 */
data class KrtMenuItem(
    val label: String,
    @param:DrawableRes val iconRes: Int? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The top bar's `⋮`, and the menu it opens.
 *
 * The design system has no dropdown of its own, so this follows `.assoc-pop`: 260 dp wide, the
 * dark-gray fill, an orange frame, and hairlines between the entries. Material's own `DropdownMenu`
 * is deliberately not used — it brings rounded corners, a ripple and an elevation tint that the
 * square-first system rules out.
 *
 * The menu closes before the entry runs. An entry that opens a modal would otherwise leave the menu
 * standing behind it, and a member who dismisses the modal would find the menu still open over a
 * screen they thought they had returned to.
 *
 * Stateless, like every other picker in this system: the caller owns whether it is open. That is
 * not a stylistic choice here — the menu is handed to the top bar as a lambda, and a lambda's
 * composition group is replaced whenever its instance changes, taking any state inside it with it.
 *
 * @param items the entries, in the order the chapter lists them.
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
    val tint =
        when {
            !item.enabled -> KrtPalette.Gray2
            item.danger -> KrtTheme.colors.dangerText
            else -> KrtPalette.Gray1
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .clickable(enabled = item.enabled, role = Role.Button, onClick = onChosen)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.iconRes?.let { icon ->
            KrtIcon(id = icon, contentDescription = null, size = ITEM_ICON, tint = tint)
        }
        Text(text = item.label, style = MaterialTheme.typography.bodyMedium, color = tint)
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
