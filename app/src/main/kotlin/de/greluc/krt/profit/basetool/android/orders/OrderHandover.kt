/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandoverSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.krtToDoubleOrNull
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Log tag for the Übergabe. */
private const val LOG_TAG = "OrderHandover"

/**
 * The Übergabe form, as typed.
 *
 * @property materialId which line is being handed over.
 * @property materialName what it is called, for the sheet's subtitle.
 * @property needed how much the order asked for, as the server rendered it.
 * @property alreadyDone how much has already changed hands, as the server rendered it.
 * @property amount how much this handover carries, as typed.
 * @property stock the rows it can be booked out of.
 * @property stockId the chosen row, or `null` while none is.
 * @property recipient who receives it, as typed. The server requires a non-blank handle.
 * @property recipientSquadron their unit, as typed, blank for none.
 * @property loading whether the candidate rows are still being read.
 * @property saving whether the write is in flight.
 * @property error the last refusal.
 */
data class OrderHandoverDraft(
    val materialId: String,
    val materialName: String,
    val needed: String?,
    val alreadyDone: String?,
    val amount: String = "",
    val stock: List<HandoverStockRow> = emptyList(),
    val stockId: String? = null,
    val recipient: String = "",
    val recipientSquadron: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /**
     * Where the line stands **after** this handover, as a fraction, or `null` when it cannot be told.
     *
     * Design ch. 10 artboard 14 draws it live: „Nach dieser Übergabe 300 / 400 · 75 %". The figure
     * that finishes an Auftrag is never formed in somebody's head.
     */
    val projected: Double?
        get() {
            val need = needed?.krtToDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
            val done = alreadyDone?.krtToDoubleOrNull() ?: 0.0
            val add = amount.krtToDoubleOrNull() ?: 0.0
            return ((done + add) / need).coerceIn(0.0, 1.0)
        }

    /** How much the line will have received once this is recorded, or `null` without a number. */
    val projectedAmount: Double?
        get() {
            val add = amount.krtToDoubleOrNull() ?: return null
            return (alreadyDone?.krtToDoubleOrNull() ?: 0.0) + add
        }

    /** Whether this handover completes the line — which is what closes the Auftrag. */
    val completes: Boolean
        get() = (projected ?: 0.0) >= 1.0

    /** Whether the form may be submitted at all. */
    val submittable: Boolean
        get() =
            !saving &&
                stockId != null &&
                recipient.isNotBlank() &&
                (amount.krtToDoubleOrNull() ?: 0.0) > 0.0
}

/**
 * Recording that material changed hands.
 *
 * > **The parity gap that stopped an Auftrag being finished from the app.** Until this existed, the
 * > app could take an Auftrag on and never close it; in the web the handover is what closes it.
 *
 * > **„Ohne Lagerbezug erfassen" is not built.** Design ch. 10 artboard 14 offers it, and the
 * > endpoint cannot serve it: `JobOrderHandoverItemCreateDto.inventoryItemId` is `@NotNull`, and
 * > the web's own form refuses to submit without a row. Flagged rather than coded around.
 *
 * @property source where the read and the write go.
 * @property scope the view model's scope.
 * @property read the draft as it stands.
 * @property write reports it back.
 * @property onRecorded a handover landed; the caller re-reads the Auftrag.
 */
class OrderHandover(
    private val source: JobOrderHandoverSource,
    private val scope: CoroutineScope,
    private val read: () -> OrderHandoverDraft?,
    private val write: (OrderHandoverDraft?) -> Unit,
    private val onRecorded: () -> Unit,
) {
    /**
     * Opens the sheet for one material line and reads its candidate stock rows.
     *
     * @param orderId the Auftrag.
     * @param material the line. A line the server sent without a material id cannot be handed over
     *   — the write is addressed by it — so the sheet does not open.
     * @param alreadyDone how much of it has actually changed hands, from `JobOrder.krtHandedOver`.
     *   Never `amount - openAmount`: that counts claims, not deliveries.
     */
    fun open(
        orderId: String,
        material: JobOrderMaterial,
        alreadyDone: String?,
    ) {
        val materialId = material.materialId ?: return
        write(
            OrderHandoverDraft(
                materialId = materialId,
                materialName = material.name,
                needed = material.needed,
                alreadyDone = alreadyDone,
            ),
        )
        scope.launch {
            when (val result = source.stockFor(orderId, materialId)) {
                is ApiResult.Success -> {
                    val current = read() ?: return@launch
                    write(
                        current.copy(
                            stock = result.value,
                            // One candidate is not a choice. Preselecting it turns the common case
                            // into a single tap instead of two.
                            stockId = result.value.singleOrNull()?.id,
                            loading = false,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the handover's stock rows could not be read: ${result.error}" }
                    write(read()?.copy(loading = false, error = result.error))
                }
            }
        }
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
    fun change(change: (OrderHandoverDraft) -> OrderHandoverDraft) {
        write(change(read() ?: return))
    }

    /**
     * Sends the handover.
     *
     * @param orderId the Auftrag.
     */
    fun submit(orderId: String) {
        val draft = read()
        val stockId = draft?.stockId
        if (draft == null || stockId == null || !draft.submittable) {
            return
        }
        write(draft.copy(saving = true, error = null))
        scope.launch {
            val result =
                source.record(
                    orderId = orderId,
                    inventoryItemId = stockId,
                    amount = draft.amount,
                    recipientHandle = draft.recipient.trim(),
                    recipientSquadron = draft.recipientSquadron.trim().takeIf { it.isNotEmpty() },
                    // The device's clock, not the server's — the member is the one who witnessed
                    // the handover, which is why the web fills this in the browser too.
                    handoverTime = Instant.now().toString(),
                )
            when (result) {
                is ApiResult.Success -> {
                    write(null)
                    onRecorded()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the handover was refused: ${result.error}" }
                    write(read()?.copy(saving = false, error = result.error))
                }
            }
        }
    }
}
