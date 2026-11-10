package ru.alkometr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Graphite,
    primaryContainer = AmberDark,
    onPrimaryContainer = Color.White,
    secondary = LevelMild,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = Color(0xFFEDEDED),
    surface = GraphiteSoft,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF33373D),
    onSurfaceVariant = Color(0xFFC3C7CC),
    outline = Color(0xFF6C7278),
    error = LevelDangerous,
    onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3B0),
    onPrimaryContainer = Ink,
    secondary = LevelSober,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = SandCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE4D4),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFF8D8577),
    error = LevelDangerous,
    onError = Color.White,
)

/**
 * Тема приложения: светло-песочная и графитовая.
 * Динамические цвета Material You намеренно отключены — цветовые зоны степеней
 * опьянения должны выглядеть одинаково на всех устройствах.
 */
@Composable
fun AlkometrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AlkometrTypography,
        content = content,
    )
}
