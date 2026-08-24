/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Width of the orange bar marking the currently selected option. */
private val SELECTED_OPTION_BAR = 3.dp

/** Maximum height of an open option list before it scrolls. */
private val LISTBOX_MAX_HEIGHT = 280.dp

/** Edge length of the custom checkbox. */
private val CHECKBOX_SIZE = 20.dp

/**
 * A single option inside a picker.
 *
 * @property value stable identifier of the option.
 * @property label text shown to the user.
 */
data class KrtOption(
    val value: String,
    val label: String,
)

/**
 * Highlights the matched substring of a filter query inside an option label.
 *
 * The design system marks matches by weight only — bold, no highlight fill — because a coloured
 * background inside an option would collide with the orange selection rule.
 *
 * @param label the full option label.
 * @param query the current filter text; blank leaves the label unstyled.
 * @return the annotated label.
 */
private fun highlight(
    label: String,
    query: String,
): AnnotatedString =
    buildAnnotatedString {
        val start = if (query.isBlank()) -1 else label.indexOf(query, ignoreCase = true)
        if (start < 0) {
            append(label)
            return@buildAnnotatedString
        }
        append(label.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(label.substring(start, start + query.length))
        }
        append(label.substring(start + query.length))
    }

/**
 * One row of an option list.
 *
 * Two different states are shown at once and must not be confused: *active* is the keyboard/pointer
 * highlight and fills the row orange with black text (the brand selection rule); *selected* is the
 * value currently held by the field and is marked by an orange leading bar plus orange text.
 *
 * @param label option text.
 * @param onClick invoked when the option is chosen.
 * @param modifier layout modifier.
 * @param active whether this row is the highlighted one.
 * @param selected whether this option is the field's current value.
 * @param query current filter text, bolded inside the label.
 */
@Composable
private fun KrtOptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    selected: Boolean = false,
    query: String = "",
) {
    val background = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val foreground =
        when {
            active -> MaterialTheme.colorScheme.onSecondaryContainer
            selected -> MaterialTheme.colorScheme.primary
            else -> KrtPalette.Gray1
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = SELECTED_OPTION_BAR, height = KrtSpacing.xl)
                    .background(if (selected && !active) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Text(
            text = highlight(label, query),
            modifier = Modifier.padding(horizontal = KrtSpacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = foreground,
        )
    }
}

/**
 * The type-to-filter combobox.
 *
 * The web app's picker is the model: the user types, the list narrows, and a muted notice states how
 * many of the total entries remain. That notice is not decoration — it is the "no silent caps" rule
 * in visual form: a filtered list must always say what it is hiding.
 *
 * The component is stateless; the caller owns query, expansion and the filtered options, which is
 * what lets a screen restore all three after process death.
 *
 * @param query current filter text.
 * @param onQueryChange invoked as the user types.
 * @param options the already-filtered options.
 * @param onSelect invoked with the chosen option.
 * @param expanded whether the option list is open.
 * @param onExpandedChange invoked when focus or a tap opens or closes the list.
 * @param modifier layout modifier.
 * @param label optional field caption.
 * @param placeholder optional hint while the query is empty.
 * @param selectedValue value of the option currently held by the field, if any.
 * @param notice muted footer line, e.g. "2 von 118 Materialien".
 */
@Composable
fun KrtCombobox(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<KrtOption>,
    onSelect: (KrtOption) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    selectedValue: String? = null,
    notice: String? = null,
) {
    Column(modifier = modifier) {
        KrtTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                onExpandedChange(true)
            },
            label = label,
            placeholder = placeholder,
        )
        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = LISTBOX_MAX_HEIGHT)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary),
            ) {
                options.forEachIndexed { index, option ->
                    KrtOptionRow(
                        label = option.label,
                        onClick = {
                            onSelect(option)
                            onExpandedChange(false)
                        },
                        active = index == 0,
                        selected = option.value == selectedValue,
                        query = query,
                    )
                }
                if (notice != null) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * A closed-list select field.
 *
 * Renders the current value with the orange chevron of the design system; tapping it opens the
 * option list. Unlike [KrtCombobox] there is no free text — use this when every valid value is
 * known and short enough to scan.
 *
 * @param value label of the current value.
 * @param options selectable options.
 * @param onSelect invoked with the chosen option.
 * @param expanded whether the option list is open.
 * @param onExpandedChange invoked when the field is tapped.
 * @param modifier layout modifier.
 * @param label optional field caption.
 * @param selectedValue value currently held, marked in the list.
 * @param enabled whether the field accepts input.
 */
@Composable
fun KrtSelectField(
    value: String,
    options: List<KrtOption>,
    onSelect: (KrtOption) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    selectedValue: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        if (label != null) {
            KrtFieldLabel(text = label, enabled = enabled)
            Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(KrtPalette.SurfaceInput)
                    .border(
                        KrtSpacing.hairline,
                        if (expanded) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
                    )
                    .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                    .clickable(enabled = enabled, role = Role.DropdownList) { onExpandedChange(!expanded) }
                    .padding(horizontal = KrtSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = KrtPalette.White,
            )
            KrtIcon(
                id = if (expanded) R.drawable.ic_krt_chevron_up else R.drawable.ic_krt_chevron_down,
                contentDescription = null,
                size = 16.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = LISTBOX_MAX_HEIGHT)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary),
            ) {
                options.forEach { option ->
                    KrtOptionRow(
                        label = option.label,
                        onClick = {
                            onSelect(option)
                            onExpandedChange(false)
                        },
                        selected = option.value == selectedValue,
                    )
                }
            }
        }
    }
}

