package jp.webpdf.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val AD_UNIT_ID = "ca-app-pub-2076567302105537/6522426990"
    private const val REWARD_AD_UNIT_ID = "ca-app-pub-2076567302105537/7966780302"
    private const val TAG = "WEB_PDF_Ad"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    private var rewardedAd: RewardedAd? = null
    private var isRewardLoading = false

    /** AdMob SDK を初期化して最初の広告をロードする */
    fun initialize(context: Context) {
        MobileAds.initialize(context) {
            Log.d(TAG, "AdMob SDK initialized")
            loadAd(context)
            loadRewardAd(context)
        }
    }

    /** 次回用の広告をプリロードする */
    fun loadAd(context: Context) {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    Log.d(TAG, "インタースティシャル広告のロード完了")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    Log.e(TAG, "広告のロード失敗: ${error.message}")
                }
            }
        )
    }

    /** 次回用のリワード広告をプリロードする */
    fun loadRewardAd(context: Context) {
        if (isRewardLoading || rewardedAd != null) return
        isRewardLoading = true
        RewardedAd.load(
            context,
            REWARD_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardLoading = false
                    Log.d(TAG, "リワード広告のロード完了")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isRewardLoading = false
                    Log.e(TAG, "リワード広告のロード失敗: ${error.message}")
                }
            }
        )
    }

    /**
     * リワード広告を表示する。
     * 広告が準備できていない場合はすぐに onDone を呼び出す（スキップ）。
     * 広告が閉じられた後 / 表示失敗後に onDone が呼ばれる。
     */
    fun showRewardAdIfAvailable(activity: Activity, onDone: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            loadRewardAd(activity)
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardAd(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                loadRewardAd(activity)
                onDone()
            }
        }
        ad.show(activity) { /* リワードアイテム — 本アプリでは報酬処理不要 */ }
    }

    /** 広告が表示可能な状態かどうか */
    val isAdReady: Boolean get() = interstitialAd != null

    /**
     * 広告を表示する。
     * 広告が準備できていない場合はすぐに onDone を呼び出す（スキップ）。
     * 広告が閉じられた後 / 表示失敗後に onDone が呼ばれる。
     */
    fun showAdIfAvailable(activity: Activity, onDone: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            loadAd(activity)
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadAd(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                loadAd(activity)
                onDone()
            }
        }
        ad.show(activity)
    }
}
