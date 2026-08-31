/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.PillShape

/** Alpha of the faint tint fill behind a toned chip. */
private const val CHIP_TINT_ALPHA = 0.12f

/** How far a disabled filter chip dims. Dimmed rather than hidden, so the row does not reflow. */
private const val DISABLED_CHIP_ALPHA = 0.45f

/** Edge length of the square status dot. Status dots are squares, only presence dots are round. */
private val STATUS_DOT = 8.dp

/** Width of the leading edge bar on a page-level status badge. */
private val STATUS_BADGE_EDGE = 3.dp

/** Period of the presence pulse. */
private const val PRESENCE_PULSE_MS = 2000

/**
 * How an org unit relates to the user's current context.
 *
 * The badge is the only pill-shaped element in the system, so its colour carries meaning: the
 * user's own unit is orange, a foreign unit is the cross-org yellow, and the neutral variant is for
 * aggregate scopes such as "Alle Einheiten".
 */
enum class KrtOrgBadgeKind {
    /** The user's own org unit (Bereich Profit). */
    Own,

    /** A Spezialkommando. */
    SpecialCommand,

    /** A unit outside the user's own org — rendered in the cross-org highlight. */
    Foreign,

    /** No specific unit: aggregate or "all units" scopes. */
    Muted,
}

/**
 * The org-unit badge — the one pill in the design system.
 *
 * Sits in the top bar as the active-context chip; tapping it opens the org switcher sheet.
 *
 * @param text unit name as displayed.
 * @param modifier layout modifier.
 * @param kind relationship to the current context, see [KrtOrgBadgeKind].
 * @param onClick optional tap handler; supply it when the badge opens the org switcher.
 */
@Composable
fun KrtOrgBadge(
    text: String,
    modifier: Modifier = Modifier,
    kind: KrtOrgBadgeKind = KrtOrgBadgeKind.Own,
    onClick: (() -> Unit)? = null,
) {
    val color =
        when (kind) {
            KrtOrgBadgeKind.Own, KrtOrgBadgeKind.SpecialCommand -> MaterialTheme.colorScheme.primary
            KrtOrgBadgeKind.Foreign -> KrtTheme.colors.crossOrg
            KrtOrgBadgeKind.Muted -> KrtPalette.TextMuted
        }
    Box(
        modifier =
            modifier
                .clip(PillShape)
                .border(KrtSpacing.hairline, color, PillShape)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
                )
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
    ) {
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * Semantic tone of a chip.
 *
 * The tone is chosen by meaning, never by decoration — an orange chip claims brand/action
 * relevance, a red one claims a problem.
 */
enum class KrtChipTone {
    /** Brand/action relevance, e.g. the record's own type. */
    Primary,

    /** A completed or positive fact ("Ausgezahlt"). */
    Success,

    /** A problem ("Überbucht"). */
    Danger,

    /** Something needing attention ("HVU"). */
    Warning,

    /** Neutral information ("Einsatz"). */
    Info,

    /** Quiet counts and secondary facts ("340 frei"). */
    Muted,

    /** A key/value readout where the value must stay bright. */
    Data,
}

/**
 * A squared data chip — the counterpart to the rounded org badge.
 *
 * Chips label a record; they are not buttons and never carry a tap handler. The toned variants take
 * their hue for border and text with a faint tint fill behind, which keeps them legible at 11 sp
 * without becoming loud.
 *
 * @param text chip label; uppercased for display except in the [KrtChipTone.Data] tone, where the
 *   value's own formatting must survive.
 * @param modifier layout modifier.
 * @param tone semantic tone, see [KrtChipTone].
 */
@Composable
fun KrtChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: KrtChipTone = KrtChipTone.Muted,
) {
    val hue =
        when (tone) {
            KrtChipTone.Primary -> MaterialTheme.colorScheme.primary
            KrtChipTone.Success -> KrtTheme.colors.successText
            KrtChipTone.Danger -> KrtTheme.colors.dangerText
            KrtChipTone.Warning -> KrtTheme.colors.warning
            KrtChipTone.Info -> KrtTheme.colors.infoText
            KrtChipTone.Muted -> KrtPalette.TextMuted
            KrtChipTone.Data -> KrtPalette.White
        }
    val fill =
        when (tone) {
            KrtChipTone.Muted, KrtChipTone.Data -> KrtPalette.SurfaceInput
            else -> hue.copy(alpha = CHIP_TINT_ALPHA)
        }
    val borderColor = if (tone == KrtChipTone.Muted || tone == KrtChipTone.Data) KrtPalette.Gray3 else hue

    Box(
        modifier =
            modifier
                .background(fill)
                .border(KrtSpacing.hairline, borderColor)
                .padding(horizontal = KrtSpacing.s8, vertical = KrtSpacing.s4),
    ) {
        Text(
            text = if (tone == KrtChipTone.Data) text else text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = hue,
        )
    }
}

