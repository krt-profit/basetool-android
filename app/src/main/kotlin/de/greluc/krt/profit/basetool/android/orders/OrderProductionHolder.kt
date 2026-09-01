/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.JobOrderProductionSource
import de.greluc.krt.profit.basetool.android.core.data.ProductionBooking
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Log tag for the Herstellung. */
private const val LOG_TAG = "OrderProduction"

/**
 * Where the production draft lives while it is being filled in.
 *
 * One value rather than a `read`/`write` pair of constructor arguments: the two are meaningless
 * apart, and passing them separately made the holder's constructor wider than the codebase allows.
 *
 * @property read the draft as it stands, or `null` when the sheet is shut.
 * @property write reports it back.
 */
data class ProductionSlot(
    val read: () -> ProductionDraft?,
    val write: (ProductionDraft?) -> Unit,
)

/**
 * „Herstellung erfassen" — booking a production run against one item line of an Auftrag.
 *
 * > **The write that moves „hergestellt".** An item Auftrag has two writes and they are not the
 * > same thing: the Übergabe hands finished goods to somebody, the Herstellung *consumes* the
 * > earmarked raw material and *creates* item stock. Without this the app could deliver items it
 * > could never record having built.
 *
 * Three deviations from design ch. 10 artboard 15, all of them because the endpoint says so and all
 * of them on the design gap list rather than coded around:
 *
 * - The artboard offers **one** „Zutaten aus dem Lager ausbuchen" checkbox for the whole run. The
 *   server takes a plan per material, over named stock rows, that must cover the demand **exactly**
 *   — so the sheet carries the web's per-material „Nicht ausbuchen" instead.
 * - The artboard has no **Einlagerung** section, and `bookIn.locationId` is `@NotNull`: produced
 *   units have to land somewhere. The sheet asks.
 * - The artboard's **„Verwendete Variante"** and **„Übergeben an"** have no field on this payload.
 *   The variant is an order-level setting (`PATCH /blueprint-variant-counting`), and handing over
 *   is the separate item-handover write.
 *
 * @property source the two calls this sheet makes.
 * @property options where the produced stock may land.
 * @property myUserId the acting member, for the org-unit lookup when no other owner is picked.
 * @property scope the view model's scope.
 * @property slot where the draft lives while it is being filled in.
 * @property onBooked a run landed; the caller re-reads the Auftrag.
 */
