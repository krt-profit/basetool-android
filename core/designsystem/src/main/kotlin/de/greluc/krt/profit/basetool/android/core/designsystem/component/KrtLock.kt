/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtBloom
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtCornerBrackets
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/**
 * Opacity of a control the caller may not use (design ch. 09, artboard 14: „Alpha 45 %").
 *
 * The same figure the button ladder uses for `enabled = false`, because to a member this *is* the
 * disabled state. The difference is only that this one answers when tapped.
 */
private const val LOCKED_ALPHA = 0.45f

/** Edge length of the badge that sits in an icon button's corner. */
private val BADGE_SIZE = 14.dp

/** Edge length of the lock inside that badge. */
private val BADGE_GLYPH = 9.dp

/** Lock size in a toast and inline in a call-to-action. */
private val LOCK_GLYPH = 16.dp

/** Gap between the toast's lock and its copy. */
private val TOAST_GAP = 10.dp

/** Maximum width of the refusal toast. */
private val TOAST_MAX_WIDTH = 396.dp

/** Bloom radius behind the refusal toast. */
private val TOAST_BLOOM = KrtSpacing.glowOverlay

/**
 * How strong that bloom is — the artboard measures `rgba(255, 210, 63, .25) 0 0 20px`.
 *
 * Passing the warning tint at full strength produces a halo several times this wide on device; the
 * bloom is meant to lift the bar off the content behind it, not to announce itself.
 */
private const val TOAST_BLOOM_ALPHA = 0.25f

/** Gap between the refusal's headline and the line that says who hands the grant out. */
private val TOAST_DETAIL_GAP = 2.dp

/** Size of that second line — the design system's `--fs-2xs` rung. */
private val DETAIL_SIZE = 11.sp

/** Its line height, the 1.4 ratio the whole scale uses. */
private val DETAIL_LINE = 16.sp

/**
 * Draws a control as locked: dimmed, and announced as locked to TalkBack.
 *
 * Alpha alone is deliberately **not** enough — at 45 % a control is indistinguishable from one
 * that is merely loading, so every locked control also carries [KrtLockBadge] or an inline
 * [KrtInlineLock] (design ch. 09, artboard 14: „Alpha allein ist von einem Ladezustand nicht zu
 * unterscheiden"). This modifier covers the half that is not a glyph.
 *
 * It does not touch the click handler. A locked control keeps its tap target so it can explain
 * itself; `enabled = false` is the thing this pattern exists to avoid.
 *
 * @param locked whether the caller may act — `false` leaves the control untouched.
 * @param stateLabel what TalkBack should announce instead of the plain label, typically the same
 *   sentence the refusal toast shows.
 */
fun Modifier.krtLocked(
    locked: Boolean,
    stateLabel: String,
): Modifier =
    if (!locked) {
        this
    } else {
        this.alpha(LOCKED_ALPHA).semantics { stateDescription = stateLabel }
    }

/**
 * The lock that marks an icon button as locked — a 14 dp badge for the button's corner.
 *
 * Drawn **fully opaque on an opaque fill**, never dimmed along with the icon it sits on: the badge
 * is what separates "you may not" from "this is still loading", so fading it would erase the one
 * signal that carries the meaning (design ch. 09, artboard 14).
 *
 * Place it with [Alignment.BottomEnd] in the [Box] that holds the icon button.
 *
 * @param modifier placement within the icon button's box.
 */
@Composable
fun KrtLockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(BADGE_SIZE).background(KrtPalette.Gray4),
        contentAlignment = Alignment.Center,
    ) {
        KrtIcon(
            id = R.drawable.ic_krt_lock,
            contentDescription = null,
            size = BADGE_GLYPH,
            tint = KrtPalette.TextMuted,
        )
    }
}

/**
 * The lock that precedes the label of a locked call-to-action.
 *
 * A full-width CTA has no corner to hang a badge on, so the lock moves inline ahead of the text
 * (design ch. 09, artboard 13). Neutral grey, not danger red — nothing has gone wrong.
 *
 * @param modifier spacing against the label that follows.
 */
@Composable
fun KrtInlineLock(modifier: Modifier = Modifier) {
    KrtIcon(
        id = R.drawable.ic_krt_lock,
        contentDescription = null,
        modifier = modifier,
        size = LOCK_GLYPH,
        tint = KrtPalette.TextMuted,
    )
}

/**
 * The one carrier for "you may not do that" — a bracket-framed bar at the foot of the screen.
 *
 * Warning-tinted rather than danger-tinted on purpose: no error has occurred, the caller simply
 * lacks a grant (design ch. 09, artboard 14: „Warnton #FFD23F mit Schloss — kein Danger, es ist
 * kein Fehler passiert").
 *
 * The design fixes this as a **singleton**: one refusal is visible at a time and a second tap
 * restarts its clock instead of stacking a second bar. That behaviour belongs to the caller's
 * state holder, not to this composable — see the app's `rememberDenial`.
 *
 * @param title the missing grant, named as a sentence — „Dafür brauchst du die Rolle Logistiker."
 *   Never a status code and never „Keine Berechtigung": the point is to name what to ask for.
 * @param detail who hands that grant out, or the rule behind a row lock.
 * @param modifier placement, typically bottom-centre above the navigation bar.
 */
@Composable
fun KrtLockToast(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .widthIn(max = TOAST_MAX_WIDTH)
                .fillMaxWidth()
                .krtBloom(KrtTheme.colors.warning.copy(alpha = TOAST_BLOOM_ALPHA), TOAST_BLOOM)
                .background(KrtPalette.Gray4)
                .border(KrtSpacing.hairline, KrtTheme.colors.warning)
                .krtCornerBrackets(color = KrtTheme.colors.warning)
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12),
    ) {
        KrtIcon(
            id = R.drawable.ic_krt_lock,
            contentDescription = null,
            size = LOCK_GLYPH,
            tint = KrtTheme.colors.warning,
        )
        Column(modifier = Modifier.padding(start = TOAST_GAP)) {
            // Sentence case, so the tracking `labelLarge` carries for uppercase labels is dropped —
            // the artboard measures this line at `letter-spacing: normal` (design ch. 09, 12).
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = TextUnit.Unspecified),
                color = KrtPalette.White,
            )
            // `--fs-2xs` Light: one rung below `bodySmall`, which the scale only carries in Bold.
            Text(
                text = detail,
                modifier = Modifier.padding(top = TOAST_DETAIL_GAP),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = DETAIL_SIZE, lineHeight = DETAIL_LINE),
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/** The refusal as the design draws it: lock, the grant to ask for, and who hands it out. */
@Preview
@Composable
private fun KrtLockToastPreview() {
    KrtPreviewSurface {
        KrtLockToast(
            title = "Dafür brauchst du die Rolle Logistiker.",
            detail = "Vergeben je Staffel durch die Administration.",
        )
    }
}
