package com.draft.editor.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Draft's native container: a zip holding the editor's HTML plus meta.json.
 * Invisible plumbing — users only ever see "documents".
 */
object DraftFile {

    const val EXTENSION = "draft"
    const val MIME_TYPE = "application/octet-stream"

    private const val DOCUMENT_ENTRY = "document.html"
    private const val META_ENTRY = "meta.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class Meta(
        val title: String = "",
        val createdAt: Long = 0L,
        val modifiedAt: Long = 0L,
        val version: Int = 1,
    )

    data class Contents(val html: String, val meta: Meta)

    fun write(html: String, meta: Meta): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DOCUMENT_ENTRY))
            zip.write(html.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(META_ENTRY))
            zip.write(json.encodeToString(meta).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /**
     * Reads a .draft file. Tolerant of a missing meta.json; returns null only
     * when the bytes are not a zip or hold no document at all.
     */
    fun read(bytes: ByteArray): Contents? = try {
        var html: String? = null
        var meta = Meta()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    DOCUMENT_ENTRY -> html = zip.readBytes().toString(Charsets.UTF_8)
                    META_ENTRY -> meta = runCatching {
                        json.decodeFromString<Meta>(zip.readBytes().toString(Charsets.UTF_8))
                    }.getOrDefault(Meta())
                }
                entry = zip.nextEntry
            }
        }
        html?.let { Contents(it, meta) }
    } catch (_: Exception) {
        null
    }

    /** True when the bytes look like a zip that contains a docx main part. */
    fun looksLikeDocx(bytes: ByteArray): Boolean = try {
        var found = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && !found) {
                if (entry.name.removePrefix("/") == "word/document.xml") found = true
                entry = zip.nextEntry
            }
        }
        found
    } catch (_: Exception) {
        false
    }
}
