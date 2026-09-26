/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial

/**
 * The material's own unit word, never a hardcoded SCU.
 *
 * @return the word to put after the figure, or an empty string when the server named no unit.
 */
@Composable
internal fun JobOrderMaterial.unitWord(): String =
    when (unit) {
        "PIECE" -> stringResource(R.string.materials_unit_piece)
        "SCU" -> stringResource(R.string.materials_unit_scu)
        else -> ""
    }
