package com.shindo91.trainerbattle.core.battle

import com.shindo91.trainerbattle.core.model.Creature
import com.shindo91.trainerbattle.core.model.Rarity
import com.shindo91.trainerbattle.core.model.SpeciesCatalog
import kotlin.random.Random

/** An NPC opponent. [smartness] (0..1) controls how often the AI picks its best move. */
data class Trainer(
    val id: String,
    val name: String,
    val title: String,
    val avatar: String,
    val team: List<Pair<String, Int>>,
    val coinReward: Int,
    /** Gems granted only on the first victory. */
    val firstWinGems: Int = 0,
    val smartness: Double = 0.5,
) {
    fun buildTeam(random: Random = Random.Default): List<Creature> =
        team.map { (speciesId, level) -> Creature.create(speciesId, level, random) }

    val maxLevel: Int get() = team.maxOf { it.second }
}

/** The single-player campaign: trainers must be beaten in order. */
object Campaign {
    val trainers: List<Trainer> = listOf(
        Trainer("t01", "Lena", "Anfängerin", "👧", listOf("pummel" to 3), coinReward = 60, firstWinGems = 5, smartness = 0.1),
        Trainer("t02", "Tom", "Käferfreund", "👦", listOf("federling" to 4, "funkmaus" to 4), 80, 5, 0.2),
        Trainer("t03", "Mia", "Wasserratte", "👩", listOf("tropfi" to 6, "tropfi" to 7), 100, 5, 0.3),
        Trainer("t04", "Karl", "Bergsteiger", "🧔", listOf("kieselkrabbe" to 8, "pummel" to 9), 130, 10, 0.35),
        Trainer("t05", "Sofia", "Gärtnerin", "👩‍🌾", listOf("sproessling" to 10, "federling" to 11, "sproessling" to 12), 160, 10, 0.4),
        Trainer("t06", "Ben", "Techniker", "👨‍🔧", listOf("funkmaus" to 13, "funkmaus" to 14, "kieselkrabbe" to 14), 200, 10, 0.5),
        Trainer("t07", "Arena-Leiterin Ida", "Feuermeisterin", "👩‍🚒", listOf("glutling" to 15, "magmarochen" to 16, "flammdrache" to 18), 300, 25, 0.6),
        Trainer("t08", "Jonas", "Segler", "🧑‍✈️", listOf("wogenhai" to 20, "sturmfalke" to 21, "tropfi" to 19), 320, 15, 0.6),
        Trainer("t09", "Nora", "Nebelläuferin", "🧝‍♀️", listOf("nebelfuchs" to 22, "donnerratte" to 23, "waldgeist" to 23), 380, 15, 0.7),
        Trainer("t10", "Arena-Leiter Otto", "Felsenfaust", "🧙", listOf("felskoloss" to 26, "brummbaer" to 26, "kristallwyrm" to 28), 500, 40, 0.75),
        Trainer("t11", "Vera", "Sturmjägerin", "🦸‍♀️", listOf("sturmfalke" to 32, "donnerratte" to 33, "wogenhai" to 33), 600, 25, 0.85),
        Trainer("t12", "Champion Aurelius", "Champion", "👑", listOf("flammdrache" to 38, "kristallwyrm" to 38, "nebelfuchs" to 39, "wogenhai" to 40), 1000, 100, 0.95),
    )

    fun byId(id: String): Trainer? = trainers.firstOrNull { it.id == id }

    /** A trainer is unlocked when all previous trainers are defeated. */
    fun isUnlocked(trainerId: String, defeated: Set<String>): Boolean {
        val index = trainers.indexOfFirst { it.id == trainerId }
        if (index < 0) return false
        return trainers.take(index).all { it.id in defeated }
    }
}

/**
 * Arena: endlessly generated rivals scaled to the player's team. Stands in for online PvP
 * until a backend exists (see README).
 */
object Arena {
    private val rivalNames = listOf("Alex", "Sam", "Kim", "Robin", "Charlie", "Jule", "Toni", "Luca", "Mika", "Noah")
    private val avatars = listOf("🧑", "👱", "🧑‍🎤", "🧑‍🚀", "🥷", "🧑‍🎨")

    fun generateRival(playerTeam: List<Creature>, rating: Int, random: Random = Random.Default): Trainer {
        val avgLevel = if (playerTeam.isEmpty()) 5 else playerTeam.sumOf { it.level } / playerTeam.size
        val size = playerTeam.size.coerceIn(1, 3)
        val pool = SpeciesCatalog.species.filter { it.rarity != Rarity.EPIC || rating >= 1400 }
        val team = List(size) {
            val species = pool.random(random)
            species.id to (avgLevel + random.nextInt(-2, 2)).coerceIn(2, 100)
        }
        return Trainer(
            id = "arena",
            name = rivalNames.random(random),
            title = "Rivale · ${ratingTier(rating)}",
            avatar = avatars.random(random),
            team = team,
            coinReward = 40 + avgLevel * 6,
            smartness = (0.3 + (rating - 1000) / 1500.0).coerceIn(0.3, 0.95),
        )
    }

    fun ratingTier(rating: Int): String = when {
        rating < 1100 -> "Bronze"
        rating < 1300 -> "Silber"
        rating < 1500 -> "Gold"
        rating < 1700 -> "Platin"
        else -> "Diamant"
    }

    fun ratingChange(won: Boolean): Int = if (won) 25 else -15
}
