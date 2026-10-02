/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.AppVersionPolicy
import de.greluc.krt.profit.basetool.android.core.data.AppVersionSource
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.UpdateSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the update wall. */
const val UPDATE_GATE_TAG: String = "update-gate"

/** Test handle for its call to action. */
const val UPDATE_GATE_CTA_TAG: String = "update-gate-cta"

/** Whether this build may still run. */
sealed interface UpdateGateState {
    /** No read has answered yet. The app runs — see the gate's KDoc. */
    data object Unknown : UpdateGateState

    /** The server serves this build. */
    data object Allowed : UpdateGateState

    /**
     * It does not.
     *
     * @property releasesUrl where the member gets the new build.
     */
    data class Blocked(
        val releasesUrl: String,
    ) : UpdateGateState
}

/**
 * Decides whether this build may run, from the served-version policy (REQ-APP-UI-004, REQ-APP-API-010).
 *
 * The policy is read when the app comes to the foreground and after a `404` on an API path, never
 * more than once per [minimumInterval]; `APP_UPDATE_REQUIRED` walls the build off at once and forces
 * a read for the release link. Fails open: a failed first read runs the app, a failed later read
 * keeps the last verdict, and a zero floor allows every build.
 *
 * @property source where the policy comes from
 * @property versionCode this build's own `versionCode`
 * @property timeSource the monotonic clock the interval is measured on
 * @property minimumInterval the shortest time between two reads that are not forced
 * @param signals the transport's update signals, collected for as long as the gate lives
 */
class UpdateGateViewModel(
    private val source: AppVersionSource,
    private val versionCode: Int,
    signals: Flow<UpdateSignal> = emptyFlow(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val minimumInterval: Duration = MINIMUM_INTERVAL,
) : ViewModel() {
    private val mutableState = MutableStateFlow<UpdateGateState>(UpdateGateState.Unknown)

    /** What the gate draws. */
    val state: StateFlow<UpdateGateState> = mutableState.asStateFlow()

    private var lastRead: TimeMark? = null
    private var reading: Job? = null
    private var retiredByServer = false
    private var releasesUrl = AppVersionPolicy.DEFAULT_RELEASES_URL

    init {
        viewModelScope.launch {
            signals.collect { signal ->
                when (signal) {
                    UpdateSignal.NOT_FOUND -> read(force = false)
                    UpdateSignal.UPDATE_REQUIRED -> retire()
                }
            }
        }
    }

    /** Reads the policy because the app came to the foreground, unless it was read within [minimumInterval]. */
    fun onForeground() {
        read(force = false)
    }

    /**
     * Walls the build off because the server retired a path it called, and reads the policy for the
     * release link; no later read lifts this wall.
     */
    private fun retire() {
        retiredByServer = true
        val url = releasesUrl
        mutableState.update { UpdateGateState.Blocked(url) }
        read(force = true)
    }

    /**
     * Starts one policy read unless one is running or, when not [force]d, the last began within
     * [minimumInterval].
     *
     * @param force whether to ignore the interval.
     */
    private fun read(force: Boolean) {
        if (reading?.isActive == true) {
            return
        }
        val last = lastRead
        if (!force && last != null && last.elapsedNow() < minimumInterval) {
            return
        }
        lastRead = timeSource.markNow()
        reading = viewModelScope.launch { apply(source.versionPolicy()) }
    }

    /**
     * Turns one read into a verdict.
     *
     * @param result the read's outcome.
     */
    private fun apply(result: ApiResult<AppVersionPolicy>) {
        when (result) {
            is ApiResult.Success -> {
                val policy = result.value
                releasesUrl = policy.releasesUrl
                val walled = retiredByServer || !policy.allows(versionCode)
                mutableState.update {
                    if (walled) UpdateGateState.Blocked(policy.releasesUrl) else UpdateGateState.Allowed
                }
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the version policy could not be read: ${result.error}" }
                mutableState.update { current ->
                    if (current is UpdateGateState.Unknown) UpdateGateState.Allowed else current
                }
            }
        }
    }

    /** Defaults. */
    companion object {
        /** How long a read stands before a resume or a `404` may trigger the next one. */
        val MINIMUM_INTERVAL: Duration = 60.seconds

        /** Log subsystem. */
        private const val LOG_TAG = "app-version"
    }
}

/**
 * The outermost gate, ahead of the lock and the session: renders [content] unless this build is
 * refused.
 *
 * The policy endpoint is anonymous, so a build that can no longer sign in still sees the wall. No
 * data is wiped and nobody is signed out. Every resume of the activity asks [viewModel] to re-read.
 *
 * @param viewModel holds the verdict.
 * @param onOpenReleases opens the release page in a browser.
 * @param onExit leaves the app; the design gives back no other destination.
 * @param content the rest of the app, composed unless the build is refused.
 */
@Composable
fun UpdateGate(
    viewModel: UpdateGateViewModel,
    onOpenReleases: (String) -> Unit,
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LifecycleResumeEffect(viewModel) {
        viewModel.onForeground()
        onPauseOrDispose { }
    }

    when (val current = state) {
        is UpdateGateState.Unknown, is UpdateGateState.Allowed -> {
            content()
        }

        is UpdateGateState.Blocked -> {
            BackHandler(enabled = true) { onExit() }
            UpdateRequiredScreen(onOpenReleases = { onOpenReleases(current.releasesUrl) })
        }
    }
}

/**
 * The non-dismissible „Update erforderlich" screen.
 *
 * Its call to action opens the GitHub release page, not a store listing.
 *
 * @param onOpenReleases opens the release page.
 * @param modifier layout modifier.
 */
@Composable
fun UpdateRequiredScreen(
    onOpenReleases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s24)
                .testTag(UPDATE_GATE_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12, Alignment.CenterVertically),
    ) {
        KrtIcon(
            id = DesignR.drawable.ic_krt_download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.update_required_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.update_required_message),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        KrtCtaButton(
            text = stringResource(R.string.update_required_cta),
            onClick = onOpenReleases,
            modifier = Modifier.fillMaxWidth().testTag(UPDATE_GATE_CTA_TAG),
        )
    }
}
