/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raw DAS KARTELL brand colours (Corporate Design Manual V2, mirrored by the web app's
 * `styles.css` and by `docs/design/android/artifacts/Theme.kt`).
 *
 * These are the only colours the app may render. Inventing a shade — even a "slightly darker
 * orange" for a pressed state — breaks the binding design system; use the existing tokens or the
 * documented alpha overlays instead. The KRT logo may appear exclusively in [Orange], [White] or
 * [Black].
 */
@Suppress("MagicNumber")
object KrtPalette {
    /** Hausfarbe — carries both action and brand identity (the one filled CTA, headings, focus). */
    val Orange = Color(0xFFE77E23)

    /** Zierfarbe hell — hover/press tint of [Orange], reached via a 200 ms colour transition. */
    val OrangeHover = Color(0xFFEEB64B)

    /** Zierfarbe dunkel — admin/elevated chrome (the top bar turns this colour in admin mode). */
    val OrangeDeep = Color(0xFFC45C00)

    /** Page canvas. Flat, untextured black — the honeycomb pattern was retired in 2026-07. */
    val Black = Color(0xFF000000)

    /** Data values — deliberately the brightest thing on any screen. */
    val White = Color(0xFFFFFFFF)

    /** Body text and neutral bold labels. */
    val Gray1 = Color(0xFFD2D2D2)

    /** Decorative only: rails, rules, disabled strokes. Fails WCAG AA as text — see [TextMuted]. */
    val Gray2 = Color(0xFF646464)

    /** Hairline borders and the pressed-row fill. */
    val Gray3 = Color(0xFF282828)

    /**
     * The fifth grey — `#464646`, ratified 2026-08-30 for **one** case.
     *
     * A neighbouring month's day in the date grid (design ch. 02 §11, ch. 01 §1). Those days are
     * tappable and must not read like the active month: [Gray2] is too close and [Gray3] would look
     * disabled. There is no second call site, and the chapter says a second one means somebody has
     * meant one of the other four.
     */
    val Gray2Dim = Color(0xFF464646)

    /** Standard surface: cards, app bars, tables, sheets. */
    val Gray4 = Color(0xFF141414)

    /** Half-step above [Gray4] for input fills and table heads, so controls sit above the surface. */
    val SurfaceInput = Color(0xFF1C1C1C)

    /** Muted text that still clears WCAG AA on black (≈6.1:1) — use instead of [Gray2] for copy. */
    val TextMuted = Color(0xFF8A8A8A)

    /** Danger as a fill (danger buttons, alert borders). Use [DangerText] when the hue is text. */
    val Danger = Color(0xFFA3000A)

    /** Danger as text — ≈5.3:1 on black. Buy prices render in this tint with a leading minus. */
    val DangerText = Color(0xFFF2564B)

    /** Success as a fill (Check-In button, swipe-to-read action). */
    val Success = Color(0xFF239E33)

    /** Success as text — ≈5.6:1 on black. Sell prices render in this tint with a leading plus. */
    val SuccessText = Color(0xFF2EBC3D)

    /** Warning (briefing status) and cross-org highlight. Bright enough to serve as text as well. */
    val Warning = Color(0xFFFFD23F)

    /** Info as a fill. Use [InfoText] when the hue is small text on black. */
    val Info = Color(0xFF355DDC)

    /** Info as text — ≈6.1:1 on black (planned status, informational alerts). */
    val InfoText = Color(0xFF6C93EF)

    /** Bereichsfarbe Raumüberlegenheit. Frozen by the manual — never altered, never decorative. */
    val DeptRaumueberlegenheit = Color(0xFF37BBC0)

    /** Bereichsfarbe Forschung. */
    val DeptForschung = Color(0xFF355DDC)

    /** Bereichsfarbe Sub-Radar. */
    val DeptSubRadar = Color(0xFFA3000A)

    /** Bereichsfarbe Marinekorps. */
    val DeptMarinekorps = Color(0xFF7A5E96)

    /** Bereichsfarbe Profit — the department this app serves. */
    val DeptProfit = Color(0xFF239E33)

    /** Bereichsfarbe Search and Rescue. */
    val DeptSearchRescue = Color(0xFFFFD23F)
}

/**
 * The Material 3 colour scheme of the app.
 *
 * Dark only — there is no light scheme and dynamic colour (Material You) is deliberately not used,
 * because the brand hues are fixed. Two mappings carry brand rules into stock M3 components:
 * `secondaryContainer`/`onSecondaryContainer` make every selection surface (navigation indicator,
 * selected option) render "orange background + black text", and `surfaceTint = surface` together
 * with a tonal elevation of 0 dp keeps surfaces flat, so depth comes from hairlines and corner
 * brackets rather than tint or shadow.
 */
