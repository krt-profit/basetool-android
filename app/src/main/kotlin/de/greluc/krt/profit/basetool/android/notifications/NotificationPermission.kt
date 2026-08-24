/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for `POST_NOTIFICATIONS` once, the first time the member reaches the app behind its gates.
 *
 * **The app checked this permission and never requested it**, which meant the shade half of design
 * chapter 14 could not work for anybody: on Android 13+ the permission is denied until asked, so
 * the channels were created, `canPost` returned `false`, and the notifier silently posted nothing.
 * Found by walking a device — a JVM test can neither grant nor deny a runtime permission, and
 * `canPost` is honest about its own answer, so nothing failed anywhere.
 *
 * **Behind the gates, not at launch.** Android's guidance is to ask in context, and the context
 * here is the app itself: notifications are app-wide rather than a feature of one screen, and a
 * member who never opens the inbox is exactly the one the shade exists for. Asking before the
 * approval and terms gates would put a system dialog in front of somebody who may not have an
 * account yet.
 *
 * **Once, and never again by us.** The launcher fires on first composition only; Android remembers
 * a denial and stops showing the dialog, and a member who said no keeps the in-app badge. There is
 * no second ask and no explainer screen: without a push channel (plan Q2) the permission buys a
 * notification only while the app is running, which is not worth pressing anybody about.
 *
 * Below API 33 this composes nothing — the permission does not exist and notifications need no
 * grant.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The answer needs no handling: `canPost` reads the live state before every post, so a
            // grant starts working immediately and a denial simply leaves the badge as the only
            // channel. Storing it here would be a second source of truth for something the
            // platform already owns — and one that would go stale the moment somebody changed it
            // in the system settings.
        }
    LaunchedEffect(Unit) {
        if (!KrtNotificationChannels.canPost(context)) {
            launcher.launch(POST_NOTIFICATIONS)
        }
    }
}

/** The runtime permission, by name: the constant is API 33 and this app starts at 30. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
