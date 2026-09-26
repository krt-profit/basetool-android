/*
 * Basetool Android — DAS KARTELL / Bereich Profit design system.
 * GENERATED FROM THE DESIGN SPEC (docs/design/android, chapters 00–17).
 *
 * Every value here is decided. Do not tune, round or "improve" one. If something you need is
 * missing, it is a spec gap — raise it, do not invent it.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.*

@Composable
fun KrtTabRow(titles: List<String>, counts: List<Int?> , selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, lockedIndices: Set<Int> = emptySet(), onLocked: (Int) -> Unit = {}) {
    val scroll = rememberScrollState()
    LaunchedEffect(selected) {  }
    Row(modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 8.dp)) {
        titles.forEachIndexed { i, title ->
            val locked = i in lockedIndices
            val active = i == selected
            Column(
                Modifier.heightIn(min = KrtDimens.tabHeight).padding(horizontal = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.weight(1f).clickableTab { if (locked) onLocked(i) else onSelect(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                ) {
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) KrtPalette.White else KrtPalette.TextMuted,
                        modifier = if (locked) Modifier.alpha(0.45f) else Modifier,
                    )
                    counts.getOrNull(i)?.let { KrtChip(it.toString(), KrtChipTone.MUTED) }
                    if (locked) Icon(painterResource(R.drawable.ic_krt_lock), contentDescription = null, tint = KrtPalette.TextMuted, modifier = Modifier.size(11.dp))
                }
                Box(Modifier.fillMaxWidth().height(KrtDimens.activeBar).background(if (active) KrtPalette.Primary else Color.Transparent))
            }
        }
    }
}

private fun Modifier.clickableTab(onClick: () -> Unit) = this.then(androidx.compose.foundation.clickable(onClick = onClick))

@Composable
fun KrtSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, lockedIndices: Set<Int> = emptySet(), onLocked: (Int) -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(KrtDimens.touchTarget).border(KrtDimens.hairline, KrtPalette.Gray3)) {
        options.forEachIndexed { i, option ->
            val active = i == selected
            val locked = i in lockedIndices
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (active) KrtPalette.Primary else Color.Transparent)
                    .clickableTab { if (locked) onLocked(i) else onSelect(i) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    option.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) KrtPalette.Black else KrtPalette.TextMuted,
                    modifier = if (locked) Modifier.alpha(0.45f) else Modifier,
                )
                if (locked) {
                    Spacer(Modifier.width(KrtSpacing.xs))
                    Icon(painterResource(R.drawable.ic_krt_lock), contentDescription = null, tint = KrtPalette.TextMuted, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

data class KrtGate(
    val allowed: Boolean,
    /** "Dafür brauchst du die Rolle Logistiker." — names the role, not the error. */
    val reason: String,
    /** One quieter line: where to ask, or which rule applies. */
    val detail: String? = null,
)

class KrtDenialState {
    var current by mutableStateOf<KrtGate?>(null)
        private set

    /** Singleton: raising again resets the 4 s timer instead of stacking a second toast. */
    fun raise(gate: KrtGate) { current = null; current = gate }
    fun clear() { current = null }
}

@Composable
fun rememberKrtDenialState(): KrtDenialState = remember { KrtDenialState() }

/**
 * Wraps an action in its gate. Returns the modifier to draw with and the click to attach.
 * The click ALWAYS fires — either the action, or the refusal.
 */
@Composable
fun rememberKrtGated(gate: KrtGate, onAllowed: () -> Unit, denials: KrtDenialState): Pair<Modifier, () -> Unit> {
    val modifier = if (gate.allowed) Modifier else Modifier.alpha(0.45f)
    val click: () -> Unit = { if (gate.allowed) onAllowed() else denials.raise(gate) }
    return modifier to click
}

