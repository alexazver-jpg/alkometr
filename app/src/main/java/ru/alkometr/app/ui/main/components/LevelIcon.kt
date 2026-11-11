package ru.alkometr.app.ui.main.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.model.IntoxicationLevel

/**
 * Сопоставление степени опьянения и собственной иконки-персонажа.
 *
 * Это extension-функция (расширение) на enum [IntoxicationLevel]: снаружи её вызывают
 * как обычный метод — `level.iconRes()`. Так UI-знания (какой drawable рисовать)
 * не попадают в модель, но и не размазываются по экранам.
 *
 * Внутри — `when (this)`, где `this` и есть конкретная степень. Для enum Kotlin
 * требует перечислить все константы (исчерпывающий `when`), поэтому забыть степень
 * нельзя: компилятор сообщит об ошибке. Возвращаемый Int — идентификатор ресурса.
 */
fun IntoxicationLevel.iconRes(): Int = when (this) {
    // Дальше пять ветвей по возрастанию степени; номер иконки совпадает с номером степени.
    IntoxicationLevel.SOBER -> R.drawable.ic_level_1
    IntoxicationLevel.MILD -> R.drawable.ic_level_2
    IntoxicationLevel.MODERATE -> R.drawable.ic_level_3
    IntoxicationLevel.STRONG -> R.drawable.ic_level_4
    IntoxicationLevel.DANGEROUS -> R.drawable.ic_level_5
}

/**
 * Та же схема, что в [iconRes], но для текстового названия степени.
 * Строки лежат в ресурсах, поэтому приложение можно перевести без правки кода.
 */
fun IntoxicationLevel.titleRes(): Int = when (this) {
    IntoxicationLevel.SOBER -> R.string.level_sober
    IntoxicationLevel.MILD -> R.string.level_mild
    IntoxicationLevel.MODERATE -> R.string.level_moderate
    IntoxicationLevel.STRONG -> R.string.level_strong
    IntoxicationLevel.DANGEROUS -> R.string.level_dangerous
}

/**
 * И снова та же схема — короткая поясняющая подпись к степени
 * (используется там, где нужно объяснить, что значит «сильное» или «опасное»).
 */
fun IntoxicationLevel.hintRes(): Int = when (this) {
    IntoxicationLevel.SOBER -> R.string.level_sober_hint
    IntoxicationLevel.MILD -> R.string.level_mild_hint
    IntoxicationLevel.MODERATE -> R.string.level_moderate_hint
    IntoxicationLevel.STRONG -> R.string.level_strong_hint
    IntoxicationLevel.DANGEROUS -> R.string.level_dangerous_hint
}

/**
 * Иконка степени опьянения. Пятая степень мягко пульсирует (1,0 → 1,08, 900 мс),
 * смена иконки анимируется кроссфейдом на уровне вызывающего кода.
 *
 * Иконки нарисованы тёмным контуром, поэтому на тёмной теме они получают
 * светлую круглую подложку — иначе персонаж сливается с фоном.
 *
 * @Composable — функция отрисовки; её тело вызывает другие composable-функции
 * (Box, Image) и потому само должно быть помечено этой аннотацией.
 * @param level степень опьянения: определяет и картинку, и наличие пульсации.
 * @param modifier модификатор от родителя (отступы, выравнивание).
 * @param size сторона квадрата иконки в dp; тип [Dp] — «плотно-независимые пиксели»,
 *   они автоматически переводятся в пиксели экрана с учётом его плотности.
 */
@Composable
fun LevelIcon(
    level: IntoxicationLevel,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    // Текст для screen reader (TalkBack). stringResource сам достаёт строку из ресурсов,
    // а второй (вложенный) вызов подставляет в её шаблон название степени.
    // Вложенность безопасна: это обычные вызовы функций, а не UI.
    val description = stringResource(
        R.string.level_content_description,
        stringResource(level.titleRes()),
    )

    // Пульсация только у опасной степени; у остальных множитель масштаба постоянный.
    // rememberInfiniteTransition — «бесконечные» часы анимации: пока элемент на экране,
    // значение плавно ходит между началом и концом.
    val scale = if (level == IntoxicationLevel.DANGEROUS) {
        val transition = rememberInfiniteTransition(label = "dangerPulse")
        // animateFloat создаёт State<Float>: Compose читает его при отрисовке и сам
        // перерисовывает иконку на каждом кадре. by — делегирование, поэтому `value`
        // работает как обычный Float, без .value.
        val value by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            // infiniteRepeatable + Reverse = «туда-обратно» без пауз, 900 мс в одну сторону.
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dangerScale",
        )
        value
    } else {
        // Любая другая степень: масштаб 1,0 — анимации нет.
        1.0f
    }

    // Тема системы: на тёмной подложке рисуем светлый круг под иконкой.
    val darkTheme = isSystemInDarkTheme()

    // Box — контейнер-«слой»: складывает содержимое друг на друга и выравнивает его.
    // Цепочка модификаторов по порядку: размер → масштаб → обрезка кругом → фон → отступ.
    // scale меняет только отрисовку, а не размер в разметке, поэтому пульсация
    // не сдвигает соседние элементы.
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (darkTheme) Color(0xFFF6EFE3) else Color.Transparent)
            .padding(if (darkTheme) size * 0.08f else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        // painterResource превращает drawable-ресурс в объект-«кисть» для Image.
        // contentDescription = null здесь недопустим: иконка несёт смысл, поэтому
        // передаём описание, и TalkBack прочитает его.
        Image(
            painter = painterResource(level.iconRes()),
            contentDescription = description,
            // Иконка занимает весь Box; отступ на тёмной теме уже заложен в padding.
            modifier = Modifier.fillMaxSize(),
        )
    }
}
