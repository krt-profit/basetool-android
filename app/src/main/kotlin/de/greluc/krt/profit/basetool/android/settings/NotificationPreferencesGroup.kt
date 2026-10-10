/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.NotificationPreference
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSettingRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The BENACHRICHTIGUNGEN group of Einstellungen: one switch per notification type, under the area
 * the type belongs to (REQ-APP-NOTIF-017).
 *
 * The switch states what the member receives, so it is on unless the type is muted. A type that
 * cannot be muted is shown on and disabled, with the reason beneath it. Until the list has been read
 * the group shows a loading line, and a failed read shows its message with a retry.
 *
 * @param preferences the switches' state.
 * @param actions the group's callbacks.
 */
@Composable
internal fun NotificationPreferencesGroup(
    preferences: NotificationPreferencesState,
    actions: NotificationPreferenceActions,
) {
    SettingsGroup(stringResource(R.string.settings_section_notifications)) {
        Text(
            text = stringResource(R.string.settings_notifications_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12),
        )
        when {
            !preferences.read && preferences.readError != null -> {
                ReadFailedNotice(reading = preferences.reading, onRetry = actions.onRetry)
            }

            !preferences.read -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.settings_notifications_loading),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12)
                            .testTag(NOTIFICATION_PREFS_LOADING_TAG),
                )
            }

            preferences.rows.isEmpty() -> {
                Text(
                    text = stringResource(R.string.settings_notifications_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12)
                            .testTag(NOTIFICATION_PREFS_EMPTY_TAG),
                )
            }

            else -> {
                NotificationAreas(preferences = preferences, onToggle = actions.onToggle)
            }
        }
        if (preferences.writeError != null) {
            Text(
                text = stringResource(R.string.settings_notifications_write_failed),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12)
                        .testTag(NOTIFICATION_PREFS_WRITE_FAILED_TAG),
            )
        }
    }
}

/**
 * Every area that has a type, in the fixed order of [NotificationArea], each with its rows in the
 * order the server listed them.
 *
 * @param preferences the switches' state.
 * @param onToggle reports a tapped switch.
 */
@Composable
private fun NotificationAreas(
    preferences: NotificationPreferencesState,
    onToggle: (String, Boolean) -> Unit,
) {
    val byArea = preferences.rows.groupBy { NotificationArea.of(it.type) }
    NotificationArea.entries.filter { it in byArea }.forEach { area ->
        KrtHairlineRule(color = KrtPalette.SurfaceInput)
        Text(
            text = stringResource(area.label).krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
        )
        byArea.getValue(area).forEachIndexed { index, row ->
            if (index > 0) {
                KrtHairlineRule(color = KrtPalette.SurfaceInput)
            }
            NotificationSwitchRow(
                row = row,
                busy = row.type in preferences.pending,
                onToggle = onToggle,
            )
        }
    }
}

/**
 * One notification type as a settings row with a square switch.
 *
 * @param row the type and its switch.
 * @param busy whether its write is in flight; the row keeps its look, the view model ignores a
 *   second tap.
 * @param onToggle reports the tap with the state the member asked for.
 */
@Composable
private fun NotificationSwitchRow(
    row: NotificationPreference,
    busy: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    val receives = !row.muted
    KrtSettingRow(
        title = stringResource(notificationPreferenceLabelRes(row.type) ?: R.string.notification_pref_unknown),
        subtitle = if (row.mutable) null else stringResource(R.string.settings_notifications_locked),
        enabled = row.mutable,
        onClick = { if (!busy) onToggle(row.type, !receives) },
        modifier =
            Modifier
                .testTag(notificationPreferenceTag(row.type))
                .semantics { toggleableState = if (receives) ToggleableState.On else ToggleableState.Off },
    ) {
        KrtToggle(checked = receives, enabled = row.mutable)
    }
}

/**
 * The message under a list that could not be read, with the retry.
 *
 * @param reading whether a read is running; the retry is disabled meanwhile.
 * @param onRetry repeats the read.
 */
@Composable
private fun ReadFailedNotice(
    reading: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12)
                .testTag(NOTIFICATION_PREFS_READ_FAILED_TAG),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        Text(
            text = stringResource(R.string.settings_notifications_read_failed),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        KrtOutlineButton(
            text = stringResource(R.string.settings_prefs_retry),
            onClick = onRetry,
            enabled = !reading,
            iconRes = DesignR.drawable.ic_krt_reset,
        )
    }
}

/**
 * The test tag of one type's row.
 *
 * @param type the type constant.
 * @return the tag.
 */
internal fun notificationPreferenceTag(type: String): String = "notification-pref-$type"

/** Test tag of the loading line. */
const val NOTIFICATION_PREFS_LOADING_TAG: String = "notification-prefs-loading"

/** Test tag of the line shown when the server lists no type. */
const val NOTIFICATION_PREFS_EMPTY_TAG: String = "notification-prefs-empty"

/** Test tag of the failed-read message. */
const val NOTIFICATION_PREFS_READ_FAILED_TAG: String = "notification-prefs-read-failed"

/** Test tag of the failed-write message. */
const val NOTIFICATION_PREFS_WRITE_FAILED_TAG: String = "notification-prefs-write-failed"
