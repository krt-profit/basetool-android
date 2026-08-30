/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtButtonStyles
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Test handle for the selection bar. */
const val BLUEPRINT_SELECTION_BAR_TAG: String = "blueprint-selection-bar"

/** Test handle for the import sheet. */
const val BLUEPRINT_IMPORT_SHEET_TAG: String = "blueprint-import-sheet"

/**
 * The selection mode's bottom bar — design ch. 18 §3 (E3).
 *
 * „Alles wählen" is what makes „alle löschen" reachable, and it is deliberately not a menu entry:
 * deleting 41 rows is for somebody who has seen the 41 rows.
 *
 * @param selection what is ticked.
 * @param total how many blueprints the member owns in all, which is what „Alles wählen" means and
 *   what the modal has to name — the list is paged, so the loaded count would understate it.
 * @param onSelectAll tick everything.
 * @param onCancel leave the mode.
 * @param onDelete open the confirmation.
 */
@Composable
internal fun BlueprintSelectionBar(
    selection: BlueprintSelection,
    total: Long,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm)
                .testTag(BLUEPRINT_SELECTION_BAR_TAG),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (selection.everything) {
                    pluralStringResource(R.plurals.blueprints_selected_all, total.toInt(), total)
                } else {
                    pluralStringResource(
                        R.plurals.blueprints_selected,
                        selection.ids.size,
                        selection.ids.size,
                        total,
                    )
                },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.Gray1,
        )
        KrtGhostButton(text = stringResource(R.string.blueprints_select_all), onClick = onSelectAll)
        KrtGhostButton(text = stringResource(R.string.blueprints_selection_cancel), onClick = onCancel)
        KrtButton(
            text = stringResource(R.string.blueprints_selection_delete),
            onClick = onDelete,
            style = KrtButtonStyles.quietDanger,
            enabled = selection.ids.isNotEmpty() && !selection.deleting,
        )
    }
}

/**
 * The danger modal that names the number and the consequence.
 *
 * No undo, because there is nothing to restore — which is why the second sentence says what the
 * unit's availability view will show afterwards rather than offering a way back.
 *
 * @param selection what is ticked.
 * @param total how many the member owns in all.
 * @param onConfirm delete them.
 * @param onDismiss leave them.
 */
