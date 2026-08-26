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
 * The notification channels of design chapter 14, and the rules that keep them honest.
 *
 * **Five, because a member should be able to silence one kind and keep another.** The chapter names
 * them — Einsätze & Check-In and Aufträge & Zuweisungen at high importance, Materialbörse, Bank &
 * Auszahlungen and System & Ankündigungen at default — and they map one for one onto
 * [NotificationKind], which the inbox already uses to pick a row's glyph. One classification, two
 * uses: a kind that files a row under a glyph files a push under a channel.
 *
 * **They could not exist until the push said what arrived.** The stream event was a bare ping, so
 * every notification was the same message and four of these channels would have been switches that
 * silence nothing. The backend now sends the kind (its REQ-NOTIF-021, ADR-0146) and the app files by
 * it. A push that still arrives without one lands on [CHANNEL_SYSTEM], which is where "something
 * happened and this build cannot say what" belongs.
 *
 * **Nothing sensitive on the lock screen.** Every channel is created `VISIBILITY_PRIVATE` and every
 * posted notification carries a public replacement saying „Neue Benachrichtigung" and nothing else.
 * Enforced here and in [SystemNotifier] rather than left to each call site, because a call site that
 * forgot would put a member's amounts on a locked screen and nothing would flag it. This matters
 * more now than it did: the shade entry carries the real wording since the owner's decision of
 * 2026-08-26, so the public replacement is the only thing standing between that wording and a
 * locked screen.
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

    /**
     * The channel this build used to post everything on.
     *
     * Kept only so [ensure] can delete it: it named itself „Allgemein" and carried every kind, so
     * leaving it would give a member a switch that silences all five of the real ones under a name
     * that says otherwise. Android remembers a deleted channel's settings if it is ever recreated.
     */
    private const val CHANNEL_GENERAL_LEGACY: String = "krt_general"

    /**
     * The channel that used to be created and never posted to.
     *
     * Deleted for the same reason it was deleted before the split: a switch that silences nothing.
     */
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
        // The two channels this app used to create. Harmless on a device that never had them.
        manager.deleteNotificationChannel(CHANNEL_GENERAL_LEGACY)
        manager.deleteNotificationChannel(CHANNEL_OPERATIONS_LEGACY)
    }

    /**
     * Answers whether the app may post at all.
     *
     * Two gates, and both matter: the runtime permission on API 33+, and the member's own switch
     * for the app in system settings. A caller that checked only the first would post into a void
     * and believe it had told somebody.
     *
     * @param context any context.
     * @return `true` when a posted notification would actually be shown.
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
            // The lock-screen rule of chapter 14, set on the channel so it holds for every
            // notification on it rather than depending on each one remembering.
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
}
