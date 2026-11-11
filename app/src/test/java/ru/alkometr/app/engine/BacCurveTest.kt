package ru.alkometr.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.alkometr.app.core.engine.BacCurve
import ru.alkometr.app.core.engine.CurvePoint

/**
 * Проверки готовой кривой: интерполяция, пик, поиск пересечения порога.
 *
 * Отличие от [BacEngineTest]: там тестируется расчёт «из порций», а здесь — работа с уже
 * посчитанным набором точек. `BacCurve` — чистая функция над списком точек, поэтому тесты
 * могут задать кривую вручную и точно знать, где у неё пик и где пересекается порог.
 */
class BacCurveTest {

    // Начало отсчёта времени — константа, иначе тест зависел бы от даты запуска.
    // Значение то же, что в тестах движка, чтобы файлы читались одинаково.
    private val t0 = 1_700_000_000_000L

    // Минута в миллисекундах: моменты точек записываются как «t0 + 10 * minute».
    private val minute = 60_000L

    // Тестовая кривая из шести точек: рост до 0,4 ‰ к 20-й минуте, затем спад до нуля к 50-й.
    // Простая форма выбрана специально: ожидаемые ответы можно посчитать в голове, поэтому
    // тест проверяет логику `BacCurve`, а не арифметику модели.
    private fun curve() = BacCurve.of(
        listOf(
            CurvePoint(t0, 0.0),
            CurvePoint(t0 + 10 * minute, 0.2),
            CurvePoint(t0 + 20 * minute, 0.4),
            CurvePoint(t0 + 30 * minute, 0.3),
            CurvePoint(t0 + 40 * minute, 0.1),
            CurvePoint(t0 + 50 * minute, 0.0),
        ),
    )

    // valueAt между двумя соседними точками — это линейная интерполяция: середина отрезка
    // 0,2 → 0,4 даёт 0,3, а середина 0,4 → 0,3 даёт 0,35. Именно так рисуется график
    // между отсчётами, поэтому проверяем обе ветви — на подъёме и на спаде.
    @Test
    fun valueAt_interpolatesLinearly() {
        assertEquals(0.3, curve().valueAt(t0 + 15 * minute), 1e-9)
        assertEquals(0.35, curve().valueAt(t0 + 25 * minute), 1e-9)
    }

    // За пределами набора точек функция обязана «прилипать» к краям, а не падать
    // и не экстраполировать в минус: до начала и после конца сессии уровень равен
    // крайнему известному значению (здесь оба края — ноль).
    @Test
    fun valueAt_clampsOutsideRange() {
        assertEquals(0.0, curve().valueAt(t0 - minute), 1e-9)
        assertEquals(0.0, curve().valueAt(t0 + 999 * minute), 1e-9)
    }

    // peak() должен вернуть не только высоту, но и момент: приложение показывает
    // «максимум 0,4 ‰ в 20:20». Поэтому проверяются оба поля — значение и время.
    @Test
    fun peak_returnsMaximum() {
        val peak = curve().peak()
        assertEquals(0.4, peak.bacGPerL, 1e-9)
        assertEquals(t0 + 20 * minute, peak.atMillis)
    }

    // firstBelow ищет первое пересечение порога после заданного момента. Порог 0,35 ‰
    // достигается на спуске между 20-й и 30-й минутой, то есть попадает в точку 30 минут:
    // именно её и должен вернуть поиск (реализация работает по отсчётам кривой).
    @Test
    fun firstBelow_findsFirstCrossingAfterTime() {
        val crossing = curve().firstBelow(0.35, t0 + 5 * minute)
        assertEquals(t0 + 30 * minute, crossing)
    }

    // Отрицательный порог не достигается никогда: кривая не уходит ниже нуля.
    // Здесь важен именно крайний случай — функция должна вернуть null (события нет),
    // а не выдуманное время и не бросить исключение.
    @Test
    fun firstBelow_returnsNullWhenNeverCrosses() {
        assertNull(curve().firstBelow(-0.5, t0))
    }

    // Пустая кривая — реальный случай: сессия ещё не начата. Такие объекты не должны
    // ронять экран, поэтому проверяем, что все методы дают безопасные значения:
    // isEmpty = true, уровень 0,0 и пик с нулевой высотой вместо исключения.
    @Test
    fun emptyCurve_isSafe() {
        val empty = BacCurve.of(emptyList())
        assertTrue(empty.isEmpty)
        assertEquals(0.0, empty.valueAt(t0), 1e-9)
        assertEquals(0.0, empty.peak().bacGPerL, 1e-9)
    }
}
