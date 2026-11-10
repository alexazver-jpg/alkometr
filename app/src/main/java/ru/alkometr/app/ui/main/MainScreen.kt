package ru.alkometr.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.DrinkPreset
import ru.alkometr.app.ui.main.components.BacGauge
import ru.alkometr.app.ui.main.components.BacTimeline
import ru.alkometr.app.ui.main.components.DrinkButtons
import ru.alkometr.app.ui.main.components.DrinkDialog
import ru.alkometr.app.ui.main.components.StatusCard
import ru.alkometr.app.ui.main.components.hintRes
import ru.alkometr.app.ui.main.components.iconRes
import ru.alkometr.app.ui.main.components.titleRes

/**
 * Главный экран: шкала-индикатор, статус, кнопки напитков и шкала времени.
 * Ничего не считает сам — берёт готовое состояние из [MainViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onAddPreset: (DrinkPreset) -> Unit,
    onSavePreset: (DrinkPreset) -> Unit,
    onCustomAdd: (Double, Double, DrinkKind, Long) -> Unit,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearSession: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val units = state.settings.units

    var menuOpen by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<DrinkPreset?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val lastAdded = state.drinks.firstOrNull { it.id == state.lastAddedId }
    val addedText = lastAdded?.let { drink ->
        stringResource(
            R.string.drink_added,
            Formatters.formatValue(drink.ethanolGrams / state.settings.modelParams.vdLiters, units),
        )
    }
    LaunchedEffect(addedText) {
        addedText?.let { snackbarHostState.showSnackbar(it) }
    }

    val driveText = when {
        state.forecast.canDriveNow -> stringResource(R.string.timeline_drive_now)
        state.forecast.readyAtMillis != null -> stringResource(
            R.string.timeline_drive_at,
            Formatters.formatClock(state.forecast.readyAtMillis),
            Formatters.formatDuration(state.forecast.remainingMillis ?: 0L),
        )

        else -> stringResource(R.string.timeline_drive_unknown)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(state.level.hintRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_settings),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.menu_more),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_history)) },
                                onClick = { menuOpen = false; onOpenHistory() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear_session)) },
                                onClick = { menuOpen = false; confirmClear = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                onClick = { menuOpen = false; onOpenAbout() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            BacGauge(
                bac = state.bacNow,
                level = state.level,
                threshold = state.settings.thresholdPermille,
                units = units,
            )

            StatusCard(state = state, modifier = Modifier.padding(bottom = 12.dp))

            DrinkButtons(
                presets = state.settings.presets,
                onAdd = { preset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddPreset(preset)
                },
                onEdit = { editingPreset = it },
                onCustom = { customDialog = true },
            )

            if (state.isEmptySession) {
                Text(
                    text = stringResource(R.string.drinks_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            LastDrinkRow(
                drink = lastAdded ?: state.lastDrink,
                nowMillis = state.nowMillis,
                canUndo = lastAdded != null,
                onUndo = onUndo,
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.timeline_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            BacTimeline(
                curve = state.curve,
                drinks = state.drinks,
                params = state.settings.modelParams,
                threshold = state.settings.thresholdPermille,
                units = units,
                nowMillis = state.nowMillis,
                initialStartMillis = state.curveStartMillis,
                initialEndMillis = maxOf(state.curveEndMillis, state.curveStartMillis + 60 * 60 * 1000L),
                driveText = driveText,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (customDialog) {
        DrinkDialog(
            title = stringResource(R.string.custom_title),
            initialVolumeMl = 50.0,
            initialAbvPercent = 40.0,
            initialKind = DrinkKind.CUSTOM,
            initialAtMillis = state.nowMillis,
            allowTime = true,
            confirmText = stringResource(R.string.custom_add),
            onDismiss = { customDialog = false },
            onConfirm = { volume, abv, kind, at ->
                customDialog = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCustomAdd(volume, abv, kind, at)
            },
        )
    }

    editingPreset?.let { preset ->
        DrinkDialog(
            title = stringResource(R.string.settings_edit_preset),
            initialVolumeMl = preset.volumeMl,
            initialAbvPercent = preset.abvPercent,
            initialKind = preset.kind,
            initialAtMillis = state.nowMillis,
            allowTime = false,
            confirmText = stringResource(R.string.save),
            onDismiss = { editingPreset = null },
            onConfirm = { volume, abv, kind, _ ->
                editingPreset = null
                onSavePreset(preset.copy(volumeMl = volume, abvPercent = abv, kind = kind))
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.menu_clear_session)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearSession()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LastDrinkRow(
    drink: Drink?,
    nowMillis: Long,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (drink == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(drink.kind.iconRes()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp),
            )
            Column {
                Text(
                    text = stringResource(
                        R.string.drink_last,
                        Formatters.formatAge(drink.atMillis, nowMillis),
                        stringResource(drink.kind.titleRes()),
                        Formatters.formatVolume(drink.volumeMl),
                        Formatters.formatVolume(drink.abvPercent),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (canUndo) {
            TextButton(onClick = onUndo) {
                Text(stringResource(R.string.undo), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
