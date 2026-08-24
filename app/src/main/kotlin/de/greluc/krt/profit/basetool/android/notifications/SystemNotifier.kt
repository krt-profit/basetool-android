/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import de.greluc.krt.profit.basetool.android.MainActivity
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.navigation.KRT_DEEP_LINK_SCHEME
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Posts a system notification for something that arrived while the app was running. */
interface SystemNotifications {
    /**
     * Tells the member about one unread item.
     *
     * @param title the headline, already localised and free of anything sensitive.
     * @param body the line under it, or `null`.
     * @param deepLinkRoute the app route to open, or `null` for the inbox.
     */
    fun notify(
        title: String,
        body: String?,
        deepLinkRoute: String?,
    )
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
    override fun notify(
        title: String,
        body: String?,
        deepLinkRoute: String?,
    ) {
        if (!KrtNotificationChannels.canPost(context)) {
            return
        }
        KrtNotificationChannels.ensure(context)

        val builder =
            NotificationCompat.Builder(context, KrtNotificationChannels.CHANNEL_GENERAL)
                // The 24 dp alpha-only silhouette of chapter 14, tinted with the brand accent.
                .setSmallIcon(DesignR.drawable.ic_krt_bell)
                .setColor(ACCENT)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                // The lock-screen stand-in. Set unconditionally: a builder that only added it for
                // notifications it judged sensitive would be one judgement call away from a leak.
                .setPublicVersion(
                    NotificationCompat.Builder(context, KrtNotificationChannels.CHANNEL_GENERAL)
                        .setSmallIcon(DesignR.drawable.ic_krt_bell)
                        .setContentTitle(context.getString(R.string.notification_public_title))
                        .build(),
                )
                .setContentIntent(intentFor(deepLinkRoute))
        body?.let { builder.setContentText(it) }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

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
        // Built explicit from the CONSTRUCTOR, not made explicit afterwards. Both forms bind the
        // same component at runtime, and only this one is visibly explicit to a reader and to
        // static analysis: `setClass` inside an `apply` block is a mutation CodeQL's
        // implicit-PendingIntent query does not follow, so the earlier version fixed the intent
        // and left the alert standing -- which is its own kind of defect, because the next person
        // has to re-derive whether the finding is real.
        //
        // Why it matters at all: a PendingIntent is handed to the system to fire on our behalf, so
        // an implicit one inside it is a blank cheque against whatever resolves it. The activity
        // is known here; nothing needs to resolve anything.
        //
        // The deep link stays as the intent's DATA, which is what the nav graph routes on.
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "$KRT_DEEP_LINK_SCHEME://$target".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
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
