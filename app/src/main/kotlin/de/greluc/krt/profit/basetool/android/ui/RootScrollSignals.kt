/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The „scrolls to top" half of design chapter 03's re-tap rule.
 *
 * > Re-tapping the active destination pops to its root and scrolls to top.
 *
 * Popping is navigation's job and it already happens. Scrolling is not: the destination is *torn
 * down and rebuilt* by that pop, and its list state is then restored from the saved-state bundle —
 * so by the time the new screen exists, it has already been put back exactly where the member
 * left it. Nothing about the rebuild distinguishes "I came back here" from "I asked for the top".
 *
 * Hence a counter that outlives the rebuild. The re-tap bumps the counter for that route; the list
 * remembers, in its own saveable state, which value it last acted on. The two differ only after a
 * re-tap, so an ordinary return to a destination restores the position as before and does not jump.
 *
 * @property ticks one counter per route, so a re-tap on „Lager" never scrolls „Einsätze".
 */
@Stable
class RootScrollSignals {
    private val ticks = mutableStateMapOf<String, Int>()

    /**
     * Records that this destination was re-tapped while it was already the active one.
     *
     * @param route the destination's route.
     */
    fun request(route: String) {
        ticks[route] = (ticks[route] ?: 0) + 1
    }

    /**
     * How often this destination has been asked to return to the top.
     *
     * @param route the destination's route.
     * @return the count, `0` for a destination never re-tapped this process.
     */
    fun ticksFor(route: String): Int = ticks[route] ?: 0
}

/**
 * The re-tap counter of the destination currently being composed.
 *
 * Provided per destination by the navigation graph rather than read per screen, so a root screen
 * needs to know nothing about routes to obey the rule — and cannot accidentally watch a sibling's
 * counter.
 */
val LocalRootScrollTick = compositionLocalOf { 0 }

/**
 * A list state for a **root** screen, which returns to the top when its destination is re-tapped.
 *
 * Drop-in for `rememberLazyListState()` at the top level of a bar or rail destination. A pushed
 * detail screen must not use it: the rule is about the destination a member is already on, and a
 * detail is not one.
 *
 * @return the state to hand to the screen's `LazyColumn`.
 */
@Composable
fun rememberRootListState(): LazyListState {
    val state = rememberLazyListState()
    ActOnReselect { state.animateScrollToItem(0) }
    return state
}

/**
 * The same for a root screen that scrolls an ordinary `Column` rather than a lazy list.
 *
 * @return the state to hand to `Modifier.verticalScroll`.
 */
@Composable
fun rememberRootScrollState(): ScrollState {
    val state = rememberScrollState()
    ActOnReselect { state.animateScrollTo(0) }
    return state
}

/**
 * Runs [onReselect] when this destination's counter moves, and never on the first composition.
 *
 * The seen value is `rememberSaveable` on purpose: the pop that precedes a re-tap destroys this
 * composition and the saved-state bundle brings the old value back, which is exactly what makes
 * the difference visible. A plain `remember` would be re-initialised to the current counter by the
 * very rebuild the rule is about, and the list would never move.
 *
 * @param onReselect what to do about it, suspending so it can animate.
 */
@Composable
private fun ActOnReselect(onReselect: suspend () -> Unit) {
    val tick = LocalRootScrollTick.current
    var seen by rememberSaveable { mutableIntStateOf(tick) }
    LaunchedEffect(tick) {
        if (tick != seen) {
            seen = tick
            onReselect()
        }
    }
}
