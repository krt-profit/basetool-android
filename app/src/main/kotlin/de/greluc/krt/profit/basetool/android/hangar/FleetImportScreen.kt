/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.FleetImportResult
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.fieldMessage
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the paste box. */
const val FLEET_IMPORT_PASTE_TAG = "fleet-import-paste"

/** Test handle for the import CTA. */
const val FLEET_IMPORT_SUBMIT_TAG = "fleet-import-submit"

/** How tall the paste box stands before it starts scrolling its own text. */
private const val PASTE_MIN_LINES = 6

/**
 * „Fleetview-Import" (design ch. 08 §3).
 *
 * Two ways in, one endpoint: a `.json` picked through the system picker, or the export pasted into
 * the box. The server takes a file part either way, so the paste is turned into one here rather
 * than becoming a second API the backend would have to grow.
 *
 * @param state what the screen holds.
 * @param onPaste the box changed.
 * @param onFilePicked a file was chosen, with its name and content.
 * @param onFileCleared the chosen file was dropped.
 * @param onImport the CTA was pressed.
 * @param onResultDismissed the tally was acknowledged.
 */
@Composable
fun FleetImportScreen(
    state: FleetImportState,
    onPaste: (String) -> Unit,
    onFilePicked: (String, ByteArray) -> Unit,
    onFileCleared: () -> Unit,
    onImport: () -> Unit,
    onResultDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Read here rather than holding the Uri: the permission granted to it is scoped to this
            // activity result, and an upload that happens a tap later would find it revoked.
            uri?.let {
                val bytes = context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() }
                if (bytes != null) {
                    onFilePicked(displayName(context, it), bytes)
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
    ) {
        if (!state.online) {
            OfflineBand()
        }
        Text(
            text = stringResource(R.string.fleet_import_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        // The chapter names Fleetview alone; the endpoint takes three formats and says so when it
        // refuses one. Naming them here saves a member the refusal.
        Text(
            text = stringResource(R.string.fleet_import_formats),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        KrtOutlineButton(
            text = stringResource(R.string.fleet_import_pick),
            onClick = { picker.launch(arrayOf(JSON_MIME, ANY_MIME)) },
            iconRes = DesignR.drawable.ic_krt_upload,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.uploading,
        )
        state.fileName?.let { name ->
            PickedFile(name = name, enabled = !state.uploading, onClear = onFileCleared)
        }
        // A rule with the label in it, as the artboard draws it: the two ways in are alternatives,
        // and a left-aligned caption reads as a heading over the box rather than as an "or".
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtHairlineRule(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.fleet_import_or),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
            KrtHairlineRule(modifier = Modifier.weight(1f))
        }
        KrtTextField(
            value = state.pasted,
            onValueChange = onPaste,
            placeholder = stringResource(R.string.fleet_import_paste_hint),
            enabled = !state.uploading && state.fileBytes == null,
            minLines = PASTE_MIN_LINES,
            modifier = Modifier.fillMaxWidth().testTag(FLEET_IMPORT_PASTE_TAG),
        )
        state.error?.let { error -> KrtFieldError(text = importError(error)) }
        KrtCtaButton(
            text = stringResource(R.string.fleet_import_submit),
            onClick = onImport,
            iconRes = DesignR.drawable.ic_krt_upload,
            enabled = state.submittable,
            modifier = Modifier.fillMaxWidth().testTag(FLEET_IMPORT_SUBMIT_TAG),
        )
        Text(
            text = stringResource(R.string.fleet_import_note),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }

    state.result?.let { result ->
        ImportResultModal(result = result, onDismiss = onResultDismissed)
    }
}

/**
 * The chosen file, with a way to drop it again.
 *
 * Named rather than acknowledged: "Datei gewählt" leaves a member who picked the wrong export with
 * no way to notice before they upload it.
 *
 * @param name the file's display name.
 * @param enabled whether it can still be dropped.
 * @param onClear drops it.
 */
@Composable
private fun PickedFile(
    name: String,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        Text(
            text = stringResource(R.string.fleet_import_picked, name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        KrtOutlineButton(
            text = stringResource(R.string.fleet_import_clear),
            onClick = onClear,
            enabled = enabled,
        )
    }
}

/**
 * What the server made of the export.
 *
 * The three counts are shown even when they are zero: "0 Duplikate" is the answer to a question a
 * member importing a second time actually has, and leaving the row out reads as the check not
 * having run. The two name lists follow their counts, capped, because a hundred unrecognised hulls
 * are a fault to report rather than a list to read.
 *
 * @param result the tally.
 * @param onDismiss closes the modal.
 */
@Composable
private fun ImportResultModal(
    result: FleetImportResult,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.fleet_import_result_title),
        confirmText = stringResource(R.string.fleet_import_result_close),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        cancelText = "",
    ) {
        KrtKeyValueRow(
            label = stringResource(R.string.fleet_import_result_imported),
            value = result.imported.toString(),
        )
        KrtKeyValueRow(
            label = stringResource(R.string.fleet_import_result_duplicates),
            value = result.duplicates.toString(),
        )
        KrtKeyValueRow(
            label = stringResource(R.string.fleet_import_result_skipped),
            value = result.skipped.toString(),
        )
        if (result.skippedShips.isNotEmpty()) {
            KrtHairlineRule()
            Text(
                text = stringResource(R.string.fleet_import_result_skipped_list),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
            NameList(names = result.skippedShips)
        }
        if (result.duplicateShips.isNotEmpty()) {
            KrtHairlineRule()
            Text(
                text = stringResource(R.string.fleet_import_result_duplicate_list),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
            NameList(names = result.duplicateShips)
        }
    }
}

/**
 * A capped list of ship names, saying so when it caps.
 *
 * @param names the names to show.
 */
@Composable
private fun NameList(names: List<String>) {
    names.take(NAME_LIST_MAX).forEach { name ->
        Text(text = name, style = MaterialTheme.typography.bodySmall, color = KrtPalette.Gray1)
    }
    if (names.size > NAME_LIST_MAX) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.fleet_import_result_more,
                    names.size - NAME_LIST_MAX,
                    names.size - NAME_LIST_MAX,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * What to say about a refused import.
 *
 * @param error the classified failure.
 * @return the message resource's text.
 */
@Composable
private fun importError(error: ApiError): String {
    // The server diagnoses the file — "Die Datei muss ein JSON-Array enthalten", "Unbekanntes
    // Format" — and it is the only party that can. Its own sentence is shown when it sent one; the
    // app's fallback covers a refusal that arrived without one.
    val named = error.fieldMessage()
    return named ?: stringResource(
        when (error) {
            is ApiError.Forbidden -> R.string.fleet_import_error_forbidden
            is ApiError.Validation -> R.string.fleet_import_error_shape
            else -> R.string.write_failed
        },
    )
}

/**
 * The display name behind a content Uri.
 *
 * @param context used for the resolver.
 * @param uri the picked document.
 * @return the name the picker shows, or a fallback when the provider supplies none.
 */
private fun displayName(
    context: android.content.Context,
    uri: Uri,
): String =
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: FALLBACK_FILE_NAME

/** MIME type of a Fleetview export. */
private const val JSON_MIME = "application/json"

/**
 * The second MIME the picker offers.
 *
 * Some providers hand a `.json` out as `text/plain` or as an octet stream, and a picker that
 * filters on `application/json` alone shows a member their own export greyed out.
 */
private const val ANY_MIME = "*/*"

/** What a file whose provider names it nothing is called. */
private const val FALLBACK_FILE_NAME = "fleetview.json"

/** How many names of a group the result modal spells out. */
private const val NAME_LIST_MAX = 12

/**
 * The Fleetview import, wired to its ViewModel.
 *
 * @param viewModel the screen's driver.
 */
@Composable
fun FleetImportRoute(viewModel: FleetImportViewModel) {
    val state by viewModel.state.collectAsState()
    FleetImportScreen(
        state = state,
        onPaste = viewModel::onPasted,
        onFilePicked = viewModel::onFilePicked,
        onFileCleared = viewModel::onFileCleared,
        onImport = viewModel::onImport,
        onResultDismissed = viewModel::onResultDismissed,
    )
}
