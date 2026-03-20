package com.example.printedit.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.example.printedit.AdManager
import com.example.printedit.data.Preset
import com.example.printedit.data.PresetRepository
import com.example.printedit.data.SettingsRepository
import com.example.printedit.data.UserAgentMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import android.util.Log
import androidx.compose.runtime.snapshotFlow

class LinkItem(val url: String, val text: String, val selected: MutableState<Boolean>)

val getLinksInSelectionJs = """
    (function() {
        var sel = window.getSelection();
        if (sel.rangeCount == 0) return JSON.stringify([]);
        var range = sel.getRangeAt(0);

        // Ensure range represents a visible selection
        var selRects = range.getClientRects();
        if (selRects.length === 0) return JSON.stringify([]);

        var container = range.commonAncestorContainer;
        if (container.nodeType == 3) container = container.parentNode;

        // Expand the container slightly up just in case the selection edges are overlapping parent boundaries
        if (container.parentElement) container = container.parentElement;

        var links = container.querySelectorAll('a');
        var resultMap = {};

        function intersects(r1, r2) {
            if (r1.width === 0 || r1.height === 0 || r2.width === 0 || r2.height === 0) return false;
            // Add a 1px tolerance to avoid edge-touching false positives
            return !(r1.right <= r2.left + 1 || r1.left >= r2.right - 1 || r1.bottom <= r2.top + 1 || r1.top >= r2.bottom - 1);
        }

        for (var i=0; i<links.length; i++) {
            var link = links[i];
            var isSelected = false;

            // Check absolute geometric bounds instead of buggy DOM containsNode
            var linkRects = link.getClientRects();
            for (var j=0; j<linkRects.length; j++) {
                for (var k=0; k<selRects.length; k++) {
                    if (intersects(linkRects[j], selRects[k])) {
                        isSelected = true;
                        break;
                    }
                }
                if (isSelected) break;
            }

            if (isSelected || sel.containsNode(link, false)) { // If it physically intersects OR is completely enclosed
                var href = link.href;
                var text = link.innerText.trim();

                if (!href || href.startsWith('javascript:') || href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('tel:')) continue;
                if (!text || text.length === 0) continue;

                // Parse URL
                try { var parsed = new URL(href); } catch(e) { continue; }

                // Skip root-only URLs (no meaningful path)
                var pathParts = parsed.pathname.split('/').filter(function(p) { return p.length > 0; });
                if (pathParts.length < 1) continue;

                if (!resultMap[href] || resultMap[href].length < text.length) {
                    resultMap[href] = text;
                }
            }
        }

        var result = [];
        for (var key in resultMap) {
            result.push({url: key, text: resultMap[key]});
        }
        return JSON.stringify(result);
    })()
""".trimIndent()

// Holds the callback to signal page load completion to the batch coroutine
private class PageLoadCallbackHolder {
    var onPageLoaded: (() -> Unit)? = null
}

// 全画像ロード完了を JS から通知するインターフェース
private class ImageLoadInterface(private val onReady: () -> Unit) {
    @android.webkit.JavascriptInterface
    fun notifyReady() {
        android.os.Handler(android.os.Looper.getMainLooper()).post { onReady() }
    }
}

// ── バッチ処理進捗通知ヘルパー ──────────────────────────────────────────────────

private const val BATCH_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
private const val BATCH_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val BATCH_NOTIF_CHANNEL_ID = "batch_pdf_progress"
private const val BATCH_NOTIF_ID = 1001

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(
            BATCH_NOTIF_CHANNEL_ID,
            "PDF自動保存の進捗",
            NotificationManager.IMPORTANCE_LOW  // バイブなし・サイレント
        ).apply { description = "バッチPDF保存の処理状況を表示します" }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(ch)
    }
}

