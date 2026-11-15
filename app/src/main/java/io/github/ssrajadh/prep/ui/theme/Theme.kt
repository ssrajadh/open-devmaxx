package io.github.ssrajadh.prep.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9E7DC),
    onPrimaryContainer = Color(0xFF0B2019),
    secondary = Color(0xFF4D6359),
    tertiary = Color(0xFF7A5A2E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CC5B8),
    onPrimary = Color(0xFF0B2019),
    primaryContainer = Color(0xFF214A3E),
    onPrimaryContainer = Color(0xFFC9E7DC),
    secondary = Color(0xFFB3CCC0),
    tertiary = Color(0xFFE8C08A),
)

/** Uses Material You wallpaper colors on Android 12+, otherwise a calm green palette. */
@Composable
fun PrepTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
