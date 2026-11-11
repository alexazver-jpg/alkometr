package ru.alkometr.app.core.engine

/**
 * Готовая кривая для отрисовки: точки + быстрый поиск значения по времени.
 * Используется и шкалой-индикатором, и шкалой времени.
 */
class BacCurve(private val points: List<CurvePoint>) {

    val isEmpty: Boolean
        get() = points.isEmpty()

    val startMillis: Long
        get() = points.firstOrNull()?.atMillis ?: 0L

    val endMillis: Long
        get() = points.lastOrNull()?.atMillis ?: 0L

    val all: List<CurvePoint>
        get() = points

    /** Максимум кривой. Для пустой кривой — ноль в нулевой момент. */
    fun peak(): PeakInfo {
        if (points.isEmpty()) return PeakInfo(0L, 0.0)
        var best = points.first()
        for (point in points) if (point.bacGPerL > best.bacGPerL) best = point
        return PeakInfo(best.atMillis, best.bacGPerL)
    }

    /** Значение в момент времени с линейной интерполяцией; вне диапазона — крайние значения. */
    fun valueAt(atMillis: Long): Double {
        if (points.isEmpty()) return 0.0
        if (atMillis <= points.first().atMillis) return points.first().bacGPerL
        if (atMillis >= points.last().atMillis) return points.last().bacGPerL

        var lo = 0
        var hi = points.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (points[mid].atMillis <= atMillis) lo = mid else hi = mid
        }
        val left = points[lo]
        val right = points[hi]
        val span = (right.atMillis - left.atMillis).toDouble()
        if (span <= 0.0) return right.bacGPerL
        val k = (atMillis - left.atMillis) / span
        return left.bacGPerL + (right.bacGPerL - left.bacGPerL) * k
    }

    /**
     * Первая точка после [afterMillis], где концентрация не выше [threshold].
     * Нужна для прогноза «за руль через…» по уже построенной кривой, без повторного интегрирования.
     */
    fun firstBelow(threshold: Double, afterMillis: Long): Long? {
        if (points.isEmpty()) return null
        // Ищем только на спаде: до пика кривая может пересечь порог на подъёме, это не «стало можно».
        val from = maxOf(afterMillis, peak().atMillis)
        return points.firstOrNull { it.atMillis > from && it.bacGPerL <= threshold }?.atMillis
    }

    companion object {
        fun of(points: List<CurvePoint>): BacCurve = BacCurve(points)
    }
}
