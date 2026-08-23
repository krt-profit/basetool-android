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
 * Subscribes a screen to its live-sync rooms for as long as its ViewModel lives
 * (REQ-APP-SYNC-002).
 *
 * The one thing every call site would otherwise get subtly wrong is what to do with the sections:
 * a screen refreshes **in place** — no spinner, no scroll reset, no emptied list while the answer
 * is in flight. A peer's change arriving as a full loading state would be worse than not syncing
 * at all, because the member did not ask for anything and would watch their screen blank itself.
 *
 * The subscription follows [viewModelScope], so it ends when the screen does and nothing has to be
 * unregistered by hand.
 *
 * @param liveSync the bridge, or `null` when the screen was built without one — a test double or a
 *   preview. A screen must keep working without live sync; it is an enhancement, never a
 *   dependency.
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
 * Announces a change the member just made, without letting it affect them.
 *
 * Fire-and-forget by construction: it launches on [viewModelScope] and never reports. The write
 * this follows has already succeeded, so a screen that surfaced a failure here would be telling the
 * member their save went wrong because somebody else's refresh did.
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
