package com.shindo91.trainerbattle.core.economy

import com.shindo91.trainerbattle.core.battle.Arena
import com.shindo91.trainerbattle.core.battle.Campaign
import com.shindo91.trainerbattle.core.model.Creature
import com.shindo91.trainerbattle.core.model.LevelUpReport
import com.shindo91.trainerbattle.core.model.Rarity
import com.shindo91.trainerbattle.core.model.SpeciesCatalog
import kotlin.random.Random

object Energy {
    const val MAX = 10
    const val BATTLE_COST = 1
    const val REGEN_MILLIS = 12 * 60 * 1000L
    const val AD_REWARD = 3

    /** Applies passive regeneration up to [MAX] based on elapsed time. */
    fun regenerate(p: PlayerProfile, now: Long): PlayerProfile {
        if (p.energy >= MAX) return p.copy(energyUpdatedAt = now)
        if (p.energyUpdatedAt <= 0) return p.copy(energyUpdatedAt = now)
        val ticks = ((now - p.energyUpdatedAt) / REGEN_MILLIS).toInt()
        if (ticks <= 0) return p
        val energy = (p.energy + ticks).coerceAtMost(MAX)
        val updatedAt = if (energy >= MAX) now else p.energyUpdatedAt + ticks * REGEN_MILLIS
        return p.copy(energy = energy, energyUpdatedAt = updatedAt)
    }

    fun millisUntilNext(p: PlayerProfile, now: Long): Long =
        if (p.energy >= MAX) 0 else (REGEN_MILLIS - (now - p.energyUpdatedAt)).coerceAtLeast(0)
}

enum class Currency { COINS, GEMS }

data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val currency: Currency,
    val icon: String,
    /** Items that act on one creature need a target. */
    val needsTarget: Boolean = false,
)

object Shop {
    val items = listOf(
        ShopItem("level_candy", "Levelbonbon", "Eine Kreatur steigt sofort um 1 Level auf.", 300, Currency.COINS, "🍬", needsTarget = true),
        ShopItem("egg_common", "Kreatur-Ei", "Schlüpft zu einer zufälligen Kreatur (Lv. 5).", 500, Currency.COINS, "🥚"),
        ShopItem("egg_rare", "Seltenes Ei", "Seltene oder epische Kreatur (Lv. 10).", 60, Currency.GEMS, "🪺"),
        ShopItem("energy_refill", "Energie auffüllen", "Füllt deine Energie komplett auf.", 15, Currency.GEMS, "⚡"),
        ShopItem("coin_pack", "Münzbeutel", "Tausche 20 Edelsteine gegen 1000 Münzen.", 20, Currency.GEMS, "💰"),
    )

    fun byId(id: String): ShopItem = items.first { it.id == id }
}

sealed interface PurchaseResult {
    data class Success(
        val profile: PlayerProfile,
        val message: String,
        val newCreature: Creature? = null,
        val levelUp: LevelUpReport? = null,
    ) : PurchaseResult
    data class Failure(val reason: String) : PurchaseResult
}

data class BattleRewards(
    val won: Boolean,
    val coins: Int,
    val gems: Int,
    val ratingChange: Int,
    val levelUps: List<Pair<Creature, LevelUpReport>>,
)

/** Pure game rules operating on [PlayerProfile]. */
object GameRules {

    fun chooseStarter(p: PlayerProfile, speciesId: String, random: Random = Random.Default): PlayerProfile {
        require(!p.hasStarter) { "Starter already chosen" }
        require(SpeciesCatalog[speciesId].isStarter) { "Not a starter" }
        return p.copy(team = listOf(Creature.create(speciesId, 5, random)))
    }

    fun canStartBattle(p: PlayerProfile): Boolean = p.team.isNotEmpty() && p.energy >= Energy.BATTLE_COST