/**
 * The square checkbox of the design system.
 *
 * Material's checkbox is rounded and animates a checkmark stroke; this one is a square that fills
 * orange with a black check, matching the web app. The whole row is the toggle target so the label
 * is tappable.
 *
 * @param checked current state.
 * @param onCheckedChange invoked with the new state.
 * @param label the text next to the box.
 * @param modifier layout modifier.
 * @param enabled whether the control accepts input.
 */
@Composable
fun KrtCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CHECKBOX_SIZE)
                    .background(if (checked) MaterialTheme.colorScheme.primary else KrtPalette.SurfaceInput)
                    .border(KrtSpacing.hairline, if (checked) MaterialTheme.colorScheme.primary else KrtPalette.Gray3),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                KrtIcon(
                    id = R.drawable.ic_krt_check,
                    contentDescription = null,
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
}

/**
 * The radio button — one of the two circular controls the design system allows.
 *
 * @param selected whether this option is chosen.
 * @param onSelect invoked when the row is tapped.
 * @param label the text next to the control.
 * @param modifier layout modifier.
 * @param enabled whether the control accepts input.
 */
@Composable
fun KrtRadioRow(
    selected: Boolean,
    onSelect: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CHECKBOX_SIZE)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else KrtPalette.SurfaceInput)
                    .border(
                        KrtSpacing.hairline,
                        if (selected) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .size(RADIO_DOT)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
}

/** Diameter of the inner dot of a selected radio button. */
private val RADIO_DOT = 8.dp

/**
 * A compact inline select rendered as a chip — used where a value sits inside a dense row (crew
 * function, unit role) and a full field would break the rhythm.
 *
 * @param value current value.
 * @param onClick opens the option list.
 * @param modifier layout modifier.
 * @param enabled whether the control accepts input.
 */
@Composable
fun KrtChipSelect(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .defaultMinSize(minHeight = KrtSpacing.xl)
                .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.Gray1,
        )
        KrtIcon(
            id = R.drawable.ic_krt_chevron_down,
            contentDescription = null,
            size = 12.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Track size of the toggle, fixed by design chapter 13. */
private val TOGGLE_WIDTH = 44.dp
private val TOGGLE_HEIGHT = 24.dp

/** Edge length of the knob, and its inset from the track edge. */
private val TOGGLE_KNOB = 18.dp
private val TOGGLE_KNOB_INSET = 2.dp

/**
 * The switch of the design system — square, never Material's rounded one.
 *
 * Square track, square knob, no ripple halo and no elevation: the whole visual language is built on
 * hairlines and right angles, and M3's pill-shaped `Switch` is the one control that would announce
 * itself as stock Android on an otherwise in-fiction screen.
 *
 * It carries **no** click handler of its own by default use: settings rows put the toggle in their
 * trailing slot and make the entire row the target, which is both the design's behaviour and the
 * only way to reach the 48 dp minimum without inflating a 24 dp control. Pass [onCheckedChange] to
 * make the toggle itself tappable when it stands alone.
 *
 * @param checked current state.
 * @param modifier layout modifier.
 * @param enabled whether the control reads as available; a disabled toggle keeps its state but
 *   renders muted, because hiding it would read as a missing feature.
 * @param onCheckedChange optional handler; leave it out when an enclosing row owns the gesture.
 */
@Composable
fun KrtToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val motion = tween<Color>(KrtTheme.motionMs)
    val track by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else KrtPalette.SurfaceInput,
        animationSpec = motion,
        label = "toggleTrack",
    )
    val edge by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
        animationSpec = motion,
        label = "toggleBorder",
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else KrtPalette.Gray2,
        animationSpec = motion,
        label = "toggleKnob",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) TOGGLE_WIDTH - TOGGLE_KNOB - TOGGLE_KNOB_INSET else TOGGLE_KNOB_INSET,
        animationSpec = tween(KrtTheme.motionMs),
        label = "toggleKnobOffset",
    )

    Box(
        modifier =
            modifier
                .size(width = TOGGLE_WIDTH, height = TOGGLE_HEIGHT)
                .background(if (enabled) track else KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, if (enabled) edge else KrtPalette.Gray3)
                .then(
                    if (onCheckedChange == null) {
                        Modifier
                    } else {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                    },
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    // The lambda overload, because the offset is state-backed: the value
                    // version would recompose the whole toggle on every animation frame.
                    .offset { IntOffset(knobOffset.roundToPx(), 0) }
                    .size(TOGGLE_KNOB)
                    .background(if (enabled) knobColor else KrtPalette.Gray3),
        )
    }
}

