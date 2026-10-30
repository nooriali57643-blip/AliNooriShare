package com.alinoori.share

import android.app.Application

/** Holds the long-lived managers so they survive Activity recreation (rotation, language change). */
class ShareApp : Application() {
    lateinit var settings: SettingsManager
    lateinit var history: TransferHistoryManager
    lateinit var scanner: FileScanner
    lateinit var connection: ConnectionManager
    lateinit var discovery: DeviceDiscoveryManager
    lateinit var transfer: FileTransferManager

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager(this)
        history = TransferHistoryManager(this)
        scanner = FileScanner(this)
        connection = ConnectionManager(this)
        discovery = DeviceDiscoveryManager(this, connection)
        transfer = FileTransferManager(this, settings, history)
        settings.applyLanguage()
        Notifier.ensureChannel(this)
    }
}
