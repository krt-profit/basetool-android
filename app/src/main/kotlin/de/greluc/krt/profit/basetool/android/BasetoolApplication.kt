/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.app.Application
import android.util.Log
import de.greluc.krt.profit.basetool.android.auth.AuthContainer
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.notifications.KrtNotificationChannels
import de.greluc.krt.profit.basetool.android.settings.ScreenCapturePreference

/**
 * Application entry point; sets the log level and owns every DataStore-backed object in the app.
 *
 * [KrtLog] is set to DEBUG when `BuildConfig.DEBUG` and to INFO otherwise, regardless of flavour.
 * Every DataStore lives here because DataStore refuses a second instance on the same file, and an
 * activity may be recreated; `ProcessStoreOwnershipTest` fails the build if a store is opened
 * elsewhere.
 */
class BasetoolApplication : Application() {
    /**
     * The auth object graph, built once per process and shared by every activity.
     *
     * `by lazy` because the Keystore work behind it is not free and a process that never shows a
     * screen should not pay for it.
     */
    val auth: AuthContainer by lazy { AuthContainer(this) }

    /**
     * The member's screen-capture choice, built once per process and shared by every activity.
     *
     * The window flag is per activity, but its DataStore must exist only once per process.
     */
    val screenCapture: ScreenCapturePreference by lazy {
        ScreenCapturePreference(ScreenCapturePreference.createStore(this))
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            KrtLog.minimumLevel = Log.DEBUG
        }
        KrtNotificationChannels.ensure(this)
    }
}
