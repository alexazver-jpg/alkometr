package ru.alkometr.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.alkometr.app.AlkometrApp
import ru.alkometr.app.core.engine.BacCurve
import ru.alkometr.app.core.engine.BacEngine
import ru.alkometr.app.core.engine.DriveForecast
import ru.alkometr.app.core.engine.PeakInfo
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

/**
 * Состояние экрана собирается в два слоя:
 *  - тяжёлый слой (кривая, пик) пересчитывается только при изменении порций или настроек
 *    и раз в 5 минут, чтобы окно не устаревало;
 *  - лёгкий слой (текущее время, уровень, остаток) обновляется раз в секунду
 *    и читает значение из уже готовой кривой.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = AlkometrApp.from(application)
    private val settingsRepository = app.settingsRepository
    private val drinksRepository = app.drinksRepository

    private val lastAddedId = MutableStateFlow<String?>(null)

    /** Тикер «сейчас»: раз в секунду, без пересчёта кривой. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    /** Редкий тикер, который обновляет окно кривой и прогноз, если приложение открыто долго. */
    private val heavyTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(HEAVY_REFRESH_MILLIS)
        }
    }

    private data class HeavyState(
        val settings: AppSettings,
        val drinks: List<Drink>,
        val curve: BacCurve,
        val peak: PeakInfo,
        val startMillis: Long,
        val endMillis: Long,
    )

    private val heavyState: Flow<HeavyState> = combine(
        settingsRepository.settings,
        drinksRepository.drinks,
        heavyTicker,
    ) { settings, drinks, now ->
        buildHeavyState(settings, drinks, now)
    }

    val uiState: StateFlow<MainUiState> = combine(
        heavyState,
        ticker,
        lastAddedId,
    ) { heavy, now, addedId ->
        buildUiState(heavy, now, addedId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(),
    )

    private fun buildHeavyState(settings: AppSettings, drinks: List<Drink>, now: Long): HeavyState {
        val params = settings.modelParams
        val firstDrink = drinks.minOfOrNull { it.atMillis }
        val curveStart = if (firstDrink == null) now - 6 * HOUR else minOf(firstDrink, now) - 30 * MINUTE
        val curveEnd = if (firstDrink == null) now + 6 * HOUR else maxOf(firstDrink, now) + 24 * HOUR

        val curve = BacCurve.of(BacEngine.curve(drinks, params, curveStart, curveEnd))
        return HeavyState(
            settings = settings,
            drinks = drinks,
            curve = curve,
            peak = curve.peak(),
            startMillis = curveStart,
            endMillis = curveEnd,
        )
    }

    private fun buildUiState(heavy: HeavyState, now: Long, addedId: String?): MainUiState {
        val settings = heavy.settings
        val threshold = settings.thresholdPermille
        val bacNow = if (heavy.drinks.isEmpty()) 0.0 else heavy.curve.valueAt(now)

        val canDriveNow = bacNow <= threshold
        val readyAt = if (canDriveNow) null else heavy.curve.firstBelow(threshold, now)
        val forecast = DriveForecast(
            bacNow = bacNow,
            thresholdGPerL = threshold,
            canDriveNow = canDriveNow,
            readyAtMillis = readyAt,
            remainingMillis = readyAt?.let { it - now },
        )

        return MainUiState(
            loading = false,
            settings = settings,
            drinks = heavy.drinks,
            nowMillis = now,
            bacNow = bacNow,
            level = IntoxicationLevel.forBac(bacNow),
            peak = heavy.peak,
            peakAhead = heavy.peak.atMillis > now + MINUTE,
            forecast = forecast,
            curve = heavy.curve,
            curveStartMillis = heavy.startMillis,
            curveEndMillis = heavy.endMillis,
            lastAddedId = addedId,
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
        const val HEAVY_REFRESH_MILLIS = 5 * MINUTE
    }
}
