package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.security.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Navy950,
    primaryContainer = Navy800,
    onPrimaryContainer = CyanAccent,
    secondary = BlueDeep,
    onSecondary = Color.White,
    secondaryContainer = Navy850,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = EmeraldSecurity,
    onTertiary = Navy950,
    background = Navy950,
    onBackground = DarkTextPrimary,
    surface = Navy900,
    onSurface = DarkTextPrimary,
    surfaceVariant = Navy850,
    onSurfaceVariant = DarkTextSecondary,
    outline = Navy600,
    outlineVariant = Navy700,
    error = RoseSecurity,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = LightText,
    tertiary = EmeraldSecurity,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = RoseSecurity,
    onError = Color.White
)

@Composable
fun DocPassTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
