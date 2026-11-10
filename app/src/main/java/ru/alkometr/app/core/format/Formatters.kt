package ru.alkometr.app.core.format

import ru.alkometr.app.core.model.Units
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Форматирование чисел, длительностей и времени.
 *
 * Подписи единиц длительности («ч», «мин») живут здесь, а не в strings.xml,
 * потому что эти же строки проверяются JVM-юнит-тестами без контекста Android.
 * Все пользовательские тексты экранов — в strings.xml.
 */
object Formatters {

    private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
    private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

    /** Число в выбранных единицах с запятой как десятичным разделителем. */
    fun formatValue(bacGPerL: Double, units: Units): String {
        val value = units.fromBac(bacGPerL)
        val text = String.format(Locale.US, "%.${units.decimals}f", value)
        return text.replace('.', ',')
    }

    /** «0,42 ‰» */
    fun formatBac(bacGPerL: Double, units: Units): String =
        "${formatValue(bacGPerL, units)} ${units.symbol}"

    /** «2 ч 14 мин», «14 мин», «1 ч», «меньше минуты». */
    fun formatDuration(millis: Long): String {
        if (millis < 60_000L) return "меньше минуты"
        val totalMinutes = ((millis + 59_999L) / 60_000L).toInt()
        if (totalMinutes <= 0) return "меньше минуты"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
            hours > 0 -> "$hours ч"
            else -> "$minutes мин"
        }
    }

    /** Давность порции: «−1 ч 20 мин» для прошлого, «+15 мин» для будущего. */
    fun formatAge(atMillis: Long, nowMillis: Long): String {
        val diff = nowMillis - atMillis
        if (diff in -60_000L..60_000L) return "только что"
        if (diff >= 0) return "−${formatDuration(diff)}"
        return "+${formatDuration(-diff)}"
    }

    /** Время суток «23:40». */
    fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clockFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Дата «10 нояб.». */
    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Дата и время «10 нояб., 23:40». */
    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Часы и минуты для полей ввода: «21:05». */
    fun formatTimeInput(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clockFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Объём без лишних нулей: «50», «150», «12,5». */
    fun formatVolume(volumeMl: Double): String {
        val rounded = Math.round(volumeMl * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded).replace('.', ',')
        }
    }
}
