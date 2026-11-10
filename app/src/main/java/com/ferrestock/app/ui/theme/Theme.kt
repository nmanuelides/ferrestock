package com.ferrestock.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferrestock.app.R

// Paleta de la marca
val Navy = Color(0xFF001A6E)
val Blue = Color(0xFF074799)
val Teal = Color(0xFF009990)
val Lime = Color(0xFFE1FFBB)

// Derivados
val Sheet = Color(0xFFF6FAF0)      // fondo claro de las "hojas", blanco con un toque de lima
val Muted = Color(0xFF5A678F)      // texto secundario
val Line = Color(0xFFD5DCEC)       // bordes y divisores
val Danger = Color(0xFFC62828)
val DangerSoft = Color(0xFFFFE3E0)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Outfit = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700))

private val base = Typography()

private fun TextStyle.outfit(weight: FontWeight? = null) =
    copy(fontFamily = Outfit, fontWeight = weight ?: fontWeight)

private val AppTypography = Typography(
    displayLarge = base.displayLarge.outfit(FontWeight.Bold),
    displayMedium = base.displayMedium.outfit(FontWeight.Bold),
    displaySmall = base.displaySmall.outfit(FontWeight.Bold),
    headlineLarge = base.headlineLarge.outfit(FontWeight.Bold),
    headlineMedium = base.headlineMedium.outfit(FontWeight.Bold),
    headlineSmall = base.headlineSmall.outfit(FontWeight.SemiBold),
    titleLarge = base.titleLarge.outfit(FontWeight.SemiBold),
    titleMedium = base.titleMedium.outfit(FontWeight.SemiBold).copy(fontSize = 17.sp),
    titleSmall = base.titleSmall.outfit(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.outfit(),
    bodyMedium = base.bodyMedium.outfit().copy(fontSize = 15.sp),
    bodySmall = base.bodySmall.outfit().copy(fontSize = 13.sp),
    labelLarge = base.labelLarge.outfit(FontWeight.SemiBold).copy(fontSize = 15.sp),
    labelMedium = base.labelMedium.outfit(FontWeight.Medium),
    labelSmall = base.labelSmall.outfit(FontWeight.Medium),
)

private val AppColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Navy,
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = Lime,
    onSecondaryContainer = Navy,
    tertiary = Blue,
    background = Sheet,
    onBackground = Navy,
    surface = Sheet,
    onSurface = Navy,
    surfaceVariant = Color.White,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
    onError = Color.White,
    inverseSurface = Navy,
    inverseOnSurface = Color.White,
    inversePrimary = Lime,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FerrestockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
