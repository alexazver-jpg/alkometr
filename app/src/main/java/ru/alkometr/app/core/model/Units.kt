package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Единицы отображения концентрации. Внутри всё считается в г/л (это же и промилле).
 *
 * ‰         — 1 г/л = 1 ‰;
 * % BAC     — объёмные проценты: 1 г/л = 0,1 %;
 * мг/100 мл — 1 г/л = 100 мг/100 мл;
 * мг/л      — концентрация в крови: 1 г/л = 1000 мг/л.
 */
@Serializable
enum class Units(
    val symbol: String,
    val factorFromGramPerLiter: Double,
    val decimals: Int,
) {
    PERMILLE("‰", 1.0, 2),
    PERCENT("%", 0.1, 2),
    MG_PER_100ML("мг/100 мл", 100.0, 0),
    MG_PER_LITER("мг/л", 1000.0, 0);

    fun fromBac(bacGPerL: Double): Double = bacGPerL * factorFromGramPerLiter

    fun toBac(value: Double): Double = value / factorFromGramPerLiter

    companion object {
        /** Порог по умолчанию — Россия, 0,3 ‰. */
        const val DEFAULT_THRESHOLD_PERMILLE = 0.3
    }
}