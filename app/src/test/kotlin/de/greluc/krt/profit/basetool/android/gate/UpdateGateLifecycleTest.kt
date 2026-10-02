/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.AppVersionPolicy
import de.greluc.krt.profit.basetool.android.core.data.AppVersionSource
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The gate reads the policy on every resume of its lifecycle, at most once per interval, and draws
 * the wall the read decides (REQ-APP-UI-004, REQ-APP-API-010).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateGateLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = TestTimeSource()
    private val owner = FakeLifecycleOwner()
    private val source = ScriptedSource(FLOOR_BELOW_BUILD)

    private companion object {
        const val THIS_BUILD = 12
        const val FLOOR_BELOW_BUILD = 8
        const val FLOOR_ABOVE_BUILD = 20
        const val CONTENT = "app content"
        const val INTERVAL_SECONDS = 60
    }

    /** A lifecycle the test moves by hand. */
    private class FakeLifecycleOwner : LifecycleOwner {
        val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle get() = registry
    }

    /**
     * Serves a policy with a floor the test changes, and counts the reads.
     *
     * @property floor the floor the next read reports.
     */
    private class ScriptedSource(
        var floor: Int,
    ) : AppVersionSource {
        var reads = 0

        override suspend fun versionPolicy(): ApiResult<AppVersionPolicy> {
            reads++
            return ApiResult.Success(
                AppVersionPolicy(
                    minimumVersionCode = floor,
                    latestVersionCode = floor,
                    releasesUrl = "https://example.invalid",
                ),
            )
        }
    }

    /** Composes the gate over a line of content under the fake lifecycle. */
    private fun render() {
        val model = UpdateGateViewModel(source = source, versionCode = THIS_BUILD, timeSource = clock)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                KrtTheme {
                    UpdateGate(viewModel = model, onOpenReleases = {}, onExit = {}) { Text(CONTENT) }
                }
            }
        }
    }

    /**
     * Moves the fake lifecycle to [state] and lets the gate react.
     *
     * @param state the target state.
     */
    private fun moveTo(state: Lifecycle.State) {
        compose.runOnIdle { owner.registry.currentState = state }
        compose.waitForIdle()
    }

    @Test
    fun `the first resume reads the policy`() {
        render()
        moveTo(Lifecycle.State.RESUMED)

        assertEquals(1, source.reads)
        compose.onNodeWithText(CONTENT).assertExists()
    }

    @Test
    fun `a pause and resume inside the interval reads nothing more`() {
        render()
        moveTo(Lifecycle.State.RESUMED)

        moveTo(Lifecycle.State.STARTED)
        moveTo(Lifecycle.State.RESUMED)
        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)

        assertEquals(1, source.reads)
    }

    @Test
    fun `a resume after the interval reads again and draws the wall`() {
        render()
        moveTo(Lifecycle.State.RESUMED)
        moveTo(Lifecycle.State.CREATED)

        source.floor = FLOOR_ABOVE_BUILD
        clock += INTERVAL_SECONDS.seconds
        moveTo(Lifecycle.State.RESUMED)

        assertEquals(2, source.reads)
        compose.onNodeWithTag(UPDATE_GATE_TAG).assertExists()
        compose.onNodeWithText(CONTENT).assertDoesNotExist()
    }
}
