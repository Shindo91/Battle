package com.shindo91.trainerbattle.core.model

/** Optional secondary effect of a move. */
enum class MoveEffect {
    NONE,
    /** Heals the user by 40% of its max HP instead of dealing damage. */
    HEAL_SELF,
    /** Raises the user's attack by one stage. */
    RAISE_ATTACK,
    /** Lowers the target's defense by one stage. */
    LOWER_DEFENSE,
}

data class Move(
    val id: String,
    val name: String,
    val type: ElementType,
    /** 0 for status moves. */
    val power: Int,
    /** Hit chance in percent (1..100). */
    val accuracy: Int = 100,
    val effect: MoveEffect = MoveEffect.NONE,
) {
    val isDamaging: Boolean get() = power > 0
}

object Moves {
    private val all = listOf(
        Move("tackle", "Rempler", ElementType.NORMAL, 40),
        Move("scratch", "Kratzer", ElementType.NORMAL, 45, accuracy = 95),
        Move("body_slam", "Bodycheck", ElementType.NORMAL, 80, accuracy = 90),
        Move("roar", "Brüllen", ElementType.NORMAL, 0, effect = MoveEffect.RAISE_ATTACK),
        Move("glare", "Starrblick", ElementType.NORMAL, 0, effect = MoveEffect.LOWER_DEFENSE),
        Move("recover", "Regeneration", ElementType.NORMAL, 0, effect = MoveEffect.HEAL_SELF),

        Move("ember", "Glut", ElementType.FIRE, 45),
        Move("flame_wheel", "Flammenrad", ElementType.FIRE, 65, accuracy = 95),
        Move("inferno", "Inferno", ElementType.FIRE, 95, accuracy = 85),

        Move("splash_shot", "Spritzer", ElementType.WATER, 45),
        Move("bubble_beam", "Blasenstrahl", ElementType.WATER, 65, accuracy = 95),
        Move("tidal_wave", "Flutwelle", ElementType.WATER, 95, accuracy = 85),

        Move("leaf_cut", "Blattschnitt", ElementType.NATURE, 45),
        Move("vine_whip", "Rankenpeitsche", ElementType.NATURE, 65, accuracy = 95),
        Move("solar_burst", "Solarstoß", ElementType.NATURE, 95, accuracy = 85),

        Move("spark", "Funke", ElementType.ELECTRIC, 45),
        Move("volt_tackle", "Voltrempler", ElementType.ELECTRIC, 70, accuracy = 95),
        Move("thunderstorm", "Donnersturm", ElementType.ELECTRIC, 95, accuracy = 80),

        Move("rock_throw", "Steinwurf", ElementType.EARTH, 45, accuracy = 95),
        Move("mud_slide", "Schlammlawine", ElementType.EARTH, 65, accuracy = 95),
        Move("quake", "Erdbeben", ElementType.EARTH, 95, accuracy = 90),

        Move("gust", "Windstoß", ElementType.AIR, 45),
        Move("wing_strike", "Flügelhieb", ElementType.AIR, 65, accuracy = 95),
        Move("hurricane", "Orkan", ElementType.AIR, 95, accuracy = 80),
    ).associateBy { it.id }

    operator fun get(id: String): Move =
        all[id] ?: throw IllegalArgumentException("Unknown move: $id")

    val ids: Set<String> get() = all.keys
}
