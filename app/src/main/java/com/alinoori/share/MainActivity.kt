package com.alinoori.share

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.alinoori.share.ui.AboutScreen
import com.alinoori.share.ui.AppTheme
import com.alinoori.share.ui.CategoryScreen
import com.alinoori.share.ui.FilesTab
import com.alinoori.share.ui.HelpScreen
import com.alinoori.share.ui.HomeScreen
import com.alinoori.share.ui.ReceiveScreen
import com.alinoori.share.ui.SendScreen
import com.alinoori.share.ui.SettingsTab
import com.alinoori.share.ui.TransfersTab
import com.alinoori.share.ui.WelcomeScreen
import com.alinoori.share.ui.tr

/** Screens that are drawn on top of the bottom-bar tabs. */
private enum class Overlay { NONE, CATEGORY, SEND, RECEIVE, ABOUT, HELP }

class MainActivity : AppCompatActivity() {

    private val vm: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.state.collectAsState()
            AppTheme(mode = settings.theme, fa = settings.language == "fa") {
                if (!settings.onboarded) {
                    WelcomeScreen(onStart = { vm.settings.setOnboarded() })
                } else {
                    MainScreen(vm)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- permissions

/** Runtime permission needed to LIST a media category (images, video, audio). */
private fun mediaPermission(cat: FileCategory): String? = when (cat) {
    FileCategory.PHOTOS ->
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    FileCategory.VIDEOS ->
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    FileCategory.MUSIC ->
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    else -> null // Apps, Received and the picker based categories need no permission
}

/** Wi-Fi Direct discovery: NEARBY_WIFI_DEVICES on Android 13+, location before that. */
private fun connectionPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

// ---------------------------------------------------------------- main screen

@Composable
private fun MainScreen(vm: ShareViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var categoryName by rememberSaveable { mutableStateOf(FileCategory.PHOTOS.name) }
    val category = FileCategory.valueOf(categoryName)

    // Runs [pending] once the permission dialog has been answered. Denied permissions never block
    // the app: media lists simply stay empty and Wi-Fi Direct reports a clear error (the
    // hotspot / same-Wi-Fi method needs none of them).
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending
        pending = null
        action?.invoke()
    }

    fun withPermissions(perms: Array<String>, action: () -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun openCategory(cat: FileCategory) {
        val open = {
            categoryName = cat.name
            vm.openCategory(cat)
            overlay = Overlay.CATEGORY
        }
        val perm = mediaPermission(cat)
        if (perm == null) open() else withPermissions(arrayOf(perm), open)
    }

    fun openSend() = withPermissions(connectionPermissions()) { overlay = Overlay.SEND }
    fun openReceive() = withPermissions(connectionPermissions()) { overlay = Overlay.RECEIVE }

    fun closeOverlay() {
        // Leaving the send / receive screens always aborts the session and frees the network.
        if (overlay == Overlay.SEND || overlay == Overlay.RECEIVE) vm.cancel()
        overlay = Overlay.NONE
    }

    BackHandler(enabled = overlay != Overlay.NONE) { closeOverlay() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (overlay == Overlay.NONE) {
                NavigationBar {
                    val tabs: List<Triple<String, ImageVector, Int>> = listOf(
                        Triple(tr("Home", "خانه"), Icons.Rounded.Home, 0),
                        Triple(tr("Files", "فایل‌ها"), Icons.Rounded.Folder, 1),
                        Triple(tr("Transfers", "انتقال‌ها"), Icons.Rounded.SwapVert, 2),
                        Triple(tr("Settings", "تنظیمات"), Icons.Rounded.Settings, 3)
                    )
                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
            when (overlay) {
                Overlay.CATEGORY -> CategoryScreen(vm, category, onBack = { closeOverlay() })
                Overlay.SEND -> SendScreen(vm, onBack = { overlay = Overlay.NONE }, onOpenReceived = { openCategory(FileCategory.RECEIVED) })
                Overlay.RECEIVE -> ReceiveScreen(vm, onBack = { overlay = Overlay.NONE }, onOpenReceived = { openCategory(FileCategory.RECEIVED) })
                Overlay.ABOUT -> AboutScreen(onBack = { closeOverlay() })
                Overlay.HELP -> HelpScreen(onBack = { closeOverlay() })
                Overlay.NONE -> when (tab) {
                    0 -> HomeScreen(vm, onSend = { openSend() }, onReceive = { openReceive() }, onOpen = { openCategory(it) })
                    1 -> FilesTab(vm, onOpen = { openCategory(it) })
                    2 -> TransfersTab(vm, onOpenReceived = { openCategory(FileCategory.RECEIVED) })
                    else -> SettingsTab(vm, onAbout = { overlay = Overlay.ABOUT }, onHelp = { overlay = Overlay.HELP })
                }
            }
        }
    }
}
