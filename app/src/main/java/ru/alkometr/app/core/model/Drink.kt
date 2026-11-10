package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Одна выпитая порция. Время хранится в epoch-миллисекундах UTC,
 * отображение — всегда в локальном часовом поясе.
 */
@Serializable
data class Drink(
    val id: String,
    val atMillis: Long,
    val volumeMl: Double,
    val abvPercent: Double,
    val kind: DrinkKind,
) {
    /** Масса чистого этанола в порции, граммы. */
    val ethanolGrams: Double
        get() = volumeMl * (abvPercent / 100.0) * ETHANOL_DENSITY

    companion object {
        /** Плотность этанола, г/мл. */
        const val ETHANOL_DENSITY = 0.789
    }
}
