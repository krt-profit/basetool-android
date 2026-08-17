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
 * Pins the Star Citizen Fan Kit compliance wording (Fan Kit Guidelines section 2b).
 *
 * The trademark notice is prescribed legal wording, not UI copy. A well-meaning German translation,
 * a "corrected" spacing before the third registered sign or a typographic clean-up would each break
 * compliance while passing every other check in the build — this test is the tripwire that turns
 * such an edit into a red build.
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

    /** The artwork the notice is coupled to must exist; a missing drawable would split the unit. */
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
