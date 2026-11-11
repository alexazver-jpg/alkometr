package ru.alkometr.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.AppSettings
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.DrinkPreset
import ru.alkometr.app.core.model.FoodIntake
import ru.alkometr.app.core.model.Metabolism
import ru.alkometr.app.core.model.Sex
import ru.alkometr.app.core.model.ThresholdPreset
import ru.alkometr.app.core.model.Units
import ru.alkometr.app.core.model.UserProfile
import ru.alkometr.app.data.BackupCodec
import ru.alkometr.app.ui.main.components.DrinkDialog
import ru.alkometr.app.ui.main.components.iconRes
import ru.alkometr.app.ui.main.components.titleRes
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Настройки: профиль, напитки, модель, порог и единицы, данные, о приложении.
 * Любое изменение сразу сохраняется и пересчитывает кривую.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    drinks: List<Drink>,
    backupJson: () -> String,
    onSave: (AppSettings) -> Unit,
    onApplyBackup: (ru.alkometr.app.data.Backup) -> Unit,
    onClearHistory: () -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showAdvanced by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<DrinkPreset?>(null) }
    var newPreset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val savedMessage = stringResource(R.string.settings_saved)
    val exportMessage = stringResource(R.string.settings_export_done)
    val exportErrorMessage = stringResource(R.string.settings_export_error)
    val importMessage = stringResource(R.string.settings_import_done)

    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
                stream.use { it.write(backupJson().toByteArray(Charsets.UTF_8)) }
                true
            }.getOrDefault(false)
            scope.launch {
                snackbar.showSnackbar(if (saved) exportMessage else exportErrorMessage)
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Пустой файл")
                BackupCodec.decode(text)
            }.onSuccess { backup ->
                onApplyBackup(backup)
                scope.launch { snackbar.showSnackbar(importMessage) }
            }.onFailure { error ->
                importError = error.message
            }
        }
    }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Профиль ---
            Section(title = stringResource(R.string.settings_profile)) {
                Text(stringResource(R.string.settings_weight), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.profile.weightKg.toFloat(),
                    onValueChange = { value ->
                        onSave(settings.copy(profile = settings.profile.copy(weightKg = value.roundToInt().toDouble())))
                    },
                    colors = brandSliderColors(),
                    valueRange = 30f..200f,
                    steps = 169,
                )
                NumberField(
                    label = stringResource(R.string.settings_weight),
                    value = settings.profile.weightKg,
                    range = 30.0..200.0,
                    onValueChange = { weight ->
                        onSave(settings.copy(profile = settings.profile.copy(weightKg = weight)))
                    },
                )
                Text(
                    text = stringResource(R.string.settings_sex),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Sex.entries.forEach { option ->
                        FilterChip(
                            colors = brandChipColors(),
                            selected = settings.profile.sex == option,
                            onClick = {
                                onSave(settings.copy(profile = settings.profile.copy(sex = option)))
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            Sex.MALE -> R.string.settings_sex_male
                                            Sex.FEMALE -> R.string.settings_sex_female
                                            Sex.UNSPECIFIED -> R.string.settings_sex_unspecified
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_sex_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OptionalIntField(
                    label = stringResource(R.string.settings_height),
                    value = settings.profile.heightCm,
                    range = UserProfile.HEIGHT_RANGE,
                    onValueChange = { height ->
                        onSave(settings.copy(profile = settings.profile.copy(heightCm = height)))
                    },
                )
                OptionalIntField(
                    label = stringResource(R.string.settings_age),
                    value = settings.profile.ageYears,
                    range = UserProfile.AGE_RANGE,
                    onValueChange = { age ->
                        onSave(settings.copy(profile = settings.profile.copy(ageYears = age)))
                    },
                )
            }

            // --- Напитки ---
            Section(title = stringResource(R.string.settings_drinks)) {
                Text(
                    text = stringResource(R.string.settings_drinks_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                settings.presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(preset.kind.iconRes()),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(preset.kind.titleRes()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.drink_caption,
                                    Formatters.formatVolume(preset.volumeMl),
                                    Formatters.formatVolume(preset.abvPercent),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { editingPreset = preset }) {
                            Text(stringResource(R.string.settings_edit))
                        }
                        TextButton(onClick = {
                            onSave(settings.copy(presets = settings.presets.filterNot { it.id == preset.id }))
                        }) {
                            Text("×", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                TextButton(onClick = { newPreset = true }) {
                    Text(stringResource(R.string.settings_add_preset))
                }
            }

            // --- Модель ---
            Section(title = stringResource(R.string.settings_model)) {
                Text(stringResource(R.string.settings_food), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FoodIntake.entries.forEach { option ->
                        FilterChip(
                            colors = brandChipColors(),
                            selected = settings.food == option,
                            onClick = { onSave(settings.copy(food = option)) },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            FoodIntake.EMPTY_STOMACH -> R.string.settings_food_empty
                                            FoodIntake.NORMAL -> R.string.settings_food_normal
                                            FoodIntake.HEAVY -> R.string.settings_food_heavy
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_metabolism),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Metabolism.entries.forEach { option ->
                        FilterChip(
                            colors = brandChipColors(),
                            selected = settings.metabolism == option,
                            onClick = { onSave(settings.copy(metabolism = option)) },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            Metabolism.SLOW -> R.string.settings_metabolism_slow
                                            Metabolism.AVERAGE -> R.string.settings_metabolism_average
                                            Metabolism.FAST -> R.string.settings_metabolism_fast
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(R.string.settings_advanced))
                }
                if (showAdvanced) {
                    Text(
                        text = stringResource(R.string.settings_advanced_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    NumberField(
                        label = stringResource(R.string.settings_ka),
                        value = settings.overrides.kaPerHour ?: settings.modelParams.kaPerHour,
                        range = 0.1..6.0,
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(kaPerHour = it))) },
                    )
                    NumberField(
                        label = stringResource(R.string.settings_vmax),
                        value = settings.overrides.vmaxGPerLH ?: settings.modelParams.vmaxGPerLH,
                        range = 0.05..0.4,
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(vmaxGPerLH = it))) },
                    )
                    NumberField(
                        label = stringResource(R.string.settings_km),
                        value = settings.overrides.kmGPerL ?: settings.modelParams.kmGPerL,
                        range = 0.001..0.5,
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(kmGPerL = it))) },
                    )
                    NumberField(
                        label = stringResource(R.string.settings_r),
                        value = settings.overrides.r ?: settings.modelParams.r,
                        range = 0.3..1.0,
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(r = it))) },
                    )
                    TextButton(onClick = {
                        onSave(settings.copy(overrides = ru.alkometr.app.core.model.ModelOverrides()))
                    }) {
                        Text(stringResource(R.string.settings_reset_params))
                    }
                }
            }

            // --- Порог и единицы ---
            Section(title = stringResource(R.string.settings_threshold)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThresholdPreset.entries.forEach { preset ->
                        FilterChip(
                            colors = brandChipColors(),
                            selected = abs(settings.thresholdPermille - preset.permille) < 0.001,
                            onClick = { onSave(settings.copy(thresholdPermille = preset.permille)) },
                            label = {
                                Text(
                                    stringResource(
                                        when (preset) {
                                            ThresholdPreset.RUSSIA -> R.string.settings_threshold_russia
                                            ThresholdPreset.EUROPE -> R.string.settings_threshold_europe
                                            ThresholdPreset.USA -> R.string.settings_threshold_usa
                                            ThresholdPreset.ZERO -> R.string.settings_threshold_zero
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                NumberField(
                    label = stringResource(R.string.settings_threshold_value),
                    value = settings.thresholdPermille,
                    range = 0.0..0.8,
                    onValueChange = { onSave(settings.copy(thresholdPermille = it)) },
                )
                Text(
                    text = stringResource(R.string.settings_threshold_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_units),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Units.entries.take(2).forEach { unit ->
                            FilterChip(
                            colors = brandChipColors(),
                                selected = settings.units == unit,
                                onClick = { onSave(settings.copy(units = unit)) },
                                label = { Text(stringResource(unitLabel(unit))) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Units.entries.drop(2).forEach { unit ->
                            FilterChip(
                            colors = brandChipColors(),
                                selected = settings.units == unit,
                                onClick = { onSave(settings.copy(units = unit)) },
                                label = { Text(stringResource(unitLabel(unit))) },
                            )
                        }
                    }
                }
            }

            // --- Данные ---
            Section(title = stringResource(R.string.settings_data)) {
                TextButton(onClick = { createBackup.launch("alkometr-backup.json") }) {
                    Text(stringResource(R.string.settings_export))
                }
                TextButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text(stringResource(R.string.settings_import))
                }
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.settings_clear_history))
                }
                TextButton(onClick = { confirmReset = true }) {
                    Text(stringResource(R.string.settings_reset_all))
                }
                Text(
                    text = stringResource(R.string.history_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- О приложении ---
            Section(title = stringResource(R.string.settings_about)) {
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val repoUrl = stringResource(R.string.settings_repository_url)
                if (repoUrl.isNotBlank()) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    TextButton(onClick = { runCatching { uriHandler.openUri(repoUrl) } }) {
                        Text(stringResource(R.string.settings_repository))
                    }
                }
                TextButton(onClick = { showDisclaimer = true }) {
                    Text(stringResource(R.string.settings_disclaimer))
                }
            }
        }
    }

    if (editingPreset != null) {
        val preset = editingPreset!!
        DrinkDialog(
            title = stringResource(R.string.settings_edit_preset),
            initialVolumeMl = preset.volumeMl,
            initialAbvPercent = preset.abvPercent,
            initialKind = preset.kind,
            initialAtMillis = System.currentTimeMillis(),
            allowTime = false,
            allowName = true,
            initialName = preset.name,
            confirmText = stringResource(R.string.save),
            onDismiss = { editingPreset = null },
            onConfirm = { draft ->
                editingPreset = null
                onSave(
                    settings.copy(
                        presets = settings.presets.map {
                            if (it.id == preset.id) {
                                it.copy(
                                    name = draft.name,
                                    volumeMl = draft.volumeMl,
                                    abvPercent = draft.abvPercent,
                                    kind = draft.kind,
                                )
                            } else {
                                it
                            }
                        },
                    ),
                )
            },
        )
    }

    if (newPreset) {
        DrinkDialog(
            title = stringResource(R.string.settings_add_preset),
            initialVolumeMl = 100.0,
            initialAbvPercent = 10.0,
            initialKind = DrinkKind.CUSTOM,
            initialAtMillis = System.currentTimeMillis(),
            allowTime = false,
            allowName = true,
            confirmText = stringResource(R.string.custom_add),
            onDismiss = { newPreset = false },
            onConfirm = { draft ->
                newPreset = false
                onSave(
                    settings.copy(
                        presets = settings.presets + DrinkPreset(
                            id = UUID.randomUUID().toString(),
                            kind = draft.kind,
                            volumeMl = draft.volumeMl,
                            abvPercent = draft.abvPercent,
                            name = draft.name,
                        ),
                    ),
                )
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_history)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                    scope.launch { snackbar.showSnackbar(savedMessage) }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_all)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetAll()
                    onBack()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_text)) },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.settings_import)) },
            text = { Text(stringResource(R.string.settings_import_error, message)) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun unitLabel(unit: Units): Int = when (unit) {
    Units.PERMILLE -> R.string.settings_units_permille
    Units.PERCENT -> R.string.settings_units_percent
    Units.MG_PER_100ML -> R.string.settings_units_mg_100
    Units.MG_PER_LITER -> R.string.settings_units_mg_l
}

/**
 * Числовое поле: показывает значение из настроек, но позволяет набирать его посимвольно.
 * Некорректный ввод не сохраняется и не сбрасывает курсор.
 */
@Composable
private fun NumberField(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(Formatters.formatVolume(value)) }
    LaunchedEffect(value) {
        val parsed = text.replace(',', '.').toDoubleOrNull()
        if (parsed == null || abs(parsed - value) > 1e-9) text = Formatters.formatVolume(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() || it == ',' || it == '.' }
            text.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it in range }
                ?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Необязательное целочисленное поле: пустое значение — null, вне диапазона не сохраняется.
 */
@Composable
private fun OptionalIntField(
    label: String,
    value: Int?,
    range: IntRange,
    onValueChange: (Int?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() }.take(3)
            if (text.isEmpty()) {
                onValueChange(null)
            } else {
                text.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun brandChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun brandSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
)