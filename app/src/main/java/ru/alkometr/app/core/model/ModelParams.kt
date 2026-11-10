package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/** Насколько плотно человек поел перед первой порцией — влияет на скорость всасывания. */
@Serializable
enum class FoodIntake(val kaPerHour: Double) {
    EMPTY_STOMACH(2.0),
    NORMAL(1.5),
    HEAVY(0.8),
}

/** Привычная частота употребления — влияет на скорость выведения. */
@Serializable
enum class Metabolism(val vmaxGPerLH: Double) {
    SLOW(0.12),
    AVERAGE(0.15),
    FAST(0.18),
}

/**
 * Параметры модели для конкретного расчёта.
 * Значения по умолчанию — физиологически обоснованные средние, не подогнанные под тесты.
 */
data class ModelParams(
    val weightKg: Double,
    val r: Double,
    val kaPerHour: Double,
    val vmaxGPerLH: Double,
    val kmGPerL: Double = KM_DEFAULT,
) {
    /** Объём распределения, литры. */
    val vdLiters: Double
        get() = r * weightKg

    companion object {
        const val KM_DEFAULT = 0.02
        const val VMAX_DEFAULT = 0.15
        const val KA_DEFAULT = 1.5

        fun of(
            profile: UserProfile,
            food: FoodIntake = FoodIntake.NORMAL,
            metabolism: Metabolism = Metabolism.AVERAGE,
        ): ModelParams = ModelParams(
            weightKg = profile.weightKg,
            r = profile.sex.r,
            kaPerHour = food.kaPerHour,
            vmaxGPerLH = metabolism.vmaxGPerLH,
            kmGPerL = KM_DEFAULT,
        )
    }
}

/**
 * Ручные переопределения параметров модели из настроек.
 * null — использовать рекомендуемое значение.
 */
@Serializable
data class ModelOverrides(
    val kaPerHour: Double? = null,
    val vmaxGPerLH: Double? = null,
    val kmGPerL: Double? = null,
    val r: Double? = null,
) {
    val isEmpty: Boolean
        get() = kaPerHour == null && vmaxGPerLH == null && kmGPerL == null && r == null

    fun applyTo(base: ModelParams): ModelParams = base.copy(
        kaPerHour = kaPerHour ?: base.kaPerHour,
        vmaxGPerLH = vmaxGPerLH ?: base.vmaxGPerLH,
        kmGPerL = kmGPerL ?: base.kmGPerL,
        r = r ?: base.r,
    )
}
