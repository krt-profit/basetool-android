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
 * An inline **warning** with the same glyph as [KrtFieldError], in the warning tint.
 *
 * The difference from an error is the whole point: a warning names something the member may well
 * have meant, and nothing is blocked. Design ch. 02 §11 uses it for a moment already gone — the
 * server, not the field, decides whether a past timestamp is legal.
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
        modifier = modifier.padding(top = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
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
 * **Accessibility is wired here rather than left to the caller**, because a field built from
 * `BasicTextField` has none of it by default and the omission is invisible on screen:
 *
 * - The **placeholder lives in the field's own `decorationBox`**, not beside it. A sibling drawn
 *   *behind* a full-width text field is obscured, and the accessibility layer prunes obscured
 *   nodes — measured on a device: the hint was legible to the eye and entirely absent from the
 *   tree, so a screen-reader user met an unlabelled box.
 * - The field carries an explicit **accessible name** (`label`, else `placeholder`). Without one
 *   `uiautomator` reports `NAF="true"` and TalkBack announces nothing but "edit box". The name has
 *   to be a semantic property rather than the visible hint alone, because the hint disappears the
 *   moment the member types — which is exactly when the field stops saying what it is for.
 * - An error is attached to the field via the **`error` semantics**, not merely rendered beneath
 *   it. A message that is only a sibling is read minutes later in traversal order, or never.
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
 * @param minLines how many lines the field stands at before it grows. Above one it takes multi-line
 *   input — a pasted export, a briefing — and the value sits at the top rather than centred.
 * @param valueStyle overrides how the typed value is rendered - size, weight and colour. Reach for
 *   it when the number IS the screen, as the aUEC amount is on the Finanz-Eintrag sheet; leave it
 *   null everywhere else so fields stay uniform.
 * @param trailing optional control at the end of the field — the combobox caret, for example. It
 *   sits inside the frame and the text area yields the width it takes.
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
            Box(modifier = Modifier.padding(top = KrtSpacing.xs))
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
                    Box(modifier = Modifier.padding(start = KrtSpacing.sm)) { trailing() }
                }
            }
        }
        if (isError && errorText != null) {
            KrtFieldError(text = errorText)
        }
    }
}

/**
 * Everything a screen reader needs from a field, in one place.
 *
 * A `BasicTextField` carries none of this by itself, and the absence is invisible on screen — which
 * is why it is a named modifier rather than three lines inlined somewhere: it is easy to leave out
 * of the next field and impossible to notice afterwards.
 *
 * @param accessibleName what the field is for, read before its content. Applied unconditionally so
 *   it survives the member typing, which is precisely when a visible hint stops naming the field.
 * @param errorMessage the validation failure to attach, or `null` when the field is valid. Attached
 *   to the field rather than left as a sibling below it, which is read minutes later in traversal
 *   order, or never.
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
 * Pulled out of [KrtTextField] so the composable itself stays under the complexity gate. Every
 * branch here is a state the field can be in rather than a variation a caller picked.
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
        .defaultMinSize(minHeight = KrtSpacing.field * minLines)
        .padding(horizontal = KrtSpacing.md, vertical = if (minLines > 1) KrtSpacing.sm else 0.dp)

/**
 * How the typed value is rendered.
 *
 * Pulled out of [KrtTextField] because the three-way merge - the ambient style, the caller's
 * override, and the field's own non-negotiables - is the one piece of that composable with real
 * branching in it.
 *
 * A caller's override wins on size and weight, but only wins on colour when it actually set one:
 * `TextStyle` reports an unset colour as `Color.Unspecified`, which as a foreground paints nothing.
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
 * The inside of a [KrtTextField]: the hint, and the editable text itself.
 *
 * **This is the field's own decoration box, not a sibling of it**, and that placement is the whole
 * point. A placeholder drawn beside the field — behind a `fillMaxWidth` text field, in the same
 * `Box` — is obscured, and the accessibility layer prunes obscured nodes: measured on a device, the
 * hint was plainly legible and entirely absent from the tree. Inside the decoration box it belongs
 * to the field's node and is both drawn and readable.
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
    // Full width and aligned by the field's own `textAlign`: without it the box wraps its content,
    // an End-aligned value has no room to move into and renders mid-field, and the placeholder of
    // such a field would sit on the opposite side from the value that replaces it.
    // A multi-line field is as tall as its `minLines`, and the box wraps that height: centring the
    // hint in it puts it three lines below the caret that will replace it.
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
 * The small "?" affordance that explains a domain rule in place.
 *
 * The web app uses it for the SCU precision note next to amount fields; the same pattern serves any
 * rule too important to omit and too long for a label. A long-press tooltip costs no layout space
 * and stays reachable for TalkBack.
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
