/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.PersonalInventoryRepository
import de.greluc.krt.profit.basetool.android.core.data.PersonalInventorySource
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalItemDraft
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the list has got. */
sealed interface PersonalInventoryPhase {
    /** The first page is on its way. */
    data object Loading : PersonalInventoryPhase

    /** A page arrived. */
    data object Ready : PersonalInventoryPhase

    /**
     * The read failed.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : PersonalInventoryPhase
}

/**
 * What the editor is doing.
 *
 * The draft lives here rather than in the composable because a 409 has to hand it back **unchanged**
 * (design ch. 14): the member's typing survives the conflict, and only the version is refreshed.
 */
sealed interface EditorState {
    /** No editor is open. */
    data object Closed : EditorState

    /**
     * The editor is open.
     *
     * @property editing the row being changed, or `null` for a new entry.
     * @property name what is in the name field.
     * @property quantity what is in the quantity field, as typed — kept as text so a half-deleted
     *   number does not snap back to zero under the member's cursor.
     * @property location the chosen place, or `null` while none is.
     * @property note what is in the note field.
     * @property saving whether a save is in flight.
     * @property error what the last save attempt returned, or `null`.
     */
    data class Open(
        val editing: PersonalItem? = null,
        val name: String = "",
        val quantity: String = "1",
        val location: PersonalLocation? = null,
        val note: String = "",
        val saving: Boolean = false,
        val error: ApiError? = null,
    ) : EditorState {
        /** Whether the three required fields are filled well enough to send. */
        val submittable: Boolean
            get() = name.isNotBlank() && quantity.toIntOrNull()?.let { it > 0 } == true && location != null
    }
}

/**
 * The place picker's own little state, inside the editor.
 *
 * @property query what the member typed.
 * @property results what came back.
 * @property searching whether a search is in flight.
 * @property capped whether the server returned a full page, so there may be more it did not send.
 */
data class LocationSearch(
    val query: String = "",
    val results: List<PersonalLocation> = emptyList(),
    val searching: Boolean = false,
    val capped: Boolean = false,
)

/**
 * Everything the screen draws.
 *
 * @property items the rows.
 * @property total how many the server says there are.
 * @property query the list's search term.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property loadingMore whether the next page is on its way.
 * @property hasMore whether another page exists.
 * @property online whether writes are possible at all.
 * @property editor the editor's state.
 * @property locations the place picker's state.
 * @property pendingDelete the row whose deletion is being confirmed, or `null`.
 * @property deleting whether a delete is in flight.
 * @property lastFailure a write failure to report once, or `null`.
 */
data class PersonalInventoryState(
    val items: List<PersonalItem> = emptyList(),
    val total: Long = 0,
    val query: String = "",
    val phase: PersonalInventoryPhase = PersonalInventoryPhase.Loading,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val online: Boolean = true,
    val editor: EditorState = EditorState.Closed,
    val locations: LocationSearch = LocationSearch(),
    val pendingDelete: PersonalItem? = null,
    val deleting: Boolean = false,
    val lastFailure: ApiError? = null,
)

/**
 * Drives "Mein Inventar".
 *
 * **Writes are disabled while the device has no network** rather than queued (design ch. 14). A
 * queued mutation carries a `version` that ages while it waits, which is precisely the write the
 * server has to refuse — so the app says so before the member types instead of after.
 *
 * @property repository the member's own stock.
 * @property connectivity whether there is a network at all.
 */
class PersonalInventoryViewModel(
    private val repository: PersonalInventorySource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PersonalInventoryState())

    /** What the screen renders. */
    val state: StateFlow<PersonalInventoryState> = mutableState.asStateFlow()

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

