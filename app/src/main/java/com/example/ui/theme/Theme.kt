package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val PrismCyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF07313D),
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3D0C26),
    onSecondaryContainer = NeonMagenta,
    tertiary = VoltLime,
    onTertiary = ObsidianBg,
    tertiaryContainer = Color(0xFF23330A),
    onTertiaryContainer = VoltLime,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder,
    outlineVariant = CyberSurfaceHigh,
    error = NeonCrimson,
    onError = Color.White
)

private val PrismShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PrismCallTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = PrismCyberColorScheme,
        typography = Typography,
        shapes = PrismShapes,
        content = content
    )
}
