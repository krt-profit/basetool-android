/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtBloom
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_TABULAR_FIGURES
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Opacity of a disabled field. */
private const val DISABLED_FIELD_ALPHA = 0.45f

/** The date half is wider than the time half: 1.35 fr to 1 fr, per design ch. 06 artboard 8. */
private const val DATE_WEIGHT = 1.35f

/**
 * The field label: neutral grey and bold, never orange.
 *
 * @param text the label.
 * @param modifier layout modifier.
 * @param enabled whether the associated field is enabled; a disabled label dims with its field.
 */
@Composable
fun KrtFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_FIELD_ALPHA),
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) KrtPalette.Gray1 else KrtPalette.Gray2,
    )
}

/**
 * An inline validation message with the warning glyph, in the danger text tint for WCAG AA
 * contrast.
 *
 * @param text the message, phrased as what to do ("Menge muss größer als 0 sein.").
 * @param modifier layout modifier.
 */
@Composable
fun KrtFieldError(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.Top,
    ) {
        KrtIcon(
            id = R.drawable.ic_krt_warning,
            contentDescription = null,
            size = 14.dp,
            tint = KrtTheme.colors.dangerText,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = KrtTheme.colors.dangerText,
        )
    }
}

/**
 * An inline warning with the same glyph as [KrtFieldError], in the warning tint `#FFD23F`.
 *
 * A warning marks something notable but permitted and locks nothing; an error displaces it, so a
 * field never shows both.
 *
 * @param text the observation, stated plainly („Liegt in der Vergangenheit").
 * @param modifier layout modifier.
 */
@Composable
fun KrtFieldWarning(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.Top,
    ) {
        KrtIcon(
            id = R.drawable.ic_krt_warning,
            contentDescription = null,
            size = 14.dp,
            tint = KrtTheme.colors.warning,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = KrtTheme.colors.warning,
        )
    }
}

/**
 * The KRT text field: square, 48 dp high, orange border and bloom on focus, danger border on error.
 *
 * Built on `BasicTextField`, with the placeholder inside the decoration box, an explicit accessible
 * name (`label`, else `placeholder`) and the error attached as `error` semantics.
 *
 * @param value current text.
 * @param onValueChange invoked on every edit.
 * @param modifier layout modifier.
 * @param label optional caption rendered above the field.
 * @param placeholder optional hint shown while [value] is empty; rendered italic and muted, and
 *   used as the field's accessible name when no [label] is given.
 * @param enabled whether the field accepts input.
 * @param isError whether the field currently fails validation.
 * @param errorText optional message rendered below the field when [isError] is `true`.
 * @param keyboardOptions keyboard configuration, e.g. a numeric keyboard for amounts.
 * @param textAlign horizontal alignment of the text; centre it for stepper-style numeric inputs.
 * @param tabularFigures whether digits render with fixed width; switch on for amounts.
 * @param minLines how many lines the field stands at before it grows; above one the value sits at
 *   the top.
 * @param valueStyle overrides size, weight and colour of the typed value; `null` for the uniform
 *   default.
 * @param trailing optional control inside the frame at the end of the field, e.g. a combobox caret.
 */
@Composable
fun KrtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textAlign: TextAlign = TextAlign.Start,
    tabularFigures: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    valueStyle: TextStyle? = null,
    minLines: Int = 1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val accessibleName = label ?: placeholder

    val borderColor =
        when {
            isError -> KrtTheme.colors.danger
            focused -> MaterialTheme.colorScheme.primary
            else -> KrtPalette.Gray3
        }

    Column(modifier = modifier) {
        if (label != null) {
            KrtFieldLabel(text = label, enabled = enabled)
            Box(modifier = Modifier.padding(top = KrtSpacing.s4))
        }
        Box(
            modifier =
                Modifier.krtFieldFrame(
                    enabled = enabled,
                    glow = focused && enabled,
                    border = borderColor,
                    minLines = minLines,
                ),
            contentAlignment = if (minLines > 1) Alignment.TopStart else Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment =
                    if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier =
                        Modifier
                            .weight(1f)
                            .krtFieldSemantics(accessibleName, if (isError) errorText else null),
                    enabled = enabled,
                    textStyle = krtValueStyle(valueStyle, textAlign, tabularFigures),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = keyboardOptions,
                    interactionSource = interactionSource,
                    singleLine = minLines == 1,
                    minLines = minLines,
                    decorationBox = { innerTextField ->
                        KrtFieldDecoration(
                            showPlaceholder = value.isEmpty(),
                            placeholder = placeholder,
                            textAlign = textAlign,
                            top = minLines > 1,
                            innerTextField = innerTextField,
                        )
                    },
                )
                if (trailing != null) {
                    Box(modifier = Modifier.padding(start = KrtSpacing.s8)) { trailing() }
                }
            }
        }
        if (isError && errorText != null) {
            KrtFieldError(text = errorText)
        }
    }
}

/**
 * Applies the accessible name and error semantics a `BasicTextField` lacks by itself.
 *
 * @param accessibleName what the field is for, read before its content; applied unconditionally so
 *   it survives typing.
 * @param errorMessage the validation failure to attach to the field, or `null` when it is valid.
 * @return the modifier chain.
 */
private fun Modifier.krtFieldSemantics(
    accessibleName: String?,
    errorMessage: String?,
): Modifier =
    this.semantics {
        accessibleName?.let { contentDescription = it }
        errorMessage?.let { error(it) }
    }

