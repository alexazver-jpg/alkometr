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

// DataStore — это способ хранить небольшие данные (ключ-значение) на диск без ручных файлов
// и без SharedPreferences: запись атомарная, чтение отдаётся потоком Flow.
// Объявление ниже — свойство-расширение Context: файл создаётся один раз на процесс,
// а preferencesDataStore(...) реализует это через делегат.
// Такое свойство обязано быть объявлено один раз на верхнем уровне файла: если создать два
// DataStore с одним именем в одном процессе, Android выбросит исключение.
// "alkometr_drinks" — имя файла на диске; у настроек оно своё, поэтому данные не пересекаются.
private val Context.drinksStore: DataStore<Preferences> by preferencesDataStore(name = "alkometr_drinks")

/**
 * Список выпитых порций в DataStore Preferences (JSON-массив).
 * Хранится история за всё время; экран истории показывает последние 7 дней,
 * на расчёт влияют только порции моложе 24 часов.
 *
 * Ключевая идея: DataStore умеет хранить только простые типы (строки, числа, флаги),
 * поэтому весь список превращается в одну JSON-строку. Читать и писать её умеет
 * только этот класс — остальной код работает с обычным List<Drink>.
 */
class DrinksRepository(private val context: Context) {

    // Ключ, под которым лежит JSON-строка. Тип ключа (stringPreferencesKey) задаёт тип значения:
    // попытка прочитать его как Int — ошибка на этапе компиляции, а не в рантайме.
    private val key = stringPreferencesKey("drinks_json")

    /** Сюда переносится JSON, который не удалось разобрать, чтобы он не пропал при следующей записи. */
    // Резервная копия «сломанной» строки: если decode не смог разобрать данные, мы не стираем их
    // молча, а копируем в отдельный ключ — так пользовательские данные можно спасти вручную.
    private val unreadableKey = stringPreferencesKey("drinks_json_unreadable")
    // Один Json на весь репозиторий: настройки неизменны, объект потокобезопасен.
    // ignoreUnknownKeys — старые версии приложения не падают на полях, добавленных новыми.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // Сериализатор именно для списка: ListSerializer оборачивает сериализатор одного Drink,
    // и мы получаем пару «в JSON-массив / из JSON-массива» без ручного цикла.
    private val serializer = ListSerializer(Drink.serializer())

    // drinks — не функция, а свойство-поток. Flow холодный: пока его никто не собирает,
    // с диска ничего не читается. Как только подписчик появился, DataStore отдаёт текущее
    // значение и присылает новое после каждой записи — UI обновляется сам.
    val drinks: Flow<List<Drink>> = context.drinksStore.data
        // catch перехватывает ошибки выше по потоку. IOException — обычная ситуация (файл занят,
        // диск недоступен): показываем пустой список вместо падения приложения.
        .catch { error ->
            // Если ошибка не связана с вводом-выводом (например, баг в коде) — пробрасываем её дальше,
            // чтобы не скрывать настоящую проблему.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        // map преобразует каждый снимок Preferences в нужный нам List<Drink>.
        .map { preferences ->
            // preferences[key] вернёт null, если ключа ещё нет (первый запуск).
            // `?.let(::decode)` — вызов decode только при непустом значении;
            // `?: emptyList()` — запасной вариант, когда ключа нет или JSON не разобрался.
            preferences[key]?.let(::decode) ?: emptyList()
        }

    /** null — если JSON не читается (повреждён или записан несовместимой версией). */
    private fun decode(raw: String): List<Drink>? =
        // runCatching ловит исключение и превращает его в Result — приложение не падает
        // из-за одной испорченной строки в хранилище.
        runCatching { json.decodeFromString(serializer, raw) }
            // getOrNull: при ошибке получаем null, и вызывающий код сам решает, что делать.
            .getOrNull()
            // Сортируем по времени: в файле порядок может быть любым, а расчёт кривой
            // ожидает хронологию. `it` — очередной Drink.
            ?.sortedBy { it.atMillis }

    // suspend — функция может приостанавливаться, не блокируя поток. Вызывать её можно
    // только из корутины (viewModelScope.launch в ViewModel).
    // first() берёт первое значение из потока и сразу отписывается: это «прочитать один раз».
    suspend fun current(): List<Drink> = drinks.first()

    // Полная перезапись списка. Нужна при импорте резервной копии.
    suspend fun save(drinks: List<Drink>) {
        // edit — транзакция: читает текущие Preferences, даёт их изменить и атомарно сохраняет.
        // Если приложение умрёт посередине, файл останется в прежнем состоянии, а не «полузаписанным».
        context.drinksStore.edit { preferences ->
            // encodeToString превращает список в строку; сортировка снова нужна,
            // чтобы файл на диске был предсказуемым и не «дёргался» без реальных изменений.
            preferences[key] = json.encodeToString(serializer, drinks.sortedBy { it.atMillis })
        }
    }

    /**
     * Читает, меняет и пишет список внутри одной транзакции DataStore:
     * параллельные add/delete/update выстраиваются в очередь и не затирают друг друга.
     */
    private suspend fun mutate(transform: (List<Drink>) -> List<Drink>) {
        // Транзакция DataStore выполняется последовательно: пока одна edit не закончилась,
        // вторая ждёт. Поэтому шаблон «прочитать -> изменить -> записать» не теряет данные,
        // хотя add и delete могут быть вызваны почти одновременно.
        context.drinksStore.edit { preferences ->
            // Сырое значение из хранилища: может быть null (первый запуск).
            val raw = preferences[key]
            // Пытаемся разобрать. null означает «строка есть, но не читается».
            val stored = raw?.let(::decode)
            // Если строка была, но разобрать не удалось — сохраняем её копию в отдельный ключ,
            // иначе следующая запись перезапишет её и данные исчезнут безвозвратно.
            if (raw != null && stored == null) preferences[unreadableKey] = raw
            // transform получает текущий список (или пустой) и возвращает новый;
            // сортировка гарантирует хронологический порядок после любого изменения.
            val updated = transform(stored ?: emptyList()).sortedBy { it.atMillis }
            // Записываем результат обратно в виде JSON.
            preferences[key] = json.encodeToString(serializer, updated)
        }
    }

    suspend fun add(drink: Drink) {
        // `it + drink` создаёт новый список, а не меняет старый: неизменяемость защищает
        // от случайного изменения данных, которые уже кто-то читает.
        mutate { it + drink }
    }

    suspend fun delete(id: String) {
        // filterNot оставляет все порции, кроме той, у которой совпал id.
        mutate { list -> list.filterNot { it.id == id } }
    }

    suspend fun update(drink: Drink) {
        // map заменяет элемент с тем же id на новую версию, остальные оставляет как были.
        // `it` во внутренней лямбде — элемент списка; во внешней — список.
        mutate { list -> list.map { if (it.id == drink.id) drink else it } }
    }

    suspend fun clear() {
        // clear() у Preferences удаляет все ключи в этом DataStore целиком.
        // `it` — MutablePreferences, объект, который можно менять внутри edit.
        context.drinksStore.edit { it.clear() }
    }
}
