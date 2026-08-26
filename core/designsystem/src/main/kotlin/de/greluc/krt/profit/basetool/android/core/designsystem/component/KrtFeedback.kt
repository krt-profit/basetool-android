/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** One full turn of the loading ring. */
private const val SPINNER_PERIOD_MS = 900

/** Sweep of the visible arc, in degrees. */
private const val SPINNER_SWEEP = 90f

/** Diameter of the loading ring. */
private val SPINNER_SIZE = 20.dp

/** Stroke width of the loading ring. */
private val SPINNER_STROKE = 2.dp

/** Width of the coloured leading edge on a banner. */
private val BANNER_EDGE = 4.dp

/** Width of the orange leading bar on a total tile. */
private val TOTAL_BAR = 4.dp

/**
 * The orange loading ring.
 *
 * The app never shows the platform's circular progress indicator: its Material styling and easing
 * do not belong to this system. Pair it with [KrtLoadingIndicator] whenever the wait is longer than
 * an instant, so the user learns *what* is loading.
 *
 * @param modifier layout modifier.
 * @param color ring colour; orange by default.
 */
@Composable
fun KrtSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "krt-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(animation = tween(SPINNER_PERIOD_MS, easing = LinearEasing)),
        label = "krt-spinner-angle",
    )

    Canvas(modifier = modifier.size(SPINNER_SIZE)) {
        val stroke = SPINNER_STROKE.toPx()
        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = SPINNER_SWEEP,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
        )
    }
}

/**
 * The loading state: ring plus an uppercase label naming what is being fetched.
 *
 * @param text the label, e.g. "Lade Einsätze…"; uppercased for display.
 * @param modifier layout modifier.
 */
@Composable
fun KrtLoadingIndicator(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtSpinner()
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The offline banner, pinned under the top bar while the device has no connection.
 *
 * It states both facts the user needs: that the screen shows cached data, and how old that data is.
 * While it is visible, every action that needs the network renders disabled — the banner is the
 * reason, so the disabled controls never look broken. Mutations are never queued for later: the
 * ledgers behind them are append-only and a replayed write would corrupt them.
 *
 * @param title the banner headline; uppercased for display.
 * @param lastUpdated human-readable timestamp of the cached data.
 * @param onRetry invoked when the user asks to reconnect.
 * @param modifier layout modifier.
 * @param retryText label of the retry action.
 */
@Composable
fun KrtOfflineBanner(
    title: String,
    modifier: Modifier = Modifier,
    lastUpdated: String? = null,
    onRetry: (() -> Unit)? = null,
    retryText: String = "Erneut verbinden",
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(BANNER_EDGE)
                    .height(KrtSpacing.touchTarget)
                    .background(KrtTheme.colors.warning),
        )
        KrtIcon(
            id = R.drawable.ic_krt_wifi_off,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = KrtSpacing.md),
            size = 20.dp,
            tint = KrtTheme.colors.warning,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtTheme.colors.warning,
            )
            // Both optional, because a screen that shows live data it simply cannot refresh
            // has neither a stamp to quote nor a retry that would mean anything. Rendering an
            // empty second line under the title would read as a timestamp that failed to load.
            lastUpdated?.let { stamp ->
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        onRetry?.let { retry ->
            KrtGhostButton(
                text = retryText,
                onClick = retry,
                modifier = Modifier.padding(KrtSpacing.sm),
            )
        }
    }
}

/**
 * Fills the viewport with a short body that pull-to-refresh can still reach.
 *
 * `PullToRefreshBox` hears the gesture through nested scroll, so a child that does not scroll never
 * forwards one - and the state a member most wants to re-read, an empty list, is exactly the one
 * with nothing to scroll. The pull then does nothing at all, which reads as a frozen screen. A
 * scroll container whose content fits consumes no drag and passes the whole of it upwards, which is
 * what the refresh box is waiting for.
 *
 * @param modifier layout modifier.
 * @param content the body. It must not fill the height: a scrolling column is measured without an
 *   upper bound, and `fillMaxSize` inside one is an error.
 */
@Composable
fun KrtRefreshableFill(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        content = content,
    )
}

/**
 * The empty state: a dashed frame, a short reason and exactly one next action.
 *
 * "Exactly one" is the rule that makes empty states useful — a screen with nothing on it should
 * point at the single thing that fills it, not offer a menu.
 *
 * @param iconRes glyph illustrating the empty collection.
 * @param title short headline.
 * @param message one sentence explaining why the list is empty and what fills it.
 * @param modifier layout modifier.
 * @param actionText label of the single action, or `null` when the user cannot act here.
 * @param onAction invoked when the action is taken.
 */
@Composable
fun KrtEmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .padding(vertical = KrtSpacing.xl, horizontal = KrtSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtIcon(id = iconRes, contentDescription = null, size = 28.dp, tint = KrtPalette.Gray2)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.Gray1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            KrtCtaButton(text = actionText, onClick = onAction)
        }
    }
}

