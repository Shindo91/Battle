package com.shindo91.trainerbattle.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shindo91.trainerbattle.core.battle.Arena
import com.shindo91.trainerbattle.core.battle.Campaign
import com.shindo91.trainerbattle.core.economy.Energy
import com.shindo91.trainerbattle.core.economy.PlayerProfile
import com.shindo91.trainerbattle.core.model.SpeciesCatalog
import com.shindo91.trainerbattle.ui.theme.Accent
import com.shindo91.trainerbattle.ui.theme.Gold
import com.shindo91.trainerbattle.ui.theme.color

@Composable
fun StarterScreen(onChoose: (speciesId: String, name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Willkommen, Trainer!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Wähle deinen Namen und deinen ersten Begleiter.", textAlign = TextAlign.Center)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(16) },
            label = { Text("Trainername") },
            singleLine = true,
        )
        SpeciesCatalog.starters.forEach { s ->
            val isSelected = selected == s.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selected = s.id },
                border = if (isSelected) BorderStroke(3.dp, s.type.color()) else null,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.emoji, fontSize = 44.sp)
                    Spacer(Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        TypeBadge(s.type)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { selected?.let { onChoose(it, name) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Abenteuer starten!", fontSize = 18.sp) }
    }
}

@Composable
fun HomeScreen(
    profile: PlayerProfile,
    canClaimDaily: Boolean,
    rewardedReady: Boolean,
    showPrivacyOptions: Boolean,
    onClaimDaily: () -> Unit,
    onCampaign: () -> Unit,
    onArena: () -> Unit,
    onTeam: () -> Unit,
    onShop: () -> Unit,
    onEnergyAd: () -> Unit,
    onPrivacyOptions: () -> Unit,
    banner: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CurrencyBar(profile, "Hallo, ${profile.trainerName}")
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    profile.team.forEach { c ->
                        Card(Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(c.species.emoji, fontSize = 36.sp)
                                Text(c.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Lv. ${c.level}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (canClaimDaily) {
                item {
                    MenuCard("🎁", "Tagesbelohnung", "Hol dir deine tägliche Belohnung ab!", Gold, onClaimDaily)
                }
            }
            item {
                val done = profile.defeatedTrainers.size
                MenuCard("🗺️", "Kampagne", "Besiege alle ${Campaign.trainers.size} Trainer ($done/${Campaign.trainers.size})", Accent, onCampaign)
            }
            item {
                MenuCard("🏟️", "Arena", "Kämpfe gegen Rivalen · ${Arena.ratingTier(profile.arenaRating)} (${profile.arenaRating})", Color(0xFF7E57C2), onArena)
            }
            item { MenuCard("🧬", "Team", "Kreaturen verwalten und aufstellen", Color(0xFF26A69A), onTeam) }
            item { MenuCard("🛒", "Shop", "Eier, Bonbons, Edelsteine & mehr", Gold, onShop) }
            if (profile.energy < Energy.MAX && rewardedReady) {
                item {
                    MenuCard("📺", "Energie gratis", "Video ansehen: +${Energy.AD_REWARD} Energie", Color(0xFF9BE564), onEnergyAd)
                }
            }
            item {
                Text(
                    "Siege: ${profile.battlesWon} · Niederlagen: ${profile.battlesLost}",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.6f),
                )
                if (showPrivacyOptions) {
                    OutlinedButton(onClick = onPrivacyOptions, modifier = Modifier.fillMaxWidth()) {
                        Text("Datenschutz-Einstellungen")
                    }
                }
            }
        }
        if (!profile.adFree) banner()
    }
}

@Composable
fun MenuCard(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.22f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.padding(8.dp))
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun CampaignScreen(profile: PlayerProfile, onBack: () -> Unit, onFight: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CurrencyBar(profile, "Kampagne", onBack)
        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Campaign.trainers, key = { it.id }) { t ->
                val defeated = t.id in profile.defeatedTrainers
                val unlocked = Campaign.isUnlocked(t.id, profile.defeatedTrainers)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (unlocked) t.avatar else "🔒", fontSize = 36.sp)
                        Spacer(Modifier.padding(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${t.title} · bis Lv. ${t.maxLevel}", fontSize = 13.sp)
                            Text(
                                if (unlocked) t.team.joinToString(" ") { SpeciesCatalog[it.first].emoji } else "???",
                                fontSize = 18.sp,
                            )
                            val gems = if (!defeated && t.firstWinGems > 0) " · 💎 ${t.firstWinGems}" else ""
                            Text("🪙 ${t.coinReward}$gems", fontSize = 13.sp, color = Gold)
                        }
                        if (unlocked) {
                            Button(onClick = { onFight(t.id) }) { Text(if (defeated) "Revanche" else "Kämpfen") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
