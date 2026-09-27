/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import kotlinx.coroutines.delay
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The last KONTO row: the member's optional RSI handle, edited in place (design ch. 19 artboard 10).
 *
 * The purpose sentence stands under the field at all times; a taken or malformed handle is refused
 * at the field with a danger frame and the input stays. The row never says whom a taken handle
 * belongs to — the server does not disclose it.
 *
 * @param rsi the row's state.
 * @param enabled whether the member may edit now: the handle has been read and no write runs.
 * @param actions the row's callbacks.
 */
@Composable
internal fun RsiHandleRow(
    rsi: RsiHandleState,
    enabled: Boolean,
    actions: RsiHandleActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12)
                .testTag(SETTINGS_RSI_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.Top,
    ) {
        KrtIcon(
            id = DesignR.drawable.ic_krt_shield,
            contentDescription = null,
            size = RSI_ICON,
            tint = if (enabled) KrtPalette.TextMuted else KrtPalette.Gray3,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            RsiHandleTitle()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s8),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtTextField(
                    value = rsi.draft,
                    onValueChange = actions.onDraft,
                    placeholder = stringResource(R.string.settings_rsi_handle_placeholder),
                    enabled = enabled,
                    isError = rsi.refusal != null,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.weight(1f).testTag(SETTINGS_RSI_FIELD_TAG),
                )
                if (rsi.saving) {
                    Box(modifier = Modifier.size(SAVE_SLOT), contentAlignment = Alignment.Center) {
                        KrtSpinner()
                    }
                } else {
                    KrtGhostButton(
                        text = stringResource(R.string.settings_rsi_handle_save),
                        onClick = actions.onSave,
                        enabled = enabled && rsi.changed,
                        compact = true,
                        modifier = Modifier.testTag(SETTINGS_RSI_SAVE_TAG),
                    )
                }
            }
            when (rsi.refusal) {
                RsiHandleRefusal.TAKEN -> KrtFieldError(stringResource(R.string.settings_rsi_handle_taken))
                RsiHandleRefusal.INVALID -> KrtFieldError(stringResource(R.string.settings_rsi_handle_invalid))
                null -> Unit
            }
            Text(
                text = stringResource(R.string.settings_rsi_handle_purpose),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.padding(top = KrtSpacing.s4),
            )
        }
    }
}

/** „RSI-Handle" with its muted „(optional)". */
@Composable
private fun RsiHandleTitle() {
    val optional = stringResource(R.string.settings_rsi_handle_optional)
    val title = stringResource(R.string.settings_rsi_handle)
    Text(
        text =
            buildAnnotatedString {
                append(title)
                append(' ')
                withStyle(SpanStyle(color = KrtPalette.TextMuted, fontSize = OPTIONAL_SIZE)) {
                    append(optional)
                }
            },
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.Gray1,
    )
}

/**
 * „RSI-Handle gespeichert." at the foot of the screen, gone after a few seconds.
 *
 * @param shown whether the toast is due.
 * @param onShown the toast has run its course.
 */
@Composable
internal fun RsiHandleSavedToast(
    shown: Boolean,
    onShown: () -> Unit,
) {
    if (!shown) {
        return
    }
    LaunchedEffect(Unit) {
        delay(TOAST_MS)
        onShown()
    }
    Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.BottomCenter) {
        KrtToast(
            title = stringResource(R.string.settings_rsi_handle_saved_title),
            message = stringResource(R.string.settings_rsi_handle_saved),
            modifier =
                Modifier
                    .padding(horizontal = KrtSpacing.s16)
                    .padding(bottom = KrtSpacing.s16)
                    .testTag(SETTINGS_RSI_TOAST_TAG),
        )
    }
}

/** Test tag of the RSI-handle row. */
const val SETTINGS_RSI_ROW_TAG: String = "settings-rsi-row"

/** Test tag of the RSI-handle field. */
const val SETTINGS_RSI_FIELD_TAG: String = "settings-rsi-field"

/** Test tag of the RSI-handle „Speichern". */
const val SETTINGS_RSI_SAVE_TAG: String = "settings-rsi-save"

/** Test tag of the „RSI-Handle gespeichert." toast. */
const val SETTINGS_RSI_TOAST_TAG: String = "settings-rsi-toast"

/** The row's leading glyph, as the other settings rows draw theirs. */
private val RSI_ICON = 20.dp

/** The slot the spinner takes while the button is away. */
private val SAVE_SLOT = 48.dp

/** Type size of „(optional)". */
private val OPTIONAL_SIZE = 11.sp

/** How long the success toast stays. */
private const val TOAST_MS = 3_000L
