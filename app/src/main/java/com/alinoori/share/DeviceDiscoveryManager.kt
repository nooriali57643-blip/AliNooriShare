package com.alinoori.share

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Finds nearby devices over Wi-Fi Direct. Caller must hold the needed runtime permission first. */
class DeviceDiscoveryManager(context: Context, private val connection: ConnectionManager) {
    private val app = context.applicationContext

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers

    private val _p2pEnabled = MutableStateFlow(true)
    val p2pEnabled: StateFlow<Boolean> = _p2pEnabled

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val st = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _p2pEnabled.value = st == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val m = connection.manager ?: return
                    val ch = connection.channel ?: return
                    try {
                        m.requestPeers(ch) { list ->
                            _peers.value = list.deviceList
                                .filter { it.status != WifiP2pDevice.UNAVAILABLE }
                                .map { PeerDevice(it.deviceName.ifBlank { it.deviceAddress }, it.deviceAddress, true) }
                        }
                    } catch (_: SecurityException) { }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!connection.wifiDirectSupported) {
            _error.value = "Wi-Fi Direct is not supported on this device"
            return
        }
        if (!registered) {
            val f = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        _error.value = null
        try {
            connection.manager?.discoverPeers(connection.channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {
                    _error.value = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device"
                        WifiP2pManager.BUSY -> "Wi-Fi Direct is busy, try again"
                        else -> "Discovery failed (code $reason). Make sure Wi-Fi is on."
                    }
                }
            })
        } catch (_: SecurityException) {
            _error.value = "Permission denied"
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (registered) {
            runCatching { app.unregisterReceiver(receiver) }
            registered = false
        }
        runCatching { connection.manager?.stopPeerDiscovery(connection.channel, null) }
        _peers.value = emptyList()
    }
}
