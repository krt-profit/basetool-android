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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.AppVersionSource
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the update wall. */
const val UPDATE_GATE_TAG: String = "update-gate"

/** Test handle for its call to action. */
const val UPDATE_GATE_CTA_TAG: String = "update-gate-cta"

/** Whether this build may still run. */
sealed interface UpdateGateState {
    /** The policy has not been read yet. The app runs — see the gate's KDoc. */
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
 * Reads the served-version policy once and decides whether this build may run
 * (REQ-APP-UI-004, server REQ-API-010).
 *
 * **Fails open, in three separate ways**, because every one of them would otherwise turn an
 * ordinary problem into an app nobody can use:
 *
 * - a **failed read** leaves the state `Unknown`, and `Unknown` runs the app. A member on a train
 *   must not be walled off because the policy request timed out.
 * - a **zero floor** allows everything, which is what an unconfigured server answers.
 * - the read happens **once**, not on a loop: a wall that appears mid-session over work in
 *   progress is worse than one that waits for the next start, and the floor does not move often
 *   enough to justify polling.
 *
 * @property source where the policy comes from
 * @property versionCode this build's own `versionCode`
 */
class UpdateGateViewModel(
    private val source: AppVersionSource,
    private val versionCode: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow<UpdateGateState>(UpdateGateState.Unknown)

    /** What the gate draws. */
    val state: StateFlow<UpdateGateState> = mutableState.asStateFlow()

    private var started = false

    /** Reads the policy, once per process. */
    fun start() {
        if (started) {
            return
        }
        started = true
        viewModelScope.launch {
            when (val result = source.versionPolicy()) {
                is ApiResult.Success -> {
                    val policy = result.value
                    mutableState.value =
                        if (policy.allows(versionCode)) {
                            UpdateGateState.Allowed
                        } else {
                            UpdateGateState.Blocked(policy.releasesUrl)
                        }
                }

                is ApiResult.Failure -> {
                    // Deliberately not an error state. There is no screen for "we could not check
                    // whether you may run", and inventing one would stop a member from working
                    // over a request that failed for reasons that have nothing to do with them.
                    KrtLog.w(LOG_TAG) { "the version policy could not be read: ${result.error}" }
                    mutableState.value = UpdateGateState.Allowed
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. */
        const val LOG_TAG = "app-version"
    }
}

/**
 * Stands outside every other gate and renders [content] unless this build is refused.
 *
 * **Outermost on purpose**, ahead of the lock and the session. The endpoint is anonymous for
 * exactly this reason (server REQ-API-010): when the breaking change is in the auth flow, the old
 * build cannot sign in, and a wall placed behind the session gate would never appear for the one
 * case it exists for — leaving the member with an authentication error that blames their
 * credentials.
 *
 * Nothing is wiped. Chapter 14 is explicit that cached data survives an update wall, so this
 * composes over the app rather than signing anybody out.
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

    LaunchedEffect(Unit) { viewModel.start() }

    when (val current = state) {
        // Unknown runs the app: see the ViewModel's KDoc. A spinner here would hold every start
        // hostage to one request, on a screen the member cannot do anything about.
        is UpdateGateState.Unknown, is UpdateGateState.Allowed -> {
            content()
        }

        is UpdateGateState.Blocked -> {
            // Back exits rather than dismissing: there is nothing behind this screen, and a back
            // press that did nothing would read as a frozen app.
            BackHandler(enabled = true) { onExit() }
            UpdateRequiredScreen(onOpenReleases = { onOpenReleases(current.releasesUrl) })
        }
    }
}

/**
 * The non-dismissible „Update erforderlich" screen of design chapter 14.
 *
 * The call to action points at the **release page**, not a store listing — distribution is GitHub
 * Releases plus Obtainium (plan Q1), so the chapter's store button has nothing to open. Recorded as
 * a deviation in `docs/specs/ui.md`.
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
