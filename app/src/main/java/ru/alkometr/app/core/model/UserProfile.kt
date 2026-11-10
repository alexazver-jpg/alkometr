package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Профиль пользователя. Обязательны только вес и пол,
 * остальное — необязательные уточнения.
 */
@Serializable
data class UserProfile(
    val weightKg: Double = 80.0,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Int? = null,
    val ageYears: Int? = null,
) {
    companion object {
        val WEIGHT_RANGE = 30.0..200.0
        val HEIGHT_RANGE = 120..220
        val AGE_RANGE = 18..100
    }
}