@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.draft.editor.ui.editor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.draft.editor.Graph
import com.draft.editor.data.DocFormat
import com.draft.editor.data.DocumentStore
import com.draft.editor.data.RecentDoc
import com.draft.editor.data.SafFiles
import com.draft.editor.export.PdfExporter
import com.draft.editor.format.DocxWriter
import com.draft.editor.format.DraftFile
import com.draft.editor.format.EditorHtml
import com.draft.editor.format.FindEngine
import com.draft.editor.format.MarkdownShortcuts
import com.draft.editor.format.OutlineItem
import com.draft.editor.format.WordCount
import com.draft.editor.format.buildOutline
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditorViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    docIdArg: String,
) : AndroidViewModel(application) {

    val docId: String = savedStateHandle["docId"] ?: docIdArg.also {
        savedStateHandle["docId"] = it
    }

    /** The single source of truth for the document being edited. */
    val richTextState = RichTextState()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _stats = MutableStateFlow(WordCount.Stats(0, 0, 0, 0, 0))
    val stats: StateFlow<WordCount.Stats> = _stats.asStateFlow()

    private val _find = MutableStateFlow(FindState())
    val find: StateFlow<FindState> = _find.asStateFlow()

    private val _snapshots = MutableStateFlow<List<DocumentStore.Snapshot>>(emptyList())
    val snapshots: StateFlow<List<DocumentStore.Snapshot>> = _snapshots.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    data class FindState(
        val active: Boolean = false,
        val query: String = "",
        val replacement: String = "",
        val showReplace: Boolean = false,
        val matches: List<FindEngine.Match> = emptyList(),
        val currentIndex: Int = -1,
    )

    private var safUri: Uri? = null
    private var format: DocFormat = DocFormat.DRAFT

    /** True while this ViewModel itself mutates the text. */
    private var programmaticEdit = false
    private var lastText: String = ""
    private var dirtySinceSafSave = false
    private var dirtySinceSnapshot = false
    private var lastSnapshotAt = 0L

    init {
        viewModelScope.launch {
            val store = Graph.documentStore
            val html = store.loadHtml(docId).orEmpty()
            val meta = store.loadMeta(docId)
            val recent = Graph.recentFiles.get(docId)

            _title.value = recent?.title ?: meta?.title.orEmpty()
            safUri = recent?.safUri?.let(Uri::parse)
            format = recent?.docFormat ?: DocFormat.DRAFT

            if (html.isNotBlank()) {
                programmaticEdit = true
                richTextState.setHtml(html)
                programmaticEdit = false
            }
            richTextState.history.clear()
            lastText = richTextState.annotatedString.text
            _stats.value = WordCount.of(lastText)
            lastSnapshotAt = System.currentTimeMillis()
            _loaded.value = true

            watchForShortcuts()
            watchForAutosave()
            snapshotLoop()
        }
    }

    // ------------------------------------------------------------ observers

    private fun watchForShortcuts() {
        viewModelScope.launch {
            snapshotFlow { richTextState.annotatedString.text }
                .distinctUntilChanged()
                .collect { newText ->
                    val previous = lastText
                    lastText = newText
                    if (previous != newText) {
                        dirtySinceSafSave = true
                        dirtySinceSnapshot = true
                    }
                    if (!programmaticEdit) {
                        maybeApplyMarkdownShortcut(previous, newText)
                        refreshFindMatches()
                    }
                }
        }
        viewModelScope.launch {
            snapshotFlow { richTextState.annotatedString.text }
                .debounce(250)
                .collect { _stats.value = WordCount.of(it) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun watchForAutosave() {
        viewModelScope.launch {
            snapshotFlow { richTextState.annotatedString }
                .debounce(AUTOSAVE_IDLE_MS)
                .collect { autosaveNow() }
        }
    }

    private fun snapshotLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(SNAPSHOT_CHECK_MS)
                if (dirtySinceSnapshot &&
                    System.currentTimeMillis() - lastSnapshotAt >= SNAPSHOT_INTERVAL_MS
                ) {
                    takeSnapshot()
                }
            }
        }
    }

    private suspend fun autosaveNow() {
        val html = richTextState.toHtml()
        Graph.documentStore.saveAutosave(docId, html, _title.value)
        Graph.recentFiles.touch(
            RecentDoc(
                docId = docId,
                title = _title.value,
                preview = previewText(),
                modifiedAt = System.currentTimeMillis(),
                safUri = safUri?.toString(),
                format = format.name,
            ),
        )
    }

    private fun previewText(): String =
        richTextState.annotatedString.text.lineSequence()
            .firstOrNull { it.isNotBlank() }?.trim()?.take(120).orEmpty()

    private suspend fun takeSnapshot() {
        dirtySinceSnapshot = false
        lastSnapshotAt = System.currentTimeMillis()
        Graph.documentStore.writeSnapshot(docId, richTextState.toHtml(), _stats.value.words)
    }

    /** Called when the editor screen goes away: autosave + write-through to the file. */
    fun onEditorClosed() {
        viewModelScope.launch {
            autosaveNow()
            if (dirtySinceSafSave && safUri != null) {
                saveToBoundFile(notify = false)
            }
        }
    }

    // ------------------------------------------------------------ markdown

    private fun maybeApplyMarkdownShortcut(previous: String, current: String) {
        if (current.length != previous.length + 1) return
        val selection = richTextState.selection
        if (!selection.collapsed) return
        val cursor = selection.min
        if (cursor < 1 || cursor > current.length) return
        val typed = current[cursor - 1]
        if (typed != ' ' && typed != '*') return

        val lineStart = current.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        if (lineStart >= cursor) return
        val line = current.substring(lineStart, cursor)

        if (typed == ' ') {
            val block = MarkdownShortcuts.detectBlock(line) ?: return
            if (richTextState.isList) return
            if (richTextState.currentHeadingStyle != HeadingStyle.Normal &&
                block != MarkdownShortcuts.Block.BULLET &&
                block != MarkdownShortcuts.Block.NUMBERED
            ) {
                return
            }
            withProgrammaticEdit {
                richTextState.removeTextRange(TextRange(lineStart, cursor))
                when (block) {
                    MarkdownShortcuts.Block.HEADING1 -> richTextState.setHeadingStyle(HeadingStyle.H1)
                    MarkdownShortcuts.Block.HEADING2 -> richTextState.setHeadingStyle(HeadingStyle.H2)
                    MarkdownShortcuts.Block.HEADING3 -> richTextState.setHeadingStyle(HeadingStyle.H3)
                    MarkdownShortcuts.Block.BULLET -> richTextState.addUnorderedList()
                    MarkdownShortcuts.Block.NUMBERED -> richTextState.addOrderedList()
                }
            }
        } else {
            val emphasis = MarkdownShortcuts.detectEmphasis(line) ?: return
            val openStart = lineStart + emphasis.openStart
            val contentStart = lineStart + emphasis.contentStart
            val contentEnd = lineStart + emphasis.contentEnd
            val closeEnd = lineStart + emphasis.closeEnd
            val markerLen = contentStart - openStart
            withProgrammaticEdit {
                richTextState.removeTextRange(TextRange(contentEnd, closeEnd))
                richTextState.removeTextRange(TextRange(openStart, contentStart))
                val newEnd = contentEnd - markerLen
                val style = if (emphasis.bold) {
                    SpanStyle(fontWeight = FontWeight.Bold)
                } else {
                    SpanStyle(fontStyle = FontStyle.Italic)
                }
                richTextState.addSpanStyle(style, TextRange(openStart, newEnd))
                richTextState.selection = TextRange(newEnd)
            }
        }
    }

    private inline fun withProgrammaticEdit(block: () -> Unit) {
        programmaticEdit = true
        try {
            block()
        } finally {
            lastText = richTextState.annotatedString.text
            programmaticEdit = false
        }
    }

    // ------------------------------------------------------------ selection assist

    fun selectWordAtCursor() {
        val text = richTextState.annotatedString.text
        if (text.isEmpty()) return
        val cursor = richTextState.selection.min.coerceIn(0, text.length)
        var start = cursor.coerceAtMost(text.length - 1)
        if (start > 0 && (start >= text.length || !text[start].isWordChar())) start--
        if (!text[start].isWordChar()) return
        var end = start + 1
        while (start > 0 && text[start - 1].isWordChar()) start--
        while (end < text.length && text[end].isWordChar()) end++
        richTextState.selection = TextRange(start, end)
    }

    fun selectParagraphAtCursor() {
        val text = richTextState.annotatedString.text
        val cursor = richTextState.selection.min.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        if (end > start) richTextState.selection = TextRange(start, end)
    }

    fun nudgeCursor(delta: Int) {
        val text = richTextState.annotatedString.text
        val base = if (delta < 0) richTextState.selection.min else richTextState.selection.max
        val target = (base + delta).coerceIn(0, text.length)
        richTextState.selection = TextRange(target)
    }

    private fun Char.isWordChar() = isLetterOrDigit() || this == '\'' || this == '’'

    // ------------------------------------------------------------ outline

    fun currentOutline(): List<OutlineItem> =
        buildOutline(EditorHtml.parse(richTextState.toHtml()))

    fun jumpTo(charOffset: Int) {
        val length = richTextState.annotatedString.text.length
        richTextState.selection = TextRange(charOffset.coerceIn(0, length))
    }

    // ------------------------------------------------------------ find & replace

    fun setFindActive(active: Boolean) {
        _find.value = if (active) {
            _find.value.copy(active = true)
        } else {
            FindState()
        }
        if (active) refreshFindMatches()
    }

    fun setFindQuery(query: String) {
        _find.value = _find.value.copy(query = query)
        refreshFindMatches(resetIndex = true)
    }

    fun setReplacement(replacement: String) {
        _find.value = _find.value.copy(replacement = replacement)
    }

    fun setShowReplace(show: Boolean) {
        _find.value = _find.value.copy(showReplace = show)
    }

    private fun refreshFindMatches(resetIndex: Boolean = false) {
        val state = _find.value
        if (!state.active) return
        val matches = FindEngine.findAll(richTextState.annotatedString.text, state.query)
        val index = when {
            matches.isEmpty() -> -1
            resetIndex || state.currentIndex !in matches.indices -> 0
            else -> state.currentIndex
        }
        _find.value = state.copy(matches = matches, currentIndex = index)
        if (resetIndex && index >= 0) highlightMatch(index)
    }

    fun findNext() = moveMatch(1)

    fun findPrevious() = moveMatch(-1)

    private fun moveMatch(direction: Int) {
        val state = _find.value
        if (state.matches.isEmpty()) return
        val index = ((state.currentIndex + direction) % state.matches.size + state.matches.size) %
            state.matches.size
        _find.value = state.copy(currentIndex = index)
        highlightMatch(index)
    }

    private fun highlightMatch(index: Int) {
        val match = _find.value.matches.getOrNull(index) ?: return
        richTextState.selection = TextRange(match.start, match.end)
    }

    fun replaceCurrent() {
        val state = _find.value
        val match = state.matches.getOrNull(state.currentIndex) ?: return
        withProgrammaticEdit {
            richTextState.replaceTextRange(TextRange(match.start, match.end), state.replacement)
        }
        refreshFindMatches()
        if (_find.value.matches.isNotEmpty()) {
            val next = state.currentIndex.coerceIn(0, _find.value.matches.lastIndex)
            _find.value = _find.value.copy(currentIndex = next)
            highlightMatch(next)
        }
    }

    fun replaceAll() {
        val state = _find.value
        if (state.matches.isEmpty()) return
        withProgrammaticEdit {
            for (match in state.matches.sortedByDescending { it.start }) {
                richTextState.replaceTextRange(TextRange(match.start, match.end), state.replacement)
            }
        }
        val count = state.matches.size
        refreshFindMatches(resetIndex = true)
        viewModelScope.launch { _messages.emit("Replaced $count occurrence${if (count == 1) "" else "s"}") }
    }

    // ------------------------------------------------------------ title

    fun rename(newTitle: String) {
        _title.value = newTitle.trim()
        viewModelScope.launch { autosaveNow() }
    }

    // ------------------------------------------------------------ saving & export

    val hasBoundFile: Boolean get() = safUri != null
    val boundFormat: DocFormat get() = format

    fun suggestedFileName(extension: String): String {
        val base = _title.value.ifBlank {
            previewText().ifBlank { "Untitled document" }
        }
        return "${base.take(60)}.$extension"
    }

    /** Save to the original location; asks the UI for a location on first save. */
    fun save(onNeedsLocation: () -> Unit) {
        if (safUri == null) {
            onNeedsLocation()
        } else {
            viewModelScope.launch { saveToBoundFile(notify = true) }
        }
    }

    private suspend fun saveToBoundFile(notify: Boolean) {
        val uri = safUri ?: return
        val bytes = when (format) {
            DocFormat.DRAFT -> buildDraftBytes()
            DocFormat.DOCX -> buildDocxBytes()
        }
        val ok = SafFiles.writeBytes(getApplication(), uri, bytes)
        if (ok) {
            dirtySinceSafSave = false
            if (notify) _messages.emit("Saved")
        } else if (notify) {
            _messages.emit("Couldn't save to the original file")
        }
    }

    /** Binds a newly created .draft file and saves into it. */
    fun bindAndSaveDraft(uri: Uri) {
        bindAndSave(uri, DocFormat.DRAFT)
    }

    /** "Save as .docx": binds the new file and writes the docx. */
    fun saveAsDocx(uri: Uri) {
        bindAndSave(uri, DocFormat.DOCX)
    }

    private fun bindAndSave(uri: Uri, newFormat: DocFormat) {
        viewModelScope.launch {
            SafFiles.takePersistablePermission(getApplication(), uri)
            safUri = uri
            format = newFormat
            if (_title.value.isBlank()) {
                SafFiles.displayName(getApplication(), uri)
                    ?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() }
                    ?.let { _title.value = it }
            }
            saveToBoundFile(notify = true)
            autosaveNow()
        }
    }

    private fun buildDraftBytes(): ByteArray {
        val now = System.currentTimeMillis()
        return DraftFile.write(
            html = richTextState.toHtml(),
            meta = DraftFile.Meta(title = _title.value, createdAt = now, modifiedAt = now),
        )
    }

    private fun buildDocxBytes(): ByteArray =
        DocxWriter.write(EditorHtml.parse(richTextState.toHtml()))

    fun exportPdf(uri: Uri) {
        viewModelScope.launch {
            val document = EditorHtml.parse(richTextState.toHtml())
            val bytes = PdfExporter.render(document)
            report(SafFiles.writeBytes(getApplication(), uri, bytes), "PDF")
        }
    }

    fun exportMarkdown(uri: Uri) {
        viewModelScope.launch {
            val markdown = richTextState.toMarkdown()
            report(SafFiles.writeBytes(getApplication(), uri, markdown.toByteArray()), "Markdown")
        }
    }

    fun exportPlainText(uri: Uri) {
        viewModelScope.launch {
            val text = richTextState.annotatedString.text
            report(SafFiles.writeBytes(getApplication(), uri, text.toByteArray()), "Plain text")
        }
    }

    private suspend fun report(ok: Boolean, what: String) {
        _messages.emit(if (ok) "$what exported" else "$what export failed")
    }

    // ------------------------------------------------------------ version history

    fun refreshSnapshots() {
        viewModelScope.launch {
            _snapshots.value = Graph.documentStore.listSnapshots(docId)
        }
    }

    suspend fun snapshotPreview(timestamp: Long): String {
        val html = Graph.documentStore.readSnapshot(docId, timestamp) ?: return ""
        return EditorHtml.parse(html).plainText.take(400)
    }

    fun restoreSnapshot(timestamp: Long) {
        viewModelScope.launch {
            val html = Graph.documentStore.readSnapshot(docId, timestamp) ?: return@launch
            // Keep what's on screen reachable: snapshot it before replacing.
            Graph.documentStore.writeSnapshot(docId, richTextState.toHtml(), _stats.value.words)
            withProgrammaticEdit {
                richTextState.setHtml(html)
            }
            _stats.value = WordCount.of(richTextState.annotatedString.text)
            autosaveNow()
            refreshSnapshots()
            _messages.emit("Version restored")
        }
    }

    private companion object {
        const val AUTOSAVE_IDLE_MS = 2_000L
        const val SNAPSHOT_INTERVAL_MS = 5 * 60_000L
        const val SNAPSHOT_CHECK_MS = 30_000L
    }
}
