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

object KrtPalette {
    val Primary = Color(0xFFE77E23)
    val AccentLight = Color(0xFFEEB64B)
    val AccentDark = Color(0xFFC45C00)

    val Black = Color(0xFF000000)
    val Gray4 = Color(0xFF141414)
    val SurfaceInput = Color(0xFF1C1C1C)
    val Gray3 = Color(0xFF282828)
    val Gray2 = Color(0xFF646464)
    val Gray1 = Color(0xFFD2D2D2)
    val White = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF8A8A8A)

    val Success = Color(0xFF239E33)
    val Warning = Color(0xFFFFD23F)
    val Danger = Color(0xFFA3000A)
    val Info = Color(0xFF355DDC)

    val SuccessText = Color(0xFF2EBC3D)
    val WarningText = Color(0xFFFFD23F)
    val DangerText = Color(0xFFF2564B)
    val InfoText = Color(0xFF6C93EF)

    val DeptRaumueberlegenheit = Color(0xFF37BBC0)
    val DeptForschung = Color(0xFF355DDC)
    val DeptSubRadar = Color(0xFFA3000A)
    val DeptMarinekorps = Color(0xFF7A5E96)
    val DeptProfit = Color(0xFF239E33)
    val DeptSearchRescue = Color(0xFFFFD23F)
}

/**
 * The ONE spacing scale — ch. 01 §5, nine steps, nothing off-scale. Names are positional so the
 * value stays readable at the call site; a screen that wants 18.dp has a layout problem.
 */
object KrtSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s10 = 10.dp
    val s12 = 12.dp
    val s14 = 14.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
}

object KrtDimens {
    val touchTarget = 44.dp
    val ctaHeight = 52.dp
    val controlHeight = 48.dp
    val fieldHeight = 48.dp
    val navIconFloor = 48.dp
    val iconButton = 48.dp
    val iconButtonSmall = 40.dp
    val icon = 18.dp
    val iconSmall = 15.dp
    val hairline = 1.dp
    val accentRule = 2.dp
    val activeBar = 3.dp
    val appBarHeight = 56.dp
    val listAppBarHeight = 64.dp
    val bottomNavHeight = 64.dp
    val tabHeight = 48.dp
    val navRailWidth = 88.dp
    val listPaneWidth = 480.dp
    val tabletBreakpoint = 600.dp
    val readableMaxWidth = 640.dp
    val contentMax = 1200.dp
    val bracket = 10.dp
}

val KrtShapes = Shapes(
    extraSmall = RectangleShape,
    small = RectangleShape,
    medium = RectangleShape,
    large = RectangleShape,
    extraLarge = RectangleShape,
)

val Lato = FontFamily(
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
)

val KrtTypography = Typography(
    titleLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp, letterSpacing = 0.9.sp),
    titleMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    titleSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.65.sp),
    headlineLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 1.6.sp),
    headlineMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 1.2.sp),
    headlineSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = 0.95.sp),
    bodyLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Light, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Light, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.55.sp),
    labelSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp),
)

/** Every amount, count and timestamp renders tabular so columns line up. */
val KrtTabularNums = TextStyle(fontFeatureSettings = "tnum")

object KrtFigure {
    val total = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp, fontFeatureSettings = "tnum")
    val card = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")
    val inline = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
}

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
