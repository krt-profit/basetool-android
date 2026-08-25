/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import android.content.ClipData.newPlainText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import de.greluc.krt.profit.basetool.android.BuildConfig
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSettingRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the licences list. */
const val LICENSES_LIST_TAG: String = "licenses-list"

/**
 * The bundled third-party software, grouped by licence (design ch. 15).
 *
 * The notice is **generated at build time** from the exact variant's dependency graph, so the list
 * is long, unfiltered and not searchable: it is a legal register rather than something to browse.
 * Every artifact carries its exact version, because the terms apply to the code that shipped, and
 * a licence's text opens in a browser rather than being bundled — the canonical addresses are
 * stable, and a bundled copy is a legal text that drifts out of date inside an APK.
 *
 * @param onOpenUrl opens a licence's canonical text; answers `false` when the device has no
 *   browser, which switches this screen to copying the address instead.
 * @param modifier layout modifier.
 */
@Composable
fun LicensesScreen(
    onOpenUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    // LocalResources, not LocalContext.current.resources: the latter is not configuration-aware,
    // and a language change recreates this screen's configuration.
    val resources = LocalResources.current
    var attempt by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<OssReport?>(null) }

    LaunchedEffect(resources, attempt) {
        report = null
        // Off the main thread: the report is a hundred-odd JSON objects read out of a raw
        // resource, and this screen is reached from a settings row that should not jank.
        report = withContext(coroutineContext) { OssLicenses.read(resources) }
    }

    when (val current = report) {
        null -> {
            LicensesLoading(modifier = modifier)
        }

        OssReport.Unreadable -> {
            LicensesFailed(modifier = modifier, onRetry = { attempt++ })
        }

        is OssReport.Loaded -> {
            LicensesList(
                artifacts = current.artifacts,
                onOpenUrl = onOpenUrl,
                modifier = modifier,
            )
        }
    }
}

/**
 * The wait, with the spinner held back.
 *
 * Design ch. 15 artboard 4: nothing for the first 300 ms. Reading the report is usually faster than
 * that, and a spinner that flashes for two frames reads as a glitch rather than as progress.
 *
 * @param modifier layout modifier.
 */
@Composable
private fun LicensesLoading(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MS)
        visible = true
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) {
            KrtLoadingIndicator(text = stringResource(R.string.licenses_loading))
        }
    }
}

/**
 * The report could not be read.
 *
 * Distinct from an empty list on purpose (design ch. 15 artboard 5): an unreadable resource is a
 * build problem a member can retry out of, while a genuinely empty register would be a defect the
 * build gate catches first (`OssLicensesTest`). Saying "no licences" for the first would be wrong
 * in a way nobody could act on.
 *
 * @param onRetry reads the resource again.
 * @param modifier layout modifier.
 */
@Composable
private fun LicensesFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_warning,
        title = stringResource(R.string.licenses_failed_title),
        message = stringResource(R.string.licenses_unavailable),
        actionText = stringResource(R.string.licenses_retry),
        onAction = onRetry,
        modifier = modifier.fillMaxWidth().padding(KrtSpacing.lg),
    )
}

/**
 * The register itself.
 *
 * @param artifacts every bundled artifact.
 * @param onOpenUrl opens a licence's text, or reports that no browser took it.
 * @param modifier layout modifier.
 */
