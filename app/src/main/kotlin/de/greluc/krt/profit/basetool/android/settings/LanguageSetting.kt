/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The in-app language, read and written through the platform's per-app language store.
 *
 * There is deliberately **no preference of our own** behind this. `AppCompatDelegate` forwards to
 * the platform `LocaleManager` on API 33+, where the choice also appears in Android's own
 * "App languages" screen and survives an app update; on API 30–32 it is AppCompat's backport,
 * persisted by the `autoStoreLocales` service declared in the manifest (ADR-0007). Storing the tag
 * a second time in a DataStore would create two sources of truth that disagree the moment a member
 * changes the language from the system settings screen rather than from this app.
 *
 * Applying a language recreates the activity — the platform does it on API 33+, AppCompat does it
 * below — which is what makes every already-composed string re-read from the new bundle without the
 * app restarting itself.
 */
object LanguageSetting {
    /**
     * The language currently on screen.
     *
     * @return the pinned language, or the one the device's own locales resolve to while nothing is
     *   pinned.
     */
    fun current(): AppLanguage =
        AppLanguage.resolve(
            pinnedTags = tagsOf(AppCompatDelegate.getApplicationLocales()),
            systemTags = tagsOf(systemLocales()),
        )

    /**
     * Pins [language] for this app.
     *
     * @param language the language to switch to.
     */
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    /**
     * The **device's** preferred locales, not the app's.
     *
     * Read from the system resources rather than from `Locale.getDefault()` or
     * `LocaleListCompat.getDefault()`: once a per-app language is set, both of those return that
     * override, so using either here would answer the wrong question — "what did the app pin",
     * which the caller already knows, instead of "what would the app fall back to".
     *
     * @return the device's locale list.
     */
    private fun systemLocales(): LocaleListCompat =
        LocaleListCompat.wrap(Resources.getSystem().configuration.locales)

    /**
     * Flattens a locale list into BCP 47 tags.
     *
     * @param locales the list to read.
     * @return the tags, most-preferred first; empty when the list is.
     */
    private fun tagsOf(locales: LocaleListCompat): List<String> =
        (0 until locales.size()).mapNotNull { index -> locales[index]?.toLanguageTag() }
}
