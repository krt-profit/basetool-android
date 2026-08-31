/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tint fill of a row in selection mode. */
private const val SELECTED_ROW_ALPHA = 0.08f

/** Width of the orange inset bar marking a selected row. */
private val SELECTION_BAR = 3.dp

/** Size of the leading glyph in a dense row. */
private val ROW_ICON = 24.dp

/** Size of the trailing chevron. */
private val ROW_CHEVRON = 18.dp

/**
 * The canonical dense list row of the app.
 *
 * The whole row is one touch target — comfortably above the 48 dp minimum at its 56 dp height — so
 * users never have to hit a small chevron. The trailing block is where the row's number goes
 * (countdown, quantity, balance); it renders with tabular figures through [KrtDataValue] so a list
 * of numbers stays aligned.
 *
 * Long-press selects when [onLongClick] is supplied, which is how the multi-select surfaces of the
 * Lager and inbox screens work.
 *
 * @param title the record's name; truncated with an ellipsis rather than wrapped.
 * @param modifier layout modifier.
 * @param subtitle optional second line — timestamps, status, context.
 * @param leadingIcon optional leading glyph identifying the record type.
 * @param trailingValue optional bright value on the right (countdown, amount).
 * @param trailingLabel optional muted caption under [trailingValue].
 * @param showChevron whether to render the "opens a detail" chevron.
 * @param selected whether the row is currently selected in selection mode.
 * @param onClick invoked on tap.
 * @param onLongClick invoked on long press; supply it to enable selection mode.
 */
