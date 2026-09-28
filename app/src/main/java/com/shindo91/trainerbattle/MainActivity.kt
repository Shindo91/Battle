package com.shindo91.trainerbattle

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.shindo91.trainerbattle.core.economy.GameRules
import com.shindo91.trainerbattle.monetization.AdsManager
import com.shindo91.trainerbattle.monetization.BillingManager
import com.shindo91.trainerbattle.ui.GameViewModel
import com.shindo91.trainerbattle.ui.Screen
import com.shindo91.trainerbattle.ui.screens.BattleScreen
import com.shindo91.trainerbattle.ui.screens.CampaignScreen
import com.shindo91.trainerbattle.ui.screens.HomeScreen
import com.shindo91.trainerbattle.ui.screens.IapOffer
import com.shindo91.trainerbattle.ui.screens.ShopScreen
import com.shindo91.trainerbattle.ui.screens.StarterScreen
import com.shindo91.trainerbattle.ui.screens.TeamScreen
import com.shindo91.trainerbattle.ui.theme.TrainerBattleTheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private lateinit var ads: AdsManager
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ads = AdsManager(this).also { it.start() }
        billing = BillingManager(applicationContext, lifecycleScope) { productId, token ->
            vm.grantPurchase(productId, token)
        }.also { it.start() }

        setContent {
            TrainerBattleTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) { GameApp() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up purchases completed while the app was in the background.
        billing.start()
    }

    override fun onDestroy() {
        billing.end()
        super.onDestroy()
    }

    @Composable
    private fun GameApp() {
        val screen by vm.screen.collectAsStateWithLifecycle()
        val profile by vm.profile.collectAsStateWithLifecycle()
        val battle by vm.battle.collectAsStateWithLifecycle()
        val toast by vm.toast.collectAsStateWithLifecycle()
        val billingMessage by billing.messages.collectAsStateWithLifecycle()
        val products by billing.products.collectAsStateWithLifecycle()
        val rewardedReady by ads.rewardedReady.collectAsStateWithLifecycle()

        LaunchedEffect(toast) {
            toast?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show(); vm.clearToast() }
        }
        LaunchedEffect(billingMessage) {
            billingMessage?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show(); billing.clearMessage() }
        }

        val goHome = { vm.navigate(Screen.HOME) }
        if (screen in setOf(Screen.CAMPAIGN, Screen.TEAM, Screen.SHOP)) BackHandler(onBack = goHome)

        when (screen) {
            Screen.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            Screen.STARTER -> StarterScreen(onChoose = vm::chooseStarter)
            Screen.HOME -> HomeScreen(
                profile = profile,
                canClaimDaily = vm.canClaimDaily,
                rewardedReady = rewardedReady,
                showPrivacyOptions = ads.isPrivacyOptionsRequired,
                onClaimDaily = vm::claimDaily,
                onCampaign = { vm.navigate(Screen.CAMPAIGN) },
                onArena = vm::startArenaBattle,
                onTeam = { vm.navigate(Screen.TEAM) },
                onShop = { vm.navigate(Screen.SHOP) },
                onEnergyAd = { ads.showRewarded(onReward = vm::rewardEnergyFromAd, onUnavailable = { vm.showToast("Gerade kein Video verfügbar") }) },
                onPrivacyOptions = ads::showPrivacyOptions,
                banner = { BannerAd() },
            )
            Screen.CAMPAIGN -> CampaignScreen(profile, onBack = goHome, onFight = vm::startCampaignBattle)
            Screen.TEAM -> TeamScreen(profile, onBack = goHome, onToggle = vm::toggleTeamMember, onMakeLeader = vm::makeLeader)
            Screen.SHOP -> ShopScreen(
                profile = profile,
                iapOffers = listOf(
                    IapOffer("gems_small", "100 Edelsteine", "💎", products["gems_small"]?.oneTimePurchaseOfferDetails?.formattedPrice),
                    IapOffer("gems_medium", "550 Edelsteine (+10 %)", "💎", products["gems_medium"]?.oneTimePurchaseOfferDetails?.formattedPrice),
                    IapOffer("gems_large", "1200 Edelsteine (+20 %)", "💰", products["gems_large"]?.oneTimePurchaseOfferDetails?.formattedPrice),
                    IapOffer(GameRules.REMOVE_ADS_PRODUCT, "Werbung entfernen", "🚫", products[GameRules.REMOVE_ADS_PRODUCT]?.oneTimePurchaseOfferDetails?.formattedPrice),
                ),
                onBack = goHome,
                onBuyItem = vm::buy,
                onBuyIap = { billing.launchPurchase(this@MainActivity, it) },
            )
            Screen.BATTLE -> battle?.let { ui ->
                BackHandler { /* Leaving mid-battle only via "Aufgeben". */ }
                BattleScreen(
                    ui = ui,
                    rewardedReady = rewardedReady,
                    onAction = vm::submitAction,
                    onDoubleCoins = { ads.showRewarded(onReward = vm::doubleBattleCoins) },
                    onLeave = {
                        vm.leaveBattle()
                        ads.onBattleFinished(profile.adFree)
                    },
                )
            }
        }
    }

    @Composable
    private fun BannerAd() {
        if (!ads.canRequestAds) return
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BuildConfig.AD_UNIT_BANNER
                    loadAd(AdRequest.Builder().build())
                }
            },
        )
    }
}
