package com.skylinestudio.security.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = OnForestGreen,
    primaryContainer = ForestGreenContainer,
    onPrimaryContainer = OnForestGreenContainer,
    secondary = Sage,
    onSecondary = OnSage,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = WarmAmber,
    onTertiary = OnWarmAmber,
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = OnWarmAmber,
    error = ErrorRed,
    errorContainer = ErrorContainerLight,
    background = SoftSlate,
    onBackground = OnBackgroundLight,
    surface = SurfaceWhite,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenDarkTheme,
    onPrimary = ForestGreenDark,
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = ForestGreenContainer,
    secondary = SageDarkTheme,
    onSecondary = SageDark,
    secondaryContainer = SageDark,
    onSecondaryContainer = SageContainer,
    tertiary = WarmAmberDarkTheme,
    onTertiary = OnWarmAmber,
    tertiaryContainer = WarmAmberDark,
    onTertiaryContainer = WarmAmberContainer,
    error = ErrorRed,
    errorContainer = ErrorContainerLight,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

// Soft, rounded shape scale — the 'Steady Guardian' feel
val TortoiseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TortoiseSecurityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TortoiseShapes,
        content = content
    )
}
