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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.alkometr.app.core.model.Drink
import java.io.IOException

private val Context.drinksStore: DataStore<Preferences> by preferencesDataStore(name = "alkometr_drinks")

/**
 * Список выпитых порций в DataStore Preferences (JSON-массив).
 * Хранится история за всё время; экран истории показывает последние 7 дней,
 * на расчёт влияют только порции моложе 24 часов.
 */
class DrinksRepository(private val context: Context) {

    private val key = stringPreferencesKey("drinks_json")

    /** Сюда переносится JSON, который не удалось разобрать, чтобы он не пропал при следующей записи. */
    private val unreadableKey = stringPreferencesKey("drinks_json_unreadable")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Drink.serializer())

    val drinks: Flow<List<Drink>> = context.drinksStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[key]?.let(::decode) ?: emptyList()
        }

    /** null — если JSON не читается (повреждён или записан несовместимой версией). */
    private fun decode(raw: String): List<Drink>? =
        runCatching { json.decodeFromString(serializer, raw) }
            .getOrNull()
            ?.sortedBy { it.atMillis }

    suspend fun current(): List<Drink> = drinks.first()

    suspend fun save(drinks: List<Drink>) {
        context.drinksStore.edit { preferences ->
            preferences[key] = json.encodeToString(serializer, drinks.sortedBy { it.atMillis })
        }
    }

    /**
     * Читает, меняет и пишет список внутри одной транзакции DataStore:
     * параллельные add/delete/update выстраиваются в очередь и не затирают друг друга.
     */
    private suspend fun mutate(transform: (List<Drink>) -> List<Drink>) {
        context.drinksStore.edit { preferences ->
            val raw = preferences[key]
            val stored = raw?.let(::decode)
            if (raw != null && stored == null) preferences[unreadableKey] = raw
            val updated = transform(stored ?: emptyList()).sortedBy { it.atMillis }
            preferences[key] = json.encodeToString(serializer, updated)
        }
    }

    suspend fun add(drink: Drink) {
        mutate { it + drink }
    }

    suspend fun delete(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    suspend fun update(drink: Drink) {
        mutate { list -> list.map { if (it.id == drink.id) drink else it } }
    }

    suspend fun clear() {
        context.drinksStore.edit { it.clear() }
    }
}