@Composable
internal fun BlueprintDeleteConfirm(
    selection: BlueprintSelection,
    total: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = if (selection.everything) total.toInt() else selection.ids.size
    KrtModal(
        title = stringResource(R.string.blueprints_delete_title),
        confirmText = stringResource(R.string.blueprints_selection_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
    ) {
        Text(
            text = pluralStringResource(R.plurals.blueprints_delete_body, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        Text(
            text = stringResource(R.string.blueprints_delete_consequence),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The two-step file import — design ch. 18 §2 (E2).
 *
 * Step one reads the file and answers three numbers; step two writes. The CTA names the number it
 * is about to write („9 übernehmen") rather than saying „Importieren", because that number is the
 * whole reason the preview exists.
 *
 * @param step how far it has got.
 * @param onFile a file was picked and read off the device; the bytes are `null` when it could not
 *   be read there, which is not an HTTP state and gets plain German rather than the fiction canon.
 * @param onApply take over what the preview resolved.
 * @param onDismiss close the sheet; nothing is written by closing.
 */
@Composable
internal fun BlueprintImportSheet(
    step: BlueprintImportStep,
    onFile: (String, ByteArray?) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (step is BlueprintImportStep.Closed) {
        return
    }
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Read here rather than holding the Uri: the permission granted to it is scoped to this
            // activity result, and an upload one tap later would find it revoked.
            uri?.let { onFile(displayName(context, it), readBytes(context, it)) }
        }

    KrtBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag(BLUEPRINT_IMPORT_SHEET_TAG),
        title = stringResource(R.string.blueprints_import_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            when (step) {
                is BlueprintImportStep.Closed -> {}

                is BlueprintImportStep.Waiting -> {
                    ImportPrompt { picker.launch(IMPORT_MIME_TYPES) }
                }

                is BlueprintImportStep.Reading -> {
                    KrtLoadingIndicator(text = stringResource(R.string.blueprints_import_reading))
                }

                is BlueprintImportStep.Preview -> {
                    ImportPreview(step = step, onApply = onApply)
                }

                is BlueprintImportStep.Writing -> {
                    KrtLoadingIndicator(
                        text = pluralStringResource(R.plurals.blueprints_import_writing, step.count, step.count),
                    )
                }

                is BlueprintImportStep.Done -> {
                    ImportDone(step = step, onDismiss = onDismiss)
                }

                is BlueprintImportStep.Failed -> {
                    ImportFailed(onRetry = { picker.launch(IMPORT_MIME_TYPES) })
                }
            }
        }
    }
}

/**
 * Step one: say what the file is and offer to pick one.
 *
 * @param onPick open the document picker.
 */
@Composable
private fun ImportPrompt(onPick: () -> Unit) {
    Text(
        text = stringResource(R.string.blueprints_import_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.Gray1,
    )
    Text(
        text = stringResource(R.string.blueprints_import_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
    KrtOutlineButton(
        text = stringResource(R.string.blueprints_import_pick),
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Step two: three numbers, the names behind two of them, and one CTA.
 *
 * „Vorhanden" is a number and never a list — an already-owned blueprint is not a result. The rows
 * that cannot be resolved **are** named, so nobody takes the file for broken.
 *
 * @param step the preview.
 * @param onApply take it over.
 */
@Composable
private fun ImportPreview(
    step: BlueprintImportStep.Preview,
    onApply: () -> Unit,
) {
    val preview = step.preview
    Text(
        text =
            stringResource(
                R.string.blueprints_import_read,
                step.fileName,
                pluralStringResource(
                    R.plurals.blueprints_import_lines,
                    preview.entries.size,
                    preview.entries.size,
                ),
            ),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md)) {
        CountBox(
            label = stringResource(R.string.blueprints_import_new),
            value = preview.importable.size,
            tint = KrtTheme.colors.successText,
        )
        CountBox(
            label = stringResource(R.string.blueprints_import_owned),
            value = preview.alreadyOwned,
            tint = KrtPalette.TextMuted,
        )
        CountBox(
            label = stringResource(R.string.blueprints_import_unknown),
            value = preview.unresolved.size,
            tint = KrtTheme.colors.warning,
        )
    }
    NameList(
        label = stringResource(R.string.blueprints_import_new),
        names = preview.importable.map { it.productName ?: it.externalName },
    )
    if (preview.unresolved.isNotEmpty()) {
        NameList(
            label = stringResource(R.string.blueprints_import_unknown),
            names = preview.unresolved.map { it.externalName },
        )
        Text(
            text = stringResource(R.string.blueprints_import_unknown_note),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        // The server can also answer SUGGESTED: candidates found, nothing resolved. Picking between
        // them is a control the artboard does not draw and this app does not have, so those rows
        // are counted with the unknown ones and the member is told where they can be resolved.
        KrtHint(explanation = stringResource(R.string.blueprints_import_suggested_hint))
    }
    KrtCtaButton(
        text =
            pluralStringResource(
                R.plurals.blueprints_import_apply,
                preview.importable.size,
                preview.importable.size,
            ),
        onClick = onApply,
        modifier = Modifier.fillMaxWidth(),
        enabled = preview.importable.isNotEmpty(),
    )
}

/**
 * The receipt.
 *
 * @param step what was written.
 * @param onDismiss close the sheet.
 */
@Composable
private fun ImportDone(
    step: BlueprintImportStep.Done,
    onDismiss: () -> Unit,
) {
    Text(
        text = pluralStringResource(R.plurals.blueprints_import_done, step.result.added, step.result.added),
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.Gray1,
    )
    KrtCtaButton(
        text = stringResource(R.string.blueprints_import_close),
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The failure, in plain German: a file the device or the server could not read is not an HTTP state.
 *
 * @param onRetry pick another file.
 */
@Composable
private fun ImportFailed(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.blueprints_import_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = KrtTheme.colors.dangerText,
    )
    KrtOutlineButton(
        text = stringResource(R.string.blueprints_import_pick),
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One of the preview's three figures.
 *
 * @param label what it counts.
 * @param value the figure.
 * @param tint the figure's colour, which is what separates „neu" from „unbekannt" at a glance.
 */
@Composable
private fun CountBox(
    label: String,
    value: Int,
    tint: androidx.compose.ui.graphics.Color,
) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tint,
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
    }
}

/**
 * A capped list of names under one of the figures.
 *
 * The cap is **stated**, never silent (ADR-0104): a preview that quietly showed six of fourteen
 * names would read as a file that lost eight rows.
 *
 * @param label which figure these belong to.
 * @param names what to list.
 */
@Composable
private fun NameList(
    label: String,
    names: List<String>,
) {
    if (names.isEmpty()) {
        return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        color = KrtPalette.Gray1,
    )
    names.take(NAME_LIST_CAP).forEach { name ->
        Text(text = name, style = MaterialTheme.typography.bodySmall, color = KrtPalette.Gray1)
    }
    val rest = names.size - NAME_LIST_CAP
    if (rest > 0) {
        Text(
            text = pluralStringResource(R.plurals.blueprints_import_more, rest, rest),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * What a picked file is called.
 *
 * @param context the activity context.
 * @param uri the picked document.
 * @return its display name, or a fallback the server can still log.
 */
private fun displayName(
    context: Context,
    uri: Uri,
): String =
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: FALLBACK_FILE_NAME

/**
 * Reads the picked file off the device.
 *
 * @param context the activity context.
 * @param uri the picked document.
 * @return its bytes, or `null` when the provider cannot open it.
 */
private fun readBytes(
    context: Context,
    uri: Uri,
): ByteArray? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
    }.getOrNull()

/**
 * What the picker filters on.
 *
 * Two entries, not one: some providers hand a `.json` out as `text/plain` or as an octet stream,
 * and a picker that filters on `application/json` alone shows a member their own export greyed out.
 */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "*/*")

/** What a file whose provider names it nothing is called. */
private const val FALLBACK_FILE_NAME = "blueprints.json"

/** How many names are listed under a figure before the rest are counted. */
private const val NAME_LIST_CAP = 6
