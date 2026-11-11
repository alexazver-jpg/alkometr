package ru.alkometr.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
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
 *
 * @Composable — пометка «эта функция описывает кусок интерфейса». Вызывать её можно
 * только из другой @Composable-функции (или из setContent), а внутри разрешено
 * пользоваться remember, анимациями и другими composable-функциями.
 *
 * Рекомпозиция — повторный вызов этой функции, когда меняются данные, которые она
 * прочитала. Compose сам запоминает, кто что читал, и перезапускает только
 * изменившиеся участки. Поэтому здесь нет кода «перерисовать экран»: достаточно,
 * чтобы пришёл новый [state] — интерфейс обновится сам.
 *
 * [Modifier] — неизменяемое описание внешних свойств элемента (размеры, отступы, фон,
 * прокрутка, обработчики). Он не рисует ничего сам, а «оборачивает» элемент: вызовы
 * в цепочке применяются по порядку, слева направо.
 *
 * Все действия переданы параметрами-колбэками: экран не хранит бизнес-данные,
 * он показывает [state] и сообщает наверх о нажатиях («состояние поднято наверх»).
 * Благодаря этому экран можно переиспользовать и тестировать без ViewModel.
 *
 * @param state полное состояние экрана: BAC, степень, порции, прогноз, настройки.
 * @param onAddPreset добавить порцию по готовому пресету.
 * @param onSavePreset сохранить изменённый пресет.
 * @param onCustomAdd добавить произвольную порцию (объём, крепость, вид, время).
 * @param onUndo отменить последнее добавление.
 * @param onOpenSettings открыть экран настроек.
 * @param onOpenHistory открыть историю.
 * @param onOpenAbout открыть «О приложении».
 * @param onClearSession очистить текущую сессию (после подтверждения в диалоге).
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
    // remember { … } вычисляет объект один раз и хранит его между рекомпозициями.
    // Без remember SnackbarHostState создавался бы заново на каждом пересчёте экрана,
    // и очередь сообщений (а с ней и показ «порция добавлена») терялась бы.
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalHapticFeedback — «локальное окружение» композиции (CompositionLocal):
    // даёт доступ к системной вибрации без ручной передачи сервиса через параметры.
    val haptics = LocalHapticFeedback.current
    // Единицы измерения из настроек: ‰ или мг/л и т. п. Нужны для всех подписей.
    val units = state.settings.units
    // Цвет линии порога берём из темы (обычно красный, colorScheme.error).
    val thresholdColor = MaterialTheme.colorScheme.error

    // Ниже — четыре независимых флага интерфейса. mutableStateOf создаёт наблюдаемую
    // ячейку: Compose перерисует экран, когда в неё запишут новое значение.
    // by — делегирование свойства, поэтому menuOpen читается и пишется как обычная
    // переменная (под капотом вызываются getValue/setValue у State).
    var menuOpen by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    // Тип указан явно: значение может быть null — «диалог правки закрыт».
    var editingPreset by remember { mutableStateOf<DrinkPreset?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // Ищем только что добавленную порцию по её id: она нужна для сообщения
    // внизу экрана и для кнопки «Отменить».
    val lastAdded = state.drinks.firstOrNull { it.id == state.lastAddedId }
    // Самое раннее время среди порций; null, если порций нет вообще.
    val firstDrinkAt = state.drinks.minOfOrNull { it.atMillis }
    // Начало видимого окна шкалы времени.
    // Если порций нет — показываем последние 6 часов: 6 ч · 60 мин · 60 с · 1000 мс.
    // Если порции есть — берём самую раннюю минус 30 минут «воздуха» слева, но не
    // правее, чем «сейчас минус 6 часов»: окно никогда не бывает короче шести часов.
    val viewStartMillis = if (firstDrinkAt == null) {
        state.nowMillis - 6 * 60 * 60 * 1000L
    } else {
        minOf(firstDrinkAt - 30 * 60 * 1000L, state.nowMillis - 6 * 60 * 60 * 1000L)
    }
    // Конец окна: минимум 6 часов вперёд, а если прогноз знает, когда BAC упадёт до
    // порога, — ещё плюс 2 часа запаса, чтобы точка «можно за руль» точно попала в кадр.
    // ?: 0L защищает от null: остаток времени может быть неизвестен.
    val viewEndMillis = state.nowMillis + maxOf(
        6 * 60 * 60 * 1000L,
        (state.forecast.remainingMillis ?: 0L) + 2 * 60 * 60 * 1000L,
    )
    // Текст сообщения о добавленной порции. lastAdded?.let выполнит блок только если
    // порция есть; иначе будет null, и сообщение просто не покажется.
    val addedText = lastAdded?.let { drink ->
        stringResource(
            R.string.drink_added,
            // Пересчёт в концентрацию: чистый спирт в граммах делим на объём
            // распределения (литры) — получаем г/л, то есть промилле.
            Formatters.formatValue(drink.ethanolGrams / state.settings.modelParams.vdLiters, units),
        )
    }
    // LaunchedEffect — побочное действие, привязанное к ключу. Блок запустится при
    // первом показе и заново — каждый раз, когда меняется addedText.
    // showSnackbar приостанавливает корутину, пока сообщение висит на экране.
    LaunchedEffect(addedText) {
        addedText?.let { snackbarHostState.showSnackbar(it) }
    }

    // Подпись под шкалой времени: можно ли ехать. Три взаимоисключающих случая.
    val driveText = when {
        // Прогноз говорит, что порог уже не превышен.
        state.forecast.canDriveNow -> stringResource(R.string.timeline_drive_now)
        // Порог превышен, но известно время, когда он станет допустимым:
        // подставляем часы и оставшееся время.
        state.forecast.readyAtMillis != null -> stringResource(
            R.string.timeline_drive_at,
            Formatters.formatClock(state.forecast.readyAtMillis),
            Formatters.formatDuration(state.forecast.remainingMillis ?: 0L),
        )

        // Данных для прогноза нет.
        else -> stringResource(R.string.timeline_drive_unknown)
    }

    // Scaffold — каркас Material-экрана: сам расставляет верхнюю панель, слой
    // сообщений (snackbar) и содержимое. Нам остаётся наполнить слоты.
    Scaffold(
        // Слой всплывающих сообщений внизу экрана.
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // TopAppBar — верхняя панель. API помечен экспериментальным, поэтому
            // над функцией стоит @OptIn(ExperimentalMaterial3Api::class).
            TopAppBar(
                title = {
                    // В заголовке две строки: название приложения и текущая степень.
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(state.level.titleRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // Кнопка настроек — сразу, без меню.
                    IconButton(onClick = onOpenSettings) {
                        // contentDescription обязателен для иконки-кнопки: TalkBack
                        // прочитает его вместо картинки.
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_settings),
                        )
                    }
                    // Box нужен, чтобы меню позиционировалось относительно своей кнопки:
                    // DropdownMenu «привязывается» к ближайшему родителю-якорю.
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.menu_more),
                            )
                        }
                        // expanded читает флаг состояния: true — меню показано.
                        // onDismissRequest закрывает меню при тапе мимо него.
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // Каждый пункт сначала закрывает меню, а затем вызывает
                            // действие — иначе меню осталось бы висеть поверх нового экрана.
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
                // Фон панели совпадает с фоном экрана — визуально панель «растворяется».
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // Лямбда содержимого получает innerPadding — отступы, которые заняли
        // верхняя панель и системные вставки. Их обязательно применять, иначе
        // контент уедет под панель.
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // Прокручиваемая часть экрана: шкала, статус, кнопки напитков.
            // weight(1f) — занять всё свободное место по высоте в родительском Column,
            // verticalScroll — разрешить прокрутку, если содержимое не влезло.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
            ) {
            // Крупная дуговая шкала: ей передаём только данные, рисует она себя сама.
            BacGauge(
                bac = state.bacNow,
                level = state.level,
                threshold = state.settings.thresholdPermille,
                units = units,
            )

            // Легенда к шкале: маленький образец пунктира + подпись значения порога.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Слева отступ 26 dp — выравнивание под начало дуги, снизу 6 dp.
                modifier = Modifier.padding(start = 26.dp, bottom = 6.dp),
            ) {
                // Canvas — элемент разметки, отдающий «холст». Внутри лямбды доступен
                // DrawScope: size (размер в пикселях) и команды рисования.
                Canvas(modifier = Modifier.size(width = 26.dp, height = 8.dp)) {
                    // Горизонтальная линия ровно по середине высоты холста:
                    // от x = 0 до x = ширины, по y = высота / 2.
                    drawLine(
                        color = thresholdColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                        // Пунктир 5 dp / 4 dp — точно такой же, как риска на дуге.
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    )
                }
                // Подпись «порог: 0,3 ‰» (число форматируется в текущих единицах).
                Text(
                    text = stringResource(
                        R.string.timeline_threshold,
                        Formatters.formatBac(state.settings.thresholdPermille, units),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Статусная карточка: сама решает, что написать, по данным state.
            StatusCard(state = state, modifier = Modifier.padding(bottom = 12.dp))

            // Кнопки-пресеты. Колбэки замыкаются на состояние этого экрана:
            // onAdd ещё и даёт тактильный отклик, onEdit/onCustom открывают диалоги.
            DrinkButtons(
                presets = state.settings.presets,
                onAdd = { preset ->
                    // Короткая вибрация подтверждает добавление, даже если смотреть в сторону.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddPreset(preset)
                },
                onEdit = { editingPreset = it },
                onCustom = { customDialog = true },
            )

            // Подсказка для пустой сессии — только когда порций ещё нет.
            if (state.isEmptySession) {
                Text(
                    text = stringResource(R.string.drinks_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Строка о последней порции. Приоритет: только что добавленная (lastAdded),
            // иначе — последняя по времени из состояния. Elvis ?: выбирает первое непустое.
            LastDrinkRow(
                drink = lastAdded ?: state.lastDrink,
                nowMillis = state.nowMillis,
                // Кнопка «Отменить» имеет смысл только для только что добавленной порции.
                canUndo = lastAdded != null,
                onUndo = onUndo,
                modifier = Modifier.padding(top = 10.dp),
            )

            // Spacer — пустой элемент-распорка: создаёт вертикальный зазор 10 dp.
            Spacer(modifier = Modifier.height(10.dp))

                // Заголовок секции шкалы времени.
                Text(
                    text = stringResource(R.string.timeline_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            // Шкала времени вынесена из прокручиваемой колонки: она должна занимать
            // фиксированное место внизу и уметь свой жест прокрутки по времени.
            BacTimeline(
                modifier = Modifier.padding(horizontal = 14.dp),
                // Кривая BAC и порции — готовые данные из ViewModel, здесь не считаются.
                curve = state.curve,
                drinks = state.drinks,
                // Параметры модели нужны, чтобы восстановить кривую при жестах.
                params = state.settings.modelParams,
                threshold = state.settings.thresholdPermille,
                units = units,
                nowMillis = state.nowMillis,
                // Начальное окно по времени: посчитано выше по порциям и прогнозу.
                initialStartMillis = viewStartMillis,
                initialEndMillis = viewEndMillis,
                driveText = driveText,
            )

        }
    }

    // Диалог произвольной порции. Он рисуется поверх экрана, потому что объявлен
    // после Scaffold: порядок в композиции задаёт и порядок слоёв.
    if (customDialog) {
        DrinkDialog(
            title = stringResource(R.string.custom_title),
            // Разумные значения по умолчанию: 50 мл и 40 % — типичная порция крепкого.
            initialVolumeMl = 50.0,
            initialAbvPercent = 40.0,
            initialKind = DrinkKind.CUSTOM,
            initialAtMillis = state.nowMillis,
            // Время разрешено: порцию можно записать «задним числом».
            allowTime = true,
            confirmText = stringResource(R.string.custom_add),
            // Закрытие без сохранения — просто гасим флаг, и диалог исчезает из композиции.
            onDismiss = { customDialog = false },
            onConfirm = { draft ->
                customDialog = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Черновик разворачиваем в аргументы колбэка — наверх уходят
                // только те данные, которые нужны ViewModel.
                onCustomAdd(draft.volumeMl, draft.abvPercent, draft.kind, draft.atMillis)
            },
        )
    }

    // Диалог правки пресета. ?.let выполнит блок только если editingPreset != null,
    // поэтому диалог существует в композиции ровно тогда, когда его открыли.
    editingPreset?.let { preset ->
        DrinkDialog(
            title = stringResource(R.string.settings_edit_preset),
            // Поля заполняются текущими значениями редактируемого пресета.
            initialVolumeMl = preset.volumeMl,
            initialAbvPercent = preset.abvPercent,
            initialKind = preset.kind,
            initialAtMillis = state.nowMillis,
            // В правке пресета время не нужно, а название — нужно.
            allowTime = false,
            allowName = true,
            initialName = preset.name,
            confirmText = stringResource(R.string.save),
            onDismiss = { editingPreset = null },
            onConfirm = { draft ->
                editingPreset = null
                // copy() создаёт копию пресета с изменёнными полями. Так исходный
                // объект не мутируется, а id и прочие поля сохраняются.
                onSavePreset(
                    preset.copy(
                        name = draft.name,
                        volumeMl = draft.volumeMl,
                        abvPercent = draft.abvPercent,
                        kind = draft.kind,
                    ),
                )
            },
        )
    }

    // Подтверждение очистки сессии. AlertDialog — готовое Material-окно с четырьмя
    // слотами: title, text, confirmButton, dismissButton. Ширину и затемнение фона
    // он берёт на себя, поэтому здесь только наполнение.
    if (confirmClear) {
        AlertDialog(
            // Тап мимо окна или системный «назад» — отмена без удаления.
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.menu_clear_session)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                // Необратимое действие: сначала гасим диалог, затем просим ViewModel
                // очистить сессию.
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

/**
 * Строка «последняя порция» с кнопкой отмены.
 *
 * private — функция нужна только этому файлу; снаружи её не видно.
 * @Composable — обычная функция отрисовки, просто маленькая и локальная.
 * @param drink порция для показа; null означает «показывать нечего».
 * @param nowMillis текущее время — от него считается «сколько минут назад».
 * @param canUndo показывать ли кнопку «Отменить».
 * @param onUndo действие отмены последнего добавления.
 * @param modifier модификатор от родителя (сюда приходит внешний отступ).
 */
@Composable
private fun LastDrinkRow(
    drink: Drink?,
    nowMillis: Long,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ранний выход: если порции нет, функция ничего не добавляет в композицию —
    // это нормальный способ «не показывать» элемент, без пустых заглушек.
    if (drink == null) return
    // Карточка-строка: на всю ширину, скруглённая, с полупрозрачным фоном
    // (40 % surfaceVariant), внутри горизонтальные 12 dp и вертикальные 8 dp.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // SpaceBetween прижимает текст влево, а кнопку «Отменить» — вправо.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Левая группа: иконка + текст. weight(1f) забирает всё свободное место,
        // чтобы длинный текст не выдавил кнопку за край.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // painterResource здесь записан полным именем, потому что импорт не добавлен.
            Icon(
                painter = androidx.compose.ui.res.painterResource(drink.kind.iconRes()),
                // Иконка декоративная: рядом есть текст с тем же смыслом.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp),
            )
            Column {
                // Одна строка-шаблон: «N назад — вид, объём, крепость».
                // formatAge считает разницу между временем порции и «сейчас»,
                // остальные formatVolume подставляют числа в текущих единицах.
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
        // Кнопка отмены есть только у только что добавленной порции.
        if (canUndo) {
            TextButton(onClick = onUndo) {
                Text(stringResource(R.string.undo), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
