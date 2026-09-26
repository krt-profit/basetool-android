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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.PayoutPreference
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFanKitBand
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSettingRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.API_VERSION
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Einstellungen: the app's own settings, the legal texts and sign-out.
 *
 * Shows the active org unit, the payout preference, the blueprint-sharing switch and a
 * „Screenshots erlauben" switch; no rank and no „Lokale Daten löschen". Sign-out sits at the bottom
 * and asks first via [SignOutConfirmModal].
 *
 * @param accountName the signed-in member's username, from the ID token; `null` while unknown.
 * @param language the language currently on screen.
 * @param onLanguageChange pins a language; the activity is recreated by the platform.
 * @param appLockEnabled whether a lock is armed.
 * @param appLockAvailable whether the device can prompt at all.
 * @param onAppLockChange arms or disarms the lock; arming raises the biometric prompt.
 * @param onOpenPrivacy opens the privacy policy in a browser.
 * @param onOpenImprint opens the imprint in a browser.
 * @param onOpenTerms opens the terms of use in a browser.
 * @param onOpenLicenses opens the in-app open-source notice.
 * @param onLogout ends the session; invoked only after the member confirms.
 * @param versionName the app's version name.
 * @param versionCode the app's build number.
 * @param modifier layout modifier.
 */
@Composable
fun SettingsScreen(
    accountName: String?,
    orgUnitName: String?,
    onSwitchOrgUnit: () -> Unit,
    preferences: MemberPreferencesState,
    onPayout: (PayoutPreference) -> Unit,
    onSharing: (Boolean) -> Unit,
    onRetryPreferences: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    screenCaptureAllowed: Boolean,
    onScreenCaptureChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenLicenses: () -> Unit,
    onLogout: () -> Unit,
    versionName: String,
    versionCode: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        SettingsColumn(
            accountName = accountName,
            orgUnitName = orgUnitName,
            onSwitchOrgUnit = onSwitchOrgUnit,
            preferences = preferences,
            onPayout = onPayout,
            onSharing = onSharing,
            onRetryPreferences = onRetryPreferences,
            language = language,
            onLanguageChange = onLanguageChange,
            appLockEnabled = appLockEnabled,
            appLockAvailable = appLockAvailable,
            onAppLockChange = onAppLockChange,
            screenCaptureAllowed = screenCaptureAllowed,
            onScreenCaptureChange = onScreenCaptureChange,
            onOpenPrivacy = onOpenPrivacy,
            onOpenImprint = onOpenImprint,
            onOpenTerms = onOpenTerms,
            onOpenLicenses = onOpenLicenses,
            onLogout = onLogout,
            versionName = versionName,
            versionCode = versionCode,
        )
    }
}

/**
 * The settings column, width-capped so it does not stretch across a tablet (ADR-0009).
 *
 * On a tablet it is the only column; the Beförderung column beside it is not built.
 *
 * @param accountName the signed-in member's username, or `null` while unknown.
 * @param language the language currently on screen.
 * @param onLanguageChange pins a language.
 * @param appLockEnabled whether a lock is armed.
 * @param appLockAvailable whether the device can prompt at all.
 * @param onAppLockChange arms or disarms the lock.
 * @param screenCaptureAllowed whether screenshots and screen recording are permitted.
 * @param onScreenCaptureChange permits or forbids them.
 * @param onOpenPrivacy opens the privacy policy.
 * @param onOpenImprint opens the imprint.
 * @param onOpenTerms opens the terms of use.
 * @param onOpenLicenses opens the open-source notice.
 * @param onLogout ends the session; invoked only after the member confirms.
 * @param versionName the app's version name.
 * @param versionCode the app's build number.
 */
