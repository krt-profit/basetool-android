/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The press highlight of the whole app: white at 8 %, in every interaction state.
 *
 * The design system specifies one number — "Ripple: white 8%, bounded, square" (ch. 01 § 5 and the
 * global rule of ch. 02) — so all four states carry it rather than Material's graded 8/10/10/16 %
 * ladder. Setting only [ripple]'s colour, as this theme did until now, leaves the alpha at
 * Material's default and renders a press at 10 %: the right hue at the wrong strength, and
 * invisible in a screenshot comparison because the difference is two percentage points on a
 * translucent overlay.
 *
 * Focus is deliberately included even though the design system draws focus as a border rather than
 * a wash — a focused control that also carries a 10 % white fill would sit brighter than the
 * pressed state next to it.
 */
private val KrtRippleAlpha =
    RippleAlpha(
        pressedAlpha = KRT_RIPPLE_ALPHA,
        focusedAlpha = KRT_RIPPLE_ALPHA,
        draggedAlpha = KRT_RIPPLE_ALPHA,
        hoveredAlpha = KRT_RIPPLE_ALPHA,
    )

/**
 * The theme every Basetool screen is wrapped in.
 *
 * Dark only, on purpose: there is no light scheme and dynamic colour is never applied, because the
 * brand hues are fixed by the corporate design manual. The theme also replaces the default Material
 * ripple with the design system's white ripple, so presses read as a flat highlight instead of a
 * coloured wash.
 *
 * Two things are resolved here rather than at the call site because they are properties of the
 * device, not of a component: the ripple alpha (see [KrtRippleAlpha]) and the motion duration (see
 * [LocalKrtMotionMs]).
 *
 * @param content the screen content, styled by this theme.
 */
@Composable
fun KrtTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Read once per composition of the theme root. The setting changes only through the system
    // settings app, which restarts the activity, so observing it would buy nothing.
    val motionMs =
        remember(context) {
            val scale =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            if (scale == 0f) 0 else KRT_MOTION_MS
        }
    CompositionLocalProvider(
        LocalKrtColors provides KrtExtendedColors(),
        LocalIndication provides ripple(color = KrtPalette.White),
        LocalRippleConfiguration provides
            RippleConfiguration(color = KrtPalette.White, rippleAlpha = KrtRippleAlpha),
        LocalKrtMotionMs provides motionMs,
        LocalKrtBottomBarInset provides
            WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
    ) {
        MaterialTheme(
            colorScheme = KrtColorScheme,
            typography = KrtTypography,
            shapes = KrtShapes,
            content = content,
        )
    }
}

/**
 * How tall the system's bottom bar is, measured at the app root.
 *
 * A bottom sheet cannot read this for itself: it is drawn in a window that reports no
 * navigation-bar inset, so `navigationBarsPadding()` inside one resolves to nothing while the sheet
 * still paints under the gesture bar. Its action row then ends up in the system's gesture region,
 * where a tap never reaches the button — measured on a device, 2026-08-23. Captured here, at the
 * one place that is inside the activity window and above every screen that consumes insets.
 */
val LocalKrtBottomBarInset: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { 0.dp }

/**
 * How long a colour or fade transition may take, in milliseconds — [KRT_MOTION_MS], or `0` when the
 * device asks for reduced motion.
 *
 * The design system allows exactly one motion (200 ms colour/fade) and requires that it be skipped
 * when the system reports reduced motion; the app honoured the first half and not the second. On
 * Android that signal is `Settings.Global.ANIMATOR_DURATION_SCALE`, which a member sets either
 * through developer options or through **Einstellungen → Bedienungshilfen → Animationen
 * entfernen** — the accessibility toggle writes the same value, so this covers the case the rule is
 * actually about.
 *
 * Read it as [KrtTheme.motionMs] and pass it to `tween(...)`. A duration of zero makes Compose jump
 * to the target value without an intermediate frame, which is the intended behaviour: the state
 * change still happens, it simply is not animated.
 *
 * The loading spinner and the pull-to-refresh ring deliberately do **not** consult this. They are
 * not decoration — a spinner that does not spin reports "nothing is happening" while the app waits
 * on the network, which is worse for everybody than the motion it avoids.
 */
val LocalKrtMotionMs: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { KRT_MOTION_MS }

/**
 * Accessors for the parts of the theme that Material 3 has no slot for.
 *
 * Mirrors the `MaterialTheme` pattern: `KrtTheme.colors.dangerText`, `KrtTheme.spacing.lg`. Both are
 * read-only and cheap, so they can be used freely inside composables.
 */
object KrtTheme {
    /** Brand colours without a Material 3 slot — semantic tints, department hues, glows. */
    val colors: KrtExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalKrtColors.current

    /** The spacing and metric scale. */
    val spacing: KrtSpacing
        get() = KrtSpacing

    /**
     * Duration of a colour or fade transition in milliseconds — `0` when the device asks for
     * reduced motion. See [LocalKrtMotionMs] for what sets it and what deliberately ignores it.
     */
    val motionMs: Int
        @Composable @ReadOnlyComposable
        get() = LocalKrtMotionMs.current
}

/**
 * Preview scaffold for this library's component previews.
 *
 * Wraps the content in [KrtTheme] and paints the flat black page canvas with the standard screen
 * margin, so a preview shows the component as it will appear on a screen rather than on Compose's
 * default white background.
 *
 * @param content the component under preview.
 */
@Composable
internal fun KrtPreviewSurface(content: @Composable () -> Unit) {
    KrtTheme {
        Box(
            modifier =
                Modifier
                    .background(KrtPalette.Black)
                    .padding(KrtSpacing.s16),
        ) {
            content()
        }
    }
}
