/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.BookInDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutKind
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which of the booking form's modes is showing (design ch. 09, Frame 2). */
enum class BookingMode {
    /** Material arrives. */
    IN,

    /** Material leaves — discarded, handed over or sold. */
    OUT,

    /** Nothing moves — only the entry's note changes. */
    NOTE,
}

/**
 * What the booking form holds.
 *
 * One state for every mode rather than one each: the member switches between them with a segment
 * and must not lose the amount they already typed, which is the field the moving modes share.
 *
 * @property mode which mode is showing.
 * @property entry the entry being booked out or rebooked; `null` in [BookingMode.IN].
 * @property amount how much, as typed.
 * @property material the material picked, for booking in.
 * @property materialQuery what is in the material search.
 * @property materials what that search returned.
 * @property place the place picked.
 * @property placeQuery what is in the place search.
 * @property places what that search returned.
 * @property quality the quality as typed, for booking in.
 * @property outKind what happens to the material on the way out.
 * @property member the member a transfer hands it to.
 * @property memberQuery what is in the member search.
 * @property members what that search returned.
 * @property terminal the terminal a sale happens at.
 * @property terminals the terminals that buy this material.
 * @property sellAmount what the sale fetched, as typed.
 * @property note the entry's note, as typed.
 * @property online whether a booking can be sent at all.
 * @property saving whether a booking is in flight.
 * @property error what the last attempt returned, or `null`.
 */
data class BookingState(
    val mode: BookingMode = BookingMode.IN,
    val entry: InventoryEntry? = null,
    val amount: String = "",
    val material: MaterialOption? = null,
    val materialQuery: String = "",
    val materials: List<MaterialOption> = emptyList(),
    val place: LocationOption? = null,
    val placeQuery: String = "",
    val places: List<LocationOption> = emptyList(),
    val quality: String = "",
    val outKind: BookOutKind = BookOutKind.DISCARD,
    val member: MemberOption? = null,
    val memberQuery: String = "",
    val members: List<MemberOption> = emptyList(),
    val terminal: TerminalOption? = null,
    val terminals: List<TerminalOption> = emptyList(),
    val sellAmount: String = "",
    val note: String = "",
    val online: Boolean = true,
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /**
     * Whether the form holds something the server will accept.
     *
     * Each mode has its own minimum, and the transfer and the sale each need the thing that makes
     * them what they are — a recipient and a terminal. Offering a save without them would send a
     * booking the server refuses, which reads to the member as the app being unreliable rather
     * than as a field they missed.
     */
    val submittable: Boolean
        get() =
            when (mode) {
                // A note moves nothing, so it needs no amount — and an emptied note is a
                // deliberate edit, not an incomplete form.
                BookingMode.NOTE -> entry != null && note != entry.note.orEmpty()

                BookingMode.IN -> positiveAmount && material != null && place != null

                BookingMode.OUT -> positiveAmount && entry != null && outKindSatisfied
            }

    /** Whether the amount is a quantity and not zero. */
    private val positiveAmount: Boolean
        get() = amount.toDoubleOrNull()?.let { it > 0 } == true

    /** Whether the chosen way out has the field that makes it what it is. */
    private val outKindSatisfied: Boolean
        get() =
            when (outKind) {
                BookOutKind.DISCARD -> true
                BookOutKind.TRANSFER -> transferMoves
                BookOutKind.SELL -> terminal != null
            }

    /**
     * Whether the transfer would actually move the material.
     *
     * The server refuses a transfer that changes neither the holder nor the place, and it is right
     * to: nothing would happen. Picking the entry's own holder is the easy way into that refusal —
     * the picker offers them like anyone else — so the form has to know the rule too.
     */
    val transferMoves: Boolean
        get() =
            (member != null && member.id != entry?.holderId) ||
                (place != null && place.id != entry?.locationId)
}

/**
 * Drives the Lager's booking form.
 *
 * Its own view model rather than more state on the tree's: the form has four pickers and three
 * modes, and folding that into the screen that also pages a tree would make both harder to read
 * than either.
 *
 * @property source the Lager.
 * @property connectivity whether there is a network at all.
 */
