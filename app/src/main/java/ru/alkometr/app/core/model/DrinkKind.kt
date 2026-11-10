package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Вид сосуда. Влияет только на иконку и подпись, на расчёт — нет.
 */
@Serializable
enum class DrinkKind {
    SHOT,
    WINE_GLASS,
    MUG,
    CUSTOM,
}
