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
 * being reachable by mis-tapping a settings row. It asks before it acts: see [SignOutConfirmModal].
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
 * The column itself, capped so it does not stretch across a tablet.
 *
 * Design chapter 13 lays the tablet out in two columns — Einstellungen beside Beförderung — and
 * this screen deliberately builds only the left one (ADR-0009).
 *
 * The reason is no longer the one this comment used to give. Beförderung's repository, view model
 * and screen all exist and are tested; what is missing is a **design chapter** for it, so its
 * destination renders a placeholder by the owner's decision (`krt-profit/basetool-android#66`).
 * Putting the second column in now would pair the settings with a placeholder on every tablet,
 * which is worse than the honest half.
 *
 * The half that is here still earns its cap: settings rows dragged to 1280 dp put a 44 dp toggle a
 * hand's width from its own label. Restoring the pairing is a one-place change once #66 lands —
 * this column keeps its width and gains a sibling.
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
        if (accountName != null) {
            SettingsGroup(stringResource(R.string.settings_section_account)) {
                KrtSettingRow(
                    title = accountName,
                    tone = KrtPalette.White,
                    leadingIcon = DesignR.drawable.ic_krt_user,
                )
                KrtHairlineRule(color = KrtPalette.SurfaceInput)
                // The same scope the top bar's chip names, in the place a member goes looking for a
                // setting. It opens the very sheet the chip opens — one switcher, two doors, and no
                // second copy of the state to disagree with the header (design ch. 13, artboard 2).
                KrtSettingRow(
                    title = stringResource(R.string.settings_active_org_unit),
                    subtitle = orgUnitName,
                    leadingIcon = DesignR.drawable.ic_krt_users,
                    onClick = onSwitchOrgUnit,
                ) {
                    KrtIcon(
                        id = DesignR.drawable.ic_krt_chevron_right,
                        contentDescription = null,
                        tint = KrtPalette.TextMuted,
                    )
                }
                KrtHairlineRule(color = KrtPalette.SurfaceInput)
                // The standing answer a sign-up starts from. It is a server value with a version, not a
                // device preference: the same member can change it in a browser, so an unread row shows
                // nothing rather than guessing „Auszahlung an mich" — which is a decision, not a default.
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
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            // Phrased as "allow", not "block": a switch a tester turns ON to get their screenshot
            // reads the right way round, and the subtitle carries the cost rather than a warning
            // icon nobody reads.
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
            KrtHairlineRule(color = KrtPalette.SurfaceInput)
            // Also a server value with a version. Unread reads as NOT sharing: the safe reading of
            // a flag that did not arrive is that nothing of the member's is being published.
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

        // The button asks rather than acts: sign-out destroys the stored key, so the way back is the
        // full browser flow, and this is the one control on the screen whose cost is not undoable by
        // tapping it again. Confirmation is deliberately NOT added to the gates' sign-out
        // (approval-pending, gate-unavailable, locked): there it is the only way forward, and a
        // confirmation on an escape hatch is friction, not safety.
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

        // App version and API version. The design's footer also carries a server-status dot; that
        // half stays undrawn because this build has no health signal to read it from, and an
        // always-green dot would be decoration that looks like a diagnosis — the one element a
        // member would trust during an outage. The API version needs no signal: it is what this
        // build was compiled against.
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

/**
 * The confirmation in front of sign-out.
 *
 * Danger tone, because the action destroys something: the encrypted refresh token and the Keystore
 * key that decrypts it are both deleted (`REQ-APP-AUTH-005`), and no local step brings the session
 * back. The body says exactly that in the member's own terms — what ends, and that the way back is
 * the browser sign-in form rather than a tap — instead of asking "are you sure?", which is the rule
 * [KrtModalTone.Danger] carries.
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