@Composable
private fun LicensesList(
    artifacts: List<OssArtifact>,
    onOpenUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val groups = remember(artifacts) { OssLicenses.byLicense(artifacts) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var browserless by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(LICENSES_LIST_TAG),
            contentPadding = PaddingValues(vertical = KrtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        ) {
            item(key = "summary") {
                LicensesSummary(artifacts = artifacts.size, licenses = groups.size)
            }
            if (browserless) {
                item(key = "browserless") { BrowserlessNotice() }
            }
            groups.forEach { (license, group) ->
                stickyHeader(key = license.spdxId) {
                    // Sticky, because the group a row belongs to is the one fact a row does not
                    // carry: scrolled into the middle of a hundred Apache artifacts, the licence
                    // heading is off-screen and the row alone says nothing about the terms.
                    LicenseHeader(license = license, count = group.size)
                }
                item(key = "${license.spdxId}-text") {
                    KrtSettingRow(
                        title =
                            stringResource(
                                if (browserless) R.string.licenses_copy_url else R.string.licenses_open_text,
                            ),
                        subtitle = license.url,
                        leadingIcon = DesignR.drawable.ic_krt_external_link,
                        onClick = {
                            if (browserless || !onOpenUrl(license.url)) {
                                browserless = true
                                val url = license.url
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(newPlainText(url, url)))
                                }
                                copied = true
                            }
                        },
                    )
                }
                items(group, key = { "${license.spdxId}-${it.coordinates}" }) { artifact ->
                    // Gray3, not the SurfaceInput hairline the settings groups use: these rows sit
                    // on the page background rather than inside a card, and #1C1C1C on black is
                    // invisible.
                    KrtHairlineRule()
                    KrtSettingRow(
                        title = artifact.name,
                        subtitle = "${artifact.coordinates}:${artifact.version}",
                    )
                }
            }
            item(key = "end") { EndOfReport() }
        }
        if (copied) {
            KrtToast(
                title = stringResource(R.string.licenses_copy_url),
                message = stringResource(R.string.licenses_copied),
                modifier = Modifier.align(Alignment.BottomCenter).padding(KrtSpacing.lg),
            )
            LaunchedEffect(copied) {
                delay(TOAST_MS)
                copied = false
            }
        }
    }
}

/**
 * What this register covers, in one line.
 *
 * The version and flavour are part of it because the register describes **this build** — an
 * attribution question is always about a particular APK, and without them the page cannot answer
 * which one.
 *
 * @param artifacts how many artifacts the report lists.
 * @param licenses how many distinct licences they fall under.
 */
@Composable
private fun LicensesSummary(
    artifacts: Int,
    licenses: Int,
) {
    Text(
        text =
            stringResource(
                R.string.licenses_summary,
                pluralStringResource(R.plurals.licenses_summary_artifacts, artifacts, artifacts),
                pluralStringResource(R.plurals.licenses_summary_licenses, licenses, licenses),
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
    )
}

/**
 * One licence's heading, with how much of the register it accounts for.
 *
 * @param license the licence.
 * @param count how many artifacts are under it.
 */
@Composable
private fun LicenseHeader(
    license: OssLicense,
    count: Int,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
        KrtSectionTitle(
            text = license.displayName,
            modifier = Modifier.padding(start = KrtSpacing.lg, end = KrtSpacing.lg, top = KrtSpacing.sm),
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.licenses_group_summary,
                    count,
                    count,
                    license.spdxId,
                ),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(start = KrtSpacing.lg, end = KrtSpacing.lg, bottom = KrtSpacing.sm),
        )
    }
}

/**
 * The device has no browser, so addresses are copied instead.
 *
 * Shown only once a tap has actually failed rather than from a capability check at start-up: the
 * check would have to guess, and the tap knows.
 */
@Composable
private fun BrowserlessNotice() {
    Text(
        text = stringResource(R.string.licenses_no_browser),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.Warning,
        modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
    )
}

/**
 * The line that says the register ended, and who wrote it.
 *
 * A long list needs a bottom: without one, a member who scrolls to the end cannot tell a finished
 * register from one that stopped loading.
 */
@Composable
private fun EndOfReport() {
    Text(
        text = stringResource(R.string.licenses_end_of_report, BuildConfig.LICENSEE_VERSION),
        style = MaterialTheme.typography.labelSmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.md),
    )
}

/** How long the screen waits before admitting it is loading (design ch. 15 artboard 4). */
private const val SPINNER_DELAY_MS = 300L

/** How long the copied-to-clipboard toast stays up. */
private const val TOAST_MS = 2600L
