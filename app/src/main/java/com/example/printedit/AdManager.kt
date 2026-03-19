package com.example.printedit

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

object AdManager {
    private const val AD_UNIT_ID = "ca-app-pub-2076567302105537/6522426990"
    private const val TAG = "PrintEdit_Ad"

    private var interstitialAd: InterstitialAd? = null
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
