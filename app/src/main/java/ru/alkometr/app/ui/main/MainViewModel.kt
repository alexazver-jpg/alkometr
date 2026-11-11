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
    // loading = true, пока данные ещё не пришли из DataStore: экран показывает заглушку,
    // а не нули, которые легко принять за настоящий результат «трезв».
    val loading: Boolean = true,
    // Настройки пользователя: единицы, порог, параметры модели, пресеты.
    val settings: AppSettings = AppSettings(),
    // История порций, уже отсортированная по времени.
    val drinks: List<Drink> = emptyList(),
    // Момент «сейчас» на время сборки состояния. Храним его в состоянии, чтобы все подписи
    // на экране считались от одного и того же мгновения и не расходились между собой.
    val nowMillis: Long = 0L,
    // Концентрация алкоголя в крови сейчас, во внутренних единицах (граммы на литр).
    val bacNow: Double = 0.0,
    // Словесный уровень опьянения: по нему выбираются цвет и подпись индикатора.
    val level: IntoxicationLevel = IntoxicationLevel.SOBER,
    // Пик кривой: когда и насколько высоко значение. Нужен для подсказки «максимум впереди».
    val peak: PeakInfo = PeakInfo(0L, 0.0),
    // true, если пик ещё не наступил: тогда честно предупреждаем, что сейчас не максимум.
    val peakAhead: Boolean = false,
    // Прогноз поездки: можно ли ехать, когда будет можно и сколько осталось ждать.
    // Аргументы позиционные (bacNow, threshold, canDriveNow, readyAt, remaining),
    // поэтому важен их порядок; это значение-заглушка до первого расчёта.
    val forecast: DriveForecast = DriveForecast(0.0, 0.3, true, null, 0L),
    // Готовая кривая выведения — тяжёлый расчёт, который пересчитывается редко.
    val curve: BacCurve = BacCurve(emptyList()),
    // Границы окна кривой: по ним график строит ось времени.
    val curveStartMillis: Long = 0L,
    val curveEndMillis: Long = 0L,
    // id последней добавленной порции: для анимации появления и для кнопки «отменить».
    val lastAddedId: String? = null,
) {
    // Производное свойство: не хранится в состоянии, а вычисляется при каждом чтении.
    // Так его невозможно забыть обновить, когда меняется drinks.
    val isEmptySession: Boolean
        get() = drinks.isEmpty()

    /** Последняя по времени порция (не обязательно только что добавленная). */
    val lastDrink: Drink?
        // maxByOrNull возвращает null для пустого списка, поэтому тип Drink?.
        get() = drinks.maxByOrNull { it.atMillis }
}

