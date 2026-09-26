/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the language control, which highlights the language the member is reading rather than the one stored.
 */
class AppLanguageTest {
    @Test
    fun `a pinned language wins over the device`() {
        assertEquals(
            AppLanguage.English,
            AppLanguage.resolve(pinnedTags = listOf("en"), systemTags = listOf("de-DE")),
        )
    }

    @Test
    fun `with nothing pinned the device decides`() {
        assertEquals(
            AppLanguage.English,
            AppLanguage.resolve(pinnedTags = emptyList(), systemTags = listOf("en-GB")),
        )
    }

    @Test
    fun `regional variants resolve to the language's bundle`() {
        listOf("de", "de-DE", "de-AT", "de-CH").forEach { tag ->
            assertEquals(
                "$tag must resolve to German",
                AppLanguage.German,
                AppLanguage.resolve(pinnedTags = emptyList(), systemTags = listOf(tag)),
            )
        }
    }

    @Test
    fun `an unsupported device language reads as German`() {
        assertEquals(
            AppLanguage.German,
            AppLanguage.resolve(pinnedTags = emptyList(), systemTags = listOf("fr-FR")),
        )
    }

    @Test
    fun `the first supported entry of a preference list wins`() {
        assertEquals(
            AppLanguage.English,
            AppLanguage.resolve(pinnedTags = emptyList(), systemTags = listOf("fr-FR", "en-US", "de")),
        )
    }

    @Test
    fun `no locale information at all still resolves`() {
        assertEquals(
            AppLanguage.German,
            AppLanguage.resolve(pinnedTags = emptyList(), systemTags = emptyList()),
        )
    }

    @Test
    fun `the segment order is German then English`() {
        assertEquals(listOf(AppLanguage.German, AppLanguage.English), AppLanguage.entries)
    }
}
