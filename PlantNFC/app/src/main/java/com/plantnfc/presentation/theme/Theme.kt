package com.plantnfc.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green40 = Color(0xFF2F7D32)
private val Green80 = Color(0xFFA5D6A7)
private val Green90 = Color(0xFFC8E6C9)
private val Green10 = Color(0xFF002201)

private val LightColors = lightColorScheme(
    primary            = Green40,
    onPrimary          = Color.White,
    primaryContainer   = Green90,
    onPrimaryContainer = Green10,
    background         = Color(0xFFF5F8F3),
    surface            = Color.White,
)

private val DarkColors = darkColorScheme(
    primary            = Green80,
    onPrimary          = Green10,
    primaryContainer   = Color(0xFF003A01),
    onPrimaryContainer = Green90,
    background         = Color(0xFF0D1A0D),
    surface            = Color(0xFF121E12),
)

@Composable
fun PlantNfcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
