package com.shindo91.trainerbattle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shindo91.trainerbattle.core.battle.Arena
import com.shindo91.trainerbattle.core.battle.Battle
import com.shindo91.trainerbattle.core.battle.BattleAction
import com.shindo91.trainerbattle.core.battle.BattleEvent
import com.shindo91.trainerbattle.core.battle.BattlePhase
import com.shindo91.trainerbattle.core.battle.Campaign
import com.shindo91.trainerbattle.core.battle.Combatant
import com.shindo91.trainerbattle.core.battle.Side
import com.shindo91.trainerbattle.core.battle.Trainer
import com.shindo91.trainerbattle.core.economy.BattleRewards
import com.shindo91.trainerbattle.core.economy.Energy
import com.shindo91.trainerbattle.core.economy.GameRules
import com.shindo91.trainerbattle.core.economy.PlayerProfile
import com.shindo91.trainerbattle.core.economy.PurchaseResult
import com.shindo91.trainerbattle.core.model.Move
import com.shindo91.trainerbattle.data.SaveRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

enum class Screen { LOADING, STARTER, HOME, CAMPAIGN, BATTLE, TEAM, SHOP }

/** Immutable snapshot of one creature in battle, for the UI. */
data class CombatantUi(
    val uid: String,
    val name: String,
    val emoji: String,
    val level: Int,
    val type: String,
    val hp: Int,
    val maxHp: Int,
    val fainted: Boolean,
)

