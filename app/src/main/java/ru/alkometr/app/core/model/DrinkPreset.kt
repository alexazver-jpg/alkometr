package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Пресет напитка для кнопки на главном экране.
 * Название не хранится: подпись собирается из вида сосуда, объёма и крепости.
 */
@Serializable
data class DrinkPreset(
    val id: String,
    val kind: DrinkKind,
    val volumeMl: Double,
    val abvPercent: Double,
    /** Необязательное своё название; если пусто — на кнопке показывается вид сосуда. */
    val name: String? = null,
) {
    companion object {
        fun defaults(): List<DrinkPreset> = listOf(
            DrinkPreset("preset_shot", DrinkKind.SHOT, 50.0, 40.0),
            DrinkPreset("preset_wine", DrinkKind.WINE_GLASS, 150.0, 12.0),
            DrinkPreset("preset_mug", DrinkKind.MUG, 500.0, 5.0),
        )
    }
}

/** Пресеты юридического порога для вождения. */
enum class ThresholdPreset(val permille: Double) {
    RUSSIA(0.3),
    EUROPE(0.5),
    USA(0.8),
    ZERO(0.0),
}