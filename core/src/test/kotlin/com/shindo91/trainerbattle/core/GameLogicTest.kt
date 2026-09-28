package com.shindo91.trainerbattle.core

import com.shindo91.trainerbattle.core.battle.Battle
import com.shindo91.trainerbattle.core.battle.BattleAction
import com.shindo91.trainerbattle.core.battle.BattlePhase
import com.shindo91.trainerbattle.core.battle.Campaign
import com.shindo91.trainerbattle.core.battle.Side
import com.shindo91.trainerbattle.core.economy.Energy
import com.shindo91.trainerbattle.core.economy.GameRules
import com.shindo91.trainerbattle.core.economy.PlayerProfile
import com.shindo91.trainerbattle.core.economy.PurchaseResult
import com.shindo91.trainerbattle.core.model.Creature
import com.shindo91.trainerbattle.core.model.ElementType
import com.shindo91.trainerbattle.core.model.Moves
import com.shindo91.trainerbattle.core.model.SpeciesCatalog
import com.shindo91.trainerbattle.core.progression.Experience
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameLogicTest {

    @Test
    fun `catalog is consistent`() {
        for (species in SpeciesCatalog.species) {
            species.learnset.values.flatten().forEach { Moves[it] }
            species.evolvesTo?.let { SpeciesCatalog[it]; assertNotNull(species.evolveLevel) }
            assertTrue(species.movesAtLevel(1).isNotEmpty(), "${species.id} has no level 1 moves")
        }
        Campaign.trainers.forEach { t -> t.team.forEach { SpeciesCatalog[it.first] } }
        assertEquals(3, SpeciesCatalog.starters.size)
    }

    @Test
    fun `type chart`() {
        assertEquals(2.0, ElementType.WATER.effectivenessAgainst(ElementType.FIRE))
        assertEquals(0.5, ElementType.FIRE.effectivenessAgainst(ElementType.WATER))
        assertEquals(0.0, ElementType.ELECTRIC.effectivenessAgainst(ElementType.EARTH))
        assertEquals(1.0, ElementType.NORMAL.effectivenessAgainst(ElementType.AIR))
    }

    @Test
    fun `xp curve round trips`() {
        for (level in 1..100) {
            assertEquals(level, Experience.levelForTotalXp(Experience.totalXpForLevel(level)))
        }
    }

    @Test
    fun `leveling learns moves and evolves`() {
        val glutling = Creature.create("glutling", 5, Random(1))
        val (evolved, report) = glutling.gainXp(Experience.totalXpForLevel(16) - glutling.xp)
        assertEquals(16, evolved.level)
        assertEquals("flammdrache", evolved.speciesId)
        assertEquals("glutling", report.evolvedFrom)
        assertTrue("flame_wheel" in evolved.moveIds)
        assertTrue(evolved.stats.hp > glutling.stats.hp)
        assertTrue(evolved.moveIds.size <= 4)
    }

    @Test
    fun `stronger team wins against first trainer`() {
        repeat(20) { seed ->
            val random = Random(seed)
            val team = listOf(Creature.create("glutling", 15, random))
            val trainer = Campaign.trainers.first()
            val battle = Battle(team, trainer, trainer.buildTeam(random), random)
            var turns = 0
            while (battle.phase != BattlePhase.FINISHED && turns++ < 100) {
                // Always use the strongest damaging move.
                val moves = battle.player.active.creature.moves
                battle.submit(BattleAction.UseMove(moves.indices.maxBy { moves[it].power }))
            }
            assertEquals(Side.PLAYER, battle.winner, "seed $seed")
            assertTrue(battle.xpByCreature.getValue(team[0].uid) > 0)
        }
    }

    @Test
    fun `player must replace fainted creature`() {
        val random = Random(7)
        val weak = Creature.create("pummel", 2, random)
        val backup = Creature.create("tropfi", 30, random)
        val trainer = Campaign.trainers[6] // strong fire trainer
        val battle = Battle(listOf(weak, backup), trainer, trainer.buildTeam(random), random)
        var turns = 0
        while (battle.phase == BattlePhase.CHOOSE_ACTION && turns++ < 50) {
            battle.submit(BattleAction.UseMove(0))
        }
        assertEquals(BattlePhase.PLAYER_MUST_SWITCH, battle.phase)
        battle.submit(BattleAction.Switch(1))
        assertEquals(BattlePhase.CHOOSE_ACTION, battle.phase)
        assertEquals(backup.uid, battle.player.active.creature.uid)
    }

    @Test
    fun `forfeit ends battle`() {
        val trainer = Campaign.trainers.first()
        val battle = Battle(listOf(Creature.create("tropfi", 5)), trainer)
        battle.submit(BattleAction.Forfeit)
        assertEquals(Side.OPPONENT, battle.winner)
    }

    @Test
    fun `battle result grants rewards once for first win`() {
        val p = GameRules.chooseStarter(PlayerProfile(), "tropfi", Random(3))
        val uid = p.team.first().uid
        val (after, rewards) = GameRules.applyBattleResult(p, "t01", 60, won = true, xpByCreature = mapOf(uid to 500))
        assertEquals(p.coins + 60, after.coins)
        assertEquals(p.gems + 5, after.gems)
        assertTrue("t01" in after.defeatedTrainers)
        assertTrue(rewards.levelUps.isNotEmpty())
        val (again, rewards2) = GameRules.applyBattleResult(after, "t01", 60, won = true, xpByCreature = emptyMap())
        assertEquals(0, rewards2.gems)
        assertEquals(after.gems, again.gems)
        assertTrue(Campaign.isUnlocked("t02", after.defeatedTrainers))
        assertFalse(Campaign.isUnlocked("t03", after.defeatedTrainers))
    }

    @Test
    fun `energy regenerates and is spent`() {
        val start = PlayerProfile(energy = Energy.MAX, energyUpdatedAt = 1)
        var p = start
        repeat(Energy.MAX) { p = GameRules.spendBattleEnergy(p, now = 1000) }
        assertEquals(0, p.energy)
        assertFalse(GameRules.canStartBattle(p.copy(team = listOf(Creature.create("tropfi", 5)))))
        val later = Energy.regenerate(p, 1000 + Energy.REGEN_MILLIS * 3 + 5)
        assertEquals(3, later.energy)
        assertEquals(Energy.MAX, Energy.regenerate(p, 1000 + Energy.REGEN_MILLIS * 100).energy)
    }

    @Test
    fun `shop purchases`() {
        var p = GameRules.chooseStarter(PlayerProfile(coins = 1000, gems = 0), "sproessling", Random(1))
        val candy = GameRules.buy(p, "level_candy", p.team.first().uid, now = 0)
        assertIs<PurchaseResult.Success>(candy)
        assertEquals(6, candy.profile.team.first().level)
        assertEquals(700, candy.profile.coins)
        p = candy.profile

        val egg = GameRules.buy(p, "egg_common", now = 0, random = Random(2))
        assertIs<PurchaseResult.Success>(egg)
        assertEquals(2, egg.profile.team.size)

        assertIs<PurchaseResult.Failure>(GameRules.buy(egg.profile, "egg_rare", now = 0))
    }

    @Test
    fun `iap grants are idempotent`() {
        val p = PlayerProfile(gems = 0)
        val once = GameRules.grantPurchase(p, "gems_medium", "token-1")
        val twice = GameRules.grantPurchase(once, "gems_medium", "token-1")
        assertEquals(550, twice.gems)
        assertTrue(GameRules.grantPurchase(p, "remove_ads", "token-2").adFree)
    }

    @Test
    fun `daily reward streak`() {
        var p = PlayerProfile(coins = 0)
        for (day in 1L..7L) p = GameRules.claimDaily(p, day).first
        assertEquals(7, p.dailyStreak)
        assertEquals(50 * (1 + 2 + 3 + 4 + 5 + 6 + 7), p.coins)
        assertEquals(PlayerProfile().gems + 15, p.gems)
        assertFalse(GameRules.canClaimDaily(p, 7))
        p = GameRules.claimDaily(p, 9).first
        assertEquals(1, p.dailyStreak)
    }

    @Test
    fun `profile survives json round trip`() {
        val p = GameRules.chooseStarter(PlayerProfile(defeatedTrainers = setOf("t01")), "glutling")
        assertEquals(p, PlayerProfile.fromJson(p.toJson()))
    }
}
