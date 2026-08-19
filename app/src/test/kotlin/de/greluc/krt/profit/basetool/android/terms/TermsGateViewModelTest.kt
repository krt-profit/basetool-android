/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import de.greluc.krt.profit.basetool.android.core.data.TermsClause
import de.greluc.krt.profit.basetool.android.core.data.TermsDocument
import de.greluc.krt.profit.basetool.android.core.data.TermsSection
import de.greluc.krt.profit.basetool.android.core.data.TermsSource
import de.greluc.krt.profit.basetool.android.core.data.TermsStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The consent gate's sequencing, which is where informed consent is either preserved or lost.
 *
 * The case that matters most has no visible symptom: if the document read fails and the gate still
 * renders, the member is asked to agree to a blank page. That is not a display bug — it is consent
 * recorded for a text nobody was shown, so it is asserted explicitly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TermsGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** A minimal document; this test is about sequencing, not rendering. */
    private val document =
        TermsDocument(
            version = "v1",
            title = "Nutzungsbedingungen",
            intro = "Intro",
            sections = listOf(TermsSection("1. Geltungsbereich", listOf(TermsClause("Klausel", emptyList())))),
            lastUpdated = "Stand: 05.08.2026",
        )

    /**
     * A source that answers from fixed results and counts what was asked.
     *
     * @property statusResult what `status()` returns
     * @property documentResult what `document()` returns
     * @property acceptResult what `accept()` returns
     */
    private class ScriptedSource(
        private val statusResult: ApiResult<TermsStatus>,
        private val documentResult: ApiResult<TermsDocument>? = null,
        private val acceptResult: ApiResult<TermsStatus>? = null,
    ) : TermsSource {
        var documentReads = 0
            private set

        override suspend fun status(): ApiResult<TermsStatus> = statusResult

        override suspend fun document(): ApiResult<TermsDocument> {
            documentReads++
            return requireNotNull(documentResult) { "document() was not expected to be called" }
        }

        override suspend fun accept(): ApiResult<TermsStatus> =
            requireNotNull(acceptResult) { "accept() was not expected to be called" }
    }

    /**
     * Installs the test dispatcher as `Dispatchers.Main`, which `viewModelScope` uses.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * Restores the real main dispatcher.
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A member who already accepted goes straight through.
     */
    @Test
    fun `recorded consent clears the gate`() =
        runTest(dispatcher) {
            val viewModel = TermsGateViewModel(ScriptedSource(ApiResult.Success(TermsStatus(true, "v1"))))

            viewModel.start()
            advanceUntilIdle()

            assertEquals(TermsGateState.Cleared, viewModel.state.value)
        }

    /**
     * The document is not fetched for somebody who already agreed.
     *
     * Otherwise every app start would pay for a document download only to be told the member
     * consented months ago.
     */
    @Test
    fun `the document is not fetched when consent is on record`() =
        runTest(dispatcher) {
            val source = ScriptedSource(ApiResult.Success(TermsStatus(true, "v1")))
            val viewModel = TermsGateViewModel(source)

            viewModel.start()
            advanceUntilIdle()

            assertEquals(0, source.documentReads)
        }

    /**
     * Missing consent brings up the wording.
     */
    @Test
    fun `missing consent shows the wording`() =
        runTest(dispatcher) {
            val viewModel =
                TermsGateViewModel(
                    ScriptedSource(
                        statusResult = ApiResult.Success(TermsStatus(false, "v1")),
                        documentResult = ApiResult.Success(document),
                    ),
                )

            viewModel.start()
            advanceUntilIdle()

            assertEquals(TermsGateState.Required(document, accepting = false, errorRes = null), viewModel.state.value)
        }

    /**
     * A document that cannot be read is a hard stop, never an empty gate.
     *
     * The whole point: asking somebody to agree to a blank page is not asking for consent.
     */
    @Test
    fun `an unreadable document never renders the gate`() =
        runTest(dispatcher) {
            val failure = ApiError.Server(status = 500, problem = null)
            val viewModel =
                TermsGateViewModel(
                    ScriptedSource(
                        statusResult = ApiResult.Success(TermsStatus(false, "v1")),
                        documentResult = ApiResult.Failure(failure),
                    ),
                )

            viewModel.start()
            advanceUntilIdle()

            assertEquals(TermsGateState.Unavailable(failure), viewModel.state.value)
        }

    /**
     * An unreadable status is reported rather than assumed either way.
     */
    @Test
    fun `an unreadable status is reported`() =
        runTest(dispatcher) {
            val failure = ApiError.Network(IOException("offline"))
            val viewModel = TermsGateViewModel(ScriptedSource(ApiResult.Failure(failure)))

            viewModel.start()
            advanceUntilIdle()

            assertEquals(TermsGateState.Unavailable(failure), viewModel.state.value)
        }

    /**
     * A recorded acceptance opens the gate.
     */
    @Test
    fun `accepting clears the gate`() =
        runTest(dispatcher) {
            val viewModel =
                TermsGateViewModel(
                    ScriptedSource(
                        statusResult = ApiResult.Success(TermsStatus(false, "v1")),
                        documentResult = ApiResult.Success(document),
                        acceptResult = ApiResult.Success(TermsStatus(true, "v1")),
                    ),
                )
            viewModel.start()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            assertEquals(TermsGateState.Cleared, viewModel.state.value)
        }

    /**
     * A failed acceptance keeps the wording on screen with a message.
     *
     * The text the member just read is exactly what they need in front of them to try again — an
     * error page would take it away.
     */
    @Test
    fun `a failed acceptance keeps the document and reports why`() =
        runTest(dispatcher) {
            val viewModel =
                TermsGateViewModel(
                    ScriptedSource(
                        statusResult = ApiResult.Success(TermsStatus(false, "v1")),
                        documentResult = ApiResult.Success(document),
                        acceptResult = ApiResult.Failure(ApiError.Network(IOException("offline"))),
                    ),
                )
            viewModel.start()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue("expected the gate to stay up, got $state", state is TermsGateState.Required)
            state as TermsGateState.Required
            assertEquals(document, state.document)
            assertTrue("expected a message", state.errorRes != null)
            assertTrue("expected the button released again", !state.accepting)
        }

    /**
     * A 200 that still reports no consent does not open the gate.
     *
     * Trusting the HTTP status over the payload here would wave a member through without their
     * consent on record — and the very next API call would bounce them straight back.
     */
    @Test
    fun `a success that reports no consent keeps the gate closed`() =
        runTest(dispatcher) {
            val viewModel =
                TermsGateViewModel(
                    ScriptedSource(
                        statusResult = ApiResult.Success(TermsStatus(false, "v1")),
                        documentResult = ApiResult.Success(document),
                        acceptResult = ApiResult.Success(TermsStatus(false, "v1")),
                    ),
                )
            viewModel.start()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            assertTrue(viewModel.state.value is TermsGateState.Required)
        }

    /**
     * Accepting before the document arrived does nothing.
     *
     * Guards the ordering rather than the UI: the button only exists on the rendered gate, but a
     * view model that acted on it from any state would be one recomposition away from recording
     * consent for a document that was never displayed.
     */
    @Test
    fun `accept does nothing before the wording is on screen`() =
        runTest(dispatcher) {
            val viewModel = TermsGateViewModel(ScriptedSource(ApiResult.Success(TermsStatus(true, "v1"))))
            viewModel.start()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            assertEquals(TermsGateState.Cleared, viewModel.state.value)
        }
}
