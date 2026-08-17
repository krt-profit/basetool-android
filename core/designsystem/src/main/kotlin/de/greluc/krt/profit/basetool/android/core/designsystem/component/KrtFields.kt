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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
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

/**
 * The field label.
 *
 * Labels are neutral grey and bold — **never orange**. Orange in a form would compete with the
 * single primary action, and the brightest element inside a form must be the value the user typed,
 * not its caption.
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
 * An inline validation message with the warning glyph.
 *
 * Rendered in the danger *text* tint rather than the canonical danger red, which would fail WCAG AA
 * at this size on the dark ground.
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
        modifier = modifier.padding(top = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
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
 * The KRT text field.
 *
 * Square, 48 dp high, filled with the input half-step so the control sits above the surface. Focus
 * is signalled by an orange border plus the small bloom — the same pair the whole system uses for
 * focus — and an error swaps the border to the danger fill while the message below uses the danger
 * text tint.
 *
 * Built on `BasicTextField` rather than `OutlinedTextField` because Material's field brings a
 * floating label, its own shape and 16 dp of internal padding, none of which match this system.
 *
 * @param value current text.
 * @param onValueChange invoked on every edit.
 * @param modifier layout modifier.
 * @param label optional caption rendered above the field.
 * @param placeholder optional hint shown while [value] is empty; rendered italic and muted.
 * @param enabled whether the field accepts input.
 * @param isError whether the field currently fails validation.
 * @param errorText optional message rendered below the field when [isError] is `true`.
 * @param keyboardOptions keyboard configuration, e.g. a numeric keyboard for amounts.
 * @param textAlign horizontal alignment of the text; centre it for stepper-style numeric inputs.
 * @param tabularFigures whether digits render with fixed width; switch on for amounts.
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor =
        when {
            isError -> KrtTheme.colors.danger
            focused -> MaterialTheme.colorScheme.primary
            else -> KrtPalette.Gray3
        }

    Column(modifier = modifier) {
        if (label != null) {
            KrtFieldLabel(text = label, enabled = enabled)
            Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else DISABLED_FIELD_ALPHA)
                    .then(
                        if (focused && enabled) {
                            Modifier.krtBloom(KrtTheme.colors.glowPrimary, KrtSpacing.xs)
                        } else {
                            Modifier
                        },
                    )
                    .background(KrtPalette.SurfaceInput)
                    .border(KrtSpacing.hairline, borderColor)
                    .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                    .padding(horizontal = KrtSpacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = KrtPalette.Gray2,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle =
                    LocalTextStyle.current
                        .merge(MaterialTheme.typography.bodyLarge)
                        .copy(
                            color = KrtPalette.White,
                            textAlign = textAlign,
                            fontFeatureSettings = if (tabularFigures) KRT_TABULAR_FIGURES else null,
                        ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = keyboardOptions,
                interactionSource = interactionSource,
                singleLine = true,
            )
        }
        if (isError && errorText != null) {
            KrtFieldError(text = errorText)
        }
    }
}

/**
 * A numeric stepper: minus button, centred value, plus button.
 *
 * Both buttons are full 48 dp targets, which is what makes the control usable one-handed on a
 * phone. The value itself stays editable so a large amount can be typed instead of tapped.
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
            Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KrtIconButton(
                iconRes = R.drawable.ic_krt_minus,
                label = "Weniger",
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
                label = "Mehr",
                onClick = onIncrement,
                enabled = enabled,
            )
        }
    }
}

@Preview(name = "Form fields", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FieldsPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.md)) {
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
