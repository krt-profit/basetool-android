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
import de.greluc.krt.profit.basetool.android.core.data.NotificationKind

/**
 * The five notification channels of design chapter 14, one per [NotificationKind].
 *
 * A push without a kind lands on [CHANNEL_SYSTEM]. Every channel is `VISIBILITY_PRIVATE`, and every
 * posted notification carries a public replacement that reveals nothing (REQ-NOTIF-021).
 */
object KrtNotificationChannels {
    /** Einsätze and Check-In — chapter 14's first channel, at high importance. */
    const val CHANNEL_MISSIONS: String = "krt_missions"

    /** Aufträge and their assignments, at high importance. */
    const val CHANNEL_ORDERS: String = "krt_orders"

    /** The Materialbörse. */
    const val CHANNEL_EXCHANGE: String = "krt_exchange"

    /** Bank and payouts. */
    const val CHANNEL_BANK: String = "krt_bank"

    /** System messages and announcements — and anything this build cannot classify. */
    const val CHANNEL_SYSTEM: String = "krt_system"

    /** A retired channel id that [ensure] deletes if it still exists. */
    private const val CHANNEL_GENERAL_LEGACY: String = "krt_general"

    /** A retired, never-posted channel id that [ensure] deletes if it still exists. */
    private const val CHANNEL_OPERATIONS_LEGACY: String = "krt_operations"

    /**
     * The channel a notification of this kind belongs on.
     *
     * @param kind what the notification is about.
     * @return the channel id.
     */
    fun channelFor(kind: NotificationKind): String =
        when (kind) {
            NotificationKind.MISSION -> CHANNEL_MISSIONS
            NotificationKind.ORDER -> CHANNEL_ORDERS
            NotificationKind.EXCHANGE -> CHANNEL_EXCHANGE
            NotificationKind.BANK -> CHANNEL_BANK
            NotificationKind.SYSTEM -> CHANNEL_SYSTEM
        }

    /**
     * Creates the channels if they do not exist yet; idempotent and safe on every start.
     *
     * @param context any context; the application context is used.
     */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            channel(context, CHANNEL_MISSIONS, R.string.notification_channel_missions, HIGH),
        )
        manager.createNotificationChannel(
            channel(context, CHANNEL_ORDERS, R.string.notification_channel_orders, HIGH),
        )
        manager.createNotificationChannel(
            channel(context, CHANNEL_EXCHANGE, R.string.notification_channel_exchange, DEFAULT),
        )
        manager.createNotificationChannel(
            channel(context, CHANNEL_BANK, R.string.notification_channel_bank, DEFAULT),
        )
        manager.createNotificationChannel(
            channel(context, CHANNEL_SYSTEM, R.string.notification_channel_system, DEFAULT),
        )
        manager.deleteNotificationChannel(CHANNEL_GENERAL_LEGACY)
        manager.deleteNotificationChannel(CHANNEL_OPERATIONS_LEGACY)
    }

    /**
     * Answers whether the app may post at all: the runtime permission on API 33+ and the app's
     * notification switch in system settings.
     *
     * @param context any context.
     * @return `true` when a posted notification would actually be shown.
     */
    fun canPost(context: Context): Boolean {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The runtime permission, by name: the constant is API 33 and this app starts at 31. */
    private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    /** Chapter 14's HOCH. */
    private const val HIGH = NotificationManager.IMPORTANCE_HIGH

    /** Chapter 14's STANDARD. */
    private const val DEFAULT = NotificationManager.IMPORTANCE_DEFAULT

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
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
}
