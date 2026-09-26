/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.greluc.krt.profit.basetool.android.BuildConfig
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The open-source notice: every third-party artifact this build packages, grouped by licence.
 *
 * The list is generated from the build's dependency graph ([OssLicenses]). The artifact rows are
 * not interactive; without a browser the licence action copies the URL instead of opening it.
 *
 * @param onOpenUrl opens a licence's canonical text; returns `false` when nothing handled it.
 * @param modifier layout modifier.
 * @param parseDispatcher where the report is parsed; injectable so a test can control it.
 */
@Composable
fun LicensesScreen(
    onOpenUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    val report by
        produceState<OssReport?>(initialValue = null, resources, reload) {
            value = withContext(parseDispatcher) { OssLicenses.read(resources) }
        }
    val browser = remember(context) { hasBrowser(context) }

    when (val current = report) {
        null -> {
            LicensesLoading(modifier = modifier)
        }

        is OssReport.Unreadable -> {
            LicensesFailed(onRetry = { reload++ }, modifier = modifier)
        }

        is OssReport.Loaded -> {
            Box(modifier = modifier.fillMaxSize()) {
                LicensesList(
                    groups = remember(current) { OssLicenses.byLicense(current.artifacts) },
                    artifactTotal = current.artifacts.size,
                    hasBrowser = browser,
                    onLicenceAction = { url ->
                        if (!browser || !onOpenUrl(url)) {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(url, url)))
                                copied = true
                            }
                        }
                    },
                )
                if (copied) {
                    LaunchedEffect(Unit) {
                        delay(TOAST_MS)
                        copied = false
                    }
                    KrtToast(
                        title = stringResource(R.string.licenses_url_copied_title),
                        message = stringResource(R.string.licenses_url_copied_message),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(KrtSpacing.s16),
                    )
                }
            }
        }
    }
}

/**
 * The reading state; the spinner appears only after 300 ms.
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
    Column(
        modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (visible) {
            KrtSpinner()
            Spacer(Modifier.height(KrtSpacing.s12))
            Text(
                text = stringResource(R.string.licenses_loading).krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The report could not be read; offers a retry that re-reads the local resource.
 *
 * Uses plain wording, not the in-fiction voice reserved for HTTP failures.
 *
 * @param onRetry reads the resource again.
 * @param modifier layout modifier.
 */
@Composable
private fun LicensesFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KrtIcon(
            id = DesignR.drawable.ic_krt_warning,
            contentDescription = null,
            size = FAILURE_ICON,
            tint = KrtPalette.DangerText,
        )
        Spacer(Modifier.height(KrtSpacing.s12))
        Text(
            text = stringResource(R.string.licenses_error_title).krtUppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = KrtPalette.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(KrtSpacing.s8))
        Text(
            text = stringResource(R.string.licenses_error_message),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(KrtSpacing.s16))
        KrtOutlineButton(
            text = stringResource(R.string.licenses_retry),
            onClick = onRetry,
            iconRes = DesignR.drawable.ic_krt_reset,
        )
    }
}

/**
 * The licence register.
 *
 * @param groups the licences in use with their artifacts, already ordered.
 * @param artifactTotal how many artifacts the report holds; not the sum of the group sizes, since a
 *   dual-licensed artifact appears under each licence.
 * @param hasBrowser whether a licence address can be opened at all.
 * @param onLicenceAction opens or copies one licence's address.
 */
@Composable
private fun LicensesList(
    groups: List<Pair<OssLicense, List<OssArtifact>>>,
    artifactTotal: Int,
    hasBrowser: Boolean,
    onLicenceAction: (String) -> Unit,
) {
    val collapsed = remember { mutableStateSetOf<String>() }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LicenceRegister(
            groups = groups,
            artifactTotal = artifactTotal,
            collapsed = collapsed,
            hasBrowser = hasBrowser,
            onLicenceAction = onLicenceAction,
        )
    }
}

/**
 * The licence register, laid out in its column.
 *
 * @param groups the licences in use with their artifacts, already ordered.
 * @param artifactTotal how many artifacts the report holds.
 * @param collapsed which groups the member has folded away.
 * @param hasBrowser whether a browser is installed, which decides if the licence text is offered.
 * @param onLicenceAction opens a licence's own text.
 */
