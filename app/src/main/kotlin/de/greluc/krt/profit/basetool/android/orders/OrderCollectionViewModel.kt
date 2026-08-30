/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialCollectionRow
import de.greluc.krt.profit.basetool.android.core.data.MaterialCollectionSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Log tag for the Materialsammelübersicht. */
private const val LOG_TAG = "OrderCollection"

/**
 * A material the Auftrag requires but that no stock row covers.
 *
 * @property materialId which material — the unlink is addressed by it.
 * @property name what it is called.
 */
data class UnbackedMaterial(
    val materialId: String,
    val name: String,
)

/**
 * The unlink being confirmed.
 *
 * Only a row with an earmarked amount asks: the link is what goes, the stock stays, and a row that
 * promised nothing has nothing to warn about.
 *
 * @property entryId the stock row.
 * @property materialName what it holds.
 * @property amount how much of it was earmarked here.
 * @property owner who holds it, or `null` when the answer redacted it.
 * @property location where it is, or `null`.
 */
data class UnlinkConfirm(
    val entryId: String,
    val materialName: String,
    val amount: BigDecimal?,
    val owner: String?,
    val location: String?,
)

/**
 * Everything the Materialsammelübersicht draws.
 *
 * @property orderId the Auftrag this belongs to.
 * @property displayId its number, for the back link and the title.
 * @property rows the linked stock rows.
 * @property unbacked the materials the Auftrag needs that no row covers.
 * @property allowed whether the caller may change anything here.
 * @property loading whether the first read is running.
 * @property saving whether a write is in flight.
 * @property error the last failure.
 * @property confirming the unlink waiting for a yes, or `null`.
 */
data class OrderCollectionState(
    val orderId: String,
    val displayId: String = "",
    val rows: List<MaterialCollectionRow> = emptyList(),
    val unbacked: List<UnbackedMaterial> = emptyList(),
    val allowed: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val confirming: UnlinkConfirm? = null,
)

/**
 * Drives „Materialsammelübersicht" — the stock rows linked to one Auftrag (REQ-APP-ORDERS-023).
 *
 * > **It belongs to the Auftrag.** `material.collection.back` reads „Zurück zum Auftrag"; design
 * > chapter 16's first draft filed this page under the material reference and corrected itself.
 *
 * @property source the rows and the three writes.
 * @property orders where the order is read, for its number and its required materials.
 * @property orderId which Auftrag.
 */
class OrderCollectionViewModel(
    private val source: MaterialCollectionSource,
    private val orders: JobOrderSource,
    private val orderId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrderCollectionState(orderId = orderId))

    /** What the screen draws. */
    val state: StateFlow<OrderCollectionState> = mutableState.asStateFlow()

    init {
        load()
    }

    /** Reads the rows and the order they belong to. */
    fun load() {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val order = (orders.detail(orderId) as? ApiResult.Success)?.value
            when (val result = source.rows(orderId)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            rows = result.value,
                            displayId = order?.displayId.orEmpty(),
                            unbacked = order.krtUnbacked(result.value),
                            loading = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the collection could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(loading = false, error = result.error)
                }
            }
        }
    }

    /**
     * Tells the screen whether the caller may write here.
     *
     * Passed in rather than derived: the gate is `LOGISTICIAN | OFFICER | ADMIN` plus edit scope on
     * the order, and the detail screen already knows the first half.
     *
     * @param value whether writes are offered.
     */
    fun onAllowed(value: Boolean) {
        mutableState.value = mutableState.value.copy(allowed = value)
    }

    /**
     * Flips one row's delivered flag.
     *
     * @param row which row.
     */
    fun onDelivered(row: MaterialCollectionRow) {
        val version = row.version ?: return
        write { source.setDelivered(row.entryId, orderId, !row.delivered, version) }
    }

    /**
     * Asks before removing a link that carries an earmarked amount, and removes one that does not.
     *
     * A row with nothing earmarked has nothing to warn about: the link is all that goes, and the
     * stock is untouched either way.
     *
     * @param row which row.
     */
    fun onUnlink(row: MaterialCollectionRow) {
        val amount = row.allocated
        if (amount == null || amount <= BigDecimal.ZERO) {
            write { source.unlinkEntry(orderId, row.entryId) }
            return
        }
        mutableState.value =
            mutableState.value.copy(
                confirming =
                    UnlinkConfirm(
                        entryId = row.entryId,
                        materialName = row.materialName,
                        amount = amount,
                        owner = row.owner,
                        location = row.location,
                    ),
            )
    }

    /** Backs out of the confirmation. */
    fun onDismissConfirm() {
        mutableState.value = mutableState.value.copy(confirming = null)
    }

    /** Removes the link the confirmation names. */
    fun onConfirmUnlink() {
        val entryId = mutableState.value.confirming?.entryId ?: return
        mutableState.value = mutableState.value.copy(confirming = null)
        write { source.unlinkEntry(orderId, entryId) }
    }

    /**
     * Removes a required material that no stock row covers.
     *
     * No confirmation: there is no amount behind it to lose.
     *
     * @param material which material.
     */
    fun onUnlinkMaterial(material: UnbackedMaterial) {
        write { source.unlinkMaterial(orderId, material.materialId) }
    }

    /**
     * Runs one write and re-reads afterwards.
     *
     * Re-read rather than patched: every write here moves a figure the server computes — the
     * earmark, the delivered flag, and whether the material still appears at all.
     *
     * @param call the write.
     */
    private fun write(call: suspend () -> ApiResult<Unit>) {
        if (mutableState.value.saving) {
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(saving = false)
                    load()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the collection write was refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }
}

/**
 * The materials the Auftrag requires that no linked row covers.
 *
 * The design's second section — „Verknüpfte Materialien (ohne Bestand)". Derived rather than read,
 * because the server has no endpoint for it: the required list and the linked rows are two answers
 * and the difference is what is missing.
 *
 * @receiver the order, or `null` when it could not be read.
 * @param rows the linked stock rows.
 * @return the materials with no row behind them.
 */
private fun JobOrder?.krtUnbacked(rows: List<MaterialCollectionRow>): List<UnbackedMaterial> {
    val covered = rows.map { it.materialName }.toSet()
    return this
        ?.materials
        ?.filter { it.materialId != null && it.name !in covered }
        ?.map { UnbackedMaterial(materialId = requireNotNull(it.materialId), name = it.name) }
        .orEmpty()
}
