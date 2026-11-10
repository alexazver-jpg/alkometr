package ru.alkometr.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.alkometr.app.R
import ru.alkometr.app.ui.history.HistoryScreen
import ru.alkometr.app.ui.main.MainScreen
import ru.alkometr.app.ui.main.MainViewModel
import ru.alkometr.app.ui.onboarding.OnboardingScreen
import ru.alkometr.app.ui.settings.SettingsScreen

/** Экраны приложения. Навигация ручная: экранов мало, библиотека навигации не нужна. */
private sealed interface Screen {
    data object Main : Screen
    data object Settings : Screen
    data object History : Screen
}

@Composable
fun AppRoot(mainViewModel: MainViewModel = viewModel()) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var aboutOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            !state.settings.onboardingDone -> OnboardingScreen(
                initial = state.settings,
                onFinish = mainViewModel::saveSettings,
            )

            screen == Screen.Settings -> SettingsScreen(
                settings = state.settings,
                drinks = state.drinks,
                backupJson = mainViewModel::buildBackupJson,
                onSave = mainViewModel::saveSettings,
                onApplyBackup = mainViewModel::applyBackup,
                onClearHistory = mainViewModel::clearHistory,
                onResetAll = mainViewModel::resetAll,
                onBack = { screen = Screen.Main },
            )

            screen == Screen.History -> HistoryScreen(
                drinks = state.drinks,
                nowMillis = state.nowMillis,
                onDelete = mainViewModel::deleteDrink,
                onAddPast = { volume, abv, kind, at ->
                    mainViewModel.addDrink(volume, abv, kind, at)
                },
                onBack = { screen = Screen.Main },
            )

            else -> MainScreen(
                state = state,
                onAddPreset = mainViewModel::addDrink,
                onSavePreset = mainViewModel::savePreset,
                onCustomAdd = { volume, abv, kind, at ->
                    mainViewModel.addDrink(volume, abv, kind, at)
                },
                onUndo = mainViewModel::undoLastAdded,
                onOpenSettings = { screen = Screen.Settings },
                onOpenHistory = { screen = Screen.History },
                onOpenAbout = { aboutOpen = true },
                onClearSession = mainViewModel::clearSession,
            )
        }
    }

    if (aboutOpen) {
        AboutDialog(onClose = { aboutOpen = false })
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.about_algorithm),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.about_algorithm_text),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.disclaimer_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.disclaimer_text),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}
