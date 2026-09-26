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
 * Declaration order is the settings control's order; German comes first as the default bundle.
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
         * Resolves the effective language the member is reading.
         *
         * The first supported pinned tag wins, else the first supported device tag, else [German].
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
         * Finds the first tag in [tags] the app has a bundle for, comparing the language subtag only.
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
