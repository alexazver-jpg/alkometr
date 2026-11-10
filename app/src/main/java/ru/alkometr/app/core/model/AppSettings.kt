package ru.alkometr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Все настройки приложения одним объектом: профиль, параметры модели,
 * порог вождения, единицы, пресеты напитков и ручные переопределения.
 */
@Serializable
data class AppSettings(
    val profile: UserProfile = UserProfile(),
    val food: FoodIntake = FoodIntake.NORMAL,
    val metabolism: Metabolism = Metabolism.AVERAGE,
    val thresholdPermille: Double = Units.DEFAULT_THRESHOLD_PERMILLE,
    val units: Units = Units.PERMILLE,
    val onboardingDone: Boolean = false,
    val presets: List<DrinkPreset> = DrinkPreset.defaults(),
    val overrides: ModelOverrides = ModelOverrides(),
) {
    /** Итоговые параметры модели с учётом ручных переопределений. */
    val modelParams: ModelParams
        get() = overrides.applyTo(ModelParams.of(profile, food, metabolism))

    companion object {
        val THRESHOLD_RANGE = 0.0..0.8
        val PRESET_VOLUME_RANGE = 10.0..2000.0
        val PRESET_ABV_RANGE = 0.0..96.0
    }
}
