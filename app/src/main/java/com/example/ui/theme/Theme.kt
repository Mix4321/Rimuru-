package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorErrorLight = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
private val ColorErrorContainerLight = androidx.compose.ui.graphics.Color(0xFFFFDAD6)

private val GreenPulseLightColorScheme = lightColorScheme(
    primary = DeepForestGreen,
    onPrimary = CleanWhite,
    primaryContainer = MintPale,
    onPrimaryContainer = DeepForestGreen,
    secondary = EmeraldPulse,
    onSecondary = ObsidianBlack,
    secondaryContainer = MintSoft,
    onSecondaryContainer = DeepForestGreen,
    tertiary = NeonLime,
    onTertiary = ObsidianBlack,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = ObsidianBlack,
    background = LightCloudBg,
    onBackground = InkDark,
    surface = CleanWhite,
    onSurface = InkDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = CleanWhite,
    surfaceContainerLow = LightCloudBg,
    surfaceContainer = LightElevated,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = OutlineSoft,
    error = ColorErrorLight,
    onError = CleanWhite,
    errorContainer = ColorErrorContainerLight,
)

private val GreenPulseDarkColorScheme = darkColorScheme(
    primary = EmeraldPulse,
    onPrimary = ObsidianBlack,
    primaryContainer = DeepForestGreen,
    onPrimaryContainer = NeonLime,
    secondary = NeonLime,
    onSecondary = ObsidianBlack,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = CleanWhite,
    tertiary = NeonLime,
    onTertiary = ObsidianBlack,
    tertiaryContainer = DeepForestGreen,
    onTertiaryContainer = NeonLime,
    background = ObsidianBlack,
    onBackground = CleanWhite,
    surface = ObsidianSurface,
    onSurface = CleanWhite,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianElevated,
    surfaceContainerHigh = DeepForestGreen,
    outline = ForestGreenMedium,
    outlineVariant = GlassBorderLight,
    error = CoralError,
    onError = CleanWhite,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CleanWhite
)

val MoveShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MoveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GreenPulseDarkColorScheme else GreenPulseLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MoveTypography,
        shapes = MoveShapes,
        content = content
    )
}
