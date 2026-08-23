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
 * Whether the device has a network at all.
 *
 * It exists for one rule, and only for that rule: **offline disables writes** rather than queueing
 * them (design spec ch. 14). The optimistic-locking contract makes a queued mutation a conflict
 * factory — a save composed against a `version` that is minutes old is precisely the write the
 * server must refuse — so the app never holds one back for later. The honest alternative is to say
 * so before the member types: a greyed-out action is information, an error after a filled-in form
 * is a waste of their time.
 *
 * **It answers "is there a network", not "does the backend answer".** A captive portal, a dead VPN
 * or a backend outage all report connected. That is deliberate: the reads on the same screen fail
 * with their own message in those cases, and a stricter signal would need a probe request of its
 * own, running on a timer, to tell the member something the next tap tells them anyway.
 *
 * Backed by `ACCESS_NETWORK_STATE` — a normal permission, granted at install, with no runtime
 * prompt. Nothing leaves the device: the callback reports the local link's state and no request is
 * made (owner decision 2026-08-23, recorded in the plan's permission inventory).
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
                    // No ConnectivityManager at all is not a device this app runs on, but the
                    // getter is nullable and reporting "offline" would lock every write on a
                    // phone that is very probably online.
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
                // The set is tracked by hand because onLost fires per network: a phone dropping
                // Wi-Fi while mobile data stays up would otherwise report itself offline.
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
