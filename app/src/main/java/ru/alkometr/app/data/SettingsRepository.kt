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

// Второй, независимый DataStore: своё имя файла ("alkometr_settings"), поэтому настройки
// и история порций физически лежат в разных файлах и не мешают друг другу.
// Свойство создаётся делегатом один раз на процесс — это требование DataStore.
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "alkometr_settings")

/**
 * Настройки в DataStore Preferences: весь объект [AppSettings] хранится одной JSON-строкой.
 * Так добавление нового поля не требует миграции ключей.
 *
 * Альтернатива — держать каждое поле отдельным ключом Preferences. Здесь выбран один JSON:
 * AppSettings читается и пишется целиком, и новое свойство с значением по умолчанию
 * просто появляется в модели, не ломая уже сохранённые данные.
 */
class SettingsRepository(private val context: Context) {

    // Ключ, под которым лежит JSON со всеми настройками.
    private val key = stringPreferencesKey("settings_json")

    /** Сюда переносится JSON, который не удалось разобрать, чтобы он не пропал при следующей записи. */
    // Страховка от потери данных: нечитаемую строку сохраняем отдельно, прежде чем писать поверх.
    private val unreadableKey = stringPreferencesKey("settings_json_unreadable")
    // ignoreUnknownKeys — переживаем поля, добавленные другой версией приложения;
    // encodeDefaults — пишем значения по умолчанию, чтобы файл был самодостаточным.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Flow настроек: холодный поток, который оживает при первой подписке.
    // Любая запись в DataStore вызовет новую эмиссию, и UI получит свежие настройки автоматически.
    val settings: Flow<AppSettings> = context.settingsStore.data
        // IOException (диск недоступен, файл занят) не должен ронять приложение:
        // в этом случае подставляем пустые Preferences, то есть настройки по умолчанию.
        .catch { error ->
            // Прочие ошибки — не про ввод-вывод, их скрывать нельзя: пробрасываем выше.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        // Превращаем снимок Preferences в объект AppSettings.
        .map { preferences ->
            // Нет ключа (первый запуск) или JSON не разобрался — отдаём AppSettings() с дефолтами.
            preferences[key]?.let(::decode) ?: AppSettings()
        }

    /** null — если JSON не читается (повреждён или записан несовместимой версией). */
    private fun decode(raw: String): AppSettings? =
        // runCatching + getOrNull: ошибка разбора не превращается в исключение наружу,
        // а сигнализирует null — вызывающий код сам решает, что показать пользователю.
        runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()

    // suspend: чтение с диска приостанавливает корутину, а не блокирует главный поток.
    // first() ждёт первое значение потока и отписывается — «прочитать текущее один раз».
    suspend fun current(): AppSettings = settings.first()

    // Полная перезапись настроек: используется экраном настроек и импортом резервной копии.
    suspend fun save(settings: AppSettings) {
        // edit — атомарная транзакция: либо новое значение сохранится целиком, либо останется старое.
        context.settingsStore.edit { preferences ->
            // Сериализуем объект целиком в одну строку и кладём под нашим ключом.
            preferences[key] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }

    /**
     * Читает, меняет и пишет настройки внутри одной транзакции DataStore:
     * параллельные правки (пресеты, порог) выстраиваются в очередь и не затирают друг друга.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        // Тот же приём, что и в DrinksRepository.mutate: чтение и запись — внутри одной edit,
        // поэтому две почти одновременные правки не потеряют изменения друг друга.
        context.settingsStore.edit { preferences ->
            // Текущая строка из хранилища (может быть null на первом запуске).
            val raw = preferences[key]
            // Разобранные настройки; null — если строки нет или она битая.
            val stored = raw?.let(::decode)
            // Битый JSON копируем в отдельный ключ, чтобы следующая запись не стёрла его навсегда.
            if (raw != null && stored == null) preferences[unreadableKey] = raw
            // transform получает текущие настройки и возвращает изменённую копию (copy);
            // если данных не было — начинаем с дефолтного AppSettings().
            preferences[key] = json.encodeToString(AppSettings.serializer(), transform(stored ?: AppSettings()))
        }
    }

    // Сброс к заводским значениям: удаляем ключи, и следующий читатель получит AppSettings().
    suspend fun reset() {
        // `it` — MutablePreferences внутри транзакции; clear() удаляет все ключи этого DataStore.
        context.settingsStore.edit { it.clear() }
    }
}
