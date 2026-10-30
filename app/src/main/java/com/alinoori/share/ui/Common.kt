package com.alinoori.share.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alinoori.share.FileCategory

fun iconFor(cat: FileCategory): ImageVector = when (cat) {
    FileCategory.PHOTOS -> Icons.Rounded.Image
    FileCategory.VIDEOS -> Icons.Rounded.VideoLibrary
    FileCategory.MUSIC -> Icons.Rounded.MusicNote
    FileCategory.APPS -> Icons.Rounded.Android
    FileCategory.FILES -> Icons.Rounded.InsertDriveFile
    FileCategory.FOLDERS -> Icons.Rounded.Folder
    FileCategory.DOCUMENTS -> Icons.Rounded.Description
    FileCategory.DOWNLOADS -> Icons.Rounded.Download
    FileCategory.RECEIVED -> Icons.Rounded.MoveToInbox
}

@Composable
fun catTitle(cat: FileCategory): String = when (cat) {
    FileCategory.PHOTOS -> tr("Photos", "عکس‌ها")
    FileCategory.VIDEOS -> tr("Videos", "ویدیوها")
    FileCategory.MUSIC -> tr("Music", "موسیقی")
    FileCategory.APPS -> tr("Apps", "برنامه‌ها")
    FileCategory.FILES -> tr("Files", "فایل‌ها")
    FileCategory.FOLDERS -> tr("Folders", "پوشه‌ها")
    FileCategory.DOCUMENTS -> tr("Documents", "اسناد")
    FileCategory.DOWNLOADS -> tr("Downloads", "دانلودها")
    FileCategory.RECEIVED -> tr("Received", "دریافت‌شده‌ها")
}

fun catColor(cat: FileCategory): Color = when (cat) {
    FileCategory.PHOTOS -> Color(0xFFEF5DA8)
    FileCategory.VIDEOS -> Color(0xFFFF7043)
    FileCategory.MUSIC -> Color(0xFF26A69A)
    FileCategory.APPS -> Color(0xFF66BB6A)
    FileCategory.FILES -> Color(0xFF2F6BFF)
    FileCategory.FOLDERS -> Color(0xFFFFB300)
    FileCategory.DOCUMENTS -> Color(0xFF5C6BC0)
    FileCategory.DOWNLOADS -> Color(0xFF7C4DFF)
    FileCategory.RECEIVED -> Color(0xFF00ACC1)
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) BrandBrush else androidx.compose.ui.graphics.SolidColor(Color(0xFF9FA3B8)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryCard(cat: FileCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(catColor(cat).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(iconFor(cat), null, tint = catColor(cat), modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(catTitle(cat), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "بازگشت")) }
        } else Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

fun formatDuration(sec: Long): String {
    if (sec < 0) return "--"
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
