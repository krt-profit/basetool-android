/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BlueprintSource
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette

/** Test handle for the „Herkunft" line of a blueprint's detail. */
const val BLUEPRINT_SOURCE_TAG: String = "blueprint-source"

/**
 * Where an owned blueprint came from, as one muted line (REQ-APP-PI-016).
 *
 * Draws nothing when the server recorded no source. The exchange client is named by its client id,
 * as the web names it.
 *
 * @param entry the blueprint.
 * @param modifier layout modifier.
 */
@Composable
fun BlueprintSourceLine(
    entry: OwnedBlueprint,
    modifier: Modifier = Modifier,
) {
    val source = entry.source ?: return
    val name = stringResource(source.labelRes())
    val client = entry.sourceClientId
    Text(
        text =
            if (client == null) {
                stringResource(R.string.blueprints_source, name)
            } else {
                stringResource(R.string.blueprints_source_via, name, client)
            },
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = modifier.testTag(BLUEPRINT_SOURCE_TAG),
    )
}

/**
 * The name of a source, in the web's words.
 *
 * @return the string resource.
 */
@StringRes
private fun BlueprintSource.labelRes(): Int =
    when (this) {
        BlueprintSource.LOG -> R.string.blueprints_source_log
        BlueprintSource.MANUAL -> R.string.blueprints_source_manual
        BlueprintSource.IMPORT -> R.string.blueprints_source_import
        BlueprintSource.DEFAULT -> R.string.blueprints_source_default
        BlueprintSource.OTHER -> R.string.blueprints_source_other
    }
