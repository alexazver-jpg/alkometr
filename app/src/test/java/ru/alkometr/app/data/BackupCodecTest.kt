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

/** Экспорт и импорт: данные должны переживать сериализацию без потерь. */
class BackupCodecTest {

    private val settings = AppSettings(
        profile = ru.alkometr.app.core.model.UserProfile(weightKg = 72.5, sex = Sex.FEMALE, heightCm = 168),
        food = FoodIntake.HEAVY,
        metabolism = Metabolism.FAST,
        thresholdPermille = 0.5,
        units = Units.PERCENT,
        onboardingDone = true,
        presets = DrinkPreset.defaults() + DrinkPreset("custom", DrinkKind.CUSTOM, 330.0, 4.5, name = "Стаут 0,33"),
    )

    private val drinks = listOf(
        Drink("a", 1_700_000_000_000L, 50.0, 40.0, DrinkKind.SHOT),
        Drink("b", 1_700_000_600_000L, 500.0, 5.0, DrinkKind.MUG),
    )

    @Test
    fun roundTrip_preservesSettingsAndDrinks() {
        val json = BackupCodec.encode(settings, drinks, 1_700_000_000_000L)
        val restored = BackupCodec.decode(json)

        assertEquals(settings, restored.settings)
        assertEquals(drinks, restored.drinks)
        assertEquals(Backup.FORMAT, restored.format)
        assertEquals(Backup.VERSION, restored.version)
    }

    @Test
    fun decode_rejectsForeignFile() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("{\"hello\":\"world\"}")
        }
    }

    @Test
    fun decode_rejectsBrokenJson() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("не json")
        }
    }

    @Test
    fun encode_isStableAndSortedByTime() {
        val json = BackupCodec.encode(settings, drinks.reversed(), 0L)
        val restored = BackupCodec.decode(json)
        assertEquals(listOf("a", "b"), restored.drinks.map { it.id })
        assertTrue(json.contains("\"format\": \"alkometr-backup\""))
    }
}
