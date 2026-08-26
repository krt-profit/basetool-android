/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.Announcement
import de.greluc.krt.profit.basetool.android.core.data.AnnouncementSource
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration

/** How far the dashboard's Einsatz band has got. */
sealed interface DashboardPhase {
    /** The first read is on its way. */
    data object Loading : DashboardPhase

    /** It arrived; the band may still be empty, which is a result. */
    data object Ready : DashboardPhase

    /** It did not. The dashboard keeps its other parts and says so in the band. */
    data object Failed : DashboardPhase
}

/**
 * Everything the dashboard draws that it has to fetch.
 *
 * The greeting is not here: the member's name comes from the ID token and the org unit from the
 * switcher, both of which the shell already holds. Copying them into this state would give the
 * screen two sources for one fact.
 *
 * @property announcementRead whether the caller has marked the current notice read. Defaults to
 *   `true` so the band never flashes „UNGELESEN" during the frames before the answer lands —
 *   claiming something is unread and taking it back is worse than being a beat late to say so.
 * @property announcement the org-wide notice, or `null` when there is none — an ordinary answer
 * @property missions the Einsätze starting within the next seven days
 * @property phase how far that read has got
 * @property refreshing whether a pull-to-refresh is running over content already on screen
 */
data class DashboardState(
    val announcement: Announcement? = null,
    val announcementRead: Boolean = true,
    val missions: List<Mission> = emptyList(),
    val phase: DashboardPhase = DashboardPhase.Loading,
    val refreshing: Boolean = false,
)

/**
 * Drives the dashboard.
 *
 * **The announcement and the Einsätze fail independently.** They are unrelated reads behind
 * unrelated permissions, and one outage must not blank the other: a member who cannot reach the
 * announcement still needs to know what is starting tonight.
 *
 * The seven-day window is computed against the **server's** clock, like the Einsatz list's "past"
 * bound. A phone running a few minutes fast would otherwise drop an Einsatz that is about to start
 * — the one a member most needs to see.
 *
 * @property missions where the Einsätze come from
 * @property announcements where the notice comes from
 * @property clock the server-corrected clock that bounds the window
 */
class DashboardViewModel(
    private val missions: MissionSource,
    private val announcements: AnnouncementSource,
    private val clock: ServerClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DashboardState())

    /** What the screen draws. */
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    /** Loads both parts. Safe to call more than once. */
    fun load() {
        reload(keepContent = false)
    }

    /** Re-reads both parts, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
    }

    /**
     * Reads the announcement and the Einsatz band.
     *
     * @param keepContent whether what is on screen survives until the answers arrive.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = DashboardPhase.Loading)
        }
        loadAnnouncement()
        loadMissions()
    }

    /**
     * Reads whether the caller has marked this notice read.
     *
     * A failure leaves it counted as read. The unread marker is an invitation to act, and showing
     * one because a request failed asks a member to clear something the app is not sure about.
     *
     * @param id the notice currently on screen.
     */
    private suspend fun readState(id: String) {
        when (val result = announcements.lastRead()) {
            is ApiResult.Success -> {
                mutableState.value = mutableState.value.copy(announcementRead = result.value == id)
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the announcement read state could not be read: ${result.error}" }
            }
        }
    }

    /**
     * Marks the notice on screen read.
     *
     * Optimistic: the marker disappears on the tap and the request follows. A refusal puts it
     * back, because a band that says „gelesen" while the server disagrees will say „UNGELESEN"
     * again at the next start, and the member will have learnt that the button does not stick.
     */
    fun onAnnouncementRead() {
        val notice = mutableState.value.announcement ?: return
        if (mutableState.value.announcementRead) {
            return
        }
        mutableState.value = mutableState.value.copy(announcementRead = true)
        viewModelScope.launch {
            // Only the refusal needs handling: the state already says read, which is the whole
            // point of marking it optimistically.
            val result = announcements.markRead(notice.id)
            if (result is ApiResult.Failure) {
                KrtLog.w(LOG_TAG) { "the announcement could not be marked read: ${result.error}" }
                mutableState.value = mutableState.value.copy(announcementRead = false)
            }
        }
    }

    /** Reads the announcement; a failure hides the band rather than failing the screen. */
    private fun loadAnnouncement() {
        viewModelScope.launch {
            when (val result = announcements.current()) {
                is ApiResult.Success -> {
                    val notice = result.value
                    mutableState.value = mutableState.value.copy(announcement = notice)
                    if (notice != null) {
                        // Sequential, and only when there is something to be unread about: the
                        // read flag costs a second request and answers a question that does not
                        // arise on a dashboard with no notice on it.
                        readState(notice.id)
                    }
                }

                is ApiResult.Failure -> {
                    // No banner is the same rendering as "nothing announced", and that is the
                    // honest one: the app does not know of an announcement. An error strip over a
                    // working dashboard would be louder than the thing it is reporting.
                    KrtLog.w(LOG_TAG) { "announcement could not be read: ${result.error}" }
                    mutableState.value = mutableState.value.copy(announcement = null)
                }
            }
        }
    }

    /** Reads the Einsätze of the next seven days. */
    private fun loadMissions() {
        viewModelScope.launch {
            val now = clock.now()
            val query = MissionQuery(from = now, until = now.plus(WINDOW))
            when (val result = missions.search(query, page = 0, pageSize = BAND_SIZE)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            missions = result.value.missions,
                            phase = DashboardPhase.Ready,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "dashboard Einsätze could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(phase = DashboardPhase.Failed, refreshing = false)
                }
            }
        }
    }

    private companion object {
        /** The window the design names: "Einsätze der nächsten 7 Tage". */
        val WINDOW: Duration = Duration.ofDays(7)

        /**
         * How many Einsätze the band holds.
         *
         * The dashboard is a summary, not the list — the Einsatz tab is one tap away and shows all
         * of them with their filters. Five is what fits above the fold beside the other bands.
         */
        const val BAND_SIZE = 5

        /** Log subsystem. */
        const val LOG_TAG = "dashboard"
    }
}
