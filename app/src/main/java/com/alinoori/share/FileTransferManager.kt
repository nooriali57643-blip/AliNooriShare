package com.alinoori.share

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest

/**
 * Sends / receives files over an already-connected TCP [Socket] (see [Protocol]).
 * Every file is verified with SHA-256; incomplete files are written to ".part" files
 * and deleted on any failure so a broken transfer never leaves a corrupt file behind.
 */
class FileTransferManager(
    private val context: Context,
    private val settings: SettingsManager,
    private val history: TransferHistoryManager
) {
    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state

    @Volatile private var cancelled = false
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var decision: CompletableDeferred<Boolean>? = null

    fun reset() { _state.value = TransferState() }

    /** Called by the UI when the user taps Accept / Reject on an incoming offer. */
    fun respond(accept: Boolean) { decision?.complete(accept) }

    /** Cancels the running transfer by closing the socket. */
    fun cancel() {
        cancelled = true
        decision?.complete(false)
        runCatching { activeSocket?.close() }
    }

    // ------------------------------------------------------------------ progress meter

    private inner class Meter(val total: Long) {
        var done = 0L
        private var lastEmit = 0L
        private var lastBytes = 0L
        private var lastTime = System.nanoTime()
        private var speed = 0.0

        fun add(n: Int, file: String, index: Int, force: Boolean = false) {
            done += n
            val now = System.nanoTime()
            if (!force && now - lastTime < 150_000_000L) return
            val dt = (now - lastTime) / 1e9
            if (dt > 0) {
                val inst = (done - lastBytes) / dt
                speed = if (speed == 0.0) inst else speed * 0.7 + inst * 0.3
            }
            lastTime = now; lastBytes = done
            _state.update { it.copy(bytesDone = done, speedBps = speed, currentFile = file, fileIndex = index) }
        }
    }

    // ------------------------------------------------------------------ SEND

    suspend fun send(socket: Socket, items: List<ShareItem>, myName: String): Boolean = withContext(Dispatchers.IO) {
        cancelled = false
        activeSocket = socket
        val total = items.sumOf { it.size }
        var peer = ""
        _state.value = TransferState(
            phase = Phase.WAITING_CONFIRM, direction = Direction.SEND,
            fileCount = items.size, totalBytes = total
        )
        var ok = false
        var note = ""
        try {
            socket.soTimeout = 130_000
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), Protocol.BUFFER))
            val inp = DataInputStream(BufferedInputStream(socket.getInputStream(), Protocol.BUFFER))

            out.writeInt(Protocol.MAGIC)
            out.writeUTF(myName)
            out.writeInt(items.size)
            for (it in items) { out.writeUTF(it.name); out.writeLong(it.size) }
            out.flush()

            val d = inp.readUnsignedByte()
            peer = inp.readUTF()
            if (d != Protocol.ACCEPT) {
                note = "rejected"
            } else {
                socket.soTimeout = 30_000
                _state.update { it.copy(phase = Phase.TRANSFERRING, peerName = peer) }
                val meter = Meter(total)
                val buf = ByteArray(Protocol.BUFFER)
                items.forEachIndexed { idx, item ->
                    val md = MessageDigest.getInstance("SHA-256")
                    var sent = 0L
                    val stream = context.contentResolver.openInputStream(item.uri)
                        ?: throw IOException("Cannot open ${item.name}")
                    stream.use { s ->
                        while (sent < item.size) {
                            if (cancelled) throw IOException("cancelled")
                            val want = minOf(buf.size.toLong(), item.size - sent).toInt()
                            val n = s.read(buf, 0, want)
                            if (n < 0) throw IOException("File changed while sending: ${item.name}")
                            out.write(buf, 0, n)
                            md.update(buf, 0, n)
                            sent += n
                            meter.add(n, item.name, idx)
                        }
                    }
                    out.write(md.digest())
                    out.flush()
                    if (inp.readUnsignedByte() != 1) throw IOException("Receiver reported a corrupt file: ${item.name}")
                    meter.add(0, item.name, idx, force = true)
                }
                ok = true
            }
        } catch (e: Exception) {
            note = if (cancelled) "cancelled" else (e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { socket.close() }
            activeSocket = null
        }
        finish(Direction.SEND, peer, items.map { it.name }, total, ok, note)
        ok
    }

    // ------------------------------------------------------------------ RECEIVE

    suspend fun receive(socket: Socket, myName: String): Boolean = withContext(Dispatchers.IO) {
        cancelled = false
        activeSocket = socket
        _state.value = TransferState(phase = Phase.CONNECTING, direction = Direction.RECEIVE)
        var ok = false
        var note = ""
        var peer = ""
        var names = emptyList<String>()
        var total = 0L
        val partFiles = ArrayList<File>()
        try {
            socket.soTimeout = 30_000
            val inp = DataInputStream(BufferedInputStream(socket.getInputStream(), Protocol.BUFFER))
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), Protocol.BUFFER))

            if (inp.readInt() != Protocol.MAGIC) throw IOException("Not an Ali Noori Share device")
            peer = inp.readUTF()
            val count = inp.readInt()
            if (count < 1 || count > Protocol.MAX_FILES) throw IOException("Invalid file count")
            val rawNames = ArrayList<String>()
            val sizes = ArrayList<Long>()
            repeat(count) {
                rawNames += inp.readUTF()
                val sz = inp.readLong()
                if (sz < 0) throw IOException("Invalid file size")
                sizes += sz
            }
            names = rawNames.map { sanitize(it) }
            total = sizes.sum()

            // Not enough room? Refuse before anything is written.
            val free = FileScanner.receivedDir(context).usableSpace
            val needLocal = settings.state.value.saveTreeUri == null
            if (needLocal && total > free) {
                out.writeByte(Protocol.REJECT); out.writeUTF(myName); out.flush()
                throw IOException("Not enough free space (${formatSize(total)} needed)")
            }

            val offer = IncomingOffer(peer, names, sizes)
            val accept: Boolean = if (!settings.state.value.confirmNewDevice) {
                true
            } else {
                val d = CompletableDeferred<Boolean>()
                decision = d
                _state.value = TransferState(
                    phase = Phase.WAITING_CONFIRM, direction = Direction.RECEIVE,
                    peerName = peer, fileCount = count, totalBytes = total, offer = offer
                )
                val r = withTimeoutOrNull(120_000) { d.await() } ?: false
                decision = null
                r
            }
            out.writeByte(if (accept) Protocol.ACCEPT else Protocol.REJECT)
            out.writeUTF(myName)
            out.flush()
            if (!accept) {
                note = if (cancelled) "cancelled" else "rejected"
            } else {
                _state.value = TransferState(
                    phase = Phase.TRANSFERRING, direction = Direction.RECEIVE,
                    peerName = peer, fileCount = count, totalBytes = total
                )
                val meter = Meter(total)
                val buf = ByteArray(Protocol.BUFFER)
                val dir = FileScanner.receivedDir(context)
                for (idx in 0 until count) {
                    val size = sizes[idx]
                    val name = names[idx]
                    val part = File(dir, "${System.nanoTime()}.part")
                    partFiles += part
                    val md = MessageDigest.getInstance("SHA-256")
                    java.io.FileOutputStream(part).use { fos ->
                        var got = 0L
                        while (got < size) {
                            if (cancelled) throw IOException("cancelled")
                            val want = minOf(buf.size.toLong(), size - got).toInt()
                            val n = inp.read(buf, 0, want)
                            if (n < 0) throw IOException("Connection lost during $name")
                            fos.write(buf, 0, n)
                            md.update(buf, 0, n)
                            got += n
                            meter.add(n, name, idx)
                        }
                        fos.fd.sync()
                    }
                    val remote = ByteArray(32)
                    inp.readFully(remote)
                    if (!MessageDigest.isEqual(remote, md.digest())) {
                        out.writeByte(0); out.flush()
                        throw IOException("Checksum mismatch: $name")
                    }
                    publish(part, name)
                    partFiles.remove(part)
                    out.writeByte(1); out.flush()
                    meter.add(0, name, idx, force = true)
                }
                ok = true
                if (settings.state.value.notifyOnReceive) Notifier.received(context, count)
            }
        } catch (e: Exception) {
            note = if (cancelled) "cancelled" else (e.message ?: e.javaClass.simpleName)
        } finally {
            partFiles.forEach { runCatching { it.delete() } }   // never leave corrupt files
            runCatching { socket.close() }
            activeSocket = null
            decision = null
        }
        finish(Direction.RECEIVE, peer, names, total, ok, note)
        ok
    }

    // ------------------------------------------------------------------ helpers

    /** Moves a verified .part file to its final place, never overwriting an existing file. */
    private fun publish(part: File, name: String) {
        val tree = settings.state.value.saveTreeUri
        if (tree != null) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(tree)) ?: throw IOException("Save folder unavailable")
            val unique = uniqueName(name) { root.findFile(it) != null }
            val doc = root.createFile(FileScanner.mimeOf(unique), unique) ?: throw IOException("Cannot create $unique")
            context.contentResolver.openOutputStream(doc.uri)?.use { o -> part.inputStream().use { it.copyTo(o, Protocol.BUFFER) } }
                ?: throw IOException("Cannot write $unique")
            part.delete()
        } else {
            val dir = FileScanner.receivedDir(context)
            val unique = uniqueName(name) { File(dir, it).exists() }
            if (!part.renameTo(File(dir, unique))) {
                part.inputStream().use { i -> File(dir, unique).outputStream().use { i.copyTo(it, Protocol.BUFFER) } }
                part.delete()
            }
        }
    }

    private fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (exists("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    /** Strips any path and unsafe characters sent by the peer. */
    private fun sanitize(raw: String): String {
        val flat = raw.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }.joinToString("_")
        val clean = flat.replace(Regex("[:*?\"<>|\\u0000-\\u001F]"), "_").trim().take(150)
        return clean.ifEmpty { "file" }
    }

    private fun finish(dir: Direction, peer: String, names: List<String>, total: Long, ok: Boolean, note: String) {
        val phase = when {
            ok -> Phase.DONE
            cancelledOrRejected(note) -> Phase.CANCELLED
            else -> Phase.FAILED
        }
        _state.update {
            it.copy(phase = phase, direction = dir, peerName = peer.ifEmpty { it.peerName }, message = note, offer = null,
                bytesDone = if (ok) total else it.bytesDone, speedBps = 0.0)
        }
        if (names.isNotEmpty()) {
            history.add(HistoryEntry(System.currentTimeMillis(), System.currentTimeMillis(), dir, peer, names, total, ok, note))
        }
    }

    private fun cancelledOrRejected(note: String) = note == "cancelled" || note == "rejected"
}
