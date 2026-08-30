/*
 * Basetool Android — DAS KARTELL / Bereich Profit design system.
 * GENERATED FROM THE DESIGN SPEC (docs/design/android, chapters 00–17).
 *
 * Rule for whoever implements this: every value here is decided. Do not tune, round or
 * "improve" one. If something you need is missing, it is a spec gap — raise it, do not invent it.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.core.designsystem.R

/* ───────────────────────────── PALETTE ─────────────────────────────
 * Two groups, and they are NOT interchangeable:
 *   FILL / BORDER values — canonical brand, department and status colours.
 *   TEXT tints (…Text)   — the same meaning, lightened to clear WCAG AA on our surfaces.
 *
 * A canonical status value used as TEXT fails AA on #000 / #141414. The web stylesheet gets this
 * wrong today for chip labels (spec README correction 16); this file is the corrected mirror.
 * So: KrtPalette.Success fills a meter, KrtPalette.SuccessText writes a word.
 */
object KrtPalette {
    // House
    val Primary = Color(0xFFE77E23)        // Hausfarbe — action + identity, never plain data
    val AccentLight = Color(0xFFEEB64B)    // Zierfarbe hell — hover / pressed of anything orange
    val AccentDark = Color(0xFFC45C00)     // Zierfarbe dunkel — admin-mode header edge only

    // Greyscale (official manual)
    val Black = Color(0xFF000000)          // page canvas — flat, untextured
    val Gray4 = Color(0xFF141414)          // surface: cards, bars, sheets
    val SurfaceInput = Color(0xFF1C1C1C)   // code-only half-step: inputs, table heads, nested fill
    val Gray3 = Color(0xFF282828)          // hairlines, disabled strokes, rules
    val Gray2 = Color(0xFF646464)          // NEVER text: hairline, scrollbar thumb, disabled fill
    val Gray1 = Color(0xFFD2D2D2)          // body text
    val White = Color(0xFFFFFFFF)          // data values, emphasis
    val TextMuted = Color(0xFF8A8A8A)      // muted grey AS TEXT: labels, placeholders, hints, units

    // Status — fills / borders
    val Success = Color(0xFF239E33)
    val Warning = Color(0xFFFFD23F)
    val Danger = Color(0xFFA3000A)
    val Info = Color(0xFF355DDC)

    // Status — text tints (use these whenever the colour IS the text)
    val SuccessText = Color(0xFF2EBC3D)
    val WarningText = Color(0xFFFFD23F)    // identical: yellow already clears AA
    val DangerText = Color(0xFFF2564B)
    val InfoText = Color(0xFF6C93EF)

    // Bereichsfarben — semantic only, never decoration, never the logo
    val DeptRaumueberlegenheit = Color(0xFF37BBC0)
    val DeptForschung = Color(0xFF355DDC)
    val DeptSubRadar = Color(0xFFA3000A)
    val DeptMarinekorps = Color(0xFF7A5E96)
    val DeptProfit = Color(0xFF239E33)
    val DeptSearchRescue = Color(0xFFFFD23F)
}

/* ───────────────────────────── SPACING & SIZES ───────────────────────── */
/**
 * The ONE spacing scale — ch. 01 §5, nine steps, nothing off-scale. Names are positional so the
 * value stays readable at the call site; a screen that wants 18.dp has a layout problem.
 */
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

