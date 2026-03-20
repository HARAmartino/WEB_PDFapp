package com.example.printedit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import com.example.printedit.data.SettingsRepository
import com.example.printedit.data.PresetRepository

/**
 * SHOW_ADVANCED フラグ付きのフォルダ選択コントラクト。
 * 標準の OpenDocumentTree では Google Drive 等のクラウドストレージが表示されないため
 * カスタムインテントを生成するコントラクトを使用する。
 */
private class FolderPickerContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // SHOW_ADVANCED は SD カード/USB 用のフラグであり Google Drive には無効。
            // 指定するとデバイスストレージビューに固定されてしまうため削除。
            // EXTRA_LOCAL_ONLY=false でクラウドストレージ（Google Drive 等）を許可する。
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    
    // Global Settings State
    var aggressiveAdBlock by remember { mutableStateOf(settingsRepository.aggressiveAdBlock) }
    var menuFixEnabled by remember { mutableStateOf(settingsRepository.menuFixEnabled) }
    var autoImageAdjust by remember { mutableStateOf(settingsRepository.autoImageAdjust) }
    var desktopMode by remember { mutableStateOf(settingsRepository.desktopMode) }
    
    val presetRepository = remember { PresetRepository(context) }
    var showPresetManager by remember { mutableStateOf(false) }

    // Menu Actions State
    var menuActions by remember { mutableStateOf(settingsRepository.menuActions) }

    // PDF Batch Save Destination
    var customSaveUri by remember { mutableStateOf(settingsRepository.customSaveUri) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = FolderPickerContract()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsRepository.customSaveUri = uri.toString()
            customSaveUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Functions
            item {
                Text(
                    text = "全般設定",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsSwitch(
                    title = "強力な広告ブロック",
                    description = "画面を覆う広告や、後から読み込まれる広告を自動で削除します。",
                    checked = aggressiveAdBlock,
                    onCheckedChange = { 
                        aggressiveAdBlock = it
                        settingsRepository.aggressiveAdBlock = it
                    }
                )
                
                SettingsSwitch(
                    title = "メニュー表示の修正",
                    description = "メニューが開かないサイトを修正します。（サイトのデザインが変わる場合があります）",
                    checked = menuFixEnabled,
                    onCheckedChange = { 
                        menuFixEnabled = it
                        settingsRepository.menuFixEnabled = it
                    }
                )
                
                SettingsSwitch(
                    title = "画像の自動調整",
                    description = "ページ読み込み時に、大きな画像を自動で画面サイズに合わせます。",
                    checked = autoImageAdjust,
                    onCheckedChange = { 
                        autoImageAdjust = it
                        settingsRepository.autoImageAdjust = it
                    }
                )
                
                SettingsSwitch(
                    title = "PC版サイトを表示",
                    description = "スマートフォン向けではなく、パソコン向けのレイアウトで表示します。（再読み込み後に反映）",
                    checked = desktopMode,
                    onCheckedChange = { 
                        desktopMode = it
                        settingsRepository.desktopMode = it
                    }
                )
            }

            // Section 2: Menu Customization
            item {
                @Suppress("DEPRECATION")
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "編集メニューのカスタマイズ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "メニューボタンを押したときに表示する機能を選択してください。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val allActions = listOf(
                    "action_remove_ads" to "広告を削除",
                    "action_presets" to "プリセット",
                    "action_adjust_images" to "画像を調整",
                    "action_remove_elements" to "要素を削除",
                    "action_undo" to "元に戻す",
                    "action_text_only" to "文字のみ表示",
                    "action_grayscale" to "白黒モード",
                    "action_remove_background" to "背景を削除"
                )
                
                allActions.forEach { (id, label) ->
                    MenuActionCheckbox(
                        label = label,
                        checked = menuActions.contains(id),
                        onCheckedChange = { isChecked ->
                            val newSet = menuActions.toMutableSet()
                            if (isChecked) newSet.add(id) else newSet.remove(id)
                            menuActions = newSet
                            settingsRepository.menuActions = newSet
                        }
                    )
                }
            }

            // Section 3: PDF Batch Save Destination
            item {
                @Suppress("DEPRECATION")
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "一括PDF保存先",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "バッチ印刷で保存するフォルダを設定します。デフォルトはダウンロード内の PrintEdit フォルダです。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (customSaveUri != null) {
                    Text(
                        text = "現在: カスタムフォルダ (設定済み)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            settingsRepository.customSaveUri = null
                            customSaveUri = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Text("デフォルト (Downloads/PrintEdit) に戻す")
                    }
                } else {
                    Text(
                        text = "現在: Downloads/PrintEdit (デフォルト)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(Unit) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存先フォルダを変更する")
                }
                Text(
                    text = "※ 一度選択すれば、以後はダイアログなしで自動保存されます。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Section 4: Presets
            item {
                @Suppress("DEPRECATION")
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "プリセット管理",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "保存したプリセットの内容確認と削除を行います。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedButton(
                    onClick = { showPresetManager = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存済みプリセットの詳細を確認・削除")
                }
            }
        }
        
        if (showPresetManager) {
            PresetManagerDialog(
                presetRepository = presetRepository,
                onDismiss = { showPresetManager = false }
            )
        }

    }
}

@Composable
fun PresetManagerDialog(
    presetRepository: PresetRepository,
    onDismiss: () -> Unit
) {
    var presets by remember { mutableStateOf(presetRepository.getPresets()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存済みプリセット") },
        text = {
            if (presets.isEmpty()) {
                Text("保存されたプリセットはありません", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(presets) { preset ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(preset.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    IconButton(
                                        onClick = {
                                            presetRepository.deletePreset(preset.name)
                                            presets = presetRepository.getPresets()
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                @Suppress("DEPRECATION")
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                val tags = mutableListOf<String>()
                                if (preset.adsRemoved) tags.add("広告削除")
                                if (preset.textOnly) tags.add("文字のみ")
                                if (preset.grayscale) tags.add("白黒")
                                if (preset.removeBackground) tags.add("背景なし")
                                if (preset.imageAdjusted) tags.add("画像調整")
                                if (tags.isNotEmpty()) {
                                    Text("有効な設定: ${tags.joinToString(", ")}", fontSize = 14.sp)
                                } else {
                                    Text("有効な設定: なし", fontSize = 14.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                if (preset.selectors.isNotEmpty()) {
                                    Text("個別に削除した要素 (${preset.selectors.size}個):", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 120.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Column {
                                            preset.selectors.forEach { sel ->
                                                Text(sel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, lineHeight = 14.sp)
                                            }
                                        }
                                    }
                                } else {
                                    Text("個別に削除した要素: なし", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun MenuActionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
