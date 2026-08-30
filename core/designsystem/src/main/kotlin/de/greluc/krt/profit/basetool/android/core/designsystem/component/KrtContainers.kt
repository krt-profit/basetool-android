/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtCornerBrackets
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Opacity of the HUD box fill — the container is deliberately translucent against the canvas. */
private const val HUD_FILL_ALPHA = 0.5f

/** Width of the orange accent bar on an accent card and a panel header. */
private val ACCENT_BAR = 3.dp

/** Width of the orange leading bar of a panel header. */
private val PANEL_BAR = 4.dp

/**
 * The signature HUD container — hairline, translucent fill and two orange corner brackets.
 *
 * This is the loudest container in the system, so it is rationed: at most one or two per screen,
 * reserved for the hero block (for example the next mission on the dashboard). Everything else uses
 * [KrtCard].
 *
 * @param modifier layout modifier.
 * @param contentPadding inner padding around [content].
 * @param content the container content.
 */
@Composable
fun KrtHudBox(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(KrtSpacing.s16),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .background(KrtPalette.Gray4.copy(alpha = HUD_FILL_ALPHA))
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .krtCornerBrackets()
                .padding(contentPadding),
        content = content,
    )
}

/**
 * A card whose left edge carries the accent rail.
 *
 * The design uses this for a block that speaks rather than lists — the dashboard's greeting, an
 * order's note. It was hand-built at each call site before, which is how the same four-line `Row` +
 * filled `Box` ended up in two files with two sets of paddings; one component keeps the rail's width
 * and colour in one place.
 *
 * The rail is `IntrinsicSize.Min` tall so it matches the content rather than a guess, which is the
 * whole reason it cannot be a border.
 *
 * @param modifier layout modifier.
 * @param contentPadding padding inside the card, to the right of the rail.
 * @param content what the block says.
 */
@Composable
fun KrtRailCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(KrtSpacing.s16),
    content: @Composable ColumnScope.() -> Unit,
) {
    KrtCard(modifier = modifier, variant = KrtCardVariant.Flush) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier =
                    Modifier
                        .width(KrtSpacing.s4)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * How a card sits in the visual hierarchy.
 *
 * The variants differ only in fill and accent, never in shape: every card is square.
 */
enum class KrtCardVariant {
    /** The default list/detail surface. */
    Default,

    /** Nested inside another card — a half-step darker so the nesting is legible. */
    Inset,

    /** Carries a 3 dp orange top bar; reserved for totals and summary cards. */
    Accent,

    /** No inner padding, for cards that wrap a table or a full-bleed list. */
    Flush,
}

/**
 * The default container of the app.
 *
 * @param modifier layout modifier.
 * @param variant hierarchy variant, see [KrtCardVariant].
 * @param onClick optional tap handler; when set the whole card becomes the touch target.
 * @param content the card content.
 */
@Composable
fun KrtCard(
    modifier: Modifier = Modifier,
    variant: KrtCardVariant = KrtCardVariant.Default,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill =
        when (variant) {
            KrtCardVariant.Inset -> KrtPalette.SurfaceInput
            else -> MaterialTheme.colorScheme.surface
        }
    val padding = if (variant == KrtCardVariant.Flush) 0.dp else KrtSpacing.s16

    Column(
        modifier =
            modifier
                .background(fill)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (variant == KrtCardVariant.Accent) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ACCENT_BAR)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/**
 * A collapsible section header with an orange leading bar, an optional count and a chevron.
 *
 * Used to fold long detail screens (Finanzen, Teilnehmer, …) into scannable sections. The header is
 * the toggle, so the whole row is the touch target and reports itself as a button to TalkBack.
 *
 * @param title section title; uppercased for display.
 * @param expanded current state; drives the chevron direction.
 * @param onToggle invoked when the header is tapped.
 * @param modifier layout modifier.
 * @param count optional item count rendered next to the title.
 * @param stateChip what the head says about itself while it is folded.
 * @param busy whether **this** section is writing. The spinner takes the chevron's place rather
 *   than appearing as a screen overlay or a global bar: a section that is saving has to say so at
 *   itself, or a Zeitplan write looks like it froze the Ziele too (design ch. 18 §3, E4).
 */
@Composable
fun KrtPanelHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    stateChip: (@Composable () -> Unit)? = null,
    busy: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(PANEL_BAR)
                    .height(KrtSpacing.touchTarget)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = title.krtUppercase(),
            modifier = Modifier.padding(horizontal = KrtSpacing.s12),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.Gray1,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(modifier = Modifier.weight(1f))
        // The state chip is what makes the fold honest: a closed section still says whether it is
        // started, saved, changed or in conflict, so nothing needed for a decision is hidden
        // (design ch. 02 §10, ch. 06 artboard 7).
        stateChip?.invoke()
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = KrtSpacing.s16).size(PANEL_SPINNER),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = KrtSpacing.hairline * 2,
            )
        } else {
            KrtIcon(
                id = if (expanded) R.drawable.ic_krt_chevron_up else R.drawable.ic_krt_chevron_down,
                contentDescription = null,
                modifier = Modifier.padding(horizontal = KrtSpacing.s16),
                size = 16.dp,
                tint = KrtPalette.TextMuted,
            )
        }
    }
}

/** The spinner that replaces a saving section's chevron — the chevron's own size. */
private val PANEL_SPINNER = 16.dp

/**
 * One label/value pair of a key-value list.
 *
 * The pairing encodes the grey discipline of the system: the key is a muted uppercase micro-label,
 * the value is the bright element the eye should land on.
 *
 * @param label the key; uppercased for display.
 * @param value the value.
 * @param modifier layout modifier.
 * @param valueColor value colour; white by default, semantic tints for signed amounts.
 */
@Composable
fun KrtKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = KrtPalette.White,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label.krtUppercase(),
            modifier = Modifier.width(KEY_COLUMN_WIDTH),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

/**
 * Width of the key column in a key-value list.
 *
 * Wide enough for the German compounds the app uses ("Verantwortliche Einheit") at the default font
 * scale, and it wraps rather than truncates beyond that.
 */
private val KEY_COLUMN_WIDTH = 120.dp

@Preview(name = "Containers", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ContainersPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
            KrtHudBox {
                Text(
                    text = "Nächster Einsatz",
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                )
                Text(
                    text = "1 dp Haarlinie · translucent · 10 dp Brackets",
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Gray1,
                )
            }
            KrtCard { Text("Standardkarte", color = KrtPalette.Gray1) }
            KrtCard(variant = KrtCardVariant.Inset) { Text("Inset", color = KrtPalette.Gray1) }
            KrtCard(variant = KrtCardVariant.Accent) { Text("Summenkarte", color = KrtPalette.Gray1) }
            KrtPanelHeader(title = "Finanzen", expanded = false, onToggle = {}, count = 4)
            KrtCard {
                KrtKeyValueRow(label = "Treffpunkt", value = "ARC-L1 Wide Forest Station")
                KrtKeyValueRow(label = "Frequenz", value = "148.500")
            }
        }
    }
}
