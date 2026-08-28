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
 * The open-source notice — every third-party artifact this build packages, grouped by licence.
 *
 * A page of its own rather than a section of the settings screen, because it is long by nature and
 * because it is a legal document: it has to be complete, and completeness here means the list is
 * generated from the dependency graph of the exact variant being built rather than curated
 * (see [OssLicenses]).
 *
 * Design chapter 15. Three of its properties are load-bearing and easy to lose:
 *
 * - **The framing text is required.** Without it the page is a wall of coordinates that never says
 *   what it is a list *of*, and the meta line is what ties those versions to one build.
 * - **The artifact rows are not interactive.** Only the licence has an address; a tappable
 *   coordinate would promise a destination that does not exist.
 * - **A device with no browser must still be able to read the licence.** The action copies the URL
 *   instead, decided when the screen is built rather than after a tap that goes nowhere.
 *
 * @param onOpenUrl opens a licence's canonical text; returns `false` when nothing handled it, which
 *   is the late half of the browser check — the early half is the package-manager probe.
 * @param modifier layout modifier.
 * @param parseDispatcher where the report is parsed. Injected rather than reached for so a test can
 *   parse on its own scheduler and observe the loading state deterministically; the default is the
 *   only value production uses.
 */
@Composable
fun LicensesScreen(
    onOpenUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    // LocalResources, not LocalContext.current.resources: the latter is not
    // configuration-aware, and a language change recreates this screen's configuration.
    val resources = LocalResources.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    // Parsed off the main thread — the chapter asks for it, and the report is a hundred-odd JSON
    // entries. `null` means "still reading", which is what the delayed spinner distinguishes from
    // "read and unusable".
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(KrtSpacing.lg),
                    )
                }
            }
        }
    }
}

/**
 * The reading state.
 *
 * The read is a local resource and normally finishes inside a frame or two, so the spinner waits
 * 300 ms before appearing (design ch. 15). A spinner that flashes for 80 ms is worse than none — it
 * reads as a stutter rather than as progress.
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
        modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (visible) {
            KrtSpinner()
            Spacer(Modifier.height(KrtSpacing.md))
            Text(
                text = stringResource(R.string.licenses_loading).krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The report could not be read.
 *
 * Deliberately **not** in the in-fiction error voice: that canon belongs to HTTP failures
 * (chapter 14), and borrowing it here would dress a missing local file as a server outage. The
 * retry re-reads the resource, which is the only thing that can change.
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
        modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Artboard 15.5 leads with the danger triangle and states the failure uppercase: the
        // report being unreadable is a fault in the installed app, not an empty list, and the two
        // must not look alike.
        KrtIcon(
            id = DesignR.drawable.ic_krt_warning,
            contentDescription = null,
            size = FAILURE_ICON,
            tint = KrtPalette.DangerText,
        )
        Spacer(Modifier.height(KrtSpacing.md))
        Text(
            text = stringResource(R.string.licenses_error_title).krtUppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = KrtPalette.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(KrtSpacing.sm))
        Text(
            text = stringResource(R.string.licenses_error_message),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(KrtSpacing.lg))
        KrtOutlineButton(
            text = stringResource(R.string.licenses_retry),
            onClick = onRetry,
            iconRes = DesignR.drawable.ic_krt_reset,
        )
    }
}

/**
 * The register itself.
 *
 * @param groups the licences in use with their artifacts, already ordered.
 * @param artifactTotal how many artifacts the report holds — **not** the sum of the group sizes: a
 *   dual-licensed artifact is listed under every licence it carries, so that sum over-counts it.
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
    // Which groups the member has folded away. Collapsed rather than expanded is the remembered
    // state, so the default stays what the chapter draws: everything visible.
    val collapsed = remember { mutableStateSetOf<String>() }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                // Design ch. 15, tablet: one 480 dp column, the rest of the canvas left black.
                // Two columns would break the sticky headers and the scan order, and a register
                // stretched to 1200 dp puts a 30-character coordinate alone on a very wide line.
                .then(if (isWideWindow()) Modifier.widthIn(max = TABLET_COLUMN) else Modifier),
        contentPadding = PaddingValues(bottom = KrtSpacing.xl),
    ) {
        item(key = "intro") {
            Column(modifier = Modifier.padding(KrtSpacing.lg)) {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
                Spacer(Modifier.height(KrtSpacing.sm))
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
                    // Muted and uppercase, as artboard 1 draws it — not orange. This line states
                    // what the register covers; the screen's orange belongs to the „LIZENZTEXT"
                    // links, which are the only things on it a member can act on.
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
                            .padding(KrtSpacing.lg),
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
                    // ONE string, version included: the chapter is explicit that the coordinate is
                    // not split across a title and a subtitle, because a reader checking a version
                    // against an advisory reads it as a single token.
                    text = "${artifact.coordinates}:${artifact.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Gray1,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ROW_MIN_HEIGHT)
                            .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
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
 * A licence's pinned heading: its name, how much of the report it covers, and its address.
 *
 * Opaque and without elevation, so a pinned header is pixel-identical to a resting one (design
 * ch. 15) — a header that changes appearance when it sticks reads as a different element scrolling
 * in. In the no-browser case the action changes its **label**, not its glyph: the chapter rules out
 * inventing a second icon for it.
 *
 * The heading also folds its own group away. That is a deviation from the chapter, which draws
 * the register fully expanded and the rows inert — asked for by the owner (2026-08-25), because a
 * hundred-odd coordinates under one licence make the *second* licence unreachable without a long
 * scroll. The fold is on the heading's body only; the licence action keeps its own target, so
 * reaching the licence text never costs an accidental collapse.
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
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtIcon(
                // The chevron is the affordance: without one, nothing says the heading can be
                // tapped, and a member discovers the fold by accident or not at all.
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
                        .padding(vertical = KrtSpacing.xs),
            ) {
                Text(
                    // Uppercase, as artboard 1 draws it and as the design system words every
                    // heading of this weight.
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
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .heightIn(min = KrtSpacing.touchTarget)
                        .clickable(onClick = onAction)
                        .padding(start = KrtSpacing.sm),
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
        // The 2 dp orange rule that closes every heading in this design system.
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
 * Asked once when the screen is built rather than after a failed tap, because the chapter's
 * fallback changes the **label** — a member should read "URL KOPIEREN" before they act, not
 * discover that "LIZENZTEXT" went nowhere. Requires the `<queries>` declaration in the manifest;
 * without it API 30+ answers "nothing" for every device.
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