/**
 * A tappable filter chip — the interactive counterpart to the deliberately inert [KrtChip].
 *
 * [KrtChip] labels a record and documents that it "never carries a tap handler"; a filter row needs
 * the opposite, so it gets its own component rather than an optional handler bolted onto that
 * contract. Same squared geometry, so a chip row still reads as one family.
 *
 * The selected state is carried by **border and text colour**, not by a filled background: a filled
 * orange chip would claim the visual weight of a primary action, and a row of filters is not a row
 * of calls to action.
 *
 * A chip that carries a **value** rather than a yes/no can also be cleared: pass [onClear] and the
 * chip grows an ✕ that removes the filter, while a tap on the label still opens whatever set it.
 * Design ch. 02 §11 d asks for exactly that for the date range („Zeitraum ✕") so the picker itself
 * does not need a reset button.
 *
 * @param text the filter's label; uppercased for display like every other chip.
 * @param selected whether this filter is currently applied.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the chip responds; a disabled chip dims rather than disappearing, so the
 *   row does not reflow while a load is in flight.
 * @param onClear removes the filter this chip carries, or `null` for a chip that only toggles.
 * @param clearLabel what a screen reader calls the ✕; required whenever [onClear] is given.
 */
@Composable
fun KrtFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
    clearLabel: String? = null,
) {
    val hue = if (selected) MaterialTheme.colorScheme.primary else KrtPalette.TextMuted
    val borderColor = if (selected) hue else KrtPalette.Gray3
    Row(
        modifier =
            modifier
                .background(if (selected) hue.copy(alpha = CHIP_TINT_ALPHA) else KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, borderColor)
                .alpha(if (enabled) 1f else DISABLED_CHIP_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.krtUppercase(),
            modifier =
                Modifier
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .padding(horizontal = KrtSpacing.s8, vertical = KrtSpacing.s4),
            style = MaterialTheme.typography.labelMedium,
            color = hue,
            // A chip's label is one word or one short phrase and is atomic: it either fits or the
            // layout around it is wrong. Without this a squeezed chip breaks „Abgebrochen" into
            // „ABGEB ROCHE N", which is unreadable and looks like corruption rather than a layout
            // that ran out of room. Callers lay chips out in a FlowRow so this never has to clip.
            maxLines = 1,
            softWrap = false,
        )
        if (onClear != null) {
            Box(
                modifier =
                    Modifier
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClear)
                        .padding(end = KrtSpacing.s8, top = KrtSpacing.s4, bottom = KrtSpacing.s4),
            ) {
                KrtIcon(
                    id = R.drawable.ic_krt_close,
                    contentDescription = clearLabel,
                    size = CHIP_CLEAR_GLYPH,
                    tint = hue,
                )
            }
        }
    }
}

/** The ✕ of a clearable filter chip, sized to the chip's own label rather than to a row icon. */
private val CHIP_CLEAR_GLYPH = 12.dp

/**
 * A **choice** chip: hairline while it is on offer, filled orange with black text once it is taken.
 *
 * Its own component rather than a fill option on [KrtFilterChip], because design ch. 18 §3 (E6)
 * ratified the two as deliberately different and they are: a filter narrows a list and must not
 * claim the weight of a primary action, while a choice **is** the value — „Funktion an Bord", a
 * crew role. A chip that looks like a value and behaves like a switch is the worse mistake of the
 * two, so the difference is drawn rather than configured.
 *
 * Filled orange carries black text, which is the system's rule for every filled orange surface.
 *
 * @param text the option's name; uppercased for display like every other chip.
 * @param selected whether this option is the chosen one.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the chip responds; a disabled chip dims rather than disappearing.
 * @param suffix what stands behind the name in the muted tint — the holder of a role already
 *   taken, which is what makes „vergeben" readable instead of merely dim.
 */
@Composable
fun KrtChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String? = null,
) {
    val hue = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            modifier
                .background(if (selected) hue else KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, if (selected) hue else KrtPalette.Gray3)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = KrtSpacing.s8, vertical = KrtSpacing.s4)
                .alpha(if (enabled) 1f else DISABLED_CHIP_ALPHA),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) KrtPalette.Black else KrtPalette.Gray1,
            maxLines = 1,
            softWrap = false,
        )
        suffix?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) KrtPalette.Black else KrtPalette.TextMuted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * A department tag in its frozen Bereichsfarbe.
 *
 * Only ever used where that department actually applies. The colour values are fixed by the
 * corporate design manual and must not be altered or reused decoratively.
 *
 * @param text department name.
 * @param color the department's frozen hue, taken from `KrtTheme.colors`.
 * @param modifier layout modifier.
 */
