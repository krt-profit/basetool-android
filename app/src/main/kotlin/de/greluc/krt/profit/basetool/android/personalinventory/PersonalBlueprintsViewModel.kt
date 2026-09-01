/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BlueprintBatchResult
import de.greluc.krt.profit.basetool.android.core.data.BlueprintImportSource
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.BlueprintRecipe
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintRepository
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FieldLimits
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Log subsystem. A blueprint's owner is member data and never reaches the log. */
private const val LOG_TAG = "personal-blueprints"

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
        val chosen: List<BlueprintProduct> = emptyList(),
        val note: String = "",
        val saving: Boolean = false,
        val error: ApiError? = null,
        val outcome: BlueprintBatchResult? = null,
    ) : BlueprintEditor {
        /** Whether there is something to send. */
        val submittable: Boolean get() = chosen.isNotEmpty()

        /** How many are picked — the CTA names it („3 Blueprints übernehmen"). */
        val count: Int get() = chosen.size

        /**
         * Whether the note field applies.
         *
         * `POST /personal-blueprints/batch` carries **only** the keys, so a note typed against
         * several products would be silently dropped. With one picked the single create is used
         * and the note goes with it; with several the field is drawn locked with that reason
         * rather than removed.
         */
        val noteApplies: Boolean get() = chosen.size <= 1

        /**
         * The catalogue rows this sheet offers.
         *
         * What the member already owns is **not offered** — design ch. 17 artboard 5, which is the
         * web's behaviour, and the sheet's notice line says so, so a missing hit does not read as
         * a broken search. This reverses the earlier choice to list an owned product greyed out
         * with „hast du schon" beside it.
         */
        val offered: List<BlueprintProduct> get() = results.filterNot { it.owned }
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
    val selectedId: String? = null,
    val recipe: RecipeState = RecipeState.Idle,
    val selection: BlueprintSelection? = null,
    val import: BlueprintImportStep = BlueprintImportStep.Closed,
)

/**
 * The recipe pane of the tablet's master-detail (design ch. 09).
 *
 * A state of its own rather than a nullable recipe plus a boolean: the pane has to tell "nothing
 * selected" apart from "selected and still loading" apart from "selected and the read failed", and
 * two flags would let those three be four.
 */
sealed interface RecipeState {
    /** Nothing is selected; the pane shows its prompt. */
    data object Idle : RecipeState

    /** A blueprint is selected and its recipe is on its way. */
    data object Loading : RecipeState

    /**
     * The recipe arrived.
     *
     * @property recipe what to draw.
     */
    data class Ready(
        val recipe: BlueprintRecipe,
    ) : RecipeState

