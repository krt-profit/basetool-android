/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/** Width of the orange bar marking the currently selected option. */
private val SELECTED_OPTION_BAR = 3.dp

/** Size of the combobox caret; the CSS paints a 12x8 chevron, square at 16 dp reads the same. */
private val CARET_SIZE = 16.dp

/** Edge length of the custom checkbox. */
private val CHECKBOX_SIZE = 20.dp

/**
 * Draws the listbox frame on the left, right and bottom, leaving the top open so the list reads as
 * an extension of the field.
 *
 * @param color the frame colour.
 * @return the modifier drawing the frame.
 */
private fun Modifier.krtListboxFrame(color: Color): Modifier =
    drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val half = stroke / 2
        drawLine(color, Offset(half, 0f), Offset(half, size.height), stroke)
        drawLine(color, Offset(size.width - half, 0f), Offset(size.width - half, size.height), stroke)
        drawLine(color, Offset(0f, size.height - half), Offset(size.width, size.height - half), stroke)
    }

/**
 * Draws a hairline over the bottom of a row, so an orange active row keeps its separator.
 *
 * @param color the hairline colour.
 * @return the modifier drawing the line.
 */
private fun Modifier.krtRowHairline(color: Color): Modifier =
    drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val y = size.height - stroke / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
    }

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
 * Bolds the matched substring of a filter query inside an option label.
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
 * *Active* (the keyboard/pointer highlight) fills the row orange with black text; *selected* (the
 * field's current value) shows an orange leading bar and orange text.
 *
 * @param label option text.
 * @param onClick invoked when the option is chosen.
 * @param modifier layout modifier.
 * @param active whether this row is the highlighted one.
 * @param selected whether this option is the field's current value.
 * @param query current filter text, bolded inside the label.
 * @param divider whether a hairline closes the row off - every option but the listbox's last.
 */
@Composable
private fun KrtOptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    selected: Boolean = false,
    query: String = "",
    divider: Boolean = false,
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
                .then(if (divider) Modifier.krtRowHairline(KrtPalette.Gray3) else Modifier)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = SELECTED_OPTION_BAR, height = KrtSpacing.s24)
                    .background(if (selected && !active) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Text(
            text = highlight(label, query),
            modifier = Modifier.padding(horizontal = KrtSpacing.s12),
            style = MaterialTheme.typography.bodyMedium,
            color = foreground,
        )
    }
}

/**
 * The type-to-filter combobox, whose muted notice states how many of the total entries remain.
 *
 * Stateless: the caller owns query, expansion and the filtered options.
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
 * @param enabled whether the field accepts input; a disabled field never opens its list.
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
    enabled: Boolean = true,
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
            enabled = enabled,
            trailing = {
                KrtIcon(
                    id = if (expanded) R.drawable.ic_krt_chevron_up else R.drawable.ic_krt_chevron_down,
                    contentDescription = null,
                    size = CARET_SIZE,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        if (expanded && enabled) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(KrtPalette.Gray4)
                        .krtListboxFrame(MaterialTheme.colorScheme.primary),
            ) {
                val rows = options.size + if (notice != null) 1 else 0
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
                        divider = index < rows - 1,
                    )
                }
                if (notice != null) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(KrtSpacing.s12),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * A closed-list select field with the orange chevron; unlike [KrtCombobox] it takes no free text.
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
            Box(modifier = Modifier.padding(top = KrtSpacing.s4))
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
                    .defaultMinSize(minHeight = KrtSpacing.controlHeight)
                    .clickable(enabled = enabled, role = Role.DropdownList) { onExpandedChange(!expanded) }
                    .padding(horizontal = KrtSpacing.s12),
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
                        .background(KrtPalette.Gray4)
                        .krtListboxFrame(MaterialTheme.colorScheme.primary),
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
 * The 22 dp unfilled checkbox a list row wears during a multi-selection, distinct from
 * [KrtCheckboxRow]'s form control.
 *
 * Renders state only; the row's `toggleable` owns the tap and the semantics.
 *
 * @param checked whether the row is in the selection.
 * @param modifier placement within the row.
 */
