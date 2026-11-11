package ru.alkometr.app.core.engine

/**
 * Готовая кривая для отрисовки: точки + быстрый поиск значения по времени.
 * Используется и шкалой-индикатором, и шкалой времени.
 *
 * Класс — тонкая обёртка над неизменяемым списком [CurvePoint]: `List` в Kotlin только для
 * чтения, поэтому объект можно спокойно передавать в UI-слой, не боясь, что его изменят.
 * Поиск значения сделан бинарным, это O(log n) вместо O(n) у обычного перебора.
 *
 * @param points точки кривой, отсортированные по времени — для бинарного поиска это обязательно.
 */
class BacCurve(private val points: List<CurvePoint>) {

    // `private val` в конструкторе — поле, доступное только внутри класса; снаружи его видно через `all`.
    /** Пустая ли кривая: нет ни одной точки. */
    val isEmpty: Boolean
        // Геттер без блока: просто возвращает результат выражения, тело в фигурных скобках не нужно.
        get() = points.isEmpty()

    /** Время первой точки, мс; 0 — если точек нет. */
    val startMillis: Long
        // `firstOrNull()?.atMillis` — безопасный доступ; `?: 0L` подставляет 0, если список пуст.
        get() = points.firstOrNull()?.atMillis ?: 0L

    /** Время последней точки, мс; 0 — если точек нет. */
    val endMillis: Long
        // lastOrNull — последний элемент или null для пустого списка.
        get() = points.lastOrNull()?.atMillis ?: 0L

    /** Все точки — для отрисовки графика. */
    val all: List<CurvePoint>
        // Отдаём тот же список: снаружи его нельзя изменить, тип List только для чтения.
        get() = points

    /** Максимум кривой. Для пустой кривой — ноль в нулевой момент. */
    fun peak(): PeakInfo {
        // Пустая кривая — договорённость: пик в нулевой момент с нулевой концентрацией.
        if (points.isEmpty()) return PeakInfo(0L, 0.0)
        // Стартовый кандидат в максимум — первая точка.
        var best = points.first()
        // Перебираем все точки и запоминаем самую высокую концентрацию.
        for (point in points) if (point.bacGPerL > best.bacGPerL) best = point
        // Возвращаем время и значение пика.
        return PeakInfo(best.atMillis, best.bacGPerL)
    }

    /** Значение в момент времени с линейной интерполяцией; вне диапазона — крайние значения. */
    fun valueAt(atMillis: Long): Double {
        // Нет точек — концентрация ноль.
        if (points.isEmpty()) return 0.0
        // Левее начала кривой — первое значение.
        if (atMillis <= points.first().atMillis) return points.first().bacGPerL
        // Правее конца кривой — последнее значение.
        if (atMillis >= points.last().atMillis) return points.last().bacGPerL

        // Бинарный поиск: держим отрезок [lo, hi], внутри которого лежит искомое время.
        var lo = 0
        var hi = points.size - 1
        // Сходимся, пока между lo и hi остаётся хотя бы одна точка.
        while (lo + 1 < hi) {
            // Середина отрезка; целочисленное деление даёт корректный индекс.
            val mid = (lo + hi) / 2
            // Середина не правее искомого времени — сдвигаем левую границу, иначе правую.
            if (points[mid].atMillis <= atMillis) lo = mid else hi = mid
        }
        // Теперь искомое время лежит между points[lo] и points[hi] (или совпадает с точкой).
        val left = points[lo]
        val right = points[hi]
        // Длина отрезка в миллисекундах как Double — чтобы деление было дробным.
        val span = (right.atMillis - left.atMillis).toDouble()
        // Две точки с одинаковым временем — интерполировать нельзя, берём правую.
        if (span <= 0.0) return right.bacGPerL
        // Доля пути от left к right: k ∈ [0, 1].
        val k = (atMillis - left.atMillis) / span
        // Линейная интерполяция концентрации на этом отрезке.
        return left.bacGPerL + (right.bacGPerL - left.bacGPerL) * k
    }

    /**
     * Первая точка после [afterMillis], где концентрация не выше [threshold].
     * Нужна для прогноза «за руль через…» по уже построенной кривой, без повторного интегрирования.
     *
     * @param threshold порог в г/л (он же ‰).
     * @param afterMillis момент, раньше которого искать не нужно — обычно «сейчас».
     */
    fun firstBelow(threshold: Double, afterMillis: Long): Long? {
        // По пустой кривой прогнозировать нечего.
        if (points.isEmpty()) return null
        // Ищем только на спаде: до пика кривая может пересечь порог на подъёме, это не «стало можно».
        val from = maxOf(afterMillis, peak().atMillis)
        // firstOrNull — первая подходящая точка или null, если такой точки нет.
        return points.firstOrNull { it.atMillis > from && it.bacGPerL <= threshold }?.atMillis
    }

    // Компаньон-объект: методы «на весь класс», вызываются как BacCurve.of(...).
    companion object {
        // Фабричный метод: читается как «кривая из списка точек».
        fun of(points: List<CurvePoint>): BacCurve = BacCurve(points)
    }
}
