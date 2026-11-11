package ru.alkometr.app.core.model

// Импорт плагина сериализации: все настройки целиком сохраняются одним JSON-объектом.
import kotlinx.serialization.Serializable

/**
 * Все настройки приложения одним объектом: профиль, параметры модели,
 * порог вождения, единицы, пресеты напитков и ручные переопределения.
 *
 * Почему один класс, а не десять отдельных ключей: объект целиком сериализуется в JSON
 * и сохраняется/читается за один шаг, а значения по умолчанию дают готовые настройки
 * «из коробки» — достаточно вызвать `AppSettings()`.
 *
 * @param profile профиль пользователя: вес, пол, рост, возраст.
 * @param food характер еды перед первой порцией — влияет на скорость всасывания ka.
 * @param metabolism привычный метаболизм — влияет на скорость выведения Vmax.
 * @param thresholdPermille юридический порог, ‰ (0,3 — Россия).
 * @param units в каких единицах показывать концентрацию.
 * @param onboardingDone пройдено ли первое знакомство: если false, показываем обучающие экраны.
 * @param presets кнопки быстрого добавления напитков на главном экране.
 * @param overrides ручные переопределения параметров модели (обычно пустые).
 */
@Serializable
data class AppSettings(
    val profile: UserProfile = UserProfile(),  // дефолтный профиль: 80 кг, пол не указан
    val food: FoodIntake = FoodIntake.NORMAL,  // обычный приём пищи
    val metabolism: Metabolism = Metabolism.AVERAGE,  // средний метаболизм
    val thresholdPermille: Double = Units.DEFAULT_THRESHOLD_PERMILLE,  // 0,3 ‰ по умолчанию
    val units: Units = Units.PERMILLE,  // показываем в промилле — привычнее всего
    val onboardingDone: Boolean = false,  // новый пользователь: обучение ещё не пройдено
    val presets: List<DrinkPreset> = DrinkPreset.defaults(),  // три стандартные кнопки напитков
    val overrides: ModelOverrides = ModelOverrides(),  // пустые переопределения = все значения рекомендуемые
) {
    /**
     * Итоговые параметры модели с учётом ручных переопределений.
     *
     * Собирается каждый раз при обращении: сначала рекомендуемые значения из профиля,
     * затем поверх них — то, что пользователь ввёл вручную.
     */
    val modelParams: ModelParams
        // Сначала ModelParams.of(...) собирает базовые параметры, затем applyTo накладывает правки.
        get() = overrides.applyTo(ModelParams.of(profile, food, metabolism))

    // Компаньон-объект: допустимые диапазоны для полей настроек.
    companion object {
        // Порог вождения, ‰: от 0 (полный запрет) до 0,8 (США).
        val THRESHOLD_RANGE = 0.0..0.8
        // Объём порции в пресете, мл: от 10 мл (шот) до 2 литров.
        val PRESET_VOLUME_RANGE = 10.0..2000.0
        // Крепость в пресете, % об.: от 0 (безалкогольное) до 96 (спирт-ректификат).
        val PRESET_ABV_RANGE = 0.0..96.0
    }
}
