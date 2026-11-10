package ru.alkometr.app

import android.app.Application
import ru.alkometr.app.data.DrinksRepository
import ru.alkometr.app.data.SettingsRepository

/**
 * Точка сборки зависимостей: репозитории живут столько же, сколько процесс.
 * Полноценный DI здесь избыточен — приложение из одного модуля и трёх экранов.
 */
class AlkometrApp : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val drinksRepository: DrinksRepository by lazy { DrinksRepository(this) }

    companion object {
        fun from(application: Application): AlkometrApp = application as AlkometrApp
    }
}
