package com.alinoori.share.ui

import android.content.Intent
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alinoori.share.FileScanner
import com.alinoori.share.ShareViewModel
import com.alinoori.share.ThemeMode
import com.alinoori.share.formatSize

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsTab(vm: ShareViewModel, onAbout: () -> Unit, onHelp: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.state.collectAsState()
    var name by remember(s.deviceName) { mutableStateOf(s.deviceName) }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.settings.setSaveTree(uri)
        }
    }

    // Storage figures
    val dir = remember { FileScanner.receivedDir(ctx) }
    val free = remember(s.saveTreeUri) { runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L) }
    val used = remember(s.saveTreeUri) { dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(tr("Settings", "تنظیمات"), null)

        SectionLabel(tr("Device", "دستگاه"))
        Group {
            OutlinedTextField(
                value = name, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text(tr("Device name shown to others", "نام دستگاه برای دیگران")) },
                onValueChange = { name = it; if (it.isNotBlank()) vm.settings.setDeviceName(it) }
            )
        }

        SectionLabel(tr("Language & theme", "زبان و تم"))
        Group {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.language == "fa", onClick = { vm.settings.setLanguage("fa") }, label = { Text("فارسی") })
                FilterChip(selected = s.language == "en", onClick = { vm.settings.setLanguage("en") }, label = { Text("English") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.theme == ThemeMode.SYSTEM, onClick = { vm.settings.setTheme(ThemeMode.SYSTEM) }, label = { Text(tr("System", "سیستم")) })
                FilterChip(selected = s.theme == ThemeMode.LIGHT, onClick = { vm.settings.setTheme(ThemeMode.LIGHT) }, label = { Text(tr("Light", "روشن")) })
                FilterChip(selected = s.theme == ThemeMode.DARK, onClick = { vm.settings.setTheme(ThemeMode.DARK) }, label = { Text(tr("Dark", "تاریک")) })
            }
        }

        SectionLabel(tr("Receiving", "دریافت"))
        Group {
            Text(tr("Save folder", "پوشه ذخیره فایل‌ها"), fontWeight = FontWeight.Medium)
            Text(
                if (s.saveTreeUri == null) tr("App folder: ", "پوشه برنامه: ") + dir.path
                else android.net.Uri.parse(s.saveTreeUri).lastPathSegment.orEmpty(),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickTree.launch(null) }) { Text(tr("Choose folder", "انتخاب پوشه")) }
                if (s.saveTreeUri != null) OutlinedButton(onClick = { vm.settings.setSaveTree(null) }) { Text(tr("Use app folder", "پوشه برنامه")) }
            }
            SwitchRow(tr("Ask before receiving files", "تأیید قبل از دریافت فایل"), s.confirmNewDevice) { vm.settings.setConfirm(it) }
            SwitchRow(tr("Notify when files are received", "اعلان هنگام دریافت فایل"), s.notifyOnReceive) { vm.settings.setNotify(it) }
        }

        SectionLabel(tr("Storage", "فضای ذخیره‌سازی"))
        Group {
            Text(tr("Received files in app folder: ", "فایل‌های دریافتی در پوشه برنامه: ") + formatSize(used))
            Text(tr("Free space: ", "فضای آزاد: ") + formatSize(free))
            OutlinedButton(onClick = { vm.history.clear() }) { Text(tr("Clear transfer history", "پاک کردن تاریخچه انتقال‌ها")) }
        }

        SectionLabel(tr("About", "درباره"))
        Group {
            Text(tr("Version ", "نسخه ") + version)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHelp) { Text(tr("Help & troubleshooting", "راهنما و عیب‌یابی")) }
                OutlinedButton(onClick = onAbout) { Text(tr("About app", "درباره برنامه")) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(tr("About", "درباره برنامه"), onBack)
        Spacer(Modifier.height(32.dp))
        Logo(110)
        Spacer(Modifier.height(16.dp))
        Text("Ali Noori Share", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(tr("Created by Ali Noori", "ساخته‌شده توسط Ali Noori"), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            tr("Offline file sharing between Android phones. Files go directly from phone to phone; nothing is uploaded anywhere.",
                "اشتراک‌گذاری فایل بدون اینترنت میان گوشی‌های اندروید. فایل‌ها مستقیم از گوشی به گوشی می‌روند و هیچ چیزی به جایی آپلود نمی‌شود."),
            modifier = Modifier.padding(horizontal = 28.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(tr("Help & troubleshooting", "راهنما و عیب‌یابی"), onBack)
        Group {
            Text(tr("How to send", "روش ارسال"), fontWeight = FontWeight.Bold)
            Text(tr("1. Select files on the sender.\n2. On the other phone tap Receive.\n3. On the sender tap Send and choose the other phone.\n4. Accept the connection and the file offer on the receiver.",
                "۱. در گوشی فرستنده فایل‌ها را انتخاب کنید.\n۲. در گوشی دیگر «دریافت فایل» را بزنید.\n۳. در فرستنده «ارسال فایل» را بزنید و گوشی مقابل را انتخاب کنید.\n۴. در گیرنده اتصال و پیشنهاد فایل را قبول کنید."))
        }
        Group {
            Text(tr("Phone not found?", "گوشی پیدا نمی‌شود؟"), fontWeight = FontWeight.Bold)
            Text(tr("• Turn Wi-Fi on on both phones (it does not need to be connected).\n• Android 12 and below: turn Location on.\n• Allow the permissions when asked.\n• Keep both screens on and close to each other.\n• Tap refresh on the Send screen.\n• Otherwise use the hotspot method: turn on a hotspot on one phone, connect the other to it, and type the code shown on the receiver.",
                "• وای‌فای را در هر دو گوشی روشن کنید (نیازی به اتصال به شبکه نیست).\n• اندروید ۱۲ و پایین‌تر: موقعیت‌یاب (Location) را روشن کنید.\n• مجوزها را هنگام درخواست تأیید کنید.\n• صفحه هر دو گوشی روشن و دو گوشی نزدیک هم باشند.\n• در صفحه ارسال دکمه جست‌وجوی مجدد را بزنید.\n• در غیر این صورت از روش هات‌اسپات استفاده کنید: در یک گوشی هات‌اسپات را روشن کنید، گوشی دیگر را به آن وصل کنید و کد نمایش‌داده‌شده در گیرنده را وارد کنید."))
        }
        Group {
            Text(tr("Good to know", "نکات مهم"), fontWeight = FontWeight.Bold)
            Text(tr("• Pause/resume is not supported; a cancelled or interrupted transfer must be restarted and never leaves a broken file.\n• Apps are shared as the base APK only.\n• Files with the same name are saved with a number, never overwritten.",
                "• توقف و ادامه پشتیبانی نمی‌شود؛ انتقال لغوشده یا قطع‌شده باید دوباره شروع شود و هیچ‌وقت فایل خراب باقی نمی‌ماند.\n• برنامه‌ها فقط به‌صورت APK اصلی به اشتراک گذاشته می‌شوند.\n• فایل‌های هم‌نام با شماره ذخیره می‌شوند و جایگزین نمی‌شوند."))
        }
    }
}
