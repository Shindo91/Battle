package com.shindo91.trainerbattle.core.model

data class BaseStats(val hp: Int, val attack: Int, val defense: Int, val speed: Int) {
    val total: Int get() = hp + attack + defense + speed
}

enum class Rarity { COMMON, RARE, EPIC }

data class Species(
    val id: String,
    val name: String,
    val type: ElementType,
    val baseStats: BaseStats,
    /** Moves learned at a given level (level -> move ids). */
    val learnset: Map<Int, List<String>>,
    val evolvesTo: String? = null,
    val evolveLevel: Int? = null,
    /** Placeholder art until real sprites exist. */
    val emoji: String,
    val rarity: Rarity = Rarity.COMMON,
    val isStarter: Boolean = false,
    /** Base XP granted when this species is defeated. */
    val xpYield: Int = baseStats.total / 4,
) {
    /** The (up to 4) most recent moves a creature of this species knows at [level]. */
    fun movesAtLevel(level: Int): List<String> =
        learnset.filterKeys { it <= level }
            .toSortedMap()
            .values
            .flatten()
            .distinct()
            .takeLast(4)
}

object SpeciesCatalog {
    private val all = listOf(
        // Fire line
        Species(
            "glutling", "Glutling", ElementType.FIRE, BaseStats(39, 52, 43, 65),
            learnset = mapOf(1 to listOf("scratch", "ember"), 7 to listOf("roar"), 13 to listOf("flame_wheel")),
            evolvesTo = "flammdrache", evolveLevel = 16, emoji = "🦎", isStarter = true,
        ),
        Species(
            "flammdrache", "Flammdrache", ElementType.FIRE, BaseStats(78, 84, 70, 90),
            learnset = mapOf(1 to listOf("scratch", "ember", "roar", "flame_wheel"), 30 to listOf("inferno")),
            emoji = "🐉",
        ),
        // Water line
        Species(
            "tropfi", "Tropfi", ElementType.WATER, BaseStats(44, 48, 65, 43),
            learnset = mapOf(1 to listOf("tackle", "splash_shot"), 7 to listOf("glare"), 13 to listOf("bubble_beam")),
            evolvesTo = "wogenhai", evolveLevel = 16, emoji = "💧", isStarter = true,
        ),
        Species(
            "wogenhai", "Wogenhai", ElementType.WATER, BaseStats(85, 80, 90, 70),
            learnset = mapOf(1 to listOf("tackle", "splash_shot", "glare", "bubble_beam"), 30 to listOf("tidal_wave")),
            emoji = "🦈",
        ),
        // Nature line
        Species(
            "sproessling", "Sprössling", ElementType.NATURE, BaseStats(45, 49, 49, 45),
            learnset = mapOf(1 to listOf("tackle", "leaf_cut"), 7 to listOf("recover"), 13 to listOf("vine_whip")),
            evolvesTo = "waldgeist", evolveLevel = 16, emoji = "🌱", isStarter = true,
        ),
        Species(
            "waldgeist", "Waldgeist", ElementType.NATURE, BaseStats(90, 78, 85, 67),
            learnset = mapOf(1 to listOf("tackle", "leaf_cut", "recover", "vine_whip"), 30 to listOf("solar_burst")),
            emoji = "🌳",
        ),
        // Electric line
        Species(
            "funkmaus", "Funkmaus", ElementType.ELECTRIC, BaseStats(35, 55, 40, 90),
            learnset = mapOf(1 to listOf("tackle", "spark"), 9 to listOf("roar"), 15 to listOf("volt_tackle")),
            evolvesTo = "donnerratte", evolveLevel = 18, emoji = "🐭",
        ),
        Species(
            "donnerratte", "Donnerratte", ElementType.ELECTRIC, BaseStats(60, 90, 55, 110),
            learnset = mapOf(1 to listOf("tackle", "spark", "roar", "volt_tackle"), 32 to listOf("thunderstorm")),
            emoji = "⚡",
        ),
        // Earth line
        Species(
            "kieselkrabbe", "Kieselkrabbe", ElementType.EARTH, BaseStats(50, 60, 80, 25),
            learnset = mapOf(1 to listOf("tackle", "rock_throw"), 9 to listOf("glare"), 17 to listOf("mud_slide")),
            evolvesTo = "felskoloss", evolveLevel = 20, emoji = "🦀",
        ),
        Species(
            "felskoloss", "Felskoloss", ElementType.EARTH, BaseStats(85, 100, 115, 35),
            learnset = mapOf(1 to listOf("tackle", "rock_throw", "glare", "mud_slide"), 34 to listOf("quake")),
            emoji = "🗿",
        ),
        // Air line
        Species(
            "federling", "Federling", ElementType.AIR, BaseStats(40, 45, 40, 70),
            learnset = mapOf(1 to listOf("tackle", "gust"), 9 to listOf("roar"), 15 to listOf("wing_strike")),
            evolvesTo = "sturmfalke", evolveLevel = 18, emoji = "🐦",
        ),
        Species(
            "sturmfalke", "Sturmfalke", ElementType.AIR, BaseStats(75, 80, 70, 101),
            learnset = mapOf(1 to listOf("tackle", "gust", "roar", "wing_strike"), 32 to listOf("hurricane")),
            emoji = "🦅",
        ),
        // Normal line
        Species(
            "pummel", "Pummel", ElementType.NORMAL, BaseStats(70, 55, 55, 30),
            learnset = mapOf(1 to listOf("tackle", "glare"), 10 to listOf("recover"), 18 to listOf("body_slam")),
            evolvesTo = "brummbaer", evolveLevel = 22, emoji = "🐻",
        ),
        Species(
            "brummbaer", "Brummbär", ElementType.NORMAL, BaseStats(110, 95, 75, 45),
            learnset = mapOf(1 to listOf("tackle", "glare", "recover", "body_slam"), 30 to listOf("roar")),
            emoji = "🐻‍❄️",
        ),
        // Rare creatures (eggs, late trainers)
        Species(
            "nebelfuchs", "Nebelfuchs", ElementType.AIR, BaseStats(70, 85, 65, 110),
            learnset = mapOf(1 to listOf("scratch", "gust", "glare"), 12 to listOf("wing_strike"), 28 to listOf("hurricane")),
            emoji = "🦊", rarity = Rarity.RARE,
        ),
        Species(
            "magmarochen", "Magmarochen", ElementType.FIRE, BaseStats(80, 95, 80, 75),
            learnset = mapOf(1 to listOf("tackle", "ember", "roar"), 12 to listOf("flame_wheel"), 28 to listOf("inferno")),
            emoji = "🔥", rarity = Rarity.RARE,
        ),
        Species(
            "kristallwyrm", "Kristallwyrm", ElementType.EARTH, BaseStats(100, 110, 110, 80),
            learnset = mapOf(1 to listOf("rock_throw", "roar", "recover"), 15 to listOf("mud_slide"), 25 to listOf("quake")),
            emoji = "💎", rarity = Rarity.EPIC,
        ),
    ).associateBy { it.id }

    operator fun get(id: String): Species =
        all[id] ?: throw IllegalArgumentException("Unknown species: $id")

    val species: Collection<Species> get() = all.values
    val starters: List<Species> get() = all.values.filter { it.isStarter }

    /** Base forms (not reachable only via evolution) of the given rarity. */
    fun hatchable(rarity: Rarity): List<Species> {
        val evolvedForms = all.values.mapNotNull { it.evolvesTo }.toSet()
        return all.values.filter { it.rarity == rarity && it.id !in evolvedForms && !it.isStarter }
    }
}
