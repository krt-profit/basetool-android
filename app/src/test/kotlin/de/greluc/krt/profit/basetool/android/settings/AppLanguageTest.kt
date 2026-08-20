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
 * The rule behind the two-segment language control.
 *
 * The control shows the language the member is **reading**, not the one they stored, and those are
 * different facts until the first tap. Every case below is one the settings screen has to get right
 * for the highlighted segment to agree with the strings on the same screen.
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
        // Android loads values/ for de-AT and de-CH alike, so a control that showed neither
        // segment highlighted would disagree with the screen it sits on.
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
        // The member is looking at values/strings.xml, because that is the default bundle Android
        // falls back to. Saying so is the only honest answer.
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
        // The settings screen maps the control's index straight onto this list, so a reordering
        // here would silently switch the meaning of both segments.
        assertEquals(listOf(AppLanguage.German, AppLanguage.English), AppLanguage.entries)
    }
}
