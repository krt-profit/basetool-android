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

/**
 * The Star Citizen Fan Kit compliance band — a legally coupled unit.
 *
 * The Fan Kit Guidelines (sections 2, 2b and 3) require the unmodified "Made By The Community"
 * artwork and the CIG trademark notice to appear **together**; neither element may be rendered,
 * moved or removed on its own, which is why they live in this single composable and why the notice
 * is not a parameter. Consequences that are easy to break by accident and must not be:
 *
 * - The notice is prescribed legal wording. It stays verbatim English in every locale — never
 *   translated, rephrased or typographically "corrected" (the space before the third registered
 *   sign is part of it).
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
    val logoDescription = stringResource(R.string.krt_fankit_logo_description)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "$logoDescription. $notice" },
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.krt_made_by_the_community),
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE),
            )
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
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
