package com.alinoori.share.ui

import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alinoori.share.FileCategory
import com.alinoori.share.ShareItem
import com.alinoori.share.ShareViewModel
import com.alinoori.share.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SortKey { NAME, SIZE, DATE }

@Composable
fun Thumb(item: ShareItem) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    item.category == FileCategory.APPS ->
                        ctx.packageManager.getApplicationIcon(item.id.removePrefix("apk:")).toBitmap(96, 96).asImageBitmap()
                    Build.VERSION.SDK_INT >= 29 && (item.mime.startsWith("image/") || item.mime.startsWith("video/")) ->
                        ctx.contentResolver.loadThumbnail(item.uri, Size(96, 96), null).asImageBitmap()
                    else -> null
                }
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(catColor(item.category).copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(iconFor(item.category), null, tint = catColor(item.category), modifier = Modifier.size(24.dp))
    }
}

@Composable
fun CategoryScreen(vm: ShareViewModel, cat: FileCategory, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    val selected by vm.selected.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortKey.DATE) }
    var menu by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addPicked(uris, cat)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    val shown = remember(items, query, sort) {
        items.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .let {
                when (sort) {
                    SortKey.NAME -> it.sortedBy { f -> f.name.lowercase() }
                    SortKey.SIZE -> it.sortedByDescending { f -> f.size }
                    SortKey.DATE -> it.sortedByDescending { f -> f.dateModified }
                }
            }
    }
    val allSelected = shown.isNotEmpty() && shown.all { selected.containsKey(it.id) }
    val canPick = cat in listOf(FileCategory.FILES, FileCategory.DOCUMENTS, FileCategory.DOWNLOADS, FileCategory.FOLDERS)
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(catTitle(cat), onBack) {
            IconButton(onClick = { if (allSelected) vm.deselect(shown) else vm.select(shown) }) {
                Icon(Icons.Rounded.SelectAll, tr("Select all", "انتخاب همه"))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Sort, tr("Sort", "مرتب‌سازی")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Name", "نام")) }, onClick = { sort = SortKey.NAME; menu = false })
                    DropdownMenuItem(text = { Text(tr("Size", "حجم")) }, onClick = { sort = SortKey.SIZE; menu = false })
                    DropdownMenuItem(text = { Text(tr("Date", "تاریخ")) }, onClick = { sort = SortKey.DATE; menu = false })
                }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(tr("Search", "جست‌وجو")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp)
        )
        if (canPick) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        if (cat == FileCategory.FOLDERS) pickTree.launch(null)
                        else pickFiles.launch(
                            if (cat == FileCategory.DOCUMENTS) arrayOf(
                                "application/pdf", "application/msword", "text/*",
                                "application/vnd.openxmlformats-officedocument.*",
                                "application/vnd.ms-excel", "application/vnd.ms-powerpoint"
                            ) else arrayOf("*/*")
                        )
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (cat == FileCategory.FOLDERS) Icons.Rounded.CreateNewFolder else Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (cat == FileCategory.FOLDERS) tr("Choose a folder", "انتخاب پوشه") else tr("Choose files", "انتخاب فایل‌ها"),
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                shown.isEmpty() -> Text(
                    if (canPick) tr("Nothing chosen yet.", "هنوز چیزی انتخاب نشده است.")
                    else tr("No files found. If you expected some, allow access when asked.", "فایلی پیدا نشد. اگر انتظار داشتید، دسترسی را تأیید کنید."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { f ->
                        val on = selected.containsKey(f.id)
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggle(f) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(f)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(
                                    "${formatSize(f.size)} · ${f.mime.substringAfterLast('/')} · ${df.format(Date(f.dateModified))}",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                if (on) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        SelectionSummary(vm)
        Spacer(Modifier.size(8.dp))
    }
}
