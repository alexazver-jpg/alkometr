package ru.alkometr.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import ru.alkometr.app.AlkometrApp
import ru.alkometr.app.core.engine.BacCurve
import ru.alkometr.app.core.engine.BacEngine
import ru.alkometr.app.core.engine.DriveForecast
import ru.alkometr.app.core.engine.PeakInfo
import ru.alkometr.app.core.engine.TimeToDrive
import ru.alkometr.app.core.model.AppSettings
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.DrinkPreset
import ru.alkometr.app.core.model.IntoxicationLevel
import java.util.UUID

/** Полное состояние главного экрана. Иммутабельный снимок, UI только рисует. */
data class MainUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val drinks: List<Drink> = emptyList(),
    val nowMillis: Long = 0L,
    val bacNow: Double = 0.0,
    val level: IntoxicationLevel = IntoxicationLevel.SOBER,
    val peak: PeakInfo = PeakInfo(0L, 0.0),
    val peakAhead: Boolean = false,
    val forecast: DriveForecast = DriveForecast(0.0, 0.3, true, null, 0L),
    val curve: BacCurve = BacCurve(emptyList()),
    val curveStartMillis: Long = 0L,
    val curveEndMillis: Long = 0L,
    val lastAddedId: String? = null,
) {
    val isEmptySession: Boolean
        get() = drinks.isEmpty()

    /** Последняя по времени порция (не обязательно только что добавленная). */
    val lastDrink: Drink?
        get() = drinks.maxByOrNull { it.atMillis }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = AlkometrApp.from(application)
    private val settingsRepository = app.settingsRepository
    private val drinksRepository = app.drinksRepository

    private val lastAddedId = MutableStateFlow<String?>(null)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settings,
        drinksRepository.drinks,
        ticker,
    ) { settings, drinks, now ->
        buildState(settings, drinks, now)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(),
    )

    private fun buildState(settings: AppSettings, drinks: List<Drink>, now: Long): MainUiState {
        val params = settings.modelParams
        val firstDrink = drinks.minOfOrNull { it.atMillis }
        // Порции старше 24 ч на модель не влияют (BacEngine), поэтому окно кривой не растягиваем на всю историю.
        val curveStart = if (firstDrink == null) now - 6 * HOUR else {
            maxOf(minOf(firstDrink, now) - 30 * MINUTE, now - BacEngine.HORIZON_MILLIS)
        }
        val curveEnd = if (firstDrink == null) now + 6 * HOUR else maxOf(firstDrink, now) + 24 * HOUR

        val points = BacEngine.curve(drinks, params, curveStart, curveEnd)
        val curve = BacCurve.of(points)
        val bacNow = if (drinks.isEmpty()) 0.0 else curve.valueAt(now)
        val peak = curve.peak()
        val forecast = TimeToDrive.forecast(drinks, params, now, settings.thresholdPermille)

        return MainUiState(
            loading = false,
            settings = settings,
            drinks = drinks,
            nowMillis = now,
            bacNow = bacNow,
            level = IntoxicationLevel.forBac(bacNow),
            peak = peak,
            peakAhead = peak.atMillis > now + MINUTE,
            forecast = forecast,
            curve = curve,
            curveStartMillis = curveStart,
            curveEndMillis = curveEnd,
            lastAddedId = lastAddedId.value,
        )
    }

    /** Добавить порцию по пресету «прямо сейчас». */
    fun addDrink(preset: DrinkPreset) {
        addDrink(
            volumeMl = preset.volumeMl,
            abvPercent = preset.abvPercent,
            kind = preset.kind,
            atMillis = System.currentTimeMillis(),
        )
    }

    fun addDrink(volumeMl: Double, abvPercent: Double, kind: DrinkKind, atMillis: Long) {
        if (volumeMl <= 0.0 || abvPercent <= 0.0) return
        val drink = Drink(
            id = UUID.randomUUID().toString(),
            atMillis = atMillis,
            volumeMl = volumeMl,
            abvPercent = abvPercent,
            kind = kind,
        )
        viewModelScope.launch(Dispatchers.IO) {
            drinksRepository.add(drink)
            lastAddedId.value = drink.id
        }
    }

    fun deleteDrink(id: String) {
        viewModelScope.launch(Dispatchers.IO) { drinksRepository.delete(id) }
    }

    fun updateDrink(drink: Drink) {
        viewModelScope.launch(Dispatchers.IO) { drinksRepository.update(drink) }
    }

    fun undoLastAdded() {
        val id = lastAddedId.value
        if (id != null) {
            deleteDrink(id)
            lastAddedId.value = null
        }
    }

    fun clearSession() {
        viewModelScope.launch(Dispatchers.IO) {
            drinksRepository.clear()
            lastAddedId.value = null
        }
    }

    fun savePreset(preset: DrinkPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.update { settings ->
                val exists = settings.presets.any { it.id == preset.id }
                val presets = if (exists) {
                    settings.presets.map { if (it.id == preset.id) preset else it }
                } else {
                    settings.presets + preset
                }
                settings.copy(presets = presets)
            }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.update { settings ->
                settings.copy(presets = settings.presets.filterNot { it.id == id })
            }
        }
    }

    /** Сохранить настройки целиком (экран настроек присылает готовый объект). */
    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.save(settings) }
    }

    /** Применить импортированную резервную копию: настройки и порции заменяются целиком. */
    fun applyBackup(backup: ru.alkometr.app.data.Backup) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.save(backup.settings)
            drinksRepository.save(backup.drinks)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { drinksRepository.clear() }
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) {
            drinksRepository.clear()
            settingsRepository.reset()
        }
    }

    /** Готовый JSON резервной копии по текущему состоянию. */
    fun buildBackupJson(): String {
        val current = uiState.value
        return ru.alkometr.app.data.BackupCodec.encode(
            settings = current.settings,
            drinks = current.drinks,
            exportedAtMillis = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