@Composable
fun KrtListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    trailingValue: String? = null,
    trailingLabel: String? = null,
    showChevron: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_ALPHA)
        } else {
            MaterialTheme.colorScheme.surface
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .defaultMinSize(minHeight = KrtSpacing.denseRow)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .width(SELECTION_BAR)
                        .size(width = SELECTION_BAR, height = KrtSpacing.s24)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (leadingIcon != null) {
            KrtIcon(
                id = leadingIcon,
                contentDescription = null,
                size = ROW_ICON,
                tint = KrtPalette.TextMuted,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingValue != null) {
            Column(horizontalAlignment = Alignment.End) {
                KrtDataValue(text = trailingValue)
                if (trailingLabel != null) {
                    Text(
                        text = trailingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
        if (showChevron) {
            KrtIcon(
                id = R.drawable.ic_krt_chevron_right,
                contentDescription = null,
                size = ROW_CHEVRON,
                tint = KrtPalette.Gray2,
            )
        }
    }
}

/** Size of the leading glyph of a settings row — a step smaller than a list row's. */
private val SETTING_ICON = 20.dp

/**
 * A row of the settings screen: leading glyph, label, optional explanation, trailing control.
 *
 * Distinct from [KrtListRow] on purpose, and not a parameter of it. A list row presents a **record**
 * — its title is the thing itself, rendered bright, and the row opens a detail. A settings row
 * presents a **control**: the label is a caption for whatever sits on the right, so it is muted, and
 * the row's job is to toggle or open that control rather than to navigate. Folding the two together
 * would mean a component whose title colour and trailing semantics depend on a flag.
 *
 * The **whole row** is the touch target when [onClick] is given, which is what lets the trailing
 * control keep its designed size — a 24 dp toggle is nowhere near tappable on its own, and the row
 * is 56 dp tall. A trailing control that handles its own gesture (a segmented control, where the row
 * cannot know which segment was meant) is placed without [onClick].
 *
 * @param title the setting's label.
 * @param modifier layout modifier.
 * @param subtitle optional second line stating the current effect in words, not the value.
 * @param leadingIcon optional glyph identifying the setting.
 * @param enabled whether the row reads as available and accepts taps.
 * @param tone colour of the label; the danger tone is reserved for destructive rows.
 * @param onClick invoked on tap; omit when the trailing control owns the gesture.
 * @param trailing the control on the right — a toggle, a segmented control, a value, a chevron.
 */
@Composable
fun KrtSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    tone: Color = KrtPalette.Gray1,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = KrtSpacing.denseRow)
                .then(
                    if (onClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    },
                ).padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            KrtIcon(
                id = leadingIcon,
                contentDescription = null,
                size = SETTING_ICON,
                tint = if (enabled) KrtPalette.TextMuted else KrtPalette.Gray3,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) tone else KrtPalette.Gray2,
            )
            if (subtitle != null) {
                // bodySmall, not labelSmall: the subtitle is a sentence, and labelSmall carries
                // 1.65 sp of tracking for UPPERCASE labels. On screen that spaced a two-line
                // explanation out until it read as a heading someone had forgotten to uppercase.
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * The "load more" control at the foot of a paginated list.
 *
 * The label always states how much of the whole is loaded ("Mehr laden — 40 von 143"): the app
 * never truncates a list silently, so the user can tell a short list from a capped one.
 *
 * @param text the label including the counts.
 * @param onClick loads the next page.
 * @param modifier layout modifier.
 * @param enabled whether loading is currently possible (disabled while a page is in flight).
 */
@Composable
fun KrtLoadMore(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    KrtGhostButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    )
}

/**
 * The end-of-list marker: a muted centred label between two hairlines.
 *
 * Shown when everything is loaded, so that the absence of a "load more" button is never ambiguous.
 *
 * @param text the marker label, e.g. "Ende der Liste"; uppercased for display.
 * @param modifier layout modifier.
 */
@Composable
fun KrtEndOfList(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
    ) {
        KrtHairlineRule(modifier = Modifier.weight(1f))
        Text(
            text = text.krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        KrtHairlineRule(modifier = Modifier.weight(1f))
    }
}

/**
 * A swipe action revealed behind a list row.
 *
 * Swipes reveal rather than commit: the design system forbids auto-committing past a threshold, and
 * a destructive swipe must be undoable through a 5 second undo toast.
 *
 * @param label action label; uppercased for display.
 * @param iconRes glyph of the action.
 * @param background full-bleed action colour (success green for "read", danger red for "delete").
 * @param modifier layout modifier.
 */
@Composable
fun KrtSwipeAction(
    label: String,
    @DrawableRes iconRes: Int,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(SWIPE_ACTION_WIDTH)
                .background(background)
                .padding(KrtSpacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KrtIcon(id = iconRes, contentDescription = null, size = 20.dp, tint = KrtPalette.White)
        Text(
            text = label.krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.White,
        )
    }
}

/** Width of a revealed swipe action — "Reveal bei 88 dp" (design ch. 07, Swipe spec). */
private val SWIPE_ACTION_WIDTH = 88.dp

/**
 * Fraction of the row width a drag must pass before release commits the action.
 *
 * "Commit ab 50% Zugweite oder Fling; darunter federt die Zeile zurueck" — below it the row springs
 * back, which is the "reveal, never auto-commit" rule of ch. 02 § 4 stated as a number.
 */
private const val SWIPE_COMMIT_FRACTION = 0.5f

/** Release speed, in px/s, that commits regardless of how far the row was dragged. */
private const val SWIPE_FLING_VELOCITY = 1000f

/**
 * Wraps a list row in the design system's two swipe actions.
 *
 * The gesture existed only as a picture until now: [KrtSwipeAction] drew the revealed tile and
 * nothing ever moved a row, so the inbox had no swipe at all. The numbers here are the spec's, not
 * defaults — reveal at [SWIPE_ACTION_WIDTH], commit at [SWIPE_COMMIT_FRACTION] of the row width or
 * on a fling, and a spring-back over `KrtTheme.motionMs`.
 *
 * Committing is left to the caller and the row is **not** removed here. A delete is optimistic with
 * a 5 s undo toast, so the list decides whether the row disappears; a "mark read" leaves it in
 * place. Either way the row animates back to rest, because a row that stayed open after its action
 * ran would read as though the action had not.
 *
 * Under reduced motion the spring-back has zero duration, so the row snaps home instead of gliding.
 * The gesture itself is a finger tracking its own position and stays available — and the row's
 * icon buttons remain the accessible path to both actions, which is what the spec requires of them
 * anyway.
 *
 * @param onStartAction invoked on a committed left-to-right swipe (the green "gelesen" reveal);
 *   `null` disables that direction.
 * @param onEndAction invoked on a committed right-to-left swipe (the red "loeschen" reveal);
 *   `null` disables that direction.
 * @param startAction the tile revealed behind a left-to-right swipe.
 * @param endAction the tile revealed behind a right-to-left swipe.
 * @param modifier layout modifier.
 * @param enabled when false the row does not move; used while a list is refreshing.
 * @param content the row itself.
 */
@Composable
fun KrtSwipeableRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onStartAction: (() -> Unit)? = null,
    onEndAction: (() -> Unit)? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val motionMs = KrtTheme.motionMs
    var rowWidth by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidth = it.width },
    ) {
        // The revealed side is decided by the sign of the offset, so only one tile is ever
        // composed — two would both be hit-testable under the row.
        if (offset.value > 0f && startAction != null) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                startAction()
            }
        } else if (offset.value < 0f && endAction != null) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                endAction()
            }
        }

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .draggable(
                        enabled = enabled && (onStartAction != null || onEndAction != null),
                        orientation = Orientation.Horizontal,
                        state =
                            rememberDraggableState { delta ->
                                scope.launch {
                                    val lower = if (onEndAction != null) -rowWidth.toFloat() else 0f
                                    val upper = if (onStartAction != null) rowWidth.toFloat() else 0f
                                    offset.snapTo((offset.value + delta).coerceIn(lower, upper))
                                }
                            },
                        onDragStopped = { velocity ->
                            val travelled = abs(offset.value)
                            val committed =
                                rowWidth > 0 &&
                                    (
                                        travelled >= rowWidth * SWIPE_COMMIT_FRACTION ||
                                            abs(velocity) >= SWIPE_FLING_VELOCITY
                                    ) &&
                                    travelled >= SWIPE_ACTION_WIDTH.value
                            if (committed) {
                                if (offset.value > 0f) onStartAction?.invoke() else onEndAction?.invoke()
                            }
                            offset.animateTo(0f, tween(motionMs))
                        },
                    ),
        ) {
            content()
        }
    }
}

