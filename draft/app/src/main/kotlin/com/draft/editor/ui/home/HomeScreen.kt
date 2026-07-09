package com.draft.editor.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draft.editor.data.RecentDoc
import java.text.DateFormat
import java.util.Date

/** Mime types the system picker offers; .draft files come through as octet-stream/zip. */
private val OPENABLE_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/octet-stream",
    "application/zip",
    "text/plain",
    "text/markdown",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDocument: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.openFromDevice(uri, onReady = onOpenDocument)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Draft") },
                actions = {
                    IconButton(onClick = { openLauncher.launch(OPENABLE_TYPES) }) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = "Open a document from this device",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.newDocument(onReady = onOpenDocument) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New document") },
            )
        },
    ) { padding ->
        if (recents.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpen = { openLauncher.launch(OPENABLE_TYPES) },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recents, key = { it.docId }) { doc ->
                    RecentDocRow(doc = doc, onClick = { onOpenDocument(doc.docId) })
                }
            }
        }
    }
}

@Composable
private fun RecentDocRow(doc: RecentDoc, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = doc.title.ifBlank { "Untitled document" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (doc.preview.isNotBlank()) {
                    Text(
                        text = doc.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatModified(doc.modifiedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatModified(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val minutes = (now - timestamp) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onOpen: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Nothing here yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Start a new document or open one from your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onOpen) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Open from device")
            }
        }
    }
}
