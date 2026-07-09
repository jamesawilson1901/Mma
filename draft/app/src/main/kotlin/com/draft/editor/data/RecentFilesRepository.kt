package com.draft.editor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** How a document maps back to a file on the device, if it does at all. */
enum class DocFormat { DRAFT, DOCX }

@Serializable
data class RecentDoc(
    val docId: String,
    val title: String,
    val preview: String,
    val modifiedAt: Long,
    val safUri: String? = null,
    val format: String = "DRAFT",
) {
    val docFormat: DocFormat
        get() = runCatching { DocFormat.valueOf(format) }.getOrDefault(DocFormat.DRAFT)
}

class RecentFilesRepository(private val context: Context) {

    private val recentsKey = stringPreferencesKey("recent_docs")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val recents: Flow<List<RecentDoc>> = context.draftDataStore.data.map { p ->
        decode(p[recentsKey]).sortedByDescending { it.modifiedAt }
    }

    private fun decode(raw: String?): List<RecentDoc> =
        raw?.let { runCatching { json.decodeFromString<List<RecentDoc>>(it) }.getOrNull() }
            ?: emptyList()

    /** Inserts or refreshes an entry, keeping the list bounded. */
    suspend fun touch(doc: RecentDoc) {
        context.draftDataStore.edit { p ->
            val list = decode(p[recentsKey])
                .filterNot { it.docId == doc.docId }
                .plus(doc)
                .sortedByDescending { it.modifiedAt }
                .take(MAX_RECENTS)
            p[recentsKey] = json.encodeToString(list)
        }
    }

    suspend fun update(docId: String, transform: (RecentDoc) -> RecentDoc) {
        context.draftDataStore.edit { p ->
            val list = decode(p[recentsKey]).map { if (it.docId == docId) transform(it) else it }
            p[recentsKey] = json.encodeToString(list)
        }
    }

    suspend fun get(docId: String): RecentDoc? =
        decode(context.draftDataStore.data.first()[recentsKey])
            .firstOrNull { it.docId == docId }

    private companion object {
        const val MAX_RECENTS = 50
    }
}
