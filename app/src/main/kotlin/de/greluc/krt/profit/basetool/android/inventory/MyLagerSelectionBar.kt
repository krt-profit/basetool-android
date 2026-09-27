/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the bar's `⋮`. */
const val MY_LAGER_BAR_MORE_TAG: String = "my-lager-bar-more"

/** Test handle for the bar's „Umbuchen". */
const val MY_LAGER_BAR_REBOOK_TAG: String = "my-lager-bar-rebook"

/** Test handle for the menu's „Einheit ändern". */
const val MY_LAGER_BAR_ORG_UNIT_TAG: String = "my-lager-bar-org-unit"

/** Width of the bar's menu — artboard 2. */
private val MENU_WIDTH = 260.dp

/** How far above the bar the menu sits — artboard 2's `bottom: 76px`. */
private val MENU_LIFT = 76.dp

/** Alpha of a menu entry that does not apply to the selection (design ch. 08 overflow rule). */
private const val DIM_ALPHA = 0.45f

/** Share of the „Umbuchen" button in the bar — artboard 2's `flex: 1.1`. */
private const val REBOOK_WEIGHT = 1.1f

/**
 * One entry of the bar's menu.
 *
 * @property label what it does.
 * @property iconRes its glyph.
 * @property enabled whether it applies to the selection; an entry that does not stays visible, dimmed.
 * @property tag its test handle.
 * @property onClick what it does, after the menu has closed.
 */
internal data class BarMenuEntry(
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val enabled: Boolean,
    val tag: String,
    val onClick: () -> Unit,
)

/**
 * The selection bar of „Mein Lager": the count, „Ausbuchen", „Umbuchen" and a `⋮` with the actions
 * that do not fit at 412 dp (design ch. 19, artboard 2). Clearing the selection is the ✕ in the
 * head, which keeps the bar for actions.
 *
 * @param state the tree's state.
 * @param onCheckout the selection is to be booked out.
 * @param onRebook the selection is to be rebooked.
 * @param onOrgUnit the selection's unit is to change.
 * @param extra further menu entries, appended after „Einheit ändern".
 */
@Composable
internal fun MyLagerSelectionBar(
    state: InventoryState,
    onCheckout: () -> Unit,
    onRebook: () -> Unit,
    onOrgUnit: () -> Unit,
    extra: List<BarMenuEntry> = emptyList(),
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val entries =
        listOf(
            BarMenuEntry(
                label = stringResource(R.string.lager_entry_org_unit),
                iconRes = DesignR.drawable.ic_krt_users,
                enabled = state.online,
                tag = MY_LAGER_BAR_ORG_UNIT_TAG,
                onClick = onOrgUnit,
            ),
        ) + extra
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        KrtBottomCtaBar {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s16),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = state.selection.size.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = KrtPalette.White,
                    )
                    Text(
                        text = stringResource(R.string.inventory_selected_word).krtUppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = KrtPalette.TextMuted,
                    )
                }
                KrtGhostButton(
                    text = stringResource(R.string.booking_mode_out),
                    onClick = onCheckout,
                    iconRes = DesignR.drawable.ic_krt_upload,
                    enabled = state.online,
                    compact = true,
                    modifier = Modifier.weight(1f).testTag(INVENTORY_CHECKOUT_TAG),
                )
                KrtOutlineButton(
                    text = stringResource(R.string.stock_move_title_rebook),
                    onClick = onRebook,
                    iconRes = DesignR.drawable.ic_krt_swap,
                    enabled = state.online,
                    compact = true,
                    modifier = Modifier.weight(REBOOK_WEIGHT).testTag(MY_LAGER_BAR_REBOOK_TAG),
                )
                Box(
                    modifier =
                        Modifier
                            .size(KrtSpacing.controlHeight)
                            .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button) { open = !open }
                            .testTag(MY_LAGER_BAR_MORE_TAG),
                    contentAlignment = Alignment.Center,
                ) {
                    KrtIcon(
                        id = DesignR.drawable.ic_krt_more_v,
                        contentDescription = stringResource(R.string.lager_selection_more),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (open) {
            BarMenu(entries = entries, onDismiss = { open = false })
        }
    }
}

/**
 * The bar's menu, raised above the bar at its right edge (artboard 2).
 *
 * @param entries the entries.
 * @param onDismiss closes the menu.
 */
@Composable
private fun BarMenu(
    entries: List<BarMenuEntry>,
    onDismiss: () -> Unit,
) {
    val lift = with(LocalDensity.current) { MENU_LIFT.roundToPx() }
    val inset = with(LocalDensity.current) { KrtSpacing.s16.roundToPx() }
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(-inset, -lift),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .width(MENU_WIDTH)
                    .background(KrtPalette.SurfaceInput)
                    .border(KrtSpacing.hairline, KrtPalette.Gray3)
                    .padding(6.dp),
        ) {
            entries.forEach { entry ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = KrtSpacing.touchTarget)
                            .alpha(if (entry.enabled) 1f else DIM_ALPHA)
                            .clickable(enabled = entry.enabled, role = Role.Button) {
                                onDismiss()
                                entry.onClick()
                            }.padding(horizontal = 10.dp)
                            .testTag(entry.tag),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KrtIcon(id = entry.iconRes, contentDescription = null, tint = KrtPalette.Gray1)
                    Text(text = entry.label, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.Gray1)
                }
            }
        }
    }
}
