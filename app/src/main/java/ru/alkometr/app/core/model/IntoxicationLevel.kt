package ru.alkometr.app.core.model

/**
 * Пять степеней опьянения. Границы — константы в одном месте.
 *
 * Важно: степень опьянения и юридический порог для вождения — разные вещи.
 * Статус «можно за руль» считается по порогу из настроек, а не по номеру степени.
 */
enum class IntoxicationLevel(
    val minBac: Double,
    val titleKey: String,
    val colorArgb: Long,
    val iconKey: String,
) {
    SOBER(0.0, "sober", 0xFF2E7D32, "ic_level_1"),
    MILD(0.20, "mild", 0xFF9CCC65, "ic_level_2"),
    MODERATE(0.50, "moderate", 0xFFFBC02D, "ic_level_3"),
    STRONG(1.50, "strong", 0xFFEF6C00, "ic_level_4"),
    DANGEROUS(2.50, "dangerous", 0xFFC62828, "ic_level_5");

    /** Верхняя граница диапазона (исключительно); у последней степени — бесконечность. */
    val maxBac: Double
        get() = when (this) {
            SOBER -> MILD.minBac
            MILD -> MODERATE.minBac
            MODERATE -> STRONG.minBac
            STRONG -> DANGEROUS.minBac
            DANGEROUS -> Double.POSITIVE_INFINITY
        }

    companion object {
        /** Верхняя граница шкалы-индикатора, ‰. */
        const val SCALE_MAX = 4.0

        fun forBac(bac: Double): IntoxicationLevel {
            val value = if (bac.isNaN()) 0.0 else bac
            return entries.last { value >= it.minBac }
        }
    }
}
