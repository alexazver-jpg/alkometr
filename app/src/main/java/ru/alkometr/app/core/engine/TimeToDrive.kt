package ru.alkometr.app.core.engine

import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.ModelParams

/**
 * Прогноз по вождению: можно ли сейчас и когда станет можно.
 * Считается строго по юридическому порогу, а не по степени опьянения.
 */
data class DriveForecast(
    val bacNow: Double,
    val thresholdGPerL: Double,
    val canDriveNow: Boolean,
    val readyAtMillis: Long?,
    val remainingMillis: Long?,
)

object TimeToDrive {

    fun forecast(
        drinks: List<Drink>,
        p: ModelParams,
        nowMillis: Long,
        thresholdGPerL: Double,
    ): DriveForecast {
        val bac = BacEngine.bacAt(drinks, p, nowMillis)
        if (bac <= thresholdGPerL) {
            return DriveForecast(
                bacNow = bac,
                thresholdGPerL = thresholdGPerL,
                canDriveNow = true,
                readyAtMillis = null,
                remainingMillis = 0L,
            )
        }
        val ready = BacEngine.timeToThreshold(drinks, p, nowMillis, thresholdGPerL)
        return DriveForecast(
            bacNow = bac,
            thresholdGPerL = thresholdGPerL,
            canDriveNow = false,
            readyAtMillis = ready,
            remainingMillis = ready?.let { it - nowMillis },
        )
    }
}
