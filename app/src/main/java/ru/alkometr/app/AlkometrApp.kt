package ru.alkometr.app

import android.app.Application
import ru.alkometr.app.data.DrinksRepository
import ru.alkometr.app.data.SettingsRepository

/**
 * Точка сборки зависимостей: репозитории живут столько же, сколько процесс.
 * Полноценный DI здесь избыточен — приложение из одного модуля и трёх экранов.
 *
 * Что важно понять новичку:
 *  - [Application] — объект, который Android создаёт один раз при старте процесса приложения
 *    и хранит, пока процесс жив. Это удобное место для общих объектов;
 *  - репозиторий — класс, который знает, ГДЕ лежат данные (здесь это DataStore).
 *    Экраны и ViewModel спрашивают данные у репозитория и не знают, файл это или база;
 *  - оба репозитория создаются лениво (`by lazy`), то есть при первом обращении.
 */
class AlkometrApp : Application() {

    // Настройки пользователя (порог промилле, единицы, пресеты) — один DataStore.
    // `by lazy { ... }` значит: тело в фигурных скобках выполнится ровно один раз при первом
    // чтении свойства, а дальше вернётся тот же самый объект. Поэтому не нужен onCreate:
    // создание объекта дешёвое и ничего не читает с диска.
    // `this` — сам Application; он же Context, а Context нужен DataStore, чтобы найти файл.
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    // История выпитых порций — отдельный DataStore (отдельный файл на диске).
    // Держим её отдельно от настроек: данные меняются с разной частотой, и повреждённый
    // JSON одного хранилища не должен ломать другое.
    val drinksRepository: DrinksRepository by lazy { DrinksRepository(this) }

    // companion object — «статическая» часть класса: снаружи обращаемся как AlkometrApp.from(...).
    companion object {
        // Приводит Application к нашему типу, чтобы из Activity или ViewModel добраться
        // до репозиториев. `as` — приведение типа: если в манифесте указан другой класс
        // Application, приложение упадёт сразу при первом вызове, и ошибку легко заметить.
        fun from(application: Application): AlkometrApp = application as AlkometrApp
    }
}