/**
 * Состояние экрана собирается в два слоя:
 *  - тяжёлый слой (кривая, пик) пересчитывается только при изменении порций или настроек
 *    и раз в 5 минут, чтобы окно не устаревало;
 *  - лёгкий слой (текущее время, уровень, остаток) обновляется раз в секунду
 *    и читает значение из уже готовой кривой.
 *
 * Зачем так: построение кривой — это интегрирование модели по всему окну (тысячи точек).
 * Считать его каждую секунду бессмысленно: за секунду кривая почти не меняется.
 * Поэтому тяжёлый результат кэшируется в HeavyState, а секундный тик лишь выбирает
 * из него значение для текущего момента — это дешёвая операция.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // AndroidViewModel умеет отдать Application — из него берём наши репозитории.
    // from(...) приводит Application к AlkometrApp (см. companion в AlkometrApp).
    private val app = AlkometrApp.from(application)
    // Ссылки на репозитории: единственный источник данных для ViewModel.
    private val settingsRepository = app.settingsRepository
    private val drinksRepository = app.drinksRepository

    // MutableStateFlow — изменяемое состояние (у обычного Flow менять значение нельзя).
    // null означает «нет только что добавленной порции».
    private val lastAddedId = MutableStateFlow<String?>(null)

    /** Тикер «сейчас»: раз в секунду, без пересчёта кривой. */
    private val ticker = flow {
        // flow { } — построитель холодного потока: тело выполняется заново для каждого
        // подписчика, в его корутине, и останавливается, когда подписчик уходит.
        while (true) {
            // emit отдаёт очередное значение вниз по потоку (в combine).
            emit(System.currentTimeMillis())
            // delay приостанавливает корутину на секунду, не блокируя поток: другие
            // корутины продолжают работать, пока эта «спит».
            delay(1_000L)
        }
    }

    /** Редкий тикер, который обновляет окно кривой и прогноз, если приложение открыто долго. */
    private val heavyTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            // Раз в 5 минут: окно кривой сдвигается вперёд, иначе график «застынет»
            // на времени запуска приложения.
            delay(HEAVY_REFRESH_MILLIS)
        }
    }

    // Внутренний снимок тяжёлого слоя: то, что посчитано один раз и переиспользуется
    // при каждом секундном тике. private — UI об этом классе не знает,
    // поэтому его поля можно менять, не задевая экран.
    private data class HeavyState(
        val settings: AppSettings,
        val drinks: List<Drink>,
        val curve: BacCurve,
        val peak: PeakInfo,
        val startMillis: Long,
        val endMillis: Long,
    )

    // combine объединяет несколько потоков: лямбда вызывается заново, когда новое значение
    // приходит из любого из них. Источники — настройки и порции (меняются редко)
    // плюс редкий тикер.
    private val heavyState: Flow<HeavyState> = combine(
        settingsRepository.settings,
        drinksRepository.drinks,
        heavyTicker,
    ) { settings, drinks, now ->
        // Каждый вызов — полный пересчёт кривой. Он дорогой, поэтому происходит только здесь,
        // а не на каждом секундном тике.
        buildHeavyState(settings, drinks, now)
    }

    // uiState — публичное состояние экрана: единственное, что читает Compose.
    val uiState: StateFlow<MainUiState> = combine(
        heavyState,
        ticker,
        lastAddedId,
    ) { heavy, now, addedId ->
        // Лёгкий слой: из готовой кривой берём значение для текущего момента.
        // Секундный тик не пересчитывает кривую — только читает её.
        buildUiState(heavy, now, addedId)
    }.stateIn(
        // stateIn превращает холодный поток в горячий StateFlow: у него есть текущее значение,
        // которое можно прочитать синхронно через .value.
        scope = viewModelScope,
        // WhileSubscribed(5_000): поток живёт, пока есть подписчики, и ещё 5 секунд после
        // их исчезновения. Этого хватает, чтобы пережить поворот экрана без остановки тикеров.
        started = SharingStarted.WhileSubscribed(5_000L),
        // Значение до первого расчёта: loading = true, нули и пустая история.
        initialValue = MainUiState(),
    )

    private fun buildHeavyState(settings: AppSettings, drinks: List<Drink>, now: Long): HeavyState {
        // Параметры модели (вес, пол, скорость выведения) — часть настроек:
        // от них зависит и высота, и форма кривой.
        val params = settings.modelParams
        // Самая ранняя порция нужна, чтобы начать график раньше неё. null — история пуста.
        val firstDrink = drinks.minOfOrNull { it.atMillis }
        // Левая граница окна: для пустой истории показываем 6 часов назад (чтобы график
        // не выглядел обрубленным), иначе — за 30 минут до первой порции.
        // minOf(firstDrink, now) не даёт уйти в будущее, если все порции ещё впереди.
        val curveStart = if (firstDrink == null) now - 6 * HOUR else minOf(firstDrink, now) - 30 * MINUTE
        // Правая граница: сутки после последней порции — столько нужно, чтобы увидеть
        // полное выведение. maxOf(firstDrink, now) учитывает порции, поставленные на будущее.
        val curveEnd = if (firstDrink == null) now + 6 * HOUR else maxOf(firstDrink, now) + 24 * HOUR

        // Тяжёлый расчёт: считаем модель на всём окне и упаковываем точки в BacCurve.
        // BacCurve.of — удобная обёртка с методами поиска (peak, valueAt, firstBelow).
        val curve = BacCurve.of(BacEngine.curve(drinks, params, curveStart, curveEnd))
        return HeavyState(
            settings = settings,
            drinks = drinks,
            curve = curve,
            // Пик считаем здесь же: он нужен подписи и графику, а меняется только вместе с кривой.
            peak = curve.peak(),
            startMillis = curveStart,
            endMillis = curveEnd,
        )
    }

    private fun buildUiState(heavy: HeavyState, now: Long, addedId: String?): MainUiState {
        // Из тяжёлого снимка достаём то, что не меняется в течение этой секунды.
        val settings = heavy.settings
        // Порог из настроек: с ним сравниваем значение, чтобы ответить «можно ехать».
        val threshold = settings.thresholdPermille
        // Пустая история — гарантированный ноль, чтобы не зависеть от поведения интерполяции
        // на краю окна. Иначе читаем значение готовой кривой в момент now.
        val bacNow = if (heavy.drinks.isEmpty()) 0.0 else heavy.curve.valueAt(now)

        // Основной ответ приложения: текущее значение не выше порога.
        val canDriveNow = bacNow <= threshold
        // Если сейчас нельзя — ищем в кривой первый момент после now, когда значение
        // опустится до порога. null означает «ждать не нужно».
        val readyAt = if (canDriveNow) null else heavy.curve.firstBelow(threshold, now)
        val forecast = DriveForecast(
            bacNow = bacNow,
            thresholdGPerL = threshold,
            canDriveNow = canDriveNow,
            readyAtMillis = readyAt,
            // Сколько осталось ждать: readyAt?.let считает разницу только когда время известно,
            // иначе останется null (а не отрицательное число).
            remainingMillis = readyAt?.let { it - now },
        )

        return MainUiState(
            // Расчёт выполнен — заглушку загрузки больше не показываем.
            loading = false,
            settings = settings,
            drinks = heavy.drinks,
            nowMillis = now,
            bacNow = bacNow,
            // Уровень вычисляет модель по значению: экран не хранит пороги у себя,
            // поэтому цвета и подписи всегда согласованы с расчётом.
            level = IntoxicationLevel.forBac(bacNow),
            peak = heavy.peak,
            // Допуск в минуту, чтобы «пик прямо сейчас» не мигал на границе секунды.
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
        // Пресет — сохранённый набор «объём + крепость + тип». Время подставляем текущее,
        // поэтому сценарий называется «прямо сейчас».
        addDrink(
            volumeMl = preset.volumeMl,
            abvPercent = preset.abvPercent,
            kind = preset.kind,
            atMillis = System.currentTimeMillis(),
        )
    }

    // Основная перегрузка: сюда приходят и пресеты, и ручной ввод с экрана.
    fun addDrink(volumeMl: Double, abvPercent: Double, kind: DrinkKind, atMillis: Long) {
        // Простая валидация: нулевой объём или нулевая крепость не дадут вклада в модель,
        // но создадут запись-мусор в истории.
        if (volumeMl <= 0.0 || abvPercent <= 0.0) return
        val drink = Drink(
            // UUID даёт уникальный id без обращения к базе: по нему потом правят и удаляют.
            id = UUID.randomUUID().toString(),
            atMillis = atMillis,
            volumeMl = volumeMl,
            abvPercent = abvPercent,
            kind = kind,
        )
        // launch запускает корутину в viewModelScope: она живёт, пока жива ViewModel,
        // и отменяется вместе с ней — утечек нет.
        // Dispatchers.IO — пул потоков для диска: запись в DataStore не должна идти
        // в главном потоке, иначе интерфейс на мгновение замрёт.
        viewModelScope.launch(Dispatchers.IO) {
            drinksRepository.add(drink)
            // id запоминаем после успешной записи: кнопка «отменить» должна отменять
            // реально сохранённую порцию.
            lastAddedId.value = drink.id
        }
    }

    fun deleteDrink(id: String) {
        // Тот же шаблон: suspend-вызов репозитория внутри корутины на IO-диспетчере.
        viewModelScope.launch(Dispatchers.IO) { drinksRepository.delete(id) }
    }

    fun updateDrink(drink: Drink) {
        // Правка существующей записи: репозиторий найдёт её по id и заменит целиком.
        viewModelScope.launch(Dispatchers.IO) { drinksRepository.update(drink) }
    }

    fun undoLastAdded() {
        // .value у MutableStateFlow читается синхронно — это значение прямо сейчас.
        val id = lastAddedId.value
        if (id != null) {
            deleteDrink(id)
            // Сбрасываем в null: отменить можно только один раз, иначе удалили бы уже другую запись.
            lastAddedId.value = null
        }
    }

    fun clearSession() {
        // Чистим историю порций и заодно сбрасываем «последнюю добавленную»: отменять уже нечего.
        viewModelScope.launch(Dispatchers.IO) {
            drinksRepository.clear()
            lastAddedId.value = null
        }
    }

    fun savePreset(preset: DrinkPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            // update получает текущие настройки и возвращает новую версию. Всё внутри
            // одной транзакции DataStore, поэтому параллельная правка не потеряется.
            settingsRepository.update { settings ->
                // Есть ли уже пресет с таким id: если да, это редактирование, а не добавление.
                val exists = settings.presets.any { it.id == preset.id }
                // Заменяем совпавший элемент или дописываем новый в конец списка.
                val presets = if (exists) {
                    settings.presets.map { if (it.id == preset.id) preset else it }
                } else {
                    settings.presets + preset
                }
                // copy создаёт новый объект настроек с другим списком пресетов:
                // остальные поля сохраняются, исходный объект не меняется.
                settings.copy(presets = presets)
            }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // filterNot убирает пресет с нужным id, порядок остальных сохраняется.
            settingsRepository.update { settings ->
                settings.copy(presets = settings.presets.filterNot { it.id == id })
            }
        }
    }

    /** Сохранить настройки целиком (экран настроек присылает готовый объект). */
    fun saveSettings(settings: AppSettings) {
        // Полная перезапись, а не update: экран уже собрал целый объект,
        // и частичные правки здесь не нужны.
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.save(settings) }
    }

    /** Применить импортированную резервную копию: настройки и порции заменяются целиком. */
    fun applyBackup(backup: ru.alkometr.app.data.Backup) {
        // Два независимых хранилища — две записи. Порядок не важен: UI обновится,
        // когда оба потока пришлют новые значения.
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.save(backup.settings)
            drinksRepository.save(backup.drinks)
        }
    }

    fun clearHistory() {
        // Удаляем только историю; настройки остаются нетронутыми.
        viewModelScope.launch(Dispatchers.IO) { drinksRepository.clear() }
    }

    fun resetAll() {
        // Полный сброс: история и настройки (включая пресеты) возвращаются к заводским.
        viewModelScope.launch(Dispatchers.IO) {
            drinksRepository.clear()
            settingsRepository.reset()
        }
    }

    /** Готовый JSON резервной копии по текущему состоянию. */
    fun buildBackupJson(): String {
        // .value у StateFlow — последнее собранное состояние. Теоретически это может быть
        // initialValue, если экран только открылся, но к нажатию «экспорт» данные уже пришли.
        val current = uiState.value
        // Кодирование — чистая операция над уже загруженными данными, поэтому без launch:
        // диск не читается, и подвесить интерфейс нечем.
        return ru.alkometr.app.data.BackupCodec.encode(
            settings = current.settings,
            drinks = current.drinks,
            // Время экспорта фиксируем здесь, чтобы в файле осталась дата создания копии.
            exportedAtMillis = System.currentTimeMillis(),
        )
    }

    private companion object {
        // Именованные константы времени: 60_000L — одна минута в миллисекундах;
        // подчёркивания помогают не пересчитывать нули глазами.
        const val MINUTE = 60_000L
        // Час выражаем через минуту — меньше шансов ошибиться в разрядах.
        const val HOUR = 60 * MINUTE
        // Как часто пересчитывать тяжёлый слой, пока приложение открыто.
        const val HEAVY_REFRESH_MILLIS = 5 * MINUTE
    }
}
