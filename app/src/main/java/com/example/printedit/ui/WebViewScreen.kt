package com.example.printedit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ListItem
import com.example.printedit.data.SettingsRepository
import com.example.printedit.data.PresetRepository
import com.example.printedit.data.SavedUrlRepository

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val presetRepository = remember { PresetRepository(context) }
    val savedUrlRepository = remember { SavedUrlRepository(context) }
    
    // State
    var currentUrl by remember { mutableStateOf(url) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var lastInjectedUrl by remember { mutableStateOf<String?>(null) } // Track last URL where JS was injected
    
    // Feature State
    var isMarqueeMode by remember { mutableStateOf(false) }
    var selectedLinksJson by remember { mutableStateOf("[]") }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showPresetSelector by remember { mutableStateOf(false) }
    
    // UI State
    var isFabExpanded by remember { mutableStateOf(false) }
    var pageLoadProgress by remember { mutableStateOf(100) }
    
    // Settings State — read directly from repository so settings changes are reflected immediately
    val menuActions = settingsRepository.menuActions
    
    // Feature Toggles (State for JS)
    var isTextOnly by remember { mutableStateOf(false) }
    var isGrayscale by remember { mutableStateOf(false) }
    var isNoBackground by remember { mutableStateOf(false) }
    var isAdsRemoved by remember { mutableStateOf(false) }
    var isImageAdjusted by remember { mutableStateOf(false) }
    var isRemoveElementMode by remember { mutableStateOf(false) }

    // Navigation Handler
    if (showSettings) {
        BackHandler { showSettings = false }
    } else {
        BackHandler {
            when {
                isFabExpanded -> isFabExpanded = false
                isRemoveElementMode -> {
                    isRemoveElementMode = false
                    webViewRef?.evaluateJavascript("if(window.toggleRemoveElementMode) window.toggleRemoveElementMode(false);", null)
                }
                isMarqueeMode -> {
                    isMarqueeMode = false
                    webViewRef?.evaluateJavascript("if(window.toggleMarqueeMode) window.toggleMarqueeMode(false);", null)
                }
                webViewRef?.canGoBack() == true -> webViewRef?.goBack()
                else -> onExit()
            }
        }
    }

    if (showSettings) {
        SettingsScreen(onNavigateBack = { showSettings = false })
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Column {
                        if (isRemoveElementMode) {
                            TopAppBar(
                                title = { 
                                    Text(
                                        "🗑 要素削除 - 削除したい部分をタップ",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = androidx.compose.ui.graphics.Color.White
                                    ) 
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFFF44336)
                                ),
                                actions = {
                                    TextButton(onClick = { 
                                        isRemoveElementMode = false
                                        webViewRef?.evaluateJavascript("if(window.toggleRemoveElementMode) window.toggleRemoveElementMode(false);", null)
                                    }) {
                                        Text("終了", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                }
                            )
                        } else {
                            CenterAlignedTopAppBar(
                                title = { Text("PrintEdit - Browser") },
                                actions = {
                                    IconButton(onClick = { webViewRef?.reload() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                                    }
                                }
                            )
                        }
                        if (pageLoadProgress < 100) {
                            LinearProgressIndicator(
                                progress = pageLoadProgress / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            ) { padding ->
                // WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.setSupportMultipleWindows(true)
                            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            
                            // Desktop Mode: Switch UserAgent based on setting
                            settings.userAgentString = if (settingsRepository.desktopMode) {
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            } else {
                                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }
                            
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    pageLoadProgress = newProgress
                                }
                                
                                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                    if (!isUserGesture || view == null) return false
                                    // まず hitTestResult で URL を取得
                                    val data = view.hitTestResult?.extra
                                    if (data != null) {
                                        view.loadUrl(data)
                                        return true
                                    }
                                    // hitTestResult が取得できない場合、WebViewTransport で URL をキャプチャ
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    val tempWebView = WebView(view.context)
                                    tempWebView.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString()
                                            if (url != null) {
                                                view.loadUrl(url)
                                            }
                                            tempWebView.destroy()
                                            return true
                                        }
                                    }
                                    transport.webView = tempWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isFabExpanded = false
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val requestUrl = request?.url?.toString() ?: return false
                                    if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                        return false
                                    }
                                    return try {
                                        val intent = Intent.parseUri(requestUrl, Intent.URI_INTENT_SCHEME)
                                        context.startActivity(intent)
                                        true
                                    } catch (e: Exception) {
                                        Log.e("PrintEdit_Intent", "Failed to resolve intent for URL: \$requestUrl", e)
                                        true
                                    }
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // SPA 対応: ページ遷移時に注入フラグをリセット
                                    view?.evaluateJavascript("delete window.injected_GizmodoUtils; delete window.injected_MarqueeSelection; delete window.injected_RemoveElement;", null)
                                    // Inject Core Functionality only if URL changed to avoid redundant injections
                                    if (url != lastInjectedUrl) {
                                        safeInjectJs(view, "GizmodoUtils", removeAdsJs + toggleTextOnlyJs + toggleGrayscaleJs + toggleNoBackgroundJs) 
                                        safeInjectJs(view, "MarqueeSelection", marqueeSelectionJs)
                                        safeInjectJs(view, "RemoveElement", toggleRemoveElementModeJs)
                                        lastInjectedUrl = url
                                    }
                                    
                                    // Reset modes
                                    view?.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly(\${isTextOnly});", null)
                                    view?.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale(\${isGrayscale});", null)
                                    view?.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground(\${isNoBackground});", null)
                                    // Auto-apply settings-based features
                                    if (settingsRepository.aggressiveAdBlock) {
                                        view?.evaluateJavascript("if(window.peManualRemoveAds) window.peManualRemoveAds();", null)
                                    }
                                    if (settingsRepository.autoImageAdjust) {
                                        view?.evaluateJavascript(smartFitImagesJs, null)
                                    }
                                    // Apply menu fix if enabled
                                    if (settingsRepository.menuFixEnabled) {
                                        view?.evaluateJavascript(menuFixJs, null)
                                    }
                                    // Restore interactive modes if they were active
                                    if (isRemoveElementMode) {
                                        view?.evaluateJavascript("if(window.toggleRemoveElementMode) window.toggleRemoveElementMode(true);", null)
                                    }
                                    if (isMarqueeMode) {
                                        view?.evaluateJavascript("if(window.toggleMarqueeMode) window.toggleMarqueeMode(true);", null)
                                    }
                                }
                            }
                            
                            setDownloadListener { downloadUrl, _, _, _, _ ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ダウンローダーを開けませんでした", Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                            loadUrl(currentUrl)
                        }
                    },
                    update = { wv ->
                        webViewRef = wv
                    },
                    modifier = Modifier.padding(padding).fillMaxSize()
                )

                // WebView のクリーンアップ
                DisposableEffect(Unit) {
                    onDispose {
                        webViewRef?.destroy()
                        webViewRef = null
                    }
                }
            }
            
            // Marquee Mode Indicator & Confirm Button
            if (isMarqueeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp), // Space for FAB
                    contentAlignment = Alignment.BottomCenter
                ) {
                     ExtendedFloatingActionButton(
                        onClick = {
                            webViewRef?.evaluateJavascript("window.getMarqueeSelection()") { json ->
                                if (json != null && json != "null" && json != "[]" && json != "\"[]\"") {
                                    var cleanJson = json
                                    if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                                        cleanJson = cleanJson.substring(1, cleanJson.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                    }
                                    selectedLinksJson = cleanJson
                                    isMarqueeMode = false 
                                    webViewRef?.evaluateJavascript("if(window.toggleMarqueeMode) window.toggleMarqueeMode(false);", null)
                                    showBatchDialog = true
                                } else {
                                    Toast.makeText(context, "リンクを選択してください", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Check, "Confirm") },
                        text = { Text("選択を確定") },
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showBatchDialog) {
                BatchLinkPrintDialog(
                    linksJson = selectedLinksJson,
                    onDismiss = { showBatchDialog = false },
                    presetSettings = Triple(isTextOnly, isGrayscale, isNoBackground) 
                )
            }
            
            // Preset Selector Dialog
            if (showPresetSelector) {
                var showSaveDialog by remember { mutableStateOf(false) }
                var presetName by remember { mutableStateOf("") }
                var presets by remember { mutableStateOf(presetRepository.getPresets()) }

                // Save Name Input Dialog
                if (showSaveDialog) {
                    AlertDialog(
                        onDismissRequest = { showSaveDialog = false },
                        title = { Text("プリセット名を入力") },
                        text = {
                            OutlinedTextField(
                                value = presetName,
                                onValueChange = { presetName = it },
                                label = { Text("プリセット名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (presetName.isNotBlank()) {
                                        webViewRef?.evaluateJavascript("window.peGetRemovedSelectors ? window.peGetRemovedSelectors() : '[]'") { selectorsJson ->
                                            var cleanJson = selectorsJson ?: "[]"
                                            if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                                                cleanJson = cleanJson.substring(1, cleanJson.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                            }
                                            
                                            val selectorsList = mutableListOf<String>()
                                            try {
                                                val jsonArray = org.json.JSONArray(cleanJson)
                                                for (i in 0 until jsonArray.length()) {
                                                    selectorsList.add(jsonArray.getString(i))
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("PrintEdit", "Error parsing selectors JSON", e)
                                            }

                                            presetRepository.savePreset(
                                                name = presetName.trim(),
                                                selectors = selectorsList,
                                                imageAdjusted = isImageAdjusted,
                                                textOnly = isTextOnly,
                                                grayscale = isGrayscale,
                                                removeBackground = isNoBackground,
                                                adsRemoved = isAdsRemoved
                                            )
                                            presets = presetRepository.getPresets()
                                            presetName = ""
                                            showSaveDialog = false
                                            Toast.makeText(context, "プリセットを保存しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = presetName.isNotBlank()
                            ) {
                                Text("保存")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSaveDialog = false }) {
                                Text("キャンセル")
                            }
                        }
                    )
                }

                AlertDialog(
                    onDismissRequest = { showPresetSelector = false },
                    title = { Text("プリセット") },
                    text = {
                        Column {
                            // Save current settings button
                            OutlinedButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("現在の設定を保存")
                            }

                            if (presets.isEmpty()) {
                                Text(
                                    "プリセットがありません",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(presets) { preset ->
                                        ListItem(
                                            headlineContent = { Text(preset.name) },
                                            supportingContent = {
                                                val tags = mutableListOf<String>()
                                                if (preset.adsRemoved) tags.add("広告削除")
                                                if (preset.textOnly) tags.add("文字のみ")
                                                if (preset.grayscale) tags.add("白黒")
                                                if (preset.removeBackground) tags.add("背景なし")
                                                if (preset.imageAdjusted) tags.add("画像調整")
                                                if (tags.isEmpty()) tags.add("設定なし")
                                                Text(tags.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                                            },
                                            leadingContent = { Icon(Icons.Filled.Settings, null) },
                                            trailingContent = {
                                                IconButton(onClick = {
                                                    presetRepository.deletePreset(preset.name)
                                                    presets = presetRepository.getPresets()
                                                    Toast.makeText(context, "「${preset.name}」を削除しました", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "削除",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            },
                                            modifier = Modifier.clickable {
                                                isTextOnly = preset.textOnly
                                                isGrayscale = preset.grayscale
                                                isNoBackground = preset.removeBackground
                                                isAdsRemoved = preset.adsRemoved
                                                isImageAdjusted = preset.imageAdjusted

                                                webViewRef?.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly($isTextOnly);", null)
                                                webViewRef?.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale($isGrayscale);", null)
                                                webViewRef?.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground($isNoBackground);", null)
                                                if (preset.adsRemoved) {
                                                    webViewRef?.evaluateJavascript("if(window.peManualRemoveAds) window.peManualRemoveAds();", null)
                                                }
                                                if (preset.imageAdjusted) {
                                                    webViewRef?.evaluateJavascript(smartFitImagesJs, null)
                                                }

                                                if (preset.selectors.isNotEmpty()) {
                                                    val selectorsJson = org.json.JSONArray(preset.selectors).toString()
                                                    val escapedJson = selectorsJson.replace("'", "\\'")
                                                    webViewRef?.evaluateJavascript("if(window.peApplyRemovedSelectors) window.peApplyRemovedSelectors('${escapedJson}');", null)
                                                }

                                                showPresetSelector = false
                                                Toast.makeText(context, "プリセット「${preset.name}」を適用しました", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPresetSelector = false }) {
                            Text("閉じる")
                        }
                    }
                )
            }

            // FAB Menu Overlay
            if (isFabExpanded) {
                // Dimmer background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isFabExpanded = false }
                )
                
                // Vertical FAB Menu
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 88.dp), // Positioned above the main FAB
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    
                    // ホーム
                    MiniFabItem(icon = Icons.Filled.Home, label = "ホーム") {
                        isFabExpanded = false
                        onExit()
                    }
                    
                    // 印刷
                    MiniFabItem(icon = Icons.Filled.Print, label = "印刷") {
                        isFabExpanded = false
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                        val adapter = webViewRef?.createPrintDocumentAdapter("PrintEdit_Page")
                        if (printManager != null && adapter != null) {
                            val printAttributes = android.print.PrintAttributes.Builder()
                                .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                                .build()
                            printManager.print("PrintEdit_Page", adapter, printAttributes)
                        }
                    }
                    
                    // URLを保存
                    MiniFabItem(icon = Icons.Filled.Bookmark, label = "URLを保存") {
                        isFabExpanded = false
                        webViewRef?.evaluateJavascript("document.title") { rawTitle ->
                            var title = rawTitle ?: ""
                            if (title.startsWith("\"") && title.endsWith("\"")) {
                                title = title.substring(1, title.length - 1)
                            }
                            val currentPageUrl = webViewRef?.url ?: url
                            savedUrlRepository.save(currentPageUrl, title)
                            Toast.makeText(context, "URLを保存しました", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 広告削除
                    if (menuActions.contains("action_remove_ads")) {
                        MiniFabItem(icon = Icons.Filled.Delete, label = "広告削除") {
                            isFabExpanded = false
                            isAdsRemoved = true
                            webViewRef?.evaluateJavascript("if(window.peManualRemoveAds) window.peManualRemoveAds();", null)
                            Toast.makeText(context, "広告削除を実行しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // 記事下部を削除
                    MiniFabItem(icon = Icons.Filled.CleaningServices, label = "記事下部を削除") {
                        isFabExpanded = false
                        webViewRef?.evaluateJavascript(removeRelatedArticlesJs, null)
                        Toast.makeText(context, "関連記事・フッター等を削除しました", Toast.LENGTH_SHORT).show()
                    }

                    // 要素を削除
                    if (menuActions.contains("action_remove_elements")) {
                        MiniFabItem(
                            icon = Icons.Filled.Clear,
                            label = if (isRemoveElementMode) "要素削除モードを終了" else "要素を削除"
                        ) {
                            isFabExpanded = false
                            isRemoveElementMode = !isRemoveElementMode
                            webViewRef?.evaluateJavascript("if(window.toggleRemoveElementMode) window.toggleRemoveElementMode($isRemoveElementMode);", null)
                            if (isRemoveElementMode) {
                                Toast.makeText(context, "削除したい要素をタップしてください", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // 元に戻す (Undo)
                    if (menuActions.contains("action_undo")) {
                        MiniFabItem(icon = Icons.Filled.Undo, label = "元に戻す") {
                            isFabExpanded = false
                            webViewRef?.evaluateJavascript("if(window.peUndoLastAction) window.peUndoLastAction();", null)
                            Toast.makeText(context, "一つ前の状態に戻しました", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // ドラッグ選択
                    MiniFabItem(icon = Icons.Filled.SelectAll, label = "ドラッグ選択") {
                        isFabExpanded = false
                        isMarqueeMode = true
                        webViewRef?.evaluateJavascript("if(window.toggleMarqueeMode) window.toggleMarqueeMode(true);", null)
                        Toast.makeText(context, "長押ししてからドラッグで選択", Toast.LENGTH_SHORT).show()
                    }

                    // 範囲選択で印刷
                    MiniFabItem(icon = Icons.Filled.ContentCut, label = "範囲選択で印刷") {
                        isFabExpanded = false
                        webViewRef?.evaluateJavascript(getLinksInSelectionJs) { json ->
                            if (json != null && json != "null" && json != "[]" && json != "\"[]\"") {
                                var cleanJson = json
                                if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                                    cleanJson = cleanJson.substring(1, cleanJson.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                }
                                selectedLinksJson = cleanJson
                                showBatchDialog = true
                            } else {
                                Toast.makeText(context, "テキストを範囲選択してからこのボタンを押してください", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // プリセット
                    if (menuActions.contains("action_presets")) {
                        MiniFabItem(icon = Icons.Filled.List, label = "プリセット") {
                            isFabExpanded = false
                            showPresetSelector = true
                        }
                    }

                    // 画像調整
                    if (menuActions.contains("action_adjust_images")) {
                        MiniFabItem(icon = Icons.Filled.Image, label = "画像調整") {
                            isFabExpanded = false
                            isImageAdjusted = true
                            webViewRef?.evaluateJavascript(smartFitImagesJs, null)
                            Toast.makeText(context, "画像を調整しました", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 文字のみ表示
                    if (menuActions.contains("action_text_only")) {
                        MiniFabItem(
                            icon = Icons.Filled.Description,
                            label = if (isTextOnly) "文字のみ表示を解除" else "文字のみ表示"
                        ) {
                            isFabExpanded = false
                            isTextOnly = !isTextOnly
                            webViewRef?.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly($isTextOnly);", null)
                        }
                    }

                    // 白黒モード
                    if (menuActions.contains("action_grayscale")) {
                        MiniFabItem(
                            icon = Icons.Filled.Contrast,
                            label = if (isGrayscale) "白黒モードを解除" else "白黒モード"
                        ) {
                            isFabExpanded = false
                            isGrayscale = !isGrayscale
                            webViewRef?.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale($isGrayscale);", null)
                        }
                    }

                    // 背景削除
                    if (menuActions.contains("action_remove_background")) {
                        MiniFabItem(
                            icon = Icons.Filled.Wallpaper,
                            label = if (isNoBackground) "背景を表示" else "背景を削除"
                        ) {
                            isFabExpanded = false
                            isNoBackground = !isNoBackground
                            webViewRef?.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground($isNoBackground);", null)
                        }
                    }

                    // 設定
                    MiniFabItem(icon = Icons.Filled.Settings, label = "設定") {
                        isFabExpanded = false
                        showSettings = true
                    }
                }
            }

            // Main FAB (bottom-end)
            FloatingActionButton(
                onClick = { isFabExpanded = !isFabExpanded },
                containerColor = if (isFabExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isFabExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isFabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = "Menu"
                )
            }
        }
    }
}

@Composable
fun MiniFabItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = label,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}


fun safeInjectJs(wv: WebView?, jsName: String, jsCode: String) {
    Log.d("PrintEdit", "Attempting to inject: $jsName")
    val wrappedJs = """
        (function() {
            try {
                if (window.injected_$jsName) { console.log('Already injected: $jsName'); return; }
                window.injected_$jsName = true;
                
                console.log('PrintEdit: Injecting $jsName...');
                $jsCode
                console.log('PrintEdit: Injected $jsName SUCCESS');
            } catch (e) {
                console.error('PrintEdit: Injection FAILED for $jsName: ' + e.message);
            }
        })();
    """.trimIndent()
    wv?.evaluateJavascript(wrappedJs) { res ->
        Log.d("PrintEdit", "Inject Result for $jsName: $res")
    }
}
