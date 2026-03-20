package com.example.printedit

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    // TODO: リリース前に本番 ID に差し替える: AdMob コンソールでリワード広告ユニットを作成
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // Google公式テスト用リワードID
    private const val TAG = "PrintEdit_Ad"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    /** AdMob SDK を初期化して最初の広告をロードする */
    fun initialize(context: Context) {
        MobileAds.initialize(context) {
            Log.d(TAG, "AdMob SDK initialized")
            loadAd(context)
        }
    }

    /** 次回用の広告をプリロードする */
    fun loadAd(context: Context) {
        if (isLoading || rewardedAd != null) return
        isLoading = true
        RewardedAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    Log.d(TAG, "リワード広告のロード完了")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    Log.e(TAG, "広告のロード失敗: ${error.message}")
                }
            }
        )
    }

    /** 広告が表示可能な状態かどうか */
    val isAdReady: Boolean get() = rewardedAd != null

    /**
     * 広告を表示する。
     * 広告が準備できていない場合はすぐに onDone を呼び出す（スキップ）。
     * 広告が閉じられた後 / 表示失敗後に onDone が呼ばれる。
     * ユーザーが報酬を獲得したかどうかに関わらず onDone は必ず呼ばれる。
     */
    fun showAdIfAvailable(activity: Activity, onDone: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            loadAd(activity)
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                loadAd(activity)
                onDone()
            }
        }
        // onUserEarnedReward: 報酬付与は不要なので何もしない
        ad.show(activity) { /* reward item — unused */ }
    }
}
