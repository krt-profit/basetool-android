/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the two string bundles carry the same keys and the same format placeholders; entries marked
 * `translatable="false"` are skipped.
 */
class StringsParityTest {
    private val german = File("src/main/res/values/strings.xml")
    private val english = File("src/main/res/values-en/strings.xml")

    @Test
    fun `both bundles declare the same keys`() {
        val germanKeys = keysOf(german)
        val englishKeys = keysOf(english)

        assertEquals(
            "keys missing from values-en/ fall back to German on an English device",
            emptySet<String>(),
            germanKeys - englishKeys,
        )
        assertEquals(
            "keys only in values-en/ are unreachable",
            emptySet<String>(),
            englishKeys - germanKeys,
        )
    }

    @Test
    fun `translations keep their format placeholders`() {
        val germanStrings = stringsOf(german)
        val englishStrings = stringsOf(english)

        englishStrings.forEach { (key, value) ->
            assertEquals(
                "placeholders of '$key' differ between the bundles — getString would crash",
                placeholdersOf(germanStrings.getValue(key)),
                placeholdersOf(value),
            )
        }
    }

    @Test
    fun `no bundle carries the Fan Kit notice`() {
        listOf(german, english).forEach { file ->
            assertTrue(
                "${file.name} must not contain the CIG trademark notice",
                !file.readText().contains("Cloud Imperium"),
            )
        }
    }

    /**
     * Extracts the declared string keys.
     *
     * @param file the bundle to read
     * @return every `name` attribute of a `<string>` element
     */
    private fun keysOf(file: File): Set<String> = stringsOf(file).keys

    /**
     * Extracts key/value pairs.
     *
     * @param file the bundle to read
     * @return the declared strings
     */
    private fun stringsOf(file: File): Map<String, String> {
        assertTrue("expected to find ${file.absolutePath}", file.exists())
        return STRING_ELEMENT
            .findAll(file.readText())
            .filterNot { it.groupValues[GROUP_ATTRIBUTES].contains("translatable=\"false\"") }
            .associate { it.groupValues[GROUP_NAME] to it.groupValues[GROUP_VALUE] }
    }

    /**
     * Collects the format placeholders in a value.
     *
     * @param value the string value
     * @return the placeholders it uses, e.g. `%1$s`
     */
    private fun placeholdersOf(value: String): Set<String> = PLACEHOLDER.findAll(value).map { it.value }.toSet()

    private companion object {
        val STRING_ELEMENT = Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val PLACEHOLDER = Regex("""%\d+\$[sd]""")

        /** Capture groups of [STRING_ELEMENT]: the key, the remaining attributes, the value. */
        const val GROUP_NAME = 1
        const val GROUP_ATTRIBUTES = 2
        const val GROUP_VALUE = 3
    }
}
