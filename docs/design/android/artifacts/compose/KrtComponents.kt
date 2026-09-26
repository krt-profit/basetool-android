/*
 * Basetool Android — DAS KARTELL / Bereich Profit design system.
 * GENERATED FROM THE DESIGN SPEC (docs/design/android, chapters 00–17).
 *
 * Every value here is decided. Do not tune, round or "improve" one. If something you need is
 * missing, it is a spec gap — raise it, do not invent it.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.*

@Composable
fun KrtCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = KrtDimens.ctaHeight).then(if (enabled) Modifier.krtEmphasisGlow() else Modifier),
        enabled = enabled && !loading,
        shape = RectangleShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KrtPalette.Primary,
            contentColor = KrtPalette.Black,
            disabledContainerColor = KrtPalette.Gray3,
            disabledContentColor = KrtPalette.Gray2,
        ),
    ) { KrtButtonContent(text, iconRes, loading) }
}

@Composable
fun KrtSuccessButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconRes: Int? = null, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = KrtDimens.ctaHeight),
        enabled = enabled && !loading,
        shape = RectangleShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KrtPalette.Success, contentColor = KrtPalette.White, disabledContainerColor = KrtPalette.Gray3, disabledContentColor = KrtPalette.Gray2),
    ) { KrtButtonContent(text, iconRes, loading) }
}

@Composable
fun KrtOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconRes: Int? = null, enabled: Boolean = true, loading: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KrtDimens.touchTarget),
        enabled = enabled && !loading,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(KrtDimens.hairline, if (enabled) KrtPalette.Primary else KrtPalette.Gray3),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = KrtPalette.Primary, disabledContentColor = KrtPalette.Gray2),
    ) { KrtButtonContent(text, iconRes, loading) }
}

@Composable
fun KrtGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconRes: Int? = null, enabled: Boolean = true, loading: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KrtDimens.touchTarget),
        enabled = enabled && !loading,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(KrtDimens.hairline, KrtPalette.Gray3),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = KrtPalette.Gray1, disabledContentColor = KrtPalette.Gray2),
    ) { KrtButtonContent(text, iconRes, loading) }
}

@Composable
fun KrtQuietDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconRes: Int? = null, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KrtDimens.touchTarget),
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = KrtPalette.DangerText, disabledContentColor = KrtPalette.Gray2),
    ) { KrtButtonContent(text, iconRes, loading = false) }
}

/** Filled red — reserved for the confirm inside a danger modal. Never on a screen. */
@Composable
fun KrtDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = KrtDimens.touchTarget),
        enabled = enabled && !loading,
        shape = RectangleShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KrtPalette.Danger, contentColor = KrtPalette.White),
    ) { KrtButtonContent(text, null, loading) }
}

@Composable
private fun KrtButtonContent(text: String, iconRes: Int?, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            iconRes != null -> Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(KrtDimens.iconSmall))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Icon-only action for REPEATED row actions whose meaning is universal (edit, trash, check,
 * login/logout, reset, up/down). Saves 50–60 % of an action column.
 *
 * @param label MANDATORY — becomes contentDescription AND the long-press tooltip. A nameless
 *   icon button is a bug, not a style choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrtIconButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = KrtDimens.iconButton,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = modifier
                .size(size)
                .border(KrtDimens.hairline, KrtPalette.Gray3)
                .then(if (enabled) Modifier else Modifier.alpha(0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
                Icon(painterResource(iconRes), contentDescription = label, modifier = Modifier.size(17.dp), tint = KrtPalette.Gray1)
            }
        }
    }
}

private fun Modifier.alpha(value: Float) = this.then(androidx.compose.ui.draw.alpha(value))

/** Plain square surface: hairline + #141414. The workhorse. */
@Composable
fun KrtCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Gray3).padding(KrtSpacing.md),
        content = content,
    )
}

/** Card that wraps a table or list: no padding, children own their insets. */
@Composable
fun KrtFlushCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Gray3), content = content)
}

/**
 * The signature container: hairline box with TWO diagonal orange corner brackets
 * (top-left, bottom-right), 10 dp arms, 2 dp stroke, translucent #141414 at 50 %. The MODAL
 * frame uses 13 dp arms with the same 2 dp stroke — two values, both from the stylesheet.
 * Use for emphasis blocks (hero numbers, attendance, KPI, state messages) — not for every card.
 */
