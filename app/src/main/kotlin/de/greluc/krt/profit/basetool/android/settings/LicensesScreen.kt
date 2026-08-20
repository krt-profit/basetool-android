/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSettingRow
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The open-source notice — every third-party artifact this build packages, grouped by licence.
 *
 * A page of its own rather than a section of the settings screen, because it is long by nature and
 * because it is a legal document: it has to be complete, and completeness here means the list is
 * generated from the dependency graph of the exact variant being built rather than curated
 * (see [OssLicenses]).
 *
 * Every artifact is listed with its exact version, since the terms a member accepted apply to the
 * code that actually shipped, and the licence text opens in a browser rather than being bundled —
 * the canonical addresses are stable and this keeps a legal text from drifting out of date inside
 * an APK.
 *
 * @param onOpenUrl opens a licence's canonical text.
 * @param modifier layout modifier.
 */
@Composable
fun LicensesScreen(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // LocalResources, not LocalContext.current.resources: the latter is not
    // configuration-aware, and a language change recreates this screen's configuration.
    val resources = LocalResources.current
    val groups = remember(resources) { OssLicenses.byLicense(OssLicenses.read(resources)) }

    if (groups.isEmpty()) {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_list,
            title = stringResource(R.string.licenses_title),
            message = stringResource(R.string.licenses_unavailable),
            modifier = modifier.fillMaxWidth().padding(KrtSpacing.lg),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        item {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
            )
        }
        groups.forEach { (license, artifacts) ->
            item(key = license.spdxId) {
                KrtSectionTitle(
                    text = license.displayName,
                    modifier =
                        Modifier.padding(
                            horizontal = KrtSpacing.lg,
                            vertical = KrtSpacing.sm,
                        ),
                )
            }
            item(key = "${license.spdxId}-text") {
                KrtSettingRow(
                    title = stringResource(R.string.licenses_open_text),
                    subtitle = license.url,
                    leadingIcon = DesignR.drawable.ic_krt_external_link,
                    onClick = { onOpenUrl(license.url) },
                )
            }
            items(artifacts, key = { "${license.spdxId}-${it.coordinates}" }) { artifact ->
                KrtHairlineRule(color = KrtPalette.SurfaceInput)
                KrtSettingRow(
                    title = artifact.name,
                    subtitle = "${artifact.coordinates}:${artifact.version}",
                )
            }
        }
    }
}
