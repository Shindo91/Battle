package com.shindo91.trainerbattle.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shindo91.trainerbattle.core.economy.Energy as EnergyRules
import com.shindo91.trainerbattle.core.economy.PlayerProfile
import com.shindo91.trainerbattle.core.model.ElementType
import com.shindo91.trainerbattle.ui.theme.Energy
import com.shindo91.trainerbattle.ui.theme.Gem
import com.shindo91.trainerbattle.ui.theme.Gold
import com.shindo91.trainerbattle.ui.theme.color

/** Top bar with currencies and a back button. */
@Composable
fun CurrencyBar(profile: PlayerProfile, title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Pill("⚡ ${profile.energy}/${EnergyRules.MAX}", Energy)
        Pill("🪙 ${profile.coins}", Gold)
        Pill("💎 ${profile.gems}", Gem)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TypeBadge(type: ElementType) = Pill(type.displayName, type.color())

@Composable
fun HpBar(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(targetValue = hp.toFloat() / maxHp.coerceAtLeast(1), label = "hp")
    val color = when {
        fraction > 0.5f -> Color(0xFF66BB6A)
        fraction > 0.2f -> Color(0xFFFFCA28)
        else -> Color(0xFFEF5350)
    }
    Column(modifier) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.15f),
        )
        Text("$hp / $maxHp KP", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun XpBar(into: Long, needed: Long, modifier: Modifier = Modifier) {
    val fraction = if (needed <= 0) 1f else (into.toFloat() / needed).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = Gem,
        trackColor = Color.White.copy(alpha = 0.15f),
    )
}
