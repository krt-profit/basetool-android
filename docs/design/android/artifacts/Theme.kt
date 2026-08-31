package de.greluc.krt.profit.basetool.app.ui.theme

// ============================================================================
// DAS KARTELL — Profit Basetool Android theme
// Generated from the binding design system (krt-profit/design-system).
// Dark-ONLY: there is no light scheme. Dynamic color (Material You) is
// deliberately NOT used — do not call dynamicDarkColorScheme().
// Spec: 01 Foundations.dc.html
//
// SINGLE SOURCE OF TRUTH: artifacts/compose/KrtTokens.kt
//
// What this file is, precisely: a VALUE-COMPATIBLE SUBSET. Every token it declares carries the
// same NAME and the same VALUE as in KrtTokens — the full Typography (12 slots), KrtFigure,
// KrtSpacing (s4…s32) and the KrtDimens tokens this app actually uses. KrtTokens declares more
// dimens (fieldHeight, icon, iconSmall, activeBar, appBarHeight, listAppBarHeight,
// bottomNavHeight, tabHeight, navRailWidth, tabletBreakpoint, readableMaxWidth); take them from
// there — do not re-declare them here under a different name. Nothing this file declares is
// missing from KrtTokens: every name here resolves there, at the same value.
//
// It once diverged: the spacing scale, the touch rule and the icon-button size were each a round
// or two behind, nine type slots differed by 1–2 sp, and a design note ended up citing this
// file's names with the other's numbers (round 15 · R2). If the two ever disagree again,
// KrtTokens wins and this file is the bug.
// ============================================================================

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ----------------------------------------------------------------------------
// Raw brand values (Corporate Design Manual V2 + basetool styles.css).
// NEVER invent additional colors. Logo renders ONLY in orange/white/black.
// ----------------------------------------------------------------------------
object KrtPalette {
    val Orange = Color(0xFFE77E23)        // Hausfarbe — action + identity
    val OrangeHover = Color(0xFFEEB64B)   // Zierfarbe hell — hover/press tint
    val OrangeDeep = Color(0xFFC45C00)    // Zierfarbe dunkel — admin/elevated chrome
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Gray1 = Color(0xFFD2D2D2)         // body text
    val Gray2 = Color(0xFF646464)         // muted DECORATIVE (fails contrast as text)
    val Gray3 = Color(0xFF282828)         // hairlines, hover-row fill
    val Gray4 = Color(0xFF141414)         // standard surface
    val SurfaceInput = Color(0xFF1C1C1C)  // input/table-head half-step
    val TextMuted = Color(0xFF8A8A8A)     // muted TEXT (AA on black)

    val Danger = Color(0xFFA3000A)        // fills/borders only
    val DangerText = Color(0xFFF2564B)    // ≈5.3:1 on black — use for red text
    val Success = Color(0xFF239E33)
    val SuccessText = Color(0xFF2EBC3D)
    val Warning = Color(0xFFFFD23F)
    val Info = Color(0xFF355DDC)
    val InfoText = Color(0xFF6C93EF)

    // Bereichsfarben — semantic department tags ONLY, never decorative,
    // never the logo. Values frozen by the manual.
    val DeptRaumueberlegenheit = Color(0xFF37BBC0)
    val DeptForschung = Color(0xFF355DDC)
    val DeptSubRadar = Color(0xFFA3000A)
    val DeptMarinekorps = Color(0xFF7A5E96)
    val DeptProfit = Color(0xFF239E33)
    val DeptSearchRescue = Color(0xFFFFD23F)
}

