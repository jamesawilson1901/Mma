package com.draft.editor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.draftDataStore by preferencesDataStore(name = "draft_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }

data class AppPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val bodyTextSp: Int = DEFAULT_BODY_TEXT_SP,
) {
    companion object {
        const val DEFAULT_BODY_TEXT_SP = 17
        const val MIN_BODY_TEXT_SP = 14
        const val MAX_BODY_TEXT_SP = 24
    }
}

class PreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val textSizeKey = intPreferencesKey("body_text_sp")

    val prefs: Flow<AppPrefs> = context.draftDataStore.data.map { p ->
        AppPrefs(
            themeMode = p[themeKey]?.let { value ->
                runCatching { ThemeMode.valueOf(value) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            bodyTextSp = (p[textSizeKey] ?: AppPrefs.DEFAULT_BODY_TEXT_SP)
                .coerceIn(AppPrefs.MIN_BODY_TEXT_SP, AppPrefs.MAX_BODY_TEXT_SP),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.draftDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setBodyTextSp(sp: Int) {
        context.draftDataStore.edit {
            it[textSizeKey] = sp.coerceIn(AppPrefs.MIN_BODY_TEXT_SP, AppPrefs.MAX_BODY_TEXT_SP)
        }
    }
}
