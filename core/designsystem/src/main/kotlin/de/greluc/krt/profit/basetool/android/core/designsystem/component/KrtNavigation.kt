/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Height of the bottom navigation bar. */
private val BOTTOM_BAR_HEIGHT = 80.dp

/** Width of the tablet navigation rail. */
private val RAIL_WIDTH = 88.dp

/** Size of the square selection indicator in the bottom bar. */
private val BAR_INDICATOR_WIDTH = 56.dp

/** Height of the square selection indicator in the bottom bar. */
private val BAR_INDICATOR_HEIGHT = 32.dp

/** Size of the square selection indicator in the rail. */
private val RAIL_INDICATOR_WIDTH = 52.dp

/** Height of the square selection indicator in the rail. */
private val RAIL_INDICATOR_HEIGHT = 30.dp

/** Glyph size inside the bottom bar indicator. */
private val BAR_ICON = 22.dp

/** Glyph size inside the rail indicator. */
private val RAIL_ICON = 20.dp

/** Width of one rail item. */
private val RAIL_ITEM_WIDTH = 80.dp

/** Minimum height of one rail item. */
private val RAIL_ITEM_HEIGHT = 56.dp

/** Edge length of the KRT mark at the head of the rail. */
private val RAIL_LOGO = 36.dp

/**
 * One navigation destination.
 *
 * @property route stable identifier of the destination; also the navigation route.
 * @property label short German label, uppercased for display.
 * @property iconRes the glyph identifying the destination.
 * @property badgeCount number of pending items, or `null` for no badge.
 */
@Immutable
data class KrtNavItem(
    val route: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val badgeCount: Int? = null,
)

/**
 * A count badge — orange block with black digits.
 *
 * Square like everything else in the system, and rendered with tabular figures so a count that
 * ticks up does not shift the layout.
 *
 * @param count the number to show; values above 99 render as "99+".
 * @param modifier layout modifier.
 */
@Composable
fun KrtCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = BADGE_MIN_SIZE, minHeight = BADGE_MIN_SIZE)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > BADGE_MAX) "$BADGE_MAX+" else count.toString(),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = BADGE_TEXT_SIZE,
                    fontFeatureSettings = "tnum",
                ),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Smallest edge of a count badge. */
private val BADGE_MIN_SIZE = 16.dp

/** Text size inside a count badge. */
private val BADGE_TEXT_SIZE = 10.sp

/** Counts above this render as "99+". */
private const val BADGE_MAX = 99

/**
 * One navigation item — a square indicator with a glyph, plus a label underneath.
 *
 * The indicator is deliberately **square**, not the Material pill: `NavigationBarItem` renders its
 * active indicator with a shape that the theme cannot override, so the design system's bar and rail
 * are built here instead of on the Material components. The selected colours follow the brand rule
 * carried by `secondaryContainer`/`onSecondaryContainer` — orange background, black glyph.
 *
 * @param item the destination.
 * @param selected whether this is the active destination.
 * @param onClick invoked on tap; the caller decides whether that navigates or pops to root.
 * @param indicatorWidth width of the selection indicator.
 * @param indicatorHeight height of the selection indicator.
 * @param iconSize glyph size inside the indicator.
 * @param labelSize label text size.
 * @param labelTracking label letter spacing.
 * @param modifier layout modifier.
 */
