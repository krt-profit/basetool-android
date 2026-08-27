/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the Star Citizen Fan Kit compliance wording.
 *
 * **Two CIG documents bind the band and they apply cumulatively**: the Fan Kit Guidelines
 * (section 2b) prescribe the short trademark line, and the Fankit Agreement (clause 2(g))
 * prescribes a second, longer notice. Both are legal wording, not UI copy. A well-meaning German
 * translation, a "corrected" spacing before a registered sign or a typographic clean-up would each
 * break compliance while passing every other check in the build — this test is the tripwire that
 * turns such an edit into a red build.
 *
 * The mirror of this test in the web app is `FanKitComplianceMvcTest`; both must stay in sync.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class KrtFanKitBandTest {
    /**
     * The notice exactly as the Fan Kit Guidelines prescribe it — including the space before the
     * third registered-trademark sign, which is part of the quoted wording.
     */
    private val requiredNotice =
        "Star Citizen®, Roberts Space Industries® and Cloud Imperium ® " +
            "are registered trademarks of Cloud Imperium Rights LLC"

    /**
     * The Fankit Agreement clause 2(g) notice, verbatim from
     * `06_Fankit_Agreement_2025_11_19.pdf` and byte-identical across the three archived kit
     * versions (2024-04-25, 2025-06-03, 2025-11-19).
     *
     * Three details read as typing mistakes and are none of them: `Ltd..` has two full stops, there
     * is **no** space before any of its four registered signs, and there is an Oxford comma before
     * "and Cloud Imperium®".
     */
    private val requiredAgreementNotice =
        "This site is not endorsed by or affiliated with the Cloud Imperium or Roberts Space " +
            "Industries group of companies. All game content and materials are copyright Cloud " +
            "Imperium Rights LLC and Cloud Imperium Rights Ltd.. Star Citizen®, Squadron 42®, " +
            "Roberts Space Industries®, and Cloud Imperium® are registered trademarks of Cloud " +
            "Imperium Rights LLC. All rights reserved."

    private val application: Application get() = RuntimeEnvironment.getApplication()

    /** The default (German-first) bundle must carry the notice byte for byte. */
    @Test
    fun trademarkNotice_isVerbatim_inDefaultLocale() {
        assertEquals(
            "the Fan Kit trademark notice must not be edited",
            requiredNotice,
            application.getString(R.string.krt_fankit_trademark_notice),
        )
    }

    /**
     * The notice must stay English even when the device runs German.
     *
     * It is marked `translatable="false"`, so a localized override would be a deliberate act — this
     * assertion makes that act fail.
     */
    @Test
    @Config(qualifiers = "de", sdk = [ROBOLECTRIC_SDK])
    fun trademarkNotice_staysEnglish_inGermanLocale() {
        assertEquals(
            "the Fan Kit trademark notice must never be translated",
            requiredNotice,
            application.getString(R.string.krt_fankit_trademark_notice),
        )
    }

    /**
     * The notice must stay English in the English locale too — a trivially true-looking assertion
     * that guards against someone "fixing" the wording while adding an English bundle.
     */
    @Test
    @Config(qualifiers = "en", sdk = [ROBOLECTRIC_SDK])
    fun trademarkNotice_staysEnglish_inEnglishLocale() {
        assertEquals(
            requiredNotice,
            application.getString(R.string.krt_fankit_trademark_notice),
        )
    }

    /** The default (German-first) bundle must carry the clause 2(g) notice byte for byte. */
    @Test
    fun agreementNotice_isVerbatim_inDefaultLocale() {
        assertEquals(
            "the Fankit Agreement clause 2(g) notice must not be edited",
            requiredAgreementNotice,
            application.getString(R.string.krt_fankit_agreement_notice),
        )
    }

    /**
     * The clause 2(g) notice must stay English on a German device.
     *
     * It is the longer of the two and reads like prose, which makes it the likelier of the pair to
     * be "helpfully" translated.
     */
    @Test
    @Config(qualifiers = "de", sdk = [ROBOLECTRIC_SDK])
    fun agreementNotice_staysEnglish_inGermanLocale() {
        assertEquals(
            "the Fankit Agreement clause 2(g) notice must never be translated",
            requiredAgreementNotice,
            application.getString(R.string.krt_fankit_agreement_notice),
        )
    }

    /** And in the English locale, guarding an "improvement" made while adding an English bundle. */
    @Test
    @Config(qualifiers = "en", sdk = [ROBOLECTRIC_SDK])
    fun agreementNotice_staysEnglish_inEnglishLocale() {
        assertEquals(
            requiredAgreementNotice,
            application.getString(R.string.krt_fankit_agreement_notice),
        )
    }

    /**
     * The two notices are not interchangeable and must not drift into each other.
     *
     * The tempting clean-up is to give both the same spacing before ®, or to fold one into the
     * other. Either leaves a plausible-looking band that satisfies neither document, so the
     * difference itself is asserted rather than left to a reviewer's eye.
     */
    @Test
    fun theTwoNotices_keepTheirDifferingRegisteredSignSpacing() {
        assertEquals(
            "section 2b prose carries a space before its third registered sign",
            true,
            requiredNotice.contains("Cloud Imperium ®"),
        )
        assertEquals(
            "clause 2(g) carries no space before any registered sign",
            false,
            requiredAgreementNotice.contains(" ®"),
        )
        assertEquals(
            "clause 2(g) writes Ltd with two full stops",
            true,
            requiredAgreementNotice.contains("Cloud Imperium Rights Ltd.. Star Citizen"),
        )
    }

    /** The artwork the notices are coupled to must exist; a missing drawable would split the unit. */
    @Test
    fun madeByTheCommunityArtwork_isBundled() {
        val drawable = application.resources.getDrawable(R.drawable.krt_made_by_the_community, null)
        assertEquals(
            "the Made By The Community artwork must ship unmodified alongside the notice",
            true,
            drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0,
        )
    }
}

/**
 * SDK level Robolectric renders against.
 *
 * Pinned rather than inherited from `targetSdk`: the app targets API 37, for which Robolectric has
 * no runtime yet. Nothing under test here is API-level dependent — these are resource lookups.
 */
private const val ROBOLECTRIC_SDK = 34
