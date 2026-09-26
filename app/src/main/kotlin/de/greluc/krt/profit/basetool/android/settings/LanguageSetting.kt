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
 * The in-app language, read and written through the platform's per-app language store (ADR-0007).
 *
 * Holds no preference of its own: `AppCompatDelegate` forwards to `LocaleManager` on API 33+ and
 * uses AppCompat's `autoStoreLocales` backport below. Applying a language recreates the activity.
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
     * The device's preferred locales, not the app's per-app override.
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
