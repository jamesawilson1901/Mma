package com.draft.editor.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.draft.editor.Graph
import com.draft.editor.data.DocFormat
import com.draft.editor.data.RecentDoc
import com.draft.editor.data.SafFiles
import com.draft.editor.format.DocxReader
import com.draft.editor.format.DraftFile
import com.draft.editor.format.EditorHtml
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val recents: StateFlow<List<RecentDoc>> = Graph.recentFiles.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a fresh empty document and hands back its id. */
    fun newDocument(onReady: (String) -> Unit) {
        val docId = UUID.randomUUID().toString()
        viewModelScope.launch {
            Graph.documentStore.saveAutosave(docId, html = "", title = "")
            Graph.recentFiles.touch(
                RecentDoc(
                    docId = docId,
                    title = "",
                    preview = "",
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
            onReady(docId)
        }
    }

    /**
     * Imports a picked file. Detects .draft and .docx by content; anything
     * else is opened as plain text. Never refuses a file.
     */
    fun openFromDevice(uri: Uri, onReady: (String) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val bytes = SafFiles.readBytes(context, uri) ?: ByteArray(0)
            val name = SafFiles.displayName(context, uri).orEmpty()

            val draft = DraftFile.read(bytes)
            val (html: String, format: DocFormat) = when {
                draft != null -> draft.html to DocFormat.DRAFT
                DraftFile.looksLikeDocx(bytes) ->
                    EditorHtml.write(DocxReader.read(bytes)) to DocFormat.DOCX
                else -> plainTextToHtml(bytes.toString(Charsets.UTF_8)) to DocFormat.DRAFT
            }

            SafFiles.takePersistablePermission(context, uri)

            // Re-opening the same file goes back to its existing document.
            val existing = recents.value.firstOrNull { it.safUri == uri.toString() }
            val docId = existing?.docId ?: UUID.randomUUID().toString()

            val title = draft?.meta?.title?.takeIf { it.isNotBlank() }
                ?: name.substringBeforeLast('.').takeIf { it.isNotBlank() }
                ?: ""
            Graph.documentStore.saveAutosave(docId, html, title)
            Graph.recentFiles.touch(
                RecentDoc(
                    docId = docId,
                    title = title,
                    preview = previewOf(html),
                    modifiedAt = System.currentTimeMillis(),
                    safUri = uri.toString(),
                    format = format.name,
                ),
            )
            onReady(docId)
        }
    }

    private fun plainTextToHtml(text: String): String {
        if (text.isBlank()) return ""
        val doc = com.draft.editor.format.DraftDocument(
            text.replace("\r\n", "\n").split('\n').map { line ->
                com.draft.editor.format.DocBlock(
                    runs = if (line.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(com.draft.editor.format.TextRun(line))
                    },
                )
            },
        )
        return EditorHtml.write(doc)
    }

    private fun previewOf(html: String): String =
        EditorHtml.parse(html).blocks.firstOrNull { it.plainText.isNotBlank() }
            ?.plainText?.take(120).orEmpty()
}
