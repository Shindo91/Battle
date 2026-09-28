package com.shindo91.trainerbattle.monetization

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.shindo91.trainerbattle.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob integration: GDPR consent (UMP), rewarded ads (player opts in for a bonus) and
 * occasional interstitials between battles. Interstitials are skipped for ad-free players.
 */
class AdsManager(private val activity: Activity) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)
    private val initialized = AtomicBoolean(false)

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var battlesSinceInterstitial = 0

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady

    /** Call once from onCreate. Shows the consent form where legally required (EU/UK). */
    fun start() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    if (consentInformation.canRequestAds()) initializeAds()
                }
            },
            { error -> Log.w(TAG, "Consent info update failed: ${error.message}") },
        )
        // Consent from a previous session lets us start loading right away.
        if (consentInformation.canRequestAds()) initializeAds()
    }

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
        }
    }

    private fun initializeAds() {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(activity) {
            loadRewarded()
            loadInterstitial()
        }
    }

    private fun loadRewarded() {
        RewardedAd.load(activity, BuildConfig.AD_UNIT_REWARDED, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded failed to load: ${error.message}")
                    rewardedAd = null
                    _rewardedReady.value = false
                }
            })
    }

    private fun loadInterstitial() {
        InterstitialAd.load(activity, BuildConfig.AD_UNIT_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                }
            })
    }

    /** Shows a rewarded ad; [onReward] runs only if the user watched it to the end. */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit = {}) {
        val ad = rewardedAd ?: run { onUnavailable(); loadRewarded(); return }
        rewardedAd = null
        _rewardedReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadRewarded()
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = loadRewarded()
        }
        ad.show(activity) { onReward() }
    }

    /** Call after each battle; shows an interstitial every [INTERSTITIAL_EVERY] battles. */
    fun onBattleFinished(adFree: Boolean) {
        if (adFree) return
        battlesSinceInterstitial++
        if (battlesSinceInterstitial < INTERSTITIAL_EVERY) return
        val ad = interstitialAd ?: run { loadInterstitial(); return }
        battlesSinceInterstitial = 0
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadInterstitial()
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = loadInterstitial()
        }
        ad.show(activity)
    }

    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    private companion object {
        const val TAG = "AdsManager"
        const val INTERSTITIAL_EVERY = 3
    }
}
