package de.greluc.krt.profit.basetool.app.ui.theme

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

object KrtPalette {
    val Orange = Color(0xFFE77E23)
    val OrangeHover = Color(0xFFEEB64B)
    val OrangeDeep = Color(0xFFC45C00)
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Gray1 = Color(0xFFD2D2D2)
    val Gray2 = Color(0xFF646464)
    val Gray3 = Color(0xFF282828)
    val Gray4 = Color(0xFF141414)
    val SurfaceInput = Color(0xFF1C1C1C)
    val TextMuted = Color(0xFF8A8A8A)

    val Danger = Color(0xFFA3000A)
    val DangerText = Color(0xFFF2564B)
    val Success = Color(0xFF239E33)
    val SuccessText = Color(0xFF2EBC3D)
    val Warning = Color(0xFFFFD23F)
    val Info = Color(0xFF355DDC)
    val InfoText = Color(0xFF6C93EF)

    val DeptRaumueberlegenheit = Color(0xFF37BBC0)
    val DeptForschung = Color(0xFF355DDC)
    val DeptSubRadar = Color(0xFFA3000A)
    val DeptMarinekorps = Color(0xFF7A5E96)
    val DeptProfit = Color(0xFF239E33)
    val DeptSearchRescue = Color(0xFFFFD23F)
}

val KrtColorScheme = darkColorScheme(
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

@Immutable
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
    val glowPrimary: Color = Color(0x4DE77E23),
    val glowPrimaryLg: Color = Color(0x33E77E23),
    val glowDangerLg: Color = Color(0x33A3000A),
)
val LocalKrtColors = staticCompositionLocalOf { KrtExtendedColors() }

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

val KrtTypography = Typography(
    headlineLarge = lato(FontWeight.Black, 32, 38, 1.6),
    headlineMedium = lato(FontWeight.Bold, 24, 30, 1.2),
    headlineSmall = lato(FontWeight.Bold, 19, 25, 0.95),
    titleLarge    = lato(FontWeight.Bold, 18, 20, 0.9),
    titleMedium   = lato(FontWeight.Bold, 15, 18, 0.8),
    titleSmall    = lato(FontWeight.Bold, 13, 16, 0.65),
    bodyLarge     = lato(FontWeight.Light, 14, 22),
    bodyMedium    = lato(FontWeight.Light, 13, 20),
    bodySmall     = lato(FontWeight.Light, 12, 17),
    labelLarge    = lato(FontWeight.Bold, 12, 14, 0.6),
    labelMedium   = lato(FontWeight.Bold, 11, 13, 0.55),
    labelSmall    = lato(FontWeight.Bold, 10, 12, 0.5),
)

object KrtFigure {
    val total  = lato(FontWeight.Black, 32, 36).copy(fontFeatureSettings = "tnum")
    val card   = lato(FontWeight.Black, 20, 24).copy(fontFeatureSettings = "tnum")
    val inline = lato(FontWeight.Black, 16, 20).copy(fontFeatureSettings = "tnum")
}

val KrtShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
val PillShape = RoundedCornerShape(percent = 50)

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
    val controlHeight = 48.dp
    val navIconFloor = 48.dp
    val ctaHeight = 52.dp
    val iconButton = 48.dp
    val iconButtonSmall = 40.dp
    val contentMax = 1200.dp
    val listPaneWidth = 480.dp
    val hairline = 1.dp
    val accentRule = 2.dp
    val bracket = 10.dp
}

const val KRT_MOTION_MS = 200

@Composable
fun KrtTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKrtColors provides KrtExtendedColors()) {
        MaterialTheme(
            colorScheme = KrtColorScheme,
            typography = KrtTypography,
            shapes = KrtShapes,
            content = content,
        )
    }
}
