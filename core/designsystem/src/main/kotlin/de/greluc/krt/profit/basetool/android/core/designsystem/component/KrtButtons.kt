/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtBloom
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtDashedBorder
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Opacity applied to any disabled control. Disabled never introduces a new colour. */
private const val DISABLED_ALPHA = 0.45f

/** Icon size inside a labelled button. */
private val BUTTON_ICON_SIZE = 16.dp

/** Horizontal padding of a labelled button; vertical padding is zero, the height carries it. */
private val BUTTON_HORIZONTAL_PADDING = 20.dp

/** Reach of the focus ring outside the button bounds. */
private val FOCUS_RING_OFFSET = 2.dp

/** Stroke width of the focus ring. */
private val FOCUS_RING_WIDTH = 2.dp

/**
 * The visual definition of one rung of the button ladder.
 *
 * States are expressed as separate colours rather than as alpha overlays because the design system
 * pins exact press colours (for example ghost buttons turn orange on press, they do not merely
 * darken).
 *
 * @property container fill in the resting state.
 * @property containerPressed fill while pressed.
 * @property content label and icon colour in the resting state.
 * @property contentPressed label and icon colour while pressed.
 * @property border border colour in the resting state, or `null` for borderless fills.
 * @property borderPressed border colour while pressed, or `null`.
 * @property focusRing colour of the focus ring — white on the orange CTA, orange everywhere else,
 *   because orange on orange is invisible.
 * @property bloom whether the button carries the orange bloom (reserved for the primary CTA).
 */
@Immutable
data class KrtButtonStyle(
    val container: Color,
    val containerPressed: Color,
    val content: Color,
    val contentPressed: Color,
    val border: Color?,
    val borderPressed: Color?,
    val focusRing: Color,
    val bloom: Boolean = false,
)

/**
 * The button ladder, strongest to quietest.
 *
 * Picking a rung is a content decision, not a styling one: [cta] marks *the* one primary action of
 * a screen context and must never appear twice, [success] is reserved for state changes such as
 * Check-In, [outline] is an emphasised secondary action, [ghost] is the routine repeated action,
 * and [quietDanger] is destructive and only commits to red once pressed.
 */
object KrtButtonStyles {
    /** Filled orange with black label plus bloom — maximum one per screen context. */
    val cta: KrtButtonStyle
        @Composable get() =
            KrtButtonStyle(
                container = MaterialTheme.colorScheme.primary,
                containerPressed = KrtPalette.OrangeHover,
                content = MaterialTheme.colorScheme.onPrimary,
                contentPressed = MaterialTheme.colorScheme.onPrimary,
                border = null,
                borderPressed = null,
                focusRing = KrtPalette.White,
                bloom = true,
            )

    /** Filled green with white label — state changes only. */
    val success: KrtButtonStyle
        @Composable get() =
            KrtButtonStyle(
                container = KrtTheme.colors.success,
                containerPressed = KrtTheme.colors.successText,
                content = KrtPalette.White,
                contentPressed = KrtPalette.White,
                border = null,
                borderPressed = null,
                focusRing = MaterialTheme.colorScheme.primary,
            )

    /** Transparent with an orange outline — emphasised secondary. */
    val outline: KrtButtonStyle
        @Composable get() =
            KrtButtonStyle(
                container = Color.Transparent,
                containerPressed = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                content = MaterialTheme.colorScheme.primary,
                contentPressed = KrtPalette.OrangeHover,
                border = MaterialTheme.colorScheme.primary,
                borderPressed = KrtPalette.OrangeHover,
                focusRing = MaterialTheme.colorScheme.primary,
            )

    /** Neutral hairline that turns orange on press — the routine row action. */
    val ghost: KrtButtonStyle
        @Composable get() =
            KrtButtonStyle(
                container = Color.Transparent,
                containerPressed = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                content = KrtPalette.Gray1,
                contentPressed = MaterialTheme.colorScheme.primary,
                border = KrtPalette.Gray3,
                borderPressed = MaterialTheme.colorScheme.primary,
                focusRing = MaterialTheme.colorScheme.primary,
            )