@Composable
fun KrtSelectionCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(SELECTION_CHECKBOX_SIZE)
                .then(if (checked) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                .border(
                    KrtSpacing.hairline,
                    if (checked) MaterialTheme.colorScheme.primary else KrtPalette.Gray2,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            KrtIcon(
                id = R.drawable.ic_krt_check,
                contentDescription = null,
                size = SELECTION_CHECK_GLYPH,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Edge length of the tree's selection mark — design ch. 09, artboard 5. */
private val SELECTION_CHECKBOX_SIZE = 22.dp

/** The check inside it. */
private val SELECTION_CHECK_GLYPH = 15.dp

/**
 * The square checkbox of the design system, filling orange with a black check; the whole row is the
 * toggle target.
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
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
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
    supporting: String? = null,
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
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
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
        Column {
            Text(
                text = label,
                style =
                    if (supporting == null) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                color = if (supporting == null) KrtPalette.Gray1 else KrtPalette.White,
            )
            supporting?.let { line ->
                Text(
                    text = line,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = RADIO_SUPPORTING_SIZE,
                            lineHeight = RADIO_SUPPORTING_LINE,
                        ),
                    color = KrtPalette.TextMuted,
                )
            }
        }
    }
}

/** Size of a radio's supporting line — the design system's `--fs-2xs` rung in Light. */
private val RADIO_SUPPORTING_SIZE = 11.sp

/** Its line height, the 1.4 ratio the whole scale uses. */
private val RADIO_SUPPORTING_LINE = 15.sp

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
                .defaultMinSize(minHeight = KrtSpacing.s24)
                .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
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
 * The square switch of the design system.
 *
 * Has no click handler unless [onCheckedChange] is passed; settings rows make the whole row the
 * target instead.
 *
 * @param checked current state.
 * @param modifier layout modifier.
 * @param enabled whether the control reads as available; a disabled toggle keeps its state but
 *   renders muted.
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
                    .offset { IntOffset(knobOffset.roundToPx(), 0) }
                    .size(TOGGLE_KNOB)
                    .background(if (enabled) knobColor else KrtPalette.Gray3),
        )
    }
}

/**
 * A few-way inline choice rendered as adjoining square segments, 48 dp tall; the selected segment is
 * orange with black text.
 *
 * @param options the segment labels, in order; each must be short enough not to wrap.
 * @param selectedIndex index of the active segment.
 * @param onSelect invoked with the index of the tapped segment.
 * @param modifier layout modifier.
 * @param enabled whether the control accepts input.
 * @param stretch whether the segments divide the available width equally instead of taking a fixed
 *   52 dp each.
 * @param activeColor fill of the chosen segment; orange by default.
 * @param activeContentColor label colour on that fill.
 * @param icons an optional leading icon per option, matched by position; a missing or null entry
 *   leaves that segment iconless.
 * @param lockedIndices options the caller may not have, drawn with a trailing padlock and still
 *   tappable so the screen can explain the lock.
 */
@Composable
fun KrtSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stretch: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    @DrawableRes icons: List<Int>? = null,
    lockedIndices: Set<Int> = emptySet(),
) {
    Row(
        modifier =
            modifier
                .height(KrtSpacing.controlHeight)
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
                            .height(KrtSpacing.controlHeight)
                            .background(KrtPalette.Gray3),
                )
            }
            Box(
                modifier =
                    (if (stretch) Modifier.weight(1f) else Modifier.width(SEGMENT_WIDTH))
                        .height(KrtSpacing.controlHeight)
                        .background(if (active) activeColor else Color.Transparent)
                        .selectable(
                            selected = active,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                val tint =
                    when {
                        active -> activeContentColor
                        enabled -> KrtPalette.TextMuted
                        else -> KrtPalette.Gray3
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons?.getOrNull(index)?.let { iconRes ->
                        KrtIcon(
                            id = iconRes,
                            contentDescription = null,
                            size = SEGMENT_ICON_SIZE,
                            tint = tint,
                        )
                    }
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (index in lockedIndices) {
                        KrtInlineLock()
                    }
                }
            }
        }
    }
}

/** Width of one segment, from design chapter 13. */
private val SEGMENT_WIDTH = 52.dp

/** The leading icon in a segment: one step below the 20 dp row icon, so the label stays the anchor. */
private val SEGMENT_ICON_SIZE = 16.dp

@Preview(name = "Pickers", showBackground = true, backgroundColor = 0xFF000000, widthDp = 412)
@Composable
private fun PickersPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12)) {
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
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtToggle(checked = true)
                KrtToggle(checked = false)
                KrtSegmentedControl(options = listOf("DE", "EN"), selectedIndex = 0, onSelect = {})
            }
        }
    }
}
