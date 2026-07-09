package com.draft.editor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.draft.editor.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A468F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF575E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF757780),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
)

/** Pure black for OLED panels: background/surfaces go #000000. */
private val OledColors: ColorScheme = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF17181C),
    surfaceContainer = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF08090B),
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color(0xFF121318),
    surfaceContainerHighest = Color(0xFF17181C),
)

private val DraftTypography = Typography(
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun isAppInDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.OLED -> true
}

@Composable
fun DraftTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        themeMode == ThemeMode.OLED -> OledColors
        isAppInDarkTheme(themeMode) -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DraftTypography,
        content = content,
    )
}
