/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.TermsDocument
import de.greluc.krt.profit.basetool.android.core.data.TermsSource
import de.greluc.krt.profit.basetool.android.core.data.TermsStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The broker that holds refused calls behind one overlay, and the overlay's view model (ADR-0025,
 * REQ-APP-AUTH-016).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReconsentTest {
    private val dispatcher = StandardTestDispatcher()

    private val document =
        TermsDocument(
            version = "d1g3st",
            title = "Nutzungsbedingungen",
            intro = "Einleitung",
            sections = emptyList(),
            lastUpdated = "Stand 26.09.2026",
        )

    /**
     * Terms endpoints with scripted answers.
     *
     * @property acceptance what the acceptance answers.
     * @property documents what the document read answers.
     */
    private inner class FakeTerms(
        var acceptance: ApiResult<TermsStatus> = ApiResult.Success(TermsStatus(accepted = true, version = "d1g3st")),
        var documents: ApiResult<TermsDocument> = ApiResult.Success(document),
    ) : TermsSource {
        var accepted = 0

        override suspend fun status() = ApiResult.Success(TermsStatus(accepted = false, version = null))

        override suspend fun document() = documents

        override suspend fun accept(): ApiResult<TermsStatus> {
            accepted += 1
            return acceptance
        }
    }

    /** A connection that the test switches. */
    private class Switchable : Connectivity {
        val state = MutableStateFlow(true)
        override val online = state
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Before the first-run gate has cleared nobody can answer, so a refusal is not held. */
    @Test
    fun `an unarmed broker returns the refusal at once`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker()

            assertFalse(broker.awaitConsent())
            assertFalse(broker.required.value)
        }

    @Test
    fun `parallel refusals share one overlay and are all released by consent`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker().apply { arm() }

            val first = async { broker.awaitConsent() }
            val second = async { broker.awaitConsent() }
            advanceUntilIdle()
            assertTrue(broker.required.value)

            broker.consented()

            assertTrue(first.await())
            assertTrue(second.await())
            assertFalse(broker.required.value)
        }

    @Test
    fun `signing out releases the waiting calls with their refusal`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker().apply { arm() }
            val waiting = async { broker.awaitConsent() }
            advanceUntilIdle()

            broker.declined()

            assertFalse(waiting.await())
        }

    @Test
    fun `disarming releases the waiting calls and stops holding new ones`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker().apply { arm() }
            val waiting = async { broker.awaitConsent() }
            advanceUntilIdle()

            broker.disarm()

            assertFalse(waiting.await())
            assertFalse(broker.awaitConsent())
        }

    @Test
    fun `a refusal opens the overlay with the wording and confirming releases the call`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker()
            val terms = FakeTerms()
            val model = ReconsentViewModel(broker, terms, Switchable())
            model.arm()
            val waiting = async { broker.awaitConsent() }
            advanceUntilIdle()

            assertTrue(model.state.value.open)
            assertEquals(document, model.state.value.document)

            model.confirm()
            advanceUntilIdle()

            assertEquals(1, terms.accepted)
            assertTrue("the refused call goes out again", waiting.await())
            assertFalse(model.state.value.open)
        }

    /** A failed confirmation keeps the overlay and the call waiting, with the error line. */
    @Test
    fun `a failed confirmation keeps the overlay open`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker()
            val terms = FakeTerms(acceptance = ApiResult.Failure(ApiError.Server(status = 500, problem = null)))
            val model = ReconsentViewModel(broker, terms, Switchable())
            model.arm()
            val waiting = async { broker.awaitConsent() }
            advanceUntilIdle()

            model.confirm()
            advanceUntilIdle()

            assertTrue(model.state.value.open)
            assertEquals(R.string.terms_error, model.state.value.errorRes)
            assertFalse(model.state.value.accepting)
            assertFalse(waiting.isCompleted)
            broker.disarm()
        }

    @Test
    fun `offline the confirmation is not sent`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker()
            val terms = FakeTerms()
            val connection = Switchable()
            val model = ReconsentViewModel(broker, terms, connection)
            model.arm()
            async { broker.awaitConsent() }
            advanceUntilIdle()

            connection.state.value = false
            advanceUntilIdle()
            model.confirm()
            advanceUntilIdle()

            assertTrue(model.state.value.offline)
            assertEquals(0, terms.accepted)
            broker.disarm()
        }

    @Test
    fun `wording that could not be read is fetched again on reconnect`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker()
            val terms = FakeTerms(documents = ApiResult.Failure(ApiError.Network(java.io.IOException("down"))))
            val connection = Switchable()
            val model = ReconsentViewModel(broker, terms, connection)
            model.arm()
            async { broker.awaitConsent() }
            advanceUntilIdle()
            assertEquals(R.string.reconsent_load_offline, model.state.value.errorRes)
            assertNull(model.state.value.document)

            terms.documents = ApiResult.Success(document)
            connection.state.value = false
            advanceUntilIdle()
            connection.state.value = true
            advanceUntilIdle()

            assertEquals(document, model.state.value.document)
            assertNull(model.state.value.errorRes)
            broker.disarm()
        }

    @Test
    fun `signing out from the overlay keeps the refusal`() =
        runTest(dispatcher) {
            val broker = ReconsentBroker()
            val model = ReconsentViewModel(broker, FakeTerms(), Switchable())
            model.arm()
            val waiting = async { broker.awaitConsent() }
            advanceUntilIdle()

            model.decline()
            advanceUntilIdle()

            assertFalse(waiting.await())
            assertFalse(model.state.value.open)
        }
}