/** The lock glyph that MUST accompany the alpha. Full opacity, neutral grey — never dimmed with it. */
@Composable
fun KrtLockGlyph(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 13.dp) {
    Icon(painterResource(R.drawable.ic_krt_lock), contentDescription = null, tint = KrtPalette.TextMuted, modifier = modifier.size(size))
}

enum class KrtStatusTone { PLANNED, ACTIVE, BRIEFING, COMPLETED, CANCELLED, OPEN, IN_PROGRESS, REJECTED }

private fun KrtStatusTone.colors(): Pair<Color, Color> = when (this) {
    KrtStatusTone.PLANNED, KrtStatusTone.OPEN -> KrtPalette.Info to KrtPalette.InfoText
    KrtStatusTone.ACTIVE, KrtStatusTone.IN_PROGRESS -> KrtPalette.Primary to KrtPalette.Primary
    KrtStatusTone.BRIEFING -> KrtPalette.Warning to KrtPalette.WarningText
    KrtStatusTone.COMPLETED -> KrtPalette.Success to KrtPalette.SuccessText
    KrtStatusTone.CANCELLED, KrtStatusTone.REJECTED -> KrtPalette.Danger to KrtPalette.DangerText
}

@Composable
fun KrtStatusPill(text: String, tone: KrtStatusTone, modifier: Modifier = Modifier) {
    val (border, label) = tone.colors()
    Box(modifier.background(border.copy(alpha = 0.12f)).border(KrtDimens.hairline, border).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = label, maxLines = 1)
    }
}

/**
 * The page-level lifecycle marker.
 *
 * Draws a 10 % tint fill, a hairline border, a 3 dp leading edge and a 10 dp square dot in the
 * state's text tint, with the label in white.
 */
@Composable
fun KrtStatusBadge(text: String, tone: KrtStatusTone, modifier: Modifier = Modifier) {
    val (_, tint) = tone.colors()
    Row(
        modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f))
            .border(KrtDimens.hairline, KrtPalette.Gray3)
            .drawBehind { drawRect(tint, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .padding(start = 14.dp + 3.dp, end = 14.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(10.dp).background(tint))
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall, color = KrtPalette.White)
    }
}

@Composable
/** @param big the card rung of [KrtFigure]; a screen's ONE hero number uses KrtFigure.total directly. */
fun KrtAmount(value: Long?, modifier: Modifier = Modifier, unit: String? = null, signed: Boolean = false, positive: Boolean = true, big: Boolean = false) {
    val text = when {
        value == null -> "—"
        signed -> (if (positive) "+" else "−") + krtFormat(value)
        else -> krtFormat(value)
    }
    val color = when {
        value == null -> KrtPalette.TextMuted
        signed && positive -> KrtPalette.SuccessText
        signed -> KrtPalette.DangerText
        else -> KrtPalette.White
    }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = if (big) KrtFigure.card else MaterialTheme.typography.titleSmall.merge(KrtTabularNums), color = color)
        if (unit != null && value != null) Text(unit, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
    }
}

/** 1284 -> "1.284" (German grouping, as the web renders it). */
fun krtFormat(value: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.GERMAN).format(value)

@Composable
fun KrtEmptyState(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(KrtSpacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.TextMuted)
        action?.invoke()
    }
}

@Composable
fun KrtSkeletonRow(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(modifier.fillMaxWidth().height(height).background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Gray3))
}

@Composable
fun KrtFanKitBand(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 12.dp).padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(R.drawable.made_by_the_community),
            contentDescription = "Star Citizen — Made by the Community",
            modifier = Modifier.size(36.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResourceCompat(R.string.fankit_trademark_2b), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), color = KrtPalette.Gray1)
            Text(stringResourceCompat(R.string.fankit_notice_2g), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), color = KrtPalette.Gray1)
        }
    }
}

@Composable private fun Image(painter: androidx.compose.ui.graphics.painter.Painter, contentDescription: String, modifier: Modifier) =
    androidx.compose.foundation.Image(painter, contentDescription, modifier)

@Composable private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
