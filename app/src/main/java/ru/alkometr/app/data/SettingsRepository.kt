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

    /** Сюда переносится JSON, который не удалось разобрать, чтобы он не пропал при следующей записи. */
    private val unreadableKey = stringPreferencesKey("settings_json_unreadable")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[key]?.let(::decode) ?: AppSettings()
        }

    /** null — если JSON не читается (повреждён или записан несовместимой версией). */
    private fun decode(raw: String): AppSettings? =
        runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(settings: AppSettings) {
        context.settingsStore.edit { preferences ->
            preferences[key] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }

    /**
     * Читает, меняет и пишет настройки внутри одной транзакции DataStore:
     * параллельные правки (пресеты, порог) выстраиваются в очередь и не затирают друг друга.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { preferences ->
            val raw = preferences[key]
            val stored = raw?.let(::decode)
            if (raw != null && stored == null) preferences[unreadableKey] = raw
            preferences[key] = json.encodeToString(AppSettings.serializer(), transform(stored ?: AppSettings()))
        }
    }

    suspend fun reset() {
        context.settingsStore.edit { it.clear() }
    }
}
