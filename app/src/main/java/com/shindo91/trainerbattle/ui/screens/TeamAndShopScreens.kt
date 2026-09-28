package com.shindo91.trainerbattle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shindo91.trainerbattle.core.economy.Currency
import com.shindo91.trainerbattle.core.economy.GameRules
import com.shindo91.trainerbattle.core.economy.PlayerProfile
import com.shindo91.trainerbattle.core.economy.Shop
import com.shindo91.trainerbattle.core.model.Creature
import com.shindo91.trainerbattle.ui.theme.Gem
import com.shindo91.trainerbattle.ui.theme.Gold

@Composable
fun TeamScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onMakeLeader: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CurrencyBar(profile, "Team", onBack)
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle("Kampfteam (${profile.team.size}/${PlayerProfile.MAX_TEAM_SIZE})") }
            items(profile.team, key = { it.uid }) { c ->
                CreatureCard(c, leader = profile.team.first().uid == c.uid) {
                    if (profile.team.first().uid != c.uid) {
                        TextButton(onClick = { onMakeLeader(c.uid) }) { Text("Anführer") }
                    }
                    TextButton(onClick = { onToggle(c.uid) }) { Text("Ins Lager") }
                }
            }
            item { SectionTitle("Lager (${profile.storage.size})") }
            if (profile.storage.isEmpty()) {
                item { Text("Noch keine weiteren Kreaturen. Brüte im Shop Eier aus!", color = Color.White.copy(alpha = 0.6f)) }
            }
            items(profile.storage, key = { it.uid }) { c ->
                CreatureCard(c, leader = false) {
                    TextButton(onClick = { onToggle(c.uid) }) { Text("Ins Team") }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun CreatureCard(c: Creature, leader: Boolean, actions: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.species.emoji, fontSize = 40.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.displayName + if (leader) " ⭐" else "", fontWeight = FontWeight.Bold)
                    TypeBadge(c.species.type)
                }
                val s = c.stats
                Text("Lv. ${c.level} · KP ${s.hp} · Ang ${s.attack} · Vert ${s.defense} · Init ${s.speed}", fontSize = 12.sp)
                XpBar(c.xpIntoLevel, c.xpForNextLevel)
                Text(c.moves.joinToString(" · ") { it.name }, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                c.species.evolveLevel?.let { Text("Entwickelt sich ab Lv. $it", fontSize = 11.sp, color = Gem) }
            }
            Column { actions() }
        }
    }
}

/** A real-money product as shown in the shop. [price] is the localized price from Google Play. */
data class IapOffer(val productId: String, val title: String, val icon: String, val price: String?)

@Composable
fun ShopScreen(
    profile: PlayerProfile,
    iapOffers: List<IapOffer>,
    onBack: () -> Unit,
    onBuyItem: (itemId: String, targetUid: String?) -> Unit,
    onBuyIap: (productId: String) -> Unit,
) {
    var pickTargetFor by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        CurrencyBar(profile, "Shop", onBack)
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle("Gegenstände") }
            items(Shop.items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.icon, fontSize = 34.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                        val isCoins = item.currency == Currency.COINS
                        Button(onClick = {
                            if (item.needsTarget) pickTargetFor = item.id else onBuyItem(item.id, null)
                        }) {
                            Text((if (isCoins) "🪙 " else "💎 ") + item.price, color = if (isCoins) Gold else Gem)
                        }
                    }
                }
            }

            item { SectionTitle("Edelsteine & Premium") }
            items(iapOffers, key = { it.productId }) { offer ->
                val owned = offer.productId == GameRules.REMOVE_ADS_PRODUCT && profile.adFree
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Gem.copy(alpha = 0.15f)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(offer.icon, fontSize = 34.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(offer.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { onBuyIap(offer.productId) }, enabled = !owned) {
                            Text(if (owned) "Gekauft" else offer.price ?: "…")
                        }
                    }
                }
            }
            item {
                Text(
                    "Käufe werden über Google Play abgewickelt.",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }

    pickTargetFor?.let { itemId ->
        AlertDialog(
            onDismissRequest = { pickTargetFor = null },
            title = { Text("Für welche Kreatur?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.allCreatures.forEach { c ->
                        OutlinedButton(
                            onClick = { pickTargetFor = null; onBuyItem(itemId, c.uid) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${c.species.emoji} ${c.displayName} (Lv. ${c.level})") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickTargetFor = null }) { Text("Abbrechen") } },
        )
    }
}
