package com.shindo91.trainerbattle.core.economy

import com.shindo91.trainerbattle.core.model.Creature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** All persistent player state. Immutable; game rules return updated copies. */
@Serializable
data class PlayerProfile(
    val trainerName: String = "Trainer",
    val coins: Int = 200,
    val gems: Int = 10,
    val energy: Int = Energy.MAX,
    /** Epoch millis of the last energy regeneration tick. */
    val energyUpdatedAt: Long = 0,
    /** Battle team, max [MAX_TEAM_SIZE]; the first creature starts the battle. */
    val team: List<Creature> = emptyList(),
    /** Creatures not in the team. */
    val storage: List<Creature> = emptyList(),
    val defeatedTrainers: Set<String> = emptySet(),
    val arenaRating: Int = 1000,
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
    /** Epoch day of the last claimed daily reward, -1 if never. */
    val lastDailyRewardDay: Long = -1,
    val dailyStreak: Int = 0,
    /** Set by the "remove ads" in-app purchase. */
    val adFree: Boolean = false,
    /** Purchase tokens already granted, so a purchase is never credited twice. */
    val grantedPurchaseTokens: Set<String> = emptySet(),
) {
    val hasStarter: Boolean get() = team.isNotEmpty() || storage.isNotEmpty()
    val allCreatures: List<Creature> get() = team + storage

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MAX_TEAM_SIZE = 3

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): PlayerProfile = json.decodeFromString(serializer(), text)
    }
}
