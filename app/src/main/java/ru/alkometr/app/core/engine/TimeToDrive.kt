package ru.alkometr.app.core.engine

// Импорты: порция напитка и параметры модели — это входные данные прогноза.
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.ModelParams

/**
 * Прогноз по вождению: можно ли сейчас и когда станет можно.
 * Считается строго по юридическому порогу, а не по степени опьянения.
 *
 * Смысл разделения: степень опьянения — медицинская шкала (самочувствие, риски),
 * а «можно за руль» — юридический критерий, у каждой страны свой порог.
 *
 * @param bacNow концентрация «сейчас», г/л (то есть ‰).
 * @param thresholdGPerL порог, с которым сравниваем, г/л (‰); 0,3 ‰ = 0,3 г/л.
 * @param canDriveNow можно ли ехать прямо сейчас.
 * @param readyAtMillis когда концентрация опустится до порога, epoch millis; null — если можно уже сейчас.
 * @param remainingMillis сколько осталось ждать, мс; 0 — если ехать можно.
 */
data class DriveForecast(
    val bacNow: Double,  // текущая концентрация, г/л: показывается пользователю как «сейчас»
    val thresholdGPerL: Double,  // порог, по которому принималось решение, г/л
    val canDriveNow: Boolean,  // итоговый ответ «зелёный/красный» для главного экрана
    val readyAtMillis: Long?,  // абсолютное время «можно ехать»; null, если ждать не нужно или прогноза нет
    val remainingMillis: Long?,  // сколько ждать в миллисекундах — удобно для таймера обратного отсчёта
)

// `object` — синглтон без состояния: одни чистые функции, поэтому создавать экземпляр не нужно.
object TimeToDrive {

    /**
     * Строит прогноз на момент [nowMillis].
     *
     * Порог передаётся аргументом, а не берётся из настроек, — так функция остаётся
     * независимой от Android и её легко проверить юнит-тестами.
     *
     * @param drinks список выпитых порций.
     * @param p параметры модели (вес, r, ka, Vmax, Km).
     * @param nowMillis текущий момент, epoch millis.
     * @param thresholdGPerL юридический порог, г/л (‰).
     */
    fun forecast(
        drinks: List<Drink>,
        p: ModelParams,
        nowMillis: Long,
        thresholdGPerL: Double,
    ): DriveForecast {
        // Концентрация в крови прямо сейчас: интегрируем модель до текущего момента.
        val bac = BacEngine.bacAt(drinks, p, nowMillis)
        // Уже не выше порога — ехать можно, ждать не нужно.
        if (bac <= thresholdGPerL) {
            return DriveForecast(
                bacNow = bac,  // текущее значение для индикатора
                thresholdGPerL = thresholdGPerL,  // запоминаем, с чем сравнивали
                canDriveNow = true,  // ответ «можно»
                readyAtMillis = null,  // ждать нечего — конкретное время не нужно
                remainingMillis = 0L,  // остаток ожидания — ноль миллисекунд
            )
        }
        // Нельзя: ищем первый момент на спаде, когда концентрация дойдёт до порога.
        val ready = BacEngine.timeToThreshold(drinks, p, nowMillis, thresholdGPerL)
        // Собираем результат для случая «сейчас нельзя».
        return DriveForecast(
            bacNow = bac,
            thresholdGPerL = thresholdGPerL,
            canDriveNow = false,  // ответ «нельзя»
            // Готовое время или null, если за горизонт в 24 часа порог так и не будет достигнут.
            readyAtMillis = ready,
            // `?.let { }` — «если не null, посчитать разницу»; если null, останется null.
            remainingMillis = ready?.let { it - nowMillis },
        )
    }
}
