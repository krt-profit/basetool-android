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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.*

/* ═════════════════════════════ 7 · TABS & SEGMENTS ═════════════════════════════
 * Tab      = white label + 3 dp orange underline when active; horizontally scrollable; the row
 *            scrolls to the active tab on open; deep-linkable via ?tab=.
 * Segment  = a scope switch (Mitglied | Verwaltung, Meine Schiffe | Org-Einheit): UPPERCASE,
 *            filled orange with BLACK text when active, hairline box, 44 dp.
 * A scope switch is NOT a tab: the tab changes what part of one thing you see, the segment
 * changes WHICH thing.
 */

@Composable
fun KrtTabRow(titles: List<String>, counts: List<Int?> , selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, lockedIndices: Set<Int> = emptySet(), onLocked: (Int) -> Unit = {}) {
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { /* scroll the active tab into view on open */ }
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
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
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
                    Spacer(Modifier.width(KrtSpacing.s4))
                    Icon(painterResource(R.drawable.ic_krt_lock), contentDescription = null, tint = KrtPalette.TextMuted, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/* ═════════════════════════════ 8 · PERMISSIONS — THE GATE ═════════════════════════════
 * /api/v1/users/me returns roles + permissions (the access token carries realm_access.roles).
 * The app READS them. An action the caller demonstrably may not perform is:
 *
 *   NOT hidden        — this org hands out roles by hand; a function nobody sees is never asked for
 *   drawn disabled    — alpha .45 PLUS a lock glyph (alpha alone is indistinguishable from loading)
 *   still TAPPABLE    — never enabled = false: what cannot be tapped cannot explain itself
 *   answered in words — the toast names the MISSING ROLE, never "403", never "Keine Berechtigung"
 *
 * Copy: "Dafür brauchst du die Rolle Logistiker." Role names come from ROLES_AND_PERMISSIONS.md
 * so the text and the role someone has to request carry the same name.
 *
 * Two kinds of lock, ONE picture (deliberately): a ROLE lock (known up front) and a ROW lock
 * (own row, or edit rights on this org unit). Both look identical; only the sentence differs.
 */
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

/* ═════════════════════════════ 9 · STATUS ═════════════════════════════
 * Two loudnesses, and they are not interchangeable:
 *   KrtStatusBadge  page-level lifecycle marker (an Einsatz IS planned) — loud, full-width capable
 *   KrtStatusPill   row-level marker in a list — quiet
 * Status enums shout: GEPLANT, AKTIV, ABGESCHLOSSEN, ABGELEHNT.
 */
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

@Composable
fun KrtStatusBadge(text: String, tone: KrtStatusTone, modifier: Modifier = Modifier) {
    val (border, label) = tone.colors()
    Row(
        modifier.fillMaxWidth().background(border.copy(alpha = 0.12f)).border(KrtDimens.hairline, border).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(text.uppercase(), style = MaterialTheme.typography.titleSmall, color = label) }
}

/* ═════════════════════════════ 10 · NUMBERS & MONEY ═════════════════════════════
 * Integers with thousands separators. A buy price renders RED with a minus, a sell price GREEN
 * with a plus — the sign comes from the KIND, never from what somebody typed. A missing value is
 * an em dash in TextMuted, never 0 and never an empty cell.
 */
@Composable
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
        Text(text, style = (if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall).merge(KrtTabularNums), color = color)
        if (unit != null && value != null) Text(unit, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
    }
}

/** 1284 -> "1.284" (German grouping, as the web renders it). */
fun krtFormat(value: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.GERMAN).format(value)

/* ═════════════════════════════ 11 · EMPTY · LOADING · ERROR ═════════════════════════════
 * A list has FOUR states and all four are drawn in the spec. Rules:
 *   loading   skeleton rows in the list shape — never a full-screen spinner over content that
 *             is already there; a spinner appears only after 300 ms
 *   empty     one sentence, in the words the web uses; NO empty frame, no illustration
 *   filtered  a different sentence PLUS a reset action — never the same copy as empty
 *   error     Kap. 14: in-fiction EN canon for HTTP states (403/404/500), plain German for a
 *             local failure, and always ONE way on ("Erneut versuchen" / "Zurück zur Basis")
 * Offline: write actions render disabled with a line saying why — never a queue.
 */
@Composable
fun KrtEmptyState(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(KrtSpacing.s12), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.TextMuted)
        action?.invoke()
    }
}

@Composable
fun KrtSkeletonRow(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(modifier.fillMaxWidth().height(height).background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Gray3))
}

/* ═════════════════════════════ 12 · FAN KIT BAND ═════════════════════════════
 * LEGALLY REQUIRED and INSEPARABLE: three elements that never render, move or disappear
 * separately — the unmodified white "Made By The Community" artwork (36 dp), the Guidelines §2b
 * trademark line, and the Fankit Agreement clause 2(g) notice. Both notices 14 sp #D2D2D2,
 * verbatim ENGLISH in every locale, never folded behind a tap, never shrunk below 14 sp.
 *
 * The two strings differ on purpose (a space before the third ® in §2b, none before 2(g)'s four;
 * "Ltd.." keeps both stops). NEVER harmonise them. Pin both byte-exact in a test, plus an
 * assertion that they still differ.
 *
 * Placement: login screen and Einstellungen, above the version footer. Both screens SCROLL.
 */
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
