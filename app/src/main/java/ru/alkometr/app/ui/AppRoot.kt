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

/**
 * Экраны приложения. Навигация ручная: экранов мало, библиотека навигации не нужна.
 *
 * Это «навигация через состояние»: текущий экран лежит в обычной переменной состояния,
 * а Compose сам перерисовывает интерфейс, когда она меняется. Ни NavHost, ни маршрутов-строк,
 * ни графа переходов — переход = присваивание нового значения.
 */
private sealed interface Screen { // sealed interface — «запечатанный» интерфейс: компилятор знает
    // все реализации (они объявлены рядом), поэтому when по Screen можно писать без ветки else.
    data object Main : Screen // data object — объект-синглтон с готовыми equals, hashCode и toString.
    // У экрана нет данных, поэтому достаточно одного экземпляра на всё приложение.
    data object Settings : Screen // Экран настроек: резервная копия, очистка истории, сброс данных.
    data object History : Screen // Экран истории за 7 дней со свайп-удалением порций.
}

@Composable // @Composable помечает функцию как часть UI: её вызывает Compose при перерисовке.
fun AppRoot(mainViewModel: MainViewModel = viewModel()) { // Параметр по умолчанию = внедрение зависимости:
    // viewModel() создаёт или достаёт MainViewModel, которая переживает поворот экрана
    // (хранится в ViewModelStore) и не перезагружает данные при каждой рекомпозиции.
    val state by mainViewModel.uiState.collectAsStateWithLifecycle() // ViewModel отдаёт StateFlow.
    // collectAsStateWithLifecycle подписывается на него только пока экран видим (экономит ресурсы),
    // а by превращает State<T> в обычное значение, чтобы читать state.loading без .value.
    var screen by remember { mutableStateOf<Screen>(Screen.Main) } // Текущий экран — состояние Compose:
    // remember сохраняет значение между рекомпозициями, mutableStateOf делает его наблюдаемым,
    // поэтому изменение screen автоматически запускает перерисовку.
    var aboutOpen by remember { mutableStateOf(false) } // Флаг диалога «О приложении»: диалог —
    // не отдельный экран, а оверлей поверх текущего, поэтому ему хватает boolean.

    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main } // Перехват системной кнопки
    // «Назад»: пока мы не на главном экране, нажатие возвращает на главный и не закрывает приложение.
    // enabled = false на главном экране отдаёт событие системе, и приложение закрывается как обычно.

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { // Surface —
        // базовый контейнер Material: рисует фон из темы и задаёт цвет содержимого по умолчанию;
        // fillMaxSize растягивает его на весь экран.
        when { // when без аргумента — цепочка условий сверху вниз: срабатывает первая истинная ветка.
            state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { // Пока
                // данные читаются с диска, показываем заглушку: Box на весь экран с центровкой содержимого.
                CircularProgressIndicator() // Стандартный круговой индикатор загрузки Material 3.
            }

            !state.settings.onboardingDone -> OnboardingScreen( // Онбординг важнее выбора экрана:
                // пока пользователь не прошёл первый запуск, показываем его вместо любого экрана.
                initial = state.settings, // Стартовые значения полей — из уже сохранённых настроек.
                onFinish = mainViewModel::saveSettings, // :: — ссылка на метод: ViewModel сохранит настройки.
            )

            screen == Screen.Settings -> SettingsScreen( // Условие «текущий экран — настройки».
                settings = state.settings, // Единственный источник правды — state из ViewModel.
                drinks = state.drinks, // История нужна для экспорта и очистки.
                backupJson = mainViewModel::buildBackupJson, // Функция-поставщик JSON для экспорта.
                onSave = mainViewModel::saveSettings, // Сохранение изменённых настроек.
                onApplyBackup = mainViewModel::applyBackup, // Восстановление данных из JSON.
                onClearHistory = mainViewModel::clearHistory, // Удалить историю, оставив настройки.
                onResetAll = mainViewModel::resetAll, // Полный сброс к состоянию первого запуска.
                onBack = { screen = Screen.Main }, // «Назад» просто меняет состояние — Compose перерисует экран.
            )

            screen == Screen.History -> HistoryScreen( // Условие «текущий экран — история».
                drinks = state.drinks, // Тот же список порций, что и на главном экране.
                nowMillis = state.nowMillis, // «Текущее время» тоже часть состояния: так UI остаётся предсказуемым.
                onDelete = mainViewModel::deleteDrink, // Удаление порции по id.
                onAddPast = { volume, abv, kind, at -> // Лямбда-адаптер: экран отдаёт четыре значения,
                    // а ViewModel принимает их в том же порядке (volume, abv, kind, at).
                    mainViewModel.addDrink(volume, abv, kind, at) // Добавляем порцию с указанным временем.
                },
                onBack = { screen = Screen.Main }, // Возврат на главный экран.
            )

            else -> MainScreen( // Во всех остальных случаях — главный экран (он же экран по умолчанию).
                state = state, // Передаём готовый снимок состояния целиком: экран только рисует.
                onAddPreset = mainViewModel::addDrink, // Добавление порции из готового пресета.
                onSavePreset = mainViewModel::savePreset, // Сохранение своего пресета напитка.
                onCustomAdd = { volume, abv, kind, at -> // Ручной ввод: объём, крепость, вид, время.
                    mainViewModel.addDrink(volume, abv, kind, at) // Тот же метод, что и для пресетов.
                },
                onUndo = mainViewModel::undoLastAdded, // Отменить последнее добавление (защита от промаха).
                onOpenSettings = { screen = Screen.Settings }, // Переход к настройкам.
                onOpenHistory = { screen = Screen.History }, // Переход к истории.
                onOpenAbout = { aboutOpen = true }, // Показать диалог «О приложении».
                onClearSession = mainViewModel::clearSession, // Начать заново: обнулить текущую сессию.
            )
        }
    }

    if (aboutOpen) { // Диалог рисуется поверх Surface и живёт отдельно от when:
        AboutDialog(onClose = { aboutOpen = false }) // закрытие — это просто сброс флага.
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) { // private: диалог нужен только внутри AppRoot.
    AlertDialog( // AlertDialog — готовый модальный диалог Material 3: затемнение, карточка, кнопки.
        onDismissRequest = onClose, // Вызывается при тапе мимо диалога или кнопке «назад».
        title = { Text(stringResource(R.string.about_title)) }, // Слот заголовка: текст берём из ресурсов,
        // чтобы его можно было перевести, не трогая код.
        text = { // Слот содержимого.
            Column( // Колонка: несколько абзацев друг под другом.
                modifier = Modifier
                    .height(360.dp) // Фиксированная высота: диалог не растягивается на весь экран.
                    .verticalScroll(rememberScrollState()), // Длинный текст прокручивается внутри диалога;
                // rememberScrollState хранит позицию прокрутки между рекомпозициями.
            ) {
                Text(
                    text = stringResource(R.string.about_text), // Вводный текст о приложении.
                    style = MaterialTheme.typography.bodyMedium, // Стиль из Type.kt — не задаём размер вручную.
                )
                Text(
                    text = stringResource(R.string.about_algorithm), // Заголовок раздела «Алгоритм».
                    style = MaterialTheme.typography.titleMedium, // Средний заголовок: крупнее и жирнее текста.
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), // Отступ сверху отделяет раздел,
                    // небольшой отступ снизу прижимает заголовок к своему тексту.
                )
                Text(
                    text = stringResource(R.string.about_algorithm_text), // Описание расчёта промилле.
                    style = MaterialTheme.typography.bodySmall, // Мелкий текст: это пояснение, а не главное.
                )
                Text(
                    text = stringResource(R.string.disclaimer_title), // Заголовок дисклеймера.
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), // Тот же ритм отступов между разделами.
                )
                Text(
                    text = stringResource(R.string.disclaimer_text), // Текст предупреждения.
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { // Слот кнопки подтверждения (у AlertDialog она обязательна).
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } // Текстовая кнопка «Закрыть»:
            // ненавязчивое действие, поэтому TextButton, а не заполненная Button.
        },
    )
}