// ----------------------------------------------------------------------------
// Material 3 colorScheme mapping (see spec ch. 01 for the rationale table)
// ----------------------------------------------------------------------------
val KrtColorScheme = darkColorScheme(
    primary = KrtPalette.Orange,
    onPrimary = KrtPalette.Black,               // CTA text is BLACK
    primaryContainer = KrtPalette.OrangeDeep,   // admin/elevated chrome
    onPrimaryContainer = KrtPalette.White,
    inversePrimary = KrtPalette.OrangeDeep,

    secondary = KrtPalette.OrangeHover,
    onSecondary = KrtPalette.Black,
    // M3 selection surfaces (nav indicator, selected chip/row) pull
    // secondaryContainer → brand rule "selection = orange bg + black text".
    secondaryContainer = KrtPalette.Orange,
    onSecondaryContainer = KrtPalette.Black,

    tertiary = KrtPalette.Warning,              // cross-org highlight
    onTertiary = KrtPalette.Black,
    tertiaryContainer = KrtPalette.Warning,
    onTertiaryContainer = KrtPalette.Black,

    background = KrtPalette.Black,              // flat #000, no texture
    onBackground = KrtPalette.Gray1,
    surface = KrtPalette.Gray4,                 // cards, header, tables
    onSurface = KrtPalette.Gray1,
    surfaceVariant = KrtPalette.SurfaceInput,   // inputs, table heads
    onSurfaceVariant = KrtPalette.TextMuted,
    surfaceTint = KrtPalette.Gray4,             // kills M3 tonal tinting — stay flat

    surfaceDim = KrtPalette.Black,
    surfaceBright = KrtPalette.Gray3,
    surfaceContainerLowest = KrtPalette.Black,
    surfaceContainerLow = KrtPalette.Gray4,
    surfaceContainer = KrtPalette.Gray4,
    surfaceContainerHigh = KrtPalette.SurfaceInput,
    surfaceContainerHighest = KrtPalette.Gray3,

    error = KrtPalette.DangerText,              // error-as-text passes AA
    onError = KrtPalette.Black,
    errorContainer = KrtPalette.Danger,         // error-as-fill
    onErrorContainer = KrtPalette.White,

    outline = KrtPalette.Gray3,                 // 1 dp hairlines everywhere
    outlineVariant = KrtPalette.SurfaceInput,
    scrim = KrtPalette.Black,                   // used at 80% + blur(4dp)
    inverseSurface = KrtPalette.Gray1,
    inverseOnSurface = KrtPalette.Gray4,
)

// ----------------------------------------------------------------------------
// Extended (non-M3) brand colors, reachable via KrtTheme.colors
// ----------------------------------------------------------------------------
@Immutable
data class KrtExtendedColors(
    val dataValue: Color = KrtPalette.White,        // bright readouts on dark chips
    val mutedDecor: Color = KrtPalette.Gray2,       // rails/rules only — NOT text
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
    val glowPrimary: Color = Color(0x4DE77E23),     // 0 0 5dp focus bloom (30%)
    val glowPrimaryLg: Color = Color(0x33E77E23),   // 0 0 20dp modal/CTA bloom (20%)
    val glowDangerLg: Color = Color(0x33A3000A),
)
val LocalKrtColors = staticCompositionLocalOf { KrtExtendedColors() }

// ----------------------------------------------------------------------------
// Typography — Lato only (bundled, OFL 1.1). Body = Light 300.
// Headlines/nav/labels/table heads = Bold 700 UPPERCASE (+letterSpacing);
// hero numbers = Black 900 with tabular figures (set fontFeatureSettings
// "tnum" on numeric readouts).
// ----------------------------------------------------------------------------
val Lato = FontFamily(
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
)

private val flatLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
private fun lato(w: FontWeight, size: Int, line: Int, track: Double = 0.0) = TextStyle(
    fontFamily = Lato, fontWeight = w,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = flatLineHeight,
)

// NOTE: Compose has no text-transform — UPPERCASE styles are applied by
// passing text.uppercase(locale) at the call site (wrap in KrtText helpers).
// NO display* slots. A figure NEVER names a display or headline style — it takes KrtFigure
// (total 32 / card 20 / inline 16, Black, tabular, no tracking). This file once carried
// displayMedium "hero KPI numbers" at Black 40 AND displaySmall at Black 32: two hero-number
// claims in adjacent lines, both contradicted by KrtFigure.total (round 15 · R3).
//
// Every value below is KrtTokens.kt verbatim. NINE of the twelve slots below once differed from
// it by 1–2 sp (title L/M/S, body L/M/S, label L/M/S) and were pulled onto its values; the two
// former display* slots were DELETED rather than reconciled (ch. 18 §5 · R2). If you change a
// number here without changing it there, you have reintroduced R2.
val KrtTypography = Typography(
    headlineLarge = lato(FontWeight.Black, 32, 38, 1.6),     // h1 — UPPERCASE, orange
    headlineMedium = lato(FontWeight.Bold, 24, 30, 1.2),     // h2 — UPPERCASE, orange
    headlineSmall = lato(FontWeight.Bold, 19, 25, 0.95),     // h3 — UPPERCASE
    titleLarge    = lato(FontWeight.Bold, 18, 20, 0.9),      // app-bar screen title — UPPERCASE
    titleMedium   = lato(FontWeight.Bold, 15, 18, 0.8),      // detail-screen title, row headline
    titleSmall    = lato(FontWeight.Bold, 13, 16, 0.65),     // section titles, card titles
    bodyLarge     = lato(FontWeight.Light, 14, 22),          // body default
    bodyMedium    = lato(FontWeight.Light, 13, 20),          // secondary text, helper lines
    bodySmall     = lato(FontWeight.Light, 12, 17),          // meta, timestamps, notices
    labelLarge    = lato(FontWeight.Bold, 12, 14, 0.6),      // buttons — UPPERCASE
    labelMedium   = lato(FontWeight.Bold, 11, 13, 0.55),     // chips, table heads — UPPERCASE
    labelSmall    = lato(FontWeight.Bold, 10, 12, 0.5),      // overline, chip text — UPPERCASE
)

