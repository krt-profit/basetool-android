/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import de.greluc.krt.profit.basetool.android.MainActivity
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.NotificationKind
import de.greluc.krt.profit.basetool.android.core.data.NotificationSignal
import de.greluc.krt.profit.basetool.android.navigation.KRT_DEEP_LINK_SCHEME
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Posts a system notification for something that arrived while the app was running. */
interface SystemNotifications {
    /**
     * Posts a notification for the signal, deriving wording, channel and deep link from its type and
     * parameters (REQ-APP-NOTIF-005).
     *
     * @param signal what the server said arrived.
     */
    fun notify(signal: NotificationSignal)
}

/**
 * Posts shade notifications while the app runs and its SSE stream delivers (REQ-APP-NOTIF-010).
 *
 * Every notification uses a private channel and a public replacement reading „Neue
 * Benachrichtigung", so nothing sensitive shows on a locked screen.
 *
 * @property context the application context.
 */
class SystemNotifier(
    private val context: Context,
) : SystemNotifications {
    /**
     * {@inheritDoc}
     *
     * The permission check lives in {@link KrtNotificationChannels#canPost} one line above the
     * post, which lint cannot follow across the call; the check is real and covers both gates.
     */
    @SuppressLint("MissingPermission")
    override fun notify(signal: NotificationSignal) {
        if (!KrtNotificationChannels.canPost(context)) {
            return
        }
        KrtNotificationChannels.ensure(context)

        val kind = NotificationKind.from(signal.type)
        val channel = KrtNotificationChannels.channelFor(kind)
        val builder =
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(DesignR.drawable.ic_krt_notification)
                .setColor(ACCENT)
                .setContentTitle(headline(signal))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, channel)
                        .setSmallIcon(DesignR.drawable.ic_krt_notification)
                        .setContentTitle(context.getString(R.string.notification_public_title))
                        .build(),
                )
                .setContentIntent(intentFor(routeFor(signal)))

        NotificationManagerCompat.from(context).notify(channel.hashCode(), builder.build())
    }

    /**
     * The sentence the shade shows: the inbox wording, localised from the type and its parameters,
     * with the generic line for an unknown type and the fixed headline for a refresh-only push
     * (REQ-APP-NOTIF-005).
     *
     * @param signal what the server said arrived.
     * @return the headline.
     */
    private fun headline(signal: NotificationSignal): String {
        val type = signal.type
        if (type.isNullOrBlank()) {
            return context.getString(R.string.notifications_type_generic)
        }
        return fillTemplate(
            template = context.getString(notificationTypeRes(type)),
            params = signal.params,
            fallback = context.getString(R.string.notifications_type_generic),
        )
    }

    /**
     * Where a tap should land, via the same resolver the inbox row uses.
     *
     * @param signal what the server said arrived.
     * @return the route, or `null` for the inbox.
     */
    private fun routeFor(signal: NotificationSignal): String? =
        notificationDestination(entityType = signal.entityType, entityId = signal.entityId)

    /**
     * Builds the tap target: a deep link to the notification's screen, or the inbox without a route.
     *
     * @param route the app route, or `null` for the inbox.
     * @return the pending intent.
     */
    private fun intentFor(route: String?): PendingIntent {
        val target = route ?: INBOX_ROUTE
        val intent = Intent(Intent.ACTION_VIEW, "$KRT_DEEP_LINK_SCHEME://$target".toUri())
        intent.component = ComponentName(context, MainActivity::class.java)
        intent.setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            target.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        /**
         * One id, reused.
         *
         * The shade shows one entry that the inbox has something new, rather than a stack that
         * grows for as long as the app runs — the app cannot clear entries it posted before a
         * restart, so a growing stack would outlive its own truth.
         */
        const val NOTIFICATION_ID = 1001

        const val INBOX_ROUTE = "notifications"

        /** The brand accent of the design system, as an ARGB int for the notification builder. */
        const val ACCENT = 0xFFE77E23.toInt()
    }
}
