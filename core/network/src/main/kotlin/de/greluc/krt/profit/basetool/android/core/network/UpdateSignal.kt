/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

/** An API answer that may mean this build calls a contract the server no longer serves (REQ-APP-API-010). */
enum class UpdateSignal {
    /** A `404`, or a problem body with code `NOT_FOUND`, on an API path. */
    NOT_FOUND,

    /** A problem body with code `APP_UPDATE_REQUIRED`: the server retired the path for this build. */
    UPDATE_REQUIRED,
}

/** Receives [UpdateSignal]s from the transport; called on an OkHttp thread and must not block. */
fun interface UpdateSignalListener {
    /**
     * Takes one signal.
     *
     * @param signal what the answer suggested.
     */
    fun onSignal(signal: UpdateSignal)

    /** Default listeners. */
    companion object {
        /** Discards every signal. */
        val None: UpdateSignalListener = UpdateSignalListener { }
    }
}
