/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import de.greluc.krt.profit.basetool.android.core.auth.ActiveOrgUnitStore
import de.greluc.krt.profit.basetool.android.core.auth.AuthDataStore
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the refresh-token DataStore file is excluded from both the `cloud-backup` and `device-transfer` sections
 * of `data_extraction_rules.xml` (REQ-APP-AUTH-004).
 *
 * Reads the XML as text, because the fact under test is a path string.
 */
class BackupExclusionTest {
    private val extractionRules = File("src/main/res/xml/data_extraction_rules.xml")

    @Test
    fun `cloud backup and device transfer both exclude the token store`() {
        val xml = read(extractionRules)
        val cloudBackup = section(xml, "cloud-backup")
        val deviceTransfer = section(xml, "device-transfer")

        assertTrue(
            "cloud-backup must exclude ${AuthDataStore.RELATIVE_PATH}",
            cloudBackup.contains(AuthDataStore.RELATIVE_PATH),
        )
        assertTrue(
            "device-transfer must exclude ${AuthDataStore.RELATIVE_PATH} — a cloud-backup rule " +
                "does not cover D2D transfer",
            deviceTransfer.contains(AuthDataStore.RELATIVE_PATH),
        )
    }

    @Test
    fun `both sections exclude the org-unit pin as well`() {
        val rules = read(extractionRules)
        val cloudBackup = section(rules, "cloud-backup")
        val deviceTransfer = section(rules, "device-transfer")
        assertTrue(
            "cloud-backup must exclude ${ActiveOrgUnitStore.BACKUP_PATH}",
            cloudBackup.contains(ActiveOrgUnitStore.BACKUP_PATH),
        )
        assertTrue(
            "device-transfer must exclude ${ActiveOrgUnitStore.BACKUP_PATH} — a cloud-backup rule " +
                "alone does not govern a phone handed to the next device",
            deviceTransfer.contains(ActiveOrgUnitStore.BACKUP_PATH),
        )
    }

    @Test
    fun `backup is off outright, not merely narrowed by exclusions`() {
        val manifest = read(File("src/main/AndroidManifest.xml"))
        assertTrue(
            "the manifest must set android:allowBackup=\"false\"",
            manifest.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun `the excluded path is the one DataStore actually writes`() {
        assertTrue(
            "the exclusion must name the datastore/ subdirectory",
            AuthDataStore.RELATIVE_PATH.startsWith("datastore/"),
        )
        assertTrue(
            "the exclusion must name the .preferences_pb file",
            AuthDataStore.RELATIVE_PATH.endsWith(".preferences_pb"),
        )
    }

    /**
     * Reads one of the rule files.
     *
     * @param file the rule file, relative to the module directory Gradle runs tests from
     * @return its content
     */
    private fun read(file: File): String {
        assertTrue("expected to find ${file.absolutePath}", file.exists())
        return file.readText()
    }

    /**
     * Extracts one XML section by name.
     *
     * @param xml the whole document
     * @param name the element name, e.g. `cloud-backup`
     * @return the text between the opening and closing tag
     */
    private fun section(
        xml: String,
        name: String,
    ): String {
        val start = xml.indexOf("<$name>")
        val end = xml.indexOf("</$name>")
        assertTrue("data_extraction_rules.xml must declare a <$name> section", start >= 0 && end > start)
        return xml.substring(start, end)
    }
}
