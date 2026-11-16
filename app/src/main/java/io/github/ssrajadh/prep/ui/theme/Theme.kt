package io.github.ssrajadh.prep.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme) {
        // Replace Material's expanding ripple with a quiet, instant press highlight.
        // Material components read LocalRippleConfiguration (null = no ripple) and fall
        // back to LocalIndication; plain Modifier.clickable uses LocalIndication directly.
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            LocalIndication provides QuietIndication(MaterialTheme.colorScheme.onSurface),
            content = content,
        )
    }
}

/** Draws a faint overlay while pressed. No animation, so nothing flashes. */
private class QuietIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, color)
    override fun equals(other: Any?) = other is QuietIndication && other.color == color
    override fun hashCode() = color.hashCode()

    private class Node(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
        private var pressed = false

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    pressed = when (i) {
                        is PressInteraction.Press -> true
                        is PressInteraction.Release, is PressInteraction.Cancel -> false
                        else -> pressed
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (pressed) drawRect(color.copy(alpha = 0.08f))
        }
    }
}
