package ru.alkometr.app.ui.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.DrinkPreset

/**
 * Иконка сосуда по виду напитка.
 *
 * Extension-функция на enum [DrinkKind]: вызывается как `preset.kind.iconRes()`.
 * `when (this)` по всем константам перечисления — исчерпывающий, поэтому новая
 * константа сразу даст ошибку компиляции, а не «пустую» иконку в рантайме.
 * Возвращается Int — идентификатор drawable-ресурса.
 */
fun DrinkKind.iconRes(): Int = when (this) {
    // Стопка, бокал, кружка, «свой» вариант — четыре заранее нарисованных иконки.
    DrinkKind.SHOT -> R.drawable.ic_shot
    DrinkKind.WINE_GLASS -> R.drawable.ic_wine
    DrinkKind.MUG -> R.drawable.ic_mug
    DrinkKind.CUSTOM -> R.drawable.ic_custom
}

/**
 * Та же схема, что в [iconRes], но для подписи вида напитка.
 * Текст хранится в строковых ресурсах — так его можно перевести и поправить без кода.
 */
fun DrinkKind.titleRes(): Int = when (this) {
    DrinkKind.SHOT -> R.string.drink_shot
    DrinkKind.WINE_GLASS -> R.string.drink_wine
    DrinkKind.MUG -> R.string.drink_mug
    DrinkKind.CUSTOM -> R.string.drink_custom
}

/**
 * Кнопки напитков: тап — добавить порцию сейчас, долгое нажатие — правка пресета.
 * Кнопка «+ Свой» открывает диалог с объёмом, крепостью и временем.
 *
 * @Composable — функция описывает часть экрана; она ничего не хранит, а получает
 * список пресетов и три колбэка. Колбэки вместо прямой записи в состояние —
 * стандартный приём: экран остаётся «тупым» и легко тестируется.
 * @param presets список кнопок-пресетов из настроек пользователя.
 * @param onAdd нажатие на пресет: добавить порцию.
 * @param onEdit долгое нажатие: открыть диалог правки этого пресета.
 * @param onCustom нажатие на «+ Свой»: открыть диалог произвольной порции.
 * @param modifier модификатор от родителя (обычно отступы).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrinkButtons(
    presets: List<DrinkPreset>,
    onAdd: (DrinkPreset) -> Unit,
    onEdit: (DrinkPreset) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Row выкладывает детей в строку. fillMaxWidth — на всю ширину экрана,
    // horizontalScroll — если кнопок больше, чем помещается, ряд прокручивается
    // пальцем по горизонтали (rememberScrollState хранит позицию прокрутки
    // между рекомпозициями). spacedBy(8.dp) — одинаковый зазор между карточками.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Рисуем по одной карточке на каждый пресет. Список небольшой и его порядок
        // задаёт пользователь, поэтому стабильные key здесь не обязательны.
        presets.forEach { preset ->
            // Column — карточка пресета. Сначала размеры (90×88 dp), затем обрезка
            // скруглением, полупрозрачный фон, обработчик нажатий и внутренние отступы.
            Column(
                modifier = Modifier
                    .width(90.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    // combinedClickable — «двойной» обработчик: обычный тап и долгое
                    // нажатие. Он экспериментальный в foundation, поэтому над функцией
                    // стоит @OptIn(ExperimentalFoundationApi::class).
                    .combinedClickable(
                        onClick = { onAdd(preset) },
                        onLongClick = { onEdit(preset) },
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Иконка сосуда декоративная: смысл дублируется подписью ниже,
                // поэтому contentDescription = null и TalkBack её пропустит.
                Icon(
                    painter = painterResource(preset.kind.iconRes()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
                // Название: пользовательское имя, если оно непустое, иначе —
                // стандартное название вида напитка. takeIf отсекает строку из пробелов,
                // elvis подставляет запасной вариант.
                Text(
                    text = preset.name?.takeIf { it.isNotBlank() } ?: stringResource(preset.kind.titleRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Одна строка и многоточие: длинное имя не разъедет карточку.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // Техническая подпись «объём / крепость» в текущих единицах измерения.
                Text(
                    text = stringResource(
                        R.string.drink_caption,
                        Formatters.formatVolume(preset.volumeMl),
                        Formatters.formatVolume(preset.abvPercent),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Последняя карточка — не пресет, а вход в диалог произвольной порции.
        // Она чуть уже (80 dp) и бледнее (35 % вместо 60 %), чтобы визуально
        // не спорить с настоящими пресетами.
        Column(
            modifier = Modifier
                .width(80.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                // Правка пресета здесь не имеет смысла, поэтому и тап, и долгое
                // нажатие ведут в один и тот же диалог.
                .combinedClickable(onClick = onCustom, onLongClick = onCustom)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Крупный «+» — визуальный акцент кнопки добавления.
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // Пояснение под плюсом; текст по центру, чтобы влез в узкую карточку.
            Text(
                text = stringResource(R.string.drink_custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
