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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.*
import kotlinx.coroutines.delay

/* ═════════════════════════════ 5 · FIELDS ═════════════════════════════
 * Labels are NEUTRAL grey, never orange — the VALUE should be the brightest thing in a form.
 * Fill #1C1C1C, hairline border, orange border + focus glow while focused. Radius 0.
 * Numeric fields accept BOTH separators: a German keyboard sends a comma.
 */

@Composable
fun KrtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    counter: Pair<Int, Int>? = null, // current to max — shown bottom-right, tabular
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.Gray1, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) KrtDimens.fieldHeight else 88.dp),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            shape = RectangleShape,
            placeholder = placeholder?.let { { Text(it, color = KrtPalette.TextMuted, style = MaterialTheme.typography.bodyMedium) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.merge(KrtTabularNums.takeIf { keyboardType == KeyboardType.Decimal || keyboardType == KeyboardType.Number } ?: MaterialTheme.typography.bodyLarge),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KrtPalette.SurfaceInput,
                unfocusedContainerColor = KrtPalette.SurfaceInput,
                disabledContainerColor = KrtPalette.SurfaceInput,
                focusedBorderColor = KrtPalette.Primary,
                unfocusedBorderColor = KrtPalette.Gray3,
                disabledBorderColor = KrtPalette.Gray3,
                errorBorderColor = KrtPalette.Danger,
                focusedTextColor = KrtPalette.White,
                unfocusedTextColor = KrtPalette.White,
                disabledTextColor = KrtPalette.TextMuted,
                cursorColor = KrtPalette.Primary,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            val note = error ?: helper
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = if (error != null) KrtPalette.DangerText else KrtPalette.TextMuted, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (counter != null) {
                Text("${counter.first} / ${counter.second}", style = MaterialTheme.typography.bodySmall.merge(KrtTabularNums), color = if (counter.first > counter.second) KrtPalette.DangerText else KrtPalette.TextMuted)
            }
        }
    }
}

/** German keyboards send a comma. Parse both, always. */
fun String.krtToDoubleOrNull(): Double? = replace(",", ".").trim().toDoubleOrNull()

/**
 * Date + time as a PAIR of fields, never one ISO string. Spec Kap. 06 artboard 8 / Kap. 11.
 * Date takes 1.35 fr, time 1 fr; both centred, tabular.
 */
@Composable
fun KrtDateTimeField(label: String, date: String, time: String, onDate: (String) -> Unit, onTime: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.Gray1, modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Box(Modifier.weight(1.35f)) { KrtTextField(date, onDate, label = "", enabled = enabled, keyboardType = KeyboardType.Number) }
            Box(Modifier.weight(1f)) { KrtTextField(time, onTime, label = "", enabled = enabled, keyboardType = KeyboardType.Number) }
        }
    }
}

/**
 * Type-to-filter combobox — the ONE control that resolves a member, a material, a location.
 * The list is a bordered popover under the field; the typed part of each option is BOLD; a muted
 * italic notice row states the cap ("n von m Treffern — weiter tippen grenzt ein."). The cap is
 * always stated, never silent.
 */
data class KrtOption(val value: String, val label: String, val match: IntRange? = null)

@Composable
fun KrtCombobox(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<KrtOption>,
    onSelect: (KrtOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String? = null,
    notice: String? = null,
    searching: Boolean = false,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        KrtTextField(
            value = query,
            onValueChange = { expanded = true; onQueryChange(it) },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
        )
        if (expanded && (options.isNotEmpty() || notice != null)) {
            Column(Modifier.fillMaxWidth().padding(top = KrtSpacing.xxs).background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Primary)) {
                options.forEach { option ->
                    Surface(onClick = { expanded = false; onSelect(option) }, shape = RectangleShape, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = KrtDimens.touchTarget)) {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.Gray1)
                        }
                    }
                    HorizontalDivider(color = KrtPalette.Gray3, thickness = KrtDimens.hairline)
                }
                if (searching) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = KrtPalette.Primary)
                        Text("Suche läuft…", style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
                    }
                } else if (notice != null) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

/* ═════════════════════════════ 6 · OVERLAYS ═════════════════════════════
 * NO native dialogs. Ever. No AlertDialog default styling, no Toast, no Snackbar chrome.
 * Sheet  = 3 dp orange top edge, #141414, 32 × 4 dp grab handle, 20 dp gutter, overlay glow.
 * Modal  = orange top edge + HUD brackets on an 80 % black + 4 dp blur scrim; ONE filled CTA,
 *          ghost cancel; a destructive modal NAMES the consequence in numbers.
 * Scrim  = KrtPalette.Black.copy(alpha = .8f) everywhere.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrtBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.krtOverlayGlow(),
        shape = RectangleShape,
        containerColor = KrtPalette.Gray4,
        scrimColor = KrtPalette.Black.copy(alpha = 0.8f),
        tonalElevation = 0.dp,
        dragHandle = {
            Column {
                Box(Modifier.fillMaxWidth().height(KrtDimens.activeBar).background(KrtPalette.Primary))
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).background(KrtPalette.Gray2, androidx.compose.foundation.shape.RoundedCornerShape(999.dp)))
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.lg).padding(bottom = KrtSpacing.lg), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Column {
                Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, color = KrtPalette.White)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
            content()
        }
    }
}

@Composable
fun KrtModal(
    onDismiss: () -> Unit,
    title: String,
    danger: Boolean = false,
    confirm: @Composable () -> Unit,
    cancel: @Composable () -> Unit = { KrtGhostButton("Abbrechen", onDismiss) },
    body: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 360.dp).background(KrtPalette.Gray4).border(KrtDimens.hairline, KrtPalette.Gray3).krtOverlayGlow(if (danger) KrtPalette.Danger else KrtPalette.Primary),
        ) {
            Box(Modifier.fillMaxWidth().height(KrtDimens.activeBar).background(if (danger) KrtPalette.Danger else KrtPalette.Primary))
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = KrtPalette.White, modifier = Modifier.padding(KrtSpacing.md))
            Column(Modifier.padding(horizontal = KrtSpacing.md), content = body)
            Row(Modifier.fillMaxWidth().padding(KrtSpacing.md), horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                Box(Modifier.weight(1f)) { cancel() }
                Box(Modifier.weight(1.3f)) { confirm() }
            }
        }
    }
}

/**
 * Corner-bracket toast — bottom, 16 dp inset, 4 s, SINGLETON: a second raise resets the timer,
 * nothing stacks. This is the single channel for a refused tap (see KrtGated) and for a
 * confirmation that needs no decision. Never a Material Snackbar.
 */
@Composable
fun KrtToast(title: String, detail: String? = null, tone: KrtChipTone = KrtChipTone.WARNING, iconRes: Int? = null, onTimeout: () -> Unit) {
    LaunchedEffect(title, detail) { delay(4_000); onTimeout() }
    val edge = when (tone) {
        KrtChipTone.DANGER -> KrtPalette.Danger
        KrtChipTone.SUCCESS -> KrtPalette.Success
        KrtChipTone.INFO -> KrtPalette.Info
        else -> KrtPalette.Warning
    }
    Row(
        Modifier.fillMaxWidth().padding(KrtSpacing.md).background(KrtPalette.Gray4).border(KrtDimens.hairline, edge).krtOverlayGlow(edge).padding(horizontal = KrtSpacing.md, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        if (iconRes != null) Icon(painterResource(iconRes), contentDescription = null, tint = edge, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.sp), color = KrtPalette.White)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
