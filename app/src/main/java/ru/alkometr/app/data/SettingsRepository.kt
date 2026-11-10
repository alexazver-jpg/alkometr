package ru.alkometr.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ru.alkometr.app.core.model.AppSettings
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "alkometr_settings")

/**
 * Настройки в DataStore Preferences: весь объект [AppSettings] хранится одной JSON-строкой.
 * Так добавление нового поля не требует миграции ключей.
 */
class SettingsRepository(private val context: Context) {

    private val key = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val raw = preferences[key] ?: return@map AppSettings()
            runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
                .getOrElse { AppSettings() }
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(settings: AppSettings) {
        context.settingsStore.edit { preferences ->
            preferences[key] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        save(transform(current()))
    }

    suspend fun reset() {
        context.settingsStore.edit { it.clear() }
    }
}
