package ru.alkometr.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.alkometr.app.core.engine.BacEngine
import ru.alkometr.app.core.engine.TimeToDrive
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.IntoxicationLevel
import ru.alkometr.app.core.model.ModelParams
import ru.alkometr.app.core.model.Sex
import ru.alkometr.app.core.model.Units

/**
 * Контрольные значения взяты из эталонной реализации модели (docs/_check_model.py).
 * Допуски по ТЗ: ±0,02 ‰ и ±10 мин. Подгонять константы модели под тесты запрещено.
 */
class BacEngineTest {

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L

    private fun params(
        weightKg: Double,
        sex: Sex,
        ka: Double = 1.5,
        vmax: Double = 0.15,
    ) = ModelParams(weightKg = weightKg, r = sex.r, kaPerHour = ka, vmaxGPerLH = vmax)

    private fun drink(
        minutesFromStart: Long,
        volumeMl: Double = 50.0,
        abv: Double = 40.0,
        kind: DrinkKind = DrinkKind.SHOT,
        id: String = "d$minutesFromStart-$volumeMl-$abv",
    ) = Drink(
        id = id,
        atMillis = t0 + minutesFromStart * minute,
        volumeMl = volumeMl,
        abvPercent = abv,
        kind = kind,
    )

    private fun bac(drinks: List<Drink>, p: ModelParams, minutes: Long): Double =
        BacEngine.bacAt(drinks, p, t0 + minutes * minute)

    private fun assertBac(expected: Double, actual: Double, tolerance: Double = 0.02) {
        assertEquals("BAC отличается больше допуска", expected, actual, tolerance)
    }

    private fun assertMinutes(expected: Double, actualMillis: Long?, toleranceMinutes: Double = 10.0) {
        assertNotNull("Ожидалось время, получено null", actualMillis)
        val actualMinutes = (actualMillis!! - t0) / minute.toDouble()
        assertEquals("Время отличается больше допуска", expected, actualMinutes, toleranceMinutes)
    }

