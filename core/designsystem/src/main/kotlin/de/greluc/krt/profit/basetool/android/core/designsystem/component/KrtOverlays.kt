/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtBloom
import de.greluc.krt.profit.basetool.android.core.designsystem.modifier.krtCornerBrackets
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.PillShape

/** Opacity of the modal scrim. */
private const val SCRIM_ALPHA = 0.8f

/** Height of the accent edge on modals, sheets and the toast. */
private val MODAL_TOP_EDGE = 3.dp

/** Bracket leg length on a modal — longer than on a container, the modal is the loudest surface. */
private val MODAL_BRACKET = 13.dp

/**
 * Bracket stroke width on a modal.
 *
 * The same 2 dp the HUD box uses. Only the **arm** differs (13 dp against 10) — two values, both
 * straight from the stylesheet, deliberately not unified (design ch. 01 §1, corrected 2026-08-30).
 */
private val MODAL_BRACKET_STROKE = 2.dp

/** Maximum width of a modal on any form factor. */
private val MODAL_MAX_WIDTH = 440.dp

/** Reach of the bloom around a modal or toast. */
private val MODAL_BLOOM = KrtSpacing.glowOverlay

/**
 * Whether a modal asks for a routine confirmation or for a destructive one.
 *
 * The danger variant is not a colour swap for emphasis: it is reserved for actions that destroy
 * data, and its copy must name the consequence rather than ask a yes/no question.
 */
enum class KrtModalTone {
    /** Routine confirmation — orange accents. */
    Standard,

    /** Destructive confirmation — red accents, consequence named in the body. */
    Danger,
}

/**
 * The KRT modal.
 *
 * The app never uses platform dialogs: their rounded corners, tonal surface and system typography
 * contradict the design system, and Android's own alert never looks like this product. The frame is
 * the loudest surface in the app — accent top edge, 13 dp brackets, the overlay glow — because a
 * modal
 * interrupts, and an interruption must be unmistakable.
 *
 * Exactly one filled CTA, placed right, with a ghost cancel to its left; back and the scrim both
 * dismiss.
 *
 * @param title the question or statement; uppercased for display.
 * @param confirmText label of the single filled action.
 * @param onConfirm invoked when the user confirms.
 * @param onDismiss invoked on cancel, back or a scrim tap.
 * @param modifier layout modifier applied to the modal frame.
 * @param tone whether this is a routine or a destructive confirmation.
 * @param cancelText label of the ghost cancel action.
 * @param content the modal body — usually one paragraph explaining the consequence.
 */
@Composable
fun KrtModal(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: KrtModalTone = KrtModalTone.Standard,
    cancelText: String = stringResource(R.string.krt_cancel),
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent =
        if (tone == KrtModalTone.Danger) KrtTheme.colors.danger else MaterialTheme.colorScheme.primary
    val bloom =
        if (tone == KrtModalTone.Danger) KrtTheme.colors.glowDangerLg else KrtTheme.colors.glowPrimaryLg

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(KrtPalette.Black.copy(alpha = SCRIM_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    modifier
                        .widthIn(max = MODAL_MAX_WIDTH)
                        .padding(KrtSpacing.lg)
                        .krtBloom(bloom, MODAL_BLOOM)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(KrtSpacing.hairline, KrtPalette.Gray3)
                        .krtCornerBrackets(color = accent, leg = MODAL_BRACKET, stroke = MODAL_BRACKET_STROKE),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(MODAL_TOP_EDGE)
                            .background(accent),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = KrtSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title.krtUppercase(),
                        modifier = Modifier.weight(1f).padding(vertical = KrtSpacing.md),
                        style = MaterialTheme.typography.titleLarge,
                        color = KrtPalette.White,
                    )
                    KrtIconButton(
                        iconRes = R.drawable.ic_krt_close,
                        label = "Schließen",
                        onClick = onDismiss,
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = KrtSpacing.lg),
                    content = content,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm, Alignment.End),
                ) {
                    KrtGhostButton(text = cancelText, onClick = onDismiss)
                    if (tone == KrtModalTone.Danger) {
                        KrtButton(
                            text = confirmText,
                            onClick = onConfirm,
                            style =
                                KrtButtonStyles.cta.copy(
                                    container = KrtTheme.colors.danger,
                                    containerPressed = KrtTheme.colors.dangerText,
                                    content = KrtPalette.White,
                                    contentPressed = KrtPalette.White,
                                    bloom = false,
                                ),
                        )
                    } else {
                        KrtCtaButton(text = confirmText, onClick = onConfirm)
                    }
                }
            }
        }
    }
}

/**
 * A toast: near-black panel with an accent border, corner brackets and the bloom.
 *
 * This composable only renders the panel; placement and the dismissal timer belong to the screen's
 * scaffold. Destructive swipes pair it with a 5 second undo action, which is what [actionLabel] is
 * for — the inbox's "Benachrichtigung geloescht. / Rueckgaengig" of design ch. 07 is the case it
 * was added for.
 *
 * The action is a ghost button rather than a second CTA: a toast is not a screen context, and the
 * design system's one-filled-CTA rule counts the screen behind it.
 *
 * @param title short headline; uppercased and rendered in the accent colour.
 * @param message one sentence of detail.
 * @param modifier layout modifier.
 * @param isError whether this is an error toast (red accents instead of orange).
 * @param actionLabel optional action, e.g. the undo of a destructive swipe; needs [onAction].
 * @param onAction invoked when the action is pressed.
 */