/**
 * The field's own frame: fill, border, glow and the room its content needs.
 *
 * @param enabled whether the field takes input; a disabled one is dimmed rather than recoloured, so
 *   its error border stays legible.
 * @param glow whether the focus bloom is on.
 * @param border the frame colour, already resolved for error and focus.
 * @param minLines how many lines tall the field stands.
 * @return the modifier the frame is drawn with.
 */
@Composable
private fun Modifier.krtFieldFrame(
    enabled: Boolean,
    glow: Boolean,
    border: Color,
    minLines: Int,
): Modifier =
    this
        .fillMaxWidth()
        .alpha(if (enabled) 1f else DISABLED_FIELD_ALPHA)
        .then(if (glow) Modifier.krtBloom(KrtTheme.colors.glowPrimary, KrtSpacing.glowFocus) else Modifier)
        .background(KrtPalette.SurfaceInput)
        .border(KrtSpacing.hairline, border)
        .defaultMinSize(minHeight = KrtSpacing.controlHeight * minLines)
        .padding(horizontal = KrtSpacing.s12, vertical = if (minLines > 1) KrtSpacing.s8 else 0.dp)

/**
 * Merges the ambient style, the caller's override and the field's fixed settings into the style of
 * the typed value.
 *
 * The override wins on size and weight, and on colour only when it sets one.
 *
 * @param valueStyle the caller's override, or null for the field default.
 * @param textAlign which edge the value sits against.
 * @param tabularFigures whether digits are held to one width.
 * @return the style to hand `BasicTextField`.
 */
@Composable
private fun krtValueStyle(
    valueStyle: TextStyle?,
    textAlign: TextAlign,
    tabularFigures: Boolean,
): TextStyle =
    LocalTextStyle.current
        .merge(valueStyle ?: MaterialTheme.typography.bodyLarge)
        .copy(
            color = valueStyle?.color?.takeIf { it.isSpecified } ?: KrtPalette.White,
            textAlign = textAlign,
            fontFeatureSettings = if (tabularFigures) KRT_TABULAR_FIGURES else null,
        )

/**
 * The decoration box of a [KrtTextField]: the hint and the editable text, so the hint belongs to
 * the field's accessibility node.
 *
 * @param showPlaceholder whether the field is empty and the hint should therefore be visible.
 * @param placeholder the hint, or `null` when the field has none.
 * @param textAlign which edge the value and its hint sit against.
 * @param top whether the field is multi-line, in which case both start at its first line.
 * @param innerTextField the editable text, supplied by `BasicTextField`.
 */
@Composable
private fun KrtFieldDecoration(
    showPlaceholder: Boolean,
    placeholder: String?,
    textAlign: TextAlign,
    top: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment =
            when {
                top -> Alignment.TopStart
                textAlign == TextAlign.End -> Alignment.CenterEnd
                textAlign == TextAlign.Center -> Alignment.Center
                else -> Alignment.CenterStart
            },
    ) {
        if (showPlaceholder && placeholder != null) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = KrtPalette.TextMuted,
            )
        }
        innerTextField()
    }
}

/**
 * A numeric stepper: minus button, editable centred value, plus button, each button a 48 dp target.
 *
 * @param value current value as text, already formatted with thousands separators.
 * @param onValueChange invoked when the text is edited directly.
 * @param onDecrement invoked when the minus button is tapped.
 * @param onIncrement invoked when the plus button is tapped.
 * @param modifier layout modifier.
 * @param label optional caption above the stepper.
 * @param enabled whether the stepper accepts input.
 */
@Composable
fun KrtStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        if (label != null) {
            KrtFieldLabel(text = label, enabled = enabled)
            Box(modifier = Modifier.padding(top = KrtSpacing.s4))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KrtIconButton(
                iconRes = R.drawable.ic_krt_minus,
                label = stringResource(R.string.krt_less),
                onClick = onDecrement,
                enabled = enabled,
            )
            KrtTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                keyboardOptions =
                    KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                textAlign = TextAlign.Center,
                tabularFigures = true,
            )
            KrtIconButton(
                iconRes = R.drawable.ic_krt_plus,
                label = stringResource(R.string.krt_more),
                onClick = onIncrement,
                enabled = enabled,
            )
        }
    }
}

/**
 * The small "?" affordance that explains a domain rule in place through a long-press tooltip that
 * TalkBack can reach.
 *
 * @param explanation the rule, one sentence.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrtHint(
    explanation: String,
    modifier: Modifier = Modifier,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(explanation) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .size(HINT_SIZE)
                    .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Diameter of the hint disc. */
private val HINT_SIZE = 18.dp

@Preview(name = "Form fields", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FieldsPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
            KrtTextField(value = "", onValueChange = {}, label = "Schiffsname", placeholder = "z. B. Carrack")
            KrtTextField(value = "Quantainium", onValueChange = {}, label = "Material")
            KrtTextField(
                value = "-200",
                onValueChange = {},
                label = "Menge (SCU)",
                isError = true,
                errorText = "Menge muss größer als 0 sein.",
            )
            KrtTextField(value = "Bereich Profit", onValueChange = {}, label = "Deaktiviert", enabled = false)
            KrtStepperField(
                value = "1.200",
                onValueChange = {},
                onDecrement = {},
                onIncrement = {},
                label = "Menge",
            )
        }
    }
}
