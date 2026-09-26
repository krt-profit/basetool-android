/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that the open-source notice is complete: the generated resource exists and is not empty, every licence
 * identifier has a name and address, and no artifact falls out of the grouping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OssLicensesTest {
    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `the notice is generated into the app and is not empty`() {
        val report = OssLicenses.read(resources)

        assertTrue("the generated open-source notice is empty", report is OssReport.Loaded)
    }

    @Test
    fun `every artifact carries coordinates and a version`() {
        OssLicenses.loaded(resources).forEach { artifact ->
            assertTrue("empty coordinates in $artifact", artifact.coordinates.contains(':'))
            assertTrue("empty version for ${artifact.coordinates}", artifact.version.isNotBlank())
            assertTrue("empty name for ${artifact.coordinates}", artifact.name.isNotBlank())
        }
    }

    @Test
    fun `every licence in the notice is one the app can name and address`() {
        val unknown =
            OssLicenses
                .loaded(resources)
                .flatMap { it.spdxIds }
                .distinct()
                .filter { OssLicense.of(it) == null }

        assertEquals(
            "SPDX identifiers with no entry in OssLicense would render as a bare string",
            emptyList<String>(),
            unknown,
        )
    }

    @Test
    fun `no artifact drops out of the grouping`() {
        val artifacts = OssLicenses.loaded(resources)
        val listed = OssLicenses.byLicense(artifacts).flatMap { (_, group) -> group }.toSet()

        assertEquals(
            "artifacts missing from every licence group would silently not be attributed",
            emptySet<OssArtifact>(),
            artifacts.toSet() - listed,
        )
    }

    @Test
    fun `an artifact offered under two licences is listed under both`() {
        val artifacts =
            OssLicenses.parse(
                """
                [
                  {
                    "groupId": "com.example", "artifactId": "dual", "version": "1.0",
                    "spdxLicenses": [
                      { "identifier": "Apache-2.0" },
                      { "identifier": "BSD-3-Clause" }
                    ]
                  }
                ]
                """.trimIndent(),
            )

        assertEquals(2, OssLicenses.byLicense(artifacts).size)
    }

    @Test
    fun `an artifact without a name falls back to its coordinates`() {
        val artifacts =
            OssLicenses.parse(
                """[{ "groupId": "com.example", "artifactId": "nameless", "version": "2.0" }]""",
            )

        assertEquals("com.example:nameless", artifacts.single().name)
    }
}