private fun canPostNotification(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

private fun postProgressNotification(context: Context, current: Int, total: Int, title: String) {
    if (!canPostNotification(context)) return
    ensureNotificationChannel(context)
    val notif = NotificationCompat.Builder(context, BATCH_NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("PDF自動保存中 $current / $total 件")
        .setContentText(title.take(60))
        .setProgress(total, current, false)
        .setOngoing(true)
        .setSilent(true)
        .build()
    NotificationManagerCompat.from(context).notify(BATCH_NOTIF_ID, notif)
}

private fun cancelProgressNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(BATCH_NOTIF_ID)
}

private fun postCompletionNotification(context: Context, message: String) {
    if (!canPostNotification(context)) return
    ensureNotificationChannel(context)
    val notif = NotificationCompat.Builder(context, BATCH_NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("PDF自動保存 完了")
        .setContentText(message)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(BATCH_NOTIF_ID + 1, notif)
}

// ────────────────────────────────────────────────────────────────────────────

private fun sanitizePdfFilename(title: String): String {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchLinkPrintDialog(
    linksJson: String,
    onDismiss: () -> Unit,
    presetSettings: Triple<Boolean, Boolean, Boolean> // textOnly, grayscale, noBackground
) {
    val context = LocalContext.current

    // Parse links
    val links = remember(linksJson) {
        val list = mutableListOf<LinkItem>()
        try {
            val jsonArray = JSONArray(linksJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(LinkItem(obj.getString("url"), obj.getString("text"), mutableStateOf(true)))
            }
        } catch (e: Exception) { e.printStackTrace() }
        list
    }

    val settingsRepository = remember { SettingsRepository(context) }
    val presetRepository = remember { PresetRepository(context) }
    var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }

    // Print Settings (defaults from caller's current state)
    var isTextOnly by remember { mutableStateOf(presetSettings.first) }
    var isGrayscale by remember { mutableStateOf(presetSettings.second) }
    var isRemoveBackground by remember { mutableStateOf(presetSettings.third) }
    var isAdsRemoved by remember { mutableStateOf(false) }
    var isImageAdjusted by remember { mutableStateOf(false) }
    var batchUaMode by remember { mutableStateOf(UserAgentMode.GLOBAL) }
    // factory に渡す UA スナップショット（処理開始時に確定）
    val batchUaSnapshot = remember { mutableStateOf(BATCH_DESKTOP_UA) }

    LaunchedEffect(Unit) {
        presets = presetRepository.getPresets()
    }

    // Batch processing state
    var isGenerating by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val callbackHolder = remember { PageLoadCallbackHolder() }
    val imageLoadCallbackHolder = remember { PageLoadCallbackHolder() }

    // ダイアログが破棄されるとき（バッチ途中でキャンセルされた場合含む）に WebView と通知を確実に破棄
    DisposableEffect(Unit) {
        onDispose {
            cancelProgressNotification(context)
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    // Batch processing coroutine — starts when isGenerating becomes true
    LaunchedEffect(isGenerating) {
        if (!isGenerating) return@LaunchedEffect

        // Wait for the hidden WebView to be created by Compose
        val wv = snapshotFlow { webViewInstance }.first { it != null }!!

        val activity = context as? android.app.Activity

        // 処理開始と同時にインタースティシャルを表示（処理は裏で続行）
        if (activity != null) {
            AdManager.showAdIfAvailable(activity) { /* 閉じても処理は独立して進む */ }
        }

        val selectedLinks = links.filter { it.selected.value }
        val customUriString = settingsRepository.customSaveUri
        var successCount = 0
        var failCount = 0

        for ((index, link) in selectedLinks.withIndex()) {
            statusMessage = "${index + 1} / ${selectedLinks.size} 件目を処理中...\n「${link.text.take(40)}」"
            postProgressNotification(context, index + 1, selectedLinks.size, link.text)

            // Set up load callback then load URL (both on main thread to avoid race)
            val loadDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                callbackHolder.onPageLoaded = {
                    if (!loadDeferred.isCompleted) loadDeferred.complete(Unit)
                }
                wv.loadUrl(link.url)
            }

            // Wait for page to finish loading (30s timeout)
            withTimeoutOrNull(30_000L) { loadDeferred.await() }

            // Apply JS transformations
            withContext(Dispatchers.Main) {
                val baseJs = removeAdsJs + "\n" + toggleTextOnlyJs + "\n" + toggleGrayscaleJs + "\n" + toggleNoBackgroundJs
                wv.evaluateJavascript(baseJs, null)
                if (isAdsRemoved) wv.evaluateJavascript("if(window.peToggleRemoveAds) window.peToggleRemoveAds(true);", null)
                if (isTextOnly) wv.evaluateJavascript("if(window.toggleTextOnly) window.toggleTextOnly(true);", null)
                if (isGrayscale) wv.evaluateJavascript("if(window.toggleGrayscale) window.toggleGrayscale(true);", null)
                if (isRemoveBackground) wv.evaluateJavascript("if(window.toggleNoBackground) window.toggleNoBackground(true);", null)
                if (isImageAdjusted) wv.evaluateJavascript(smartFitImagesJs, null)
                // 遅延ロード画像を強制展開
                wv.evaluateJavascript(forceLoadLazyImagesJs, null)
            }

            // 全画像のロード完了を JS から通知で受け取る（最大10秒待機）
            val imgDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                imageLoadCallbackHolder.onPageLoaded = {
                    if (!imgDeferred.isCompleted) imgDeferred.complete(Unit)
                }
                wv.evaluateJavascript("""
                    (function() {
                        var imgs = Array.from(document.images).filter(function(img) {
                            return img.src && !img.src.startsWith('data:');
                        });
                        var pending = imgs.filter(function(img) {
                            return !img.complete || img.naturalHeight === 0;
                        });
                        if (pending.length === 0) {
                            if (window._PEImgBridge) window._PEImgBridge.notifyReady();
                            return;
                        }
                        var done = 0;
                        pending.forEach(function(img) {
                            function check() {
                                done++;
                                if (done >= pending.length && window._PEImgBridge) {
                                    window._PEImgBridge.notifyReady();
                                }
                            }
                            img.addEventListener('load',  check, {once: true});
                            img.addEventListener('error', check, {once: true});
                        });
                    })();
                """.trimIndent(), null)
            }
            withTimeoutOrNull(10_000L) { imgDeferred.await() }

            // Get page title
            val titleDeferred = kotlinx.coroutines.CompletableDeferred<String>()
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript("document.title") { rawTitle ->
                    val t = rawTitle?.removeSurrounding("\"")?.ifBlank { link.text } ?: link.text
                    titleDeferred.complete(t)
                }
            }
            val title = titleDeferred.await().ifBlank { link.text }
            val filename = sanitizePdfFilename(title)

            // Save PDF to target
            val success = if (customUriString != null) {
                savePdfToFolderUri(context, wv, Uri.parse(customUriString), filename)
            } else {
                savePdfToDownloadsImpl(context, wv, filename)
            }

            if (success) successCount++ else failCount++
            progress = index + 1
        }

        // 完了通知と結果表示
        val destination = if (customUriString != null) "設定したフォルダ" else "Downloads/PrintEdit"
        val msg = when {
            failCount == 0 -> "✅ ${successCount}件のPDFを\n${destination}に保存しました！"
            successCount == 0 -> "❌ PDF保存に失敗しました（${failCount}件）"
            else -> "⚠️ ${successCount}件保存成功\n${failCount}件は失敗しました"
        }
        cancelProgressNotification(context)
        postCompletionNotification(context, msg.replace("\n", " "))
        isGenerating = false
        resultMessage = msg
        isDone = true
    }

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }) {
        Box {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isDone) {
                        // ---- Completion View ----
                        Text(
                            "保存完了",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            resultMessage,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = onDismiss) { Text("OK") }
                        }
                    } else if (isGenerating) {
                        // ---- Progress View ----
                        Text(
                            "PDF自動保存処理中...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2
                        )
                        @Suppress("DEPRECATION")
                        LinearProgressIndicator(
                            progress = if (total > 0) progress.toFloat() / total else 0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "$progress / $total 件完了",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // バナー広告（処理中に表示。×ボタン不要）
                        // TODO: リリース前に本番IDに変更
                        AndroidView(
                            factory = { ctx ->
                                AdView(ctx).apply {
                                    setAdSize(AdSize.BANNER)
                                    adUnitId = "ca-app-pub-3940256099942544/6300978111" // テスト用バナーID
                                    loadAd(AdRequest.Builder().build())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // ---- Setup View ----
                        Text(
                            "バッチPDF保存",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        // Select all / none row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${links.count { it.selected.value }} / ${links.size} 件選択",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row {
                                TextButton(onClick = { links.forEach { it.selected.value = true } }) { Text("全選択") }
                                TextButton(onClick = { links.forEach { it.selected.value = false } }) { Text("全解除") }
                            }
                        }

                        // Link list
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(links) { link ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = link.selected.value,
                                        onCheckedChange = { link.selected.value = it }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            link.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            link.url,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        @Suppress("DEPRECATION")
                        Divider()

                        // Print Settings
                        Text("印刷設定", style = MaterialTheme.typography.labelLarge)

                        // Preset selector
                        if (presets.isNotEmpty()) {
                            var showPresetMenu by remember { mutableStateOf(false) }
                            var appliedPresetName by remember { mutableStateOf<String?>(null) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showPresetMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (appliedPresetName != null)
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    else ButtonDefaults.outlinedButtonColors()
                                ) {
                                    Text(appliedPresetName?.let { "✓ $it" } ?: "プリセットを適用...")
                                }
                                DropdownMenu(
                                    expanded = showPresetMenu,
                                    onDismissRequest = { showPresetMenu = false }
                                ) {
                                    presets.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text(preset.name) },
                                            onClick = {
                                                isTextOnly = preset.textOnly
                                                isGrayscale = preset.grayscale
                                                isRemoveBackground = preset.removeBackground
                                                isAdsRemoved = preset.adsRemoved
                                                isImageAdjusted = preset.imageAdjusted
                                                appliedPresetName = preset.name
                                                showPresetMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // UA selection
                        Text("表示モード", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                UserAgentMode.GLOBAL to "グローバル設定",
                                UserAgentMode.MOBILE to "モバイル",
                                UserAgentMode.DESKTOP to "PC"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = batchUaMode == mode,
                                    onClick = { batchUaMode = mode },
                                    label = { Text(label) }
                                )
                            }
                        }

                        // Settings chips — row 1
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isTextOnly,
                                onClick = { isTextOnly = !isTextOnly },
                                label = { Text("文字のみ") }
                            )
                            FilterChip(
                                selected = isGrayscale,
                                onClick = { isGrayscale = !isGrayscale },
                                label = { Text("白黒") }
                            )
                            FilterChip(
                                selected = isRemoveBackground,
                                onClick = { isRemoveBackground = !isRemoveBackground },
                                label = { Text("背景なし") }
                            )
                        }
                        // Settings chips — row 2
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isAdsRemoved,
                                onClick = { isAdsRemoved = !isAdsRemoved },
                                label = { Text("広告削除") }
                            )
                            FilterChip(
                                selected = isImageAdjusted,
                                onClick = { isImageAdjusted = !isImageAdjusted },
                                label = { Text("画像調整") }
                            )
                        }

                        // Save destination info
                        val customUri = settingsRepository.customSaveUri
                        Text(
                            text = if (customUri != null) "保存先: 設定済みカスタムフォルダ" else "保存先: Downloads/PrintEdit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) { Text("キャンセル") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val count = links.count { it.selected.value }
                                    if (count == 0) {
                                        Toast.makeText(context, "リンクを1件以上選択してください", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    total = count
                                    progress = 0
                                    statusMessage = "WebViewを準備中..."
                                    batchUaSnapshot.value = when (batchUaMode) {
                                        UserAgentMode.MOBILE  -> BATCH_MOBILE_UA
                                        UserAgentMode.DESKTOP -> BATCH_DESKTOP_UA
                                        UserAgentMode.GLOBAL  ->
                                            if (settingsRepository.desktopMode) BATCH_DESKTOP_UA else BATCH_MOBILE_UA
                                    }
                                    isGenerating = true
                                }
                            ) {
                                Text("一括保存 (${links.count { it.selected.value }}件)")
                            }
                        }
                    }
                }
            }

            // Hidden WebView — added to composition only during batch processing.
            // Uses desktop UA + wideViewPort for proper page rendering.
            if (isGenerating) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = batchUaSnapshot.value
                            addJavascriptInterface(
                                ImageLoadInterface {
                                    imageLoadCallbackHolder.onPageLoaded?.let { cb ->
                                        imageLoadCallbackHolder.onPageLoaded = null
                                        cb()
                                    }
                                },
                                "_PEImgBridge"
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    callbackHolder.onPageLoaded?.let { cb ->
                                        callbackHolder.onPageLoaded = null
                                        cb()
                                    }
                                }
                            }
                        }
                    },
                    update = { wv -> webViewInstance = wv },
                    // alpha(0f) は SurfaceView ベースの WebView では compositor レイヤーを
                    // 透過できず描画アーティファクトを引き起こすため使わない。
                    // size(1.dp) だけで実質的に不可視となる。
                    modifier = Modifier.size(1.dp)
                )
            }
        }
    }
}

