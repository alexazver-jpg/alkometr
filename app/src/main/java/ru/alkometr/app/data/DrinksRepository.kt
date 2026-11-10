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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Drink.serializer())

    val drinks: Flow<List<Drink>> = context.drinksStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val raw = preferences[key] ?: return@map emptyList()
            runCatching { json.decodeFromString(serializer, raw) }
                .getOrElse { emptyList() }
                .sortedBy { it.atMillis }
        }

    suspend fun current(): List<Drink> = drinks.first()

    suspend fun save(drinks: List<Drink>) {
        context.drinksStore.edit { preferences ->
            preferences[key] = json.encodeToString(serializer, drinks.sortedBy { it.atMillis })
        }
    }

    suspend fun add(drink: Drink) {
        save(current() + drink)
    }

    suspend fun delete(id: String) {
        save(current().filterNot { it.id == id })
    }

    suspend fun update(drink: Drink) {
        save(current().map { if (it.id == drink.id) drink else it })
    }

    suspend fun clear() {
        context.drinksStore.edit { it.clear() }
    }
}