    /** Quiet grey that commits to red on press — destructive actions. */
    val quietDanger: KrtButtonStyle
        @Composable get() =
            KrtButtonStyle(
                container = Color.Transparent,
                containerPressed = KrtTheme.colors.danger.copy(alpha = 0.12f),
                content = KrtPalette.Gray2,
                contentPressed = KrtTheme.colors.dangerText,
                border = KrtPalette.Gray3,
                borderPressed = KrtTheme.colors.danger,
                focusRing = MaterialTheme.colorScheme.primary,
            )

    /**
     * Borderless icon button for app chrome — the top bar's bell and back arrow.
     *
     * Chrome sits on a surface that already carries its own edges; a hairline around every icon up
     * there would read as a second frame. Only the glyph reacts to the press.
     */
    val chrome: KrtButtonStyle
        @Composable get() =
            KrtButtonStyle(
                container = Color.Transparent,
                containerPressed = Color.Transparent,
                content = KrtPalette.Gray1,
                contentPressed = MaterialTheme.colorScheme.primary,
                border = null,
                borderPressed = null,
                focusRing = MaterialTheme.colorScheme.primary,
            )
}

/**
 * A KRT button.
 *
 * Square by definition (radius 0), at least 48 dp high, label uppercased and tracked. Prefer the
 * named wrappers [KrtCtaButton], [KrtSuccessButton], [KrtOutlineButton], [KrtGhostButton] and
 * [KrtQuietDangerButton] — they encode which rung of the ladder is appropriate; this function
 * exists for the rare case where the rung is chosen dynamically.
 *
 * @param text button label; uppercased for display.
 * @param onClick invoked on tap; not called while [enabled] is `false`.
 * @param style the ladder rung, see [KrtButtonStyles].
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input; disabled renders at 45 % opacity.
 * @param iconRes optional leading glyph, rendered at 16 dp with an 8 dp gap.
 */
@Composable
fun KrtButton(
    text: String,
    onClick: () -> Unit,
    style: KrtButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }

    val motion = androidx.compose.animation.core.tween<Color>(KrtTheme.motionMs)
    val container by animateColorAsState(
        targetValue = if (pressed) style.containerPressed else style.container,
        animationSpec = motion,
        label = "krt-button-container",
    )
    val content by animateColorAsState(
        targetValue = if (pressed) style.contentPressed else style.content,
        animationSpec = motion,
        label = "krt-button-content",
    )
    val borderColor = if (pressed) style.borderPressed else style.border

    Row(
        modifier =
            modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .then(
                    if (focused) {
                        Modifier.padding(FOCUS_RING_OFFSET).border(FOCUS_RING_WIDTH, style.focusRing)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (style.bloom &&
                        enabled
                    ) {
                        Modifier.krtBloom(KrtTheme.colors.glowPrimary, KrtSpacing.xs)
                    } else {
                        Modifier
                    },
                )
                .background(container)
                .then(if (borderColor != null) Modifier.border(KrtSpacing.hairline, borderColor) else Modifier)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(color = KrtPalette.White),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = BUTTON_HORIZONTAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            if (iconRes != null) {
                KrtIcon(id = iconRes, contentDescription = null, size = BUTTON_ICON_SIZE, tint = content)
            }
            Text(
                text = text.krtUppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

/**
 * The single primary action of a screen context (Anmelden, Speichern).
 *
 * Never render two of these in the same context — the filled orange fill is what tells the user
 * where the screen wants them to go.
 *
 * @param text button label; uppercased for display.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input.
 * @param iconRes optional leading glyph.
 */
@Composable
fun KrtCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) = KrtButton(text, onClick, KrtButtonStyles.cta, modifier, enabled, iconRes)

/**
 * A state-changing action such as Check-In.
 *
 * Green is reserved for transitions that put something into an active state; it is not a second
 * primary button.
 *
 * @param text button label; uppercased for display.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input.
 * @param iconRes optional leading glyph.
 */
@Composable
fun KrtSuccessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) = KrtButton(text, onClick, KrtButtonStyles.success, modifier, enabled, iconRes)

/**
 * An emphasised secondary action (for example "Crew zuweisen").
 *
 * @param text button label; uppercased for display.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input.
 * @param iconRes optional leading glyph.
 */
@Composable
fun KrtOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) = KrtButton(text, onClick, KrtButtonStyles.outline, modifier, enabled, iconRes)

/**
 * The routine, repeated action (Bearbeiten, Check-Out, "Mehr laden").
 *
 * @param text button label; uppercased for display.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input.
 * @param iconRes optional leading glyph.
 */
