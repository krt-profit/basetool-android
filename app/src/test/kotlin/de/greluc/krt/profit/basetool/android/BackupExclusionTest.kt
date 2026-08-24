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
 * The refresh token must not leave the device through a backup — in **either** rule set.
 *
 * This is the failure that reports nothing when it happens. Renaming the DataStore file, or
 * excluding a path that turns out not to exist, breaks no build and shows no symptom; it simply
 * starts copying an encrypted refresh token into Google Drive or onto the next phone. minSdk 30
 * still spans both worlds, so the app needs `backup_rules.xml` (API ≤ 30) *and*
 * `data_extraction_rules.xml` (API 31+) — and the second needs the exclusion in both its
 * `cloud-backup` and `device-transfer` sections, because `allowBackup=false` alone does not
 * reliably stop a device-to-device transfer.
 *
 * Reading the XML as text is crude on purpose: the assertion should fail when the *file the app
 * writes* stops matching the *path the rules exclude*, which is a string-level fact.
 */
class BackupExclusionTest {
    private val legacyRules = File("src/main/res/xml/backup_rules.xml")
    private val extractionRules = File("src/main/res/xml/data_extraction_rules.xml")

    @Test
    fun `the legacy rule set excludes the token store`() {
        val xml = read(legacyRules)

        assertTrue(
            "backup_rules.xml must exclude ${AuthDataStore.RELATIVE_PATH}",
            xml.contains(AuthDataStore.RELATIVE_PATH),
        )
    }

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
    fun `every rule set excludes the org-unit pin as well`() {
        // Three source comments promise this test covers it — backup_rules.xml, the
        // data_extraction_rules and ActiveOrgUnitStore itself — and until now none of them was
        // true. The exclusions are correct today, so nothing is exposed; what was missing is the
        // guard that keeps them correct. A renamed FILE_NAME would otherwise start shipping one
        // member's org scope into cloud backup and device-to-device transfer, silently.
        val legacy = read(File("src/main/res/xml/backup_rules.xml"))
        assertTrue(
            "backup_rules.xml must exclude ${ActiveOrgUnitStore.BACKUP_PATH}",
            legacy.contains(ActiveOrgUnitStore.BACKUP_PATH),
        )

        val rules = read(File("src/main/res/xml/data_extraction_rules.xml"))
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
        // The exclusions above are the belt; this is the braces. With both persisted files
        // excluded a restore produces an empty app anyway, so leaving backup on bought a member
        // nothing and cost a standing invariant: every file added later has to be remembered in
        // three rule sets, and forgetting one is invisible.
        val manifest = read(File("src/main/AndroidManifest.xml"))
        assertTrue(
            "the manifest must set android:allowBackup=\"false\"",
            manifest.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun `the excluded path is the one DataStore actually writes`() {
        // preferencesDataStoreFile puts the store under files/datastore/, so excluding the bare
        // store name — the obvious-looking rule — would match nothing at all.
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
