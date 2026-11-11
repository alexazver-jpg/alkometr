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
 *
 * Зачем отдельный объект: один и тот же формат («0,42 ‰», «2 ч 14 мин») нужен и экрану,
 * и экспорту, и тестам. Правило хранится в одном месте, поэтому число не может выглядеть
 * по-разному в разных частях приложения.
 * `object` — синглтон Kotlin: экземпляр один на весь процесс, создаётся при первом обращении.
 */
object Formatters {

    // DateTimeFormatter неизменяемый и потокобезопасный, поэтому создаём его один раз на процесс,
    // а не внутри каждого вызова: создание форматтера — относительно дорогая операция.
    // Locale.ROOT — «нейтральная» локаль: формат часов не зависит от языка телефона.
    private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    // «d MMM» — день и сокращённый месяц. Локаль ru задана жёстко, чтобы подпись
    // не стала внезапно английской на телефоне с другим языком системы.
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
    // Готовая комбинация «дата + время»: один формат на все места, где нужны оба значения,
    // чтобы они нигде не разошлись по стилю.
    private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

    /** Число в выбранных единицах с запятой как десятичным разделителем. */
    fun formatValue(bacGPerL: Double, units: Units): String {
        // Сначала переводим «внутренние» единицы (граммы на литр) в те, что выбрал пользователь
        // (‰, мг/л и т. д.). Внутри приложения считаем всегда в одной системе — так меньше ошибок.
        val value = units.fromBac(bacGPerL)
        // String.format с локалью Locale.US даёт точку как разделитель дроби, а число знаков
        // после запятой берём из units.decimals: одна строка кода обслуживает все единицы.
        val text = String.format(Locale.US, "%.${units.decimals}f", value)
        // Точку меняем на запятую вручную: результат получается одинаковым на любом устройстве
        // и не зависит от системных настроек (в русском дробная часть пишется через запятую).
        return text.replace('.', ',')
    }

    /** «0,42 ‰» */
    fun formatBac(bacGPerL: Double, units: Units): String =
        // Склеиваем уже отформатированное число с символом единицы: сам символ знает Units,
        // поэтому форматтер не хранит таблицу «‰ / мг/л» у себя.
        "${formatValue(bacGPerL, units)} ${units.symbol}"

    /** «2 ч 14 мин», «14 мин», «1 ч», «меньше минуты». */
    fun formatDuration(millis: Long): String {
        // Отдельная ветка для очень малых значений: показывать «0 мин» бессмысленно,
        // честнее сказать «меньше минуты».
        if (millis < 60_000L) return "меньше минуты"
        // Делим с округлением вверх: 61 секунда должна показаться как «2 мин», а не «1 мин»,
        // иначе пользователь видит «1 мин», хотя время ещё не вышло. 59_999 добавляется,
        // чтобы целое деление сработало как округление вверх.
        val totalMinutes = ((millis + 59_999L) / 60_000L).toInt()
        // Защита от отрицательных значений (например, при ошибке в расчётах): не показываем «-3 мин».
        if (totalMinutes <= 0) return "меньше минуты"
        // Целая часть от деления на 60 — часы.
        val hours = totalMinutes / 60
        // Остаток от деления на 60 — минуты внутри последнего часа.
        val minutes = totalMinutes % 60
        // when без аргумента — цепочка условий: берём первую подходящую ветку.
        return when {
            // Самый информативный случай: есть и часы, и минуты.
            hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
            // Ровно целое число часов: «2 ч» читается лучше, чем «2 ч 0 мин».
            hours > 0 -> "$hours ч"
            // Осталось только меньше часа — показываем минуты.
            else -> "$minutes мин"
        }
    }

    /** Давность порции: «−1 ч 20 мин» для прошлого, «+15 мин» для будущего. */
    fun formatAge(atMillis: Long, nowMillis: Long): String {
        // Разница «сейчас минус момент порции»: положительная — порция в прошлом,
        // отрицательная — время порции ещё не наступило (её можно поставить заранее).
        val diff = nowMillis - atMillis
        // Допуск в минуту в обе стороны: «сейчас» и «минуту назад» нет смысла различать.
        if (diff in -60_000L..60_000L) return "только что"
        // Прошлое показываем со знаком минус — так принято в подписях «сколько прошло».
        if (diff >= 0) return "−${formatDuration(diff)}"
        // Будущее — со знаком плюс; минус перед diff убирает отрицательное значение.
        return "+${formatDuration(-diff)}"
    }

    /** Время суток «23:40». */
    fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        // Instant — момент на временной шкале без часового пояса; atZone добавляет пояс,
        // иначе «23:40» могло бы означать разное время в разных странах.
        // Пояс — параметр со значением по умолчанию: в тестах его подменяют на фиксированный.
        clockFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Дата «10 нояб.». */
    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        // Тот же перевод миллисекунд в дату с учётом пояса, но формат только с днём и месяцем.
        dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Дата и время «10 нояб., 23:40». */
    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        // Нужен там, где важно и число, и время: например, отметка создания резервной копии.
        dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Часы и минуты для полей ввода: «21:05». */
    fun formatTimeInput(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        // Формат тот же, что у formatClock, но метод назван по назначению: поле ввода
        // должно получать «21:05», и намерение кода читается сразу.
        clockFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Объём без лишних нулей: «50», «150», «12,5». */
    fun formatVolume(volumeMl: Double): String {
        // Округляем до десятых: миллилитры точнее десятой доли в быту не нужны,
        // и это убирает «хвосты» вычислений вроде 49.999999.
        val rounded = Math.round(volumeMl * 10.0) / 10.0
        // Проверяем, целое ли число. Если да — печатаем без дробной части,
        // иначе пользователь увидел бы «50,0» вместо аккуратного «50».
        return if (rounded % 1.0 == 0.0) {
            // toInt() безопасен: объёмы напитков далеки от предела Int, а дробная часть уже нулевая.
            rounded.toInt().toString()
        } else {
            // Дробный случай: ровно один знак после запятой, и снова меняем точку на запятую.
            String.format(Locale.US, "%.1f", rounded).replace('.', ',')
        }
    }
}
