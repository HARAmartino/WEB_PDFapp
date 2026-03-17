package com.example.printedit.ui

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.printedit.data.Preset
import com.example.printedit.data.PresetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import android.util.Log

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

    var selectedPresetName by remember { mutableStateOf<String?>(null) }
    
    // Repositories & State
    val presetRepository = remember { PresetRepository(context) }
    var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    
    // Mutable Settings State (Default to passed params)
    var isTextOnly by remember { mutableStateOf(presetSettings.first) }
    var isGrayscale by remember { mutableStateOf(presetSettings.second) }
    var isRemoveBackground by remember { mutableStateOf(presetSettings.third) }
    var isAdsRemoved by remember { mutableStateOf(false) }
    var isImageAdjusted by remember { mutableStateOf(false) }
    
    // Load Presets
    LaunchedEffect(Unit) {
        presets = presetRepository.getPresets()
    }

    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("準備中...") }
    var isWaitingForImages by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pendingSaveTitle by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    val targetLinks = remember(isGenerating) { links.filter { it.selected.value } }
    var currentLinkIndex by remember { mutableStateOf(0) }

    var selectedFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val folderPickLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                // We got the folder!
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                selectedFolderUri = uri
                isGenerating = true
                currentLinkIndex = 0
            } else {
                Toast.makeText(context, "フォルダ選択がキャンセルされました", Toast.LENGTH_SHORT).show()
                isGenerating = false
            }
        }
    )

    LaunchedEffect(pendingSaveTitle) {
        val title = pendingSaveTitle
        val folderUri = selectedFolderUri
        if (title != null && folderUri != null && webViewInstance != null) {
            statusMessage = "PDFを保存中: $title..."
            coroutineScope.launch {
                try {
                    val docId = DocumentsContract.getTreeDocumentId(folderUri)
                    val dirUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                    val newFileUri = DocumentsContract.createDocument(context.contentResolver, dirUri, "application/pdf", title)
                    
                    if (newFileUri != null) {
                        val success = saveWebViewAsPdfToTarget(context, webViewInstance!!, newFileUri)
                        if (success) {
                            val nextIndex = currentLinkIndex + 1
                            if (nextIndex < targetLinks.size) {
                                currentLinkIndex = nextIndex
                                progress = nextIndex
                                statusMessage = "${nextIndex + 1} / $total ページ目を処理中: ${targetLinks[nextIndex].text}"
                            } else {
                                Toast.makeText(context, "すべてのPDFの保存が完了しました！", Toast.LENGTH_LONG).show()
                                onDismiss() // All done
                            }
                        } else {
                            Toast.makeText(context, "保存失敗: $title", Toast.LENGTH_SHORT).show()
                            currentLinkIndex++
                        }
                    } else {
                        Toast.makeText(context, "ファイル作成失敗: $title", Toast.LENGTH_SHORT).show()
                        currentLinkIndex++
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "エラーが発生しました: ${e.message}", Toast.LENGTH_SHORT).show()
                    currentLinkIndex++
                } finally {
                    pendingSaveTitle = null
                }
            }
        }
    }

    if (isGenerating) {
        // Recursive Loading UI (Overlay)
        AlertDialog(
            onDismissRequest = {},
            title = { Text("PDF自動保存処理中") },
            text = {
                Column {
                    LinearProgressIndicator(progress = if (total > 0) progress.toFloat() / total else 0f)
                    Spacer(Modifier.height(8.dp))
                    Text(statusMessage)
                    Text("保存先を選択してください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = {}
        )
        
        // Hidden WebView for Processing
        var loadedUrlForIndex by remember { mutableStateOf(-1) }

        // WebView のクリーンアップ
        DisposableEffect(Unit) {
            onDispose {
                webViewInstance?.destroy()
                webViewInstance = null
            }
        }

        // Bug #4 fix: WebView の再利用 — destroy せず loadUrl で次ページを読み込む
        LaunchedEffect(currentLinkIndex, webViewInstance) {
            if (webViewInstance != null && currentLinkIndex < targetLinks.size && loadedUrlForIndex != currentLinkIndex) {
                loadedUrlForIndex = currentLinkIndex
                webViewInstance?.loadUrl(targetLinks[currentLinkIndex].url)
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    addJavascriptInterface(PrintReadyInterface {
                        // This indicates images are ready and WebView finished rendering
                        isWaitingForImages = false
                        statusMessage = "保存先を準備中..."
                        var baseTitle = targetLinks[currentLinkIndex].text.take(30).replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
                        if (baseTitle.isEmpty()) {
                            baseTitle = "Document_${currentLinkIndex + 1}"
                        }
                        pendingSaveTitle = "PrintEdit_${baseTitle}.pdf"
                    }, "AndroidPrint")
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 1. Inject all JS function definitions first
                            val jsDefs = StringBuilder()
                            jsDefs.append(toggleTextOnlyJs)
                            jsDefs.append(toggleGrayscaleJs)
                            jsDefs.append(toggleNoBackgroundJs)
                            jsDefs.append(removeAdsJs)
                            jsDefs.append(toggleRemoveElementModeJs)
                            view?.evaluateJavascript(jsDefs.toString(), null)
                            
                            // 2. Then call the toggle functions with the preset values
                            val jsCalls = StringBuilder()
                            if (isTextOnly) jsCalls.append("if(window.toggleTextOnly) window.toggleTextOnly(true);")
                            if (isGrayscale) jsCalls.append("if(window.toggleGrayscale) window.toggleGrayscale(true);")
                            if (isRemoveBackground) jsCalls.append("if(window.toggleNoBackground) window.toggleNoBackground(true);")
                            // Bug #6 fix: 広告削除はフラグに基づいて条件実行
                            if (isAdsRemoved) jsCalls.append("if(window.peManualRemoveAds) window.peManualRemoveAds();")
                            
                            // Bug #10 fix: Apply selectors from chosen preset
                            presets.firstOrNull { it.name == selectedPresetName }?.let { preset ->
                                if (preset.selectors.isNotEmpty()) {
                                    val selectorsJson = org.json.JSONArray(preset.selectors).toString()
                                    val escapedJson = selectorsJson.replace("'", "\\'")
                                    jsCalls.append("if(window.peApplyRemovedSelectors) window.peApplyRemovedSelectors('${escapedJson}');")
                                }
                            }
                            
                            view?.evaluateJavascript(jsCalls.toString(), null)
                            
                            // 2. Wait for images to load, then notify AndroidPrint
                            statusMessage = "画像の読み込みと自動スクロール中..."
                            isWaitingForImages = true
                            view?.postDelayed({
                                val imageWaitJs = """
                                    (function() {
                                        // Inject Pagination CSS to prevent cutoffs in Canvas drawing
                                        var style = document.createElement('style');
                                        style.innerHTML = 'img, p, h1, h2, h3, h4, li, figure { page-break-inside: avoid !important; break-inside: avoid !important; }';
                                        document.head.appendChild(style);
                                        
                                        function notify() { AndroidPrint.notifyReady(); }
                                        
                                        var currentPos = 0;
                                        var step = window.innerHeight || 800;
                                        
                                        var interval = setInterval(function() {
                                            currentPos += step;
                                            window.scrollTo(0, currentPos);
                                            var currentMax = Math.max(document.body.scrollHeight, 5000);
                                            if (currentPos >= currentMax || currentPos > 30000) {
                                                clearInterval(interval);
                                                window.scrollTo(0, 0);
                                                
                                                setTimeout(function() {
                                                    checkImages();
                                                }, 1500); 
                                            }
                                        }, 150);
                                        
                                        function checkImages() {
                                            var images = Array.from(document.images);
                                            var outstanding = images.length;
                                            var resolved = false;
                                            
                                            function checkDone() {
                                                if (resolved) return;
                                                outstanding--;
                                                if (outstanding <= 0) {
                                                    resolved = true;
                                                    notify();
                                                }
                                            }
                                            
                                            if (outstanding === 0) {
                                                notify();
                                                return;
                                            }
                                            
                                            setTimeout(function() { 
                                                if (!resolved) { 
                                                    resolved = true; 
                                                    notify(); 
                                                } 
                                            }, 10000); // 10 seconds timeout for images
                                            
                                            images.forEach(function(img) {
                                                if (img.complete) {
                                                    checkDone();
                                                } else {
                                                    // Check lazy load attrs and force src
                                                    var rawDataSrc = img.dataset.src || img.dataset.lazySrc || img.getAttribute('data-original');
                                                    if (rawDataSrc && (!img.src || img.src.indexOf('data:') === 0)) {
                                                         try { img.src = new URL(rawDataSrc, document.baseURI).href; } catch(e){}
                                                    }
                                                    if (img.getAttribute('loading') === 'lazy') {
                                                        img.removeAttribute('loading');
                                                    }
                                                    
                                                    img.addEventListener('load', checkDone, {once: true});
                                                    img.addEventListener('error', checkDone, {once: true});
                                                }
                                            });
                                        }
                                    })();
                                """.trimIndent()
                                view.evaluateJavascript(imageWaitJs, null)
                            }, 500) // Delay apply
                        }
                    }
                }
            },
            update = { webView ->
                // update は WebView の参照を維持するだけ。URL 読み込みは LaunchedEffect で行う
                webViewInstance = webView
            },
            modifier = Modifier.fillMaxSize().alpha(0.01f) // Use fillMaxSize to ensure all layout resolves for images
        )
        
        LaunchedEffect(Unit) {
            total = targetLinks.size
            if (total == 0) {
                onDismiss()
            } else {
                statusMessage = "1 / $total ページ目を処理中: ${targetLinks[0].text}"
            }
        }

    } else {
        // Selection UI
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("リンクをまとめて印刷") },
            text = {
                Column {
                    Text("${links.size} 件のリンクが見つかりました")
                    
                    // Preset Selection
                    if (presets.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("プリセットを適用:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(presets) { preset ->
                                FilterChip(
                                    selected = selectedPresetName == preset.name,
                                    onClick = { 
                                        selectedPresetName = preset.name
                                        isTextOnly = preset.textOnly
                                        isGrayscale = preset.grayscale
                                        isRemoveBackground = preset.removeBackground
                                        // Bug #7 fix: adsRemoved, imageAdjusted も反映
                                        isAdsRemoved = preset.adsRemoved
                                        isImageAdjusted = preset.imageAdjusted
                                    },
                                    label = { Text(preset.name) },
                                    leadingIcon = { Icon(Icons.Filled.Settings, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                    
                    // Manual Overrides
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isTextOnly, onCheckedChange = { isTextOnly = it })
                        Text("文字のみ", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = isGrayscale, onCheckedChange = { isGrayscale = it })
                        Text("白黒", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = isRemoveBackground, onCheckedChange = { isRemoveBackground = it })
                        Text("背景削除", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                        items(links) { link ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = link.selected.value,
                                    onCheckedChange = { link.selected.value = it }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(link.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(link.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { folderPickLauncher.launch(null) },
                    enabled = links.any { it.selected.value } && !isGenerating // Prevent double click
                ) {
                    Text("フォルダを選択して作成開始")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        )
    }
}

class PrintReadyInterface(private val onReady: () -> Unit) {
    @android.webkit.JavascriptInterface
    fun notifyReady() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onReady()
        }
    }
}

suspend fun saveWebViewAsPdfToTarget(context: Context, webView: WebView, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(targetUri, "w") ?: return@withContext false

        // Wait to make sure WebView rendering thread has fully settled
        kotlinx.coroutines.delay(1000)

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
