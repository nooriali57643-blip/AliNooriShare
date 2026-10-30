package com.alinoori.share

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lists shareable files. Media categories use MediaStore (needs media permission);
 * Files / Documents / Downloads / Folders use the Storage Access Framework pickers,
 * so no broad storage permission is ever requested.
 */
class FileScanner(private val context: Context) {

    private val resolver get() = context.contentResolver

    suspend fun scanMedia(category: FileCategory): List<ShareItem> = withContext(Dispatchers.IO) {
        val collection = when (category) {
            FileCategory.PHOTOS -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileCategory.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return@withContext emptyList()
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val out = ArrayList<ShareItem>()
        runCatching {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(collection, id)
                    out += ShareItem(
                        id = uri.toString(),
                        uri = uri,
                        name = c.getString(iName) ?: "file_$id",
                        size = c.getLong(iSize),
                        mime = c.getString(iMime) ?: "application/octet-stream",
                        dateModified = c.getLong(iDate) * 1000,
                        category = category
                    )
                }
            }
        }
        out
    }

    /** Installed apps whose base APK is readable. Split APKs: only base.apk is shared. */
    suspend fun scanApps(): List<ShareItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .mapNotNull { info ->
                val f = File(info.sourceDir)
                if (!f.canRead()) return@mapNotNull null
                val label = info.loadLabel(pm).toString().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                ShareItem(
                    id = "apk:" + info.packageName,
                    uri = Uri.fromFile(f),
                    name = "$label.apk",
                    size = f.length(),
                    mime = "application/vnd.android.package-archive",
                    dateModified = f.lastModified(),
                    category = FileCategory.APPS
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Files already received, from the app folder or the user's chosen SAF folder. */
    suspend fun scanReceived(saveTreeUri: String?): List<ShareItem> = withContext(Dispatchers.IO) {
        if (saveTreeUri != null) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(saveTreeUri))
            return@withContext tree?.listFiles()?.filter { it.isFile }?.map { toItem(it, FileCategory.RECEIVED) }.orEmpty()
                .sortedByDescending { it.dateModified }
        }
        receivedDir(context).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.map {
                ShareItem(
                    id = it.absolutePath, uri = Uri.fromFile(it), name = it.name, size = it.length(),
                    mime = mimeOf(it.name), dateModified = it.lastModified(), category = FileCategory.RECEIVED
                )
            }.orEmpty().sortedByDescending { it.dateModified }
    }

    /** Converts URIs returned by the SAF multi-file picker into [ShareItem]s. */
    suspend fun fromPicked(uris: List<Uri>, category: FileCategory): List<ShareItem> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            runCatching {
                var name = "file"
                var size = -1L
                resolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n) ?: name
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
                if (size < 0) size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                ShareItem(
                    id = uri.toString(), uri = uri, name = name, size = size,
                    mime = resolver.getType(uri) ?: mimeOf(name),
                    dateModified = System.currentTimeMillis(), category = category
                )
            }.getOrNull()
        }
    }

    /** Recursively lists every file in a folder the user picked (capped for safety). */
    suspend fun fromTree(treeUri: Uri, limit: Int = 5000): List<ShareItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = ArrayList<ShareItem>()
        fun walk(dir: DocumentFile, prefix: String) {
            for (f in dir.listFiles()) {
                if (out.size >= limit) return
                if (f.isDirectory) walk(f, prefix + (f.name ?: "dir") + "/")
                else out += toItem(f, FileCategory.FOLDERS).copy(name = prefix + (f.name ?: "file"))
            }
        }
        walk(root, (root.name ?: "folder") + "/")
        out
    }

    private fun toItem(f: DocumentFile, cat: FileCategory) = ShareItem(
        id = f.uri.toString(), uri = f.uri, name = f.name ?: "file", size = f.length(),
        mime = f.type ?: mimeOf(f.name ?: ""), dateModified = f.lastModified(), category = cat
    )

    companion object {
        fun receivedDir(context: Context): File {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, "Received").apply { mkdirs() }
        }

        fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
