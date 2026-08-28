/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MaterialDetailSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialEntryPage
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the tablet pane's read stands.
 *
 * Its own phase rather than a nullable page: „not read yet", „could not be read" and „read and
 * empty" are three different things on a pane, and a member looking at an empty table has to know
 * which of them they are seeing.
 */
sealed interface MaterialPanePhase {
    /** The page is on its way. */
    data object Loading : MaterialPanePhase

    /**
     * The read failed.
     *
     * @property error what went wrong, for the retry the pane offers.
     */
    data class Failed(
        val error: ApiError,
    ) : MaterialPanePhase

    /**
     * The page arrived.
     *
     * @property page the entries and where they sit in the whole.
     */
    data class Ready(
        val page: MaterialEntryPage,
    ) : MaterialPanePhase
}

/**
 * Which material the tablet pane is showing, and what it holds.
 *
 * @property materialId which material.
 * @property name what it is called, carried so the pane can head itself before the read lands.
 * @property unit the quantity unit, from the group row that selected it.
 * @property phase where the read stands.
 */
data class MaterialPane(
    val materialId: String,
    val name: String,
    val unit: String?,
    val phase: MaterialPanePhase,
)

/**
 * Drives the Lager's tablet detail pane.
 *
 * A holder of its own rather than three more methods on [InventoryViewModel]: the pane is a
 * separate concern reading a separate endpoint — `/inventory/material/{id}`, the whole material
 * flat — and the tree's view model was already at the size where one more responsibility stops
 * being findable. It carries its own state so the tree's state does not have to know a pane exists.
 *
 * @property source the read.
 * @property scope the view model's scope, so a pane read dies with the screen.
 */
class MaterialPaneLoader(
    private val source: MaterialDetailSource,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<MaterialPane?>(null)

    /** Which material the pane shows, or `null` while nothing is selected. */
    val state: StateFlow<MaterialPane?> = mutableState.asStateFlow()

    /**
     * Shows a material, and reads its first page.
     *
     * Re-selecting the material already shown does nothing, so toggling a group shut and open again
     * does not re-read it.
     *
     * @param materialId which material.
     * @param name what it is called.
     * @param unit its quantity unit.
     */
    fun select(
        materialId: String,
        name: String,
        unit: String?,
    ) {
        if (mutableState.value?.materialId == materialId) {
            return
        }
        mutableState.value = MaterialPane(materialId, name, unit, MaterialPanePhase.Loading)
        load(materialId, 0)
    }

    /**
     * Turns the pane to another page.
     *
     * @param page the zero-based index.
     */
    fun page(page: Int) {
        val pane = mutableState.value ?: return
        mutableState.value = pane.copy(phase = MaterialPanePhase.Loading)
        load(pane.materialId, page)
    }

    /** Reads the first page again after a failure. */
    fun retry() {
        page(0)
    }

    /**
     * Reads one page into the pane.
     *
     * The answer is dropped when the pane has moved on to another material: a slow read must not
     * fill the pane of a material the member has since left.
     *
     * @param materialId which material.
     * @param page the zero-based index.
     */
    private fun load(
        materialId: String,
        page: Int,
    ) {
        scope.launch {
            val result = source.materialEntries(materialId, page)
            val pane = mutableState.value
            if (pane?.materialId != materialId) {
                return@launch
            }
            mutableState.value =
                pane.copy(
                    phase =
                        when (result) {
                            is ApiResult.Success -> {
                                MaterialPanePhase.Ready(result.value)
                            }

                            is ApiResult.Failure -> {
                                KrtLog.w(LOG_TAG) { "the material pane could not be read: ${result.error}" }
                                MaterialPanePhase.Failed(result.error)
                            }
                        },
                )
        }
    }

    private companion object {
        /** Log subsystem. No member name and no place reach the log. */
        const val LOG_TAG = "inventory"
    }
}
