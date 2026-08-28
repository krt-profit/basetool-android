/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.InventoryAllocation
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.LocalCaller
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * What the Lager tree renders.
 *
 * The two states nobody looks at while developing: a group that failed to open, and a group with a
 * material the server sent without an id — which cannot be opened at all and must not pretend
 * otherwise.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class InventoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun group(
        id: String? = "m1",
        name: String = "Quantainium",
        amount: String? = "1250.5",
    ) = InventoryGroup(
        materialId = id,
        name = name,
        unit = "SCU",
        amount = amount,
        quality = "880",
        maxQuality = "940",
    )

    private fun entry(note: String? = null) =
        InventoryEntry(
            id = "e1",
            materialName = "Quantainium",
            materialId = "m1",
            unit = "SCU",
            locationName = "ARC-L1",
            locationId = "l1",
            holder = "Rhea",
            holderId = "u1",
            amount = "12,5",
            quality = "880",
            personal = false,
            note = note,
            version = 5L,
        )

    /** A member with no grants at all — the caller every gated control is drawn for. */
    private fun plainMember() = Identity(userId = "someone-else", logistician = false)

    private fun stack(
        location: String = "ARC-L1",
        amount: String = "1000",
        quality: String = "880",
    ) = InventoryStack(
        holder = "Rhea",
        holderId = "u-rhea",
        location = location,
        personal = false,
        amount = amount,
        quality = quality,
        entryCount = 1,
    )

    /**
     * Renders the tree.
     *
     * @param state what to draw.
     * @param toggled receives the material id of a tapped group.
     * @param stacksToggled receives a tapped stack.
     * @param booked records that the booking action was taken.
     * @param bookedOut receives an entry whose booking action was taken.
     */
    private fun show(
        state: InventoryState,
        toggled: MutableList<String> = mutableListOf(),
        stacksToggled: MutableList<InventoryStack> = mutableListOf(),
        booked: MutableList<Unit> = mutableListOf(),
        bookedOut: MutableList<InventoryEntry> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                InventoryScreen(
                    state = state,
                    onToggleGroup = { toggled.add(it) },
                    onToggleStack = { _, stack -> stacksToggled.add(stack) },
                    onToggleBranch = { _, _ -> },
                    onBookIn = { booked.add(Unit) },
                    onBookOut = { bookedOut.add(it) },
                    onAllocate = {},
                    selection = emptySet(),
                    onToggleSelected = {},
                    denials = DenialState(),
                    onWithStockOnlyChanged = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                )
            }
        }
    }

    /**
     * A tree with one group open on one stack.
     *
     * @param entries how far that stack's entries have got, or `null` while it is closed.
     * @return the state.
     */
    private fun readyWithStack(entries: EntriesPhase? = null) =
        InventoryState(
            groups = listOf(group()),
            total = 1,
            phase = InventoryPhase.Ready,
            opened = mapOf("m1" to StackPhase.Ready(listOf(stack()))),
            openedStacks =
                entries?.let { mapOf(stackKey("m1", stack()) to it) } ?: emptyMap(),
        )

    @Test
    fun `a group states its material, its amount and its unit`() {
        show(InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready))

        compose.onNodeWithText("Quantainium").assertIsDisplayed()
        compose.onNodeWithText("1.250,5").assertIsDisplayed()
        compose.onNodeWithText("SCU").assertIsDisplayed()
        compose.onNodeWithTag(INVENTORY_TREE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a closed group shows none of its holdings`() {
        show(InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready))

        compose.onAllNodesWithText("Rhea", substring = true).assertCountEquals(0)
    }

    @Test
    fun `tapping a group reports its material id`() {
        val toggled = mutableListOf<String>()
        show(
            InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready),
            toggled = toggled,
        )

        compose.onNodeWithText("Quantainium").performClick()

        assertEquals(listOf("m1"), toggled)
    }

    @Test
    fun `a group the server sent without an id offers no tap`() {
        // It still holds something and is therefore shown, but it cannot be asked for — and a tap
        // that does nothing is how a member concludes the app is broken.
        val toggled = mutableListOf<String>()
        show(
            InventoryState(
                groups = listOf(group(id = null, name = "Namenlos")),
                total = 1,
                phase = InventoryPhase.Ready,
            ),
            toggled = toggled,
        )

        compose.onNodeWithText("Namenlos").performClick()

        assertEquals(emptyList<String>(), toggled)
    }

    @Test
    fun `an opened group shows its holdings`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Ready(listOf(stack()))),
            ),
        )

        // Two levels now, not one: the holder heads their own stacks and a stack names its place.
        // Merged into "Rhea · ARC-L1" they read as one thing, and a member holding one material at
        // two places got two unrelated rows with no total (REQ-APP-INV-017, artboard 1).
        compose.onNodeWithText("Rhea").assertIsDisplayed()
        // „Ort · geteilt", as artboard 1 writes it. The pool is said on every row, not only on the
        // personal ones — a member could not otherwise tell shared stock from an unlabelled row.
        compose.onNodeWithText("ARC-L1 · geteilt").assertIsDisplayed()
        // The entry count is the app's own addition and is said only where it tells the member
        // something. One entry per stack is what a stack looks like by default.
        compose.onAllNodesWithText("1 Eintrag").assertCountEquals(0)
        compose.onNodeWithText("Q 880").assertIsDisplayed()
    }

    @Test
    fun `a stack of one entry is one row, not two`() {
        // Every artboard of chapter 09 draws this case as a single row — place, pool, quality,
        // amount — because its fixture gives every stack one entry. The app used to draw a header
        // that looked like that row and a bare amount beneath it.
        show(readyWithStack(entries = EntriesPhase.Ready(listOf(entry()))))

        compose.onAllNodesWithText("ARC-L1 · geteilt").assertCountEquals(1)
        compose.onAllNodesWithText("Q 880").assertCountEquals(1)
        // The row is the entry: its amount, and its two writes.
        compose.onAllNodesWithText("12,5").assertCountEquals(1)
        compose.onNodeWithContentDescription("Buchen").assertIsDisplayed()
    }

    @Test
    fun `a stack of several entries keeps them as rows of their own`() {
        show(
            readyWithStack(
                entries =
                    EntriesPhase.Ready(
                        listOf(entry().copy(id = "e1"), entry().copy(id = "e2", amount = "7")),
                    ),
            ),
        )

        // Two entries, so the header stays a header and the entries are rows beneath it — each
        // saying which of the two it is, since nothing else about them differs.
        compose.onAllNodesWithText("ARC-L1 · geteilt").assertCountEquals(1)
        compose.onAllNodesWithText("12,5").assertCountEquals(1)
        compose.onAllNodesWithText("7").assertCountEquals(1)
        compose.onNodeWithText("Buchung 1/2").assertIsDisplayed()
        compose.onNodeWithText("Buchung 2/2").assertIsDisplayed()
    }

    @Test
    fun `an entry says what of it is already promised`() {
        // Artboard 1 puts the splits on the row: „#A-1042 · 200" for an Auftrag, the Einsatz beside
        // it. The model has carried both since the Zuordnung sheet was built and the tree drew
        // neither, so the one thing that turns a quantity into a plan was the one thing missing.
        show(
            readyWithStack(
                entries =
                    EntriesPhase.Ready(
                        listOf(
                            entry().copy(
                                jobOrderAllocations =
                                    listOf(InventoryAllocation("a1", "#A-1042", null, "200")),
                                missionAllocations =
                                    listOf(InventoryAllocation("m9", "Vertikaler Abbau", null, "80")),
                            ),
                        ),
                    ),
            ),
        )

        // ignoreCase: KrtChip uppercases, as every chip in the design system does. The Auftrag
        // chip matched either way, which is exactly how a case-sensitive assertion hides that the
        // other one is uppercased too.
        compose.onNodeWithText("#A-1042 · 200", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Vertikaler Abbau · 80", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a stack of several entries says how many`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Ready(listOf(stack().copy(entryCount = 4)))),
            ),
        )

        compose.onNodeWithText("4 Einträge").assertIsDisplayed()
    }

    @Test
    fun `a personal stack says so in the same place`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Ready(listOf(stack().copy(personal = true)))),
            ),
        )

        compose.onNodeWithText("ARC-L1 · persönlich").assertIsDisplayed()
    }

    @Test
    fun `a holder's stacks are headed by their total`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened =
                    mapOf(
                        "m1" to
                            StackPhase.Ready(
                                listOf(
                                    stack(location = "ARC-L1", amount = "442", quality = "874"),
                                    stack(location = "Lorville", amount = "200", quality = "901"),
                                ),
                            ),
                    ),
            ),
        )

        // One heading for the two places, carrying what they add up to. The two stacks keep their
        // own figures beneath it.
        compose.onNodeWithText("Rhea").assertIsDisplayed()
        compose.onNodeWithText("642").assertIsDisplayed()
        compose.onNodeWithText("442").assertIsDisplayed()
        compose.onNodeWithText("200").assertIsDisplayed()
    }

    @Test
    fun `a total that cannot be added is not shown at all`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened =
                    mapOf(
                        "m1" to
                            StackPhase.Ready(
                                listOf(
                                    stack(location = "ARC-L1", amount = "442"),
                                    stack(location = "Lorville", amount = "viele"),
                                ),
                            ),
                    ),
            ),
        )

        // A subtotal quietly missing one of its parts is worse than no subtotal: it would read as
        // 442 and be wrong by however much "viele" is.
        compose.onNodeWithText("Rhea").assertIsDisplayed()
        compose.onAllNodesWithText("442").assertCountEquals(1)
    }

    @Test
    fun `a group that failed to open stays open and says so`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Failed),
            ),
        )

        compose.onNodeWithText("Die Bestände dieser Gruppe konnten nicht geladen werden.")
            .assertIsDisplayed()
    }

    @Test
    fun `an emptied group says so rather than looking unopened`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Ready(emptyList())),
            ),
        )

        compose.onNodeWithText("In dieser Gruppe liegt nichts mehr.").assertIsDisplayed()
    }

    @Test
    fun `an empty Lager and a filtered-out page are different sentences`() {
        show(InventoryState(phase = InventoryPhase.Ready))
        compose.onNodeWithText("Leeres Lager").assertIsDisplayed()
    }

    @Test
    fun `a closed stack shows none of its entries`() {
        show(readyWithStack())

        compose.onAllNodesWithText("12,5").assertCountEquals(0)
    }

    @Test
    fun `tapping a stack reports it`() {
        val tapped = mutableListOf<InventoryStack>()
        show(readyWithStack(), stacksToggled = tapped)

        compose.onNodeWithText("ARC-L1 · geteilt").performClick()

        assertEquals(listOf(stack()), tapped)
    }

    @Test
    fun `an opened stack lists its entries with their notes`() {
        show(readyWithStack(entries = EntriesPhase.Ready(listOf(entry(note = "Reserviert")))))

        compose.onAllNodesWithText("12,5").assertCountEquals(1)
        compose.onNodeWithText("Reserviert").assertIsDisplayed()
        // „nie wiederholte Header" (artboard 1): the stack is keyed by (holder, place, quality), so
        // its entries share all three and only the stack says them. The group says none of it, and
        // the entry row carries what actually varies — its amount, its note, its allocations.
        compose.onAllNodesWithText("Q 880").assertCountEquals(1)
        compose.onAllNodesWithText("ARC-L1 · geteilt").assertCountEquals(1)
    }

    @Test
    fun `an entry offers the booking form`() {
        val booked = mutableListOf<InventoryEntry>()
        show(
            readyWithStack(entries = EntriesPhase.Ready(listOf(entry()))),
            bookedOut = booked,
        )

        // By its label, not its text: the action is an icon square (artboard 11) and „Buchen" is
        // now what a screen reader hears rather than what the row prints.
        compose.onNodeWithContentDescription("Buchen").performClick()

        assertEquals(listOf(entry()), booked)
    }

    @Test
    fun `an emptied stack says so rather than looking closed`() {
        show(readyWithStack(entries = EntriesPhase.Ready(emptyList())))

        compose.onNodeWithText("Keine Einträge.").assertIsDisplayed()
    }

    @Test
    fun `a stack whose entries failed stays open and says so`() {
        show(readyWithStack(entries = EntriesPhase.Failed))

        compose.onNodeWithText("Die Bestände dieser Gruppe konnten nicht geladen werden.")
            .assertIsDisplayed()
    }

    @Test
    fun `the screen's own action books material in`() {
        val booked = mutableListOf<Unit>()
        show(InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready), booked = booked)

        compose.onNodeWithTag(INVENTORY_BOOK_TAG).performClick()

        assertEquals(1, booked.size)
    }

    @Test
    fun `offline the tree says so and offers no booking`() {
        show(
            readyWithStack(entries = EntriesPhase.Ready(listOf(entry()))).copy(online = false),
        )

        compose.onNodeWithText("Kein Netz — Ändern ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(INVENTORY_BOOK_TAG).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Buchen").assertIsNotEnabled()
    }

    @Test
    fun `a failed tree offers a retry`() {
        show(InventoryState(phase = InventoryPhase.Failed(ApiError.Network(IOException("x")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    /**
     * The locked action answers instead of doing nothing.
     *
     * `enabled = false` would satisfy "does not write" just as well and is exactly what the design
     * forbids (ADR-0011): a control that cannot be tapped cannot say which role is missing. So the
     * assertion is deliberately about the *refusal*, not about the write being skipped.
     */
    @Test
    fun `a caller without the Logistiker role gets an answer, not a dead button`() {
        val denials = DenialState()
        val allocated = mutableListOf<InventoryEntry>()
        compose.setContent {
            CompositionLocalProvider(LocalCaller provides plainMember()) {
                KrtTheme {
                    InventoryScreen(
                        state = readyWithStack(EntriesPhase.Ready(listOf(entry()))),
                        onToggleGroup = {},
                        onToggleStack = { _, _ -> },
                        onBookIn = {},
                        onBookOut = {},
                        onAllocate = { allocated.add(it) },
                        onToggleBranch = { _, _ -> },
                        selection = emptySet(),
                        onToggleSelected = {},
                        denials = denials,
                        onWithStockOnlyChanged = {},
                        onRefresh = {},
                        onRetryNow = {},
                        onLoadMore = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Zuordnen").performClick()

        assertEquals(emptyList<InventoryEntry>(), allocated)
        assertEquals("Dafür brauchst du die Rolle Logistiker.", denials.current?.title)
    }
}
