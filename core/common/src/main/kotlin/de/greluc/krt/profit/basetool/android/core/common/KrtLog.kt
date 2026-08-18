/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.common

import android.util.Log

/**
 * The single logging facade of the app.
 *
 * `android.util.Log` is not called anywhere else — a CI gate forbids it (security concept §4,
 * "static guardrails"). Two reasons, and neither is style:
 *
 * 1. **Redaction has to have one place.** Tokens, callsigns and e-mail addresses must never reach a
 *    log sink (main repo REQ-OBS-004, which this app inherits by contract). A facade is where a
 *    redaction rule can actually be applied and tested; scattered `Log.d` calls are where one gets
 *    forgotten.
 * 2. **Release builds must be quiet by default.** [minimumLevel] is raised for release builds
 *    rather than relying on every call site remembering to guard itself.
 *
 * The API is deliberately small: no varargs formatting, no lazy-message overloads beyond the lambda
 * below, no per-class logger instances. A tag is a short subsystem name, not a class name.
 */
object KrtLog {
    /** Emitted for every message this facade writes, so a subsystem is greppable in logcat. */
    private const val TAG_PREFIX = "KRT"

    /**
     * Messages below this level are dropped without evaluating their lambda.
     *
     * Defaults to [Log.INFO]; the application sets it to [Log.DEBUG] in debug builds. It is a
     * `var` rather than a build-config read so `core:common` stays free of a dependency on the
     * application's generated config, and so tests can raise or lower it.
     */
    @Volatile
    @JvmStatic
    var minimumLevel: Int = Log.INFO

    /**
     * Logs a debug message, evaluating [message] only when [Log.DEBUG] passes [minimumLevel].
     *
     * @param tag short subsystem name, e.g. `auth` or `http`
     * @param message produced only when the message is actually emitted
     */
    fun d(
        tag: String,
        message: () -> String,
    ) = log(Log.DEBUG, tag, null, message)

    /**
     * Logs an informational message.
     *
     * @param tag short subsystem name
     * @param message produced only when the message is actually emitted
     */
    fun i(
        tag: String,
        message: () -> String,
    ) = log(Log.INFO, tag, null, message)

    /**
     * Logs a warning — a condition the app recovered from, such as a retried request.
     *
     * @param tag short subsystem name
     * @param throwable optional cause; its stack trace is attached
     * @param message produced only when the message is actually emitted
     */
    fun w(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) = log(Log.WARN, tag, throwable, message)

    /**
     * Logs an error — a condition the user will notice.
     *
     * @param tag short subsystem name
     * @param throwable optional cause; its stack trace is attached
     * @param message produced only when the message is actually emitted
     */
    fun e(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) = log(Log.ERROR, tag, throwable, message)

    /**
     * Writes one message if [level] passes the [minimumLevel] gate.
     *
     * @param level one of the `android.util.Log` level constants
     * @param tag short subsystem name, prefixed with `KRT` on the way out
     * @param throwable optional cause
     * @param message evaluated only when the message is emitted
     */
    private fun log(
        level: Int,
        tag: String,
        throwable: Throwable?,
        message: () -> String,
    ) {
        if (level < minimumLevel) return
        val fullTag = "$TAG_PREFIX/$tag"
        val text = message()
        when (level) {
            Log.DEBUG -> Log.d(fullTag, text, throwable)
            Log.INFO -> Log.i(fullTag, text, throwable)
            Log.WARN -> Log.w(fullTag, text, throwable)
            else -> Log.e(fullTag, text, throwable)
        }
    }
}
