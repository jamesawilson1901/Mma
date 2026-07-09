package com.draft.editor.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.draft.editor.data.AppPrefs
import com.draft.editor.data.DocumentStore
import com.draft.editor.data.ThemeMode
import com.draft.editor.format.OutlineItem
import com.draft.editor.format.WordCount
import java.text.DateFormat
import java.util.Date

// --------------------------------------------------------------- outline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    outline: List<OutlineItem>,
    onJump: (OutlineItem) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Outline",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (outline.isEmpty()) {
            Text(
                "No headings yet. Use H1–H3 styles (or type “# ” at the start of a line) " +
                    "and they will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
                items(outline) { item ->
                    androidx.compose.material3.Surface(
                        onClick = { onJump(item) },
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(start = (24 + (item.level - 1) * 20).dp, end = 24.dp),
                        ) {
                            Text(
                                "H${item.level}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                item.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = when (item.level) {
                                    1 -> MaterialTheme.typography.titleMedium
                                    2 -> MaterialTheme.typography.bodyLarge
                                    else -> MaterialTheme.typography.bodyMedium
                                },
                                fontWeight = if (item.level == 1) FontWeight.SemiBold else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- versions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistorySheet(
    snapshots: List<DocumentStore.Snapshot>,
    previewLoader: suspend (Long) -> String,
    onRestore: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Version history",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (snapshots.isEmpty()) {
            Text(
                "No snapshots yet. Draft takes one automatically every 5 minutes " +
                    "while you write, and keeps the last 20.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
                items(snapshots, key = { it.timestamp }) { snapshot ->
                    var expanded by remember { mutableStateOf(false) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    DateFormat.getDateTimeInstance(
                                        DateFormat.MEDIUM,
                                        DateFormat.SHORT,
                                    ).format(Date(snapshot.timestamp)),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "${snapshot.wordCount} words",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Hide" else "Preview")
                            }
                            TextButton(onClick = { onRestore(snapshot.timestamp) }) {
                                Text("Restore")
                            }
                        }
                        if (expanded) {
                            val preview by produceState(initialValue = "…", snapshot.timestamp) {
                                value = previewLoader(snapshot.timestamp).ifBlank { "(empty)" }
                            }
                            Text(
                                preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- dialogs

@Composable
fun RenameDialog(
    current: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document name") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Untitled document") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(value); onDismiss() }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun LinkDialog(
    initialText: String,
    initialUrl: String,
    textEditable: Boolean,
    canRemove: Boolean,
    onConfirm: (text: String, url: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (canRemove) "Edit link" else "Insert link") },
        text = {
            Column {
                if (textEditable) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text("Text") },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Link (https://…)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanUrl = url.trim().let {
                        if (it.isNotEmpty() && !it.contains("://") && !it.startsWith("#")) {
                            "https://$it"
                        } else {
                            it
                        }
                    }
                    if (cleanUrl.isNotEmpty()) onConfirm(text.trim(), cleanUrl)
                    onDismiss()
                },
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                if (canRemove) {
                    TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ExportDialog(
    onPdf: () -> Unit,
    onMarkdown: () -> Unit,
    onPlainText: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column {
                TextButton(
                    onClick = { onPdf(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Box(Modifier.fillMaxWidth()) { Text("PDF") } }
                TextButton(
                    onClick = { onMarkdown(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Box(Modifier.fillMaxWidth()) { Text("Markdown (.md)") } }
                TextButton(
                    onClick = { onPlainText(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Box(Modifier.fillMaxWidth()) { Text("Plain text (.txt)") } }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun WordCountDialog(stats: WordCount.Stats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Word count") },
        text = {
            Column {
                StatRow("Words", stats.words.toString())
                StatRow("Characters", stats.characters.toString())
                StatRow("Characters (no spaces)", stats.charactersNoSpaces.toString())
                StatRow("Paragraphs", stats.paragraphs.toString())
                StatRow("Reading time", "~${stats.readingMinutes} min")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeOption("Follow system", ThemeMode.SYSTEM, current, onSelect)
                ThemeOption("Light", ThemeMode.LIGHT, current, onSelect)
                ThemeOption("Dark", ThemeMode.DARK, current, onSelect)
                ThemeOption("Pure black (OLED)", ThemeMode.OLED, current, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun TextSizeDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text size") },
        text = {
            Column {
                Text(
                    "The quick brown fox jumps over the lazy dog.",
                    fontSize = value.toInt().sp,
                    lineHeight = (value.toInt() + 8).sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 13.sp)
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = AppPrefs.MIN_BODY_TEXT_SP.toFloat()..AppPrefs.MAX_BODY_TEXT_SP.toFloat(),
                        steps = AppPrefs.MAX_BODY_TEXT_SP - AppPrefs.MIN_BODY_TEXT_SP - 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text("A", fontSize = 22.sp)
                }
                Text(
                    "${value.toInt()} sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(value.toInt()); onDismiss() }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Draft") },
        text = {
            Column {
                Text(
                    "A word processor that respects your writing time.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "• Fully offline — the app has no network permission at all\n" +
                        "• No account, no ads, no subscriptions, no AI\n" +
                        "• Autosaves every couple of seconds, snapshots every 5 minutes\n" +
                        "• Opens and saves .draft and .docx, exports PDF, Markdown and plain text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Version 1.0 · Built with Jetpack Compose and compose-rich-editor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
