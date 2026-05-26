package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = DarkSlate,
    surface = SoftBG,
    onSurface = DarkSlate,
    surfaceVariant = BorderGray,
    onSurfaceVariant = LightSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force clean bright fintech style
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
