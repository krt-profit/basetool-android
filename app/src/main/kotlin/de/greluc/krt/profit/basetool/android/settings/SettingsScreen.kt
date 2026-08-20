/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFanKitBand
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSettingRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Einstellungen — the app's own settings, the legal texts and the way out (design ch. 13).
 *
 * What is here is what the **app** decides on its own. The chapter also draws the member's rank,
 * the active org unit, the payout preference and a blueprint-sharing switch; every one of those is
 * a value the backend owns, and none of the endpoints behind them is consumed yet. They arrive with
 * the read-only member core rather than being drawn now from placeholder data — a settings screen
 * that shows a rank nobody set is worse than one that does not show a rank at all. The same holds
 * for the chapter's "Lokale Daten löschen": there is no offline cache to delete yet, and a
 * destructive-looking button that does nothing teaches members to distrust the ones that do.
 *
 * Sign-out lives at the bottom of this screen, which is where the design puts it and where it stops
 * being reachable by mis-tapping a settings row.
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
 * @param onLogout ends the session.
 * @param versionName the app's version name.
 * @param versionCode the app's build number.
 * @param modifier layout modifier.
 */
@Composable
fun SettingsScreen(
    accountName: String?,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenImprint: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenLicenses: () -> Unit,
    onLogout: () -> Unit,
    versionName: String,
    versionCode: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.lg),
    ) {
        if (accountName != null) {
            SettingsGroup(stringResource(R.string.settings_section_account)) {
                KrtSettingRow(
                    title = accountName,
                    tone = KrtPalette.White,
                    leadingIcon = DesignR.drawable.ic_krt_user,
                )
            }
        }

        SettingsGroup(stringResource(R.string.settings_section_app)) {
            KrtSettingRow(
                title = stringResource(R.string.settings_language),
                leadingIcon = DesignR.drawable.ic_krt_globe,
            ) {
                // No row-level onClick: the row cannot know which segment was meant.
                KrtSegmentedControl(
                    options = AppLanguage.entries.map { it.tag.uppercase() },
                    selectedIndex = AppLanguage.entries.indexOf(language),
                    onSelect = { index -> onLanguageChange(AppLanguage.entries[index]) },
                )
            }
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            // Disabled rather than hidden when the device has no screen lock: hiding it would read
            // as a missing feature, and the subtitle names the one thing the member can do about it.
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

        KrtQuietDangerButton(
            text = stringResource(R.string.logout),
            onClick = onLogout,
            iconRes = DesignR.drawable.ic_krt_logout,
            modifier = Modifier.fillMaxWidth(),
        )

        // App version only. The design's footer also carries a server-status dot and the API
        // version; both describe the link to the backend, and this build has no health signal to
        // read them from — an always-green dot would be decoration that looks like a diagnosis.
        Text(
            text = stringResource(R.string.settings_version, versionName, versionCode),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.Gray2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = KrtSpacing.md),
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        KrtSectionTitle(text = title)
        KrtCard(variant = KrtCardVariant.Flush, content = content)
    }
}

/**
 * A row that leaves the app for a web page.
 *
 * The trailing glyph is the external-link one, not a chevron: the two mean different things and a
 * member is entitled to know before tapping that the browser is about to open.
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

/** Size of a settings row's trailing glyph. */
private val TRAILING_ICON = 16.dp

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
                onOpenPrivacy = {},
                onOpenImprint = {},
                onOpenTerms = {},
                onOpenLicenses = {},
                onLogout = {},
                versionName = "0.1.0-alpha01",
                versionCode = 1,
            )
        }
    }
}