class BookingViewModel(
    private val source: InventorySource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow<BookingState?>(null)

    /** What the form draws, or `null` when it is closed. */
    val state: StateFlow<BookingState?> = mutableState.asStateFlow()

    private val onlineState = MutableStateFlow(true)

    /** Whether a booking can be sent at all. */
    val online: StateFlow<Boolean> = onlineState.asStateFlow()

    private var searchJob: Job? = null
    private var saved: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                onlineState.value = online
                update { it.copy(online = online) }
            }
        }
    }

    /**
     * Opens the form for booking material in.
     *
     * @param onSaved what to run once a booking lands, so the tree can re-read itself.
     */
    fun openBookIn(onSaved: () -> Unit) {
        saved = onSaved
        mutableState.value = BookingState(mode = BookingMode.IN, online = onlineState.value)
    }

    /**
     * Opens the form on one entry.
     *
     * @param entry the entry.
     * @param mode which of the entry's modes the form opens on.
     * @param onSaved what to run once a booking lands.
     */
    fun openForEntry(
        entry: InventoryEntry,
        mode: BookingMode,
        onSaved: () -> Unit,
    ) {
        saved = onSaved
        mutableState.value =
            BookingState(
                mode = mode,
                entry = entry,
                note = entry.note.orEmpty(),
                online = onlineState.value,
            )
    }

    /**
     * Answers the conflict dialog's „Neu laden": closes the form and makes the tree re-read itself.
     *
     * It reuses the `onSaved` hook the caller already supplies for a landed booking, because the
     * tree needs exactly the same thing after a refused one: the row it is showing is stale either
     * way. Reloading does **not** retry the write -- see [ConflictModal] for why a retry against a
     * newer version would defeat the lock it just ran into.
     */
    fun onConflictReload() {
        saved?.invoke()
        onDismissed()
    }

    /** Closes the form, discarding what was typed. */
    fun onDismissed() {
        searchJob?.cancel()
        mutableState.value = null
    }

    /**
     * Switches mode.
     *
     * The amount survives: it is the one field every mode shares, and a member who typed 12 before
     * realising they meant "aus" should not type it again.
     *
     * @param mode the new mode.
     */
    fun onModeChanged(mode: BookingMode) {
        update { it.copy(mode = mode, error = null) }
    }

    /**
     * Updates the form.
     *
     * @param transform how it changes.
     */
    private fun update(transform: (BookingState) -> BookingState) {
        mutableState.value = mutableState.value?.let(transform)
    }

    /**
     * Sets the amount.
     *
     * @param value what the member typed, unparsed.
     */
    fun onAmountChanged(value: String) = update { it.copy(amount = value.filterQuantity(), error = null) }

    /**
     * Sets the quality.
     *
     * @param value what the member typed, unparsed.
     */
    fun onQualityChanged(value: String) =
        update { it.copy(quality = value.filter(Char::isDigit).take(QUALITY_DIGITS), error = null) }

    /**
     * Sets what a sale fetched.
     *
     * @param value what the member typed, unparsed.
     */
    fun onSellAmountChanged(value: String) = update { it.copy(sellAmount = value.filterQuantity(), error = null) }

    /**
     * Sets the entry's note.
     *
     * @param value what the member typed.
     */
    fun onNoteChanged(value: String) = update { it.copy(note = value.take(NOTE_LENGTH), error = null) }

    /**
     * Sets what happens to the material on the way out.
     *
     * @param kind discard, transfer or sell.
     */
    fun onOutKindChanged(kind: BookOutKind) {
        update { it.copy(outKind = kind, error = null) }
        if (kind == BookOutKind.SELL) {
            loadTerminals()
        }
    }

    /**
     * Searches materials.
     *
     * @param query what the member typed.
     */
    fun onMaterialQueryChanged(query: String) {
        update { it.copy(materialQuery = query) }
        search(query) { results -> update { it.copy(materials = results) } }
    }

    /**
     * Picks a material.
     *
     * @param material the material.
     */
    fun onMaterialChosen(material: MaterialOption) =
        update { it.copy(material = material, materials = emptyList(), error = null) }

    /**
     * Searches places.
     *
     * @param query what the member typed.
     */
    fun onPlaceQueryChanged(query: String) {
        update { it.copy(placeQuery = query) }
        searchPlaces(query)
    }

    /**
     * Picks a place.
     *
     * @param place the place.
     */
    fun onPlaceChosen(place: LocationOption) =
        update { it.copy(place = place, places = emptyList(), error = null) }

    /**
     * Searches members.
     *
     * @param query what the member typed.
     */
    fun onMemberQueryChanged(query: String) {
        update { it.copy(memberQuery = query) }
        searchMembers(query)
    }

    /**
     * Picks a member.
     *
     * @param member the recipient.
     */
    fun onMemberChosen(member: MemberOption) =
        update { it.copy(member = member, members = emptyList(), error = null) }

    /**
     * Picks a terminal.
     *
     * @param terminal where the sale happens.
     */
    fun onTerminalChosen(terminal: TerminalOption) =
        update { it.copy(terminal = terminal, error = null) }

    /** Sends whatever the form holds. */
    fun onSave() {
        val current = mutableState.value ?: return
        if (!current.submittable || !onlineState.value) {
            return
        }
        update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = send(current)) {
                is ApiResult.Success -> {
                    mutableState.value = null
                    saved?.invoke()
                }

                // The form keeps every field: a conflict or a refusal is not a reason to make the
                // member type an amount and re-pick a material.
                is ApiResult.Failure -> {
                    update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Sends what the form holds as the booking its mode stands for.
     *
     * @param current the form.
     * @return what the server answered.
     */
    private suspend fun send(current: BookingState): ApiResult<Unit> =
        when (current.mode) {
            BookingMode.IN -> {
                source.bookIn(
                    BookInDraft(
                        materialId = current.material?.id.orEmpty(),
                        locationId = current.place?.id.orEmpty(),
                        amount = current.amount,
                        quality = current.quality.toIntOrNull(),
                    ),
                )
            }

            BookingMode.OUT -> {
                source.bookOut(
                    id = current.entry?.id.orEmpty(),
                    version = current.entry?.version,
                    draft =
                        BookOutDraft(
                            amount = current.amount,
                            kind = current.outKind,
                            targetUserId = current.member?.id,
                            targetLocationId = current.place?.id,
                            terminal = current.terminal?.name,
                            sellAmount = current.sellAmount.takeIf { it.isNotBlank() },
                        ),
                )
            }

            BookingMode.NOTE -> {
                source.updateNote(
                    id = current.entry?.id.orEmpty(),
                    version = current.entry?.version,
                    note = current.note.takeIf { it.isNotBlank() },
                )
            }
        }

    /**
     * Runs a debounced material search.
     *
     * @param query what the member typed.
     * @param onResults what to do with the answer.
     */
    private fun search(
        query: String,
        onResults: (List<MaterialOption>) -> Unit,
    ) {
        searchJob?.cancel()
        if (query.trim().length < MIN_SEARCH) {
            onResults(emptyList())
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                (source.materials(query) as? ApiResult.Success)?.let { onResults(it.value) }
            }
    }

    /**
     * Runs a debounced place search.
     *
     * @param query what the member typed.
     */
    private fun searchPlaces(query: String) {
        searchJob?.cancel()
        if (query.trim().length < MIN_SEARCH) {
            update { it.copy(places = emptyList()) }
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                (source.locations(query) as? ApiResult.Success)?.let { result ->
                    update { it.copy(places = result.value) }
                }
            }
    }

    /**
     * Runs a debounced member search.
     *
     * @param query what the member typed.
     */
    private fun searchMembers(query: String) {
        searchJob?.cancel()
        if (query.trim().length < MIN_SEARCH) {
            update { it.copy(members = emptyList()) }
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                (source.members(query) as? ApiResult.Success)?.let { result ->
                    update { it.copy(members = result.value) }
                }
            }
    }

    /** Reads the terminals that buy the entry's material. */
    private fun loadTerminals() {
        val current = mutableState.value
        val materialId = current?.material?.id ?: current?.entry?.materialId
        if (materialId == null) {
            // Without a material id there is no terminal list to offer. The sheet says so rather
            // than showing an empty list that looks like a failed read.
            return
        }
        viewModelScope.launch {
            (source.terminals(materialId) as? ApiResult.Success)?.let { result ->
                update { it.copy(terminals = result.value) }
            }
        }
    }

    private companion object {
        /** How long typing settles before a search goes out. */
        const val DEBOUNCE_MILLIS = 300L

        /** Below this a search returns most of the catalogue. */
        const val MIN_SEARCH = 2

        /** Quality runs 0–1000, so four digits is the ceiling. */
        const val QUALITY_DIGITS = 4

        /** What the server's note column takes. */
        const val NOTE_LENGTH = 500
    }
}

/**
 * Keeps only what a quantity may contain.
 *
 * Digits and one separator: the Lager books in cSCU and µSCU, so a decimal point has to survive,
 * but a member cannot type two of them into one number.
 *
 * @return the cleaned text.
 */
private fun String.filterQuantity(): String {
    val cleaned = replace(',', '.').filter { it.isDigit() || it == '.' }
    val first = cleaned.indexOf('.')
    return if (first < 0) {
        cleaned
    } else {
        cleaned.substring(0, first + 1) + cleaned.substring(first + 1).filter(Char::isDigit)
    }
}
