/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * How dim a control is when the caller may not use it.
 *
 * The disabled rung of the button ladder (design ch. 02 §1: „Disabled = 45 % opacity, never a new
 * color"). Deliberately the same figure the design system's own buttons use for `enabled = false`,
 * because to a member this *is* the disabled state — the difference is only that this one answers.
 */
private const val GATED_ALPHA = 0.45f

/**
 * A control the caller may not use: drawn as disabled, still able to explain itself.
 *
 * `enabled = false` is what this deliberately avoids. A Compose control with that flag receives no
 * tap, and a control that cannot be tapped cannot say why it is dim — which leaves a grey button
 * that tells a member nothing, the worst of both options. So the control keeps a live tap target,
 * performs no write, and names the missing grant (ADR-0011, `REQ-APP-AUTH-013`).
 *
 * Hiding it was the alternative and was rejected: this organisation grants roles by hand, and a
 * feature nobody can see is a feature nobody asks to be given.
 *
 * @property allowed whether the action may actually run.
 * @property reason what to say when it may not — the **grant's name**, never a status code.
 */
data class Gate(
    val allowed: Boolean,
    val reason: String,
)

/**
 * What a gated control should do on tap, and how it should look.
 *
 * @param gate whether the caller may act, and why not.
 * @param onAllowed the real action.
 * @param onDenied invoked with [Gate.reason] instead, when they may not.
 * @return the modifier to dim with, and the click to install.
 */
@Composable
fun rememberGated(
    gate: Gate,
    onAllowed: () -> Unit,
    onDenied: (String) -> Unit,
): Pair<Modifier, () -> Unit> {
    val dim = if (gate.allowed) Modifier else Modifier.alpha(GATED_ALPHA)
    val click: () -> Unit = { if (gate.allowed) onAllowed() else onDenied(gate.reason) }
    return dim to click
}

/**
 * Holds the last refusal, so a screen can show it and let it go again.
 *
 * The **placement** of that message — a tooltip on the control, a toast at the foot, a line under
 * the button — is the open question in round 3 of the design prompt. Until it is answered the app
 * uses the toast it already has, because that is the one carrier the design system draws for a
 * transient sentence (ch. 02 §7) and swapping it later touches one call site per screen.
 *
 * @return the current refusal and the setter that raises or clears it.
 */
@Composable
fun rememberDenial(): Pair<String?, (String?) -> Unit> {
    var denial by remember { mutableStateOf<String?>(null) }
    return denial to { value: String? -> denial = value }
}
