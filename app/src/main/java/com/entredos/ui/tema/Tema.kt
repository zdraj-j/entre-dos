package com.entredos.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entredos.R

/*
 * Diseño aprobado: acento violeta, naranja solo para alertas, fondo con un leve tono lila
 * y letra Manrope (incluida en la app, licencia OFL en docs/licencias).
 */

private val Claro = lightColorScheme(
    primary = Color(0xFF5B3FB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE6FA),
    onPrimaryContainer = Color(0xFF3B2585),
    secondary = Color(0xFF5E5A6B),
    secondaryContainer = Color(0xFFECE6FA),
    onSecondaryContainer = Color(0xFF3B2585),
    error = Color(0xFFB5541C),
    onError = Color.White,
    errorContainer = Color(0xFFFBEADF),
    onErrorContainer = Color(0xFF6B2C0A),
    background = Color(0xFFF6F4FA),
    onBackground = Color(0xFF1C1A24),
    surface = Color(0xFFF6F4FA),
    onSurface = Color(0xFF1C1A24),
    onSurfaceVariant = Color(0xFF5E5A6B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1EEF7),
    outline = Color(0xFF8E8A9B),
    outlineVariant = Color(0xFFE6E2EF),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFB9A3FF),
    onPrimary = Color(0xFF1C1433),
    primaryContainer = Color(0xFF2D2545),
    onPrimaryContainer = Color(0xFFD9CCFF),
    secondary = Color(0xFFB4AFC2),
    secondaryContainer = Color(0xFF2D2545),
    onSecondaryContainer = Color(0xFFD9CCFF),
    error = Color(0xFFFFB08A),
    onError = Color(0xFF3A1A08),
    errorContainer = Color(0xFF3A2419),
    onErrorContainer = Color(0xFFFFD3BD),
    background = Color(0xFF141219),
    onBackground = Color(0xFFECE9F3),
    surface = Color(0xFF141219),
    onSurface = Color(0xFFECE9F3),
    onSurfaceVariant = Color(0xFFB4AFC2),
    surfaceContainerLowest = Color(0xFF1F1C26),
    surfaceContainerLow = Color(0xFF1F1C26),
    surfaceContainer = Color(0xFF1F1C26),
    surfaceContainerHigh = Color(0xFF29252F),
    outline = Color(0xFF8A8598),
    outlineVariant = Color(0xFF2F2B38),
)

/** Colores que Material no tiene: los dos titulares y el texto tenue. */
@Immutable
data class ColoresExtra(val titularA: Color, val titularB: Color, val sobreTitular: Color, val tenue: Color)

private val ExtraClaro = ColoresExtra(Color(0xFF5B3FB0), Color(0xFF2F7F8F), Color.White, Color(0xFF8E8A9B))
private val ExtraOscuro = ColoresExtra(Color(0xFFB9A3FF), Color(0xFF7FC8D6), Color(0xFF1C1433), Color(0xFF8A8598))

val LocalColoresExtra = staticCompositionLocalOf { ExtraClaro }

object Extra {
    val colores: ColoresExtra @Composable @ReadOnlyComposable get() = LocalColoresExtra.current
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(peso: FontWeight) = Font(R.font.manrope, peso, variationSettings = FontVariation.Settings(FontVariation.weight(peso.weight)))

val Manrope = FontFamily(
    manrope(FontWeight.Normal), manrope(FontWeight.Medium), manrope(FontWeight.SemiBold), manrope(FontWeight.Bold), manrope(FontWeight.ExtraBold),
)

private val base = Typography()
private fun TextStyle.m(peso: FontWeight? = null) = copy(fontFamily = Manrope, fontWeight = peso ?: fontWeight)

private val Tipografia = Typography(
    displayLarge = base.displayLarge.m(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.m(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.m(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.m(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.m(FontWeight.ExtraBold).copy(fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.m(FontWeight.ExtraBold),
    titleLarge = base.titleLarge.m(FontWeight.ExtraBold).copy(fontSize = 20.sp),
    titleMedium = base.titleMedium.m(FontWeight.ExtraBold).copy(fontSize = 15.sp),
    titleSmall = base.titleSmall.m(FontWeight.Bold),
    bodyLarge = base.bodyLarge.m(FontWeight.SemiBold).copy(fontSize = 15.sp),
    bodyMedium = base.bodyMedium.m(FontWeight.Medium),
    bodySmall = base.bodySmall.m(FontWeight.Medium),
    labelLarge = base.labelLarge.m(FontWeight.Bold),
    labelMedium = base.labelMedium.m(FontWeight.SemiBold),
    labelSmall = base.labelSmall.m(FontWeight.SemiBold),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Montos: cifras de ancho fijo para que los pesos queden alineados. */
val EstiloCifras = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TemaEntreDos(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalColoresExtra provides if (oscuro) ExtraOscuro else ExtraClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, shapes = Formas, content = contenido)
    }
}
