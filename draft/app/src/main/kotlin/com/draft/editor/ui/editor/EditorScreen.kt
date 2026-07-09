package com.draft.editor.ui.editor

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.draft.editor.data.AppPrefs
import com.draft.editor.data.ThemeMode
import com.draft.editor.Graph
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.launch

private const val DOCX_MIME =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun EditorScreen(
    docId: String,
    prefs: AppPrefs,
    onClose: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: EditorViewModel = viewModel(
        key = "editor-$docId",
        factory = viewModelFactory {
            initializer {
                EditorViewModel(
                    application = application,
                    savedStateHandle = createSavedStateHandle(),
                    docIdArg = docId,
                )
            }
        },
    )

    val state = vm.richTextState
    val title by vm.title.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val find by vm.find.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // Link the editor's link colour to the theme.
    val linkColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(state, linkColor) {
        state.config.linkColor = linkColor
        state.config.linkTextDecoration = TextDecoration.Underline
    }

    fun close() {
        vm.onEditorClosed()
        onClose()
    }
    BackHandler(onBack = ::close)

    // ------------------------------------------------------------- launchers

    val createDraftLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) vm.bindAndSaveDraft(uri) }

    val createDocxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DOCX_MIME),
    ) { uri -> if (uri != null) vm.saveAsDocx(uri) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> if (uri != null) vm.exportPdf(uri) }

    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> if (uri != null) vm.exportMarkdown(uri) }

    val plainTextLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) vm.exportPlainText(uri) }

    // ------------------------------------------------------------- dialogs

    var showOutline by rememberSaveable { mutableStateOf(false) }
    var showVersions by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showLink by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showWordCount by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showTextSize by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    // ------------------------------------------------------------- layout

    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        EditorTopBar(
            title = title,
            stats = stats,
            onBack = ::close,
            onRename = { showRename = true },
            onOutline = { showOutline = true },
            onFind = { vm.setFindActive(!find.active) },
            menu = { closeMenu ->
                DropdownMenuItem(
                    text = { Text("Save") },
                    onClick = {
                        closeMenu()
                        vm.save(
                            onNeedsLocation = {
                                createDraftLauncher.launch(vm.suggestedFileName("draft"))
                            },
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text("Save as .docx") },
                    onClick = {
                        closeMenu()
                        createDocxLauncher.launch(vm.suggestedFileName("docx"))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export") },
                    onClick = { closeMenu(); showExport = true },
                )
                DropdownMenuItem(
                    text = { Text("Version history") },
                    onClick = { closeMenu(); vm.refreshSnapshots(); showVersions = true },
                )
                DropdownMenuItem(
                    text = { Text("Word count details") },
                    onClick = { closeMenu(); showWordCount = true },
                )
                DropdownMenuItem(
                    text = { Text("Theme") },
                    onClick = { closeMenu(); showTheme = true },
                )
                DropdownMenuItem(
                    text = { Text("Text size") },
                    onClick = { closeMenu(); showTextSize = true },
                )
                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = { closeMenu(); showAbout = true },
                )
            },
        )

        if (find.active) {
            FindReplaceBar(
                find = find,
                onQueryChange = vm::setFindQuery,
                onReplacementChange = vm::setReplacement,
                onToggleReplace = { vm.setShowReplace(!find.showReplace) },
                onNext = vm::findNext,
                onPrevious = vm::findPrevious,
                onReplace = vm::replaceCurrent,
                onReplaceAll = vm::replaceAll,
                onClose = { vm.setFindActive(false) },
            )
        }

        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { focusRequester.requestFocus() },
            ) {
                BasicRichTextEditor(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = prefs.bodyTextSp.sp,
                        lineHeight = (prefs.bodyTextSp * 1.55f).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.height(160.dp))
            }
            if (loaded && state.annotatedString.text.isEmpty()) {
                Text(
                    "Start writing…",
                    style = TextStyle(fontSize = prefs.bodyTextSp.sp),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        EditorToolbar(
            state = state,
            onInsertLink = { showLink = true },
            onSelectWord = vm::selectWordAtCursor,
            onSelectParagraph = vm::selectParagraphAtCursor,
            onNudge = vm::nudgeCursor,
        )
    }

    // ------------------------------------------------------------- overlays

    if (showOutline) {
        OutlineSheet(
            outline = remember { vm.currentOutline() },
            onJump = { item ->
                vm.jumpTo(item.charOffset)
                showOutline = false
            },
            onDismiss = { showOutline = false },
        )
    }

    if (showVersions) {
        val snapshots by vm.snapshots.collectAsStateWithLifecycle()
        VersionHistorySheet(
            snapshots = snapshots,
            previewLoader = { vm.snapshotPreview(it) },
            onRestore = { timestamp ->
                vm.restoreSnapshot(timestamp)
                showVersions = false
            },
            onDismiss = { showVersions = false },
        )
    }

    if (showRename) {
        RenameDialog(
            current = title,
            onRename = vm::rename,
            onDismiss = { showRename = false },
        )
    }

    if (showLink) {
        val selection = state.selection
        val selectedText = if (!selection.collapsed) {
            state.annotatedString.text.substring(selection.min, selection.max)
        } else {
            ""
        }
        val isLink = state.isLink
        LinkDialog(
            initialText = if (isLink) state.selectedLinkText.orEmpty() else selectedText,
            initialUrl = if (isLink) state.selectedLinkUrl.orEmpty() else "",
            textEditable = selection.collapsed && !isLink,
            canRemove = isLink,
            onConfirm = { text, url ->
                when {
                    isLink -> state.updateLink(url)
                    !selection.collapsed -> state.addLinkToSelection(url)
                    else -> state.addLink(text.ifBlank { url }, url)
                }
            },
            onRemove = { state.removeLink() },
            onDismiss = { showLink = false },
        )
    }

    if (showExport) {
        ExportDialog(
            onPdf = { pdfLauncher.launch(vm.suggestedFileName("pdf")) },
            onMarkdown = { markdownLauncher.launch(vm.suggestedFileName("md")) },
            onPlainText = { plainTextLauncher.launch(vm.suggestedFileName("txt")) },
            onDismiss = { showExport = false },
        )
    }

    if (showWordCount) {
        WordCountDialog(stats = stats, onDismiss = { showWordCount = false })
    }

    if (showTheme) {
        ThemeDialog(
            current = prefs.themeMode,
            onSelect = { mode: ThemeMode ->
                scope.launch { Graph.preferences.setThemeMode(mode) }
            },
            onDismiss = { showTheme = false },
        )
    }

    if (showTextSize) {
        TextSizeDialog(
            current = prefs.bodyTextSp,
            onSelect = { sp -> scope.launch { Graph.preferences.setBodyTextSp(sp) } },
            onDismiss = { showTextSize = false },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

// ------------------------------------------------------------------ top bar

@Composable
private fun EditorTopBar(
    title: String,
    stats: com.draft.editor.format.WordCount.Stats,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onOutline: () -> Unit,
    onFind: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    Surface(tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to documents",
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRename)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = title.ifBlank { "Untitled document" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${stats.words} words · ${stats.characters} characters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onOutline) {
                Icon(
                    Icons.AutoMirrored.Filled.Toc,
                    contentDescription = "Document outline",
                )
            }
            IconButton(onClick = onFind) {
                Icon(Icons.Filled.Search, contentDescription = "Find and replace")
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu { menuOpen = false }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ find bar

@Composable
private fun FindReplaceBar(
    find: EditorViewModel.FindState,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onToggleReplace: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = find.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Find") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (find.query.isEmpty()) {
                        ""
                    } else if (find.matches.isEmpty()) {
                        "0/0"
                    } else {
                        "${find.currentIndex + 1}/${find.matches.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onPrevious, enabled = find.matches.isNotEmpty()) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = onNext, enabled = find.matches.isNotEmpty()) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close find bar")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleReplace) {
                    Text(if (find.showReplace) "Hide replace" else "Replace…")
                }
            }
            if (find.showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = find.replacement,
                        onValueChange = onReplacementChange,
                        singleLine = true,
                        placeholder = { Text("Replace with") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onReplace, enabled = find.matches.isNotEmpty()) {
                        Text("Replace")
                    }
                    TextButton(onClick = onReplaceAll, enabled = find.matches.isNotEmpty()) {
                        Text("All")
                    }
                }
            }
        }
    }
}