    /** Loads the first page, the first time the screen is opened. */
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
     * Debounced, because every keystroke would otherwise be a request; the delay is the same one
     * the Einsatz list uses, so the two screens feel alike.
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
                delay(SEARCH_DEBOUNCE_MILLIS)
                load(page = 0, keepRows = false)
            }
    }

    /** Re-reads the first page while keeping the rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepRows = true)
    }

    /** Appends the next page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is PersonalInventoryPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch { load(page = current.items.size / PAGE_SIZE, keepRows = true) }
    }

    /** Opens the editor for a new entry. */
    fun onCreate() {
        mutableState.value =
            mutableState.value.copy(editor = EditorState.Open(), locations = LocationSearch())
    }

    /**
     * Opens the editor for an existing entry.
     *
     * The place is seeded from what the row already carries, so a member who only changes the
     * quantity does not have to search for the place they already chose.
     *
     * @param item the row to change.
     */
    fun onEdit(item: PersonalItem) {
        mutableState.value =
            mutableState.value.copy(
                editor =
                    EditorState.Open(
                        editing = item,
                        name = item.name,
                        quantity = item.quantity.toString(),
                        location =
                            item.locationUexId?.let {
                                PersonalLocation(
                                    uexId = it,
                                    kind = item.locationKind,
                                    name = item.locationName.orEmpty(),
                                    system = null,
                                    parent = null,
                                )
                            },
                        note = item.note.orEmpty(),
                    ),
                locations = LocationSearch(),
            )
    }

    /** Closes the editor, discarding what was typed. */
    fun onEditorDismissed() {
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(editor = EditorState.Closed)
    }

    /**
     * Updates one editor field.
     *
     * @param transform how the open editor changes.
     */
    private fun editor(transform: (EditorState.Open) -> EditorState.Open) {
        val open = mutableState.value.editor as? EditorState.Open ?: return
        mutableState.value = mutableState.value.copy(editor = transform(open))
    }

    /**
     * Sets the name.
     *
     * @param value what the member typed.
     */
    fun onNameChanged(value: String) = editor { it.copy(name = value, error = null) }

    /**
     * Sets the quantity.
     *
     * @param value what the member typed, unparsed.
     */
    fun onQuantityChanged(value: String) =
        editor { it.copy(quantity = value.filter(Char::isDigit).take(QUANTITY_MAX_DIGITS), error = null) }

    /**
     * Steps the quantity.
     *
     * @param by how much, positive or negative.
     */
    fun onQuantityStepped(by: Int) =
        editor {
            val next = ((it.quantity.toIntOrNull() ?: 0) + by).coerceAtLeast(1)
            it.copy(quantity = next.toString(), error = null)
        }

    /**
     * Sets the note.
     *
     * @param value what the member typed.
     */
    fun onNoteChanged(value: String) = editor { it.copy(note = value, error = null) }

    /**
     * Chooses a place.
     *
     * @param location the place.
     */
    fun onLocationChosen(location: PersonalLocation) {
        editor { it.copy(location = location, error = null) }
        mutableState.value = mutableState.value.copy(locations = LocationSearch())
    }

    /**
     * Searches places.
     *
     * @param query what the member typed into the picker.
     */
    fun onLocationQueryChanged(query: String) {
        mutableState.value =
            mutableState.value.copy(locations = mutableState.value.locations.copy(query = query))
        searchJob?.cancel()
        if (query.trim().length < MIN_SEARCH_LENGTH) {
            mutableState.value =
                mutableState.value.copy(
                    locations = mutableState.value.locations.copy(results = emptyList(), capped = false),
                )
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MILLIS)
                mutableState.value =
                    mutableState.value.copy(locations = mutableState.value.locations.copy(searching = true))
                when (val result = repository.locations(query)) {
                    is ApiResult.Success -> {
                        mutableState.value =
                            mutableState.value.copy(
                                locations =
                                    mutableState.value.locations.copy(
                                        results = result.value,
                                        searching = false,
                                        // A full answer means the server stopped counting, not that
                                        // there are exactly this many places (ADR-0104).
                                        capped = result.value.size >= PersonalInventoryRepository.LOCATION_LIMIT,
                                    ),
                            )
                    }

                    is ApiResult.Failure -> {
                        mutableState.value =
                            mutableState.value.copy(
                                locations =
                                    mutableState.value.locations.copy(
                                        results = emptyList(),
                                        searching = false,
                                        capped = false,
                                    ),
                                lastFailure = result.error,
                            )
                    }
                }
            }
    }

    /** Saves what the editor holds. */
    fun onSave() {
        val open = mutableState.value.editor as? EditorState.Open
        val place = open?.location
        val quantity = open?.quantity?.toIntOrNull()
        // One guard rather than a return per condition: an editor that is closed, one that is
        // incomplete and a device that is offline all mean the same thing here — nothing to send.
        val sendable = open != null && place != null && quantity != null
        if (!sendable || !mutableState.value.online) {
            return
        }
        val draft =
            PersonalItemDraft(
                name = open.name.trim(),
                quantity = quantity,
                locationUexId = place.uexId,
                locationKind = place.kind,
                note = open.note.trim().takeIf { it.isNotEmpty() },
            )
        editor { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val editing = open.editing
            val version = editing?.version
            val result =
                if (editing != null && version != null) {
                    repository.update(editing.id, version, draft)
                } else {
                    repository.create(draft)
                }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(editor = EditorState.Closed)
                    reload(keepRows = true)
                }

                // The draft stays exactly as typed. A conflict dialog that cleared the form would
                // punish the member for someone else's edit.
                is ApiResult.Failure -> {
                    editor { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Asks before deleting.
     *
     * @param item the row.
     */
    fun onDeleteRequested(item: PersonalItem) {
        mutableState.value = mutableState.value.copy(pendingDelete = item)
    }

    /** Abandons the deletion. */
    fun onDeleteDismissed() {
        mutableState.value = mutableState.value.copy(pendingDelete = null)
    }

    /** Deletes the row the member confirmed. */
    fun onDeleteConfirmed() {
        val item = mutableState.value.pendingDelete ?: return
        if (!mutableState.value.online) {
            return
        }
        mutableState.value = mutableState.value.copy(deleting = true)
        viewModelScope.launch {
            when (val result = repository.delete(item.id)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(pendingDelete = null, deleting = false)
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

    /** Acknowledges the last write failure, so its message is shown once. */
    fun onFailureShown() {
        mutableState.value = mutableState.value.copy(lastFailure = null)
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
     * Reads one page.
     *
     * @param page the zero-based index.
     * @param keepRows whether to keep what is on screen while it runs.
     */
    private suspend fun load(
        page: Int,
        keepRows: Boolean,
    ) {
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = PersonalInventoryPhase.Loading)
        }
        when (val result = repository.page(query = mutableState.value.query, page = page)) {
            is ApiResult.Success -> {
                val current = mutableState.value
                mutableState.value =
                    current.copy(
                        items = if (page == 0) result.value.items else current.items + result.value.items,
                        total = result.value.totalElements,
                        hasMore = result.value.hasMore,
                        phase = PersonalInventoryPhase.Ready,
                        refreshing = false,
                        loadingMore = false,
                    )
            }

            is ApiResult.Failure -> {
                mutableState.value =
                    mutableState.value.copy(
                        phase = PersonalInventoryPhase.Failed(result.error),
                        refreshing = false,
                        loadingMore = false,
                    )
            }
        }
    }

    private companion object {
        /** Rows per page, mirroring the repository's default. */
        const val PAGE_SIZE = PersonalInventoryRepository.DEFAULT_PAGE_SIZE

        /** How long typing settles before a request goes out. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** Below this, a place search would return most of the catalogue. */
        const val MIN_SEARCH_LENGTH = 2

        /** A quantity longer than this is a typo, not a number. */
        const val QUANTITY_MAX_DIGITS = 7
    }
}
