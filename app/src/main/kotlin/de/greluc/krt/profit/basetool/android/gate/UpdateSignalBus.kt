/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import de.greluc.krt.profit.basetool.android.core.network.UpdateSignal
import de.greluc.krt.profit.basetool.android.core.network.UpdateSignalListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries the API client's [UpdateSignal]s to whichever [UpdateGateViewModel] is alive (REQ-APP-API-010).
 *
 * One per process, owned by the auth graph beside the client. A signal raised while no gate
 * collects is dropped: the next gate reads the policy on its first resume anyway.
 */
class UpdateSignalBus : UpdateSignalListener {
    private val flow =
        MutableSharedFlow<UpdateSignal>(
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** The signals, in arrival order. */
    val signals: SharedFlow<UpdateSignal> = flow.asSharedFlow()

    /**
     * Publishes [signal] without suspending, as an OkHttp thread requires.
     *
     * @param signal what the transport saw.
     */
    override fun onSignal(signal: UpdateSignal) {
        flow.tryEmit(signal)
    }

    private companion object {
        /** Room for a burst of failed calls from one screen before the oldest is dropped. */
        const val BUFFER = 64
    }
}
