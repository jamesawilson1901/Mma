package com.draft.editor

import android.app.Application
import android.content.Context
import com.draft.editor.data.DocumentStore
import com.draft.editor.data.PreferencesRepository
import com.draft.editor.data.RecentFilesRepository

/** MVVM-lite service locator: three singletons, no DI framework. */
object Graph {
    lateinit var documentStore: DocumentStore
        private set
    lateinit var recentFiles: RecentFilesRepository
        private set
    lateinit var preferences: PreferencesRepository
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        documentStore = DocumentStore(app)
        recentFiles = RecentFilesRepository(app)
        preferences = PreferencesRepository(app)
    }
}

class DraftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