@Composable
fun KrtGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) = KrtButton(text, onClick, KrtButtonStyles.ghost, modifier, enabled, iconRes)

/**
 * A destructive action.
 *
 * Rests in quiet grey and only turns red under the finger, so deleting never looks like the
 * expected next step. Destructive flows additionally confirm through the danger modal, which names
 * the consequence.
 *
 * @param text button label; uppercased for display.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input.
 * @param iconRes optional leading glyph.
 */
@Composable
fun KrtQuietDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) = KrtButton(text, onClick, KrtButtonStyles.quietDanger, modifier, enabled, iconRes)

/**
 * A square icon-only button for repeated universal row actions (edit, delete, check-in, book-out).
 *
 * Icon-only is deliberately restricted: a rare or ambiguous action always gets a label. Because the
 * glyph carries the whole meaning, [label] is mandatory and serves both as the TalkBack description
 * and as the long-press tooltip.
 *
 * @param iconRes the glyph to render.
 * @param label spoken description and tooltip text — never omit it.
 * @param onClick invoked on tap.
 * @param modifier layout modifier.
 * @param enabled whether the button reacts to input.
 * @param style ladder rung; ghost by default, quiet danger for destructive row actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrtIconButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: KrtButtonStyle = KrtButtonStyles.ghost,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container = if (pressed) style.containerPressed else style.container
    val content = if (pressed) style.contentPressed else style.content
    val borderColor = if (pressed) style.borderPressed else style.border

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Row(
            modifier =
                modifier
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .size(KrtSpacing.touchTarget)
                    .background(container)
                    .then(if (borderColor != null) Modifier.border(KrtSpacing.hairline, borderColor) else Modifier)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(color = KrtPalette.White),
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtIcon(id = iconRes, contentDescription = label, size = BUTTON_ICON_SIZE, tint = content)
        }
    }
}

@Preview(name = "Button ladder", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ButtonLadderPreview() {
    KrtPreviewSurface {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            KrtCtaButton("Anmelden", {}, iconRes = R.drawable.ic_krt_login)
            KrtSuccessButton("Check-In", {}, iconRes = R.drawable.ic_krt_check)
            KrtOutlineButton("Crew zuweisen", {}, iconRes = R.drawable.ic_krt_users)
            KrtGhostButton("Bearbeiten", {}, iconRes = R.drawable.ic_krt_edit)
            KrtQuietDangerButton("Löschen", {}, iconRes = R.drawable.ic_krt_trash)
            KrtGhostButton("Deaktiviert", {}, enabled = false)
            KrtIconButton(R.drawable.ic_krt_bookout, "Ausbuchen", {})
        }
    }
}

/**
 * The dashed „+ …" surface that opens a picker — the design system's `.assoc-add`.
 *
 * Not a button in the ladder and deliberately quieter than one: it marks a **place something can be
 * added to** rather than an action to take now, which is why the border is dashed and the label
 * muted until it is touched. The design uses it wherever a set is being filled from a list too long
 * to lay out — the Lager's allocation chips, and the Einsatz's crew (ch. 06 artboard 14).
 *
 * A chip field over the whole candidate list is the shape this replaces: it grows with the list and
 * is four rows high at fourteen names on a 412 dp phone.
 *
 * @param text what will be added, e.g. „Person zuweisen". The „+" is drawn, not typed.
 * @param onClick opens the picker.
 * @param modifier layout modifier.
 * @param enabled whether it may be used right now.
 * @param locked whether the caller may not use it at all — draws the lock glyph in place of the
 *   plus, at full opacity beside the dimmed label, and stays tappable so the refusal can be said.
 */
@Composable
fun KrtAssocAdd(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    locked: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val accent = if (pressed) MaterialTheme.colorScheme.primary else KrtPalette.TextMuted
    val border = if (pressed) MaterialTheme.colorScheme.primary else KrtPalette.Gray3

    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .krtDashedBorder(border)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIcon(
            id = if (locked) R.drawable.ic_krt_lock else R.drawable.ic_krt_plus,
            contentDescription = null,
            size = ASSOC_GLYPH,
            tint = if (locked) KrtPalette.TextMuted else accent,
        )
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (locked) accent.copy(alpha = DISABLED_ALPHA) else accent,
        )
    }
}

/** The plus (or lock) on an `.assoc-add`. */
private val ASSOC_GLYPH = 12.dp
