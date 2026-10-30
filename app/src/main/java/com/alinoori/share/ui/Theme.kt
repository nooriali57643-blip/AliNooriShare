package com.alinoori.share.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.alinoori.share.ThemeMode

val Blue = Color(0xFF2F6BFF)
val Purple = Color(0xFF7C4DFF)
val BrandBrush = Brush.linearGradient(listOf(Blue, Purple))

private val Light = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    secondary = Purple, onSecondary = Color.White,
    background = Color(0xFFF5F6FF), onBackground = Color(0xFF15172B),
    surface = Color.White, onSurface = Color(0xFF15172B),
    surfaceVariant = Color(0xFFE9EBFF), onSurfaceVariant = Color(0xFF4A4D6A),
    primaryContainer = Color(0xFFDDE5FF), onPrimaryContainer = Color(0xFF0B2A80),
    error = Color(0xFFD32F2F)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86A6FF), onPrimary = Color(0xFF0A1B52),
    secondary = Color(0xFFB7A2FF), onSecondary = Color(0xFF2A1670),
    background = Color(0xFF0E1020), onBackground = Color(0xFFE8E9F8),
    surface = Color(0xFF171A30), onSurface = Color(0xFFE8E9F8),
    surfaceVariant = Color(0xFF23274A), onSurfaceVariant = Color(0xFFB4B7D6),
    primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFDDE5FF),
    error = Color(0xFFFF8A80)
)

/** True when the UI language is Persian (also drives RTL). */
val LocalFa = compositionLocalOf { true }

@Composable
fun tr(en: String, fa: String): String = if (LocalFa.current) fa else en

@Composable
fun AppTheme(mode: ThemeMode, fa: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalFa provides fa,
        LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
