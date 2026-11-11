package ru.alkometr.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.alkometr.app.core.model.AppSettings
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.DrinkPreset
import ru.alkometr.app.core.model.FoodIntake
import ru.alkometr.app.core.model.Metabolism
import ru.alkometr.app.core.model.Sex
import ru.alkometr.app.core.model.Units

/**
 * Экспорт и импорт: данные должны переживать сериализацию без потерь.
 *
 * Речь о резервной копии: настройки и история порций превращаются в JSON, сохраняются
 * в файл и потом восстанавливаются. Пользователь не должен заметить разницы, поэтому
 * главный тест здесь — «round trip»: закодировали, раскодировали, сравнили с исходником.
 * JUnit создаёт новый экземпляр класса на каждый тест, так что поля ниже — общая заготовка,
 * а не изменяемое состояние, которым тесты могли бы поделиться.
 */
class BackupCodecTest {

    // Эталонные настройки для проверки: заполнены все поля, которые есть в модели, включая
    // нестандартные значения (женщина, тяжёлая еда, быстрый метаболизм, проценты вместо промилле),
    // и добавлен пользовательский пресет напитка. Чем больше разных полей, тем заметнее потеря
    // данных при сериализации.
    private val settings = AppSettings(
        profile = ru.alkometr.app.core.model.UserProfile(weightKg = 72.5, sex = Sex.FEMALE, heightCm = 168),
        food = FoodIntake.HEAVY,
        metabolism = Metabolism.FAST,
        thresholdPermille = 0.5,
        units = Units.PERCENT,
        onboardingDone = true,
        presets = DrinkPreset.defaults() + DrinkPreset("custom", DrinkKind.CUSTOM, 330.0, 4.5, name = "Стаут 0,33"),
    )

    // Две порции с разными параметрами и временем. Аргументы переданы позиционно:
    // id, момент времени, объём, крепость, вид напитка.
    private val drinks = listOf(
        Drink("a", 1_700_000_000_000L, 50.0, 40.0, DrinkKind.SHOT),
        Drink("b", 1_700_000_600_000L, 500.0, 5.0, DrinkKind.MUG),
    )

    // Главный тест: полный цикл «объекты → JSON → объекты». Сравниваются целые объекты,
    // а не отдельные поля: у data-классов equals проверяет все свойства, поэтому любая
    // потерянная деталь (единицы, порог, пресеты) сразу уронит тест.
    @Test
    fun roundTrip_preservesSettingsAndDrinks() {
        val json = BackupCodec.encode(settings, drinks, 1_700_000_000_000L)
        val restored = BackupCodec.decode(json)

        // Настройки и история должны совпасть с исходными один в один.
        assertEquals(settings, restored.settings)
        assertEquals(drinks, restored.drinks)
        // Плюс служебные поля: по ним импорт понимает, что файл «свой» и какой он версии.
        assertEquals(Backup.FORMAT, restored.format)
        assertEquals(Backup.VERSION, restored.version)
    }

    // Чужой файл: JSON синтаксически корректный, но это не наша резервная копия.
    // Импорт обязан отказаться, иначе он молча затрёт настройки случайными значениями.
    // assertThrows проверяет не только результат, но и тип исключения: ждём
    // IllegalArgumentException — значит, ошибка обработана как «неподходящий файл».
    @Test
    fun decode_rejectsForeignFile() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("{\"hello\":\"world\"}")
        }
    }

    // Битый JSON: файл обрезан или испорчен при передаче. Это тоже пользовательская
    // ошибка, а не сбой приложения, поэтому снова ожидаем IllegalArgumentException,
    // а не падение с другим исключением.
    @Test
    fun decode_rejectsBrokenJson() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("не json")
        }
    }

    // Кодирование должно быть устойчивым: на вход подаём историю в обратном порядке
    // (как она пришла бы из другого источника), а на выходе ожидаем сортировку по времени.
    // Порядок важен для графика и для повторного экспорта — одинаковые данные должны
    // давать одинаковый файл.
    @Test
    fun encode_isStableAndSortedByTime() {
        val json = BackupCodec.encode(settings, drinks.reversed(), 0L)
        val restored = BackupCodec.decode(json)
        // После восстановления порции идут по возрастанию времени: сначала "a", потом "b".
        assertEquals(listOf("a", "b"), restored.drinks.map { it.id })
        // И в файле есть маркер формата — по нему импорт отличает свою копию от чужой.
        assertTrue(json.contains("\"format\": \"alkometr-backup\""))
    }
}
