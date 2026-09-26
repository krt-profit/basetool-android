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
 * What a pushed screen puts in the top bar instead of its destination's static title: the
 * subject's name beside the back arrow, without org chip or bell.
 *
 * @property title the subject's name, or `null` when the screen only adds actions to its existing
 *   section bar.
 * @property titleBadge drawn beside the title — the subject's kind, where it has one.
 * @property subtitle drawn under the title, small — usually a status pill.
 * @property actions trailing controls the screen owns, such as its overflow menu.
 * @property selection a running multi-selection, which replaces the whole bar (design ch. 09,
 *   artboard 5).
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
 * Publishes this screen's head while the screen is composed, and clears it on dispose.
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
    SideEffect { slot.value = published }
    DisposableEffect(Unit) {
        onDispose {
            if (slot.value === published) {
                slot.value = null
            }
        }
    }
}
