package com.draft.editor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small helpers around the Storage Access Framework. */
object SafFiles {

    suspend fun readBytes(context: Context, uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }

    /** Writes the full content, truncating whatever was there. */
    suspend fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                } != null
            }.getOrDefault(false)
        }

    /** Keeps access to a picked file across restarts so recents can reopen it. */
    fun takePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .recoverCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
