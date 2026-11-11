package ru.alkometr.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.alkometr.app.core.engine.BacCurve
import ru.alkometr.app.core.engine.CurvePoint

/** Проверки готовой кривой: интерполяция, пик, поиск пересечения порога. */
class BacCurveTest {

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L

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

    @Test
    fun valueAt_interpolatesLinearly() {
        assertEquals(0.3, curve().valueAt(t0 + 15 * minute), 1e-9)
        assertEquals(0.35, curve().valueAt(t0 + 25 * minute), 1e-9)
    }

    @Test
    fun valueAt_clampsOutsideRange() {
        assertEquals(0.0, curve().valueAt(t0 - minute), 1e-9)
        assertEquals(0.0, curve().valueAt(t0 + 999 * minute), 1e-9)
    }

    @Test
    fun peak_returnsMaximum() {
        val peak = curve().peak()
        assertEquals(0.4, peak.bacGPerL, 1e-9)
        assertEquals(t0 + 20 * minute, peak.atMillis)
    }

    @Test
    fun firstBelow_findsFirstCrossingAfterTime() {
        val crossing = curve().firstBelow(0.35, t0 + 5 * minute)
        assertEquals(t0 + 30 * minute, crossing)
    }

    @Test
    fun firstBelow_returnsNullWhenNeverCrosses() {
        assertNull(curve().firstBelow(-0.5, t0))
    }

    @Test
    fun emptyCurve_isSafe() {
        val empty = BacCurve.of(emptyList())
        assertTrue(empty.isEmpty)
        assertEquals(0.0, empty.valueAt(t0), 1e-9)
        assertEquals(0.0, empty.peak().bacGPerL, 1e-9)
    }
}
