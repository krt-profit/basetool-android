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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState

/**
 * What a pushed screen puts in the top bar instead of its destination's static title.
 *
 * Design chapters 06, 10, 11 and 12 all draw the same head on a detail: the **subject's own name**
 * beside the back arrow, its status underneath, and an overflow on the right — no org chip and no
 * bell. Those two belong to the roots, where a member is choosing what to look at; on a detail they
 * compete with the thing being looked at, and the name ends up repeated once in the bar as a
 * category ("EINSATZ") and once below it as a fact.
 *
 * @property title the subject's name, or `null` when the screen only wants to add actions to the
 *   section bar it already has — a top-level destination is not a subject and must not start
 *   rendering as one.
 * @property titleBadge drawn beside the title — the subject's KIND, where the subject has one
 *   (design ch. 10 artboard 2 puts „MATERIAL" next to the order's number).
 * @property subtitle drawn under the title, small — usually a status pill.
 * @property actions trailing controls the screen owns, such as its overflow menu.
 * @property selection a running multi-selection, which **replaces** the whole bar rather than
 *   decorating it (design ch. 09, artboard 5). The org chip and the bell are for choosing what to
 *   look at, and while a member is picking rows they are picking within one scope already.
 */
data class ScreenTopBar(
    val title: String? = null,
    val titleBadge: (@Composable () -> Unit)? = null,
    val subtitle: (@Composable () -> Unit)? = null,
    val actions: (@Composable () -> Unit)? = null,
    val selection: SelectionBar? = null,
)

/**
 * The head a screen wears while a multi-selection is running.
 *
 * @property count how many rows are selected — the bar's whole text, „n gewählt".
 * @property onClear leaves selection mode; the design offers exactly two ways out, this ✕ and the
 *   system back gesture, and never "deselect everything one by one".
 */
data class SelectionBar(
    val count: Int,
    val onClear: () -> Unit,
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
 * @param title the subject's name, or `null` to keep the destination's own section title.
 * @param titleBadge drawn beside the title — the subject's kind.
 * @param subtitle drawn under it.
 * @param actions trailing controls the screen owns, such as its overflow menu.
 * @param selection a running multi-selection, which replaces the bar entirely while it lasts.
 */
@Composable
fun ProvideScreenTopBar(
    title: String? = null,
    titleBadge: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    selection: SelectionBar? = null,
) {
    // Publish from the screen's own composable rather than from a `LazyColumn` item: a lazy item
    // is disposed and recomposed as the list measures, which makes the head come and go for
    // reasons that have nothing to do with the screen.
    val slot = LocalScreenTopBar.current
    val published by
        rememberUpdatedState(
            ScreenTopBar(
                title = title,
                titleBadge = titleBadge,
                subtitle = subtitle,
                actions = actions,
                selection = selection,
            ),
        )
    // Published on every successful recomposition, cleared once on the way out. Keying a
    // DisposableEffect on the slots instead looks tidier and is a trap: a `subtitle` or `actions`
    // lambda is a fresh instance each frame, so the effect disposed and re-ran continuously — which
    // replaced the composition group behind `actions` and reset any state inside it, an overflow
    // menu that would not stay open (found on a device, 2026-08-26).
    SideEffect { slot.value = published }
    DisposableEffect(Unit) {
        onDispose {
            // Clear only what THIS screen last published. Compose recreates a subtree before it
            // disposes the old one, so a recomposed detail publishes its head and the outgoing
            // instance's `onDispose` then ran a moment later and wiped it — leaving the shell on
            // the route's fallback title. The Auftrag detail showed „Auftrag" instead of its
            // number and status for exactly this reason, on every open, and the sequence is only
            // visible in a log: publish → read → publish → read null.
            if (slot.value === published) {
                slot.value = null
            }
        }
    }
}