    @Test
    fun emptySession_returnsZero() {
        val p = params(80.0, Sex.MALE)
        assertBac(0.0, bac(emptyList(), p, 0))
        assertBac(0.0, bac(emptyList(), p, 120))
        val points = BacEngine.curve(emptyList(), p, t0, t0 + 60 * minute)
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.bacGPerL == 0.0 })
    }

    @Test
    fun singleShot_matchesGolden() {
        // A. Мужчина 80 кг, 1 стопка 50 мл 40 % (15,8 г) в t=0
        val p = params(80.0, Sex.MALE)
        val drinks = listOf(drink(0))

        assertBac(0.097, bac(drinks, p, 30))
        assertBac(0.102, bac(drinks, p, 60))

        val peak = BacEngine.peak(drinks, p, t0, t0 + 6 * 60 * minute)
        assertBac(0.106, peak.bacGPerL)
        assertMinutes(47.0, peak.atMillis)

        assertMinutes(2.98 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    @Test
    fun beerFemale_matchesGolden() {
        // B. Женщина 70 кг, 1 кружка 500 мл 5 % (19,7 г)
        val p = params(70.0, Sex.FEMALE)
        val drinks = listOf(drink(0, volumeMl = 500.0, abv = 5.0, kind = DrinkKind.MUG))

        assertBac(0.252, bac(drinks, p, 60))
        assertBac(0.195, bac(drinks, p, 120))

        val peak = BacEngine.peak(drinks, p, t0, t0 + 8 * 60 * minute)
        assertBac(0.252, peak.bacGPerL)
        assertMinutes(65.0, peak.atMillis)

        assertMinutes(4.18 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    @Test
    fun threeShots_matchesGolden() {
        // C. Мужчина 90 кг, 3 стопки 50 мл 40 % в 0, 15, 30 мин
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))

        assertBac(0.374, bac(drinks, p, 60))

        val peak = BacEngine.peak(drinks, p, t0, t0 + 10 * 60 * minute)
        assertBac(0.434, peak.bacGPerL)
        assertMinutes(97.0, peak.atMillis)

        assertMinutes(3.17 * 60, BacEngine.timeToThreshold(drinks, p, t0, 0.3))
        assertMinutes(5.97 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    @Test
    fun twoWineGlasses_matchesGolden() {
        // D. Женщина 60 кг, 2 бокала 150 мл 12 % в 0 и 20 мин
        val p = params(60.0, Sex.FEMALE)
        val drinks = listOf(
            drink(0, volumeMl = 150.0, abv = 12.0, kind = DrinkKind.WINE_GLASS),
            drink(20, volumeMl = 150.0, abv = 12.0, kind = DrinkKind.WINE_GLASS),
        )

        assertBac(0.454, bac(drinks, p, 60))

        val peak = BacEngine.peak(drinks, p, t0, t0 + 10 * 60 * minute)
        assertBac(0.506, peak.bacGPerL)
        assertMinutes(95.0, peak.atMillis)

        assertMinutes(6.43 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    @Test
    fun beerPlusShot_ka2_matchesGolden() {
        // E. Мужчина 80 кг, 500 мл 5 % в 0 + 50 мл 40 % на 10-й мин, ka = 2,0
        val p = params(80.0, Sex.MALE, ka = 2.0)
        val drinks = listOf(
            drink(0, volumeMl = 500.0, abv = 5.0, kind = DrinkKind.MUG),
            drink(10),
        )

        assertBac(0.399, bac(drinks, p, 60))

        val peak = BacEngine.peak(drinks, p, t0, t0 + 8 * 60 * minute)
        assertBac(0.402, peak.bacGPerL)
        assertMinutes(68.0, peak.atMillis)
    }

    @Test
    fun curveNeverNegative() {
        val p = params(80.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(0, volumeMl = 500.0, abv = 5.0, kind = DrinkKind.MUG))
        val points = BacEngine.curve(drinks, p, t0, t0 + 24 * 60 * minute)
        assertTrue(points.all { it.bacGPerL >= 0.0 })
    }

    @Test
    fun monotonicDecreaseAfterPeak() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        val points = BacEngine.curve(drinks, p, t0, t0 + 12 * 60 * minute)
        val peakIndex = points.indices.maxByOrNull { points[it].bacGPerL }!!
        for (i in peakIndex + 1 until points.size) {
            assertTrue(
                "После пика кривая должна убывать (${points[i - 1].bacGPerL} → ${points[i].bacGPerL})",
                points[i].bacGPerL <= points[i - 1].bacGPerL + 1e-9,
            )
        }
    }

    @Test
    fun eliminationSlope_atHighBac_isAbout015PerHour() {
        // Большая доза натощак (ka = 2,0): через 4 часа всасывание закончено,
        // концентрация 1,5–2,5 ‰ — здесь работает нулевой порядок выведения.
        val p = params(80.0, Sex.MALE, ka = 2.0)
        val drinks = listOf(drink(0, volumeMl = 500.0, abv = 40.0, kind = DrinkKind.CUSTOM))
        val startMinutes = 4 * 60L
        val points = BacEngine.curve(drinks, p, t0, t0 + 12 * 60 * minute)

        val start = points.first { it.atMillis == t0 + startMinutes * minute }
        assertTrue("Ожидался уровень 1,5–2,5 ‰, получено ${start.bacGPerL}", start.bacGPerL in 1.5..2.5)

        val afterHour = points.first { it.atMillis == start.atMillis + 60 * minute }
        val slope = start.bacGPerL - afterHour.bacGPerL
        assertEquals("Скорость выведения должна быть около 0,15 ‰/ч", 0.15, slope, 0.02)
    }

    @Test
    fun deterministic_sameInputSameOutput() {
        val p = params(70.0, Sex.FEMALE)
        val drinks = listOf(drink(0, 500.0, 5.0, DrinkKind.MUG), drink(20, 150.0, 12.0, DrinkKind.WINE_GLASS))
        val first = BacEngine.curve(drinks, p, t0, t0 + 6 * 60 * minute)
        val second = BacEngine.curve(drinks, p, t0, t0 + 6 * 60 * minute)
        assertEquals(first, second)
    }

    @Test
    fun orderOfDrinksDoesNotMatter() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        val shuffled = listOf(drinks[2], drinks[0], drinks[1])
        val a = BacEngine.curve(drinks, p, t0, t0 + 8 * 60 * minute)
        val b = BacEngine.curve(shuffled, p, t0, t0 + 8 * 60 * minute)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].atMillis, b[i].atMillis)
            assertEquals(a[i].bacGPerL, b[i].bacGPerL, 1e-9)
        }
    }

    @Test
    fun timeToThreshold_correct() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))

        // До порога 0,3 ‰ — около 3,17 ч от первой порции
        assertMinutes(3.17 * 60, BacEngine.timeToThreshold(drinks, p, t0, 0.3))

        // Если порог уже не превышен — возвращается текущий момент
        val late = t0 + 12 * 60 * minute
        assertEquals(late, BacEngine.timeToThreshold(drinks, p, late, 0.3))
    }

    @Test
    fun deleteDrink_recomputesHistory() {
        val p = params(90.0, Sex.MALE)
        val all = listOf(drink(0), drink(15), drink(30))
        val less = listOf(drink(0), drink(15))

        val before = BacEngine.bacAt(all, p, t0 + 60 * minute)
        val after = BacEngine.bacAt(less, p, t0 + 60 * minute)
        assertTrue("После удаления порции уровень должен упасть", after < before)

        // Прошлое тоже пересчитывается: значение после удалённой порции меньше
        val beforeAt40 = BacEngine.bacAt(all, p, t0 + 40 * minute)
        val afterAt40 = BacEngine.bacAt(less, p, t0 + 40 * minute)
        assertTrue(afterAt40 < beforeAt40)
    }

    @Test
    fun addDrink_raisesLevelImmediately() {
        val p = params(80.0, Sex.MALE)
        val now = t0 + 30 * minute
        val before = BacEngine.bacAt(listOf(drink(0)), p, now)
        // Порция, добавленная «сейчас», начинает всасываться с текущей минуты,
        // поэтому рост проверяем через минуту после добавления.
        val after = BacEngine.bacAt(listOf(drink(0), drink(30)), p, now + minute)
        assertTrue("Добавленная порция должна поднять уровень", after > before)
    }

    @Test
    fun levelBoundaries_exact() {
        assertEquals(IntoxicationLevel.SOBER, IntoxicationLevel.forBac(0.0))
        assertEquals(IntoxicationLevel.SOBER, IntoxicationLevel.forBac(0.199))
        assertEquals(IntoxicationLevel.MILD, IntoxicationLevel.forBac(0.20))
        assertEquals(IntoxicationLevel.MILD, IntoxicationLevel.forBac(0.499))
        assertEquals(IntoxicationLevel.MODERATE, IntoxicationLevel.forBac(0.50))
        assertEquals(IntoxicationLevel.MODERATE, IntoxicationLevel.forBac(1.499))
        assertEquals(IntoxicationLevel.STRONG, IntoxicationLevel.forBac(1.50))
        assertEquals(IntoxicationLevel.STRONG, IntoxicationLevel.forBac(2.499))
        assertEquals(IntoxicationLevel.DANGEROUS, IntoxicationLevel.forBac(2.50))
        assertEquals(IntoxicationLevel.DANGEROUS, IntoxicationLevel.forBac(5.0))
    }

    @Test
    fun unitsConversion_roundTrip() {
        val bac = 0.42
        Units.entries.forEach { units ->
            val shown = units.fromBac(bac)
            assertEquals(bac, units.toBac(shown), 1e-9)
        }
        assertEquals(0.42, Units.PERMILLE.fromBac(0.42), 1e-9)
        assertEquals(0.042, Units.PERCENT.fromBac(0.42), 1e-9)
        assertEquals(42.0, Units.MG_PER_100ML.fromBac(0.42), 1e-9)
        assertEquals(420.0, Units.MG_PER_LITER.fromBac(0.42), 1e-9)
    }

    @Test
    fun timeFormatter_ru() {
        assertEquals("2 ч 14 мин", Formatters.formatDuration((2 * 60 + 14) * minute))
        assertEquals("14 мин", Formatters.formatDuration(14 * minute))
        assertEquals("1 ч", Formatters.formatDuration(60 * minute))
        assertEquals("меньше минуты", Formatters.formatDuration(30_000L))
        assertEquals("−1 ч 20 мин", Formatters.formatAge(t0, t0 + 80 * minute))
        assertEquals("+15 мин", Formatters.formatAge(t0 + 15 * minute, t0))
    }

    @Test
    fun fortyEightHoursLater_isZero() {
        val p = params(80.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        val value = BacEngine.bacAt(drinks, p, t0 + 48 * 60 * minute)
        assertEquals(0.0, value, 1e-6)
    }

    @Test
    fun forecast_thresholdLogic() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        val now = t0 + 60 * minute

        val forecast = TimeToDrive.forecast(drinks, p, now, 0.3)
        assertTrue(!forecast.canDriveNow)
        assertTrue(forecast.remainingMillis != null && forecast.remainingMillis!! > 0)

        val sober = TimeToDrive.forecast(emptyList(), p, now, 0.3)
        assertTrue(sober.canDriveNow)
        assertEquals(0L, sober.remainingMillis)
    }
}
