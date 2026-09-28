package com.shindo91.trainerbattle.core.battle

import com.shindo91.trainerbattle.core.model.Creature
import com.shindo91.trainerbattle.core.model.Move
import com.shindo91.trainerbattle.core.model.MoveEffect
import com.shindo91.trainerbattle.core.progression.Experience
import kotlin.math.max
import kotlin.random.Random

enum class Side { PLAYER, OPPONENT;
    val other: Side get() = if (this == PLAYER) OPPONENT else PLAYER
}

enum class BattlePhase { CHOOSE_ACTION, PLAYER_MUST_SWITCH, FINISHED }

sealed interface BattleAction {
    data class UseMove(val moveIndex: Int) : BattleAction
    data class Switch(val memberIndex: Int) : BattleAction
    data object Forfeit : BattleAction
}

/** Human-readable battle log entries; [side] tells the UI whom it concerns. */
data class BattleEvent(val text: String, val side: Side? = null, val kind: Kind = Kind.INFO) {
    enum class Kind { INFO, ATTACK, DAMAGE, SUPER_EFFECTIVE, NOT_EFFECTIVE, MISS, FAINT, SWITCH, STAT, HEAL, END }
}

/** A creature's in-battle state. */
class Combatant(val creature: Creature) {
    val maxHp: Int = creature.stats.hp
    var hp: Int = maxHp
        internal set
    var attackStage: Int = 0
        internal set
    var defenseStage: Int = 0
        internal set

    val isFainted: Boolean get() = hp <= 0
    val hpFraction: Float get() = hp.toFloat() / maxHp

    internal fun resetStages() { attackStage = 0; defenseStage = 0 }

    val effectiveAttack: Double get() = creature.stats.attack * stageMultiplier(attackStage)
    val effectiveDefense: Double get() = creature.stats.defense * stageMultiplier(defenseStage)

    companion object {
        fun stageMultiplier(stage: Int): Double =
            if (stage >= 0) (2.0 + stage) / 2.0 else 2.0 / (2.0 - stage)
    }
}

class BattleTeam(creatures: List<Creature>) {
    init { require(creatures.isNotEmpty()) { "A team needs at least one creature" } }

    val members: List<Combatant> = creatures.map { Combatant(it) }
    var activeIndex: Int = 0
        internal set
    val active: Combatant get() = members[activeIndex]
    val isDefeated: Boolean get() = members.all { it.isFainted }
    fun nextAliveIndex(): Int? = members.indexOfFirst { !it.isFainted }.takeIf { it >= 0 }
}

object DamageCalculator {
    data class Result(val damage: Int, val effectiveness: Double, val critical: Boolean)

    fun calculate(attacker: Combatant, defender: Combatant, move: Move, random: Random): Result {
        val effectiveness = move.type.effectivenessAgainst(defender.creature.species.type)
        if (!move.isDamaging || effectiveness == 0.0) return Result(0, effectiveness, false)
        val level = attacker.creature.level
        val base = ((2.0 * level / 5 + 2) * move.power * attacker.effectiveAttack / defender.effectiveDefense) / 50 + 2
        val stab = if (move.type == attacker.creature.species.type) 1.5 else 1.0
        val critical = random.nextInt(16) == 0
        val crit = if (critical) 1.5 else 1.0
        val roll = 0.85 + random.nextDouble() * 0.15
        val damage = max(1, (base * stab * effectiveness * crit * roll).toInt())
        return Result(damage, effectiveness, critical)
    }
}

/**
 * Turn-based 1v1 battle between two teams. The player submits an action per turn; the
 * opponent is driven by [BattleAi]. Not thread-safe; drive it from one thread.
 */
