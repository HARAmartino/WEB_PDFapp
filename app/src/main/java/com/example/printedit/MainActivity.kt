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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AdMob SDK を初期化（広告プリロードも開始）
        AdManager.initialize(this)

        // Handle incoming SEND intent
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val repo = SavedUrlRepository(this)
                // Extract URL from sharedText (useful if format is "Title URL")
                val extractedUrl = sharedText.split("\\s+".toRegex()).firstOrNull { it.startsWith("http") } ?: sharedText
                repo.save(extractedUrl, "共有されたリンク")
                Toast.makeText(this, "URLを保存しました", Toast.LENGTH_SHORT).show()
                // Finish early if you don't want to open the UI, 
                // but let's just show HomeScreen so the user can verify.
            }
        }

        setContent {
            PrintEditTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                    when (val screen = currentScreen) {
                        is Screen.Home -> HomeScreen(
                            onSearch = { query ->
                                currentScreen = Screen.Browser(query)
                            },
                            onOpenSettings = { currentScreen = Screen.Settings }
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
}
