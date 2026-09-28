package com.shindo91.trainerbattle.core.model

/** Element types of creatures and moves. */
enum class ElementType(val displayName: String) {
    NORMAL("Normal"),
    FIRE("Feuer"),
    WATER("Wasser"),
    NATURE("Natur"),
    ELECTRIC("Blitz"),
    EARTH("Erde"),
    AIR("Luft");

    /** Damage multiplier when a move of this type hits a creature of [defender] type. */
    fun effectivenessAgainst(defender: ElementType): Double =
        CHART[this]?.get(defender) ?: 1.0

    companion object {
        private val CHART: Map<ElementType, Map<ElementType, Double>> = mapOf(
            FIRE to mapOf(NATURE to 2.0, AIR to 1.0, WATER to 0.5, FIRE to 0.5, EARTH to 0.5),
            WATER to mapOf(FIRE to 2.0, EARTH to 2.0, WATER to 0.5, NATURE to 0.5),
            NATURE to mapOf(WATER to 2.0, EARTH to 2.0, FIRE to 0.5, NATURE to 0.5, AIR to 0.5),
            ELECTRIC to mapOf(WATER to 2.0, AIR to 2.0, EARTH to 0.0, NATURE to 0.5, ELECTRIC to 0.5),
            EARTH to mapOf(FIRE to 2.0, ELECTRIC to 2.0, AIR to 0.0, NATURE to 0.5),
            AIR to mapOf(NATURE to 2.0, EARTH to 1.0, ELECTRIC to 0.5),
        )
    }
}
