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
import de.greluc.krt.profit.basetool.android.settings.ScreenCapturePreference
import de.greluc.krt.profit.basetool.android.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Puts Einstellungen on a device without a session in front of it.
 *
 * It exists for one reason, and it is not convenience. In the running app this screen sits behind
 * the login, and the login **cannot be completed on an emulator at the minSdk floor**: Keycloak
 * marks its auth-session cookies `Secure; SameSite=None`, and a browser only sends those over
 * `http://127.0.0.1` from Chrome 89, which the API 30 image predates (DEV_CI § 5). Without this
 * entry the language switch — the one part of this screen whose behaviour differs between API 30
 * and API 33+ — could only ever be looked at on the platform where it is least likely to break.
 *
 * An `AppCompatActivity` for the same reason the real one is: below API 33, AppCompat's per-app
 * language backport recreates AppCompat delegates and nothing else, so a `ComponentActivity` here
 * would show a language switch that appears to do nothing and would be the wrong lesson entirely
 * (ADR-0007).
 *
 * **What is real and what is not.** The language control, the legal links, the open-source notice
 * and the screenshot switch are wired exactly as in the app — the last one including its effect on
 * this window, because "can a tester actually take the screenshot" is only answerable on a device
 * and this screen is the only way to reach the switch here. The account name is fixed text, and the
 * app-lock toggle flips a local boolean — the real one is backed by an auth-bound Keystore key and
 * belongs to `AppLockKeystoreContractTest`, which exercises it against a real Keystore.
 *
 * **Dev flavour only.** A release build ships no development surfaces.
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
        ScreenCapturePreference(ScreenCapturePreference.createStore(this))
    }

    /**
     * Mirrors `MainActivity`: block first, relax only once the stored choice has been read.
     *
     * The order is the point being verified here as much as the switch itself — a preview that read
     * the preference before setting the flag would look identical and would not exercise it.
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
