package ru.alkometr.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.AppSettings
import ru.alkometr.app.core.model.Sex
import ru.alkometr.app.core.model.ThresholdPreset
import kotlin.math.roundToInt

/**
 * Первый запуск: вес, пол, порог вождения, дисклеймер.
 * Шаги можно пропустить — тогда остаются значения по умолчанию.
 */
@Composable
fun OnboardingScreen(
    initial: AppSettings,
    onFinish: (AppSettings) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var weight by remember { mutableFloatStateOf(initial.profile.weightKg.toFloat()) }
    var weightText by remember { mutableStateOf(Formatters.formatVolume(initial.profile.weightKg)) }
    var sex by remember { mutableStateOf(initial.profile.sex) }
    var threshold by remember { mutableStateOf(initial.thresholdPermille) }
    var thresholdText by remember { mutableStateOf(Formatters.formatVolume(initial.thresholdPermille)) }

    val weightValue = weightText.replace(',', '.').toDoubleOrNull()
    val weightValid = weightValue != null && weightValue in 30.0..200.0
    val othersValid = sex != null
    val thresholdValue = thresholdText.replace(',', '.').toDoubleOrNull()
    val thresholdValid = thresholdValue != null && thresholdValue in 0.0..0.8

    fun finish() {
        val profile = initial.profile.copy(
            weightKg = if (weightValid) weightValue!! else initial.profile.weightKg,
            sex = sex,
        )
        val thresholdPermille = if (thresholdValid) thresholdValue!! else initial.thresholdPermille
        onFinish(
            initial.copy(
                profile = profile,
                thresholdPermille = thresholdPermille,
                onboardingDone = true,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_step, step + 1, 4),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (step + 1) / 4f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(4.dp)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp),
        ) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_weight_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_weight_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Slider(
                        value = weight,
                        onValueChange = {
                            weight = it
                            weightText = it.roundToInt().toString()
                        },
                        valueRange = 30f..200f,
                        steps = 169,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { text ->
                            weightText = text.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                            weightText.replace(',', '.').toDoubleOrNull()?.let {
                                if (it in 30.0..200.0) weight = it.toFloat()
                            }
                        },
                        label = { Text(stringResource(R.string.settings_weight)) },
                        isError = !weightValid,
                        supportingText = if (!weightValid) {
                            { Text(stringResource(R.string.error_weight)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                1 -> {
                    Text(stringResource(R.string.onboarding_sex_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_sex_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        SexOption(Sex.MALE, stringResource(R.string.settings_sex_male), sex == Sex.MALE) { sex = Sex.MALE }
                        SexOption(Sex.FEMALE, stringResource(R.string.settings_sex_female), sex == Sex.FEMALE) { sex = Sex.FEMALE }
                        SexOption(Sex.UNSPECIFIED, stringResource(R.string.settings_sex_unspecified), sex == Sex.UNSPECIFIED) { sex = Sex.UNSPECIFIED }
                    }
                }

                2 -> {
                    Text(stringResource(R.string.onboarding_threshold_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_threshold_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        ThresholdChip(ThresholdPreset.RUSSIA, stringResource(R.string.settings_threshold_russia), threshold) {
                            threshold = it; thresholdText = Formatters.formatVolume(it)
                        }
                        ThresholdChip(ThresholdPreset.EUROPE, stringResource(R.string.settings_threshold_europe), threshold) {
                            threshold = it; thresholdText = Formatters.formatVolume(it)
                        }
                        ThresholdChip(ThresholdPreset.USA, stringResource(R.string.settings_threshold_usa), threshold) {
                            threshold = it; thresholdText = Formatters.formatVolume(it)
                        }
                        ThresholdChip(ThresholdPreset.ZERO, stringResource(R.string.settings_threshold_zero), threshold) {
                            threshold = it; thresholdText = Formatters.formatVolume(it)
                        }
                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { text ->
                                thresholdText = text.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                                thresholdText.replace(',', '.').toDoubleOrNull()?.let {
                                    if (it in 0.0..0.8) threshold = it
                                }
                            },
                            label = { Text(stringResource(R.string.settings_threshold_value)) },
                            isError = !thresholdValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }
                }

                else -> {
                    Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.disclaimer_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { finish() }) {
                Text(stringResource(R.string.onboarding_skip))
            }
            Button(
                onClick = {
                    if (step < 3) step++ else finish()
                },
                enabled = when (step) {
                    0 -> weightValid
                    1 -> othersValid
                    2 -> thresholdValid
                    else -> true
                },
            ) {
                Text(
                    stringResource(
                        if (step == 3) R.string.onboarding_start else R.string.onboarding_next,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SexOption(sex: Sex, title: String, selected: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (sex == Sex.UNSPECIFIED) {
            Text(
                stringResource(R.string.settings_sex_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThresholdChip(preset: ThresholdPreset, title: String, current: Double, onSelect: (Double) -> Unit) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        FilterChip(
            selected = kotlin.math.abs(current - preset.permille) < 0.001,
            onClick = { onSelect(preset.permille) },
            label = { Text(title) },
        )
    }
}
