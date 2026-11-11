package ru.alkometr.app.ui.main.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.DrinkKind
import java.util.Calendar

/** То, что пользователь ввёл в диалоге порции. */
data class DrinkDraft(
    val name: String?,
    val volumeMl: Double,
    val abvPercent: Double,
    val kind: DrinkKind,
    val atMillis: Long,
)

/**
 * Диалог порции: объём, крепость, вид сосуда и (опционально) время.
 * В режиме пресета добавляется необязательное название.
 */
@Composable
fun DrinkDialog(
    title: String,
    initialVolumeMl: Double,
    initialAbvPercent: Double,
    initialKind: DrinkKind,
    initialAtMillis: Long,
    allowTime: Boolean,
    confirmText: String,
    onDismiss: () -> Unit,
    allowName: Boolean = false,
    initialName: String? = null,
    onConfirm: (DrinkDraft) -> Unit,
) {
    var volumeText by remember { mutableStateOf(Formatters.formatVolume(initialVolumeMl)) }
    var abvText by remember { mutableStateOf(Formatters.formatVolume(initialAbvPercent)) }
    var nameText by remember { mutableStateOf(initialName.orEmpty()) }
    var kind by remember { mutableStateOf(initialKind) }
    var atMillis by remember { mutableStateOf(initialAtMillis) }

    val volume = volumeText.replace(',', '.').toDoubleOrNull()
    val abv = abvText.replace(',', '.').toDoubleOrNull()
    val volumeError = volume == null || volume !in 10.0..2000.0
    val abvError = abv == null || abv !in 0.1..96.0
    val valid = !volumeError && !abvError

    val context = LocalContext.current

    fun pickDateTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = atMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        atMillis = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowName) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it.take(40) },
                        label = { Text(stringResource(R.string.settings_preset_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrinkKind.entries.filter { it != DrinkKind.CUSTOM }.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(option.titleRes())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text(stringResource(R.string.custom_volume)) },
                    isError = volumeError,
                    supportingText = if (volumeError) {
                        { Text(stringResource(R.string.error_volume)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = abvText,
                    onValueChange = { abvText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text(stringResource(R.string.custom_abv)) },
                    isError = abvError,
                    supportingText = if (abvError) {
                        { Text(stringResource(R.string.error_abv)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.custom_time),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Row {
                            TextButton(onClick = { atMillis = System.currentTimeMillis() }) {
                                Text(stringResource(R.string.custom_time_now))
                            }
                            TextButton(onClick = { pickDateTime() }) {
                                Text(Formatters.formatDateTime(atMillis))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) {
                        onConfirm(
                            DrinkDraft(
                                name = nameText.trim().takeIf { it.isNotEmpty() },
                                volumeMl = volume!!,
                                abvPercent = abv!!,
                                kind = kind,
                                atMillis = atMillis,
                            ),
                        )
                    }
                },
                enabled = valid,
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
