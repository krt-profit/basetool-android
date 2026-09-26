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
 * Requests `POST_NOTIFICATIONS` once, on first composition after the member has passed the app's
 * approval and terms gates.
 *
 * A denial is not asked again. Below API 33 this composes nothing.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        }
    LaunchedEffect(Unit) {
        if (!KrtNotificationChannels.canPost(context)) {
            launcher.launch(POST_NOTIFICATIONS)
        }
    }
}

/** The runtime permission, by name: the constant is API 33 and this app starts at 31. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
