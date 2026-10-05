package com.entredos.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores del boceto: verde de cuentas claras sobre un fondo gris verdoso, con acento naranja para alertas. */
private val Claro = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EFE8),
    onPrimaryContainer = Color(0xFF124A33),
    secondary = Color(0xFF4E5A53),
    secondaryContainer = Color(0xFFE3EFE8),
    onSecondaryContainer = Color(0xFF124A33),
    error = Color(0xFFA4410E),
    background = Color(0xFFF3F5F2),
    onBackground = Color(0xFF142019),
    surface = Color(0xFFF3F5F2),
    onSurface = Color(0xFF142019),
    onSurfaceVariant = Color(0xFF4E5A53),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAF7),
    surfaceContainer = Color(0xFFEDF1EE),
    outline = Color(0xFF8A948E),
    outlineVariant = Color(0xFFDDE3DF),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF8FD9B6),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF124A33),
    onPrimaryContainer = Color(0xFFCDEEDD),
    secondary = Color(0xFFB4C0B9),
    secondaryContainer = Color(0xFF263530),
    onSecondaryContainer = Color(0xFFCDEEDD),
    error = Color(0xFFFFB59A),
    background = Color(0xFF101613),
    onBackground = Color(0xFFE0E6E2),
    surface = Color(0xFF101613),
    onSurface = Color(0xFFE0E6E2),
    onSurfaceVariant = Color(0xFFB4C0B9),
    surfaceContainerLowest = Color(0xFF18201C),
    surfaceContainerLow = Color(0xFF151C18),
    surfaceContainer = Color(0xFF1C2520),
    outline = Color(0xFF7E8983),
    outlineVariant = Color(0xFF34413A),
)

@Composable
fun TemaEntreDos(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = contenido)
}
