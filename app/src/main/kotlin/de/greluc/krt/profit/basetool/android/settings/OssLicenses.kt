/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import android.content.res.Resources
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import org.json.JSONArray
import org.json.JSONException

/**
 * A licence the app may redistribute under, with the name and address to show for it.
 *
 * Must match the `licensee { allow(…) }` list in `app/build.gradle.kts`; `OssLicensesTest` fails on
 * an identifier missing here.
 *
 * @property spdxId the SPDX identifier as it appears in the dependency's POM.
 * @property displayName the licence's own name, shown as the group heading.
 * @property url the canonical text, opened in a browser.
 */
enum class OssLicense(
    val spdxId: String,
    val displayName: String,
    val url: String,
) {
    /** Apache License 2.0 — everything AndroidX, Kotlin and Compose ships under. */
    Apache2("Apache-2.0", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),

    /** BSD 3-Clause — the protobuf runtime DataStore embeds. */
    Bsd3Clause("BSD-3-Clause", "BSD 3-Clause License", "https://opensource.org/license/bsd-3-clause"),

    ;

    companion object {
        /**
         * Looks up a licence by its SPDX identifier.
         *
         * @param spdxId the identifier from the report.
         * @return the licence, or `null` when it is one this app does not know — which the build's
         *   allow-list is supposed to have prevented.
         */
        fun of(spdxId: String): OssLicense? = entries.firstOrNull { it.spdxId == spdxId }
    }
}

/**
 * One third-party artifact bundled into the app.
 *
 * @property coordinates Maven coordinates without the version, e.g. `androidx.compose.ui:ui`.
 * @property name the library's own name where its POM states one, else the coordinates.
 * @property version the exact version that ships.
 * @property spdxIds the SPDX identifiers the POM declares; more than one means the recipient may
 *   choose, and the app lists the artifact under each.
 */
data class OssArtifact(
    val coordinates: String,
    val name: String,
    val version: String,
    val spdxIds: List<String>,
)

/**
 * Reads the open-source notice the build generated into `res/raw/oss_licenses.json` from the app's
 * own dependency graph.
 */
object OssLicenses {
    /** Log subsystem. */
    private const val LOG_TAG = "settings"

    /**
     * Parses the bundled report.
     *
     * A malformed or missing report yields [OssReport] `Unreadable` rather than an exception.
     *
     * @param resources the app's resources, holding `raw/oss_licenses.json`.
     * @return every bundled artifact, sorted by name, case-insensitively.
     */
    fun read(resources: Resources): OssReport =
        try {
            val json = resources.openRawResource(R.raw.oss_licenses).use { it.readBytes() }
            val artifacts = parse(String(json, Charsets.UTF_8))
            if (artifacts.isEmpty()) OssReport.Unreadable else OssReport.Loaded(artifacts)
        } catch (unreadable: Resources.NotFoundException) {
            KrtLog.e(LOG_TAG, unreadable) { "the generated open-source notice is missing" }
            OssReport.Unreadable
        } catch (malformed: JSONException) {
            KrtLog.e(LOG_TAG, malformed) { "the generated open-source notice is not readable" }
            OssReport.Unreadable
        }

    /**
     * The artifacts of a readable report, or an empty list.
     *
     * Intended for tests; the screen distinguishes the two outcomes through [read].
     *
     * @param resources the app's resources.
     * @return the artifacts, or empty when the report could not be read.
     */
    fun loaded(resources: Resources): List<OssArtifact> =
        (read(resources) as? OssReport.Loaded)?.artifacts.orEmpty()

    /**
     * Parses Licensee's `artifacts.json`.
     *
     * @param json the report's contents.
     * @return every artifact it lists, sorted by name.
     * @throws JSONException when the document is not the expected array of objects.
     */
    fun parse(json: String): List<OssArtifact> {
        val array = JSONArray(json)
        return (0 until array.length())
            .map { index ->
                val entry = array.getJSONObject(index)
                val group = entry.getString("groupId")
                val artifact = entry.getString("artifactId")
                val coordinates = "$group:$artifact"
                val licenses = entry.optJSONArray("spdxLicenses")
                OssArtifact(
                    coordinates = coordinates,
                    name = entry.optString("name").ifBlank { coordinates },
                    version = entry.getString("version"),
                    spdxIds =
                        (0 until (licenses?.length() ?: 0)).map { license ->
                            licenses!!.getJSONObject(license).getString("identifier")
                        },
                )
            }.sortedBy { it.name.lowercase() }
    }

    /**
     * Groups artifacts under the licences they are offered under.
     *
     * An artifact with two licences appears under both; a licence without artifacts is dropped. The
     * order is alphabetical by licence name, then by coordinate, so it is stable across builds.
     *
     * @param artifacts the parsed report.
     * @return one entry per licence in use, alphabetically, each with its artifacts alphabetically.
     */
    fun byLicense(artifacts: List<OssArtifact>): List<Pair<OssLicense, List<OssArtifact>>> =
        OssLicense.entries
            .mapNotNull { license ->
                artifacts
                    .filter { license.spdxId in it.spdxIds }
                    .sortedBy { it.coordinates.lowercase() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { license to it }
            }.sortedBy { (license, _) -> license.displayName.lowercase() }
}

/**
 * The outcome of reading the bundled report: loaded, or unreadable.
 */
sealed interface OssReport {
    /**
     * The report was read.
     *
     * @property artifacts every bundled artifact; never empty, since an empty report is
     *   [Unreadable].
     */
    data class Loaded(
        val artifacts: List<OssArtifact>,
    ) : OssReport

    /** The resource is missing, malformed, or lists nothing. */
    data object Unreadable : OssReport
}
