package com.draft.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.draft.editor.data.AppPrefs
import com.draft.editor.ui.editor.EditorScreen
import com.draft.editor.ui.home.HomeScreen
import com.draft.editor.ui.theme.DraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DraftApp()
        }
    }
}

@Composable
private fun DraftApp() {
    val prefs by Graph.preferences.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())

    // Two screens, one saveable slot: null = Home, non-null = document open.
    // rememberSaveable keeps the open document across rotation and process death.
    var openDocId by rememberSaveable { mutableStateOf<String?>(null) }

    DraftTheme(themeMode = prefs.themeMode) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val docId = openDocId
            if (docId == null) {
                HomeScreen(onOpenDocument = { openDocId = it })
            } else {
                EditorScreen(
                    docId = docId,
                    prefs = prefs,
                    onClose = { openDocId = null },
                )
            }
        }
    }
}