@Composable
private fun SettingsColumn(
    accountName: String?,
    orgUnitName: String?,
    onSwitchOrgUnit: () -> Unit,
    preferences: MemberPreferencesState,
    onPayout: (PayoutPreference) -> Unit,
    onSharing: (Boolean) -> Unit,
    onRetryPreferences: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    screenCaptureAllowed: Boolean,
    onScreenCaptureChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenLicenses: () -> Unit,
    onLogout: () -> Unit,
    versionName: String,
    versionCode: Int,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = COLUMN_MAX_WIDTH)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
    ) {
        AccountGroup(
            accountName = accountName,
            orgUnitName = orgUnitName,
            onSwitchOrgUnit = onSwitchOrgUnit,
            preferences = preferences,
            onPayout = onPayout,
            onSharing = onSharing,
            onRetryPreferences = onRetryPreferences,
        )

        SettingsGroup(stringResource(R.string.settings_section_app)) {
            KrtSettingRow(
                title = stringResource(R.string.settings_language),
                leadingIcon = DesignR.drawable.ic_krt_globe,
            ) {
                KrtSegmentedControl(
                    options = AppLanguage.entries.map { it.tag.uppercase() },
                    selectedIndex = AppLanguage.entries.indexOf(language),
                    onSelect = { index -> onLanguageChange(AppLanguage.entries[index]) },
                )
            }
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            KrtSettingRow(
                title = stringResource(R.string.lock_setting),
                subtitle =
                    stringResource(
                        when {
                            !appLockAvailable -> R.string.lock_setting_unavailable
                            appLockEnabled -> R.string.lock_setting_on
                            else -> R.string.lock_setting_off
                        },
                    ),
                leadingIcon = DesignR.drawable.ic_krt_fingerprint,
                enabled = appLockAvailable,
                onClick = { onAppLockChange(!appLockEnabled) },
            ) {
                KrtToggle(checked = appLockEnabled, enabled = appLockAvailable)
            }
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            KrtSettingRow(
                title = stringResource(R.string.screencapture_setting),
                subtitle =
                    stringResource(
                        if (screenCaptureAllowed) {
                            R.string.screencapture_setting_on
                        } else {
                            R.string.screencapture_setting_off
                        },
                    ),
                leadingIcon = DesignR.drawable.ic_krt_eye,
                onClick = { onScreenCaptureChange(!screenCaptureAllowed) },
            ) {
                KrtToggle(checked = screenCaptureAllowed)
            }
        }

        SettingsGroup(stringResource(R.string.settings_section_legal)) {
            ExternalRow(R.string.settings_privacy, DesignR.drawable.ic_krt_shield, onOpenPrivacy)
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            ExternalRow(R.string.settings_imprint, DesignR.drawable.ic_krt_info, onOpenImprint)
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            ExternalRow(R.string.settings_terms, DesignR.drawable.ic_krt_clipboard_check, onOpenTerms)
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            KrtSettingRow(
                title = stringResource(R.string.licenses_title),
                leadingIcon = DesignR.drawable.ic_krt_list,
                onClick = onOpenLicenses,
            ) {
                TrailingGlyph(DesignR.drawable.ic_krt_chevron_right)
            }
        }

        KrtFanKitBand()

        var confirmingSignOut by rememberSaveable { mutableStateOf(false) }

        KrtQuietDangerButton(
            text = stringResource(R.string.logout),
            onClick = { confirmingSignOut = true },
            iconRes = DesignR.drawable.ic_krt_logout,
            modifier = Modifier.fillMaxWidth().testTag(SETTINGS_LOGOUT_TAG),
        )

        if (confirmingSignOut) {
            SignOutConfirmModal(
                onConfirm = {
                    confirmingSignOut = false
                    onLogout()
                },
                onDismiss = { confirmingSignOut = false },
            )
        }

        Text(
            text =
                stringResource(R.string.settings_version, versionName, versionCode, API_VERSION),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = KrtSpacing.s12),
        )
    }
}

/**
 * Explains why the two server-side account rows are disabled, when they are, and offers a retry.
 *
 * Shows a failed read or a refused write; the rows stay disabled without a read version.
 *
 * @param preferences the two rows' state.
 * @param onRetry re-read both values.
 */
