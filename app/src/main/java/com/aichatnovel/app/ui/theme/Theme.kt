package com.aichatnovel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 深色方案：沉浸式演出场景的主要形态。
 */
private val TheaterDarkColorScheme = darkColorScheme(
    primary = AmberSand,
    onPrimary = Color(0xFF3A2A08),
    primaryContainer = Color(0xFF4A3A16),
    onPrimaryContainer = Color(0xFFF6DFAE),
    secondary = WarmPaperMuted,
    onSecondary = Color(0xFF322A1E),
    secondaryContainer = Color(0xFF453C2E),
    onSecondaryContainer = Color(0xFFE8DCC6),
    tertiary = MossGreen,
    onTertiary = Color(0xFF14290F),
    tertiaryContainer = MossGreenDim,
    onTertiaryContainer = Color(0xFFD3E8CE),
    background = WarmInk,
    onBackground = WarmPaper,
    surface = WarmInk,
    onSurface = WarmPaper,
    surfaceVariant = WarmInkRaised,
    onSurfaceVariant = WarmPaperMuted,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * 浅色方案：系统偏好为浅色时的对应形态，保持同一套暖色语义。
 */
private val TheaterLightColorScheme = lightColorScheme(
    primary = AmberSandDim,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF271900),
    secondary = LightInkMuted,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightPaperRaised,
    onSecondaryContainer = Color(0xFF201A10),
    tertiary = Color(0xFF4A6350),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9E6CC),
    onTertiaryContainer = Color(0xFF08200C),
    background = LightPaper,
    onBackground = LightInk,
    surface = LightPaper,
    onSurface = LightInk,
    surfaceVariant = LightPaperRaised,
    onSurfaceVariant = LightInkMuted,
    error = Color(0xFFFFBAA1),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * 应用主题。
 *
 * 默认深色：这是小说剧情演出的沉浸形态，不跟随系统动态取色，
 * 以保证页面层级与对比度在任何设备上一致。
 */
@Composable
fun AIChatNovelTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TheaterDarkColorScheme else TheaterLightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
