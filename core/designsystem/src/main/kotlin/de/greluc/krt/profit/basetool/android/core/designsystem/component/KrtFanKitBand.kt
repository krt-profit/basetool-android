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
 * The Star Citizen Fan Kit compliance band: the "Made By The Community" artwork, the Fan Kit
 * Guidelines trademark line and the Fankit Agreement clause 2(g) notice, as one legal unit.
 *
 * - Both notices are verbatim English in every locale; their differing punctuation is intended and
 *   asserted by `KrtFanKitBandTest`.
 * - The 2(g) notice is never folded behind a disclosure, the whole band uses one type size, and the
 *   artwork is never modified.
 * - The band is static, carries no KRT styling, and appears only on the login and settings
 *   screens; TalkBack reads it as one node.
 *
 * @param modifier layout modifier; only layout may be adjusted.
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
            modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.s12),
            horizontalArrangement = Arrangement.spacedBy(LOGO_GAP),
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
        Box(modifier = Modifier.background(KrtPalette.Black).padding(horizontal = KrtSpacing.s16)) {
            KrtFanKitBand()
        }
    }
}
