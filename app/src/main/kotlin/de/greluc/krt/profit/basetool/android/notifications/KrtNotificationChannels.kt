/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.greluc.krt.profit.basetool.android.R

/**
 * The notification channels of design chapter 14, and the one place a system notification is posted
 * (REQ-APP-NOTIF-010).
 *
 * **What this can and cannot do.** There is no push channel — that was decided (plan Q2), and
 * without FCM nothing reaches a member whose app is not running. What is left is real but partial:
 * while the app runs, its own SSE stream delivers notifications, and a member who has switched
 * screens or locked the device still gets told. That is the whole benefit, and it is worth stating
 * plainly rather than implying the app pushes.
 *
 * **Nothing sensitive on the lock screen.** Chapter 14 fixes this: the public version of every
 * notification says „Neue Benachrichtigung" and nothing else. It is enforced by construction here —
 * the channels are created with {@code VISIBILITY_PRIVATE} and the posted notification carries a
 * public replacement — rather than left to each call site to remember, because a call site that
 * forgot would put a member's amounts on a locked screen and nothing would flag it.
 */
object KrtNotificationChannels {
    /** Einsätze and the system's own messages: the channel chapter 14 names first. */
    const val CHANNEL_OPERATIONS: String = "krt_operations"

    /** Everything else the inbox carries. */
    const val CHANNEL_GENERAL: String = "krt_general"

    /**
     * Creates the channels, if they do not exist yet.
     *
     * Idempotent, and safe to call on every start: Android keeps a channel's user-chosen importance
     * once it exists, so re-creating it changes nothing a member configured.
     *
     * @param context any context; the application context is used.
     */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            channel(
                context,
                CHANNEL_OPERATIONS,
                R.string.notification_channel_operations,
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            channel(
                context,
                CHANNEL_GENERAL,
                R.string.notification_channel_general,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /**
     * Answers whether the app may post at all.
     *
     * Two gates, and both matter: the runtime permission on API 33+, and the member's own switch
     * for the app in system settings. A caller that checked only the first would post into a void
     * and believe it had told somebody.
     *
     * @param context any context.
     * @return {@code true} when a posted notification would actually be shown.
     */
    fun canPost(context: Context): Boolean {
        // The permission itself only exists from API 33. Below that a notification needs no runtime
        // grant, so the member's own switch is the whole gate -- and naming the constant by string
        // rather than by field keeps the compile-time reference off a platform this app also runs
        // on (minSdk 30).
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The runtime permission, by name: the constant is API 33 and this app starts at 30. */
    private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    /**
     * Builds one channel.
     *
     * @param context for the display name.
     * @param id the channel id.
     * @param nameRes its user-visible name.
     * @param importance the channel importance.
     * @return the channel, private on the lock screen.
     */
    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        importance: Int,
    ): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), importance).apply {
            // The lock-screen rule of chapter 14, set on the channel so it holds for every
            // notification on it rather than depending on each one remembering.
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
}
