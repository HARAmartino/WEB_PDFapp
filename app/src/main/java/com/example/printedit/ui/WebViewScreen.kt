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
import android.webkit.WebResourceResponse
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.printedit.AdManager
import com.example.printedit.data.SettingsRepository
import com.example.printedit.data.PresetRepository
import com.example.printedit.data.SavedUrlRepository
import com.example.printedit.data.SiteProfile
import com.example.printedit.data.SiteProfileRepository
import com.example.printedit.data.UserAgentMode

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val presetRepository = remember { PresetRepository(context) }
    val savedUrlRepository = remember { SavedUrlRepository(context) }
    val siteProfileRepository = remember { SiteProfileRepository(context) }

    // サイトプロファイル用: 現在のページホスト (background thread から AtomicRef で安全にアクセス)
    val currentSiteHostRef = remember { java.util.concurrent.atomic.AtomicReference("") }
    val isAdsRemovedRef = remember { java.util.concurrent.atomic.AtomicBoolean(settingsRepository.aggressiveAdBlock) }
    var currentSiteHost by remember { mutableStateOf("") }
    var showSiteProfileDialog by remember { mutableStateOf(false) }
    
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
    var isAdsRemoved by remember { mutableStateOf(settingsRepository.aggressiveAdBlock) }
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
                                navigationIcon = {
                                    IconButton(onClick = onExit) {
                                        Icon(Icons.Filled.Home, contentDescription = "Home")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { webViewRef?.reload() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                                    }
                                }
                            )
                        }
                        if (pageLoadProgress < 100) {
                            @Suppress("DEPRECATION")
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
                            if (0 != (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)) {
                                android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // databaseEnabled は API 23+ で Web SQL が廃止されたため不要
                            settings.allowContentAccess = true
                            settings.allowFileAccess = false
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = false
                            // COMPATIBILITY_MODE: HTTPS ページに HTTP リソースを含むサイトに対応しつつ
                            // ブラウザが元々ブロックしていたものは引き続きブロック
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.setSupportMultipleWindows(true)
                            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            settings.loadsImagesAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.textZoom = 100  // システムフォントサイズ設定の影響を受けないよう固定
                            settings.useWideViewPort = true
                            // loadWithOverviewMode=true はページ全体を縮小表示するため
                            // IntersectionObserver の「ビューポート内」判定が狂い lazy 画像が読み込まれない原因となる
                            settings.loadWithOverviewMode = false
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            // レンダラーが非表示中にメモリ不足で終了した場合、アプリがクラッシュしないよう許可
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                            }

                            // Desktop Mode: Switch UserAgent based on setting
                            settings.userAgentString = if (settingsRepository.desktopMode) DESKTOP_UA else MOBILE_UA
                            
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    pageLoadProgress = newProgress
                                }

                                // カメラ・マイク等の権限リクエストはすべて拒否
                                override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                                    request.deny()
                                }

                                // 位置情報リクエストは拒否
                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: android.webkit.GeolocationPermissions.Callback?
                                ) {
                                    callback?.invoke(origin, false, false)
                                }

                                // デバッグビルドのみ JS コンソールログを Logcat に出力
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                                    if (0 != (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)) {
                                        Log.d("WebConsole", "${consoleMessage.message()} [${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}]")
                                    }
                                    return true
                                }
                                
                                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                    if (!isUserGesture || view == null || resultMsg == null) return false
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    // 一時 WebView で target="_blank" / window.open() の URL をキャプチャして
                                    // メイン WebView にリダイレクトする。
                                    // 注: hitTestResult.extra で URL を取得して return true だけ返すと
                                    //     sendToTarget() が呼ばれずレンダラーの pending handle が永遠に残るため
                                    //     必ず transport を設定してから sendToTarget() を呼ぶ。
                                    val tempWebView = WebView(view.context)
                                    var urlCaptured = false
                                    tempWebView.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                            if (!urlCaptured) {
                                                urlCaptured = true
                                                request?.url?.toString()?.let { view.loadUrl(it) }
                                                v?.destroy()
                                            }
                                            return true
                                        }
                                    }
                                    transport.webView = tempWebView
                                    resultMsg.sendToTarget()
                                    // フォールバック: URL が捕捉できなかった場合のみ破棄（3秒）
                                    view.postDelayed({
                                        if (!urlCaptured) try { tempWebView.destroy() } catch (_: Exception) {}
                                    }, 3000)
                                    return true
                                }
                            }
                            
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val host = request?.url?.host
                                        ?.removePrefix("www.")
                                        ?.lowercase() ?: return null
                                    // サイトプロファイルで広告ブロック無効 (ホワイトリスト) の場合はスキップ
                                    val currentPageHost = currentSiteHostRef.get()
                                    if (currentPageHost.isNotEmpty() &&
                                        siteProfileRepository.getProfile(currentPageHost)?.disableAdBlock == true) {
                                        return null
                                    }
                                    // 「強力な広告ブロック」または「広告を削除」が有効な場合にブロック
                                    if ((settingsRepository.aggressiveAdBlock || isAdsRemovedRef.get()) &&
                                        (MAJOR_AD_NETWORKS.contains(host) || MAJOR_AD_NETWORKS.any { host.endsWith(".$it") }
                                        || AD_HOSTS.contains(host) || AD_HOSTS.any { host.endsWith(".$it") })) {
                                        return emptyResponse()
                                    }
                                    return null
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isFabExpanded = false
                                    lastInjectedUrl = null

                                    // 現在のページホストを記録 (UI 表示 & background thread 用)
                                    val host = url?.let {
                                        android.net.Uri.parse(it).host?.removePrefix("www.")?.lowercase()
                                    } ?: ""
                                    currentSiteHostRef.set(host)
                                    currentSiteHost = host

                                    // サイトプロファイルに基づく WebView 設定の適用
                                    if (host.isNotEmpty() && view != null) {
                                        val profile = siteProfileRepository.getProfile(host)

                                        // UA 上書き: 変更が必要なら即座に適用してリロード（1回のみ）
                                        val globalUA = if (settingsRepository.desktopMode) DESKTOP_UA else MOBILE_UA
                                        val targetUA = when (profile?.userAgent) {
                                            UserAgentMode.MOBILE   -> MOBILE_UA
                                            UserAgentMode.DESKTOP  -> DESKTOP_UA
                                            else -> globalUA
                                        }
                                        if (view.settings.userAgentString != targetUA) {
                                            view.settings.userAgentString = targetUA
                                            view.reload() // onPageStarted が再発火する際には UA が一致しているためループしない
                                            return
                                        }

                                        // 自動再生設定 (次のメディア操作から有効)
                                        view.settings.mediaPlaybackRequiresUserGesture = !(profile?.allowAutoplay ?: false)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val requestUrl = request?.url?.toString() ?: return false
                                    // http/https は WebView 自身で処理
                                    if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                        return false
                                    }
                                    // blob: / data: はページ内部リソース（PDF表示・Canvas出力等）— WebView で処理
                                    if (requestUrl.startsWith("blob:") || requestUrl.startsWith("data:")) {
                                        return false
                                    }
                                    // 安全なスキームのみ外部アプリへ渡す（tel/mailto/market 等）
                                    // intent:// URI はそのままパースすると任意 Activity を起動できるため
                                    // コンポーネント・パッケージ指定を除去してから渡す
                                    val safeSchemes = setOf("tel", "mailto", "market", "intent")
                                    val scheme = request.url?.scheme?.lowercase() ?: return true
                                    if (scheme !in safeSchemes) return true
                                    return try {
                                        val intent = Intent.parseUri(requestUrl, Intent.URI_INTENT_SCHEME).apply {
                                            component = null   // 特定コンポーネントへの直接起動を禁止
                                            selector = null    // セレクター経由の迂回を禁止
                                        }
                                        context.startActivity(intent)
                                        true
                                    } catch (e: Exception) {
                                        Log.e("PrintEdit_Intent", "Failed to resolve intent for URL: $requestUrl", e)
                                        true
                                    }
                                }

                                // SSL エラー発生時はロードをキャンセル（proceed() は呼ばない）
                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: android.webkit.SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    handler?.cancel()
                                    Log.w("PrintEdit_SSL", "SSL error: ${error?.primaryError}")
                                }

                                // WebView レンダラーがクラッシュ／OOM で終了した場合のリカバリ
                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    Log.e("PrintEdit_Renderer", "Renderer gone. crashed=${detail?.didCrash()}")
                                    // 親 Activity を再起動して回復
                                    (context as? android.app.Activity)?.recreate()
                                    return true
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // http/https 以外 (about:blank, error pages, data:) は処理しない
                                    if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) return
                                    // iframe の onPageFinished を除外: view.url はメインフレームの URL を保持するため
                                    // url != view.url の場合は iframe のコールバックとみなしてスキップ
                                    if (url != view?.url) return
                                    // SPA 対応: ページ遷移時に注入フラグをリセット
                                    // _peForceLoadRunning も削除: SPA ルート変更時に画像強制ロードが再実行されるようにする
                                    view.evaluateJavascript("delete window.injected_GizmodoUtils; delete window.injected_MarqueeSelection; delete window.injected_RemoveElement; delete window.injected_MenuFix; delete window._peForceLoadRunning;", null)
                                    // Inject Core Functionality (lastInjectedUrl は onPageStarted でリセット済み)
                                    if (url != lastInjectedUrl) {
                                        safeInjectJs(view, "GizmodoUtils", removeAdsJs + toggleTextOnlyJs + toggleGrayscaleJs + toggleNoBackgroundJs) 
                                        safeInjectJs(view, "MarqueeSelection", marqueeSelectionJs)
                                        safeInjectJs(view, "RemoveElement", toggleRemoveElementModeJs)
                                        lastInjectedUrl = url
                                    }
                                    
                                    // Reset modes (view は上の null チェックで非 null 確定)
                                    view.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly($isTextOnly);", null)
                                    view.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale($isGrayscale);", null)
                                    view.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground($isNoBackground);", null)
                                    // 広告ブロックが有効な場合は DOM クリーンアップも実行（空白スキマ除去）
                                    if (isAdsRemoved || settingsRepository.aggressiveAdBlock) {
                                        view.evaluateJavascript("if(window.peToggleRemoveAds) window.peToggleRemoveAds(true);", null)
                                    }
                                    // サイトプロファイル取得（onPageFinished 内で一度だけ取得）
                                    val siteHost = android.net.Uri.parse(url).host
                                        ?.removePrefix("www.")?.lowercase() ?: ""
                                    val siteProfile = siteProfileRepository.getProfile(siteHost)

                                    // 遅延読み込み画像を強制ロード（skipLazyLoader が有効なサイトは除く）
                                    if (siteProfile?.skipLazyLoader != true) {
                                        view.postDelayed({
                                            view.evaluateJavascript(forceLoadLazyImagesJs, null)
                                        }, 500)
                                    }
                                    if (settingsRepository.autoImageAdjust) {
                                        view.evaluateJavascript(smartFitImagesJs, null)
                                    }
                                    // Apply menu fix if enabled（グローバル設定 or サイトプロファイルで強制）
                                    if (settingsRepository.menuFixEnabled || siteProfile?.forceMenuFix == true) {
                                        safeInjectJs(view, "MenuFix", menuFixJs)
                                    }
                                    // Restore interactive modes if they were active
                                    if (isRemoveElementMode) {
                                        view.evaluateJavascript("if(window.toggleRemoveElementMode) window.toggleRemoveElementMode(true);", null)
                                    }
                                    if (isMarqueeMode) {
                                        view.evaluateJavascript("if(window.toggleMarqueeMode) window.toggleMarqueeMode(true);", null)
                                    }

                                    // ── サイトプロファイル: カスタム CSS / JS の注入 ──────────────────
                                    if (siteProfile != null) {
                                        if (siteProfile.customCss.isNotBlank()) {
                                            val b64 = android.util.Base64.encodeToString(
                                                siteProfile.customCss.toByteArray(Charsets.UTF_8),
                                                android.util.Base64.NO_WRAP)
                                            // Base64 経由でエスケープ問題を回避
                                            view.evaluateJavascript("""
                                                (function(){var s=document.getElementById('pe-site-css');
                                                if(!s){s=document.createElement('style');s.id='pe-site-css';
                                                (document.head||document.documentElement).appendChild(s);}
                                                s.textContent=atob('$b64');})();
                                            """.trimIndent(), null)
                                        }
                                        if (siteProfile.customJs.isNotBlank()) {
                                            val b64 = android.util.Base64.encodeToString(
                                                siteProfile.customJs.toByteArray(Charsets.UTF_8),
                                                android.util.Base64.NO_WRAP)
                                            view.evaluateJavascript(
                                                "(function(){try{eval(atob('$b64'));}catch(e){console.error('PE site JS:',e);}})();",
                                                null)
                                        }
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

                            // Cookie を有効化（ログイン維持・サードパーティ認証等に必要）
                            android.webkit.CookieManager.getInstance().let { cm ->
                                cm.setAcceptCookie(true)
                                cm.setAcceptThirdPartyCookies(this, true) // this = WebView instance
                            }

                            loadUrl(currentUrl)
                        }
                    },
                    update = { wv ->
                        // webViewRef の更新のみ。loadUrl はここで呼ばない。
                        // update は state 変化（progress、FAB 開閉等）のたびに呼ばれるため、
                        // ここで loadUrl すると user がナビゲートするたびに初期 URL にリセットされる。
                        webViewRef = wv
                    },
                    modifier = Modifier.padding(padding).fillMaxSize()
                )

                // WebView のクリーンアップ
                DisposableEffect(Unit) {
                    onDispose {
                        val wv = webViewRef
                        webViewRef = null
                        wv?.let {
                            it.stopLoading()
                            it.loadUrl("about:blank") // リソース解放を促す
                            it.clearHistory()
                            android.webkit.CookieManager.getInstance().flush() // セッションを永続化
                            it.destroy()
                        }
                    }
                }

                // WebView ライフサイクル連携: アプリがバックグラウンドに入ったときにWebViewを一時停止する
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                            Lifecycle.Event.ON_PAUSE  -> webViewRef?.onPause()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                                                isAdsRemovedRef.set(preset.adsRemoved)
                                                isImageAdjusted = preset.imageAdjusted

                                                // Bug #3 fix: preset の値を直接使用（Compose state は recomposition 後に反映されるため）
                                                webViewRef?.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly(${preset.textOnly});", null)
                                                webViewRef?.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale(${preset.grayscale});", null)
                                                webViewRef?.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground(${preset.removeBackground});", null)
                                                if (preset.adsRemoved) {
                                                    webViewRef?.evaluateJavascript("if(window.peToggleRemoveAds) window.peToggleRemoveAds(true);", null)
                                                }
                                                if (preset.imageAdjusted) {
                                                    webViewRef?.evaluateJavascript(smartFitImagesJs, null)
                                                }

                                                if (preset.selectors.isNotEmpty()) {
                                                    // JSON は有効な JS 式なので文字列ラップ不要。エスケープ問題を完全に回避する
                                                    val selectorsJson = org.json.JSONArray(preset.selectors).toString()
                                                    webViewRef?.evaluateJavascript("if(window.peApplyRemovedSelectors) window.peApplyRemovedSelectors($selectorsJson);", null)
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
                        val activity = context as? android.app.Activity
                        val doPrint = {
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
                        // 広告が準備できていれば全画面広告を表示してから印刷ダイアログを開く
                        if (activity != null) {
                            AdManager.showAdIfAvailable(activity, doPrint)
                        } else {
                            doPrint()
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

                    // 広告削除 (Toggle)
                    if (menuActions.contains("action_remove_ads")) {
                        val adLabel = if (isAdsRemoved) "広告削除を停止" else "広告削除"
                        val adIcon = if (isAdsRemoved) Icons.Filled.Block else Icons.Filled.Delete
                        MiniFabItem(icon = adIcon, label = adLabel) {
                            isFabExpanded = false
                            val newVal = !isAdsRemoved
                            isAdsRemoved = newVal
                            isAdsRemovedRef.set(newVal)
                            if (newVal) {
                                webViewRef?.evaluateJavascript("if(window.peToggleRemoveAds) window.peToggleRemoveAds(true);", null)
                                Toast.makeText(context, "広告削除を有効にしました", Toast.LENGTH_SHORT).show()
                            } else {
                                webViewRef?.evaluateJavascript("if(window.peToggleRemoveAds) window.peToggleRemoveAds(false);", null)
                                Toast.makeText(context, "広告削除を停止しました（再読込します）", Toast.LENGTH_SHORT).show()
                                webViewRef?.reload()
                            }
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
                            val newMode = !isRemoveElementMode
                            isRemoveElementMode = newMode
                            webViewRef?.evaluateJavascript("if(window.toggleRemoveElementMode) window.toggleRemoveElementMode($newMode);", null)
                            if (newMode) {
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
                            val newVal = !isTextOnly
                            isTextOnly = newVal
                            webViewRef?.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly($newVal);", null)
                        }
                    }

                    // 白黒モード
                    if (menuActions.contains("action_grayscale")) {
                        MiniFabItem(
                            icon = Icons.Filled.Contrast,
                            label = if (isGrayscale) "白黒モードを解除" else "白黒モード"
                        ) {
                            isFabExpanded = false
                            val newVal = !isGrayscale
                            isGrayscale = newVal
                            webViewRef?.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale($newVal);", null)
                        }
                    }

                    // 背景削除
                    if (menuActions.contains("action_remove_background")) {
                        MiniFabItem(
                            icon = Icons.Filled.Wallpaper,
                            label = if (isNoBackground) "背景を表示" else "背景を削除"
                        ) {
                            isFabExpanded = false
                            val newVal = !isNoBackground
                            isNoBackground = newVal
                            webViewRef?.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground($newVal);", null)
                        }
                    }

                    // このサイトを設定
                    MiniFabItem(icon = Icons.Filled.Tune, label = "このサイトを設定") {
                        isFabExpanded = false
                        val host = webViewRef?.url?.let {
                            android.net.Uri.parse(it).host?.removePrefix("www.")?.lowercase()
                        }.orEmpty()
                        if (host.isNotEmpty()) {
                            currentSiteHost = host
                            showSiteProfileDialog = true
                        } else {
                            Toast.makeText(context, "ページを読み込んでから設定してください", Toast.LENGTH_SHORT).show()
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

            // Settings overlay — rendered on top of browser so WebView is never destroyed
            if (showSettings) {
                SettingsScreen(onNavigateBack = { showSettings = false })
            }

            // サイト最適化ダイアログ
            if (showSiteProfileDialog && currentSiteHost.isNotEmpty()) {
                SiteProfileDialog(
                    domain = currentSiteHost,
                    siteProfileRepository = siteProfileRepository,
                    onDismiss = { showSiteProfileDialog = false },
                    onSaveAndReload = {
                        showSiteProfileDialog = false
                        webViewRef?.reload()
                    }
                )
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


// ─────────────────────────────────────────────────────────────────────────────
// サイト最適化ダイアログ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SiteProfileDialog(
    domain: String,
    siteProfileRepository: com.example.printedit.data.SiteProfileRepository,
    onDismiss: () -> Unit,
    onSaveAndReload: () -> Unit,
) {
    val existing = remember(domain) { siteProfileRepository.getProfile(domain) }
    var uaMode       by remember(existing) { mutableStateOf(existing?.userAgent    ?: UserAgentMode.GLOBAL) }
    var forceMenuFix by remember(existing) { mutableStateOf(existing?.forceMenuFix ?: false) }
    var skipLazy     by remember(existing) { mutableStateOf(existing?.skipLazyLoader ?: false) }
    var allowAuto    by remember(existing) { mutableStateOf(existing?.allowAutoplay  ?: false) }
    var disableAds   by remember(existing) { mutableStateOf(existing?.disableAdBlock ?: false) }
    var css          by remember(existing) { mutableStateOf(existing?.customCss     ?: "") }
    var js           by remember(existing) { mutableStateOf(existing?.customJs      ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("サイト設定") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                // ドメイン表示
                Text(
                    text = domain,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // ── ユーザーエージェント ──────────────────────────────────────
                Text(
                    "ユーザーエージェント",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                UserAgentMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uaMode = mode }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = uaMode == mode, onClick = { uaMode = mode })
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                @Suppress("DEPRECATION")
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // ── 各種トグル ───────────────────────────────────────────────
                SiteProfileSwitch(
                    label = "メニュー表示の修正を強制",
                    description = "グローバル設定に関わらずこのサイトでメニュー修正を適用",
                    checked = forceMenuFix
                ) { forceMenuFix = it }
                SiteProfileSwitch(
                    label = "遅延画像読み込みをスキップ",
                    description = "IO パッチが悪影響を及ぼすサイト向け",
                    checked = skipLazy
                ) { skipLazy = it }
                SiteProfileSwitch(
                    label = "メディアの自動再生を許可",
                    description = "動画・音声がタップなしで再生されるようになる",
                    checked = allowAuto
                ) { allowAuto = it }
                SiteProfileSwitch(
                    label = "広告ブロックを無効にする",
                    description = "このサイトをホワイトリストに追加（広告収入を支援したいサイト等）",
                    checked = disableAds
                ) { disableAds = it }

                Spacer(modifier = Modifier.height(12.dp))
                @Suppress("DEPRECATION")
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // ── カスタム CSS ──────────────────────────────────────────────
                Text(
                    "カスタム CSS（省略可）",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = css,
                    onValueChange = { css = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "例: body { font-size: 18px !important; }",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 3,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── カスタム JavaScript ───────────────────────────────────────
                Text(
                    "カスタム JavaScript（省略可）",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = js,
                    onValueChange = { js = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "例: document.body.style.zoom = '1.2';",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 3,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                )

                // ── 削除ボタン（既存プロファイルがある場合のみ）─────────────
                if (existing != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            siteProfileRepository.deleteProfile(domain)
                            onSaveAndReload()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("このサイトの設定を削除して再読み込み")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                siteProfileRepository.saveProfile(
                    SiteProfile(
                        domain = domain,
                        userAgent = uaMode,
                        forceMenuFix = forceMenuFix,
                        skipLazyLoader = skipLazy,
                        allowAutoplay = allowAuto,
                        disableAdBlock = disableAds,
                        customCss = css.trim(),
                        customJs = js.trim(),
                    )
                )
                onSaveAndReload()
            }) {
                Text("保存して再読み込み")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun SiteProfileSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** 広告ドメインのホスト一覧。shouldInterceptRequest でリクエスト自体を遮断する */
private val AD_HOSTS = setOf(
    // Google広告
    "doubleclick.net", "googlesyndication.com", "googletagservices.com",
    "googleadservices.com", "pagead2.googlesyndication.com",
    // 海外主要ネットワーク
    "amazon-adsystem.com", "taboola.com", "outbrain.com",
    "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com",
    "openx.net", "adsrvr.org", "smartadserver.com", "adnxs.com",
    "lijit.com", "triplelift.com", "sharethrough.com", "33across.com",
    "yieldmo.com", "media.net", "spotx.tv", "spotxchange.com",
    // 日本の広告ネットワーク
    "socdm.com",          // MicroAd
    "microad.jp", "microad.net",
    "logly.co.jp",
    "genieesspv.jp", "geniee.co.jp",
    "nend.net",
    "imobile.co.jp", "i-mobile.co.jp",
    "zucks.net",
    "adstir.com",
    "fluct.jp",
    "smartnews-ads.com",
    "yj-ad.jp",           // Yahoo Japan 広告
    "yads.c.yimg.jp",     // Yahoo Display Network (YDN)
    "yads.c.yimg.com",
    "admatrix.jp",
    "nobnag.com",
    "adingo.jp",
    "realgraph.jp",
    "adjust.com",         // アトリビューション計測兼広告
    "mopub.com",
    "impact-ad.jp",       // Impact Radius
    "an.yahoo.co.jp",     // Yahoo! JAPANアドネットワーク
    "ad.logly.co.jp",
    "jbbs.livedoor.jp",
    "hayabusa.io",        // minkabu等の広告
)

/**
 * 代表的な大手広告ネットワークのホスト一覧。
 * 「強力な広告ブロック」設定に関わらず常時ブロックされる。
 */
private val MAJOR_AD_NETWORKS = setOf(
    // ── Google Ads ────────────────────────────────────────────────────────
    "doubleclick.net",                // Google DoubleClick / DFP
    "googlesyndication.com",          // Google AdSense
    "googleadservices.com",           // Google 広告配信
    "googletagservices.com",          // Google Publisher Tag
    "adservice.google.com",           // Google 広告サービス (新系)
    "adservice.google.co.jp",         // 同・日本向け
    "pagead2.googlesyndication.com",  // AdSense ページ広告
    // ── Meta (Facebook / Instagram) ───────────────────────────────────────
    "connect.facebook.net",           // Meta Pixel / Audience Network SDK
    "an.facebook.com",                // Facebook Audience Network 配信
    "an.instagram.com",               // Instagram Audience Network
    // ── LINE Yahoo / Yahoo Japan ──────────────────────────────────────────
    "yj-ad.jp",                       // Yahoo Japan 広告
    "yads.c.yimg.jp",                 // Yahoo Display Network (YDN)
    "yads.c.yimg.com",
    "an.yahoo.co.jp",                 // Yahoo! JAPAN アドネットワーク
    // ── X (旧 Twitter) ────────────────────────────────────────────────────
    "ads.twitter.com",                // Twitter Ads 配信
    "analytics.twitter.com",          // Twitter 広告トラッキング
    "ads-twitter.com",
    // ── TikTok / ByteDance ────────────────────────────────────────────────
    "ads.tiktok.com",
    "ad.tiktok.com",
    "analytics.tiktok.com",
    "ads-sg.tiktok.com",
    // ── Microsoft / Bing Ads ──────────────────────────────────────────────
    "bat.bing.com",                   // Microsoft Advertising トラッキングピクセル
    "ads.microsoft.com",
    // ── Amazon Advertising ────────────────────────────────────────────────
    "amazon-adsystem.com",
    "assoc-amazon.jp",
    // ── Snapchat Ads ──────────────────────────────────────────────────────
    "sc-static.net",                  // Snapchat Pixel
    "tr.snapchat.com",
    // ── AppLovin / IronSource (Unity) ─────────────────────────────────────
    "applovin.com",
    "ironsource.com",
    // ── LINE Ads ──────────────────────────────────────────────────────────
    "tr.line.me",                     // LINE コンバージョン API
    "impact.line.me",
)

/** ブロック時に返す空レスポンスを毎回新規生成する（ByteArrayInputStream のポジションは共有不可） */
private fun emptyResponse() = WebResourceResponse(
    "text/plain", "utf-8",
    java.io.ByteArrayInputStream(ByteArray(0))
)

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