    fun spendBattleEnergy(p: PlayerProfile, now: Long): PlayerProfile {
        val regenerated = Energy.regenerate(p, now)
        require(regenerated.energy >= Energy.BATTLE_COST) { "Not enough energy" }
        // Start the regen timer when dropping below max.
        val updatedAt = if (regenerated.energy >= Energy.MAX) now else regenerated.energyUpdatedAt
        return regenerated.copy(energy = regenerated.energy - Energy.BATTLE_COST, energyUpdatedAt = updatedAt)
    }

    /**
     * Applies a finished battle. [trainerId] is a campaign id or "arena". A lost battle still
     * grants the XP earned and a small consolation so progress never feels wasted.
     */
    fun applyBattleResult(
        p: PlayerProfile,
        trainerId: String,
        coinReward: Int,
        won: Boolean,
        xpByCreature: Map<String, Long>,
    ): Pair<PlayerProfile, BattleRewards> {
        val levelUps = mutableListOf<Pair<Creature, LevelUpReport>>()
        fun level(c: Creature): Creature {
            val xp = xpByCreature[c.uid] ?: return c
            val (updated, report) = c.gainXp(xp)
            if (report.leveledUp) levelUps += updated to report
            return updated
        }
        val team = p.team.map(::level)

        val isArena = trainerId == "arena"
        val firstWin = won && !isArena && trainerId !in p.defeatedTrainers
        val coins = if (won) coinReward else coinReward / 5
        val gems = if (firstWin) Campaign.byId(trainerId)?.firstWinGems ?: 0 else 0
        val ratingChange = if (isArena) Arena.ratingChange(won) else 0

        val updated = p.copy(
            team = team,
            coins = p.coins + coins,
            gems = p.gems + gems,
            defeatedTrainers = if (won && !isArena) p.defeatedTrainers + trainerId else p.defeatedTrainers,
            arenaRating = (p.arenaRating + ratingChange).coerceAtLeast(0),
            battlesWon = p.battlesWon + if (won) 1 else 0,
            battlesLost = p.battlesLost + if (won) 0 else 1,
        )
        return updated to BattleRewards(won, coins, gems, ratingChange, levelUps)
    }

    /** Rewarded ad after a win: doubles the coin reward. */
    fun applyDoubleCoins(p: PlayerProfile, rewards: BattleRewards): PlayerProfile =
        p.copy(coins = p.coins + rewards.coins)

    fun applyEnergyAd(p: PlayerProfile, now: Long): PlayerProfile {
        val r = Energy.regenerate(p, now)
        return r.copy(energy = (r.energy + Energy.AD_REWARD).coerceAtMost(Energy.MAX))
    }

    fun canClaimDaily(p: PlayerProfile, epochDay: Long): Boolean = p.lastDailyRewardDay < epochDay

    /** Daily login reward; the streak (max 7) raises the reward, day 7 gives gems. */
    fun claimDaily(p: PlayerProfile, epochDay: Long): Pair<PlayerProfile, String> {
        require(canClaimDaily(p, epochDay)) { "Already claimed today" }
        val streak = if (p.lastDailyRewardDay == epochDay - 1) (p.dailyStreak % 7) + 1 else 1
        val coins = 50 * streak
        val gems = if (streak == 7) 15 else 0
        val msg = "Tag $streak: +$coins Münzen" + if (gems > 0) " und +$gems Edelsteine" else ""
        return p.copy(coins = p.coins + coins, gems = p.gems + gems, lastDailyRewardDay = epochDay, dailyStreak = streak) to msg
    }

