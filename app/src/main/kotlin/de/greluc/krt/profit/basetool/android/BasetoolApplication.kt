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

/**
 * Application entry point, and the one place that decides how loud the app is.
 *
 * [KrtLog] documents that "the application sets it to DEBUG in debug builds" — and until this class
 * existed, nothing did. Every `KrtLog.d` in the codebase was dropped, including the auth trail that
 * says what a login attempt did, which is exactly the trail one needs while a login is failing.
 *
 * The gate is `BuildConfig.DEBUG` rather than the flavour: a release build stays at INFO no matter
 * which backend it points at, and a debuggable build is verbose no matter which one it points at.
 *
 * It also owns the auth object graph, which is not a tidiness decision. [AuthContainer] documents
 * itself as "built once per process", and while it hung off the activity it was built once per
 * *activity* — the difference is invisible until something recreates one. The token DataStore
 * refuses a second instance on the same file outright, so the second `AuthContainer` threw
 * `IllegalStateException: There are multiple DataStores active for the same file` and the process
 * died: from the member's side, the app vanishes to the home screen. Anything that recreates the
 * activity does it — a rotation on a tablet, a system font-size change, and now a language change,
 * which is how it was finally observed.
 */
class BasetoolApplication : Application() {
    /**
     * The auth object graph, built once per process and shared by every activity.
     *
     * `by lazy` because the Keystore work behind it is not free and a process that never shows a
     * screen should not pay for it.
     */
    val auth: AuthContainer by lazy { AuthContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            KrtLog.minimumLevel = Log.DEBUG
        }
    }
}
