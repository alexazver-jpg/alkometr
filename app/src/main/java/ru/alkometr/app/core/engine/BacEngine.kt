package ru.alkometr.app.core.engine

import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.ModelParams
import kotlin.math.max
import kotlin.math.min

/** Точка кривой: момент времени (epoch millis) и концентрация в г/л (== ‰). */
data class CurvePoint(val atMillis: Long, val bacGPerL: Double)

/** Информация о пике концентрации. */
data class PeakInfo(val atMillis: Long, val bacGPerL: Double)

/**
 * Чистая математика модели:
 *  - всасывание первого порядка: da/dt = −ka·a, a(t_drink) = D;
 *  - поступление в кровь: input(t) = ka·Σa;
 *  - выведение по Михаэлису–Ментену: dC/dt = input/Vd − Vmax·C/(Km + C).
 *
 * Никакого состояния внутри: всё определяется списком порций, параметрами и временем.
 * Класс не импортирует ничего из android.*, поэтому работает в JVM-юнит-тестах.
 */
object BacEngine {

    /** Шаг интегрирования — 1 минута. */
    const val STEP_MILLIS: Long = 60_000L

    /** Горизонт прогноза от первой порции — 24 часа. */
    const val HORIZON_MILLIS: Long = 24L * 60L * 60L * 1000L

    /**
     * Кривая концентрации на отрезке [fromMillis, toMillis].
     * Возвращает точки с шагом [stepMillis] плюс точное значение на левой границе.
     */
    fun curve(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        toMillis: Long,
        stepMillis: Long = STEP_MILLIS,
    ): List<CurvePoint> {
        require(stepMillis > 0) { "Шаг интегрирования должен быть положительным" }
        if (toMillis < fromMillis) return emptyList()

        val raw = simulate(drinks, p, fromMillis, toMillis, stepMillis)
        if (raw.isEmpty()) return listOf(CurvePoint(fromMillis, 0.0))

        val result = ArrayList<CurvePoint>(raw.size + 1)
        result.add(CurvePoint(fromMillis, valueAt(raw, fromMillis)))
        for (point in raw) {
            if (point.atMillis > fromMillis && point.atMillis <= toMillis) result.add(point)
        }
        return result
    }

    /** Концентрация в конкретный момент, г/л. */
    fun bacAt(drinks: List<Drink>, p: ModelParams, atMillis: Long): Double {
        val raw = simulate(drinks, p, atMillis, atMillis, STEP_MILLIS)
        return raw.lastOrNull()?.bacGPerL ?: 0.0
    }

    /** Пик концентрации на отрезке. */
    fun peak(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        toMillis: Long,
    ): PeakInfo {
        val points = curve(drinks, p, fromMillis, toMillis)
        var best = points.firstOrNull() ?: return PeakInfo(fromMillis, 0.0)
        for (point in points) {
            if (point.bacGPerL > best.bacGPerL) best = point
        }
        return PeakInfo(best.atMillis, best.bacGPerL)
    }

    /**
     * Первый момент после [fromMillis], когда концентрация не выше [threshold] и уже не растёт.
     * null — если за горизонт прогноза этого не произошло.
     */
    fun timeToThreshold(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        threshold: Double,
    ): Long? {
        val horizonEnd = horizonEnd(drinks, fromMillis)
        val points = curve(drinks, p, fromMillis, horizonEnd)
        val peakIndex = points.indices.maxByOrNull { points[it].bacGPerL } ?: return fromMillis
        for (i in peakIndex until points.size) {
            if (points[i].bacGPerL <= threshold) return points[i].atMillis
        }
        return null
    }

    /** Время до практически нулевого уровня (0,001 ‰). */
    fun timeToZero(drinks: List<Drink>, p: ModelParams, fromMillis: Long): Long? =
        timeToThreshold(drinks, p, fromMillis, ZERO_THRESHOLD)

    /** Горизонт прогноза: 24 часа от первой порции (или от текущего момента). */
    fun horizonEnd(drinks: List<Drink>, fromMillis: Long): Long {
        val firstDrink = drinks.minOfOrNull { it.atMillis } ?: return fromMillis + HORIZON_MILLIS
        return max(firstDrink, fromMillis) + HORIZON_MILLIS
    }

    /** Уровень, который считается нулевым. */
    const val ZERO_THRESHOLD = 0.001

    // --- внутреннее ---------------------------------------------------------

    /**
     * Явный метод Эйлера с шагом [stepMillis]. Модель стартует не раньше чем за 24 ч
     * до [fromMillis]: порции старше горизонта уже не влияют на результат.
     */
    private fun simulate(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        toMillis: Long,
        stepMillis: Long,
    ): List<CurvePoint> {
        val firstDrink = drinks.minOfOrNull { it.atMillis }
        val simStart = when {
            firstDrink == null -> fromMillis
            firstDrink < fromMillis -> max(firstDrink, fromMillis - HORIZON_MILLIS)
            else -> fromMillis
        }
        if (toMillis < simStart) return listOf(CurvePoint(toMillis, 0.0))

        val validDrinks = drinks.filter { it.volumeMl > 0 && it.abvPercent > 0 }
        val grams = DoubleArray(validDrinks.size) { validDrinks[it].ethanolGrams }
        val pending = DoubleArray(validDrinks.size)
        val added = BooleanArray(validDrinks.size)

        var concentration = 0.0
        var time = simStart
        val points = ArrayList<CurvePoint>()
        points.add(CurvePoint(simStart, 0.0))

        while (time < toMillis) {
            val dtMillis = min(stepMillis, toMillis - time)
            val dtHours = dtMillis / 3_600_000.0

            for (i in validDrinks.indices) {
                if (!added[i] && validDrinks[i].atMillis <= time) {
                    added[i] = true
                    pending[i] = grams[i]
                }
            }

            var inputGramsPerHour = 0.0
            for (i in pending.indices) inputGramsPerHour += pending[i]
            inputGramsPerHour *= p.kaPerHour

            val elimination = if (concentration > 0.0) {
                p.vmaxGPerLH * concentration / (p.kmGPerL + concentration)
            } else {
                0.0
            }

            concentration = max(0.0, concentration + (inputGramsPerHour / p.vdLiters - elimination) * dtHours)
            for (i in pending.indices) {
                pending[i] = max(0.0, pending[i] - p.kaPerHour * pending[i] * dtHours)
            }

            time += dtMillis
            points.add(CurvePoint(time, concentration))
        }
        return points
    }

    /** Линейная интерполяция по точкам кривой. */
    private fun valueAt(points: List<CurvePoint>, atMillis: Long): Double {
        if (points.isEmpty()) return 0.0
        if (atMillis <= points.first().atMillis) return points.first().bacGPerL
        if (atMillis >= points.last().atMillis) return points.last().bacGPerL
        for (i in 1 until points.size) {
            val right = points[i]
            if (right.atMillis >= atMillis) {
                val left = points[i - 1]
                val span = (right.atMillis - left.atMillis).toDouble()
                if (span <= 0.0) return right.bacGPerL
                val k = (atMillis - left.atMillis) / span
                return left.bacGPerL + (right.bacGPerL - left.bacGPerL) * k
            }
        }
        return points.last().bacGPerL
    }
}
