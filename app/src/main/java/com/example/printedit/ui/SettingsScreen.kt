package jp.webpdf.app.ui

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
import androidx.compose.ui.res.stringResource
import jp.webpdf.app.R
import jp.webpdf.app.data.SettingsRepository
import jp.webpdf.app.data.PresetRepository

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
                title = { Text(stringResource(R.string.settings)) },
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
                    text = stringResource(R.string.general_settings),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SettingsSwitch(
                    title = stringResource(R.string.aggressive_ad_block),
                    description = stringResource(R.string.aggressive_ad_block_desc),
                    checked = aggressiveAdBlock,
                    onCheckedChange = {
                        aggressiveAdBlock = it
                        settingsRepository.aggressiveAdBlock = it
                    }
                )

                SettingsSwitch(
                    title = stringResource(R.string.menu_fix),
                    description = stringResource(R.string.menu_fix_desc),
                    checked = menuFixEnabled,
                    onCheckedChange = {
                        menuFixEnabled = it
                        settingsRepository.menuFixEnabled = it
                    }
                )

                SettingsSwitch(
                    title = stringResource(R.string.auto_image_adjust),
                    description = stringResource(R.string.auto_image_adjust_desc),
                    checked = autoImageAdjust,
                    onCheckedChange = {
                        autoImageAdjust = it
                        settingsRepository.autoImageAdjust = it
                    }
                )

                SettingsSwitch(
                    title = stringResource(R.string.desktop_mode),
                    description = stringResource(R.string.desktop_mode_desc),
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
                    text = stringResource(R.string.menu_customization),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.menu_customization_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val allActions = listOf(
                    "action_remove_ads" to stringResource(R.string.action_label_remove_ads),
                    "action_remove_article_bottom" to stringResource(R.string.action_label_remove_article_bottom),
                    "action_collapse_empty" to stringResource(R.string.action_label_collapse_empty),
                    "action_presets" to stringResource(R.string.action_label_presets),
                    "action_adjust_images" to stringResource(R.string.action_label_adjust_images),
                    "action_remove_elements" to stringResource(R.string.action_label_remove_elements),
                    "action_undo" to stringResource(R.string.action_label_undo),
                    "action_marquee" to stringResource(R.string.action_label_marquee),
                    "action_batch_print" to stringResource(R.string.action_label_batch_print),
                    "action_save_url" to stringResource(R.string.action_label_save_url),
                    "action_text_only" to stringResource(R.string.action_label_text_only),
                    "action_grayscale" to stringResource(R.string.action_label_grayscale),
                    "action_remove_background" to stringResource(R.string.action_label_remove_background)
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
                    text = stringResource(R.string.batch_pdf_destination),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.batch_pdf_destination_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (customSaveUri != null) {
                    Text(
                        text = stringResource(R.string.current_custom_folder),
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
                        Text(stringResource(R.string.reset_to_default_folder))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.current_default_folder),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(Unit) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.change_save_folder))
                }
                Text(
                    text = stringResource(R.string.auto_save_note),
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
                    text = stringResource(R.string.preset_management),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.preset_management_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedButton(
                    onClick = { showPresetManager = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.view_delete_presets))
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
        title = { Text(stringResource(R.string.saved_presets_title)) },
        text = {
            if (presets.isEmpty()) {
                Text(stringResource(R.string.no_presets_saved), modifier = Modifier.padding(16.dp))
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
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                @Suppress("DEPRECATION")
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                val tags = mutableListOf<String>()
                                if (preset.adsRemoved) tags.add(stringResource(R.string.preset_tag_ad_block))
                                if (preset.textOnly) tags.add(stringResource(R.string.preset_tag_text_only))
                                if (preset.grayscale) tags.add(stringResource(R.string.preset_tag_grayscale))
                                if (preset.removeBackground) tags.add(stringResource(R.string.preset_tag_no_background))
                                if (preset.imageAdjusted) tags.add(stringResource(R.string.preset_tag_image_adjust))
                                if (tags.isNotEmpty()) {
                                    Text(stringResource(R.string.active_settings, tags.joinToString(", ")), fontSize = 14.sp)
                                } else {
                                    Text(stringResource(R.string.no_active_settings), fontSize = 14.sp)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                if (preset.selectors.isNotEmpty()) {
                                    Text(stringResource(R.string.removed_elements_count, preset.selectors.size), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                                    Text(stringResource(R.string.no_removed_elements), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
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