data class BattleUi(
    val trainer: Trainer,
    val player: CombatantUi,
    val enemy: CombatantUi,
    val playerTeam: List<CombatantUi>,
    val activeIndex: Int,
    val moves: List<Move>,
    val log: List<BattleEvent>,
    val phase: BattlePhase,
    /** True while events of the last turn are still being revealed. */
    val animating: Boolean,
    val rewards: BattleRewards? = null,
    val coinsDoubled: Boolean = false,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(app)
    private val saveMutex = Mutex()

    private val _profile = MutableStateFlow(PlayerProfile())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    private val _screen = MutableStateFlow(Screen.LOADING)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _battle = MutableStateFlow<BattleUi?>(null)
    val battle: StateFlow<BattleUi?> = _battle.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var activeBattle: Battle? = null
    private var activeTrainerId: String? = null

    init {
        viewModelScope.launch {
            val loaded = Energy.regenerate(repo.load(), now())
            _profile.value = loaded
            _screen.value = if (loaded.hasStarter) Screen.HOME else Screen.STARTER
        }
        // Energy ticks while the app is open.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val p = _profile.value
                val regenerated = Energy.regenerate(p, now())
                if (regenerated.energy != p.energy) persist(regenerated)
            }
        }
    }

    private fun now() = System.currentTimeMillis()
    private fun today() = LocalDate.now().toEpochDay()

    private fun persist(p: PlayerProfile) {
        _profile.value = p
        viewModelScope.launch { saveMutex.withLock { repo.save(_profile.value) } }
    }

    fun navigate(screen: Screen) { _screen.value = screen }
    fun showToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = null }

    // ----- Onboarding -----

    fun chooseStarter(speciesId: String, trainerName: String) {
        val named = _profile.value.copy(trainerName = trainerName.trim().ifBlank { "Trainer" })
        persist(GameRules.chooseStarter(named, speciesId).copy(energyUpdatedAt = now()))
        _screen.value = Screen.HOME
    }

    // ----- Daily reward -----

    val canClaimDaily: Boolean get() = GameRules.canClaimDaily(_profile.value, today())

    fun claimDaily() {
        if (!canClaimDaily) return
        val (p, msg) = GameRules.claimDaily(_profile.value, today())
        persist(p)
        showToast(msg)
    }

    // ----- Battles -----

    fun startCampaignBattle(trainerId: String) {
        val trainer = Campaign.byId(trainerId) ?: return
        if (!Campaign.isUnlocked(trainerId, _profile.value.defeatedTrainers)) return
        startBattle(trainer, trainerId)
    }

    fun startArenaBattle() {
        val p = _profile.value
        startBattle(Arena.generateRival(p.team, p.arenaRating), "arena")
    }

    private fun startBattle(trainer: Trainer, trainerId: String) {
        val p = Energy.regenerate(_profile.value, now())
        if (!GameRules.canStartBattle(p)) {
            _profile.value = p
            showToast("Keine Energie! Warte kurz, schau ein Video oder fülle im Shop auf.")
            return
        }
        persist(GameRules.spendBattleEnergy(p, now()))
        val battle = Battle(p.team, trainer)
        activeBattle = battle
        activeTrainerId = trainerId
        _battle.value = snapshot(battle, battle.introEvents(), animating = false)
        _screen.value = Screen.BATTLE
    }

    fun submitAction(action: BattleAction) {
        val battle = activeBattle ?: return
        val current = _battle.value ?: return
        if (current.animating || battle.phase == BattlePhase.FINISHED) return
        val events = runCatching { battle.submit(action) }.getOrElse {
            showToast(it.message ?: "Ungültige Aktion")
            return
        }
        // Reveal the turn's events one by one for a bit of drama.
        viewModelScope.launch {
            var log = current.log
            for (event in events) {
                log = (log + event).takeLast(MAX_LOG)
                _battle.value = snapshot(battle, log, animating = true)
                delay(EVENT_DELAY_MS)
            }
            val finished = battle.phase == BattlePhase.FINISHED
            val rewards = if (finished) finishBattle(battle) else null
            _battle.value = snapshot(battle, log, animating = false).copy(rewards = rewards)
        }
    }

    private fun finishBattle(battle: Battle): BattleRewards {
        val trainerId = activeTrainerId ?: "arena"
        val (p, rewards) = GameRules.applyBattleResult(
            _profile.value, trainerId, battle.opponent.coinReward,
            won = battle.winner == Side.PLAYER, xpByCreature = battle.xpByCreature,
        )
        persist(p)
        return rewards
    }

    /** Rewarded ad callback: doubles the coins of the last won battle, once. */
    fun doubleBattleCoins() {
        val b = _battle.value ?: return
        val rewards = b.rewards ?: return
        if (b.coinsDoubled || !rewards.won) return
        persist(GameRules.applyDoubleCoins(_profile.value, rewards))
        _battle.value = b.copy(coinsDoubled = true)
    }

    fun leaveBattle() {
        activeBattle = null
        activeTrainerId = null
        _battle.value = null
        _screen.value = Screen.HOME
    }

    private fun snapshot(battle: Battle, log: List<BattleEvent>, animating: Boolean) = BattleUi(
        trainer = battle.opponent,
        player = battle.player.active.toUi(),
        enemy = battle.enemy.active.toUi(),
        playerTeam = battle.player.members.map { it.toUi() },
        activeIndex = battle.player.activeIndex,
        moves = battle.player.active.creature.moves,
        log = log,
        phase = battle.phase,
        animating = animating,
    )

    private fun Combatant.toUi() = CombatantUi(
        uid = creature.uid,
        name = creature.displayName,
        emoji = creature.species.emoji,
        level = creature.level,
        type = creature.species.type.displayName,
        hp = hp,
        maxHp = maxHp,
        fainted = isFainted,
    )

    // ----- Energy / ads -----

    fun rewardEnergyFromAd() {
        persist(GameRules.applyEnergyAd(_profile.value, now()))
        showToast("+${Energy.AD_REWARD} Energie")
    }

    // ----- Team -----

    fun toggleTeamMember(uid: String) {
        val before = _profile.value
        val after = GameRules.toggleTeamMember(before, uid)
        if (after == before) {
            showToast(if (before.team.any { it.uid == uid }) "Mindestens eine Kreatur muss im Team sein" else "Team ist voll (max. ${PlayerProfile.MAX_TEAM_SIZE})")
        }
        persist(after)
    }

    fun makeLeader(uid: String) = persist(GameRules.makeLeader(_profile.value, uid))

    // ----- Shop -----

    fun buy(itemId: String, targetUid: String? = null) {
        when (val r = GameRules.buy(_profile.value, itemId, targetUid, now())) {
            is PurchaseResult.Success -> {
                persist(r.profile)
                val evolved = r.levelUp?.evolvedTo?.let { " Es hat sich entwickelt!" } ?: ""
                showToast(r.message + evolved)
            }
            is PurchaseResult.Failure -> showToast(r.reason)
        }
    }

    /** Called by BillingManager once Google Play confirms a purchase. */
    suspend fun grantPurchase(productId: String, purchaseToken: String) {
        val before = _profile.value
        val after = GameRules.grantPurchase(before, productId, purchaseToken)
        if (after == before) return
        _profile.value = after
        saveMutex.withLock { repo.save(after) }
        showToast(if (productId == GameRules.REMOVE_ADS_PRODUCT) "Werbung entfernt – danke!" else "Edelsteine gutgeschrieben!")
    }

    private companion object {
        const val MAX_LOG = 6
        const val EVENT_DELAY_MS = 550L
    }
}
