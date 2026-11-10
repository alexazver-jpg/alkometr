package ru.alkometr.app.data

import kotlinx.serialization.json.Json
import ru.alkometr.app.core.model.AppSettings
import ru.alkometr.app.core.model.Drink
import kotlinx.serialization.Serializable

/** Формат файла резервной копии. */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtMillis: Long,
    val settings: AppSettings,
    val drinks: List<Drink>,
) {
    companion object {
        const val FORMAT = "alkometr-backup"
        const val VERSION = 1
    }
}

/** Экспорт и импорт данных приложения в JSON. */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(settings: AppSettings, drinks: List<Drink>, exportedAtMillis: Long): String =
        json.encodeToString(
            Backup.serializer(),
            Backup(
                exportedAtMillis = exportedAtMillis,
                settings = settings,
                drinks = drinks.sortedBy { it.atMillis },
            ),
        )

    /**
     * Разбор резервной копии. Бросает [IllegalArgumentException] с понятным текстом,
     * если файл не от этого приложения или повреждён.
     */
    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Файл не является резервной копией «АлкоМетра»", e)
        }
        require(backup.format == Backup.FORMAT) { "Неизвестный формат файла: ${backup.format}" }
        require(backup.version <= Backup.VERSION) { "Файл создан более новой версией приложения" }
        return backup
    }
}
