/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncEvent
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Subscribes a screen to its live-sync rooms for as long as its ViewModel lives (REQ-APP-SYNC-002).
 *
 * The screen must refresh in place: no spinner, no scroll reset, no emptied list. The subscription
 * ends with [viewModelScope].
 *
 * @param liveSync the bridge, or `null` when the screen was built without one; a screen must work
 *   without live sync.
 * @param topics the rooms this screen cares about.
 * @param onChanged what to re-read, given the sections that moved. Called on the main scope.
 * @return the collector, so a caller that needs to resubscribe can cancel it.
 */
fun ViewModel.observeLiveSync(
    liveSync: LiveSyncSource?,
    topics: Set<LiveSyncTopic>,
    onChanged: (Set<String>) -> Unit,
): Job? {
    if (liveSync == null || topics.isEmpty()) {
        return null
    }
    return viewModelScope.launch {
        liveSync.observe(topics).collect { event ->
            if (event is LiveSyncEvent.Changed) {
                onChanged(event.sections)
            }
        }
    }
}

/**
 * Announces a change the member just made, fire-and-forget on [viewModelScope].
 *
 * Never reports a failure, since the write it follows has already succeeded.
 *
 * @param liveSync the bridge, or `null` when the screen was built without one.
 * @param topic the room that changed.
 * @param sections the regions that changed.
 */
fun ViewModel.publishLiveSync(
    liveSync: LiveSyncSource?,
    topic: LiveSyncTopic,
    vararg sections: String,
) {
    if (liveSync == null || sections.isEmpty()) {
        return
    }
    viewModelScope.launch { liveSync.publish(topic, sections.toSet()) }
}

/**
 * One of the member's own live-sync rooms, such as their hangar or their blueprints
 * (REQ-APP-SYNC-006).
 *
 * The room is named by the member's id, which is read once; until it arrives nothing is joined and
 * [announce] does nothing. A change that arrives here is only re-read, never announced again
 * (REQ-APP-SYNC-004).
 *
 * @property scope where the subscription and the announcements run; the screen's view-model scope.
 * @property liveSync the bridge, or `null` when the screen was built without one.
 * @property section the one section the room carries.
 * @param identity reads the member's own id, or `null` when the screen was built without one.
 * @param room names the room for an id, e.g. [LiveSyncTopic.hangar].
 * @param onChanged what to re-read when somebody else changed the room — another device, the web
 *   or a connected application; `null` for a screen that only announces.
 */
class OwnLiveSyncRoom(
    private val scope: CoroutineScope,
    private val liveSync: LiveSyncSource?,
    identity: IdentitySource?,
    room: (String) -> LiveSyncTopic,
    private val section: String,
    onChanged: (() -> Unit)? = null,
) {
    @Volatile
    private var topic: LiveSyncTopic? = null

    init {
        val bridge = liveSync
        if (bridge != null && identity != null) {
            scope.launch {
                val id = identity.myUserId()
                if (id is ApiResult.Success) {
                    val own = room(id.value)
                    topic = own
                    if (onChanged != null) {
                        bridge.observe(setOf(own)).collect { event ->
                            if (event is LiveSyncEvent.Changed && section in event.sections) {
                                onChanged()
                            }
                        }
                    }
                }
            }
        }
    }

    /** Tells the member's other screens and browser tabs that this screen just wrote here. */
    fun announce() {
        val own = topic ?: return
        val bridge = liveSync ?: return
        scope.launch { bridge.publish(own, setOf(section)) }
    }
}
