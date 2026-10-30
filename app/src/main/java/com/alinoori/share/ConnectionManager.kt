package com.alinoori.share

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

/**
 * Owns the Wi-Fi Direct channel and turns "connected to a peer" into a plain TCP [Socket].
 *
 * Wi-Fi Direct: the group owner (GO) listens on [Protocol.PORT]; the other side connects to the GO.
 * Which side is GO is decided by Android, so the TCP direction is independent of who sends files.
 *
 * Hotspot / same Wi-Fi network (fallback): the receiver listens and shows its IP code;
 * the sender types that code.
 */
class ConnectionManager(context: Context) {
    private val app = context.applicationContext

    val manager: WifiP2pManager? = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    val channel: WifiP2pManager.Channel? = manager?.initialize(app, app.mainLooper, null)

    /** False on devices without Wi-Fi Direct; the UI then offers the hotspot/Wi-Fi method only. */
    val wifiDirectSupported: Boolean get() = manager != null && channel != null

    private val _info = MutableStateFlow<WifiP2pInfo?>(null)
    val groupInfo: StateFlow<WifiP2pInfo?> = _info

    private var serverSocket: ServerSocket? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
            val m = manager ?: return
            val ch = channel ?: return
            m.requestConnectionInfo(ch) { info ->
                _info.value = if (info != null && info.groupFormed) info else null
            }
        }
    }

    fun start() {
        if (receiverRegistered || !wifiDirectSupported) return
        val f = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        androidx.core.content.ContextCompat.registerReceiver(
            app, receiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    fun stop() {
        if (receiverRegistered) {
            runCatching { app.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    /** Starts a Wi-Fi Direct connection to [address]. Returns null on success or an error text. */
    @SuppressLint("MissingPermission")
    suspend fun connectP2p(address: String): String? {
        val m = manager ?: return "Wi-Fi Direct not supported"
        val ch = channel ?: return "Wi-Fi Direct not supported"
        val cfg = WifiP2pConfig().apply {
            deviceAddress = address
            wps.setup = android.net.wifi.WpsInfo.PBC
        }
        return suspendCancellableCoroutine { cont ->
            try {
                m.connect(ch, cfg, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { if (cont.isActive) cont.resume(null) }
                    override fun onFailure(reason: Int) { if (cont.isActive) cont.resume(reasonText(reason)) }
                })
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume("Permission denied")
            }
        }
    }

    /** Waits until the Wi-Fi Direct group is formed, then returns a connected socket. */
    suspend fun socketFromGroup(timeoutMs: Long = 60_000): Socket = withContext(Dispatchers.IO) {
        var waited = 0L
        var info = _info.value
        while (info == null && waited < timeoutMs) {
            delay(300); waited += 300; info = _info.value
        }
        info ?: throw java.io.IOException("Wi-Fi Direct group was not formed")
        if (info.isGroupOwner) acceptOne(timeoutMs) else connectTo(info.groupOwnerAddress.hostAddress ?: "", Protocol.PORT)
    }

    /** Receiver side for hotspot/Wi-Fi mode: listen and wait for the sender. */
    suspend fun acceptOne(timeoutMs: Long): Socket = withContext(Dispatchers.IO) {
        closeServer()
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(Protocol.PORT))
        ss.soTimeout = timeoutMs.toInt()
        serverSocket = ss
        try {
            ss.accept().also { it.tcpNoDelay = true }
        } finally {
            closeServer()
        }
    }

    /** Connects with retries (the other side may not be listening yet). */
    suspend fun connectTo(host: String, port: Int, attempts: Int = 20): Socket = withContext(Dispatchers.IO) {
        var last: Exception? = null
        repeat(attempts) {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 4000)
                return@withContext s
            } catch (e: Exception) {
                last = e
                delay(1000)
            }
        }
        throw java.io.IOException("Could not connect to $host:$port", last)
    }

    fun closeServer() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        closeServer()
        val m = manager ?: return
        val ch = channel ?: return
        runCatching {
            m.cancelConnect(ch, null)
            m.removeGroup(ch, null)
        }
        _info.value = null
    }

    /** The device's IPv4 address on Wi-Fi/hotspot, shown as the connection code. */
    fun localIpAddress(): String? {
        val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val ipInt = wm?.connectionInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            return "${ipInt and 0xff}.${ipInt shr 8 and 0xff}.${ipInt shr 16 and 0xff}.${ipInt shr 24 and 0xff}"
        }
        // Hotspot host or Wi-Fi without info: scan interfaces for a private IPv4
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }

    private fun reasonText(reason: Int) = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device"
        WifiP2pManager.BUSY -> "Wi-Fi Direct is busy, try again"
        else -> "Connection failed (code $reason)"
    }
}
