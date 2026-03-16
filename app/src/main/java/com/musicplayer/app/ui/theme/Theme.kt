package com.musicplayer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PowerampColorScheme = darkColorScheme(
    primary = PowerampPrimary,
    onPrimary = PowerampOnPrimary,
    secondary = PowerampSecondary,
    onSecondary = PowerampOnPrimary,
    tertiary = PowerampTertiary,
    onTertiary = PowerampOnPrimary,
    background = PowerampBackground,
    onBackground = PowerampOnSurface,
    surface = PowerampSurface,
    onSurface = PowerampOnSurface,
    surfaceVariant = PowerampSurfaceVariant,
    onSurfaceVariant = PowerampOnSurfaceVariant,
    error = PowerampError,
    onError = PowerampOnError,
    outline = PowerampOutline,
    surfaceContainer = PowerampSurface,
    surfaceContainerHigh = PowerampSurface,
    surfaceContainerHighest = PowerampSurface,
    surfaceContainerLow = PowerampSurface,
    surfaceContainerLowest = PowerampSurface,
    inverseSurface = PowerampOnSurface,
    inverseOnSurface = PowerampBackground,
    inversePrimary = PowerampSecondary
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MusicPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PowerampColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
