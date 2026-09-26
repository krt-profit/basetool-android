/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.greluc.krt.profit.basetool.android.auth.CustomTabLauncher
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.settings.LanguageSetting
import de.greluc.krt.profit.basetool.android.settings.LicensesScreen
import de.greluc.krt.profit.basetool.android.settings.MemberPreferencesState
import de.greluc.krt.profit.basetool.android.settings.ScreenCapturePreference
import de.greluc.krt.profit.basetool.android.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Shows Einstellungen on a device without a session, as a dev-flavour launcher entry.
 *
 * The language control, legal links, open-source notice and screenshot switch are wired as in the
 * app; the account name is fixed text and the app-lock toggle flips a local boolean. An
 * `AppCompatActivity` so the per-app language backport below API 33 applies (ADR-0007). Dev flavour
 * only.
 */
class SettingsPreviewActivity : AppCompatActivity() {
    /**
     * Installs the settings screen with a one-level push to the open-source notice.
     *
     * @param savedInstanceState the recreation state; a language change recreates this activity,
     *   which is the behaviour being looked at.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followScreenCapturePreference()
        enableEdgeToEdge()
        setContent {
            KrtTheme {
                val captureBlocked by screenCapturePreference.blocked.collectAsState(initial = true)
                var licenses by remember { mutableStateOf(false) }
                var locked by remember { mutableStateOf(false) }
                var language by remember { mutableStateOf(LanguageSetting.current()) }

                if (licenses) {
                    BackHandler { licenses = false }
                    LicensesScreen(
                        onOpenUrl = { url -> CustomTabLauncher.launch(this@SettingsPreviewActivity, url) },
                    )
                } else {
                    SettingsScreen(
                        orgUnitName = null,
                        onSwitchOrgUnit = {},
                        preferences = MemberPreferencesState(),
                        onPayout = {},
                        onRetryPreferences = {},
                        onSharing = {},
                        accountName = "GrafRotz",
                        language = language,
                        onLanguageChange = { chosen ->
                            language = chosen
                            LanguageSetting.apply(chosen)
                        },
                        appLockEnabled = locked,
                        appLockAvailable = true,
                        onAppLockChange = { locked = it },
                        screenCaptureAllowed = !captureBlocked,
                        onScreenCaptureChange = { allowed ->
                            lifecycleScope.launch { screenCapturePreference.set(blocked = !allowed) }
                        },
                        onOpenPrivacy = { openWebPage("/privacy") },
                        onOpenImprint = { openWebPage("/impressum") },
                        onOpenTerms = { openWebPage("/terms") },
                        onOpenLicenses = { licenses = true },
                        onLogout = { },
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                    )
                }
            }
        }
    }

    /** The real preference, so the switch on this screen has the effect it claims. */
    private val screenCapturePreference by lazy {
        (application as BasetoolApplication).screenCapture
    }

    /**
     * Mirrors `MainActivity`: sets `FLAG_SECURE` first and relaxes it only once the stored
     * screen-capture choice has been read.
     */
    private fun followScreenCapturePreference() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                screenCapturePreference.blocked.collect { blocked ->
                    if (blocked) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    /**
     * Opens one of the web app's public pages, exactly as the real screen does.
     *
     * @param path the page's path, including the leading slash
     */
    private fun openWebPage(path: String) {
        CustomTabLauncher.launch(this, BuildConfig.WEB_BASE_URL + path)
    }
}
