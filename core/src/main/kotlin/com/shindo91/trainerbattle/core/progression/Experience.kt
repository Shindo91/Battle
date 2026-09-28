package com.shindo91.trainerbattle.core.progression

object Experience {
    const val MAX_LEVEL = 100

    /** Total XP a creature needs to have reached [level]. */
    fun totalXpForLevel(level: Int): Long {
        val l = level.coerceIn(1, MAX_LEVEL).toLong()
        return if (l == 1L) 0L else l * l * l
    }

    fun levelForTotalXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && totalXpForLevel(level + 1) <= xp) level++
        return level
    }

    /**
     * XP granted for defeating a creature with [xpYield] at [defeatedLevel].
     * Trainer battles give a 50% bonus.
     */
    fun rewardFor(xpYield: Int, defeatedLevel: Int, trainerBattle: Boolean = true): Long {
        val base = xpYield.toLong() * defeatedLevel / 5
        return if (trainerBattle) base * 3 / 2 else base
    }
}