    /**
     * The read failed.
     *
     * @property error what went wrong, so the pane can say whether retrying is worth it.
     */
    data class Failed(
        val error: ApiError,
    ) : RecipeState
}

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
    private val imports: BlueprintImportSource,
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

    /**
     * The list's selection mode and the deletes it makes possible.
     *
     * Its own holder because it is one concern with one entry point (a long press) and one exit,
     * and because the view model is at detekt's function cap without it.
     */
    inner class Selection {
        /**
         * A long press opened the mode on one row.
         *
         * @param id the row that was held.
         */
        fun start(id: String) {
            if (mutableState.value.selection != null) {
                return
            }
            mutableState.value = mutableState.value.copy(selection = BlueprintSelection(ids = setOf(id)))
        }

        /**
         * A row was ticked or unticked.
         *
         * Unticking anything drops [BlueprintSelection.everything]: the one-call delete removes
         * every blueprint the member owns, so it may only run while the member has actually asked
         * for all of them.
         *
         * @param id the row.
         */
        fun toggle(id: String) {
            val open = mutableState.value.selection ?: return
            val next = if (id in open.ids) open.ids - id else open.ids + id
            mutableState.value =
                mutableState.value.copy(
                    selection = open.copy(ids = next, everything = false),
                )
        }

        /**
         * „Alles wählen" — every row the member owns, not merely every row loaded.
         *
         * The list is paged, so ticking what is on screen would delete less than it promised. The
         * flag says „all", the delete then uses the endpoint that means all, and the modal names
         * the total rather than the loaded count.
         */
        fun selectAll() {
            val open = mutableState.value.selection ?: return
            val loaded = mutableState.value.items.map { it.id }.toSet()
            mutableState.value =
                mutableState.value.copy(selection = open.copy(ids = loaded, everything = true))
        }

        /** Leaves the mode without deleting anything. */
        fun cancel() {
            mutableState.value = mutableState.value.copy(selection = null)
        }

        /**
         * Opens or closes the danger modal.
         *
         * @param asking whether it is on screen.
         */
        fun ask(asking: Boolean) {
            val open = mutableState.value.selection ?: return
            mutableState.value = mutableState.value.copy(selection = open.copy(asking = asking))
        }

        /**
         * Deletes what is ticked.
         *
         * **Everything** goes through the one call that means everything; a partial selection is
         * looped row by row, because the endpoint takes no ids. A loop can half-succeed, so what
         * came back is counted and the rows that refused stay ticked — the same shape „Mein
         * Inventar" uses, and the reason backend ask G6 exists.
         */
        fun confirm() {
            val open = mutableState.value.selection ?: return
            if (open.deleting || open.ids.isEmpty()) {
                return
            }
            mutableState.value =
                mutableState.value.copy(selection = open.copy(deleting = true, asking = false))
            viewModelScope.launch {
                val refused = if (open.everything) deleteEverything() else deleteEach(open.ids)
                mutableState.value =
                    mutableState.value.copy(
                        selection =
                            if (refused.isEmpty()) {
                                null
                            } else {
                                open.copy(ids = refused, deleting = false, everything = false)
                            },
                    )
                reload(keepRows = false)
            }
        }
    }

    /** The list's selection mode. */
    val selection: Selection = Selection()

    /**
     * Deletes the lot in one call.
     *
     * @return the ids that refused, which for an atomic call is either none or all of them.
     */
    private suspend fun deleteEverything(): Set<String> =
        when (val result = repository.removeAll()) {
            is ApiResult.Success -> {
                emptySet()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the blueprints could not be deleted: ${result.error}" }
                mutableState.value.selection?.ids.orEmpty()
            }
        }

    /**
     * Deletes row by row, because the endpoint takes no ids.
     *
     * The outcomes are collected first and counted afterwards rather than tallied inside the loop:
     * a counter mutated in a lambda is invisible to static analysis, and this is the shape that
     * survived that review once already.
     *
     * @param ids what to delete.
     * @return the ids that refused.
     */
    private suspend fun deleteEach(ids: Set<String>): Set<String> {
        val outcomes = ids.map { id -> id to repository.remove(id) }
        val refused = outcomes.filter { it.second is ApiResult.Failure }.map { it.first }.toSet()
        if (refused.isNotEmpty()) {
            KrtLog.w(LOG_TAG) { "${refused.size} of ${ids.size} blueprints refused deletion" }
        }
        return refused
    }

    /**
     * The two-step file import.
     *
     * The steps are two calls on purpose: `preview` reads and answers, `apply` writes. Nothing
     * between them touches the server, so the member can back out of a file they picked by mistake.
     */
    inner class Import {
        /** Opens the sheet, waiting for a file. */
        fun open() {
            mutableState.value = mutableState.value.copy(import = BlueprintImportStep.Waiting)
        }

        /** Closes it, whatever step it was on. Nothing is written by closing. */
        fun dismiss() {
            mutableState.value = mutableState.value.copy(import = BlueprintImportStep.Closed)
        }

        /**
         * A file was picked and read off the device.
         *
         * @param fileName what it is called; the server logs it.
         * @param bytes its content, or `null` when the device could not read it — which is not an
         *   HTTP state, so it gets plain German rather than the fiction canon.
         */
        fun onFile(
            fileName: String,
            bytes: ByteArray?,
        ) {
            if (bytes == null) {
                mutableState.value = mutableState.value.copy(import = BlueprintImportStep.Failed(null))
                return
            }
            mutableState.value = mutableState.value.copy(import = BlueprintImportStep.Reading(fileName))
            viewModelScope.launch {
                mutableState.value =
                    mutableState.value.copy(
                        import =
                            when (val result = imports.importPreview(fileName, bytes)) {
                                is ApiResult.Success -> {
                                    BlueprintImportStep.Preview(fileName, result.value)
                                }

                                is ApiResult.Failure -> {
                                    KrtLog.w(LOG_TAG) { "the import file could not be read: ${result.error}" }
                                    BlueprintImportStep.Failed(result.error)
                                }
                            },
                    )
            }
        }

        /** Takes over what the preview resolved — the only step that writes. */
        fun apply() {
            val step = mutableState.value.import as? BlueprintImportStep.Preview ?: return
            val entries = step.preview.importable
            if (entries.isEmpty()) {
                dismiss()
                return
            }
            mutableState.value = mutableState.value.copy(import = BlueprintImportStep.Writing(entries.size))
            viewModelScope.launch {
                when (val result = imports.importApply(entries)) {
                    is ApiResult.Success -> {
                        mutableState.value =
                            mutableState.value.copy(import = BlueprintImportStep.Done(result.value))
                        reload(keepRows = false)
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the import could not be applied: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(import = BlueprintImportStep.Failed(result.error))
                    }
                }
            }
        }
    }

    /** The two-step file import. */
    val import: Import = Import()

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
     * Selects a blueprint and reads its recipe.
     *
     * Only the tablet's master-detail calls this; on a phone the row does nothing of the sort,
     * because the design gives the phone no recipe screen to go to.
     *
     * Re-selecting the same row is ignored rather than re-read: the pane already shows it, and a
     * second read would blank a recipe the member is looking at.
     *
     * @param id the owned blueprint to show.
     */
    fun onSelect(id: String) {
        if (mutableState.value.selectedId == id && mutableState.value.recipe is RecipeState.Ready) {
            return
        }
        mutableState.value =
            mutableState.value.copy(selectedId = id, recipe = RecipeState.Loading)
        viewModelScope.launch {
            when (val result = repository.recipe(id)) {
                is ApiResult.Success -> {
                    // Guarded: a slow read for a row the member has since left must not overwrite
                    // the recipe of the one they are looking at now.
                    if (mutableState.value.selectedId == id) {
                        mutableState.value =
                            mutableState.value.copy(recipe = RecipeState.Ready(result.value))
                    }
                }

                is ApiResult.Failure -> {
                    if (mutableState.value.selectedId == id) {
                        mutableState.value =
                            mutableState.value.copy(recipe = RecipeState.Failed(result.error))
                    }
                }
            }
        }
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
        update<BlueprintEditor.Adding> { adding ->
            val already = adding.chosen.any { it.productKey == product.productKey }
            adding.copy(
                // A second tap takes it back off: the row is a checkbox, and a checkbox that only
                // ever ticks is a trap on a list the member is still narrowing down.
                chosen =
                    if (already) {
                        adding.chosen.filterNot { it.productKey == product.productKey }
                    } else {
                        adding.chosen + product
                    },
                error = null,
                outcome = null,
            )
        }
    }

    /**
     * Sets the note, in whichever sheet is open.
     *
     * @param note what the member typed.
     */
    fun onNoteChanged(note: String) {
        val capped = note.take(FieldLimits.BLUEPRINT_NOTE)
        val next =
            when (val editor = mutableState.value.editor) {
                is BlueprintEditor.Adding -> editor.copy(note = capped, error = null)
                is BlueprintEditor.Editing -> editor.copy(note = capped, error = null)
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
        val picked = editor.chosen
        if (picked.isEmpty()) {
            return
        }
        mutableState.value = mutableState.value.copy(editor = editor.copy(saving = true, error = null))
        viewModelScope.launch {
            // One product keeps the single create, because that is the call that carries the note.
            // Several go through the batch, which carries none.
            val single = picked.singleOrNull()
            if (single != null) {
                addOne(editor, single)
            } else {
                addMany(editor, picked)
            }
        }
    }

    /**
     * Adds one product, with its note.
     *
     * @param editor the open sheet.
     * @param product the single picked product.
     */
    private suspend fun addOne(
        editor: BlueprintEditor.Adding,
        product: BlueprintProduct,
    ) {
        val note = editor.note.trim().takeIf { it.isNotEmpty() }
        when (val result = repository.add(product.productKey, note)) {
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

    /**
     * Adds several products at once.
     *
     * The sheet **stays open** on success and shows what the server did — „2 übernommen · 1
     * bereits vorhanden", design ch. 17 artboard 5. Closing on a partial result would hide the
     * skipped ones, which is the one thing the line exists to say.
     *
     * @param editor the open sheet.
     * @param picked the chosen products.
     */
    private suspend fun addMany(
        editor: BlueprintEditor.Adding,
        picked: List<BlueprintProduct>,
    ) {
        when (val result = repository.addAll(picked.map { it.productKey })) {
            is ApiResult.Success -> {
                mutableState.value =
                    mutableState.value.copy(
                        editor =
                            editor.copy(
                                saving = false,
                                chosen = emptyList(),
                                outcome = result.value,
                            ),
                    )
                if (result.value.anyAdded) {
                    reload(keepRows = true)
                }
            }

            is ApiResult.Failure -> {
                mutableState.value =
                    mutableState.value.copy(editor = editor.copy(saving = false, error = result.error))
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
