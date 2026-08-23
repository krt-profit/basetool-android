/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Operation
import de.greluc.krt.profit.basetool.android.core.data.OperationDetail
import de.greluc.krt.profit.basetool.android.core.data.OperationMissionResult
import de.greluc.krt.profit.basetool.android.core.data.OperationOverview
import de.greluc.krt.profit.basetool.android.core.data.OperationPayout
import de.greluc.krt.profit.basetool.android.core.data.OperationPayouts
import de.greluc.krt.profit.basetool.android.core.data.OperationQuery
import de.greluc.krt.profit.basetool.android.core.data.OperationRollup
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * What the Operationen list and detail actually render.
 *
 * German is pinned: it is the primary bundle, and the copy rules ("Einsätze", never "Missionen")
 * are asserted against it.
 *
 * **A real phone's size is pinned too.** Robolectric's default display is 320×470 dp — smaller than
 * any device this app supports — and both of these screens carry a segment, a search field and a
 * chip row above their content. At the default size the content below that chrome is off-screen, so
 * `assertIsDisplayed` fails on rows a member would plainly see. Asserting at 411×891 dp tests the
 * layout the design was drawn for rather than one no member has.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class OperationsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun operation(
        id: String,
        name: String,
        status: OperationStatus = OperationStatus.ACTIVE,
        description: String? = null,
    ) = Operation(id = id, name = name, status = status, rawStatus = status.name, description = description)

    /**
     * Renders the list in [state].
     *
     * @param state what to draw.
     * @param opened receives the id of a tapped row.
     * @param segmentTaps records a tap on the Einsätze half of the segment.
     */
    private fun showList(
        state: OperationsState,
        opened: MutableList<String> = mutableListOf(),
        segmentTaps: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                OperationsScreen(
                    state = state,
                    onSearchChanged = {},
                    onStatusToggled = {},
                    onResetFilters = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenOperation = { opened.add(it) },
                    onOpenMissions = { segmentTaps.add(Unit) },
                )
            }
        }
    }

    /**
     * Renders the detail in [state].
     *
     * @param state what to draw.
     * @param opened receives the id of a tapped Einsatz row.
     */
    private fun showDetail(
        state: OperationDetailState,
        opened: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                OperationDetailScreen(
                    state = state,
                    onRefresh = {},
                    onOpenMission = { opened.add(it) },
                )
            }
        }
    }

    private fun overview(
        payouts: List<OperationPayout> = emptyList(),
        missions: List<OperationMissionResult> = emptyList(),
        truncated: Boolean = false,
        preliminary: Boolean? = false,
    ) = OperationOverview(
        detail =
            OperationDetail(
                id = "o1",
                name = "Operation Rotschild",
                status = OperationStatus.ACTIVE,
                rawStatus = "ACTIVE",
                description = null,
                payoutPreliminary = preliminary,
            ),
        rollup = OperationRollup(total = "74700.0000", truncated = truncated, missions = missions),
        payouts = OperationPayouts(totalDonations = "4150.0000", rows = payouts),
    )

    @Test
    fun `running and finished Operationen are separate groups`() {
        showList(
            OperationsState(
                operations =
                    listOf(
                        operation("o1", "Operation Rotschild"),
                        operation("o2", "Operation Eisvogel", status = OperationStatus.COMPLETED),
                    ),
                total = 2,
                phase = OperationsPhase.Ready,
            ),
        )

        // The design system uppercases a section title for display, so the assertion has to be
        // case-insensitive rather than pinned to the resource string.
        compose.onNodeWithText("Laufend", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Operation Rotschild").assertIsDisplayed()
        compose.onNodeWithText("Operation Eisvogel").assertIsDisplayed()
        compose.onNodeWithTag(OPERATIONS_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `tapping a row opens that Operation and no other`() {
        val opened = mutableListOf<String>()
        showList(
            OperationsState(
                operations = listOf(operation("o1", "Operation Rotschild"), operation("o2", "Operation Eisvogel")),
                total = 2,
                phase = OperationsPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("Operation Eisvogel").performClick()

        assertEquals(listOf("o2"), opened)
    }

    @Test
    fun `the segment offers the Einsatz list`() {
        showList(OperationsState(phase = OperationsPhase.Ready))

        compose.onNodeWithTag(LIST_SEGMENT_TAG).assertIsDisplayed()
    }

    @Test
    fun `an unfiltered empty list says no Operation exists`() {
        showList(OperationsState(phase = OperationsPhase.Ready))

        compose.onNodeWithText("Keine Operationen").assertIsDisplayed()
    }

    @Test
    fun `a filtered empty list says the filters matched nothing`() {
        // Showing "no Operation exists" for a filtered miss would tell a member the org is idle
        // when it is merely their own filter.
        showList(
            OperationsState(
                query = OperationQuery(text = "zzz"),
                searchText = "zzz",
                phase = OperationsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Nichts gefunden").assertIsDisplayed()
    }

    @Test
    fun `a failed list offers a retry rather than an empty state`() {
        showList(OperationsState(phase = OperationsPhase.Failed(ApiError.Network(IOException("offline")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the detail states its counts and its rolled-up net`() {
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview =
                    overview(
                        payouts = listOf(payoutRow("u1", "Rhea")),
                        missions = listOf(OperationMissionResult("m1", "Vertikaler Abbau", "86400.0000")),
                    ),
                phase = OperationDetailPhase.Ready,
            ),
        )

        compose.onNodeWithText("1 Einsatz · 1 Teilnehmer").assertIsDisplayed()
        // Grouped and stripped of the padding zeros a numeric(_,4) column carries.
        compose.onNodeWithText("74.700").assertIsDisplayed()
        compose.onNodeWithText("86.400").assertIsDisplayed()
    }

    @Test
    fun `the roll-up share is what a donating member earned, not the nought they receive`() {
        // The server zeroes shareAmount for a donating participant and moves the figure to
        // donatedAmount. Reading the first row's share printed "0" against members who had earned
        // as much as everyone else — found on a device, on an Operation whose first row donates.
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview =
                    overview(
                        payouts =
                            listOf(
                                payoutRow("p1", "Dorn", donating = true, share = "0.0000", donated = "4150.0000"),
                                payoutRow("p2", "Vex"),
                            ),
                    ),
                phase = OperationDetailPhase.Ready,
            ),
        )

        // The label is uppercased by the key-value row. Two nodes read 4.150: the donations total
        // and the share — reading the donor's zeroed shareAmount would leave only the first.
        compose.onNodeWithText("ANTEIL (2)").assertIsDisplayed()
        compose.onAllNodesWithText("4.150").assertCountEquals(2)
    }

    @Test
    fun `unequal shares are stated as the range they span`() {
        // The pool is split by how long each member took part, so one figure is only true when
        // attendance was equal. Naming both ends says what a single number could not.
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview =
                    overview(
                        payouts =
                            listOf(
                                payoutRow("p1", "Dorn", share = "4150.0000"),
                                payoutRow("p2", "Vex", share = "2075.0000"),
                            ),
                    ),
                phase = OperationDetailPhase.Ready,
            ),
        )

        compose.onNodeWithText("ANTEIL (2)").assertIsDisplayed()
        compose.onNodeWithText("2.075 – 4.150").assertIsDisplayed()
    }

    @Test
    fun `a truncated roll-up says so`() {
        // ADR-0104 in the main repo: a capped list may never look complete.
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview =
                    overview(
                        missions = listOf(OperationMissionResult("m1", "Vertikaler Abbau", "1.0")),
                        truncated = true,
                    ),
                phase = OperationDetailPhase.Ready,
            ),
        )

        compose.onNodeWithText(
            "Der Server hat die Einsatzliste gekürzt. Das Netto oben umfasst trotzdem alle Einsätze.",
        )
            .assertIsDisplayed()
    }

    @Test
    fun `a preliminary payout is flagged`() {
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview = overview(preliminary = true),
                phase = OperationDetailPhase.Ready,
            ),
        )

        compose.onNodeWithText(
            "Vorläufig: mindestens ein Einsatz ist noch nicht abgeschlossen, " +
                "die Beträge können sich noch verschieben.",
        ).assertIsDisplayed()
    }

    @Test
    fun `an uncomputed preliminary flag claims nothing`() {
        // `null` means the server did not compute it. Rendering the warning anyway would put a
        // caveat on figures that may well be final.
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview = overview(preliminary = null),
                phase = OperationDetailPhase.Ready,
            ),
        )

        compose.onNodeWithText("Finanz-Rollup", ignoreCase = true).assertIsDisplayed()
        compose.onAllNodesWithText(
            "Vorläufig: mindestens ein Einsatz ist noch nicht abgeschlossen, " +
                "die Beträge können sich noch verschieben.",
        ).assertCountEquals(0)
    }

    @Test
    fun `your own payout row is the one shown as yours`() {
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview = overview(payouts = listOf(payoutRow("u1", "Rhea"), payoutRow("u2", "Dorn"))),
                phase = OperationDetailPhase.Ready,
                myUserId = "u2",
            ),
        )

        compose.onNodeWithText("Dein Anteil", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Dorn").assertIsDisplayed()
    }

    @Test
    fun `a caller who took part in nothing is told so, and only when that is known`() {
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview = overview(payouts = listOf(payoutRow("u1", "Rhea"))),
                phase = OperationDetailPhase.Ready,
                myUserId = "u9",
            ),
        )

        compose.onNodeWithText("Du bist an dieser Operation nicht beteiligt.").assertIsDisplayed()
    }

    @Test
    fun `a refused Operation is worded as a refusal, not as an outage`() {
        showDetail(
            OperationDetailState(
                operationId = "o1",
                phase = OperationDetailPhase.Failed(ApiError.Forbidden()),
            ),
        )

        compose.onNodeWithText("Access Denied").assertIsDisplayed()
        compose.onNodeWithText("Diese Operation ist für dich nicht einsehbar.").assertIsDisplayed()
    }

    @Test
    fun `tapping an Einsatz row opens that Einsatz`() {
        val opened = mutableListOf<String>()
        showDetail(
            OperationDetailState(
                operationId = "o1",
                overview =
                    overview(
                        missions =
                            listOf(
                                OperationMissionResult("m1", "Vertikaler Abbau", "1.0"),
                                OperationMissionResult("m2", "Konvoi-Eskorte", "2.0"),
                            ),
                    ),
                phase = OperationDetailPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("Konvoi-Eskorte").performClick()

        assertEquals(listOf("m2"), opened)
    }

    private fun payoutRow(
        id: String,
        name: String,
        donating: Boolean = false,
        share: String? = "4150.0000",
        donated: String? = null,
    ) = OperationPayout(
        participantId = id,
        participantName = name,
        donating = donating,
        share = share,
        donated = donated,
        payout = "4129.2500",
        paidOut = false,
    )
}
