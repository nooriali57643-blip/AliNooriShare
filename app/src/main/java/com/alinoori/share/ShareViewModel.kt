package com.alinoori.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Socket

enum class Mode { NONE, SEND, RECEIVE }
enum class Stage { IDLE, WAITING, CONNECTING, TRANSFER, FINISHED }

data class Session(
    val mode: Mode = Mode.NONE,
    val stage: Stage = Stage.IDLE,
    /** "ip:port" shown by the receiver for hotspot / same-Wi-Fi connections. */
    val code: String? = null,
    val error: String? = null
)

class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val sa = app as ShareApp
    val settings get() = sa.settings
    val history get() = sa.history
    val transfer get() = sa.transfer
    val discovery get() = sa.discovery
    val connection get() = sa.connection

    // ---- file browsing / selection
    private val _items = MutableStateFlow<List<ShareItem>>(emptyList())
    val items: StateFlow<List<ShareItem>> = _items
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _selected = MutableStateFlow<Map<String, ShareItem>>(emptyMap())
    val selected: StateFlow<Map<String, ShareItem>> = _selected
    private val _category = MutableStateFlow<FileCategory?>(null)
    val category: StateFlow<FileCategory?> = _category
    private val pickedCache = HashMap<FileCategory, List<ShareItem>>()

    // ---- session
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session
    private var job: Job? = null

    fun openCategory(cat: FileCategory) {
        _category.value = cat
        when (cat) {
            FileCategory.PHOTOS, FileCategory.VIDEOS, FileCategory.MUSIC -> load { sa.scanner.scanMedia(cat) }
            FileCategory.APPS -> load { sa.scanner.scanApps() }
            FileCategory.RECEIVED -> load { sa.scanner.scanReceived(settings.state.value.saveTreeUri) }
            else -> _items.value = pickedCache[cat].orEmpty()
        }
    }

    private fun load(block: suspend () -> List<ShareItem>) {
        _loading.value = true
        viewModelScope.launch {
            _items.value = runCatching { block() }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    /** Files chosen with the system picker (Files / Documents / Downloads). */
    fun addPicked(uris: List<Uri>, cat: FileCategory) {
        viewModelScope.launch {
            val picked = sa.scanner.fromPicked(uris, cat)
            val merged = (pickedCache[cat].orEmpty() + picked).distinctBy { it.id }
            pickedCache[cat] = merged
            if (_category.value == cat) _items.value = merged
            select(picked)
        }
    }

    fun addFolder(tree: Uri) {
        viewModelScope.launch {
            _loading.value = true
            val files = sa.scanner.fromTree(tree)
            val merged = (pickedCache[FileCategory.FOLDERS].orEmpty() + files).distinctBy { it.id }
            pickedCache[FileCategory.FOLDERS] = merged
            if (_category.value == FileCategory.FOLDERS) _items.value = merged
            select(files)
            _loading.value = false
        }
    }

    fun toggle(item: ShareItem) = _selected.update { if (it.containsKey(item.id)) it - item.id else it + (item.id to item) }
    fun select(list: List<ShareItem>) = _selected.update { it + list.associateBy { i -> i.id } }
    fun deselect(list: List<ShareItem>) = _selected.update { it - list.map { i -> i.id }.toSet() }
    fun clearSelection() { _selected.value = emptyMap() }

    // ---- RECEIVE

    /** Caller must already hold the Wi-Fi Direct runtime permission. */
    fun startReceive() {
        if (_session.value.stage != Stage.IDLE && _session.value.stage != Stage.FINISHED) return
        transfer.reset()
        val ip = connection.localIpAddress()
        _session.value = Session(Mode.RECEIVE, Stage.WAITING, code = ip?.let { "$it:${Protocol.PORT}" })
        TransferService.start(sa)
        connection.start()
        discovery.start()   // makes this device visible to a nearby sender
        job = viewModelScope.launch {
            try {
                val socket = acquireReceiverSocket()
                _session.update { it.copy(stage = Stage.TRANSFER) }
                transfer.receive(socket, settings.state.value.deviceName)
            } catch (e: Exception) {
                if (_session.value.stage != Stage.IDLE)
                    _session.update { it.copy(error = e.message ?: "Connection failed") }
            } finally {
                endSession()
            }
        }
    }

    private suspend fun acquireReceiverSocket(): Socket = coroutineScope {
        val result = CompletableDeferred<Socket>()
        val accept = launch {
            try { result.complete(connection.acceptOne(10 * 60_000L)) }
            catch (e: Exception) { result.completeExceptionally(e) }
        }
        // If we ended up as Wi-Fi Direct *client*, the sender is the group owner: connect to it.
        val watcher = launch {
            val info = connection.groupInfo.filterNotNull().first { !it.isGroupOwner }
            try { result.complete(connection.connectTo(info.groupOwnerAddress.hostAddress ?: "", Protocol.PORT)) }
            catch (e: Exception) { result.completeExceptionally(e) }
        }
        try { result.await() } finally {
            accept.cancel(); watcher.cancel(); connection.closeServer()
        }
    }

    // ---- SEND

    fun startDiscovery() {
        _session.value = Session(Mode.SEND, Stage.WAITING)
        connection.start()
        discovery.start()
    }

    fun sendToPeer(peer: PeerDevice) = runSend {
        val err = connection.connectP2p(peer.address)
        if (err != null) throw java.io.IOException(err)
        connection.socketFromGroup()
    }

    /** Hotspot / same Wi-Fi: [code] is "ip" or "ip:port" shown on the receiving phone. */
    fun sendToCode(code: String) = runSend {
        val parts = code.trim().split(":")
        val host = parts[0].trim()
        val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: Protocol.PORT
        if (host.isEmpty()) throw java.io.IOException("Invalid code")
        connection.connectTo(host, port, attempts = 5)
    }

    private fun runSend(connect: suspend () -> Socket) {
        val files = _selected.value.values.toList()
        if (files.isEmpty()) return
        transfer.reset()
        _session.value = Session(Mode.SEND, Stage.CONNECTING)
        TransferService.start(sa)
        job = viewModelScope.launch {
            try {
                val socket = connect()
                _session.update { it.copy(stage = Stage.TRANSFER) }
                if (transfer.send(socket, files, settings.state.value.deviceName)) clearSelection()
            } catch (e: Exception) {
                if (_session.value.stage != Stage.IDLE)
                    _session.update { it.copy(error = e.message ?: "Connection failed") }
            } finally {
                endSession()
            }
        }
    }

    /** User pressed cancel / back: aborts everything and releases the network. */
    fun cancel() {
        transfer.cancel()
        job?.cancel()
        endSession(reset = true)
    }

    private fun endSession(reset: Boolean = false) {
        connection.closeServer()
        connection.disconnect()
        discovery.stop()
        connection.stop()
        TransferService.stop(sa)
        _session.update { if (reset) Session() else it.copy(stage = Stage.FINISHED) }
    }

    fun dismissResult() {
        transfer.reset()
        _session.value = Session()
    }

    override fun onCleared() {
        super.onCleared()
        connection.stop()
        discovery.stop()
    }
}