    fun buy(p: PlayerProfile, itemId: String, targetUid: String? = null, now: Long, random: Random = Random.Default): PurchaseResult {
        val item = Shop.byId(itemId)
        val balance = if (item.currency == Currency.COINS) p.coins else p.gems
        if (balance < item.price) {
            return PurchaseResult.Failure(if (item.currency == Currency.COINS) "Nicht genug Münzen" else "Nicht genug Edelsteine")
        }
        val paid = if (item.currency == Currency.COINS) p.copy(coins = p.coins - item.price) else p.copy(gems = p.gems - item.price)

        return when (item.id) {
            "level_candy" -> {
                val target = p.allCreatures.firstOrNull { it.uid == targetUid }
                    ?: return PurchaseResult.Failure("Wähle eine Kreatur aus")
                if (target.xpForNextLevel == 0L) return PurchaseResult.Failure("${target.displayName} hat das Maximallevel")
                val (updated, report) = target.gainXp(target.xpForNextLevel - target.xpIntoLevel)
                PurchaseResult.Success(replaceCreature(paid, updated), "${updated.displayName} ist jetzt Level ${updated.level}!", levelUp = report)
            }
            "egg_common" -> hatch(paid, SpeciesCatalog.hatchable(Rarity.COMMON), 5, random)
            "egg_rare" -> {
                val pool = if (random.nextInt(100) < 15) SpeciesCatalog.hatchable(Rarity.EPIC) else SpeciesCatalog.hatchable(Rarity.RARE)
                hatch(paid, pool, 10, random)
            }
            "energy_refill" -> {
                if (Energy.regenerate(p, now).energy >= Energy.MAX) return PurchaseResult.Failure("Energie ist bereits voll")
                PurchaseResult.Success(paid.copy(energy = Energy.MAX, energyUpdatedAt = now), "Energie aufgefüllt!")
            }
            "coin_pack" -> PurchaseResult.Success(paid.copy(coins = paid.coins + 1000), "+1000 Münzen")
            else -> PurchaseResult.Failure("Unbekannter Artikel")
        }
    }

    private fun hatch(p: PlayerProfile, pool: List<com.shindo91.trainerbattle.core.model.Species>, level: Int, random: Random): PurchaseResult {
        val species = pool.random(random)
        val creature = Creature.create(species.id, level, random)
        val updated = if (p.team.size < PlayerProfile.MAX_TEAM_SIZE) p.copy(team = p.team + creature)
        else p.copy(storage = p.storage + creature)
        return PurchaseResult.Success(updated, "Ein ${species.name} ist geschlüpft!", newCreature = creature)
    }

    private fun replaceCreature(p: PlayerProfile, c: Creature): PlayerProfile = p.copy(
        team = p.team.map { if (it.uid == c.uid) c else it },
        storage = p.storage.map { if (it.uid == c.uid) c else it },
    )

    /** Moves a creature between team and storage, or reorders so it leads the team. */
    fun toggleTeamMember(p: PlayerProfile, uid: String): PlayerProfile {
        val inTeam = p.team.firstOrNull { it.uid == uid }
        if (inTeam != null) {
            if (p.team.size <= 1) return p
            return p.copy(team = p.team - inTeam, storage = listOf(inTeam) + p.storage)
        }
        val stored = p.storage.firstOrNull { it.uid == uid } ?: return p
        if (p.team.size >= PlayerProfile.MAX_TEAM_SIZE) return p
        return p.copy(team = p.team + stored, storage = p.storage - stored)
    }

    fun makeLeader(p: PlayerProfile, uid: String): PlayerProfile {
        val c = p.team.firstOrNull { it.uid == uid } ?: return p
        return p.copy(team = listOf(c) + (p.team - c))
    }

    // ----- In-app purchases (granted after Google Play confirms the purchase) -----

    /** Gem amounts for consumable product ids configured in the Play Console. */
    val gemProducts: Map<String, Int> = mapOf(
        "gems_small" to 100,
        "gems_medium" to 550,
        "gems_large" to 1200,
    )
    const val REMOVE_ADS_PRODUCT = "remove_ads"

    /** Idempotent: a purchase token is credited at most once. */
    fun grantPurchase(p: PlayerProfile, productId: String, purchaseToken: String): PlayerProfile {
        if (purchaseToken in p.grantedPurchaseTokens) return p
        val tokens = p.grantedPurchaseTokens + purchaseToken
        return when (productId) {
            REMOVE_ADS_PRODUCT -> p.copy(adFree = true, grantedPurchaseTokens = tokens)
            in gemProducts -> p.copy(gems = p.gems + gemProducts.getValue(productId), grantedPurchaseTokens = tokens)
            else -> p
        }
    }
}
