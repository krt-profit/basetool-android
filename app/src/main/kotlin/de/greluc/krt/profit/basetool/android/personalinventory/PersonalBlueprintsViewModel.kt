/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintRepository
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the blueprint list has got. */
sealed interface BlueprintsPhase {
    /** The first page is on its way. */
    data object Loading : BlueprintsPhase

    /** A page arrived. */
    data object Ready : BlueprintsPhase

    /**
     * The read failed.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : BlueprintsPhase
}

/**
 * What the blueprint editor holds.
 *
 * Two shapes rather than one with a nullable everything: adding needs a product and no version,
 * changing needs a version and no product, and folding them together makes both harder to read.
 */
sealed interface BlueprintEditor {
    /** Nothing is open. */
    data object Closed : BlueprintEditor

    /**
     * The add sheet.
     *
     * @property query what the member typed into the product search.
     * @property results what came back.
     * @property searching whether a search is in flight.
     * @property capped whether the answer came back full, so there may be more.
     * @property chosen the product picked, or `null`.
     * @property note the optional note.
     * @property saving whether a save is in flight.
     * @property error what the last attempt returned, or `null`.
     */
    data class Adding(
        val query: String = "",
        val results: List<BlueprintProduct> = emptyList(),
        val searching: Boolean = false,
        val capped: Boolean = false,
        val chosen: BlueprintProduct? = null,
        val note: String = "",
        val saving: Boolean = false,
        val error: ApiError? = null,
    ) : BlueprintEditor {
        /** Whether there is something to send. */
        val submittable: Boolean get() = chosen != null && !chosen.owned
    }

    /**
     * The note sheet of an entry the member already owns.
     *
     * @property entry the row being changed.
     * @property note what is in the field.
     * @property saving whether a save is in flight.
     * @property error what the last attempt returned, or `null`.
     */
    data class Editing(
        val entry: OwnedBlueprint,
        val note: String,
        val saving: Boolean = false,
        val error: ApiError? = null,
    ) : BlueprintEditor
}

/**
 * Everything the Blueprints tab draws.
 *
 * @property items the rows.
 * @property craftability what can be built, keyed by row id; empty while it is still loading or
 *   after it failed — the chip then says nothing rather than claiming "nicht baubar".
 * @property total how many the server says there are.
 * @property query the list's search term.
 * @property withRefinery whether refining counts towards what is reachable.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property hasMore whether another page exists.
 * @property online whether writes are possible at all.
 * @property editor the editor's state.
 * @property pendingDelete the row whose removal is being confirmed, or `null`.
 * @property deleting whether a removal is in flight.
 * @property lastFailure a write failure to report once, or `null`.
 */
data class BlueprintsState(
    val items: List<OwnedBlueprint> = emptyList(),
    val craftability: Map<String, Craftability> = emptyMap(),
    val total: Long = 0,
    val query: String = "",
    val withRefinery: Boolean = false,
    val phase: BlueprintsPhase = BlueprintsPhase.Loading,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val hasMore: Boolean = false,
    val online: Boolean = true,
    val editor: BlueprintEditor = BlueprintEditor.Closed,
    val pendingDelete: OwnedBlueprint? = null,
    val deleting: Boolean = false,
    val lastFailure: ApiError? = null,
)

/**
 * Drives the Blueprints tab of "Mein Inventar".
 *
 * **Craftability is a second, independent read.** It fails on its own without taking the list with
 * it: a member who cannot see whether something is buildable can still see what they own, and a
 * chip that guessed "nicht baubar" from a failed request would be worse than no chip.
 *
 * @property repository the member's own blueprints.
 * @property connectivity whether there is a network at all.
 */
class PersonalBlueprintsViewModel(
    private val repository: PersonalBlueprintSource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BlueprintsState())

    /** What the tab renders. */
    val state: StateFlow<BlueprintsState> = mutableState.asStateFlow()

