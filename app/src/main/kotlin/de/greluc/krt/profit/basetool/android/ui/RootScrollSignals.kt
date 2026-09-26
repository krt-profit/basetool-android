/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
 * Per-route re-tap counters that let a root screen scroll to the top when its active destination is re-tapped.
 *
 * The counters survive the pop-and-rebuild of the destination; a list scrolls only when its counter
 * differs from the value it last acted on, so an ordinary return keeps the restored position.
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
 * A list state for a root screen that scrolls to the top when its destination is re-tapped.
 *
 * Replaces `rememberLazyListState()` on bar and rail destinations only, never on pushed detail screens.
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
 * The grid counterpart of `rememberRootListState` for a root screen whose lazy list is a grid.
 *
 * @return the state to hand to the screen's `LazyVerticalGrid`.
 */
@Composable
fun rememberRootGridState(): LazyGridState {
    val state = rememberLazyGridState()
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
 * Runs [onReselect] when this destination's counter moves, never on the first composition.
 *
 * The last seen value is saveable, so it survives the pop that precedes a re-tap.
 *
 * @param onReselect the action to run, suspending so it can animate.
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