@Composable
fun KrtToast(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = if (isError) KrtTheme.colors.dangerText else MaterialTheme.colorScheme.primary
    val bloom = if (isError) KrtTheme.colors.glowDangerLg else KrtTheme.colors.glowPrimaryLg

    Column(
        modifier =
            modifier
                .widthIn(max = TOAST_MAX_WIDTH)
                .krtBloom(bloom, MODAL_BLOOM)
                .background(KrtPalette.Black.copy(alpha = TOAST_FILL_ALPHA))
                .border(KrtSpacing.hairline, accent)
                .krtCornerBrackets(color = accent)
                .padding(KrtSpacing.lg),
    ) {
        Text(
            text = title.krtUppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = KrtSpacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        if (actionLabel != null && onAction != null) {
            KrtGhostButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = KrtSpacing.sm),
            )
        }
    }
}

/** Maximum width of a toast. */
private val TOAST_MAX_WIDTH = 360.dp

/** Fill opacity of a toast — near-opaque black so it reads over any content. */
private const val TOAST_FILL_ALPHA = 0.95f

/**
 * The KRT bottom sheet — the phone's answer to a side panel.
 *
 * Square (shape 0 dp) with the accent top edge, and it carries the one rounded exception the design
 * system grants besides the org badge: the pill drag handle. Used for pickers and switchers where a
 * modal would be too heavy; the org switcher is the canonical case.
 *
 * @param onDismiss invoked on swipe-down, scrim tap or back.
 * @param modifier layout modifier applied to the sheet body.
 * @param title optional uppercase heading rendered under the handle.
 * @param content the sheet content, typically a column of [KrtSheetOption]s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrtBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The sheet is bottom-anchored and draws to the very edge of the screen, which puts its
        // action row inside the system's gesture region: the tap goes to the system, not to the
        // save button (found on a device, 2026-08-23). The padding goes on the sheet itself —
        // padding its content only makes the content taller and scrolls the row out of reach
        // instead of lifting it. [LocalKrtBottomBarInset] rather than `navigationBarsPadding()`,
        // because the sheet's own window reports no navigation-bar inset at all.
        modifier = modifier.padding(bottom = LocalKrtBottomBarInset.current),
        sheetState = rememberModalBottomSheetState(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = KrtPalette.Black.copy(alpha = SCRIM_ALPHA),
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(MODAL_TOP_EDGE)
                            .background(MaterialTheme.colorScheme.primary),
                )
                Box(
                    modifier =
                        Modifier
                            .padding(top = KrtSpacing.sm, bottom = KrtSpacing.xs)
                            .size(width = DRAG_HANDLE_WIDTH, height = DRAG_HANDLE_HEIGHT)
                            .clip(PillShape)
                            .background(KrtPalette.Gray2),
                )
            }
        },
    ) {
        // The sheet draws to the bottom edge of the screen and the last thing in it is usually
        // the save action, so without this the action row lands in the system's gesture region and
        // the tap never reaches the button (found on a device, 2026-08-23).
        //
        // [LocalKrtBottomBarInset] rather than `navigationBarsPadding()`: the sheet's own window
        // reports no navigation-bar inset at all, so the modifier — and every variant of it —
        // resolves to zero here.
        Column(modifier = Modifier.imePadding()) {
            if (title != null) {
                Text(
                    text = title.krtUppercase(),
                    modifier =
                        Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
                    style = MaterialTheme.typography.labelLarge,
                    color = KrtPalette.White,
                )
            }
            content()
        }
    }
}

/** Width of the sheet drag handle. */
private val DRAG_HANDLE_WIDTH = 32.dp

/** Height of the sheet drag handle. */
private val DRAG_HANDLE_HEIGHT = 4.dp

/**
 * One row of a selection sheet, for example the org switcher.
 *
 * The selected row uses the brand selection rule — orange background with black text — which is the
 * same rule the navigation indicator and every Material selection surface follow.
 *
 * @param text the option label.
 * @param selected whether this option is the active one.
 * @param onClick invoked when the option is chosen.
 * @param modifier layout modifier.
 */
@Composable
fun KrtSheetOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val foreground =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else KrtPalette.Gray1

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = foreground,
        )
        if (selected) {
            KrtIcon(
                id = R.drawable.ic_krt_check,
                contentDescription = null,
                size = 18.dp,
                tint = foreground,
            )
        }
    }
}

@Preview(name = "Toast", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ToastPreview() {
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.md)) {
            KrtToast(title = "Gespeichert", message = "Schiff erfolgreich hinzugefügt.")
            KrtToast(
                title = "Fehler 409",
                message = "Der Eintrag wurde zwischenzeitlich geändert. Neu laden und erneut versuchen.",
                isError = true,
            )
            KrtSheetOption(text = "Bereich Profit", selected = true, onClick = {})
            KrtSheetOption(text = "SK VANGUARD", selected = false, onClick = {})
        }
    }
}
