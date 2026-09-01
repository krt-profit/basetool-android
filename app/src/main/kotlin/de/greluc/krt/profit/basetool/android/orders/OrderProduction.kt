/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.ProductionBookIn
import de.greluc.krt.profit.basetool.android.core.data.ProductionDraw
import de.greluc.krt.profit.basetool.android.core.data.krtToDoubleOrNull
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import kotlin.math.abs
import kotlin.math.roundToLong

/** How close two quantities have to be to count as covering each other. */
private const val EPSILON = 1e-4

/** Quantities are reconciled to three decimals, which is what the web rounds to. */
private const val THOUSANDTHS = 1000.0

/** The unit a whole-number material is counted in. */
const val UNIT_PIECE: String = "PIECE"

/**
 * Rounds a quantity the way the material's unit is counted.
 *
 * A `PIECE` material has no halves — a plan that asked for 2.5 casings would be refused by the
 * server — so it rounds to whole numbers; everything else settles at thousandths, which is where
 * the web's own reconcile gate rounds.
 *
 * @receiver the quantity.
 * @param unit the material's `quantityType`.
 * @return the rounded quantity.
 */
fun Double.krtRoundForUnit(unit: String?): Double =
    if (unit == UNIT_PIECE) {
        this.roundToLong().toDouble()
    } else {
        Math.round(this * THOUSANDTHS) / THOUSANDTHS
    }

/**
 * One required material of the line being manufactured, and the plan for covering it.
 *
 * @property materialId which material.
 * @property name what it is called.
 * @property unit `SCU` or `PIECE`.
 * @property requiredTotal what the **whole** line needs, as the server derived it.
 * @property lineAmount how many units the whole line is for, so a partial run can be priced.
 * @property loading whether its candidate rows are still being read.
 * @property rows the stock rows earmarked to this Auftrag that hold it.
 * @property amounts how much is taken off each row, keyed by row id, as typed.
 * @property skipped whether this material is deliberately not booked out — consumed outside the
 *   tool. Its demand then drops out of the gate and no draw is sent for it.
 */
data class ProductionMaterialDraft(
    val materialId: String,
    val name: String,
    val unit: String?,
    val requiredTotal: Double,
    val lineAmount: Int,
    val loading: Boolean = true,
    val rows: List<HandoverStockRow> = emptyList(),
    val amounts: Map<String, String> = emptyMap(),
    val skipped: Boolean = false,
) {
    /**
     * What manufacturing [units] of the line consumes of this material.
     *
     * The server's figure is the line's total, so a partial run is that total scaled by the share
     * being built. Deriving it from the line rather than from a per-unit figure keeps one rounding
     * step instead of one per unit.
     *
     * @param units how many are being built.
     * @return the demand, rounded the way the unit is counted.
     */
    fun demand(units: Int): Double {
        if (lineAmount <= 0) {
            return 0.0
        }
        return (requiredTotal * units / lineAmount).krtRoundForUnit(unit)
    }

    /** What the plan currently assigns, over every row. */
    val assigned: Double
        get() = amounts.values.sumOf { it.krtToDoubleOrNull() ?: 0.0 }.krtRoundForUnit(unit)

    /**
     * What is still missing, or negative when too much was assigned.
     *
     * @param units how many are being built.
     * @return demand minus assigned.
     */
    fun rest(units: Int): Double = (demand(units) - assigned).krtRoundForUnit(unit)

    /**
     * Whether this material may pass the gate.
     *
     * **Exactly**, not "at least": the server refuses a plan that over- or under-covers the demand
     * („Zuweisung deckt den Materialbedarf nicht exakt."), so approximate is a 400 waiting to
     * happen. A skipped material is covered by definition — its demand was dropped.
     *
     * @param units how many are being built.
     * @return whether it reconciles.
     */
    fun covered(units: Int): Boolean = skipped || abs(rest(units)) < EPSILON

    /**
     * The draws this material contributes to the write.
     *
     * @return one entry per row with a positive amount, empty when skipped.
     */
    fun draws(): List<ProductionDraw> {
        if (skipped) {
            return emptyList()
        }
        return rows.mapNotNull { row ->
            val amount = amounts[row.id]?.krtToDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
            val version = row.version ?: return@mapNotNull null
            ProductionDraw(
                inventoryItemId = row.id,
                materialId = materialId,
                amount = amount,
                version = version,
            )
        }
    }
}

