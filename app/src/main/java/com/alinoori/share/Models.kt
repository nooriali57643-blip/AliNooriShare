package com.alinoori.share

import android.net.Uri

enum class FileCategory { PHOTOS, VIDEOS, MUSIC, APPS, FILES, FOLDERS, DOCUMENTS, DOWNLOADS, RECEIVED }

/** One selectable/shareable file. [uri] is always readable through ContentResolver. */
data class ShareItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val dateModified: Long,
    val category: FileCategory
)

data class PeerDevice(
    val name: String,
    /** Wi-Fi Direct MAC address, or "ip:port" for manual/hotspot connections. */
    val address: String,
    val viaWifiDirect: Boolean
)

enum class Direction { SEND, RECEIVE }

enum class Phase {
    IDLE, DISCOVERING, CONNECTING, WAITING_CONFIRM, TRANSFERRING, DONE, FAILED, CANCELLED
}

data class IncomingOffer(
    val deviceName: String,
    val names: List<String>,
    val sizes: List<Long>
) {
    val totalBytes: Long get() = sizes.sum()
}

data class TransferState(
    val phase: Phase = Phase.IDLE,
    val direction: Direction = Direction.SEND,
    val peerName: String = "",
    val currentFile: String = "",
    val fileIndex: Int = 0,
    val fileCount: Int = 0,
    val bytesDone: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Double = 0.0,
    val message: String = "",
    val offer: IncomingOffer? = null
) {
    val progress: Float get() = if (totalBytes <= 0) 0f else (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f)
    val etaSeconds: Long get() = if (speedBps < 1.0) -1 else ((totalBytes - bytesDone) / speedBps).toLong()
    val active: Boolean get() = phase == Phase.CONNECTING || phase == Phase.WAITING_CONFIRM || phase == Phase.TRANSFERRING
}

data class HistoryEntry(
    val id: Long,
    val time: Long,
    val direction: Direction,
    val peerName: String,
    val fileNames: List<String>,
    val totalBytes: Long,
    val success: Boolean,
    val note: String
)

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
}