// The figure ladder — mirrors KrtFigure in KrtTokens.kt (Black, tabular, NO letter-spacing).
object KrtFigure {
    val total  = lato(FontWeight.Black, 32, 36).copy(fontFeatureSettings = "tnum")  // one hero number per screen
    val card   = lato(FontWeight.Black, 20, 24).copy(fontFeatureSettings = "tnum")  // figure in a card or row
    val inline = lato(FontWeight.Black, 16, 20).copy(fontFeatureSettings = "tnum")  // figure beside a label
}

// ----------------------------------------------------------------------------
// Shapes — square-first. The ONLY rounded things are pill badges (999 dp),
// and the circular radio / spinner / presence dot. Status dots are SQUARE 8 dp.
// ----------------------------------------------------------------------------
val KrtShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
val PillShape = RoundedCornerShape(percent = 50)   // squadron badge / pills only

// ----------------------------------------------------------------------------
// Spacing & metrics (dp)
// ----------------------------------------------------------------------------
// The ONE spacing scale — ch. 01 §5, nine steps, nothing off-scale (A3).
// Positional names so the value stays readable at the call site.
object KrtSpacing {
    val s4 = 4.dp     // field to helper text
    val s8 = 8.dp     // inside a dense row
    val s10 = 10.dp   // between cards in a list
    val s12 = 12.dp   // between sections; card padding (vertical)
    val s14 = 14.dp   // card padding (horizontal)
    val s16 = 16.dp   // screen gutter on phone; modal padding
    val s20 = 20.dp   // sheet gutter
    val s24 = 24.dp   // tablet content gutter
    val s32 = 32.dp
}

// Three sizes, NOT one floor — the distinction that once shrank every input (ch. 02 §1 · A2).
object KrtDimens {
    val touchTarget = 44.dp      // MINIMUM TAP AREA: rows, accordion heads, menu entries.
                                 // NEVER a control height — that is controlHeight below.
    val controlHeight = 48.dp    // field / button / select / segment
    val navIconFloor = 48.dp     // bottom-nav and rail icon slots
    val ctaHeight = 52.dp        // bottom-anchored primary CTA
    val iconButton = 48.dp       // icon-only row action (round 14 · S3)
    val iconButtonSmall = 40.dp  // the ONE exception: Ablauf move buttons, 40 × 44 (ch. 18 §3)
    val contentMax = 1200.dp     // tablet content cap
    val listPaneWidth = 480.dp   // tablet list column in list-detail
    val hairline = 1.dp
    val accentRule = 2.dp        // orange under-rule on app bars and table heads (name as in KrtTokens)
    val bracket = 10.dp          // .hud-box arms; the modal frame draws 13 dp
}

// Motion: 0.2 s color transitions only. No bounces, no parallax.
// Always honor Settings.Global.ANIMATOR_DURATION_SCALE / reduced motion.
const val KRT_MOTION_MS = 200

@Composable
fun KrtTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKrtColors provides KrtExtendedColors()) {
        MaterialTheme(
            colorScheme = KrtColorScheme,   // never dynamicDarkColorScheme()
            typography = KrtTypography,
            shapes = KrtShapes,
            content = content,
        )
    }
}
