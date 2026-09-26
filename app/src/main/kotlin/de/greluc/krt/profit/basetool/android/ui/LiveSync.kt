/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncEvent
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
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
