package com.alinoori.share

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores a local-only history of transfers as JSON in app-private storage. */
class TransferHistoryManager(context: Context) {
    private val file = File(context.applicationContext.filesDir, "history.json")
    private val lock = Any()

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    private fun read(): List<HistoryEntry> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val names = o.getJSONArray("files")
                HistoryEntry(
                    id = o.getLong("id"),
                    time = o.getLong("time"),
                    direction = Direction.valueOf(o.getString("dir")),
                    peerName = o.getString("peer"),
                    fileNames = (0 until names.length()).map { names.getString(it) },
                    totalBytes = o.getLong("bytes"),
                    success = o.getBoolean("ok"),
                    note = o.optString("note", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<HistoryEntry>) = synchronized(lock) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("time", e.time); put("dir", e.direction.name)
                put("peer", e.peerName); put("files", JSONArray(e.fileNames))
                put("bytes", e.totalBytes); put("ok", e.success); put("note", e.note)
            })
        }
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
        _entries.value = list
    }

    fun add(entry: HistoryEntry) {
        write((listOf(entry) + _entries.value).take(500))
    }

    fun clear() = write(emptyList())
}
