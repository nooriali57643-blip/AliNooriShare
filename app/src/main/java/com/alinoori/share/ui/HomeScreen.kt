package com.alinoori.share.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alinoori.share.FileCategory
import com.alinoori.share.ShareViewModel
import com.alinoori.share.formatSize

@Composable
fun Logo(size: Int = 88) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(BrandBrush),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Rounded.SwapVert, null, tint = Color.White, modifier = Modifier.size((size * 0.6).dp)) }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Logo(120)
        Spacer(Modifier.height(24.dp))
        Text("Ali Noori Share", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Text(
            tr(
                "Send photos, videos, music, apps and files directly between two Android phones — no internet needed.",
                "عکس، ویدیو، موسیقی، برنامه و فایل را بدون نیاز به اینترنت و مستقیم میان دو گوشی اندروید منتقل کنید."
            ),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp
        )
        Spacer(Modifier.height(36.dp))
        GradientButton(tr("Get started", "شروع"), Icons.AutoMirrored.Rounded.Send, Modifier.fillMaxWidth(), onClick = onStart)
    }
}

@Composable
fun SelectionSummary(vm: ShareViewModel) {
    val sel by vm.selected.collectAsState()
    if (sel.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tr("${sel.size} selected · ${formatSize(sel.values.sumOf { it.size })}",
                "${sel.size} مورد انتخاب‌شده · ${formatSize(sel.values.sumOf { it.size })}"),
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            tr("Clear", "پاک کردن"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickableNoRipple { vm.clearSelection() }.padding(6.dp)
        )
    }
}

@Composable
fun CategoryGrid(cats: List<FileCategory>, onOpen: (FileCategory) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cats.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c -> CategoryCard(c, Modifier.weight(1f)) { onOpen(c) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: ShareViewModel, onSend: () -> Unit, onReceive: () -> Unit, onOpen: (FileCategory) -> Unit) {
    val sel by vm.selected.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(48)
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Ali Noori Share", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(tr("Works without internet", "بدون نیاز به اینترنت"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientButton(tr("Send", "ارسال فایل"), Icons.AutoMirrored.Rounded.Send, Modifier.weight(1f), enabled = sel.isNotEmpty(), onClick = onSend)
            GradientButton(tr("Receive", "دریافت فایل"), Icons.Rounded.Download, Modifier.weight(1f), onClick = onReceive)
        }
        if (sel.isEmpty()) {
            Text(
                tr("Choose files below, then tap Send.", "ابتدا فایل‌ها را از پایین انتخاب کنید، سپس «ارسال» را بزنید."),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        } else Spacer(Modifier.height(8.dp))
        SelectionSummary(vm)
        SectionLabel(tr("Browse", "مرور فایل‌ها"))
        CategoryGrid(FileCategory.values().toList(), onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FilesTab(vm: ShareViewModel, onOpen: (FileCategory) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        TopBar(tr("Files", "فایل‌ها"), null)
        SelectionSummary(vm)
        Spacer(Modifier.height(8.dp))
        CategoryGrid(FileCategory.values().toList(), onOpen)
        Spacer(Modifier.height(24.dp))
    }
}
