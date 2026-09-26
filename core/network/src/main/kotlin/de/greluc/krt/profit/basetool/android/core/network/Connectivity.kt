/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the device has a network at all, so the app can disable writes while offline rather than
 * queue them.
 *
 * Reports the local link only, not whether the backend answers; backed by `ACCESS_NETWORK_STATE`
 * and makes no request.
 */
interface Connectivity {
    /**
     * Emits `true` while a validated network is available.
     *
     * Emits the current state immediately on collection, then on every change. Distinct values
     * only, so a flapping link does not recompose the screen on every transition.
     */
    val online: Flow<Boolean>
}

/**
 * The platform implementation.
 *
 * @property context an application context; the callback outlives any single screen.
 */
class SystemConnectivity(
    private val context: Context,
) : Connectivity {
    override val online: Flow<Boolean>
        get() =
            callbackFlow {
                val manager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (manager == null) {
                    trySend(true)
                    awaitClose {}
                    return@callbackFlow
                }
                val available = mutableSetOf<Network>()
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            available.add(network)
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            available.remove(network)
                            trySend(available.isNotEmpty())
                        }
                    }
                trySend(manager.hasNetwork())
                val request =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                manager.registerNetworkCallback(request, callback)
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
}

/**
 * Reads the current state once, for the value emitted before the first callback arrives.
 *
 * @return `true` when the active network reports internet capability.
 */
private fun ConnectivityManager.hasNetwork(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
