/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

/**
 * The languages the app ships strings for.
 *
 * The order is the order of the segmented control on the settings screen, and German is first
 * because it is the default bundle: the squadron speaks German, so an untranslated key falls back
 * to the language the members actually read.
 *
 * @property tag the BCP 47 language tag handed to the platform.
 */
enum class AppLanguage(
    val tag: String,
) {
    /** German — `values/strings.xml`, the default bundle. */
    German("de"),

    /** English — `values-en/strings.xml`. */
    English("en"),

    ;

    companion object {
        /**
         * Resolves the language the member is actually reading.
         *
         * Takes **two** inputs because there are two different questions behind one answer. The
         * first is what the member pinned — an explicit choice, which is empty until they make one.
         * The second is what the device asks for, which decides the bundle Android loads while
         * nothing is pinned.
         *
         * The result is therefore the *effective* language rather than the stored preference, and
         * that is deliberate: the settings screen highlights what the screen is written in. A
         * control that showed "nothing selected" while the member reads German would be accurate
         * about the store and useless about the app. Tapping a segment then turns the resolved
         * answer into a pinned one, which is exactly the transition the member expects.
         *
         * Anything unrecognised resolves to [German], matching Android's own fallback to the
         * default bundle — a member on a French device reads German, and the control says so.
         *
         * @param pinnedTags the explicitly chosen language tags, most-preferred first; empty when
         *   the member has never chosen.
         * @param systemTags the device's preferred language tags, most-preferred first.
         * @return the language whose strings are on screen.
         */
        fun resolve(
            pinnedTags: List<String>,
            systemTags: List<String>,
        ): AppLanguage =
            firstSupported(pinnedTags) ?: firstSupported(systemTags) ?: German

        /**
         * Finds the first tag in [tags] the app has a bundle for.
         *
         * Compares the language subtag only, so `de-AT`, `de-CH` and `de` all resolve to German —
         * Android resolves regional variants to the same bundle, and a control that disagreed with
         * the strings on screen would be worse than no control.
         *
         * @param tags language tags, most-preferred first.
         * @return the matching language, or `null` when none is supported.
         */
        private fun firstSupported(tags: List<String>): AppLanguage? =
            tags.firstNotNullOfOrNull { tag ->
                val language = tag.substringBefore('-').lowercase()
                entries.firstOrNull { it.tag == language }
            }
    }
}
