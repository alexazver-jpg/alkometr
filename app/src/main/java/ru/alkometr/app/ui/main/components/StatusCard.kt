package ru.alkometr.app.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.IntoxicationLevel
import ru.alkometr.app.ui.main.MainUiState

/**
 * Статусная карточка: главный ответ приложения — можно ли за руль и когда будет можно.
 * Считается по юридическому порогу, а не по степени опьянения.
 */
@Composable
fun StatusCard(state: MainUiState, modifier: Modifier = Modifier) {
    val units = state.settings.units
    val threshold = state.settings.thresholdPermille
    val level = state.level
    val canDrive = state.bacNow <= threshold

    val accent = if (canDrive) Color(IntoxicationLevel.SOBER.colorArgb) else Color(IntoxicationLevel.DANGEROUS.colorArgb)
    val title: String = when {
        state.isEmptySession -> stringResource(R.string.status_no_data)
        canDrive -> stringResource(R.string.status_can_drive)
        state.forecast.readyAtMillis != null -> stringResource(
            R.string.status_drive_in,
            Formatters.formatDuration(state.forecast.remainingMillis ?: 0L),
            Formatters.formatClock(state.forecast.readyAtMillis),
        )

        else -> stringResource(R.string.timeline_drive_unknown)
    }

    val peakText: String? = when {
        state.isEmptySession -> null
        state.peakAhead -> stringResource(
            R.string.status_peak_ahead,
            Formatters.formatBac(state.peak.bacGPerL, units),
            Formatters.formatClock(state.peak.atMillis),
        )

        else -> stringResource(
            R.string.status_peak_past,
            Formatters.formatBac(state.peak.bacGPerL, units),
            Formatters.formatClock(state.peak.atMillis),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
        if (peakText != null) {
            Text(
                text = peakText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (level == IntoxicationLevel.DANGEROUS) {
            Text(
                text = stringResource(R.string.status_danger),
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
