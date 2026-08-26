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
     * Tells the member what arrived.
     *
     * Takes the signal rather than a rendered headline: the wording is assembled from the type and
     * its parameters (REQ-APP-NOTIF-005), which needs resources, and the channel and the deep link
     * are decided by the same two fields. Splitting that across the caller and here would give two
     * places a chance to disagree about what one push is.
     *
     * @param signal what the server said arrived.
     */
    fun notify(signal: NotificationSignal)
}

/**
 * The shade half of design chapter 14 (REQ-APP-NOTIF-010).
 *
 * **Nothing sensitive reaches a locked screen.** The channel is private and this builder supplies
 * the public replacement chapter 14 dictates — „Neue Benachrichtigung" and nothing else — so an
 * amount or a member's name can never be read off a locked device. Both halves are here rather than
 * at the call sites, because a call site that forgot one would produce exactly the leak the rule
 * exists to prevent and nothing would flag it.
 *
 * **It posts only what the app itself received.** There is no push channel (plan Q2), so this fires
 * while the app runs and its SSE stream delivers — not afterwards. Chapter 14's shade mockups are
 * therefore honoured for a member who has the app open on another screen or has just locked the
 * device, and not for one who closed it. That is the whole of what is available without FCM.
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
                // The 24 dp alpha-only silhouette of chapter 14, tinted with the brand accent.
                // A bell stood here, which is every app's notification icon and therefore nobody's:
                // in a full status bar the point of a small icon is saying WHICH app posted.
                .setSmallIcon(DesignR.drawable.ic_krt_notification)
                .setColor(ACCENT)
                .setContentTitle(headline(signal))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                // The lock-screen stand-in. Set unconditionally: a builder that only added it for
                // notifications it judged sensitive would be one judgement call away from a leak --
                // and since the shade now carries the real wording, this is the only thing between
                // that wording and a locked screen.
                .setPublicVersion(
                    NotificationCompat.Builder(context, channel)
                        .setSmallIcon(DesignR.drawable.ic_krt_notification)
                        .setContentTitle(context.getString(R.string.notification_public_title))
                        .build(),
                )
                .setContentIntent(intentFor(routeFor(signal)))

        // One id per channel, not one for the whole app. Five entries at most, each replaced by the
        // newest of its own kind: an Auftrag must not overwrite the Einsatz that starts in ten
        // minutes, and a growing stack would outlive its own truth (the app cannot clear entries it
        // posted before a restart).
        NotificationManagerCompat.from(context).notify(channel.hashCode(), builder.build())
    }

    /**
     * The sentence the shade shows.
     *
     * The same wording the inbox row carries, assembled from the type and its parameters so it is
     * localised here rather than on the server, and so an unknown type degrades to the generic
     * line instead of to a blank (REQ-APP-NOTIF-005). A refresh-only push has no type and keeps the
     * fixed headline the app used before the payload existed.
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
     * Where a tap should land.
     *
     * The same resolver the inbox row uses, so the shade and the list cannot disagree about which
     * screen a notification belongs to — and so an entity this build has no screen for falls back
     * to the inbox rather than to a route that does not exist.
     *
     * @param signal what the server said arrived.
     * @return the route, or `null` for the inbox.
     */
    private fun routeFor(signal: NotificationSignal): String? =
        notificationDestination(entityType = signal.entityType, entityId = signal.entityId)

    /**
     * Builds the tap target.
     *
     * Deep-links straight to the screen the item is about, which is chapter 14's rule; without a
     * route it opens the inbox, because dropping the member on the dashboard after they tapped a
     * specific notification makes them hunt for what they were told about.
     *
     * @param route the app route, or `null` for the inbox.
     * @return the pending intent.
     */
    private fun intentFor(route: String?): PendingIntent {
        val target = route ?: INBOX_ROUTE
        // Component AND package, assigned as plain statements rather than inside an `apply`.
        //
        // A PendingIntent is handed to the system to fire on our behalf, so an implicit intent
        // inside one is a blank cheque against whatever resolves it. The activity is known here;
        // nothing needs to resolve anything. All three forms tried -- `package` alone, `setClass`
        // in an `apply`, the `Intent(Context, Class)` constructor -- bind correctly at runtime,
        // and the first two left CodeQL's implicit-PendingIntent query still reporting. An alert
        // that survives its own fix is its own defect: the next reader has to re-derive whether
        // the finding is real, and after the third round the readable form is the one written out
        // in full.
        //
        // The deep link stays as the intent's DATA, which is what the nav graph routes on.
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
