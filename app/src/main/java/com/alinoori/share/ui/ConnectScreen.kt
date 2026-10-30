package com.alinoori.share.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alinoori.share.Mode
import com.alinoori.share.Phase
import com.alinoori.share.ShareViewModel
import com.alinoori.share.Stage
import com.alinoori.share.formatSize
import com.alinoori.share.qrBitmap

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

/** SEND flow: find nearby devices (Wi-Fi Direct) or type the code shown on the receiver. */
@Composable
fun SendScreen(vm: ShareViewModel, onBack: () -> Unit, onOpenReceived: () -> Unit) {
    val session by vm.session.collectAsState()
    val peers by vm.discovery.peers.collectAsState()
    val p2pOn by vm.discovery.p2pEnabled.collectAsState()
    val err by vm.discovery.error.collectAsState()
    val sel by vm.selected.collectAsState()
    val ts by vm.transfer.state.collectAsState()
    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { if (session.mode != Mode.SEND) vm.startDiscovery() }

    val busy = session.stage == Stage.CONNECTING || session.stage == Stage.TRANSFER ||
        ts.phase != Phase.IDLE || (session.stage == Stage.FINISHED)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(tr("Send", "ارسال"), onBack = { vm.cancel(); onBack() }) {
            if (!busy) IconButton(onClick = { vm.discovery.start() }) { Icon(Icons.Rounded.Refresh, tr("Refresh", "جست‌وجوی مجدد")) }
        }
        Hint(tr("${sel.size} files · ${formatSize(sel.values.sumOf { it.size })}", "${sel.size} فایل · ${formatSize(sel.values.sumOf { it.size })}"))

        if (busy) {
            if (session.stage == Stage.CONNECTING && ts.phase == Phase.IDLE) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Connecting… The other phone may ask you to accept the connection.", "در حال اتصال… ممکن است گوشی مقابل از شما تأیید اتصال بخواهد."), fontSize = 14.sp)
                }
            }
            TransferPanel(vm, onOpenReceived)
        } else {
            SectionLabel(tr("Nearby devices (Wi-Fi Direct)", "دستگاه‌های نزدیک (Wi-Fi Direct)"))
            if (!vm.connection.wifiDirectSupported) {
                Hint(tr("Wi-Fi Direct is not supported on this phone. Use the hotspot / same Wi-Fi method below.", "این گوشی از Wi-Fi Direct پشتیبانی نمی‌کند. از روش هات‌اسپات / وای‌فای مشترک در پایین استفاده کنید."))
            } else {
                if (!p2pOn) Hint(tr("Turn Wi-Fi on to find devices.", "برای پیدا کردن دستگاه‌ها وای‌فای را روشن کنید."))
                err?.let { Hint(it) }
                if (peers.isEmpty()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Searching… On the other phone open Ali Noori Share and tap Receive.", "در حال جست‌وجو… در گوشی مقابل برنامه را باز کرده و «دریافت فایل» را بزنید."), fontSize = 14.sp)
                    }
                    if (Build.VERSION.SDK_INT in 24..32) Hint(tr("On Android 12 and below, Location must be turned on for device discovery.", "در اندروید ۱۲ و پایین‌تر برای پیدا کردن دستگاه‌ها باید موقعیت‌یاب (Location) روشن باشد."))
                }
                peers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface).clickable { vm.sendToPeer(p) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(BrandBrush), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhoneAndroid, null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(tr("Tap to connect and send", "برای اتصال و ارسال بزنید"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionLabel(tr("Hotspot / same Wi-Fi", "هات‌اسپات / وای‌فای مشترک"))
            Hint(tr("Connect both phones to the same Wi-Fi or to one phone's hotspot. Enter the code shown on the receiving phone.", "هر دو گوشی را به یک وای‌فای یا هات‌اسپات یکی از گوشی‌ها وصل کنید و کدی را که در گوشی گیرنده نمایش داده می‌شود وارد کنید."))
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it }, singleLine = true,
                    placeholder = { Text("192.168.43.1:8988") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { vm.sendToCode(code) }, enabled = code.isNotBlank()) { Text(tr("Send", "ارسال")) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** RECEIVE flow: becomes visible, shows the code/QR, asks before accepting, shows progress. */
@Composable
fun ReceiveScreen(vm: ShareViewModel, onBack: () -> Unit, onOpenReceived: () -> Unit) {
    val session by vm.session.collectAsState()
    val ts by vm.transfer.state.collectAsState()
    val settings by vm.settings.state.collectAsState()
    val ownP2pHint = !vm.connection.wifiDirectSupported

    LaunchedEffect(Unit) { if (session.mode != Mode.RECEIVE) vm.startReceive() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(tr("Receive", "دریافت"), onBack = { vm.cancel(); onBack() })

        val showPanel = ts.phase != Phase.IDLE || session.stage == Stage.FINISHED
        if (showPanel) {
            TransferPanel(vm, onOpenReceived)
        } else {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text(tr("Waiting for a sender…", "در انتظار فرستنده…"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Hint(tr("This phone is visible as “${settings.deviceName}”. On the other phone choose Send and tap this device.", "این گوشی با نام «${settings.deviceName}» قابل مشاهده است. در گوشی دیگر «ارسال» را بزنید و این دستگاه را انتخاب کنید."))
            if (ownP2pHint) Hint(tr("Wi-Fi Direct is not supported here; use the code below.", "Wi-Fi Direct روی این گوشی پشتیبانی نمی‌شود؛ از کد زیر استفاده کنید."))

            SectionLabel(tr("Hotspot / same Wi-Fi code", "کد هات‌اسپات / وای‌فای مشترک"))
            val code = session.code
            if (code != null) {
                Image(qrBitmap(code, 420), null, modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(8.dp))
                Text(code, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Hint(tr("Type this code on the sender, or scan the QR with any scanner app and type the text.", "این کد را در گوشی فرستنده وارد کنید (یا QR را با هر برنامه اسکنر بخوانید و متن را وارد کنید)."))
            } else {
                Hint(tr("Not connected to Wi-Fi or a hotspot. Connect to one (or turn on your hotspot) to get a code.", "به وای‌فای یا هات‌اسپات وصل نیستید. برای دریافت کد به یکی از آن‌ها وصل شوید یا هات‌اسپات خود را روشن کنید."))
            }
            session.error?.let { Hint(it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
