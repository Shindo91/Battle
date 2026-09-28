package com.shindo91.trainerbattle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shindo91.trainerbattle.core.model.ElementType

val Night = Color(0xFF1B1B2F)
val Panel = Color(0xFF26264A)
val Accent = Color(0xFFE94560)
val Gold = Color(0xFFFFC857)
val Gem = Color(0xFF5BC0EB)
val Energy = Color(0xFF9BE564)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Gold,
    background = Night,
    surface = Panel,
    surfaceVariant = Color(0xFF32325D),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun TrainerBattleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

fun ElementType.color(): Color = when (this) {
    ElementType.NORMAL -> Color(0xFFB0A8B9)
    ElementType.FIRE -> Color(0xFFFF7043)
    ElementType.WATER -> Color(0xFF42A5F5)
    ElementType.NATURE -> Color(0xFF66BB6A)
    ElementType.ELECTRIC -> Color(0xFFFFD54F)
    ElementType.EARTH -> Color(0xFFA1887F)
    ElementType.AIR -> Color(0xFF81D4FA)
}