suspend fun saveWebViewAsPdfToTarget(context: Context, webView: WebView, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(targetUri, "w") ?: return@withContext false

        // Wait to make sure WebView rendering thread has fully settled
        kotlinx.coroutines.delay(500)

        val isSuccess = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val printAdapter = webView.createPrintDocumentAdapter("BatchPrint")
                    val printAttributes = android.print.PrintAttributes.Builder()
                        .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    android.print.PdfPrint.print(printAdapter, printAttributes, pfd, object : android.print.PdfPrint.PrintCallback {
                        override fun onSuccess() {
                            try { pfd.close() } catch (e: Exception) { Log.e("PrintEdit", "Error closing pfd", e) }
                            continuation.resumeWith(Result.success(true))
                        }

                        override fun onFailure(errorMsg: String?) {
                            try { pfd.close() } catch (e: Exception) { Log.e("PrintEdit", "Error closing pfd", e) }
                            Log.e("PrintEdit", "PDF generation failed: $errorMsg")
                            continuation.resumeWith(Result.success(false))
                        }
                    })
                } catch (e: Exception) {
                    e.printStackTrace()
                    try { pfd.close() } catch (ex: Exception) { Log.e("PrintEdit", "Error closing pfd", ex) }
                    continuation.resumeWith(Result.success(false))
                }
            }
        }
        return@withContext isSuccess
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}

suspend fun savePdfToDownloadsImpl(context: Context, webView: WebView, title: String): Boolean = withContext(Dispatchers.IO) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, title)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/PrintEdit")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext false
            val success = saveWebViewAsPdfToTarget(context, webView, uri)

            contentValues.clear()
            contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)

            return@withContext success
        } else {
            // Fallback for Android 9 and below
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PrintEdit")
            if (!dir.exists()) dir.mkdirs()
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            var file = java.io.File(dir, safeTitle)
            var counter = 1
            while (file.exists()) {
                file = java.io.File(dir, safeTitle.replace(".pdf", " ($counter).pdf"))
                counter++
            }
            val uri = Uri.fromFile(file)
            return@withContext saveWebViewAsPdfToTarget(context, webView, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}

// Save PDF to a user-selected folder URI (e.g. Google Drive, custom folder)
suspend fun savePdfToFolderUri(context: Context, webView: WebView, treeUri: Uri, filename: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val docUri = android.provider.DocumentsContract.createDocument(
            context.contentResolver,
            android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            ),
            "application/pdf",
            filename
        ) ?: return@withContext false
        saveWebViewAsPdfToTarget(context, webView, docUri)
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
