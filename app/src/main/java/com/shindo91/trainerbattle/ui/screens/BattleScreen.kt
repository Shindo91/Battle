package com.shindo91.trainerbattle.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shindo91.trainerbattle.core.battle.BattleAction
import com.shindo91.trainerbattle.core.battle.BattleEvent
import com.shindo91.trainerbattle.core.battle.BattlePhase
import com.shindo91.trainerbattle.core.model.Moves
import com.shindo91.trainerbattle.core.model.SpeciesCatalog
import com.shindo91.trainerbattle.ui.BattleUi
import com.shindo91.trainerbattle.ui.CombatantUi
import com.shindo91.trainerbattle.ui.theme.Gem
import com.shindo91.trainerbattle.ui.theme.Gold
import com.shindo91.trainerbattle.ui.theme.color

@Composable
fun BattleScreen(
    ui: BattleUi,
    rewardedReady: Boolean,
    onAction: (BattleAction) -> Unit,
    onDoubleCoins: () -> Unit,
    onLeave: () -> Unit,
) {
    var showSwitch by remember { mutableStateOf(false) }
    var confirmForfeit by remember { mutableStateOf(false) }
    val mustSwitch = ui.phase == BattlePhase.PLAYER_MUST_SWITCH && !ui.animating

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${ui.trainer.avatar} ${ui.trainer.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (ui.phase != BattlePhase.FINISHED) {
                TextButton(onClick = { confirmForfeit = true }) { Text("Aufgeben") }
            }
        }

        // Enemy (top right), player (bottom left) - classic layout.
        CombatantPanel(ui.enemy, Modifier.fillMaxWidth(0.75f).align(Alignment.End))
        Spacer(Modifier.height(8.dp))
        CombatantPanel(ui.player, Modifier.fillMaxWidth(0.75f))
        Spacer(Modifier.height(8.dp))

        BattleLog(ui.log, Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))

        val controlsEnabled = !ui.animating && ui.phase == BattlePhase.CHOOSE_ACTION
        ui.moves.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { move ->
                    val index = ui.moves.indexOf(move)
                    Button(
                        onClick = { onAction(BattleAction.UseMove(index)) },
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = move.type.color().copy(alpha = 0.85f)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(move.name, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text(
                                if (move.isDamaging) "${move.type.displayName} · ${move.power}" else "Status",
                                fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        OutlinedButton(
            onClick = { showSwitch = true },
            enabled = controlsEnabled && ui.playerTeam.count { !it.fainted } > 1,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kreatur wechseln") }
    }

    if (showSwitch || mustSwitch) {
        SwitchDialog(
            ui = ui,
            forced = mustSwitch,
            onPick = { showSwitch = false; onAction(BattleAction.Switch(it)) },
            onDismiss = { showSwitch = false },
        )
    }

    if (confirmForfeit) {
        AlertDialog(
            onDismissRequest = { confirmForfeit = false },
            title = { Text("Aufgeben?") },
            text = { Text("Du verlierst diesen Kampf, behältst aber die bisher verdienten EP.") },
            confirmButton = { TextButton(onClick = { confirmForfeit = false; onAction(BattleAction.Forfeit) }) { Text("Aufgeben") } },
            dismissButton = { TextButton(onClick = { confirmForfeit = false }) { Text("Weiterkämpfen") } },
        )
    }

    ui.rewards?.let { rewards ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (rewards.won) "🏆 Sieg!" else "Niederlage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val coins = if (ui.coinsDoubled) rewards.coins * 2 else rewards.coins
                    Text("🪙 +$coins Münzen", color = Gold)
                    if (rewards.gems > 0) Text("💎 +${rewards.gems} Edelsteine", color = Gem)
                    if (rewards.ratingChange != 0) {
                        val sign = if (rewards.ratingChange > 0) "+" else ""
                        Text("Arena-Wertung: $sign${rewards.ratingChange}")
                    }
                    rewards.levelUps.forEach { (creature, report) ->
                        Text("${creature.species.emoji} ${creature.displayName} erreicht Level ${report.newLevel}!", fontWeight = FontWeight.Bold)
                        report.evolvedFrom?.let {
                            Text("✨ ${SpeciesCatalog[it].name} hat sich zu ${creature.species.name} entwickelt!")
                        }
                        report.learnedMoves.forEach { Text("  Neue Attacke: ${Moves[it].name}") }
                    }
                }
            },
            confirmButton = { Button(onClick = onLeave) { Text("Weiter") } },
            dismissButton = {
                if (rewards.won && !ui.coinsDoubled && rewardedReady) {
                    TextButton(onClick = onDoubleCoins) { Text("📺 Münzen verdoppeln") }
                }
            },
        )
    }
}

@Composable
private fun CombatantPanel(c: CombatantUi, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (c.fainted) 0.3f else 1f, label = "faint")
    Card(modifier.alpha(alpha)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 48.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Lv. ${c.level}", fontSize = 13.sp)
                }
                Text(c.type, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                HpBar(c.hp, c.maxHp)
            }
        }
    }
}

@Composable
private fun BattleLog(log: List<BattleEvent>, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            log.forEachIndexed { i, e ->
                val color = when (e.kind) {
                    BattleEvent.Kind.SUPER_EFFECTIVE -> Color(0xFFFFB74D)
                    BattleEvent.Kind.NOT_EFFECTIVE, BattleEvent.Kind.MISS -> Color(0xFFB0BEC5)
                    BattleEvent.Kind.FAINT -> Color(0xFFEF5350)
                    BattleEvent.Kind.END -> Gold
                    BattleEvent.Kind.HEAL -> Color(0xFF81C784)
                    else -> Color.White
                }
                // Older lines fade out.
                val fade = 0.4f + 0.6f * (i + 1) / log.size
                Text(e.text, color = color.copy(alpha = fade), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SwitchDialog(ui: BattleUi, forced: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!forced) onDismiss() },
        title = { Text(if (forced) "Wähle die nächste Kreatur" else "Kreatur wechseln") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.playerTeam.forEachIndexed { index, c ->
                    val selectable = !c.fainted && index != ui.activeIndex
                    OutlinedButton(onClick = { onPick(index) }, enabled = selectable, modifier = Modifier.fillMaxWidth()) {
                        Text("${c.emoji} ${c.name} Lv. ${c.level} – ${c.hp}/${c.maxHp} KP")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { if (!forced) TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
