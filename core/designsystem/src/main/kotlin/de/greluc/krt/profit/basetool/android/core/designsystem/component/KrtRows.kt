/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Tint fill of a row in selection mode. */
private const val SELECTED_ROW_ALPHA = 0.08f

/** Width of the orange inset bar marking a selected row. */
private val SELECTION_BAR = 3.dp

/** Size of the leading glyph in a dense row. */
private val ROW_ICON = 24.dp

/** Size of the trailing chevron. */
private val ROW_CHEVRON = 18.dp

/**
 * The canonical dense list row of the app.
 *
 * The whole row is one touch target — comfortably above the 48 dp minimum at its 56 dp height — so
 * users never have to hit a small chevron. The trailing block is where the row's number goes
 * (countdown, quantity, balance); it renders with tabular figures through [KrtDataValue] so a list
 * of numbers stays aligned.
 *
 * Long-press selects when [onLongClick] is supplied, which is how the multi-select surfaces of the
 * Lager and inbox screens work.
 *
 * @param title the record's name; truncated with an ellipsis rather than wrapped.
 * @param modifier layout modifier.
 * @param subtitle optional second line — timestamps, status, context.
 * @param leadingIcon optional leading glyph identifying the record type.
 * @param trailingValue optional bright value on the right (countdown, amount).
 * @param trailingLabel optional muted caption under [trailingValue].
 * @param showChevron whether to render the "opens a detail" chevron.
 * @param selected whether the row is currently selected in selection mode.
 * @param onClick invoked on tap.
 * @param onLongClick invoked on long press; supply it to enable selection mode.
 */
@Composable
fun KrtListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    trailingValue: String? = null,
    trailingLabel: String? = null,
    showChevron: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_ALPHA)
        } else {
            MaterialTheme.colorScheme.surface
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .defaultMinSize(minHeight = KrtSpacing.denseRow)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .width(SELECTION_BAR)
                        .size(width = SELECTION_BAR, height = KrtSpacing.xl)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (leadingIcon != null) {
            KrtIcon(
                id = leadingIcon,
                contentDescription = null,
                size = ROW_ICON,
                tint = KrtPalette.TextMuted,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingValue != null) {
            Column(horizontalAlignment = Alignment.End) {
                KrtDataValue(text = trailingValue)
                if (trailingLabel != null) {
                    Text(
                        text = trailingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
        if (showChevron) {
            KrtIcon(
                id = R.drawable.ic_krt_chevron_right,
                contentDescription = null,
                size = ROW_CHEVRON,
                tint = KrtPalette.Gray2,
            )
        }
    }
}

/**
 * The "load more" control at the foot of a paginated list.
 *
 * The label always states how much of the whole is loaded ("Mehr laden — 40 von 143"): the app
 * never truncates a list silently, so the user can tell a short list from a capped one.
 *
 * @param text the label including the counts.
 * @param onClick loads the next page.
 * @param modifier layout modifier.
 * @param enabled whether loading is currently possible (disabled while a page is in flight).
 */
@Composable
fun KrtLoadMore(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    KrtGhostButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    )
}

/**
 * The end-of-list marker: a muted centred label between two hairlines.
 *
 * Shown when everything is loaded, so that the absence of a "load more" button is never ambiguous.
 *
 * @param text the marker label, e.g. "Ende der Liste"; uppercased for display.
 * @param modifier layout modifier.
 */
@Composable
fun KrtEndOfList(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = KrtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md),
    ) {
        KrtHairlineRule(modifier = Modifier.weight(1f))
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.Gray2,
            textAlign = TextAlign.Center,
        )
        KrtHairlineRule(modifier = Modifier.weight(1f))
    }
}

/**
 * A swipe action revealed behind a list row.
 *
 * Swipes reveal rather than commit: the design system forbids auto-committing past a threshold, and
 * a destructive swipe must be undoable through a 5 second undo toast.
 *
 * @param label action label; uppercased for display.
 * @param iconRes glyph of the action.
 * @param background full-bleed action colour (success green for "read", danger red for "delete").
 * @param modifier layout modifier.
 */
@Composable
fun KrtSwipeAction(
    label: String,
    @DrawableRes iconRes: Int,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(SWIPE_ACTION_WIDTH)
                .background(background)
                .padding(KrtSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KrtIcon(id = iconRes, contentDescription = null, size = 20.dp, tint = KrtPalette.White)
        Text(
            text = label.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.White,
        )
    }
}

/** Width of a revealed swipe action. */
private val SWIPE_ACTION_WIDTH = 96.dp

@Preview(name = "List rows", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RowsPreview() {
    KrtPreviewSurface {
        Column {
            KrtListRow(
                title = "Vertikaler Abbau — Lyria",
                subtitle = "Heute · 21:00 · Geplant",
                leadingIcon = R.drawable.ic_krt_target,
                trailingValue = "in 2 Std.",
                trailingLabel = "12 angemeldet",
            )
            KrtListRow(
                title = "Ausgewählte Zeile",
                subtitle = "3 dp orangener Balken",
                leadingIcon = R.drawable.ic_krt_crate,
                selected = true,
                showChevron = false,
            )
            KrtLoadMore("Mehr laden — 40 von 143", {})
            KrtEndOfList("Ende der Liste")
        }
    }
}
