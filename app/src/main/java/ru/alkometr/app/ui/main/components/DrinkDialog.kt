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

/**
 * То, что пользователь ввёл в диалоге порции.
 *
 * Обычный data class — не UI-состояние. Диалог собирает такой объект и отдаёт
 * наружу одним колбэком [onConfirm], а что с ним делать, решает вызывающий экран.
 *
 * @param name название пресета; null — «без имени», для разовой порции не нужно.
 * @param volumeMl объём порции в миллилитрах.
 * @param abvPercent крепость в процентах объёмных (0,1…96).
 * @param kind вид сосуда — влияет на иконку и на стандартную подпись.
 * @param atMillis время употребления, миллисекунды Unix-эпохи.
 */
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
 *
 * @Composable — диалог тоже всего лишь функция, описывающая UI: он рисуется поверх
 * экрана, но живёт в том же дереве композиции и подчиняется тем же правилам.
 * @param title заголовок окна (разный для «своей» порции и правки пресета).
 * @param initialVolumeMl начальный объём — с него заполняется поле ввода.
 * @param initialAbvPercent начальная крепость.
 * @param initialKind заранее выбранный вид сосуда.
 * @param initialAtMillis начальное время порции.
 * @param allowTime показывать ли выбор времени (в правке пресета время не нужно).
 * @param confirmText подпись кнопки подтверждения.
 * @param onDismiss закрытие без сохранения (тап мимо окна, кнопка «Отмена», back).
 * @param allowName показывать ли поле названия — только в режиме пресета.
 * @param initialName начальное название пресета, если оно есть.
 * @param onConfirm подтверждение: отдаёт наверх собранный [DrinkDraft].
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
    // Локальное состояние полей ввода. Три вещи сразу:
    // remember — сохранить значение между рекомпозициями (иначе оно сбрасывалось бы
    //   на каждом пересчёте экрана, и ввод «стирался» бы у пользователя под пальцами);
    // mutableStateOf — наблюдаемая ячейка: Compose перерисует зависимые TextField,
    //   когда в неё запишут новое значение;
    // by — делегирование свойства, чтобы работать с ним как с обычной переменной.
    // Начальные значения берутся из параметров только при первом показе диалога.
    var volumeText by remember { mutableStateOf(Formatters.formatVolume(initialVolumeMl)) }
    var abvText by remember { mutableStateOf(Formatters.formatVolume(initialAbvPercent)) }
    // orEmpty() превращает возможный null в пустую строку — TextField null не принимает.
    var nameText by remember { mutableStateOf(initialName.orEmpty()) }
    var kind by remember { mutableStateOf(initialKind) }
    var atMillis by remember { mutableStateOf(initialAtMillis) }

    // Разбор текста в число. В русской локали дробную часть пишут через запятую,
    // а toDouble() понимает только точку, поэтому запятую заранее заменяем.
    // toDoubleOrNull возвращает null вместо исключения — это удобно для валидации.
    val volume = volumeText.replace(',', '.').toDoubleOrNull()
    val abv = abvText.replace(',', '.').toDoubleOrNull()
    // Проверка границ: объём от 10 до 2000 мл, крепость от 0,1 до 96 %.
    // `volume == null` покрывает и пустое поле, и мусор вроде «12,3,4».
    val volumeError = volume == null || volume !in 10.0..2000.0
    val abvError = abv == null || abv !in 0.1..96.0
    // Кнопка подтверждения активна только когда обе проверки пройдены.
    val valid = !volumeError && !abvError

    // LocalContext — текущий Android Context из ближайшего окружения композиции.
    // Он нужен системным диалогам выбора даты и времени, которые не умеют Compose.
    val context = LocalContext.current

    // Локальная функция: сначала системный выбор даты, затем сразу выбор времени.
    // Календарь создаём от текущего значения, чтобы открыть пикеры на нём, а не «сегодня».
    fun pickDateTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = atMillis }
        // DatePickerDialog принимает контекст, колбэк (view, год, месяц, день)
        // и начальные значения. Месяц в Calendar считается с нуля — так и передаём.
        DatePickerDialog(
            context,
            { _, year, month, day ->
                // Пользователь выбрал дату — записываем её в календарь…
                calendar.set(year, month, day)
                // …и сразу спрашиваем время. Два шага, потому что отдельного
                // «дата+время» пикера в Android нет.
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        // Время в 24-часовом виде — так его и сохраняем.
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        // Готово: пишем результат в наблюдаемое состояние,
                        // Compose перерисует подпись с датой рядом с кнопкой.
                        atMillis = calendar.timeInMillis
                    },
                    // Начальные часы/минуты для пикера времени.
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    // true — 24-часовой формат.
                    true,
                ).show()
            },
            // Начальная дата для пикера.
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    // AlertDialog — готовый Material-диалог с четырьмя слотами:
    // title (заголовок), text (содержимое), confirmButton и dismissButton.
    // Ширину и позиционирование он берёт на себя, нам остаётся наполнить слоты.
    AlertDialog(
        // Вызывается при закрытии «мимо» окна или системной кнопкой «назад».
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Column — вертикальный список полей с одинаковым зазором 10 dp.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Поле названия есть только в режиме правки пресета.
                if (allowName) {
                    // OutlinedTextField — поле ввода с рамкой. value/onValueChange —
                    // классическая пара «значение + что делать при вводе»: состояние
                    // хранится снаружи поля, поэтому ввод предсказуем и проверяем.
                    OutlinedTextField(
                        value = nameText,
                        // take(40) — жёсткий лимит длины, чтобы имя влезло в кнопку.
                        onValueChange = { nameText = it.take(40) },
                        // label «плавает» на рамке и подсказывает смысл поля.
                        label = { Text(stringResource(R.string.settings_preset_name)) },
                        // Одна строка: Enter не создаёт переносов.
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Ряд чипов-переключателей вида сосуда.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Берём все виды, кроме CUSTOM: «свой» — не сосуд, а отсутствие
                    // готового варианта, выбирать его в диалоге бессмысленно.
                    DrinkKind.entries.filter { it != DrinkKind.CUSTOM }.forEach { option ->
                        // FilterChip — компактная «таблетка»-переключатель. selected
                        // отвечает за подсветку, onClick меняет выбор. Состояние снова
                        // живёт снаружи чипа, в переменной kind.
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(option.titleRes())) },
                        )
                    }
                }
                // Поле объёма. Ввод фильтруем посимвольно: разрешены только цифры,
                // запятая и точка — остальное просто не попадает в текст.
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text(stringResource(R.string.custom_volume)) },
                    // isError красит рамку и подпись в цвет ошибки.
                    isError = volumeError,
                    // supportingText — строка под полем: показываем её только при ошибке,
                    // иначе null, и место под ней не занимается.
                    supportingText = if (volumeError) {
                        { Text(stringResource(R.string.error_volume)) }
                    } else {
                        null
                    },
                    // Просим у клавиатуры цифровой блок с разделителем дробной части.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Поле крепости устроено точно так же, как поле объёма:
                // тот же фильтр символов, та же проверка и та же подсказка об ошибке.
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
                // Блок времени нужен только для разовой порции «задним числом».
                if (allowTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        // SpaceBetween разводит подпись влево, а кнопки — вправо.
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.custom_time),
                            style = MaterialTheme.typography.bodyMedium,
                            // Небольшой верхний отступ, чтобы подпись встала по центру
                            // двух кнопок справа.
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Row {
                            // Быстрый вариант — «сейчас»: одно нажатие вместо двух пикеров.
                            TextButton(onClick = { atMillis = System.currentTimeMillis() }) {
                                Text(stringResource(R.string.custom_time_now))
                            }
                            // Кнопка с текущим временем в подписи: открывает выбор даты и времени.
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
                    // Второй барьер после enabled: не собираем черновик из невалидных данных.
                    if (valid) {
                        onConfirm(
                            DrinkDraft(
                                // trim() убирает случайные пробелы, takeIf отсекает пустое имя.
                                name = nameText.trim().takeIf { it.isNotEmpty() },
                                // !! безопасен: valid уже гарантирует, что оба числа не null.
                                volumeMl = volume!!,
                                abvPercent = abv!!,
                                kind = kind,
                                atMillis = atMillis,
                            ),
                        )
                    }
                },
                // enabled = false делает кнопку серой и некликабельной — понятная
                // подсказка пользователю, что в форме есть ошибка.
                enabled = valid,
            ) {
                Text(confirmText)
            }
        },
        // «Отмена» просто закрывает диалог, ничего не сохраняя.
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
