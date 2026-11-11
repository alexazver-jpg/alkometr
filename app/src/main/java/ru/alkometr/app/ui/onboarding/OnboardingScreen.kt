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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
 *
 * Экран работает иначе, чем настройки: он не сохраняет каждое нажатие, а собирает
 * всё локально и отдаёт результат один раз, в [onFinish]. Так сделано специально —
 * до конца мастер-настройки настройки в хранилище не нужны, а частично заполненный
 * профиль только путал бы расчёт.
 *
 * @param initial настройки на момент старта: из них берутся значения по умолчанию,
 *   поэтому повторный запуск мастера начинается с уже введённых чисел.
 * @param onFinish вызывается один раз — с готовыми настройками и флагом onboardingDone.
 */
@Composable
fun OnboardingScreen(
    initial: AppSettings,
    onFinish: (AppSettings) -> Unit,
) {
    // Номер текущего шага: 0 — вес, 1 — пол, 2 — порог, 3 — дисклеймер.
    // mutableIntStateOf хранит Int без боксинга; remember переживает рекомпозицию.
    var step by remember { mutableIntStateOf(0) }
    // Вес живёт в двух видах: числом (для Slider) и текстом (для поля ввода).
    // Слайдеру нужен Float, текстовому полю — строка, и оба должны совпадать.
    var weight by remember { mutableFloatStateOf(initial.profile.weightKg.toFloat()) }
    var weightText by remember { mutableStateOf(Formatters.formatVolume(initial.profile.weightKg)) }
    // Пол: null означает «ещё не выбрано». Это отдельное состояние от значения в профиле,
    // потому что мастеру нужно знать, ответил ли пользователь, — иначе кнопку «Далее» не заблокировать.
    var sex by remember { mutableStateOf(initial.profile.sex) }
    // Порог тоже в двух видах: число для логики, текст для поля. Порог — Double,
    // поэтому здесь mutableStateOf, а не mutableFloatStateOf.
    var threshold by remember { mutableStateOf(initial.thresholdPermille) }
    var thresholdText by remember { mutableStateOf(Formatters.formatVolume(initial.thresholdPermille)) }

    // Валидация считается на каждой рекомпозиции — это дёшево и всегда актуально.
    // Запятая меняется на точку: toDoubleOrNull понимает только точку.
    val weightValue = weightText.replace(',', '.').toDoubleOrNull()
    // weightValid — единственное условие, по которому включается кнопка «Далее» на шаге 0.
    val weightValid = weightValue != null && weightValue in 30.0..200.0
    val othersValid = sex != null
    val thresholdValue = thresholdText.replace(',', '.').toDoubleOrNull()
    val thresholdValid = thresholdValue != null && thresholdValue in 0.0..0.8

    // Локальная функция завершения мастера: вызывается и кнопкой «Пропустить», и кнопкой
    // на последнем шаге, поэтому логика вынесена в одно место.
    fun finish() {
        // Если ввод некорректен, берём значение из initial — молча оставляем прежним,
        // а не сохраняем мусор и не роняем приложение.
        val profile = initial.profile.copy(
            weightKg = if (weightValid) weightValue!! else initial.profile.weightKg,
            sex = sex,
        )
        val thresholdPermille = if (thresholdValid) thresholdValue!! else initial.thresholdPermille
        onFinish(
            // copy создаёт новую неизменяемую копию настроек: старый объект не меняется,
            // поэтому Compose видит новое состояние и перерисовывает экраны.
            initial.copy(
                profile = profile,
                thresholdPermille = thresholdPermille,
                onboardingDone = true,  // флаг: в следующий раз приложение откроется сразу на главном экране
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // systemBarsPadding — отступ под строку статуса и навигационную полосу:
            // без него текст уезжал бы под системные элементы.
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            // onboarding_step — шаблон «Шаг %1$d из %2$d». step + 1 нужен, потому что
            // внутри счёт начинается с нуля, а человеку показываем с единицы.
            text = stringResource(R.string.onboarding_step, step + 1, 4),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Линейный индикатор прогресса: (step + 1) / 4f — доля пройденных шагов от 0 до 1.
        // progress — лямбда, поэтому пересчитывается только при изменении step.
        LinearProgressIndicator(
            progress = { (step + 1) / 4f },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),  // светлая дорожка под заливкой
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(4.dp)),  // скругляем прямые концы индикатора
        )

        // weight(1f) — контент занимает всё место между индикатором и кнопками,
        // поэтому кнопки всегда прижаты к низу экрана.
        // verticalScroll спасает на маленьких экранах и при крупном системном шрифте.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp),
        ) {
            // when(step) — «переключатель экранов»: рисуем ровно один шаг.
            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_weight_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_weight_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,  // пояснение приглушённым цветом
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // Слайдер и поле ниже правят одно и то же значение: слайдер задаёт грубо,
                    // поле — точно. При изменении одного второе обновляется, потому что
                    // источник правды один — переменная weight / weightText.
                    Slider(
                        colors = brandSliderColors(),
                        value = weight,
                        onValueChange = {
                            weight = it  // число для логики
                            // Округляем до целого: шаг слайдера — 1 кг, дробные килограммы не нужны.
                            weightText = it.roundToInt().toString()
                        },
                        valueRange = 30f..200f,  // те же границы, что и в валидации ниже
                        steps = 169,  // (200 - 30) - 1 = 169 промежуточных остановок — ровно по 1 кг
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { text ->
                            // Фильтр «только цифры, запятая и точка»: буквы и минус не попадут в поле,
                            // поэтому пользователь не сможет ввести заведомо неверное значение.
                            weightText = text.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                            // Слайдер двигаем только на корректном числе в диапазоне:
                            // иначе он дёргался бы на промежуточном вводе вроде «1,».
                            weightText.replace(',', '.').toDoubleOrNull()?.let {
                                if (it in 30.0..200.0) weight = it.toFloat()
                            }
                        },
                        label = { Text(stringResource(R.string.settings_weight)) },
                        // isError подсвечивает поле и рамку красным, пока значение невалидно.
                        isError = !weightValid,
                        // supportingText показывается только при ошибке: подсказка «зачем» уже есть выше,
                        // поэтому пустое место под полем не занимаем.
                        supportingText = if (!weightValid) {
                            { Text(stringResource(R.string.error_weight)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,  // многострочное поле здесь только мешало бы
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
                    // Пол — три большие карточки вместо чипов: на первом запуске важнее
                    // крупная цель для пальца, чем компактность.
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        // SexOption — локальный компонент ниже. Второй аргумент — подпись,
                        // третий — выбран ли вариант, четвёртый — что сделать при нажатии.
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
                        // Готовые пороги стран: пользователю проще нажать «Россия»,
                        // чем вспоминать значение в промилле.
                        ThresholdChip(ThresholdPreset.RUSSIA, stringResource(R.string.settings_threshold_russia), threshold) {
                            // Выбор чипа меняет и число, и текст в поле — они не должны расходиться.
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
                                // Тот же фильтр, что и для веса: цифры и разделитель, ничего больше.
                                thresholdText = text.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                                // Порог допускает 0.0 (полный запрет), поэтому диапазон начинается с нуля.
                                thresholdText.replace(',', '.').toDoubleOrNull()?.let {
                                    if (it in 0.0..0.8) threshold = it
                                }
                            },
                            label = { Text(stringResource(R.string.settings_threshold_value)) },
                            // Красная подсветка без supportingText: место под текст здесь экономит высоту.
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
                    // Четвёртый шаг — только текст: дисклеймер, что приложение не заменяет алкотестер.
                    Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.disclaimer_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,  // здесь обычный цвет: текст надо прочитать, а не пробежать
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // Пустой распорный блок: гарантирует зазор между прокручиваемым текстом и кнопками,
        // даже если текст короткий.
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,  // «Пропустить» слева, основная кнопка справа
        ) {
            // Пропуск доступен всегда: мастер не должен запирать пользователя.
            // finish() сохранит то, что уже введено, и значения по умолчанию для остального.
            TextButton(onClick = { finish() }) {
                Text(stringResource(R.string.onboarding_skip))
            }
            Button(
                onClick = {
                    // До последнего шага кнопка листает дальше, на последнем — завершает мастер.
                    if (step < 3) step++ else finish()
                },
                // enabled управляет и кликабельностью, и внешним видом (серая неактивная кнопка).
                // Проверка зависит от шага: невалидный ввод нельзя протащить дальше.
                enabled = when (step) {
                    0 -> weightValid
                    1 -> othersValid
                    2 -> thresholdValid
                    else -> true  // дисклеймер читать нечего проверять
                },
            ) {
                Text(
                    // На последнем шаге кнопка меняет смысл и подпись: «Начать» вместо «Далее».
                    stringResource(
                        if (step == 3) R.string.onboarding_start else R.string.onboarding_next,
                    ),
                )
            }
        }
    }
}

/**
 * Большая карточка-вариант для выбора пола.
 * @param sex значение, которое представляет карточка (нужно, чтобы показать подсказку для UNSPECIFIED).
 * @param title подпись варианта.
 * @param selected выбран ли он сейчас — от этого зависит фон.
 * @param onSelect что сделать при нажатии; сам компонент состояние не хранит.
 */
@Composable
private fun SexOption(sex: Sex, title: String, selected: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)  // зазор между карточками
            .clip(RoundedCornerShape(14.dp))
            // Цвет фона — единственный индикатор выбора: залитый primary для выбранного,
            // приглушённый surfaceVariant для остальных.
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            // clickable добавляет и обработчик нажатия, и волну-риппл, и доступность для TalkBack.
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (sex == Sex.UNSPECIFIED) {
            // Поясняем, что будет, если не указывать пол: расчёт пойдёт по средним значениям,
            // и точность снизится. Это влияет на решение пользователя, поэтому текст прямо здесь.
            Text(
                stringResource(R.string.settings_sex_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Чип готового порога: показывает вариант и сообщает выбранное значение в промилле.
 * @param preset пресет страны/режима, из него берётся число.
 * @param current текущий порог — нужен, чтобы понять, выбран ли этот чип.
 * @param onSelect отдаёт значение пресета наверх.
 */
@Composable
private fun ThresholdChip(preset: ThresholdPreset, title: String, current: Double, onSelect: (Double) -> Unit) {
    // Row на один элемент: даёт вертикальный отступ и оставляет возможность
    // позже добавить рядом пояснение, не переделывая разметку.
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        FilterChip(
            colors = brandChipColors(),
            // Сравнение с допуском: у Double 0.3 после пересчётов может оказаться 0.30000000000000004,
            // и точное == не совпало бы никогда.
            selected = kotlin.math.abs(current - preset.permille) < 0.001,
            onClick = { onSelect(preset.permille) },
            label = { Text(title) },
        )
    }
}

/**
 * Цвета FilterChip под бренд: обычный чип выглядит как фон, выбранный — как светлая
 * заливка основного цвета. Так чипы не спорят с крупными заголовками шагов.
 */
@Composable
private fun brandChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Цвета Slider под бренд: ползунок и активная дорожка — основным цветом,
 * неактивная часть — тем же цветом с прозрачностью.
 */
@Composable
private fun brandSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
)
