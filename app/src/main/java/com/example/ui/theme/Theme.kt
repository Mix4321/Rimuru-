package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MoveLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = PureWhite,
    primaryContainer = MintPale,
    onPrimaryContainer = ForestGreenDeep,
    secondary = EmeraldVibrant,
    onSecondary = PureWhite,
    secondaryContainer = MintSoft,
    onSecondaryContainer = ForestGreenDeep,
    tertiary = AmberWarm,
    onTertiary = PureWhite,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = InkDark,
    background = CreamBackground,
    onBackground = InkDark,
    surface = PureWhite,
    onSurface = InkDark,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = CreamBackground,
    surfaceContainer = CreamElevated,
    surfaceContainerHigh = CreamSurfaceVariant,
    outline = OutlineSoft,
    error = CoralError,
    onError = PureWhite,
    errorContainer = CoralErrorContainer,
)

private val MoveDarkColorScheme = darkColorScheme(
    primary = MintBright,
    onPrimary = ForestGreenDeep,
    primaryContainer = ForestGreenMedium,
    onPrimaryContainer = MintSoft,
    secondary = MintSoft,
    onSecondary = ForestGreenDeep,
    secondaryContainer = ForestGreen,
    onSecondaryContainer = MintPale,
    tertiary = AmberWarm,
    onTertiary = PureWhite,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = ForestGreenMedium,
    error = CoralErrorContainer,
    onError = CoralError,
)

val MoveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MoveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MoveDarkColorScheme else MoveLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MoveTypography,
        shapes = MoveShapes,
        content = content
    )
}
