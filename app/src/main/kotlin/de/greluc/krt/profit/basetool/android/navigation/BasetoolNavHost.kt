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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_MOTION_MS
import de.greluc.krt.profit.basetool.android.settings.AppLanguage
import de.greluc.krt.profit.basetool.android.settings.LicensesScreen
import de.greluc.krt.profit.basetool.android.settings.SettingsScreen
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
 * @param onLogout ends the session.
 * @param settings everything the Einstellungen screen needs that the graph cannot know.
 * @param modifier layout modifier.
 */
@Composable
fun BasetoolNavHost(
    navController: NavHostController,
    onOpenDestination: (KrtDestination) -> Unit,
    onLogout: () -> Unit,
    settings: SettingsBindings,
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
                    KrtDestination.More -> {
                        MoreScreen(onOpen = onOpenDestination)
                    }

                    KrtDestination.Settings -> {
                        val version =
                            LocalContext.current.let { context ->
                                context.packageManager.getPackageInfo(context.packageName, 0)
                            }
                        SettingsScreen(
                            accountName = settings.accountName,
                            language = settings.language,
                            onLanguageChange = settings.onLanguageChange,
                            appLockEnabled = settings.appLockEnabled,
                            appLockAvailable = settings.appLockAvailable,
                            onAppLockChange = settings.onAppLockChange,
                            onOpenPrivacy = settings.onOpenPrivacy,
                            onOpenImprint = settings.onOpenImprint,
                            onOpenTerms = settings.onOpenTerms,
                            // A plain push, NOT navigateToTopLevel: the notice is a sub-page of
                            // this screen, so back has to return here rather than to Übersicht.
                            onOpenLicenses = { navController.navigate(KrtDestination.Licenses.route) },
                            onLogout = onLogout,
                            versionName = version.versionName.orEmpty(),
                            versionCode = settings.versionCode,
                        )
                    }

                    KrtDestination.Licenses -> {
                        LicensesScreen(onOpenUrl = settings.onOpenUrl)
                    }

                    else -> {
                        PlaceholderScreen(destination = destination)
                    }
                }
            }
        }
    }
}

/**
 * Everything the Einstellungen screen needs from outside the navigation graph.
 *
 * Bundled into one holder rather than threaded through as ten parameters, because every one of them
 * would otherwise have to be declared twice more — on [BasetoolNavHost] and on `BasetoolApp` — for
 * a screen that is the only consumer. The activity owns all of it: the session, the Keystore-backed
 * lock, and the browser hand-off.
 *
 * @property accountName the signed-in member's username from the ID token, or `null` while unknown.
 * @property language the language currently on screen.
 * @property onLanguageChange pins a language; the platform then recreates the activity.
 * @property appLockEnabled whether a lock is armed.
 * @property appLockAvailable whether the device can prompt at all.
 * @property onAppLockChange arms or disarms the lock; arming raises the biometric prompt.
 * @property onOpenPrivacy opens the privacy policy in a browser.
 * @property onOpenImprint opens the imprint in a browser.
 * @property onOpenTerms opens the terms of use in a browser.
 * @property onOpenUrl opens an arbitrary URL in a browser; used by the open-source notice.
 * @property versionCode the app's build number, from `BuildConfig`.
 */
@Immutable
data class SettingsBindings(
    val accountName: String?,
    val language: AppLanguage,
    val onLanguageChange: (AppLanguage) -> Unit,
    val appLockEnabled: Boolean,
    val appLockAvailable: Boolean,
    val onAppLockChange: (Boolean) -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenImprint: () -> Unit,
    val onOpenTerms: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val versionCode: Int,
)
