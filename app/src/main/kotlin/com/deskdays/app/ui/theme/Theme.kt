package com.deskdays.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.deskdays.app.R

/** Palette from the design brief / mockups. */
object DeskColors {
    val Background = Color(0xFFF5F6F4)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF15181C)
    val TextSecondary = Color(0xFF5B6168)
    val TextBody = Color(0xFF3A4047)
    val Accent = Color(0xFF0F766E)
    val AccentDark = Color(0xFF0B5D55)
    val AccentContainer = Color(0xFFDDF1EC)
    val Track = Color(0xFFE6E8E5)
    val Outline = Color(0xFFC9CDC8)
    val Remote = Color(0xFFDDE0DC)
    val Leave = Color(0xFFDBE6FB)
    val LeaveText = Color(0xFF1E40AF)
    val LeaveBorder = Color(0xFF2563EB)
    val BelowTarget = Color(0xFFC2410C)
    val Holiday = Color(0xFFFDEBD3)
    val HolidayText = Color(0xFF9A3412)
    val Muted = Color(0xFF6B7178)
    val CurrentMonthBar = Color(0xFF9FD3C9)
}

private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(
    variable(R.font.inter, 400),
    variable(R.font.inter, 500),
    variable(R.font.inter, 600),
    variable(R.font.inter, 700),
)

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

private val base = Typography()

private val DeskTypography = Typography(
    displayMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter, fontSize = 15.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter, fontSize = 14.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Inter, fontSize = 13.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontSize = 12.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)

private val DeskColorScheme = lightColorScheme(
    primary = DeskColors.Accent,
    onPrimary = Color.White,
    primaryContainer = DeskColors.AccentContainer,
    onPrimaryContainer = DeskColors.AccentDark,
    secondaryContainer = DeskColors.AccentContainer,
    onSecondaryContainer = DeskColors.AccentDark,
    background = DeskColors.Background,
    onBackground = DeskColors.Text,
    surface = DeskColors.Surface,
    onSurface = DeskColors.Text,
    onSurfaceVariant = DeskColors.TextSecondary,
    surfaceVariant = DeskColors.Track,
    outline = DeskColors.Outline,
    outlineVariant = DeskColors.Track,
)

/** Light-only for now, matching the mockups. A dark scheme can be added later. */
@Composable
fun DeskDaysTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DeskColorScheme, typography = DeskTypography, content = content)
}
