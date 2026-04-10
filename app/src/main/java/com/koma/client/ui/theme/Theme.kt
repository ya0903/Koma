package com.koma.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val KomaDarkColors = darkColorScheme(
    primary = KomaNavy500,
    onPrimary = KomaInk100,
    primaryContainer = KomaNavy700,
    onPrimaryContainer = KomaNavy100,
    secondary = KomaNavy200,
    background = KomaNavy900,
    onBackground = KomaInk100,
    surface = KomaNavy800,
    onSurface = KomaInk100,
)

private val KomaLightColors = lightColorScheme(
    primary = KomaNavy700,
    onPrimary = KomaInk100,
    primaryContainer = KomaNavy100,
    onPrimaryContainer = KomaNavy900,
    secondary = KomaNavy500,
    background = KomaInk100,
    onBackground = KomaInk900,
    surface = KomaInk100,
    onSurface = KomaInk900,
)

@Composable
fun KomaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> KomaDarkColors
        else -> KomaLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KomaTypography,
        content = content,
    )
}
