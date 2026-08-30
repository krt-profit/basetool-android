/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandoverSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Log tag for the item Übergabe. */
private const val LOG_TAG = "OrderItemHandover"

/**
 * Handing finished items over, as the form holds it.
 *
 * @property itemId which ordered line.
 * @property itemName what is being handed over.
 * @property ordered how many the line asked for.
 * @property delivered how many have already changed hands.
 * @property deliverable how many may still go — built and not yet delivered.
 * @property amount how many this handover carries, as typed.
 * @property recipient who receives them, as typed. The server requires a non-blank handle.
 * @property saving whether the write is in flight.
 * @property error the last refusal.
 */
data class ItemHandoverDraft(
    val itemId: String,
    val itemName: String,
    val ordered: Int,
    val delivered: Int,
    val deliverable: Int,
    val amount: String = "1",
    val recipient: String = "",
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /** How many this handover claims, or `null` when nothing usable is typed. */
    val units: Int?
        get() = amount.trim().toIntOrNull()?.takeIf { it >= 1 }

    /** Where the line stands after this handover, or `null` without a number. */
    val projected: Int?
        get() = units?.let { delivered + it }

    /** Whether this handover finishes the line. */
    val completes: Boolean
        get() = ordered > 0 && (projected ?: 0) >= ordered

    /**
     * Whether the typed count is one the line can still take.
     *
     * The ceiling is what has been **built** and not yet delivered, never `ordered - delivered`:
     * a unit nobody has manufactured cannot be handed over, and the server answers 400 for the
     * attempt.
     */
    val amountValid: Boolean
        get() = units?.let { it <= deliverable } == true

    /** Whether the form may be sent. */
    val submittable: Boolean
        get() = !saving && amountValid && recipient.isNotBlank()
}

/**
 * Builds the form for one item line, or refuses to open on a line that cannot carry a handover.
 *
 * @receiver the line.
 * @return the draft, or `null` for a line the server sent without an id — the write is addressed by
 *   it — or one with nothing built and undelivered, which has nothing to hand over.
 */
fun JobOrderItem.krtItemHandoverDraft(): ItemHandoverDraft? {
    val lineId = id ?: return null
    return ItemHandoverDraft(
        itemId = lineId,
        itemName = name.orEmpty(),
        ordered = amount,
        delivered = delivered,
        deliverable = deliverable,
    )
}

/**
 * „Übergabe erfassen" for an item Auftrag — the write that finishes one.
 *
 * > **The item order's own handover, not the material one with a count.** The server keeps the two
 * > on separate endpoints and separate logs; this one moves `deliveredAmount` and closes the order
 * > once every line is fully delivered.
 *
 * @property source where the write goes.
 * @property scope the view model's scope.
 * @property read the draft as it stands.
 * @property write reports it back.
 * @property onRecorded a handover landed; the caller re-reads the Auftrag.
 */
class OrderItemHandover(
    private val source: JobOrderHandoverSource,
    private val scope: CoroutineScope,
    private val read: () -> ItemHandoverDraft?,
    private val write: (ItemHandoverDraft?) -> Unit,
    private val onRecorded: () -> Unit,
) {
    /**
     * Opens the sheet for one item line.
     *
     * @param item the line.
     */
    fun open(item: JobOrderItem) {
        write(item.krtItemHandoverDraft() ?: return)
    }

    /** Closes the sheet, discarding what was typed. */
    fun dismiss() {
        write(null)
    }

    /**
     * Records a change in the open form.
     *
     * @param change what the field did to it.
     */
    fun change(change: (ItemHandoverDraft) -> ItemHandoverDraft) {
        write(change(read() ?: return))
    }

    /**
     * Sends the handover.
     *
     * @param orderId the Auftrag.
     */
    fun submit(orderId: String) {
        val draft = read()?.takeIf { it.submittable } ?: return
        val units = draft.units ?: return
        write(draft.copy(saving = true, error = null))
        scope.launch {
            val result =
                source.recordItemHandover(
                    orderId = orderId,
                    itemId = draft.itemId,
                    amount = units,
                    recipientHandle = draft.recipient.trim(),
                    // The device's clock: the member is the one who witnessed the handover, which
                    // is why the web fills this in the browser for the same reason.
                    handoverTime = Instant.now().toString(),
                )
            when (result) {
                is ApiResult.Success -> {
                    write(null)
                    onRecorded()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the item handover was refused: ${result.error}" }
                    write(read()?.copy(saving = false, error = result.error))
                }
            }
        }
    }
}