@Composable
fun KrtDepartmentTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, color)
                .padding(horizontal = KrtSpacing.s8, vertical = KrtSpacing.s4),
    ) {
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * Lifecycle state of a record (mission, order, refinery run).
 *
 * Each state maps to a fixed hue so a status is recognisable before it is read.
 */
enum class KrtStatusTone {
    /** Scheduled but not started. */
    Planned,

    /** Currently running. */
    Active,

    /** Briefing in progress — the attention state. */
    Briefing,

    /** Finished. */
    Completed,

    /** Called off. */
    Cancelled,
}

/**
 * Resolves the display colour of a status.
 *
 * Always returns the *text* tint of the semantic hue, because a status label is small text on a dark
 * ground and the canonical fills would fail WCAG AA there.
 *
 * @return the colour to use for both dot and label.
 */
@Composable
private fun KrtStatusTone.color(): Color =
    when (this) {
        KrtStatusTone.Planned -> KrtTheme.colors.infoText
        KrtStatusTone.Active -> KrtTheme.colors.successText
        KrtStatusTone.Briefing -> KrtTheme.colors.warning
        KrtStatusTone.Completed -> KrtPalette.TextMuted
        KrtStatusTone.Cancelled -> KrtTheme.colors.dangerText
    }

/**
 * The hue a status is drawn in, for callers outside this file.
 *
 * A **surface** can carry a status as well as a chip: design ch. 06 (F2) draws the lifecycle band as
 * a card washed in its own status tone, so the state, the countdown and the action read as one thing
 * rather than as a chip with two strangers beside it.
 *
 * @return the tone's colour — always the *text* tint, which is what stays legible on the dark
 *   ground and what a border drawn from it has to match.
 */
@Composable
fun KrtStatusTone.krtColor(): Color = color()

/**
 * The row-level status indicator: a square 8 dp dot plus an uppercase label.
 *
 * Deliberately quiet — inside a list the status must not compete with the record's name.
 *
 * @param text status label; uppercased for display.
 * @param tone the lifecycle state.
 * @param modifier layout modifier.
 */
@Composable
fun KrtStatusPill(
    text: String,
    tone: KrtStatusTone,
    modifier: Modifier = Modifier,
) {
    val color = tone.color()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(STATUS_DOT).background(color))
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * The page-level status badge — the louder sibling of [KrtStatusPill].
 *
 * Carries a 3 dp leading edge and a tinted fill, and belongs at the top of a detail screen where a
 * single status describes the whole record.
 *
 * @param text status label; uppercased for display.
 * @param tone the lifecycle state.
 * @param modifier layout modifier.
 */
@Composable
fun KrtStatusBadge(
    text: String,
    tone: KrtStatusTone,
    modifier: Modifier = Modifier,
) {
    val color = tone.color()
    Row(
        modifier =
            modifier
                .background(color.copy(alpha = CHIP_TINT_ALPHA))
                .defaultMinSize(minHeight = KrtSpacing.s24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = STATUS_BADGE_EDGE, height = KrtSpacing.s24)
                    .background(color),
        )
        Box(
            modifier =
                Modifier
                    .padding(start = KrtSpacing.s8)
                    .size(10.dp)
                    .background(color),
        )
        Text(
            text = text.krtUppercase(),
            modifier = Modifier.padding(horizontal = KrtSpacing.s8),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * The live-presence indicator: a pulsing orange dot plus the names of the peers editing right now.
 *
 * Presence is ambient information and must never block input or steal focus — it only tells the
 * user that someone else is in the same record.
 *
 * @param text the presence sentence, e.g. "Wird gerade bearbeitet von Rhea, Dorn".
 * @param modifier layout modifier.
 * @param count optional peer count rendered before the text.
 */
@Composable
fun KrtPresenceIndicator(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    // Unlike the loading spinner, this pulse carries no information the text does not already
    // give — it is the one purely decorative animation in the app, so reduced motion stops it
    // outright rather than shortening it. A zero-duration infinite repeat would spin the
    // animation clock forever without ever settling, so the transition is skipped entirely.
    val reducedMotion = KrtTheme.motionMs == 0
    val pulse by
        if (reducedMotion) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "krt-presence").animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(PRESENCE_PULSE_MS),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "krt-presence-alpha",
            )
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(STATUS_DOT)
                    .alpha(pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The "updates available" pill shown when live data changed underneath an open editor.
 *
 * The app never yanks state out from under an active edit: the peer's change is signalled here and
 * applied only when the user taps.
 *
 * @param text the invitation, e.g. "Aktualisierung verfügbar — Antippen zum Laden".
 * @param onClick invoked when the user accepts the refresh.
 * @param modifier layout modifier.
 */
@Composable
fun KrtUpdateAvailablePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = KrtSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(name = "Status and chips", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StatusPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                KrtOrgBadge("Bereich Profit")
                KrtOrgBadge("TITAN", kind = KrtOrgBadgeKind.Foreign)
                KrtOrgBadge("Alle Einheiten", kind = KrtOrgBadgeKind.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
                KrtChip("Auftrag", tone = KrtChipTone.Primary)
                KrtChip("Ausgezahlt", tone = KrtChipTone.Success)
                KrtChip("Überbucht", tone = KrtChipTone.Danger)
                KrtChip("340 frei", tone = KrtChipTone.Muted)
            }
            KrtDepartmentTag("Profit", KrtTheme.colors.deptProfit)
            KrtStatusPill("Geplant", KrtStatusTone.Planned)
            KrtStatusPill("Aktiv", KrtStatusTone.Active)
            KrtStatusBadge("Aktiv", KrtStatusTone.Active)
            KrtPresenceIndicator("Wird gerade bearbeitet von Rhea, Dorn", count = 2)
            KrtUpdateAvailablePill("Aktualisierung verfügbar", {})
        }
    }
}
