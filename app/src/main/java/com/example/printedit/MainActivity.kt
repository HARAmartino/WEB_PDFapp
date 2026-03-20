package com.example.printedit

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import com.example.printedit.ui.WebViewScreen
import com.example.printedit.ui.HomeScreen
import com.example.printedit.ui.SettingsScreen
import com.example.printedit.ui.theme.PrintEditTheme
import com.example.printedit.data.SavedUrlRepository

/** 型安全な画面遷移用 sealed class */
sealed class Screen {
    object Home : Screen()
    data class Browser(val url: String) : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    // Compose 層に新しい共有インテントを伝えるための State
    // (onNewIntent はコンポーザブルの外で呼ばれるため、State 経由でブリッジする)
    private val pendingSharedUrl = mutableStateOf<String?>(null)
    // HomeScreen の savedUrls を強制リフレッシュするためのカウンター
    private val homeRefreshCounter = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AdMob SDK を初期化（広告プリロードも開始）
        AdManager.initialize(this)

        // 起動時の共有インテントを処理（onCreate では pendingSharedUrl を使わず直接保存のみ）
        handleSendIntent(intent, notifyCompose = false)

        setContent {
            PrintEditTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                    // onNewIntent で届いた URL をホーム画面に反映する
                    val shared by pendingSharedUrl
                    val refreshCount by homeRefreshCounter
                    LaunchedEffect(shared) {
                        if (shared != null) {
                            // カウンターをインクリメントして HomeScreen の savedUrls を強制更新
                            homeRefreshCounter.value++
                            // ホーム画面へ遷移（既に Home の場合もカウンター変化で更新される）
                            currentScreen = Screen.Home
                            pendingSharedUrl.value = null
                        }
                    }

                    when (val screen = currentScreen) {
                        is Screen.Home -> HomeScreen(
                            onSearch = { query ->
                                currentScreen = Screen.Browser(query)
                            },
                            onOpenSettings = { currentScreen = Screen.Settings },
                            refreshKey = refreshCount
                        )
                        is Screen.Browser -> WebViewScreen(
                            url = screen.url,
                            onExit = { currentScreen = Screen.Home }
                        )
                        is Screen.Settings -> SettingsScreen(
                            onNavigateBack = { currentScreen = Screen.Home }
                        )
                    }
                }
            }
        }
    }

    /**
     * アプリが既に起動中に別アプリから URL を共有されたときに呼ばれる。
     * onCreate では処理されないため、onNewIntent でも同じロジックを実行する。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent, notifyCompose = true)
    }

    /**
     * ACTION_SEND インテントを処理して URL を保存する。
     * [notifyCompose] が true のとき Compose 層にリフレッシュを通知する
     * (onNewIntent の場合のみ: onCreate 時は setContent 前なので不要)
     */
    private fun handleSendIntent(intent: Intent?, notifyCompose: Boolean = true) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val repo = SavedUrlRepository(this)
                // "タイトル URL" のような形式からURLを抽出する
                val extractedUrl = sharedText.split("\\s+".toRegex())
                    .firstOrNull { it.startsWith("http") } ?: sharedText
                repo.save(extractedUrl, "共有されたリンク")
                Toast.makeText(this, "URLを保存しました", Toast.LENGTH_SHORT).show()
                if (notifyCompose) {
                    // Compose 層にホーム画面へ戻り savedUrls を更新させる
                    pendingSharedUrl.value = extractedUrl
                }
            }
        }
    }
}
