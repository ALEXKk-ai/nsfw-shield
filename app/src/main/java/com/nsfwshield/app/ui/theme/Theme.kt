package com.nsfwshield.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Brand Colors ───
private val ShieldBlue = Color(0xFF1A73E8)
private val ShieldDarkBlue = Color(0xFF0D47A1)
private val ShieldAccent = Color(0xFF00BFA5)
private val ShieldError = Color(0xFFEF5350)
private val ShieldWarning = Color(0xFFFFA726)
private val ShieldSuccess = Color(0xFF66BB6A)

// ─── Dark Theme ───
private val DarkColorScheme = darkColorScheme(
    primary = ShieldBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = ShieldAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    error = ShieldError,
    onError = Color.White,
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE7E9EA),
    surface = Color(0xFF16202A),
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF1E2D3D),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF37474F),
    outlineVariant = Color(0xFF263238),
)

// ─── Light Theme ───
private val LightColorScheme = lightColorScheme(
    primary = ShieldBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = ShieldDarkBlue,
    secondary = ShieldAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    error = ShieldError,
    onError = Color.White,
    background = Color(0xFFF8FAFB),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFF0F4F8),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFB0BEC5),
    outlineVariant = Color(0xFFE0E3E7),
)

@Composable
fun NSFWShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