@Preview(name = "List rows", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RowsPreview() {
    KrtPreviewSurface {
        Column {
            KrtListRow(
                title = "Vertikaler Abbau — Lyria",
                subtitle = "Heute · 21:00 · Geplant",
                leadingIcon = R.drawable.ic_krt_target,
                trailingValue = "in 2 Std.",
                trailingLabel = "12 angemeldet",
            )
            KrtListRow(
                title = "Ausgewählte Zeile",
                subtitle = "3 dp orangener Balken",
                leadingIcon = R.drawable.ic_krt_crate,
                selected = true,
                showChevron = false,
            )
            KrtLoadMore("Mehr laden — 40 von 143", {})
            KrtEndOfList("Ende der Liste")
        }
    }
}

/**
 * Where one step of an Ablauf stands relative to the rest of the list.
 *
 * The three values are the design system's `.step--done` / `.step--now` / plain `.step`, and they
 * are derived rather than stored: the wire carries `done` per step and nothing else, so "now" is
 * the first step that is not done. That is what artboard 06-13 draws — a „Geplant" Einsatz still
 * marks the step the crew is about to reach.
 */
enum class KrtStepState {
    /** Ticked off. Green box with a check, and the rail below it turns green with it. */
    Done,

    /** The first step that is not done — the one the list is currently about. */
    Now,

    /** Still ahead. */
    Ahead,
}

/**
 * One line of an Ablauf, drawn as the design system's `.ablauf > .step`.
 *
 * The numbered box and the rail running out of its foot are what make this a **timeline** rather
 * than a list with a status chip: progress is read down the left edge in one movement, and the
 * green segment stops exactly where the work stops. A „ERLEDIGT" chip on the right says the same
 * thing about one row and nothing at all about the list.
 *
 * Design ch. 06 artboard 13 puts the actions **on this row** rather than under it — „die Zeile
 * bleibt EINE Zeile hoch". They are centred against the whole row, so a title that wraps does not
 * drag them out of line with their neighbours.
 *
 * @param number the step's 1-based position, shown when it is not [KrtStepState.Done].
 * @param state where it stands; decides the box, the title's colour and the rail below it.
 * @param title what happens.
 * @param modifier layout modifier.
 * @param meta the time-and-place line beneath the title, or `null` when the step has none.
 * @param connected whether a rail runs on to a following step — `false` on the last row, which must
 *   not trail a stub into empty space.
 * @param actions the row's own controls, drawn at the trailing edge and vertically centred.
 */
@Composable
fun KrtStepRow(
    number: Int,
    state: KrtStepState,
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    connected: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s14),
        verticalAlignment = Alignment.Top,
    ) {
        StepRail(number = number, state = state, connected = connected)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    // The gap to the next step lives inside this row so the rail can run through
                    // it; a gap between list items would break the line at every step.
                    .padding(top = STEP_BODY_TOP, bottom = if (connected) KrtSpacing.s16 else 0.dp),
            verticalArrangement = Arrangement.spacedBy(STEP_BODY_GAP),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color =
                    when (state) {
                        KrtStepState.Done -> KrtPalette.Gray1
                        KrtStepState.Now -> KrtPalette.Orange
                        KrtStepState.Ahead -> KrtPalette.White
                    },
            )
            meta?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterVertically)) { actions() }
    }
}