@Composable
fun KrtHudBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .background(KrtPalette.Gray4.copy(alpha = 0.5f))
            .border(KrtDimens.hairline, KrtPalette.Gray3)
            .drawBehind {
                val arm = 10.dp.toPx()
                val w = 2.dp.toPx()
                drawLine(KrtPalette.Primary, Offset(0f, 0f), Offset(arm, 0f), w, StrokeCap.Square)
                drawLine(KrtPalette.Primary, Offset(0f, 0f), Offset(0f, arm), w, StrokeCap.Square)
                drawLine(KrtPalette.Primary, Offset(size.width, size.height), Offset(size.width - arm, size.height), w, StrokeCap.Square)
                drawLine(KrtPalette.Primary, Offset(size.width, size.height), Offset(size.width, size.height - arm), w, StrokeCap.Square)
            }
            .padding(horizontal = KrtSpacing.md, vertical = 14.dp),
        content = content,
    )
}

enum class KrtChipTone { NEUTRAL, PRIMARY, SUCCESS, DANGER, WARNING, INFO, MUTED, DATA }

@Composable
fun KrtChip(text: String, tone: KrtChipTone = KrtChipTone.NEUTRAL, modifier: Modifier = Modifier) {
    val (border, label) = when (tone) {
        KrtChipTone.NEUTRAL -> KrtPalette.Gray3 to KrtPalette.Gray1
        KrtChipTone.PRIMARY -> KrtPalette.Primary to KrtPalette.Primary
        KrtChipTone.SUCCESS -> KrtPalette.Success to KrtPalette.SuccessText
        KrtChipTone.DANGER -> KrtPalette.Danger to KrtPalette.DangerText
        KrtChipTone.WARNING -> KrtPalette.Warning to KrtPalette.WarningText
        KrtChipTone.INFO -> KrtPalette.Info to KrtPalette.InfoText
        KrtChipTone.MUTED -> KrtPalette.Gray3 to KrtPalette.TextMuted
        KrtChipTone.DATA -> KrtPalette.Gray3 to KrtPalette.White
    }
    val fill = when (tone) {
        KrtChipTone.NEUTRAL, KrtChipTone.MUTED -> Color.Transparent
        KrtChipTone.DATA -> KrtPalette.SurfaceInput
        else -> border.copy(alpha = 0.12f)
    }
    Box(
        modifier = modifier.background(fill).border(KrtDimens.hairline, border).padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = label, maxLines = 1)
    }
}

/** The ONE rounded element besides the radio: the squadron / org-unit badge. */
@Composable
fun KrtSquadronBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(KrtPalette.Primary.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .border(KrtDimens.hairline, KrtPalette.Primary, RoundedCornerShape(999.dp))
            .padding(horizontal = KrtSpacing.sm, vertical = 4.dp),
    ) { Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = KrtPalette.Primary) }
}

/** Muted uppercase label above a group, with the hairline rule under it. */
@Composable
fun KrtSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(bottom = KrtSpacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = KrtPalette.Primary)
        Box(Modifier.weight(1f).height(KrtDimens.hairline).background(KrtPalette.Gray3))
        trailing?.invoke()
    }
}

/**
 * Collapsible section head; the closed head still shows count, amount or state chip, and the
 * chevron rotates 0° → 90°.
 *
 * Several sections may be open at once. Keep [expanded] in `rememberSaveable`.
 */
@Composable
fun KrtPanelHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    stateChip: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        Surface(
            onClick = onToggle,
            shape = RectangleShape,
            color = KrtPalette.Gray4,
            modifier = Modifier.fillMaxWidth().heightIn(min = KrtDimens.tabHeight).semantics { },
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                Box(Modifier.width(KrtDimens.activeBar).height(18.dp).background(KrtPalette.Primary))
                Spacer(Modifier.width(KrtSpacing.xs))
                Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = KrtPalette.White)
                if (count != null) KrtChip(count.toString(), KrtChipTone.MUTED)
                Spacer(Modifier.weight(1f))
                stateChip?.invoke()
                Icon(
                    painterResource(de.greluc.krt.profit.basetool.android.core.designsystem.R.drawable.ic_krt_chevron_right),
                    contentDescription = null,
                    tint = KrtPalette.Primary,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
                )
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Gray3).padding(14.dp, 12.dp), content = content)
        }
    }
}

private fun Modifier.rotate(degrees: Float) = this.then(androidx.compose.ui.draw.rotate(degrees))
