package com.shindo91.trainerbattle.core.model

import com.shindo91.trainerbattle.core.progression.Experience
import kotlinx.serialization.Serializable
import kotlin.random.Random

data class Stats(val hp: Int, val attack: Int, val defense: Int, val speed: Int)

/** What happened when a creature gained XP. */
data class LevelUpReport(
    val oldLevel: Int,
    val newLevel: Int,
    val learnedMoves: List<String>,
    val evolvedFrom: String?,
    val evolvedTo: String?,
) {
    val leveledUp: Boolean get() = newLevel > oldLevel
    val evolved: Boolean get() = evolvedTo != null
}

/** An owned creature. Immutable; progression returns updated copies. */
@Serializable
data class Creature(
    val uid: String,
    val speciesId: String,
    val level: Int,
    val xp: Long = Experience.totalXpForLevel(level),
    val moveIds: List<String> = SpeciesCatalog[speciesId].movesAtLevel(level),
    val nickname: String? = null,
) {
    val species: Species get() = SpeciesCatalog[speciesId]
    val displayName: String get() = nickname ?: species.name
    val moves: List<Move> get() = moveIds.map { Moves[it] }

    val stats: Stats
        get() {
            val b = species.baseStats
            fun stat(base: Int) = base * 2 * level / 100 + 5
            return Stats(
                hp = b.hp * 2 * level / 100 + level + 10,
                attack = stat(b.attack),
                defense = stat(b.defense),
                speed = stat(b.speed),
            )
        }

    val xpIntoLevel: Long get() = xp - Experience.totalXpForLevel(level)
    val xpForNextLevel: Long
        get() = if (level >= Experience.MAX_LEVEL) 0
        else Experience.totalXpForLevel(level + 1) - Experience.totalXpForLevel(level)

    /** Adds XP, applying level-ups, new moves and evolutions. */
    fun gainXp(amount: Long): Pair<Creature, LevelUpReport> {
        require(amount >= 0) { "XP must not be negative" }
        val newXp = (xp + amount).coerceAtMost(Experience.totalXpForLevel(Experience.MAX_LEVEL))
        val newLevel = Experience.levelForTotalXp(newXp)

        var speciesNow = species
        var evolvedTo: String? = null
        // Evolution chains may skip several stages on big XP gains.
        while (speciesNow.evolvesTo != null && newLevel >= (speciesNow.evolveLevel ?: Int.MAX_VALUE)) {
            speciesNow = SpeciesCatalog[speciesNow.evolvesTo!!]
            evolvedTo = speciesNow.id
        }

        val learned = mutableListOf<String>()
        val moves = moveIds.toMutableList()
        val candidates = speciesNow.learnset
            .filterKeys { it in (level + 1)..newLevel || (evolvedTo != null && it <= newLevel) }
            .toSortedMap().values.flatten()
        for (moveId in candidates) {
            if (moveId in moves) continue
            if (moves.size >= 4) {
                // Replace the weakest move so the creature keeps getting stronger.
                val weakest = moves.minBy { Moves[it].power }
                if (Moves[weakest].power >= Moves[moveId].power) continue
                moves.remove(weakest)
            }
            moves += moveId
            learned += moveId
        }

        val updated = copy(speciesId = speciesNow.id, level = newLevel, xp = newXp, moveIds = moves)
        return updated to LevelUpReport(
            oldLevel = level,
            newLevel = newLevel,
            learnedMoves = learned,
            evolvedFrom = if (evolvedTo != null) speciesId else null,
            evolvedTo = evolvedTo,
        )
    }

    companion object {
        fun create(speciesId: String, level: Int, random: Random = Random.Default): Creature =
            Creature(uid = newUid(random), speciesId = speciesId, level = level)

        fun newUid(random: Random = Random.Default): String =
            (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")
    }
}