val KrtColorScheme =
    darkColorScheme(
        primary = KrtPalette.Orange,
        onPrimary = KrtPalette.Black,
        primaryContainer = KrtPalette.OrangeDeep,
        onPrimaryContainer = KrtPalette.White,
        inversePrimary = KrtPalette.OrangeDeep,
        secondary = KrtPalette.OrangeHover,
        onSecondary = KrtPalette.Black,
        secondaryContainer = KrtPalette.Orange,
        onSecondaryContainer = KrtPalette.Black,
        tertiary = KrtPalette.Warning,
        onTertiary = KrtPalette.Black,
        tertiaryContainer = KrtPalette.Warning,
        onTertiaryContainer = KrtPalette.Black,
        background = KrtPalette.Black,
        onBackground = KrtPalette.Gray1,
        surface = KrtPalette.Gray4,
        onSurface = KrtPalette.Gray1,
        surfaceVariant = KrtPalette.SurfaceInput,
        onSurfaceVariant = KrtPalette.TextMuted,
        surfaceTint = KrtPalette.Gray4,
        surfaceDim = KrtPalette.Black,
        surfaceBright = KrtPalette.Gray3,
        surfaceContainerLowest = KrtPalette.Black,
        surfaceContainerLow = KrtPalette.Gray4,
        surfaceContainer = KrtPalette.Gray4,
        surfaceContainerHigh = KrtPalette.SurfaceInput,
        surfaceContainerHighest = KrtPalette.Gray3,
        error = KrtPalette.DangerText,
        onError = KrtPalette.Black,
        errorContainer = KrtPalette.Danger,
        onErrorContainer = KrtPalette.White,
        outline = KrtPalette.Gray3,
        outlineVariant = KrtPalette.SurfaceInput,
        scrim = KrtPalette.Black,
        inverseSurface = KrtPalette.Gray1,
        inverseOnSurface = KrtPalette.Gray4,
    )

/**
 * Brand colours that have no Material 3 slot, reachable through `KrtTheme.colors`.
 *
 * The semantic pairs exist twice on purpose: the canonical hue is the **fill** (buttons, borders,
 * swipe backgrounds), the `*Text` variant is the **text tint** that clears WCAG AA as small text on
 * black. Rendering [danger] as label text instead of [dangerText] is a contrast defect, not a
 * styling preference.
 *
 * @property dataValue bright readouts on dark chips — the brightest element on screen.
 * @property mutedDecor rails, rules and disabled strokes; never text.
 * @property success success fill (Check-In, swipe-to-read).
 * @property successText success text tint, also used for positive deltas and sell prices.
 * @property warning briefing status and cross-org highlight.
 * @property info informational fill.
 * @property infoText informational text tint (planned status).
 * @property danger destructive fill.
 * @property dangerText destructive text tint, also used for negative deltas and buy prices.
 * @property crossOrg highlight for rows and links belonging to a foreign org unit.
 * @property deptRaumueberlegenheit Bereichsfarbe, semantic use only.
 * @property deptForschung Bereichsfarbe, semantic use only.
 * @property deptSubRadar Bereichsfarbe, semantic use only.
 * @property deptMarinekorps Bereichsfarbe, semantic use only.
 * @property deptProfit Bereichsfarbe, semantic use only.
 * @property deptSearchRescue Bereichsfarbe, semantic use only.
 * @property glowPrimary the **focus** glow — 6 dp at 10 %. The only glow on an input.
 * @property glowPrimaryLg the **overlay** glow — 12 dp at 10 %, for sheets, modals and toasts.
 * @property glowEmphasis the **emphasis** glow — 12 dp at 7 %, for the bar carrying the one
 *   primary action. Quieter than the overlay on purpose: an anchored CTA is not a surface.
 * @property glowDangerLg the danger modal's overlay glow, same 12 dp at 10 %.
 */
@Immutable
@Suppress("MagicNumber")
data class KrtExtendedColors(
    val dataValue: Color = KrtPalette.White,
    val mutedDecor: Color = KrtPalette.Gray2,
    val success: Color = KrtPalette.Success,
    val successText: Color = KrtPalette.SuccessText,
    val warning: Color = KrtPalette.Warning,
    val info: Color = KrtPalette.Info,
    val infoText: Color = KrtPalette.InfoText,
    val danger: Color = KrtPalette.Danger,
    val dangerText: Color = KrtPalette.DangerText,
    val crossOrg: Color = KrtPalette.Warning,
    val deptRaumueberlegenheit: Color = KrtPalette.DeptRaumueberlegenheit,
    val deptForschung: Color = KrtPalette.DeptForschung,
    val deptSubRadar: Color = KrtPalette.DeptSubRadar,
    val deptMarinekorps: Color = KrtPalette.DeptMarinekorps,
    val deptProfit: Color = KrtPalette.DeptProfit,
    val deptSearchRescue: Color = KrtPalette.DeptSearchRescue,
    // The capped scale of design ch. 01 §1: radius ≤ 12 dp AND alpha ≤ 0.10, in three sizes.
    // 0x1A is 26/255 ≈ 10 %, 0x12 is 18/255 ≈ 7 %.
    val glowPrimary: Color = Color(0x1AE77E23),
    val glowPrimaryLg: Color = Color(0x1AE77E23),
    val glowEmphasis: Color = Color(0x12E77E23),
    val glowDangerLg: Color = Color(0x1AA3000A),
)

/**
 * Provides [KrtExtendedColors] to the composition.
 *
 * Static because the palette never changes at runtime: there is exactly one theme, so a reader of
 * this local can be skipped by recomposition entirely.
 */
val LocalKrtColors = staticCompositionLocalOf { KrtExtendedColors() }
