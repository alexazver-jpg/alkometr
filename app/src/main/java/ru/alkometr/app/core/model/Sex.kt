package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Пол определяет коэффициент распределения r в формуле Видмарка.
 */
@Serializable
enum class Sex(val r: Double) {
    MALE(0.71),
    FEMALE(0.58),
    UNSPECIFIED(0.65),
}