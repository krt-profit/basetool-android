/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.app.Application
import android.util.Log
import de.greluc.krt.profit.basetool.android.core.common.KrtLog

/**
 * Application entry point, and the one place that decides how loud the app is.
 *
 * [KrtLog] documents that "the application sets it to DEBUG in debug builds" — and until this class
 * existed, nothing did. Every `KrtLog.d` in the codebase was dropped, including the auth trail that
 * says what a login attempt did, which is exactly the trail one needs while a login is failing.
 *
 * The gate is `BuildConfig.DEBUG` rather than the flavour: a release build stays at INFO no matter
 * which backend it points at, and a debuggable build is verbose no matter which one it points at.
 */
class BasetoolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            KrtLog.minimumLevel = Log.DEBUG
        }
    }
}