/**
 * Where the produced units are stored, as the form holds it.
 *
 * @property locationId the chosen place, or `null` while none is. The server requires one.
 * @property locationQuery what is typed in the place picker.
 * @property locations the current matches.
 * @property moreLocations whether the catalogue holds places this page does not carry.
 * @property ownerId whose stock it becomes; seeded with the acting member.
 * @property ownerName how they read, so the field shows a name and not an id.
 * @property ownerQuery what is typed in the member picker.
 * @property members the current matches.
 * @property moreMembers whether the roster holds members this page does not carry.
 * @property orgUnits the owner's memberships, as read for **them** and not for the caller.
 * @property orgUnitId the chosen pool.
 * @property personal whether it goes into the owner's personal pool.
 * @property allocate whether the produced units are earmarked back to this Auftrag.
 */
data class ProductionBookInDraft(
    val locationId: String? = null,
    val locationQuery: String = "",
    val locations: List<LocationOption> = emptyList(),
    val moreLocations: Boolean = false,
    val ownerId: String? = null,
    val ownerName: String = "",
    val ownerQuery: String = "",
    val members: List<MemberOption> = emptyList(),
    val moreMembers: Boolean = false,
    val orgUnits: List<OrgUnitOption> = emptyList(),
    val orgUnitId: String? = null,
    val personal: Boolean = false,
    val allocate: Boolean = true,
) {
    /**
     * The book-in as the wire takes it, or `null` while no place is chosen.
     *
     * @return the payload block.
     */
    fun toWire(): ProductionBookIn? {
        val location = locationId ?: return null
        return ProductionBookIn(
            locationId = location,
            ownerUserId = ownerId,
            // Only sent when the owner actually has a choice. With exactly one membership the
            // server resolves it itself; with none there is nothing to send.
            owningOrgUnitId = orgUnitId.takeIf { orgUnits.size > 1 },
            personal = personal,
            // Personal stock never carries earmarks (REQ-INV-032), and the server answers 400 for
            // the combination rather than silently dropping one of them.
            allocateToOrder = !personal && allocate,
        )
    }
}

/**
 * „Herstellung erfassen" — one production run against one item line, as it is being filled in.
 *
 * @property orderId the Auftrag.
 * @property itemId the line being manufactured.
 * @property itemName what is being built.
 * @property lineAmount how many the line asks for.
 * @property manufactured how many have already been built.
 * @property version the line's optimistic lock.
 * @property amount how many this run produced, as typed.
 * @property materials the required materials and their plans.
 * @property bookIn where the produced units land.
 * @property saving whether the write is in flight.
 * @property error the last refusal.
 */
data class ProductionDraft(
    val orderId: String,
    val itemId: String,
    val itemName: String,
    val lineAmount: Int,
    val manufactured: Int,
    val version: Long,
    val amount: String = "1",
    val materials: List<ProductionMaterialDraft> = emptyList(),
    val bookIn: ProductionBookInDraft = ProductionBookInDraft(),
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /** How many of the line are still to be built. */
    val remaining: Int
        get() = (lineAmount - manufactured).coerceAtLeast(0)

    /** How many this run claims, or `null` when nothing usable is typed. */
    val units: Int?
        get() = amount.trim().toIntOrNull()?.takeIf { it >= 1 }

    /** Where the line stands after this booking, or `null` without a number. */
    val projected: Int?
        get() = units?.let { manufactured + it }

    /** Whether this booking finishes the line. */
    val completes: Boolean
        get() = (projected ?: 0) >= lineAmount && lineAmount > 0

    /** Whether the amount is one the line can still take. */
    val amountValid: Boolean
        get() = units?.let { it <= remaining } == true

    /** Whether every required material reconciles — the gate the server also applies. */
    val reconciled: Boolean
        get() = units?.let { u -> materials.all { it.covered(u) } } == true

    /**
     * Whether the form may be sent.
     *
     * Three gates, all of them the server's own: a whole amount within what is left, a plan that
     * covers every non-skipped material exactly, and a place for the produced stock.
     */
    val submittable: Boolean
        get() = !saving && amountValid && reconciled && bookIn.locationId != null && materials.none { it.loading }
}

/**
 * Builds the empty plan for one item line.
 *
 * @receiver the line being manufactured.
 * @param orderId the Auftrag.
 * @return the draft, or `null` for a line the server sent without an id or a version — neither can
 *   be addressed by the write, so the sheet does not open on it.
 */
fun JobOrderItem.krtProductionDraft(orderId: String): ProductionDraft? {
    val lineId = id
    val lock = version
    if (lineId == null || lock == null) {
        return null
    }
    return ProductionDraft(
        orderId = orderId,
        itemId = lineId,
        itemName = name.orEmpty(),
        lineAmount = amount,
        manufactured = manufactured,
        version = lock,
        materials =
            requirements.map { requirement ->
                ProductionMaterialDraft(
                    materialId = requirement.materialId,
                    name = requirement.name,
                    unit = requirement.unit,
                    requiredTotal = requirement.requiredTotal,
                    lineAmount = amount,
                )
            },
    )
}
