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
 * The open-source notice is a legal document, so the tests here ask whether it is **complete**
 * rather than whether the parser works.
 *
 * Three ways it could be quietly wrong, each pinned below. The generated resource could be missing
 * or empty, which the screen renders as a polite empty state and nobody would question. An artifact
 * could carry a licence identifier the app has no name or address for, which happens the first time
 * a transitive dependency arrives under something new. And an artifact could fall out of the
 * grouping entirely and simply not be listed — the failure mode that looks exactly like success.
 *
 * All three are invisible in a screenshot of a screen that shows 138 rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OssLicensesTest {
    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `the notice is generated into the app and is not empty`() {
        val report = OssLicenses.read(resources)

        // If the build wiring in app/build.gradle.kts stops running, the app still builds and the
        // screen still opens; only the attribution disappears. A report that reads as Unreadable
        // covers both the missing file and the empty one — the screen makes the same offer either
        // way, and neither is a notice.
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

        // The fix is to read the licence, decide whether the app may redistribute under it, then
        // add it to BOTH `licensee { allow(…) }` and OssLicense — never to one of them.
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

        // The recipient may rely on either, so listing it once under an arbitrary one would
        // misstate the terms.
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
