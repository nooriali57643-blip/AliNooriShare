package com.alinoori.share.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alinoori.share.Direction
import com.alinoori.share.Phase
import com.alinoori.share.ShareViewModel
import com.alinoori.share.Stage
import com.alinoori.share.formatSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun noteText(note: String): String = when (note) {
    "rejected" -> tr("The other device declined the transfer.", "دستگاه مقابل انتقال را رد کرد.")
    "cancelled" -> tr("Transfer cancelled.", "انتقال لغو شد.")
    "" -> ""
    else -> note
}

/** Live status of the running (or just finished) transfer, with accept/reject and cancel. */
@Composable
fun TransferPanel(vm: ShareViewModel, onOpenReceived: () -> Unit) {
    val st by vm.transfer.state.collectAsState()
    val session by vm.session.collectAsState()
    val card = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surface).padding(18.dp)

    when (st.phase) {
        Phase.WAITING_CONFIRM -> {
            val offer = st.offer
            Column(card, horizontalAlignment = Alignment.CenterHorizontally) {
                if (offer != null) {
                    Text(tr("${offer.deviceName} wants to send you", "${offer.deviceName} می‌خواهد برای شما ارسال کند"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    offer.names.take(5).forEachIndexed { i, n ->
                        Text("${n}  (${formatSize(offer.sizes[i])})", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    }
                    if (offer.names.size > 5) Text(tr("+ ${offer.names.size - 5} more", "و ${offer.names.size - 5} مورد دیگر"), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Total: ", "مجموع: ") + formatSize(offer.totalBytes), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.transfer.respond(false) }) { Text(tr("Reject", "رد")) }
                        Button(onClick = { vm.transfer.respond(true) }) { Text(tr("Accept", "قبول")) }
                    }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Waiting for the other phone to accept…", "در انتظار تأیید دستگاه مقابل…"))
                    TextButton(onClick = { vm.cancel() }) { Text(tr("Cancel", "لغو")) }
                }
            }
        }
        Phase.TRANSFERRING -> Column(card) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (st.direction == Direction.SEND) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (st.direction == Direction.SEND) tr("Sending to ", "ارسال به ") else tr("Receiving from ", "دریافت از ")) + st.peerName,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(st.currentFile, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text("${minOf(st.fileIndex + 1, st.fileCount)} / ${st.fileCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(st.progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                Text(String.format(Locale.US, "%.1f MB/s", st.speedBps / 1048576.0))
                Text(tr("ETA ", "باقی‌مانده ") + formatDuration(st.etaSeconds))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Done ", "منتقل‌شده ") + formatSize(st.bytesDone) + tr(" · Left ", " · باقی‌مانده ") + formatSize((st.totalBytes - st.bytesDone).coerceAtLeast(0)),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) { Text(tr("Cancel transfer", "لغو انتقال")) }
        }
        Phase.DONE, Phase.FAILED, Phase.CANCELLED -> Column(card, horizontalAlignment = Alignment.CenterHorizontally) {
            val ok = st.phase == Phase.DONE
            Icon(
                if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                tint = if (ok) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (ok) tr("Transfer complete (${formatSize(st.totalBytes)})", "انتقال با موفقیت انجام شد (${formatSize(st.totalBytes)})")
                else if (st.phase == Phase.CANCELLED) noteText(st.message)
                else tr("Transfer failed", "انتقال ناموفق بود") + "\n" + noteText(st.message) + (session.error?.let { "\n$it" } ?: ""),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ok && st.direction == Direction.RECEIVE) OutlinedButton(onClick = { vm.dismissResult(); onOpenReceived() }) { Text(tr("Open received files", "باز کردن دریافت‌ها")) }
                Button(onClick = { vm.dismissResult() }) { Text(tr("OK", "تأیید")) }
            }
        }
        else -> {
            // Connection failed before the transfer even started
            val err = session.error
            if (err != null && session.stage == Stage.FINISHED) {
                Column(card, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Could not connect", "اتصال برقرار نشد"), fontWeight = FontWeight.Bold)
                    Text(err, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.dismissResult() }) { Text(tr("OK", "تأیید")) }
                }
            }
        }
    }
}

@Composable
fun TransfersTab(vm: ShareViewModel, onOpenReceived: () -> Unit) {
    val history by vm.history.entries.collectAsState()
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(tr("Transfers", "انتقال‌ها"), null)
        TransferPanel(vm, onOpenReceived)
        SectionLabel(tr("History", "تاریخچه"))
        if (history.isEmpty()) {
            Text(tr("No transfers yet.", "هنوز انتقالی انجام نشده است."), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.weight(1f)) {
            items(history, key = { it.id }) { h ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (h.direction == Direction.SEND) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Download, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            h.fileNames.firstOrNull().orEmpty() + if (h.fileNames.size > 1) "  +${h.fileNames.size - 1}" else "",
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${h.peerName.ifEmpty { "?" }} · ${formatSize(h.totalBytes)} · ${df.format(Date(h.time))}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        if (h.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                        tint = if (h.success) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
