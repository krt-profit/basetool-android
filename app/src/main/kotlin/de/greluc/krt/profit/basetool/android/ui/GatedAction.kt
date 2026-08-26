/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtLocked

/**
 * How long a refusal stays on screen, in milliseconds (design ch. 09, artboard 14: „4 s").
 */
const val DENIAL_TOAST_MS = 4_000L

/**
 * A control the caller may not use: drawn as locked, still able to explain itself.
 *
 * `enabled = false` is what this deliberately avoids. A Compose control with that flag receives no
 * tap, and a control that cannot be tapped cannot say why it is dim — which leaves a grey button
 * that tells a member nothing, the worst of both options. So the control keeps a live tap target,
 * performs no write, and names the missing grant (ADR-0011, `REQ-APP-AUTH-013`).
 *
 * Hiding it was the alternative and was rejected: this organisation grants roles by hand, and a
 * feature nobody can see is a feature nobody asks to be given.
 *
 * The design draws two kinds of lock with the **same picture** and different copy (ch. 09, artboard
 * 14): a *role* lock, knowable before the tap („Dafür brauchst du die Rolle Logistiker."), and a
 * *row* lock on someone else's entry („Nur deine eigene Zeile …"). Both are this type; only
 * [reason] and [detail] differ.
 *
 * @property allowed whether the action may actually run.
 * @property reason what to say when it may not — the **grant's name** as a sentence, never a status
 *   code and never „Keine Berechtigung": a member has to learn what to ask for.
 * @property detail who hands that grant out, or the rule behind a row lock.
 */
data class Gate(
    val allowed: Boolean,
    val reason: String,
    val detail: String,
)

/**
 * One refusal, and the tap that raised it.
 *
 * @property title the missing grant.
 * @property detail who hands it out.
 * @property serial which tap this is — it changes on every raise, including a repeat of the same
 *   refusal, so the dismissal timer restarts rather than letting the first tap's clock run out
 *   under the second („erneuter Tipp setzt den Timer zurück").
 */
data class Denial(
    val title: String,
    val detail: String,
    val serial: Int,
)

/**
 * Holds the refusal a screen is currently showing.
 *
 * Deliberately a single slot rather than a queue: the design makes the lock toast a **singleton**,
 * so a second refusal replaces the first instead of stacking behind it (design ch. 09, artboard 14:
 * „Singleton; … nichts stapelt").
 */
@Stable
class DenialState {
    /** The refusal on screen, or `null` when none is. */
    var current: Denial? by mutableStateOf(null)
        private set

    private var taps = 0

    /**
     * Shows [gate]'s refusal, restarting the dismissal clock even if it is already on screen.
     *
     * @param gate the gate that refused.
     */
    fun raise(gate: Gate) {
        taps += 1
        current = Denial(gate.reason, gate.detail, taps)
    }

    /** Takes the refusal off screen — called by the timer, or when the screen moves on. */
    fun clear() {
        current = null
    }
}

/**
 * Remembers the screen's single refusal slot.
 *
 * @return the holder to raise refusals on and to read the visible one from.
 */
@Composable
fun rememberDenialState(): DenialState = remember { DenialState() }

/**
 * What a locked control should do on tap, and how it should look.
 *
 * The returned modifier dims the control and gives TalkBack the refusal as its state description,
 * so a screen reader is told the same thing the toast says rather than reading an unqualified
 * label. The glyph half of the pattern — [KrtLockBadge][de.greluc.krt.profit.basetool.android
 * .core.designsystem.component.KrtLockBadge] on an icon button, an inline lock on a
 * call-to-action — is the caller's to place, because only the caller knows the control's shape.
 *
 * @param gate whether the caller may act, and why not.
 * @param onAllowed the real action.
 * @param denials where to raise the refusal when they may not.
 * @return the modifier to apply, and the click to install.
 */
@Composable
fun rememberGated(
    gate: Gate,
    onAllowed: () -> Unit,
    denials: DenialState,
): Pair<Modifier, () -> Unit> {
    val dim = Modifier.krtLocked(locked = !gate.allowed, stateLabel = gate.reason)
    val click: () -> Unit = { if (gate.allowed) onAllowed() else denials.raise(gate) }
    return dim to click
}
