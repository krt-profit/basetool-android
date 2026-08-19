/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_MOTION_MS
import de.greluc.krt.profit.basetool.android.ui.MoreScreen
import de.greluc.krt.profit.basetool.android.ui.PlaceholderScreen

/**
 * The navigation graph.
 *
 * Every destination registers the deep link that opens it, so a notification tap, a web link and an
 * in-app navigation converge on one address per screen. Transitions are a plain 200 ms cross-fade:
 * the design system allows colour and fade only — no slide-in stacks, no parallax — and a fade also
 * keeps the predictive-back preview honest, because the previous screen is shown as it really is.
 *
 * @param navController the controller driving the graph.
 * @param onOpenDestination invoked when a list entry opens another destination.
 * @param modifier layout modifier.
 */
@Composable
fun BasetoolNavHost(
    navController: NavHostController,
    onOpenDestination: (KrtDestination) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = KrtDestination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(KRT_MOTION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(KRT_MOTION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(KRT_MOTION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(KRT_MOTION_MS)) },
    ) {
        KrtDestination.entries.forEach { destination ->
            composable(
                route = destination.route,
                deepLinks = listOf(navDeepLink { uriPattern = destination.deepLink }),
            ) {
                when (destination) {
                    KrtDestination.More -> MoreScreen(onOpen = onOpenDestination, onLogout = onLogout)
                    else -> PlaceholderScreen(destination = destination)
                }
            }
        }
    }
}
