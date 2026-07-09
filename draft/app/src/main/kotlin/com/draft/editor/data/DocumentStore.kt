package com.draft.editor.data

import android.content.Context
import com.draft.editor.format.DraftFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App-private storage backing autosave and version snapshots:
 *
 *   filesDir/documents/{docId}/current.html
 *   filesDir/documents/{docId}/meta.json
 *   filesDir/documents/{docId}/snapshots/{timestampMillis}.html
 *
 * Work never leaves the device and survives process death; the visible
 * .draft/.docx file (via SAF) is written separately on demand and on exit.
 */
class DocumentStore(private val context: Context) {

    data class Snapshot(val timestamp: Long, val wordCount: Int)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun docDir(docId: String): File =
        File(File(context.filesDir, "documents"), docId)

    private fun snapshotsDir(docId: String): File = File(docDir(docId), "snapshots")

    suspend fun loadHtml(docId: String): String? = withContext(Dispatchers.IO) {
        val file = File(docDir(docId), "current.html")
        if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    suspend fun loadMeta(docId: String): DraftFile.Meta? = withContext(Dispatchers.IO) {
        val file = File(docDir(docId), "meta.json")
        if (!file.isFile) return@withContext null
        runCatching { json.decodeFromString<DraftFile.Meta>(file.readText()) }.getOrNull()
    }

    suspend fun saveAutosave(docId: String, html: String, title: String) =
        withContext(Dispatchers.IO) {
            val dir = docDir(docId).apply { mkdirs() }
            val now = System.currentTimeMillis()
            val created = loadMetaBlocking(docId)?.createdAt?.takeIf { it > 0 } ?: now
            atomicWrite(File(dir, "current.html"), html)
            atomicWrite(
                File(dir, "meta.json"),
                json.encodeToString(
                    DraftFile.Meta(title = title, createdAt = created, modifiedAt = now),
                ),
            )
        }

    private fun loadMetaBlocking(docId: String): DraftFile.Meta? {
        val file = File(docDir(docId), "meta.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<DraftFile.Meta>(file.readText()) }.getOrNull()
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    // -------------------------------------------------------------- snapshots

    suspend fun writeSnapshot(docId: String, html: String, wordCount: Int) =
        withContext(Dispatchers.IO) {
            val dir = snapshotsDir(docId).apply { mkdirs() }
            File(dir, "${System.currentTimeMillis()}_$wordCount.html").writeText(html)
            pruneSnapshots(dir)
        }

    private fun pruneSnapshots(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".html") } ?: return
        files.sortedByDescending { snapshotTimestamp(it) }
            .drop(MAX_SNAPSHOTS)
            .forEach { it.delete() }
    }

    private fun snapshotTimestamp(file: File): Long =
        file.name.substringBefore('_').substringBefore('.').toLongOrNull() ?: 0L

    private fun snapshotWordCount(file: File): Int =
        file.name.substringAfter('_', "").substringBefore('.').toIntOrNull() ?: 0

    suspend fun listSnapshots(docId: String): List<Snapshot> = withContext(Dispatchers.IO) {
        val files = snapshotsDir(docId).listFiles { f -> f.isFile && f.name.endsWith(".html") }
            ?: return@withContext emptyList()
        files.map { Snapshot(snapshotTimestamp(it), snapshotWordCount(it)) }
            .sortedByDescending { it.timestamp }
    }

    suspend fun readSnapshot(docId: String, timestamp: Long): String? =
        withContext(Dispatchers.IO) {
            val file = snapshotsDir(docId)
                .listFiles { f -> f.name.startsWith("${timestamp}_") || f.name == "$timestamp.html" }
                ?.firstOrNull()
            file?.let { runCatching { it.readText() }.getOrNull() }
        }

    suspend fun deleteDocument(docId: String): Unit = withContext(Dispatchers.IO) {
        docDir(docId).deleteRecursively()
        Unit
    }

    private companion object {
        const val MAX_SNAPSHOTS = 20
    }
}