@Composable
private fun LicenceRegister(
    groups: List<Pair<OssLicense, List<OssArtifact>>>,
    artifactTotal: Int,
    collapsed: MutableSet<String>,
    hasBrowser: Boolean,
    onLicenceAction: (String) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .then(if (isWideWindow()) Modifier.widthIn(max = TABLET_COLUMN) else Modifier)
                .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = KrtSpacing.s24),
    ) {
        item(key = "intro") {
            Column(modifier = Modifier.padding(KrtSpacing.s16)) {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
                Spacer(Modifier.height(KrtSpacing.s8))
                Text(
                    text =
                        stringResource(
                            R.string.licenses_meta,
                            pluralStringResource(
                                R.plurals.licenses_artifact_count,
                                artifactTotal,
                                artifactTotal,
                            ),
                            pluralStringResource(
                                R.plurals.licenses_license_count,
                                groups.size,
                                groups.size,
                            ),
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                            BuildConfig.FLAVOR.replaceFirstChar { it.uppercase() },
                        ).krtUppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        if (!hasBrowser) {
            item(key = "no-browser") {
                Text(
                    text = stringResource(R.string.licenses_no_browser_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Warning,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(KrtPalette.SurfaceInput)
                            .padding(KrtSpacing.s16),
                )
            }
        }
        groups.forEach { (license, artifacts) ->
            val folded = license.spdxId in collapsed
            stickyHeader(key = license.spdxId) {
                LicenseHeader(
                    license = license,
                    count = artifacts.size,
                    hasBrowser = hasBrowser,
                    collapsed = folded,
                    onToggle = {
                        if (folded) collapsed.remove(license.spdxId) else collapsed.add(license.spdxId)
                    },
                    onAction = { onLicenceAction(license.url) },
                )
            }
            items(
                if (folded) emptyList() else artifacts,
                key = { "${license.spdxId}-${it.coordinates}" },
            ) { artifact ->
                Text(
                    text = "${artifact.coordinates}:${artifact.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Gray1,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ROW_MIN_HEIGHT)
                            .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
                )
            }
        }
        item(key = "end") {
            KrtHairlineRule()
            KrtEndOfList(
                text = stringResource(R.string.licenses_end_of_report, BuildConfig.LICENSEE_VERSION),
            )
        }
    }
}

/**
 * A licence's pinned heading: its name, how many artifacts it covers, and its address.
 *
 * Looks the same pinned and resting. Tapping the heading folds its group; the licence action is a
 * separate target whose label changes when no browser is installed.
 *
 * @param license the licence.
 * @param count how many artifacts sit under it.
 * @param hasBrowser decides whether the action opens or copies.
 * @param collapsed whether this group's artifacts are folded away.
 * @param onToggle folds the group away or back.
 * @param onAction opens or copies the licence address.
 */
@Composable
private fun LicenseHeader(
    license: OssLicense,
    count: Int,
    hasBrowser: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(KrtPalette.SurfaceInput)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtIcon(
                id =
                    if (collapsed) {
                        DesignR.drawable.ic_krt_chevron_right
                    } else {
                        DesignR.drawable.ic_krt_chevron_down
                    },
                contentDescription = null,
                tint = KrtPalette.TextMuted,
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onToggle)
                        .padding(vertical = KrtSpacing.s4),
            ) {
                Text(
                    text = license.displayName.krtUppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = KrtPalette.White,
                )
                Text(
                    text =
                        stringResource(
                            R.string.licenses_group_subtitle,
                            pluralStringResource(R.plurals.licenses_artifact_count, count, count),
                            license.spdxId,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .heightIn(min = KrtSpacing.touchTarget)
                        .clickable(onClick = onAction)
                        .padding(start = KrtSpacing.s8),
            ) {
                Text(
                    text =
                        stringResource(
                            if (hasBrowser) {
                                R.string.licenses_open_text
                            } else {
                                R.string.licenses_copy_url
                            },
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (hasBrowser) {
                    KrtIcon(
                        id = DesignR.drawable.ic_krt_external_link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KrtSpacing.headingRule)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Whether anything on this device can open an `https` address.
 *
 * Requires the manifest's `<queries>` declaration; without it API 30+ always answers no.
 *
 * @param context used for its package manager.
 * @return `true` when at least one activity handles `VIEW https:`.
 */
private fun hasBrowser(context: Context): Boolean {
    val probe = Intent(Intent.ACTION_VIEW, "https://example.invalid".toUri())
    return context.packageManager
        .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        .isNotEmpty()
}

/** How long the copy confirmation stays up. */
private const val TOAST_MS = 2_600L

/** How long a local read may take before a spinner is worth showing (design ch. 15). */
private const val SPINNER_DELAY_MS = 300L

/** Minimum height of an artifact row, so a one-line coordinate still gets a comfortable band. */
private val ROW_MIN_HEIGHT = 40.dp

/** The register's column on a tablet — the width design ch. 15 keeps from the settings pane. */
private val TABLET_COLUMN = 480.dp

/** Size of the danger glyph on the unreadable-report state (artboard 15.5). */
private val FAILURE_ICON = 40.dp
