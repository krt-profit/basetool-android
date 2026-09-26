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
 * Reads the served-version policy once and decides whether this build may run (REQ-APP-UI-004).
 *
 * Fails open: a failed read leaves the state `Unknown`, which runs the app; a zero floor allows every
 * build; and the policy is read once per process, never polled.
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
 * The outermost gate, ahead of the lock and the session: renders [content] unless this build is
 * refused.
 *
 * The policy endpoint is anonymous, so a build that can no longer sign in still sees the wall. No
 * data is wiped and nobody is signed out.
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