class OrderProduction(
    private val source: JobOrderProductionSource,
    private val options: BookInOptions,
    private val myUserId: suspend () -> String?,
    private val scope: CoroutineScope,
    private val slot: ProductionSlot,
    private val onBooked: () -> Unit,
) {
    /** The draft as it stands. */
    private fun read(): ProductionDraft? = slot.read()

    /**
     * Reports the draft back.
     *
     * @param draft what it now is, or `null` to shut the sheet.
     */
    private fun write(draft: ProductionDraft?) {
        slot.write(draft)
    }

    /**
     * Opens the sheet for one item line and reads everything it needs to be filled in.
     *
     * @param orderId the Auftrag.
     * @param item the line being manufactured.
     * @param responsibleOrgUnitId the unit working the Auftrag — preselected as the book-in pool
     *   when the owner belongs to it, which is what the web does.
     */
    fun open(
        orderId: String,
        item: JobOrderItem,
        responsibleOrgUnitId: String?,
    ) {
        val draft = item.krtProductionDraft(orderId) ?: return
        write(draft)
        scope.launch { loadStock(draft) }
        scope.launch { loadLocations("") }
        scope.launch { loadOrgUnits(responsibleOrgUnitId) }
    }

    /** Closes the sheet, discarding the plan. */
    fun dismiss() {
        write(null)
    }

    /**
     * How many units this run produced.
     *
     * @param value what was typed.
     */
    fun changeAmount(value: String) {
        write(read()?.copy(amount = value.filter { it.isDigit() }))
    }

    /**
     * How much comes off one stock row.
     *
     * @param materialId which material's plan.
     * @param rowId which row.
     * @param value what was typed.
     */
    fun changeDraw(
        materialId: String,
        rowId: String,
        value: String,
    ) {
        onMaterial(materialId) { it.copy(amounts = it.amounts + (rowId to value)) }
    }

    /**
     * Marks a material as consumed outside the tool, or takes that back.
     *
     * Its demand then drops out of the gate and no draw is sent for it — but the amounts already
     * typed are kept, so unticking restores the plan rather than making it be entered twice.
     *
     * @param materialId which material.
     */
    fun toggleSkip(materialId: String) {
        onMaterial(materialId) { it.copy(skipped = !it.skipped) }
    }

    /**
     * Fills one material's plan to exactly its demand, taking from the rows in order.
     *
     * The gate is an exact match and the arithmetic is the member's otherwise — three rows and a
     * demand of 1 234,5 is a subtraction nobody should do on a phone. It never assigns more than a
     * row can give.
     *
     * @param materialId which material.
     */
    fun autoFill(materialId: String) {
        val units = read()?.units ?: return
        onMaterial(materialId) { material ->
            var left = material.demand(units)
            val filled = mutableMapOf<String, String>()
            material.rows.forEach { row ->
                val take = minOf(left, row.available).krtRoundForUnit(material.unit).coerceAtLeast(0.0)
                left = (left - take).krtRoundForUnit(material.unit)
                filled[row.id] = if (take > 0.0) take.krtPlain() else ""
            }
            material.copy(amounts = filled)
        }
    }

    /**
     * The place picker's query changed.
     *
     * @param query what was typed.
     */
    fun searchLocations(query: String) {
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(locationQuery = query)) })
        scope.launch { loadLocations(query) }
    }

    /**
     * A place was picked.
     *
     * @param id the location.
     * @param name how it reads.
     */
    fun chooseLocation(
        id: String,
        name: String,
    ) {
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(locationId = id, locationQuery = name)) })
    }

    /**
     * The member picker's query changed.
     *
     * @param query what was typed.
     */
    fun searchMembers(query: String) {
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(ownerQuery = query)) })
        scope.launch {
            val result = options.members(query)
            if (result is ApiResult.Success) {
                write(
                    read()?.let {
                        it.copy(
                            bookIn =
                                it.bookIn.copy(members = result.value.rows, moreMembers = result.value.more),
                        )
                    },
                )
            }
        }
    }

    /**
     * Somebody else's name is on the produced stock.
     *
     * The pool choice is re-read for **them**: the server validates the picked unit against the
     * owner's own memberships, so keeping the previous owner's list would offer a unit the write
     * then refuses.
     *
     * @param id the member, or `null` to hand it back to the acting member.
     * @param name how they read.
     */
    fun chooseOwner(
        id: String?,
        name: String,
    ) {
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(ownerId = id, ownerName = name, ownerQuery = name)) })
        scope.launch { loadOrgUnits(read()?.bookIn?.orgUnitId) }
    }

    /**
     * Which pool the produced stock lands in.
     *
     * @param id the org unit.
     */
    fun chooseOrgUnit(id: String) {
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(orgUnitId = id)) })
    }

    /**
     * Books it into the owner's personal pool, or takes that back.
     *
     * Personal stock never carries earmarks, so ticking this clears „dem Auftrag zuordnen" and
     * unticking restores it — the same coupling the Lager's own book-in has, and the combination
     * the server answers 400 for.
     */
    fun togglePersonal() {
        write(
            read()?.let {
                val personal = !it.bookIn.personal
                it.copy(bookIn = it.bookIn.copy(personal = personal, allocate = !personal))
            },
        )
    }

    /** Earmarks the produced units back to this Auftrag, or takes that back. */
    fun toggleAllocate() {
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(allocate = !it.bookIn.allocate)) })
    }

    /** Sends the booking. */
    fun submit() {
        // `submittable` already carries the whole gate — a whole amount inside what is left, an
        // exact plan, and a place — so the two reads below can only fail together with it.
        val draft = read()?.takeIf { it.submittable } ?: return
        val units = draft.units
        val bookIn = draft.bookIn.toWire()
        if (units == null || bookIn == null) {
            return
        }
        write(draft.copy(saving = true, error = null))
        scope.launch {
            val result =
                source.bookProduction(
                    ProductionBooking(
                        orderId = draft.orderId,
                        itemId = draft.itemId,
                        amount = units,
                        version = draft.version,
                        consumption = draft.materials.flatMap { it.draws() },
                        skippedMaterialIds = draft.materials.filter { it.skipped }.map { it.materialId },
                        bookIn = bookIn,
                    ),
                )
            when (result) {
                is ApiResult.Success -> {
                    write(null)
                    onBooked()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the production booking was refused: ${result.error}" }
                    write(read()?.copy(saving = false, error = result.error))
                }
            }
        }
    }

    /**
     * Applies a change to one material's plan.
     *
     * @param materialId which material.
     * @param change what happens to it.
     */
    private fun onMaterial(
        materialId: String,
        change: (ProductionMaterialDraft) -> ProductionMaterialDraft,
    ) {
        val draft = read() ?: return
        write(
            draft.copy(
                materials = draft.materials.map { if (it.materialId == materialId) change(it) else it },
            ),
        )
    }

    /**
     * Reads the candidate stock rows of every required material.
     *
     * Only rows **earmarked to this Auftrag** are candidates: a production booking draws against
     * the promise, so a row without one is not offered — the server would refuse it.
     *
     * @param draft the freshly opened plan.
     */
    private suspend fun loadStock(draft: ProductionDraft) {
        draft.materials.forEach { material ->
            val result = source.linkedStock(draft.orderId, material.materialId)
            val rows =
                when (result) {
                    is ApiResult.Success -> {
                        result.value.filter { it.slice > 0.0 && it.version != null }
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the linked stock could not be read: ${result.error}" }
                        emptyList()
                    }
                }
            onMaterial(material.materialId) { it.copy(rows = rows, loading = false) }
        }
    }

    /**
     * Reads the places the produced stock can be booked in at.
     *
     * @param query what was typed.
     */
    private suspend fun loadLocations(query: String) {
        val result = options.locations(query)
        if (result is ApiResult.Success) {
            write(
                read()?.let {
                    it.copy(
                        bookIn =
                            it.bookIn.copy(locations = result.value.rows, moreLocations = result.value.more),
                    )
                },
            )
        }
    }

    /**
     * Reads the owner's memberships and preselects one.
     *
     * The Auftrag's responsible unit when the owner belongs to it, else their first — the web's own
     * resolution, and the reason the „more than one membership and no pick" 400 is unreachable from
     * this screen.
     *
     * @param preferred the unit to preselect if the owner has it.
     */
    private suspend fun loadOrgUnits(preferred: String?) {
        val owner = read()?.bookIn?.ownerId ?: myUserId() ?: return
        val result = options.orgUnitsFor(owner)
        if (result !is ApiResult.Success) {
            return
        }
        val units = result.value
        val chosen = units.firstOrNull { it.id == preferred }?.id ?: units.firstOrNull()?.id
        write(read()?.let { it.copy(bookIn = it.bookIn.copy(orgUnits = units, orgUnitId = chosen)) })
    }
}

/**
 * Renders a quantity without scientific notation, for a field somebody then edits.
 *
 * @receiver the amount.
 * @return the plain decimal, with a trailing `.0` dropped so a whole number reads as one.
 */
internal fun Double.krtPlain(): String =
    java.math.BigDecimal(this.toString()).stripTrailingZeros().toPlainString()
