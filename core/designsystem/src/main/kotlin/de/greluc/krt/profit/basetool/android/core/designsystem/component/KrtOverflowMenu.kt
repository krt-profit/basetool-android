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
 * @property danger whether the entry destroys something — it is then stated in the danger tint so
 *   it cannot be mistaken for the routine entry above it.
 * @property reason a line under the label — what the entry does, or, on an entry that cannot be
 *   chosen, why not. „Keine Schiffe im Hangar" is the whole answer a member needs, and a menu that
 *   dims a row without saying why is a menu that looks broken.
 * @property enabled whether it can be chosen at all; a disabled entry **stays visible** and recedes
 *   to 45 %, so the menu never changes shape between openings and a member can still see what the
 *   feature is (design ch. 08, artboard 5).
 * @property locked whether the caller lacks the grant for it. Drawn like [enabled] `= false` plus a
 *   lock glyph and answered on tap, per `REQ-APP-AUTH-013` — never hidden, and distinct from a row
 *   that is merely inapplicable right now.
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
 * The top bar's `⋮`, and the menu it opens.
 *
 * The design system has no dropdown of its own, so this follows `.assoc-pop`: 268 dp wide, the
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
    // Destructive rows are red **in the menu**, not only in the modal that follows: the colour is
    // the warning and the modal is the confirmation. Red appearing first at the modal would be a
    // surprise at the second step (design ch. 08, artboards 4–6).
    val tint = if (item.danger) KrtTheme.colors.dangerText else KrtPalette.Gray1
    val dimmed = !item.enabled || item.locked
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                // A locked row keeps its tap target so it can name the grant it wants; a row that
                // is merely inapplicable has nothing to say beyond its reason line and takes none.
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
