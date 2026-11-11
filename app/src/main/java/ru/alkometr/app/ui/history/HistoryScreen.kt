package ru.alkometr.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.ui.main.components.DrinkDialog
import ru.alkometr.app.ui.main.components.iconRes
import ru.alkometr.app.ui.main.components.titleRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * История за последние 7 дней: свайп по порции удаляет её.
 * Кнопка «Добавить за прошлое время» открывает тот же диалог порции.
 *
 * Экран «глупый»: он не хранит данные и не меняет их сам, а получает список и колбэки.
 * Всё состояние живёт в MainViewModel — так экран легко переиспользовать и тестировать.
 */
@OptIn(ExperimentalMaterial3Api::class) // OptIn разрешает пользоваться экспериментальным API Material 3
// (TopAppBar и SwipeToDismissBox помечены @ExperimentalMaterial3Api): их сигнатуры могут измениться
// в будущих версиях, поэтому компилятор требует явного согласия.
@Composable
fun HistoryScreen(
    drinks: List<Drink>, // Все выпитые порции — входные данные, экран их не копирует и не хранит.
    nowMillis: Long, // Текущее время в миллисекундах эпохи: нужно для границы «неделя» и для диалога.
    onDelete: (String) -> Unit, // Колбэк «удалить по id»: экран не трогает хранилище сам.
    onAddPast: (Double, Double, DrinkKind, Long) -> Unit, // Колбэк добавления порции задним числом:
    // объём, крепость, вид напитка, время.
    onBack: () -> Unit, // Колбэк «назад»: экран не знает, кто его открыл, — просто сообщает наверх.
) {
    var addDialog by remember { mutableStateOf(false) } // Локальное состояние UI: открыт ли диалог добавления.
    // Хранить его во ViewModel не нужно: после поворота экрана диалог просто закроется.
    val zone = ZoneId.systemDefault() // Часовой пояс устройства: без него нельзя перейти от момента
    // времени к календарной дате, а значит и сгруппировать записи по дням.
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate() // «Сейчас» в виде даты —
    // по ней определяем подписи «Сегодня» и «Вчера».
    val weekAgo = nowMillis - 7L * 24 * 60 * 60 * 1000 // Граница «неделю назад»: 7 суток, переведённые
    // в миллисекунды. 7L — тип Long, иначе умножение больших чисел переполнило бы Int.

    val visible = drinks // Цепочка преобразований списка — обычный Kotlin, без магии Compose.
        .filter { it.atMillis >= weekAgo } // 1) Отбрасываем всё, что старше недели.
        .sortedByDescending { it.atMillis } // 2) Сортируем по времени: свежие сверху.
        .groupBy { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() } // 3) Группируем по дате:
    // получается LinkedHashMap «дата → список порций», и порядок дней сохраняется от свежих к старым.

    Scaffold( // Scaffold — каркас экрана Material: раскладывает верхнюю панель и содержимое
        // и выдаёт через padding отступ, равный высоте панели.
        topBar = { // Слот верхней панели.
            TopAppBar( // TopAppBar — стандартная панель заголовка.
                title = { Text(stringResource(R.string.history_title)) }, // Заголовок экрана из ресурсов.
                navigationIcon = { // Слот левой иконки — обычно «назад».
                    IconButton(onClick = onBack) { // IconButton даёт круговую область нажатия и ripple.
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) // contentDescription
                        // обязательно: озвучивается TalkBack — по нему незрячий понимает смысл иконки.
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors( // Переопределяем цвета панели по умолчанию.
                    containerColor = MaterialTheme.colorScheme.background, // Фон панели = фон экрана,
                    // поэтому панель не выглядит отдельной полосой другого цвета.
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background, // Фон всего каркаса — тоже из темы.
    ) { padding -> // padding — внутренние отступы, которые Scaffold зарезервировал под панель.
        Column(
            modifier = Modifier
                .padding(padding) // Сдвигаем содержимое ниже панели, иначе оно уйдёт под неё.
                .fillMaxSize() // Колонка занимает весь экран.
                .verticalScroll(rememberScrollState()) // Вся страница прокручивается целиком;
                // rememberScrollState хранит позицию прокрутки между рекомпозициями.
                .padding(horizontal = 14.dp, vertical = 8.dp), // Внутренние поля: 14 dp по бокам, 8 dp сверху/снизу.
            verticalArrangement = Arrangement.spacedBy(8.dp), // Одинаковый зазор 8 dp между всеми элементами.
        ) {
            Button(
                onClick = { addDialog = true }, // Нажатие лишь выставляет флаг — данные меняет ViewModel.
                modifier = Modifier.fillMaxWidth(), // Кнопка растянута на всю ширину — главное действие экрана.
            ) {
                Text(stringResource(R.string.history_add_past)) // Подпись «Добавить за прошлое время».
            }

            if (visible.isEmpty()) { // Пустое состояние: без него пользователь видел бы просто пустой экран.
                Text(
                    text = stringResource(R.string.history_empty), // Поясняющий текст «пока ничего нет».
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Приглушённый цвет: это подсказка, не данные.
                    modifier = Modifier.padding(top = 12.dp), // Отступ от кнопки, чтобы текст не прилипал.
                )
            } else {
                visible.forEach { (date, list) -> // Деструктуризация пары «ключ → значение» из Map:
                    // date — день, list — порции этого дня.
                    Text(
                        text = dayTitle(date, today), // «Сегодня» / «Вчера» / дата — считаем в dayTitle.
                        style = MaterialTheme.typography.labelLarge, // Стиль метки группы: мельче заголовка, но заметный.
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // Второстепенный текст.
                        modifier = Modifier.padding(top = 6.dp), // Небольшой отступ от предыдущей группы.
                    )
                    list.forEach { drink -> // Порции внутри одного дня.
                        HistoryRow(drink = drink, onDelete = { onDelete(drink.id) }) // Колбэк замыкает id
                        // конкретной порции: сама строка не знает, как её удалять.
                    }
                }
            }

            Text(
                text = stringResource(R.string.history_range_hint), // Пояснение «храним 7 дней».
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp), // Отступ от последней группы записей.
            )
        }
    }

    if (addDialog) { // Диалог рисуется поверх Scaffold, поэтому он вне его лямбды.
        DrinkDialog( // Тот же диалог, что и на главном экране, — переиспользование компонента.
            title = stringResource(R.string.history_add_past), // Заголовок объясняет режим «задним числом».
            initialVolumeMl = 50.0, // Значение по умолчанию: 50 мл — типичная порция крепкого напитка.
            initialAbvPercent = 40.0, // 40 % — типичная крепость водки.
            initialKind = DrinkKind.SHOT, // Вид напитка по умолчанию — «стопка».
            initialAtMillis = nowMillis, // Время по умолчанию — текущий момент, его можно сдвинуть в прошлое.
            allowTime = true, // Разрешаем менять время: именно за этим сюда и приходят.
            confirmText = stringResource(R.string.custom_add), // Подпись кнопки подтверждения.
            onDismiss = { addDialog = false }, // Закрытие без сохранения — просто сбрасываем флаг.
            onConfirm = { draft -> // draft — черновик порции, который собрал диалог.
                addDialog = false // Сначала закрываем диалог, чтобы он не «мигал».
                onAddPast(draft.volumeMl, draft.abvPercent, draft.kind, draft.atMillis) // И отдаём данные наверх.
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Свайп-контейнер тоже экспериментальный API Material 3.
@Composable
private fun HistoryRow(drink: Drink, onDelete: () -> Unit) { // Одна строка истории; private — деталь экрана.
    val dismissState = rememberSwipeToDismissBoxState( // Состояние свайпа: помнит смещение строки,
        // а также умеет «доводить» жест до конца или возвращать строку назад.
        confirmValueChange = { value -> // Колбэк-подтверждение: спрашивают разрешения перейти в новое состояние.
            if (value == SwipeToDismissBoxValue.EndToStart) { // EndToStart = свайп справа налево
                // (в RTL-языках направление зеркалится автоматически).
                onDelete() // Сначала просим ViewModel удалить запись.
                true // true — разрешаем переход: строка уезжает, но она тут же исчезнет из списка.
            } else {
                false // Любое другое состояние (например, возврат в Settled) запрещаем —
                // строка остаётся на месте.
            }
        },
    )
    SwipeToDismissBox( // Контейнер, который двигает содержимое пальцем и показывает фон под ним.
        state = dismissState, // Состояние из remember выше — так Compose знает смещение.
        enableDismissFromStartToEnd = false, // Запрещаем свайп слева направо: удаление только в одну сторону,
        // чтобы случайное движение в обратную сторону ничего не ломало.
        backgroundContent = { // Что видно под уезжающей строкой.
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Фон на всю ширину строки.
                    .clip(RoundedCornerShape(12.dp)) // Скругляем углы так же, как у самой строки.
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f)) // Полупрозрачный красный:
                    // копия цвета error с alpha 0.25 — намёк на опасность, но без крика.
                    .padding(horizontal = 16.dp, vertical = 14.dp), // Отступы под текст подсказки.
                horizontalArrangement = Arrangement.End, // Прижимаем текст вправо — туда, куда ведёт свайп.
            ) {
                Text(
                    text = stringResource(R.string.history_delete), // Подпись «Удалить».
                    color = MaterialTheme.colorScheme.error, // Насыщенный красный текст на бледном фоне.
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        Row( // Сама строка: иконка напитка + две строки текста.
            modifier = Modifier
                .fillMaxWidth() // Строка занимает всю ширину списка.
                .clip(RoundedCornerShape(12.dp)) // Скруглённые углы карточки.
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) // Полупрозрачная
                // поверхность: строка мягко выделяется на фоне, но не выглядит «тяжёлой».
                .padding(horizontal = 12.dp, vertical = 10.dp), // Внутренние поля строки.
            verticalAlignment = Alignment.CenterVertically, // Иконка и текст выровнены по центру по вертикали.
        ) {
            Icon(
                painter = painterResource(drink.kind.iconRes()), // Картинка из ресурсов по виду напитка.
                contentDescription = null, // null осознанно: иконка декоративная, вид напитка уже есть в тексте,
                // иначе TalkBack зачитал бы его дважды.
                tint = MaterialTheme.colorScheme.primary, // Красим иконку в акцентный цвет темы.
                modifier = Modifier.padding(end = 10.dp), // Отступ между иконкой и текстом.
            )
            Column(modifier = Modifier.weight(1f)) { // weight(1f) — колонка забирает всё оставшееся место,
                // поэтому длинный текст переносится, а не выдавливает иконку.
                Text(
                    text = "${Formatters.formatClock(drink.atMillis)} · ${stringResource(drink.kind.titleRes())}", // Время
                    // и название напитка одной строкой; · — визуальный разделитель.
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource( // Вторая строка собирается из двух шаблонов ресурсов.
                        R.string.drink_caption, // Шаблон «объём и крепость» с двумя подстановками.
                        Formatters.formatVolume(drink.volumeMl), // Объём, уже с единицами измерения.
                        Formatters.formatVolume(drink.abvPercent), // Крепость форматируется тем же помощником.
                    ) + " · " + stringResource( // Склеиваем части обычным «+» — получается одна строка.
                        R.string.history_item_grams, // Шаблон «столько-то граммов этанола».
                        Formatters.formatVolume(drink.ethanolGrams), // Масса чистого спирта в порции.
                    ),
                    style = MaterialTheme.typography.bodySmall, // Мелкий второстепенный текст.
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Приглушённый цвет.
                )
            }
        }
    }
}

@Composable
private fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) { // when-выражение возвращает
    // строку: ветки сравниваются по очереди.
    today -> stringResource(R.string.history_today) // Сегодняшняя дата — подпись «Сегодня».
    today.minusDays(1) -> stringResource(R.string.history_yesterday) // Предыдущий день — «Вчера».
    else -> Formatters.formatDate( // Остальные дни — обычная дата.
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), // Formatters работает с миллисекундами,
        // поэтому начало дня переводим в момент времени: atStartOfDay → Instant → epoch millis.
    )
}
