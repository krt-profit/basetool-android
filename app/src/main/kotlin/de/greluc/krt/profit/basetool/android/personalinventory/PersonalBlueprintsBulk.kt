/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import de.greluc.krt.profit.basetool.android.core.data.BlueprintImportPreview
import de.greluc.krt.profit.basetool.android.core.data.BlueprintImportResult
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/**
 * The blueprint list's selection mode: long press, orange bar, ticks and a bottom bar.
 *
 * It is the only entry to „alle löschen", because `DELETE /personal-blueprints` deletes every row
 * and takes no ids.
 *
 * @property ids which rows are ticked; may be empty.
 * @property everything whether every row the member owns is ticked, not merely every row on screen;
 *   only then may the one-call delete be used.
 * @property deleting whether a delete is running.
 * @property asking whether the danger modal is open.
 */
data class BlueprintSelection(
    val ids: Set<String> = emptySet(),
    val everything: Boolean = false,
    val deleting: Boolean = false,
    val asking: Boolean = false,
)

/**
 * How far the file import has got — design ch. 18 §2 (E2).
 *
 * Two calls, two steps, and only the second writes: `preview` reads the file and answers three
 * numbers, `apply` takes them over. A one-step import would be a mass write with no preview.
 */
sealed interface BlueprintImportStep {
    /** Nothing is going on; the screen shows its ordinary list. */
    data object Closed : BlueprintImportStep

    /** The sheet is open and waiting for a file to be picked. */
    data object Waiting : BlueprintImportStep

    /**
     * The file is being read.
     *
     * @property fileName what the member picked, so the spinner can name it.
     */
    data class Reading(
        val fileName: String,
    ) : BlueprintImportStep

    /**
     * The server answered what it found. **Nothing has been written yet.**
     *
     * @property fileName the file this came from.
     * @property preview what it contains.
     */
    data class Preview(
        val fileName: String,
        val preview: BlueprintImportPreview,
    ) : BlueprintImportStep

    /**
     * The apply is running.
     *
     * @property count how many rows it is writing, which is what the CTA said it would.
     */
    data class Writing(
        val count: Int,
    ) : BlueprintImportStep

    /**
     * It is written.
     *
     * @property result what the server reported.
     */
    data class Done(
        val result: BlueprintImportResult,
    ) : BlueprintImportStep

    /**
     * The import failed.
     *
     * @property error what went wrong, or `null` when the file itself was unreadable on the device.
     */
    data class Failed(
        val error: ApiError?,
    ) : BlueprintImportStep
}