/**
 * The numbered box and the rail that leaves its foot.
 *
 * @param number the step's 1-based position.
 * @param state where the step stands.
 * @param connected whether the rail continues to a following step.
 */
@Composable
private fun StepRail(
    number: Int,
    state: KrtStepState,
    connected: Boolean,
) {
    val accent =
        when (state) {
            KrtStepState.Done -> KrtPalette.Success
            KrtStepState.Now -> KrtPalette.Orange
            KrtStepState.Ahead -> KrtPalette.Gray2
        }
    Column(
        modifier = Modifier.width(STEP_NUM_SIZE).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(STEP_NUM_SIZE)
                    .background(
                        if (state == KrtStepState.Done) {
                            KrtPalette.Success.copy(alpha = STEP_DONE_FILL_ALPHA)
                        } else {
                            KrtPalette.Black
                        },
                    ).border(KrtSpacing.hairline, accent),
            contentAlignment = Alignment.Center,
        ) {
            if (state == KrtStepState.Done) {
                KrtIcon(
                    id = R.drawable.ic_krt_check,
                    contentDescription = null,
                    size = STEP_CHECK_SIZE,
                    tint = KrtPalette.SuccessText,
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.sp),
                    color = if (state == KrtStepState.Now) KrtPalette.Orange else KrtPalette.Gray1,
                )
            }
        }
        if (connected) {
            Box(
                modifier =
                    Modifier
                        .width(STEP_RAIL_WIDTH)
                        .weight(1f)
                        .background(
                            if (state == KrtStepState.Done) KrtPalette.Success else KrtPalette.Gray3,
                        ),
            )
        }
    }
}

/** The box carrying the step's number — 30 dp square, per `.ablauf .step-num`. */
private val STEP_NUM_SIZE = 30.dp

/** The check inside a ticked box, sized so it sits inside the 30 dp square with air around it. */
private val STEP_CHECK_SIZE = 16.dp

/** The rail between two boxes, per `.ablauf .step:not(:last-child)::before`. */
private val STEP_RAIL_WIDTH = 2.dp

/** The body's optical alignment with the number box, which sits a touch higher than the text. */
private val STEP_BODY_TOP = KrtSpacing.s4

/** Title to meta — they are one block, so the gap is tighter than any spacing token. */
private val STEP_BODY_GAP = 2.dp

/** The wash behind a ticked box: `rgba(35,158,51,0.10)`. */
private const val STEP_DONE_FILL_ALPHA = 0.10f