@Composable
private fun PreferencesNotice(
    preferences: MemberPreferencesState,
    onRetry: () -> Unit,
) {
    val readError = preferences.readError
    val writeError = preferences.error
    if (readError == null && writeError == null) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        Text(
            text =
                stringResource(
                    if (readError != null) {
                        R.string.settings_prefs_read_failed
                    } else {
                        R.string.settings_prefs_write_failed
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        if (readError != null) {
            KrtOutlineButton(
                text = stringResource(R.string.settings_prefs_retry),
                onClick = onRetry,
                enabled = !preferences.reading,
                iconRes = DesignR.drawable.ic_krt_reset,
            )
        }
    }
}

/**
 * The KONTO group: who the member is, their active scope, and their two server-side settings
 * (ADR-0021).
 *
 * Everything here belongs to the account rather than the device.
 *
 * @param accountName the signed-in member's username, or `null` while unknown.
 * @param orgUnitName the active scope's name, or `null` while unknown.
 * @param onSwitchOrgUnit open the scope switcher.
 * @param preferences the two server-side rows.
 * @param onPayout set where the member's share goes by default.
 * @param onSharing share or unshare the member's blueprints.
 * @param onRetryPreferences re-read both values after a failed read.
 */
@Composable
@Suppress("LongParameterList")
private fun AccountGroup(
    accountName: String?,
    orgUnitName: String?,
    onSwitchOrgUnit: () -> Unit,
    preferences: MemberPreferencesState,
    onPayout: (PayoutPreference) -> Unit,
    onSharing: (Boolean) -> Unit,
    onRetryPreferences: () -> Unit,
) {
    if (accountName != null || preferences.readError != null || preferences.error != null) {
        SettingsGroup(stringResource(R.string.settings_section_account)) {
            if (accountName != null) {
                KrtSettingRow(
                    title = accountName,
                    tone = KrtPalette.White,
                    leadingIcon = DesignR.drawable.ic_krt_user,
                )
                KrtHairlineRule(color = KrtPalette.SurfaceInput)
            }
            KrtSettingRow(
                title = stringResource(R.string.settings_active_org_unit),
                leadingIcon = DesignR.drawable.ic_krt_users,
                onClick = onSwitchOrgUnit,
            ) {
                SettingValue(orgUnitName)
                KrtIcon(
                    id = DesignR.drawable.ic_krt_chevron_right,
                    contentDescription = null,
                    tint = KrtPalette.TextMuted,
                )
            }
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            KrtSettingRow(
                title = stringResource(R.string.settings_payout_preference),
                subtitle =
                    stringResource(
                        when (preferences.payout) {
                            PayoutPreference.PAYOUT -> R.string.mission_detail_payout_self
                            PayoutPreference.DONATE -> R.string.mission_detail_payout_org
                            null -> R.string.settings_payout_unset
                        },
                    ),
                leadingIcon = DesignR.drawable.ic_krt_bank,
                enabled = preferences.payout != null && !preferences.saving,
                onClick = {
                    onPayout(
                        if (preferences.payout == PayoutPreference.DONATE) {
                            PayoutPreference.PAYOUT
                        } else {
                            PayoutPreference.DONATE
                        },
                    )
                },
            ) {
                KrtIcon(
                    id = DesignR.drawable.ic_krt_chevron_right,
                    contentDescription = null,
                    tint = KrtPalette.TextMuted,
                )
            }
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            KrtSettingRow(
                title = stringResource(R.string.settings_blueprint_sharing),
                subtitle = stringResource(R.string.settings_blueprint_sharing_hint),
                leadingIcon = DesignR.drawable.ic_krt_blueprint,
                enabled = preferences.sharing != null && !preferences.saving,
                onClick = { onSharing(preferences.sharing != true) },
            ) {
                KrtToggle(
                    checked = preferences.sharing == true,
                    enabled = preferences.sharing != null && !preferences.saving,
                )
            }
            PreferencesNotice(preferences = preferences, onRetry = onRetryPreferences)
        }
    }
}

/**
 * A titled group of settings rows, drawn as one bordered block.
 *
 * @param title the group heading.
 * @param content the rows, separated by hairlines by the caller.
 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        KrtSectionTitle(text = title)
        KrtCard(variant = KrtCardVariant.Flush, content = content)
    }
}

/**
 * A row that opens a web page in the browser, marked with the external-link glyph.
 *
 * @param label string resource of the row's label.
 * @param icon leading glyph.
 * @param onClick opens the page.
 */
@Composable
private fun ExternalRow(
    label: Int,
    icon: Int,
    onClick: () -> Unit,
) {
    KrtSettingRow(
        title = stringResource(label),
        leadingIcon = icon,
        onClick = onClick,
    ) {
        TrailingGlyph(DesignR.drawable.ic_krt_external_link)
    }
}

/**
 * The muted trailing glyph of a navigating settings row.
 *
 * @param iconRes the glyph.
 */
@Composable
private fun TrailingGlyph(iconRes: Int) {
    KrtIcon(
        id = iconRes,
        contentDescription = null,
        size = TRAILING_ICON,
        tint = KrtPalette.Gray2,
    )
}

/**
 * The danger-tone confirmation in front of sign-out.
 *
 * Signing out deletes the encrypted refresh token and its Keystore key (REQ-APP-AUTH-005); the body
 * says so and that the way back is the browser sign-in.
 *
 * @param onConfirm the member confirmed; the caller signs out.
 * @param onDismiss cancel, back or a scrim tap; nothing happens.
 */
@Composable
private fun SignOutConfirmModal(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.logout_confirm_title),
        confirmText = stringResource(R.string.logout_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        cancelText = stringResource(R.string.logout_confirm_cancel),
        modifier = Modifier.testTag(SETTINGS_LOGOUT_CONFIRM_TAG),
    ) {
        Text(
            text = stringResource(R.string.logout_confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
}

/** Test tag of the sign-out button at the foot of the screen. */
const val SETTINGS_LOGOUT_TAG: String = "settings-logout"

/** Test tag of the sign-out confirmation modal. */
const val SETTINGS_LOGOUT_CONFIRM_TAG: String = "settings-logout-confirm"

/** Size of a settings row's trailing glyph. */
private val TRAILING_ICON = 16.dp

/** Width cap of the settings column; the same figure the login screen uses (design ch. 04). */
private val COLUMN_MAX_WIDTH = 480.dp

@Preview(name = "Einstellungen", showBackground = true, backgroundColor = 0xFF000000, widthDp = 412)
@Composable
private fun SettingsPreview() {
    KrtTheme {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SettingsScreen(
                accountName = "GrafRotz",
                language = AppLanguage.German,
                onLanguageChange = {},
                appLockEnabled = true,
                appLockAvailable = true,
                onAppLockChange = {},
                screenCaptureAllowed = false,
                onScreenCaptureChange = {},
                onOpenPrivacy = {},
                orgUnitName = "Bereich Profit",
                onSwitchOrgUnit = {},
                preferences =
                    MemberPreferencesState(
                        payout = PayoutPreference.PAYOUT,
                        sharing = true,
                        version = 1,
                    ),
                onPayout = {},
                onRetryPreferences = {},
                onSharing = {},
                onOpenImprint = {},
                onOpenTerms = {},
                onOpenLicenses = {},
                onLogout = {},
                versionName = "0.1.0",
                versionCode = 1,
            )
        }
    }
}

/**
 * A setting's current value, in the trailing slot of its row.
 *
 * @param value the value, or `null`/blank when there is none to show.
 */
@Composable
private fun SettingValue(value: String?) {
    value?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = KrtPalette.White,
        )
    }
}