/**
 * The totals tile: an orange leading bar, a muted label and a bright value.
 *
 * The orange bar marks this as the sum of the screen; ordinary figures use [KrtKpiCard].
 *
 * @param label what is being totalled; uppercased for display.
 * @param value the formatted total.
 * @param modifier layout modifier.
 * @param unit optional unit rendered after the value in a quieter tone.
 */
@Composable
fun KrtTotalTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface)
                .border(KrtSpacing.hairline, KrtPalette.Gray3),
    ) {
        Box(
            modifier =
                Modifier
                    .width(TOTAL_BAR)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.padding(KrtSpacing.md)) {
            Text(
                text = label.krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                KrtDataValue(text = value, style = MaterialTheme.typography.displaySmall)
                if (unit != null) {
                    Text(
                        text = unit,
                        modifier = Modifier.padding(start = KrtSpacing.xs, bottom = KrtSpacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.Gray2,
                    )
                }
            }
        }
    }
}

/**
 * A KPI tile with an optional delta and sparkline.
 *
 * Deltas take the semantic text tints — positive green, negative red — matching the price rule of
 * the system (buy prices red with a minus, sell prices green with a plus).
 *
 * @param title what the figure measures.
 * @param value the formatted figure.
 * @param modifier layout modifier.
 * @param delta optional change indicator, already formatted with its sign.
 * @param deltaPositive whether [delta] is an improvement; drives its colour.
 * @param sparkline optional series of values, oldest first, drawn as a plain line.
 * @param sparklineDescription what a screen reader is told the sparkline shows.
 * @param onClick optional tap handler making the whole tile the target.
 */
@Composable
fun KrtKpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean = true,
    sparkline: List<Float>? = null,
    onClick: (() -> Unit)? = null,
    sparklineDescription: String? = null,
) {
    KrtCard(modifier = modifier, onClick = onClick) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.TextMuted,
        )
        KrtDataValue(
            text = value,
            modifier = Modifier.padding(top = KrtSpacing.xs),
            style = MaterialTheme.typography.displaySmall,
        )
        if (delta != null || sparkline != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (delta != null) {
                    Text(
                        text = delta,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (deltaPositive) KrtTheme.colors.successText else KrtTheme.colors.dangerText,
                    )
                }
                if (sparkline != null) {
                    KrtSparkline(values = sparkline, contentDescription = sparklineDescription)
                }
            }
        }
    }
}

/**
 * A sparkline drawn by hand — the app ships no charting library.
 *
 * Values are normalised across their own min/max, so the line shows shape rather than absolute
 * level; the figure above it carries the number.
 *
 * @param values the series, oldest first. Fewer than two points render nothing.
 * @param modifier layout modifier.
 * @param color line colour; orange by default.
 * @param contentDescription what a screen reader is told the line shows; a chart with none
 *   is a blank to anyone not looking at it.
 */
@Composable
fun KrtSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
) {
    if (values.size < 2) return
    val described =
        contentDescription?.let { text -> modifier.semantics { this.contentDescription = text } }
            ?: modifier
    Canvas(
        modifier = described.defaultMinSize(minWidth = SPARKLINE_WIDTH, minHeight = SPARKLINE_HEIGHT),
    ) {
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f }
        val stepX = size.width / (values.size - 1)
        // A flat series has no span to scale by. Halfway up is the honest picture of "it did not
        // move"; dividing by a substituted 1f pins every point to the bottom edge and draws a fall
        // that never happened.
        val yFor = { value: Float ->
            span?.let { size.height - (value - min) / it * size.height } ?: (size.height / 2f)
        }
        var previous = Offset(0f, yFor(values[0]))
        for (index in 1 until values.size) {
            val point = Offset(x = stepX * index, y = yFor(values[index]))
            drawLine(
                color = color,
                start = previous,
                end = point,
                strokeWidth = SPARKLINE_STROKE.toPx(),
                cap = StrokeCap.Round,
            )
            previous = point
        }
    }
}

/** Width of a sparkline. */
private val SPARKLINE_WIDTH = 72.dp

/** Height of a sparkline. */
private val SPARKLINE_HEIGHT = 20.dp

/** Stroke width of a sparkline. */
private val SPARKLINE_STROKE = 1.5.dp

@Preview(name = "Feedback", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedbackPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.md)) {
            KrtLoadingIndicator("Lade Einsätze…")
            KrtOfflineBanner(
                title = "Offline — zeigt gespeicherten Stand",
                lastUpdated = "Zuletzt aktualisiert 17.08. 14:32",
                onRetry = {},
            )
            KrtEmptyState(
                iconRes = R.drawable.ic_krt_crate,
                title = "Keine Einträge",
                message = "Dein Lager ist leer. Buche Material ein, um es hier zu verwalten.",
                actionText = "Einbuchen",
                onAction = {},
            )
            KrtTotalTile(label = "Gesamt IRI", value = "1.245.300", unit = "aUEC")
            KrtKpiCard(
                title = "Einsatzkasse",
                value = "84.200",
                delta = "+12.400",
                sparkline = listOf(16f, 14f, 15f, 9f, 11f, 5f, 7f),
            )
        }
    }
}