/**
 * A two-or-more-way inline choice, rendered as adjoining square segments.
 *
 * Used where the options are few, short and mutually exclusive and a dropdown would be a tap too
 * many — the language pair on the settings screen is the canonical case. The selected segment is
 * orange with black text, which is the same "selection = orange, text = black" rule the navigation
 * indicator and the option sheet follow.
 *
 * **48 dp tall, not the 36 px of the web design.** This is the platform correction [KrtSpacing]
 * already documents for touch targets: on a phone this is a finger target, and a 36 dp one fails
 * Android's accessibility minimum. Segment width stays as designed unless [stretch] is set.
 *
 * @param options the segment labels, in order; each must be short enough not to wrap.
 * @param selectedIndex index of the active segment.
 * @param onSelect invoked with the index of the tapped segment.
 * @param modifier layout modifier.
 * @param enabled whether the control accepts input.
 * @param stretch whether the segments divide the available width equally instead of taking the
 *   fixed 52 dp of design chapter 13. The fixed width is right for a pair like DE/EN that sits
 *   beside other controls; a segment that *is* the control — the Einsätze/Operationen switch above
 *   a list (chapter 06 §1) — spans the row, and a word like "Operationen" does not fit 52 dp.
 */
@Composable
fun KrtSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stretch: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .height(KrtSpacing.touchTarget)
                .border(KrtSpacing.hairline, KrtPalette.Gray3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            if (index > 0) {
                Box(
                    modifier =
                        Modifier
                            .width(KrtSpacing.hairline)
                            .height(KrtSpacing.touchTarget)
                            .background(KrtPalette.Gray3),
                )
            }
            Box(
                modifier =
                    (if (stretch) Modifier.weight(1f) else Modifier.width(SEGMENT_WIDTH))
                        .height(KrtSpacing.touchTarget)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        ).selectable(
                            selected = active,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        when {
                            active -> MaterialTheme.colorScheme.onPrimary
                            enabled -> KrtPalette.TextMuted
                            else -> KrtPalette.Gray3
                        },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Width of one segment, from design chapter 13. */
private val SEGMENT_WIDTH = 52.dp

@Preview(name = "Pickers", showBackground = true, backgroundColor = 0xFF000000, widthDp = 412)
@Composable
private fun PickersPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.md)) {
            KrtCombobox(
                query = "quan",
                onQueryChange = {},
                options =
                    listOf(
                        KrtOption("quantainium", "Quantainium"),
                        KrtOption("quantum-fuel", "Quantum Fuel"),
                    ),
                onSelect = {},
                expanded = true,
                onExpandedChange = {},
                label = "Material",
                notice = "2 von 118 Materialien",
            )
            KrtSelectField(
                value = "Bereich Profit",
                options = listOf(KrtOption("iri", "Bereich Profit"), KrtOption("sk", "SK VANGUARD")),
                onSelect = {},
                expanded = false,
                onExpandedChange = {},
                label = "Org-Einheit",
                selectedValue = "iri",
            )
            KrtCheckboxRow(checked = true, onCheckedChange = {}, label = "LTI versichert")
            KrtRadioRow(selected = true, onSelect = {}, label = "Auszahlung")
            KrtRadioRow(selected = false, onSelect = {}, label = "Org-Kasse")
            KrtChipSelect(value = "Pilot", onClick = {})
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtToggle(checked = true)
                KrtToggle(checked = false)
                KrtSegmentedControl(options = listOf("DE", "EN"), selectedIndex = 0, onSelect = {})
            }
        }
    }
}
