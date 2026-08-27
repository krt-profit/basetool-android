/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Edge length of the artwork. Fixed by the design spec — the logo is never scaled. */
private val LOGO_SIZE = 36.dp

/** Gap between artwork and notice. */
private val LOGO_GAP = 14.dp

/** Gap between the two notices, from the artboard's redline. */
private val NOTICE_GAP = 8.dp

/** Line height of the section-2b line, as drawn. */
private const val TRADEMARK_LINE_HEIGHT = 1.45f

/** Line height of the clause-2(g) paragraph, which runs longer and is set looser. */
private const val AGREEMENT_LINE_HEIGHT = 1.5f

/**
 * The Star Citizen Fan Kit compliance band — a legally coupled unit of **three** elements.
 *
 * **Two CIG documents bind this band and they apply cumulatively.** The Fan Kit Guidelines
 * (sections 2, 2b and 3) require the unmodified "Made By The Community" artwork together with the
 * CIG trademark line. The Fankit **Agreement**, clause 2(g), separately requires a longer notice —
 * the non-affiliation sentence, the copyright line, "Squadron 42®" in the mark list and a closing
 * "All rights reserved." Neither notice substitutes for the other.
 *
 * None of the three may be rendered, moved or removed on its own, which is why they live in this
 * single composable and why neither notice is a parameter. Consequences that are easy to break by
 * accident and must not be:
 *
 * - Both notices are prescribed legal wording. They stay verbatim English in every locale — never
 *   translated, rephrased or typographically "corrected".
 * - **The two differ in details that look like mistakes, and both are right.** The §2b line carries
 *   a space before its third ®, because CIG's §2b prose writes it that way; clause 2(g) carries
 *   none before any of its four, writes `Ltd..` with two full stops and takes an Oxford comma
 *   before "and Cloud Imperium®". Harmonising them yields a tidier band that satisfies neither
 *   document, so `KrtFanKitBandTest` asserts the difference itself.
 * - **The 2(g) paragraph is never folded behind a disclosure.** A notice behind a tap is not
 *   "reasonably prominent" in the sense the clause asks for (design decision, 27.08.2026); the
 *   login page scrolls instead.
 * - **One type size across the whole band.** The Guidelines' ~10 pt floor is ≈ 13.3 sp, which
 *   leaves no honest step down from the 14 sp the line above already uses.
 * - The artwork ships unmodified: no recolour, tint, flip, distortion, outline, shadow or effect.
 * - The band is static and non-interactive. KRT brackets, glows and orange are deliberately absent:
 *   this is third-party attribution and must not read as a button or as a KRT badge, nor compete
 *   with the screen's single orange call to action.
 * - Placement is fixed to the login screen (above the version footer) and the settings screen.
 *   Nowhere else.
 *
 * TalkBack reads artwork and notice as one node, matching the legal unit.
 *
 * @param modifier layout modifier. Leave the colours alone; only layout may be adjusted.
 */
@Composable
fun KrtFanKitBand(modifier: Modifier = Modifier) {
    val notice = stringResource(R.string.krt_fankit_trademark_notice)
    val agreementNotice = stringResource(R.string.krt_fankit_agreement_notice)
    val logoDescription = stringResource(R.string.krt_fankit_logo_description)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "$logoDescription. $notice $agreementNotice"
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KrtSpacing.hairline)
                    .background(KrtPalette.Gray3),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LOGO_GAP),
            // Top-aligned, not centred: with the 2(g) paragraph the text column runs several
            // lines, and a vertically centred logo would float in the middle of it.
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = painterResource(R.drawable.krt_made_by_the_community),
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE),
            )
            Column(verticalArrangement = Arrangement.spacedBy(NOTICE_GAP)) {
                Text(
                    text = notice,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            lineHeight =
                                MaterialTheme.typography.bodyMedium.fontSize *
                                    TRADEMARK_LINE_HEIGHT,
                        ),
                    color = KrtPalette.Gray1,
                )
                Text(
                    text = agreementNotice,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            lineHeight =
                                MaterialTheme.typography.bodyMedium.fontSize *
                                    AGREEMENT_LINE_HEIGHT,
                        ),
                    color = KrtPalette.Gray1,
                )
            }
        }
    }
}

@Preview(name = "Fan Kit band — phone", showBackground = true, backgroundColor = 0xFF000000, widthDp = 412)
@Composable
private fun FanKitBandPhonePreview() {
    de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme {
        Box(modifier = Modifier.background(KrtPalette.Black).padding(horizontal = KrtSpacing.lg)) {
            KrtFanKitBand()
        }
    }
}