@Composable
private fun KrtNavItemContent(
    item: KrtNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    indicatorWidth: androidx.compose.ui.unit.Dp,
    indicatorHeight: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    labelTracking: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    val indicator = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val glyph = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else KrtPalette.TextMuted
    val label = if (selected) KrtPalette.White else KrtPalette.TextMuted

    Column(
        modifier =
            modifier
                .defaultMinSize(minHeight = KrtSpacing.navIconFloor)
                .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4, Alignment.CenterVertically),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = indicatorWidth, height = indicatorHeight)
                    .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            KrtIcon(id = item.iconRes, contentDescription = null, size = iconSize, tint = glyph)
            if (item.badgeCount != null) {
                KrtCountBadge(
                    count = item.badgeCount,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Text(
            text = item.label.krtUppercase(),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = labelSize,
                    letterSpacing = labelTracking,
                ),
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The phone navigation bar: up to five destinations across the bottom.
 *
 * Five is the hard ceiling of the design spec; everything else lives behind the "Mehr" destination.
 *
 * @param items the destinations, in order.
 * @param selectedRoute route of the active destination.
 * @param onSelect invoked with the tapped destination.
 * @param modifier layout modifier.
 */
@Composable
fun KrtBottomBar(
    items: List<KrtNavItem>,
    selectedRoute: String,
    onSelect: (KrtNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .height(BOTTOM_BAR_HEIGHT)
                .padding(top = KrtSpacing.s8, bottom = KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            KrtNavItemContent(
                item = item,
                selected = item.route == selectedRoute,
                onClick = { onSelect(item) },
                indicatorWidth = BAR_INDICATOR_WIDTH,
                indicatorHeight = BAR_INDICATOR_HEIGHT,
                iconSize = BAR_ICON,
                labelSize = BAR_LABEL_SIZE,
                labelTracking = BAR_LABEL_TRACKING,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Label size in the bottom bar. */
private val BAR_LABEL_SIZE = 10.5.sp

/** Label letter spacing in the bottom bar. */
private val BAR_LABEL_TRACKING = 0.4.sp

/** Label size in the rail. */
private val RAIL_LABEL_SIZE = 9.5.sp

/** Label letter spacing in the rail. */
private val RAIL_LABEL_TRACKING = 0.3.sp

/**
 * The tablet navigation rail: the Basetool app mark, up to eight destinations, and a footer slot.
 *
 * Wider than the phone bar can carry, which is why the tablet exposes the destinations that sit
 * behind "Mehr" on a phone.
 *
 * @param items the destinations, in order.
 * @param selectedRoute route of the active destination.
 * @param onSelect invoked with the tapped destination.
 * @param modifier layout modifier.
 * @param footer optional trailing content pinned to the bottom of the rail, e.g. settings.
 */
@Composable
fun KrtNavigationRail(
    items: List<KrtNavItem>,
    selectedRoute: String,
    onSelect: (KrtNavItem) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = KrtSpacing.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.krt_basetool_logo),
            contentDescription = null,
            modifier = Modifier.size(RAIL_LOGO).padding(bottom = KrtSpacing.s12),
        )
        items.forEach { item ->
            KrtNavItemContent(
                item = item,
                selected = item.route == selectedRoute,
                onClick = { onSelect(item) },
                indicatorWidth = RAIL_INDICATOR_WIDTH,
                indicatorHeight = RAIL_INDICATOR_HEIGHT,
                iconSize = RAIL_ICON,
                labelSize = RAIL_LABEL_SIZE,
                labelTracking = RAIL_LABEL_TRACKING,
                modifier = Modifier.width(RAIL_ITEM_WIDTH).defaultMinSize(minHeight = RAIL_ITEM_HEIGHT),
            )
        }
        Box(modifier = Modifier.weight(1f))
        footer?.invoke()
    }
}

@Preview(name = "Bottom bar", showBackground = true, backgroundColor = 0xFF000000, widthDp = 412)
@Composable
private fun BottomBarPreview() {
    KrtPreviewSurface {
        KrtBottomBar(
            items =
                listOf(
                    KrtNavItem("home", "Übersicht", R.drawable.ic_krt_dashboard),
                    KrtNavItem("missions", "Einsätze", R.drawable.ic_krt_target, badgeCount = 2),
                    KrtNavItem("orders", "Aufträge", R.drawable.ic_krt_clipboard_list),
                    KrtNavItem("inventory", "Lager", R.drawable.ic_krt_crate),
                    KrtNavItem("more", "Mehr", R.drawable.ic_krt_more_h),
                ),
            selectedRoute = "home",
            onSelect = {},
        )
    }
}
