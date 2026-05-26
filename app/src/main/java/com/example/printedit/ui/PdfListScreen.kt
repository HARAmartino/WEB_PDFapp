package jp.webpdf.app.ui

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.webpdf.app.R
import jp.webpdf.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedPdf(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
)

private suspend fun loadSavedPdfs(
    context: android.content.Context,
    customUriString: String?,
): List<SavedPdf> = withContext(Dispatchers.IO) {
    if (customUriString != null) {
        loadPdfsFromTreeUri(context, Uri.parse(customUriString))
    } else {
        loadPdfsFromMediaStore(context)
    }
}

private fun loadPdfsFromMediaStore(context: android.content.Context): List<SavedPdf> {
    val list = mutableListOf<SavedPdf>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("%WEB_PDF%", "application/pdf")
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                list.add(
                    SavedPdf(
                        uri = uri,
                        name = (cursor.getString(nameCol) ?: "").removeSuffix(".pdf"),
                        size = cursor.getLong(sizeCol),
                        dateModified = cursor.getLong(dateCol) * 1_000L,
                    )
                )
            }
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = java.io.File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "WEB_PDF",
        )
        if (dir.exists()) {
            dir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    list.add(
                        SavedPdf(
                            uri = Uri.fromFile(file),
                            name = file.nameWithoutExtension,
                            size = file.length(),
                            dateModified = file.lastModified(),
                        )
                    )
                }
        }
    }
    return list
}

private fun loadPdfsFromTreeUri(
    context: android.content.Context,
    treeUri: Uri,
): List<SavedPdf> {
    val list = mutableListOf<SavedPdf>()
    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(mimeCol) != "application/pdf") continue
                val docId = cursor.getString(idCol)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                list.add(
                    SavedPdf(
                        uri = docUri,
                        name = (cursor.getString(nameCol) ?: "").removeSuffix(".pdf"),
                        size = cursor.getLong(sizeCol),
                        dateModified = cursor.getLong(dateCol),
                    )
                )
            }
        }
    } catch (_: Exception) {}
    return list.sortedByDescending { it.dateModified }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "${bytes / 1_024} KB"
    else -> "%.1f MB".format(bytes / (1_024.0 * 1_024.0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfListScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    var pdfs by remember { mutableStateOf<List<SavedPdf>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        pdfs = loadSavedPdfs(context, settingsRepository.customSaveUri)
        isLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pdf_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            pdfs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.pdf_list_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.pdf_list_empty_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(pdfs, key = { it.uri.toString() }) { pdf ->
                        PdfListItem(
                            pdf = pdf,
                            onOpen = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(pdf.uri, "application/pdf")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.pdf_open_failed),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, pdf.uri)
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        context.getString(R.string.pdf_share_chooser),
                                    )
                                )
                            },
                            onDelete = {
                                try {
                                    if (settingsRepository.customSaveUri != null) {
                                        DocumentsContract.deleteDocument(
                                            context.contentResolver, pdf.uri
                                        )
                                    } else {
                                        context.contentResolver.delete(pdf.uri, null, null)
                                    }
                                    pdfs = pdfs.filter { it.uri != pdf.uri }
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.pdf_delete_failed),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfListItem(
    pdf: SavedPdf,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(pdf.dateModified) {
        if (pdf.dateModified > 0)
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(pdf.dateModified))
        else ""
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.pdf_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.pdf_delete_confirm_msg,
                        pdf.name.ifBlank { stringResource(R.string.untitled) },
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(
                        stringResource(R.string.pdf_delete_label),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.name.ifBlank { stringResource(R.string.untitled) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dateStr.isNotEmpty()) {
                        Text(
                            text = dateStr,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                    if (pdf.size > 0) {
                        Text(
                            text = formatFileSize(pdf.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.pdf_share_label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
