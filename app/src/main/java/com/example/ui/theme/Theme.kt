package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

@Immutable
data class MeuPaisExtendedColors(
    val isDark: Boolean,
    val cardBorder: Color,
    val divider: Color,
    val elevatedSurface: Color,
    val verifiedBadge: Color,
    val onlineDot: Color,
    val storyRingStart: Color,
    val storyRingEnd: Color,
    val hashtagText: Color
)

val LocalMeuPaisColors = staticCompositionLocalOf {
    MeuPaisExtendedColors(
        isDark = true,
        cardBorder = NavyBorder,
        divider = NavyDivider,
        elevatedSurface = NavyElevated,
        verifiedBadge = VerifiedBlue,
        onlineDot = OnlineGreen,
        storyRingStart = OrangeVibrant,
        storyRingEnd = GoldStar,
        hashtagText = OrangeSoft
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = OrangeVibrant,
    onPrimary = PureWhite,
    primaryContainer = OrangeContainerDark,
    onPrimaryContainer = OrangeSoft,
    secondary = VerifiedBlue,
    onSecondary = PureWhite,
    tertiary = GoldStar,
    onTertiary = PureBlack,
    background = NavyDeep,
    onBackground = TextPrimaryDark,
    surface = NavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = NavyBorder,
    outlineVariant = NavyDivider,
    error = CrimsonAngola,
    onError = PureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = OrangeVibrant,
    onPrimary = PureWhite,
    primaryContainer = OrangeContainerLight,
    onPrimaryContainer = OrangeVibrant,
    secondary = NavyElevated,
    onSecondary = PureWhite,
    tertiary = GoldStar,
    onTertiary = PureBlack,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    outlineVariant = LightDivider,
    error = CrimsonAngola,
    onError = PureWhite
)

private val MeuPaisShapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun MeuPaisTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val extendedColors = if (useDark) {
        MeuPaisExtendedColors(
            isDark = true,
            cardBorder = NavyBorder,
            divider = NavyDivider,
            elevatedSurface = NavyElevated,
            verifiedBadge = VerifiedBlue,
            onlineDot = OnlineGreen,
            storyRingStart = OrangeVibrant,
            storyRingEnd = GoldStar,
            hashtagText = OrangeSoft
        )
    } else {
        MeuPaisExtendedColors(
            isDark = false,
            cardBorder = LightBorder,
            divider = LightDivider,
            elevatedSurface = LightElevated,
            verifiedBadge = VerifiedBlue,
            onlineDot = OnlineGreen,
            storyRingStart = OrangeVibrant,
            storyRingEnd = CrimsonAngola,
            hashtagText = OrangeVibrant
        )
    }

    CompositionLocalProvider(LocalMeuPaisColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeuPaisTypography,
            shapes = MeuPaisShapes,
            content = content
        )
    }
}