class Battle(
    playerCreatures: List<Creature>,
    val opponent: Trainer,
    opponentCreatures: List<Creature> = opponent.buildTeam(),
    private val random: Random = Random.Default,
) {
    val player = BattleTeam(playerCreatures)
    val enemy = BattleTeam(opponentCreatures)

    var phase: BattlePhase = BattlePhase.CHOOSE_ACTION
        private set
    var winner: Side? = null
        private set
    var turn: Int = 0
        private set

    private val xpEarned = mutableMapOf<String, Long>()
    private val ai = BattleAi(opponent.smartness, random)

    /** XP per player creature uid, accumulated during the battle. */
    val xpByCreature: Map<String, Long> get() = xpEarned.toMap()

    fun team(side: Side): BattleTeam = if (side == Side.PLAYER) player else enemy

    fun introEvents(): List<BattleEvent> = listOf(
        BattleEvent("${opponent.name} fordert dich heraus!", Side.OPPONENT),
        BattleEvent("${opponent.name} schickt ${enemy.active.creature.displayName} in den Kampf!", Side.OPPONENT, BattleEvent.Kind.SWITCH),
        BattleEvent("Los, ${player.active.creature.displayName}!", Side.PLAYER, BattleEvent.Kind.SWITCH),
    )

    fun submit(action: BattleAction): List<BattleEvent> {
        check(phase != BattlePhase.FINISHED) { "Battle is already finished" }
        val events = mutableListOf<BattleEvent>()

        if (action is BattleAction.Forfeit) {
            events += BattleEvent("Du gibst auf.", Side.PLAYER, BattleEvent.Kind.END)
            finish(Side.OPPONENT, events)
            return events
        }

        if (phase == BattlePhase.PLAYER_MUST_SWITCH) {
            require(action is BattleAction.Switch) { "A fainted creature must be replaced first" }
            switchIn(Side.PLAYER, action.memberIndex, events)
            phase = BattlePhase.CHOOSE_ACTION
            return events
        }

        turn++
        val enemyMove = ai.chooseMove(enemy.active, player.active)

        when (action) {
            is BattleAction.Switch -> {
                require(action.memberIndex != player.activeIndex) { "Creature is already active" }
                switchIn(Side.PLAYER, action.memberIndex, events)
                executeMove(Side.OPPONENT, enemyMove, events)
            }
            is BattleAction.UseMove -> {
                val moves = player.active.creature.moves
                require(action.moveIndex in moves.indices) { "Invalid move index" }
                val playerMove = moves[action.moveIndex]
                val playerActor = player.active
                val enemyActor = enemy.active
                val order = if (playerGoesFirst()) listOf(Side.PLAYER to playerMove, Side.OPPONENT to enemyMove)
                else listOf(Side.OPPONENT to enemyMove, Side.PLAYER to playerMove)
                for ((side, move) in order) {
                    if (phase == BattlePhase.FINISHED) break
                    // A creature that fainted or was replaced this turn loses its move.
                    val actor = if (side == Side.PLAYER) playerActor else enemyActor
                    if (actor.isFainted || team(side).active !== actor) continue
                    executeMove(side, move, events)
                }
            }
            BattleAction.Forfeit -> error("handled above")
        }
        return events
    }

    private fun playerGoesFirst(): Boolean {
        val ps = player.active.creature.stats.speed
        val es = enemy.active.creature.stats.speed
        return if (ps != es) ps > es else random.nextBoolean()
    }

    private fun switchIn(side: Side, index: Int, events: MutableList<BattleEvent>) {
        val t = team(side)
        require(index in t.members.indices) { "Invalid team index" }
        require(!t.members[index].isFainted) { "Cannot switch to a fainted creature" }
        t.active.resetStages()
        t.activeIndex = index
        val text = if (side == Side.PLAYER) "Los, ${t.active.creature.displayName}!"
        else "${opponent.name} schickt ${t.active.creature.displayName} in den Kampf!"
        events += BattleEvent(text, side, BattleEvent.Kind.SWITCH)
    }

    private fun executeMove(side: Side, move: Move, events: MutableList<BattleEvent>) {
        val attacker = team(side).active
        val defender = team(side.other).active
        val prefix = if (side == Side.OPPONENT) "Gegnerisches " else ""
        events += BattleEvent("$prefix${attacker.creature.displayName} setzt ${move.name} ein!", side, BattleEvent.Kind.ATTACK)

        if (random.nextInt(100) >= move.accuracy) {
            events += BattleEvent("Daneben!", side, BattleEvent.Kind.MISS)
            return
        }

        when (move.effect) {
            MoveEffect.HEAL_SELF -> {
                val healed = minOf(attacker.maxHp - attacker.hp, attacker.maxHp * 2 / 5)
                attacker.hp += healed
                events += BattleEvent("${attacker.creature.displayName} heilt $healed KP.", side, BattleEvent.Kind.HEAL)
            }
            MoveEffect.RAISE_ATTACK -> {
                if (attacker.attackStage >= 6) {
                    events += BattleEvent("Der Angriff kann nicht weiter steigen.", side, BattleEvent.Kind.STAT)
                } else {
                    attacker.attackStage++
                    events += BattleEvent("Angriff von ${attacker.creature.displayName} steigt!", side, BattleEvent.Kind.STAT)
                }
            }
            MoveEffect.LOWER_DEFENSE -> {
                if (defender.defenseStage <= -6) {
                    events += BattleEvent("Die Verteidigung kann nicht weiter sinken.", side.other, BattleEvent.Kind.STAT)
                } else {
                    defender.defenseStage--
                    events += BattleEvent("Verteidigung von ${defender.creature.displayName} sinkt!", side.other, BattleEvent.Kind.STAT)
                }
            }
            MoveEffect.NONE -> Unit
        }

        if (!move.isDamaging) return

        val result = DamageCalculator.calculate(attacker, defender, move, random)
        when {
            result.effectiveness == 0.0 -> {
                events += BattleEvent("Das hat keine Wirkung…", side.other, BattleEvent.Kind.NOT_EFFECTIVE)
                return
            }
            result.effectiveness > 1.0 -> events += BattleEvent("Das ist sehr effektiv!", side.other, BattleEvent.Kind.SUPER_EFFECTIVE)
            result.effectiveness < 1.0 -> events += BattleEvent("Das ist nicht sehr effektiv…", side.other, BattleEvent.Kind.NOT_EFFECTIVE)
        }
        if (result.critical) events += BattleEvent("Volltreffer!", side.other, BattleEvent.Kind.DAMAGE)
        defender.hp = max(0, defender.hp - result.damage)
        events += BattleEvent("${defender.creature.displayName} verliert ${result.damage} KP.", side.other, BattleEvent.Kind.DAMAGE)

        if (defender.isFainted) handleFaint(side.other, events)
    }

    private fun handleFaint(side: Side, events: MutableList<BattleEvent>) {
        val t = team(side)
        val fainted = t.active
        events += BattleEvent("${fainted.creature.displayName} ist besiegt!", side, BattleEvent.Kind.FAINT)

        if (side == Side.OPPONENT) awardXp(fainted, events)

        if (t.isDefeated) {
            finish(side.other, events)
            return
        }
        if (side == Side.OPPONENT) {
            switchIn(Side.OPPONENT, t.nextAliveIndex()!!, events)
        } else {
            phase = BattlePhase.PLAYER_MUST_SWITCH
        }
    }

    /** Active creature gets full XP, conscious benched teammates half ("XP share"). */
    private fun awardXp(defeated: Combatant, events: MutableList<BattleEvent>) {
        val c = defeated.creature
        val xp = Experience.rewardFor(c.species.xpYield, c.level)
        player.members.forEachIndexed { index, member ->
            if (member.isFainted) return@forEachIndexed
            val amount = if (index == player.activeIndex) xp else xp / 2
            xpEarned.merge(member.creature.uid, amount, Long::plus)
        }
        events += BattleEvent("${player.active.creature.displayName} erhält $xp EP.", Side.PLAYER, BattleEvent.Kind.INFO)
    }

    private fun finish(winnerSide: Side, events: MutableList<BattleEvent>) {
        winner = winnerSide
        phase = BattlePhase.FINISHED
        val text = if (winnerSide == Side.PLAYER) "Du hast ${opponent.name} besiegt!" else "Du hast verloren…"
        events += BattleEvent(text, winnerSide, BattleEvent.Kind.END)
    }
}

/** Opponent AI: picks the highest-scoring move with probability [smartness], else a random one. */
class BattleAi(private val smartness: Double, private val random: Random) {
    fun chooseMove(self: Combatant, target: Combatant): Move {
        val moves = self.creature.moves
        if (random.nextDouble() >= smartness) return moves.random(random)
        return moves.maxBy { score(it, self, target) }
    }

    internal fun score(move: Move, self: Combatant, target: Combatant): Double = when (move.effect) {
        MoveEffect.HEAL_SELF -> if (self.hpFraction < 0.5f) 90.0 else 0.0
        MoveEffect.RAISE_ATTACK -> if (self.attackStage < 2 && self.hpFraction > 0.6f) 50.0 else 5.0
        MoveEffect.LOWER_DEFENSE -> if (target.defenseStage > -2 && self.hpFraction > 0.6f) 45.0 else 5.0
        MoveEffect.NONE -> {
            val stab = if (move.type == self.creature.species.type) 1.5 else 1.0
            move.power * move.accuracy / 100.0 * stab * move.type.effectivenessAgainst(target.creature.species.type)
        }
    }
}
