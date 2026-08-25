/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * What a pushed screen puts in the top bar instead of its destination's static title.
 *
 * Design chapters 06, 10, 11 and 12 all draw the same head on a detail: the **subject's own name**
 * beside the back arrow, its status underneath, and an overflow on the right — no org chip and no
 * bell. Those two belong to the roots, where a member is choosing what to look at; on a detail they
 * compete with the thing being looked at, and the name ends up repeated once in the bar as a
 * category ("EINSATZ") and once below it as a fact.
 *
 * @property title the subject's name.
 * @property subtitle drawn under the title, small — usually a status pill.
 */
data class ScreenTopBar(
    val title: String,
    val subtitle: (@Composable () -> Unit)? = null,
)

/**
 * The slot a detail screen writes its head into.
 *
 * A composition local rather than a navigation argument because the head's content is *data the
 * screen loads* — the Einsatz's name is not known when the route is built, and threading it back up
 * through the NavHost would make every detail screen's signature carry its own title.
 */
val LocalScreenTopBar: androidx.compose.runtime.ProvidableCompositionLocal<MutableState<ScreenTopBar?>> =
    compositionLocalOf { mutableStateOf(null) }

/**
 * Publishes this screen's head for as long as the screen is composed, and clears it on the way out.
 *
 * Clearing on dispose is the load-bearing half: a stale head would leave the previous Einsatz's
 * name in the bar of whatever screen came next, which reads as a navigation bug rather than a
 * rendering one.
 *
 * @param title the subject's name.
 * @param subtitle drawn under it.
 */
@Composable
fun ProvideScreenTopBar(
    title: String,
    subtitle: (@Composable () -> Unit)? = null,
) {
    val slot = LocalScreenTopBar.current
    DisposableEffect(title, subtitle) {
        slot.value = ScreenTopBar(title = title, subtitle = subtitle)
        onDispose { slot.value = null }
    }
}