    /**
     * The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003).
     *
     * Shared rather than re-derived: the conditions under which a countdown is right are the same
     * on every screen.
     */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepRows = false) },
        )

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var loadedOnce = false

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
    }

    /** Loads the first page, the first time the tab is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /**
     * Narrows the list.
     *
     * @param query what the member typed.
     */
    fun onQueryChanged(query: String) {
        if (query == mutableState.value.query) {
            return
        }
        mutableState.value = mutableState.value.copy(query = query)
        loadedOnce = true
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                load(page = 0, keepRows = false)
            }
    }

    /** Re-reads while keeping the rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepRows = true)
    }

    /** Appends the next page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (!current.hasMore || current.phase !is BlueprintsPhase.Ready) {
            return
        }
        viewModelScope.launch { load(page = current.items.size / PAGE_SIZE, keepRows = true) }
    }

    /**
     * Switches the chip between "from what I hold" and "once I refine".
     *
     * No re-read: both answers come from the same call, which is why it asks for them together.
     *
     * @param enabled whether refining counts.
     */
    fun onRefineryChanged(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(withRefinery = enabled)
    }

    /** Opens the add sheet. */
    fun onAdd() {
        mutableState.value = mutableState.value.copy(editor = BlueprintEditor.Adding())
    }

    /**
     * Opens the note sheet of an owned entry.
     *
     * @param entry the row.
     */
    fun onEdit(entry: OwnedBlueprint) {
        mutableState.value =
            mutableState.value.copy(
                editor = BlueprintEditor.Editing(entry = entry, note = entry.note.orEmpty()),
            )
    }

    /** Closes whatever is open, discarding what was typed. */
    fun onEditorDismissed() {
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(editor = BlueprintEditor.Closed)
    }

    /**
     * Searches the catalogue.
     *
     * @param query what the member typed.
     */
    fun onProductQueryChanged(query: String) {
        val adding = mutableState.value.editor as? BlueprintEditor.Adding ?: return
        mutableState.value = mutableState.value.copy(editor = adding.copy(query = query))
        searchJob?.cancel()
        if (query.trim().length < MIN_SEARCH_LENGTH) {
            update<BlueprintEditor.Adding> { it.copy(results = emptyList(), capped = false) }
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                update<BlueprintEditor.Adding> { it.copy(searching = true) }
                when (val result = repository.products(query)) {
                    is ApiResult.Success -> {
                        update<BlueprintEditor.Adding> {
                            it.copy(
                                results = result.value,
                                searching = false,
                                capped = result.value.size >= PersonalBlueprintRepository.PRODUCT_LIMIT,
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        update<BlueprintEditor.Adding> {
                            it.copy(results = emptyList(), searching = false, capped = false)
                        }
                        mutableState.value = mutableState.value.copy(lastFailure = result.error)
                    }
                }
            }
    }

    /**
     * Chooses a product to add.
     *
     * @param product the catalogue row.
     */
    fun onProductChosen(product: BlueprintProduct) {
        update<BlueprintEditor.Adding> { it.copy(chosen = product, error = null) }
    }

    /**
     * Sets the note, in whichever sheet is open.
     *
     * @param note what the member typed.
     */
    fun onNoteChanged(note: String) {
        val next =
            when (val editor = mutableState.value.editor) {
                is BlueprintEditor.Adding -> editor.copy(note = note, error = null)
                is BlueprintEditor.Editing -> editor.copy(note = note, error = null)
                BlueprintEditor.Closed -> return
            }
        mutableState.value = mutableState.value.copy(editor = next)
    }

    /** Saves whatever the open sheet holds. */
    fun onSave() {
        if (!mutableState.value.online) {
            return
        }
        when (val editor = mutableState.value.editor) {
            is BlueprintEditor.Adding -> add(editor)
            is BlueprintEditor.Editing -> edit(editor)
            BlueprintEditor.Closed -> return
        }
    }

    /**
     * Adds the chosen product.
     *
     * @param editor the open add sheet.
     */
    private fun add(editor: BlueprintEditor.Adding) {
        val product = editor.chosen ?: return
        mutableState.value = mutableState.value.copy(editor = editor.copy(saving = true, error = null))
        viewModelScope.launch {
            when (val result = repository.add(product.productKey, editor.note.trim().takeIf { it.isNotEmpty() })) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(editor = BlueprintEditor.Closed)
                    reload(keepRows = true)
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        mutableState.value.copy(editor = editor.copy(saving = false, error = result.error))
                }
            }
        }
    }

    /**
     * Saves a changed note.
     *
     * @param editor the open note sheet.
     */
    private fun edit(editor: BlueprintEditor.Editing) {
        val version = editor.entry.version ?: return
        mutableState.value = mutableState.value.copy(editor = editor.copy(saving = true, error = null))
        viewModelScope.launch {
            val note = editor.note.trim().takeIf { it.isNotEmpty() }
            when (val result = repository.updateNote(editor.entry.id, version, note)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(editor = BlueprintEditor.Closed)
                    reload(keepRows = true)
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        mutableState.value.copy(editor = editor.copy(saving = false, error = result.error))
                }
            }
        }
    }

    /**
     * Asks before removing.
     *
     * @param entry the row.
     */
    fun onDeleteRequested(entry: OwnedBlueprint) {
        mutableState.value = mutableState.value.copy(pendingDelete = entry)
    }

    /** Abandons the removal. */
    fun onDeleteDismissed() {
        mutableState.value = mutableState.value.copy(pendingDelete = null)
    }

    /** Removes the row the member confirmed. */
    fun onDeleteConfirmed() {
        val entry = mutableState.value.pendingDelete ?: return
        if (!mutableState.value.online) {
            return
        }
        mutableState.value = mutableState.value.copy(deleting = true)
        viewModelScope.launch {
            when (val result = repository.remove(entry.id)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(pendingDelete = null, deleting = false)
                    reload(keepRows = true)
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        mutableState.value.copy(
                            pendingDelete = null,
                            deleting = false,
                            lastFailure = result.error,
                        )
                }
            }
        }
    }

    /** Acknowledges the last write failure. */
    fun onFailureShown() {
        mutableState.value = mutableState.value.copy(lastFailure = null)
    }

    /**
     * Transforms the open editor when it is of the expected shape.
     *
     * @param T the shape.
     * @param transform how it changes.
     */
    private inline fun <reified T : BlueprintEditor> update(transform: (T) -> BlueprintEditor) {
        val editor = mutableState.value.editor as? T ?: return
        mutableState.value = mutableState.value.copy(editor = transform(editor))
    }

    /**
     * Re-reads from the first page.
     *
     * @param keepRows whether the rows stay on screen while the read runs.
     */
    private fun reload(keepRows: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(page = 0, keepRows = keepRows) }
    }

    /**
     * Reads one page, and the craftability beside it.
     *
     * @param page the zero-based index.
     * @param keepRows whether to keep what is on screen while it runs.
     */
    private suspend fun load(
        page: Int,
        keepRows: Boolean,
    ) {
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = BlueprintsPhase.Loading)
        }
        when (val result = repository.page(query = mutableState.value.query, page = page)) {
            is ApiResult.Success -> {
                val current = mutableState.value
                mutableState.value =
                    current.copy(
                        items = if (page == 0) result.value.items else current.items + result.value.items,
                        total = result.value.totalElements,
                        hasMore = result.value.hasMore,
                        phase = BlueprintsPhase.Ready,
                        refreshing = false,
                    )
                loadCraftability()
                retry.onSuccess()
            }

            is ApiResult.Failure -> {
                mutableState.value =
                    mutableState.value.copy(
                        phase = BlueprintsPhase.Failed(result.error),
                        refreshing = false,
                    )
                retry.onFailure(result.error, hasContent = false)
            }
        }
    }

    /**
     * Reads what can be built.
     *
     * A failure here is deliberately silent in the list: the rows are still true, and a chip
     * absent says less than a chip that guessed.
     */
    private suspend fun loadCraftability() {
        when (val result = repository.craftability()) {
            is ApiResult.Success -> {
                mutableState.value = mutableState.value.copy(craftability = result.value)
            }

            is ApiResult.Failure -> {
                mutableState.value = mutableState.value.copy(craftability = emptyMap())
            }
        }
    }

    private companion object {
        /** Rows per page, mirroring the repository's default. */
        const val PAGE_SIZE = PersonalBlueprintRepository.DEFAULT_PAGE_SIZE

        /** How long typing settles before a request goes out. */
        const val DEBOUNCE_MILLIS = 300L

        /** Below this, a catalogue search would return most of the catalogue. */
        const val MIN_SEARCH_LENGTH = 2
    }
}
