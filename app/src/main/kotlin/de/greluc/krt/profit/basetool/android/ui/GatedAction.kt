/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLockToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtLocked
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import kotlinx.coroutines.delay

/**
 * How long a refusal stays on screen, in milliseconds (design ch. 09, artboard 14: „4 s").
 */
const val DENIAL_TOAST_MS = 4_000L

/**
 * A control the caller may not use: drawn as locked, still able to explain itself
 * (ADR-0011, REQ-APP-AUTH-013).
 *
 * The control keeps a live tap target, performs no write and names the missing grant; it is neither
 * `enabled = false` nor hidden. Role locks and row locks differ only in [reason] and [detail].
 *
 * @property allowed whether the action may actually run.
 * @property reason what to say when it may not: the grant's name as a sentence, never a status code
 *   or „Keine Berechtigung".
 * @property detail who hands that grant out, or the rule behind a row lock.
 */
data class Gate(
    val allowed: Boolean,
    val reason: String,
    val detail: String,
) {
    companion object {
        /**
         * Builds a gate from a permission that may not have been read yet.
         *
         * `true` opens the control; `false` locks it and names the missing grant; `null` locks it and says
         * the permission could not be checked (ADR-0011).
         *
         * @param permitted the permission's answer, or `null` when it is not known.
         * @param reason the missing-grant headline, used only for a real refusal.
         * @param detail who hands that grant out.
         * @param unknownReason the headline when the answer is not known.
         * @param unknownDetail what to do about it.
         * @return the gate to render.
         */
        fun of(
            permitted: Boolean?,
            reason: String,
            detail: String,
            unknownReason: String,
            unknownDetail: String,
        ): Gate =
            when (permitted) {
                true -> Gate(allowed = true, reason = reason, detail = detail)
                false -> Gate(allowed = false, reason = reason, detail = detail)
                null -> Gate(allowed = false, reason = unknownReason, detail = unknownDetail)
            }
    }
}

/**
 * One refusal, and the tap that raised it.
 *
 * @property title the missing grant.
 * @property detail who hands it out.
 * @property serial which tap this is; changes on every raise, including a repeat, so the dismissal
 *   timer restarts.
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
 * What a locked control does on tap, and how it looks.
 *
 * The returned modifier dims the control and gives TalkBack the refusal as its state description.
 * The lock glyph is the caller's to place.
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

/**
 * The refusal toast: one at a time, at the foot of the screen, gone after four seconds.
 *
 * Place it at the screen level, never inside a lazy list row.
 *
 * @param state the screen's refusal slot; nothing is drawn while it is empty.
 */
@Composable
fun DenialToast(state: DenialState) {
    val denial = state.current ?: return
    LaunchedEffect(denial.serial) {
        delay(DENIAL_TOAST_MS)
        state.clear()
    }
    Box(
        modifier = Modifier.fillMaxSize().zIndex(1f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        KrtLockToast(
            title = denial.title,
            detail = denial.detail,
            modifier =
                Modifier
                    .padding(horizontal = KrtSpacing.s16)
                    .padding(bottom = KrtSpacing.s16 + LocalKrtBottomBarInset.current),
        )
    }
}
