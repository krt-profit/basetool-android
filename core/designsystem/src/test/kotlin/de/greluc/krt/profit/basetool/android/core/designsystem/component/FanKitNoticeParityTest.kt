/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The two Fan Kit notices must exist in the default bundle and in **no** localized one.
 *
 * [KrtFanKitBandTest] asserts what the notices say once Android has resolved them. This one asserts
 * the shape of the resource files themselves, which is a different failure: an override added to
 * `values-en/` would resolve correctly for a German device and silently ship a translated legal
 * notice to an English one, and a per-locale test that happens to run under the wrong qualifier
 * would not see it.
 *
 * The a11y description of the artwork (`krt_fankit_logo_description`) is deliberately **not**
 * covered — that one is UI copy about a picture and is translated on purpose.
 */
class FanKitNoticeParityTest {
    private companion object {
        /** The keys that are quoted legal wording and therefore locale-invariant. */
        val PRESCRIBED_KEYS =
            listOf("krt_fankit_trademark_notice", "krt_fankit_agreement_notice")

        /** Bundles a translated override could hide in. */
        val LOCALIZED_BUNDLES = listOf("src/main/res/values-en/strings.xml")

        const val DEFAULT_BUNDLE = "src/main/res/values/strings.xml"
    }

    @Test
    fun `both prescribed notices are declared once, in the default bundle`() {
        val defaults = File(DEFAULT_BUNDLE).readText()
        PRESCRIBED_KEYS.forEach { key ->
            assertEquals(
                "'$key' must be declared exactly once in the default bundle",
                1,
                Regex("""<string name="$key"""").findAll(defaults).count(),
            )
        }
    }

    @Test
    fun `both prescribed notices are marked untranslatable`() {
        val defaults = File(DEFAULT_BUNDLE).readText()
        PRESCRIBED_KEYS.forEach { key ->
            val declaration =
                Regex("""<string name="$key"[^>]*>""").find(defaults)?.value.orEmpty()
            assertEquals(
                "'$key' must carry translatable=\"false\" — it is quoted legal wording, and the" +
                    " marker is also what keeps Android Lint's MissingTranslation quiet",
                true,
                declaration.contains("""translatable="false""""),
            )
        }
    }

    @Test
    fun `no localized bundle overrides a prescribed notice`() {
        LOCALIZED_BUNDLES.forEach { path ->
            val bundle = File(path)
            if (!bundle.exists()) {
                return@forEach
            }
            val text = bundle.readText()
            PRESCRIBED_KEYS.forEach { key ->
                assertEquals(
                    "'$key' must not appear in $path — a localized override would ship a" +
                        " translated legal notice while every other check stays green",
                    false,
                    text.contains("""<string name="$key""""),
                )
            }
        }
    }
}