object KrtDimens {
    val touchTarget = 44.dp        // MINIMUM TAP AREA for rows, accordion heads, menu entries.
                                   // NOT a control height — see controlHeight (48.dp). Deriving a
                                   // field’s height from this token shrank every input once (ch. 02 §1).
    val ctaHeight = 52.dp          // bottom-anchored primary CTA
    val controlHeight = 48.dp      // FIELD / BUTTON / SELECT / SEGMENT — one control height (ch. 02 §1)
    val fieldHeight = 48.dp        // alias of controlHeight; the date/time pair matches it (ch. 02 §11)
    val navIconFloor = 48.dp       // bottom-nav and rail icon slots
    val iconButton = 44.dp         // icon-only row action (square)
    val iconButtonSmall = 40.dp    // same inside a dense card head
    val icon = 18.dp
    val iconSmall = 15.dp
    val hairline = 1.dp
    val accentRule = 2.dp          // orange under-rule of app bars and table heads
    val activeBar = 3.dp           // active tab underline / selected row bar
    val appBarHeight = 56.dp       // detail screens (back arrow + title)
    val listAppBarHeight = 64.dp   // list screens (title + org chip + bell)
    val bottomNavHeight = 64.dp
    val tabHeight = 48.dp
    val navRailWidth = 88.dp
    val listPaneWidth = 480.dp     // tablet list column in list-detail
    val tabletBreakpoint = 600.dp  // below it: phone layout
    val readableMaxWidth = 640.dp  // long-form text cap
}

/* ───────────────────────────── SHAPE ─────────────────────────────
 * Radius is ZERO everywhere. The only rounded things in the product are the pill squadron badge
 * (999.dp) and the circular radio control. Do not soften anything else.
 */
val KrtShapes = Shapes(
    extraSmall = RectangleShape,
    small = RectangleShape,
    medium = RectangleShape,
    large = RectangleShape,
    extraLarge = RectangleShape,
)

/* ───────────────────────────── TYPE ─────────────────────────────
 * One family: Lato, self-hosted (300 / 400 / 700 / 900). No second display face — hierarchy is
 * weight. Headings uppercase and tracked; body Light 300; labels Bold 700 uppercase.
 * Drop the four .ttf files into core/designsystem/src/main/res/font/.
 */
val Lato = FontFamily(
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
)

val KrtTypography = Typography(
    // Screen titles in an app bar — orange, uppercase, tracked
    titleLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp, letterSpacing = 0.9.sp),
    titleMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    titleSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.65.sp),
    // Hero numbers (balance, attendance, KPI) — Black 900
    headlineLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 22.sp),
    headlineSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 18.sp),
    // Body
    bodyLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Light, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Light, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 17.sp),
    // Labels — UPPERCASE is applied by the composable, not baked into the style
    labelLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.55.sp),
    labelSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp),
)

/** Every amount, count and timestamp renders tabular so columns line up. */
val KrtTabularNums = TextStyle(fontFeatureSettings = "tnum")

/* ───────────────────────────── THEME ─────────────────────────────
 * Dark only. There is no light theme, and Material You dynamic colour is deliberately OFF: the
 * org orange is identity, not a preference. Never call dynamicDarkColorScheme().
 *
 * Mapping notes, so nothing has to be guessed:
 *   onPrimary = Black       filled orange always carries BLACK text
 *   surfaceVariant          input / table-head fill (#1C1C1C)
 *   onSurfaceVariant        TextMuted (#8A8A8A) — NOT Gray2
 *   outline / outlineVariant  the one hairline (#282828)
 *   errorContainer          20 % danger tint, as the alert fill
 */
private val KrtColorScheme = darkColorScheme(
    primary = KrtPalette.Primary,
    onPrimary = KrtPalette.Black,
    primaryContainer = KrtPalette.Gray4,
    onPrimaryContainer = KrtPalette.Primary,
    secondary = KrtPalette.AccentLight,
    onSecondary = KrtPalette.Black,
    background = KrtPalette.Black,
    onBackground = KrtPalette.Gray1,
    surface = KrtPalette.Gray4,
    onSurface = KrtPalette.Gray1,
    surfaceVariant = KrtPalette.SurfaceInput,
    onSurfaceVariant = KrtPalette.TextMuted,
    outline = KrtPalette.Gray3,
    outlineVariant = KrtPalette.Gray3,
    error = KrtPalette.Danger,
    onError = KrtPalette.White,
    errorContainer = KrtPalette.Danger.copy(alpha = 0.20f),
    onErrorContainer = KrtPalette.DangerText,
    scrim = KrtPalette.Black.copy(alpha = 0.80f),
)

@Composable
fun KrtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KrtColorScheme,
        typography = KrtTypography,
        shapes = KrtShapes,
        content = content,
    )
}
