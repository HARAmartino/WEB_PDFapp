package com.example.printedit

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import com.example.printedit.ui.WebViewScreen
import com.example.printedit.ui.HomeScreen
import com.example.printedit.ui.SettingsScreen
import com.example.printedit.ui.theme.PrintEditTheme

/** 型安全な画面遷移用 sealed class */
sealed class Screen {
    object Home : Screen()
    data class Browser(val url: String) : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.enableSlowWholeDocumentDraw()
        super.onCreate(savedInstanceState)
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
