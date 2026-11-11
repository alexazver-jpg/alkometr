package ru.alkometr.app.ui.main.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.IntoxicationLevel
import ru.alkometr.app.core.model.Units
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Геометрия шкалы задаётся двумя константами, углы — в градусах, как в Canvas.
// Важно про систему координат: 0° смотрит вправо («на 3 часа»), углы растут по часовой
// стрелке, потому что ось Y на экране направлена вниз.
// START_ANGLE = 150° — левый нижний край шкалы.
// SWEEP_ANGLE = 240° — длина дуги; 150° + 240° = 390°, то есть 30° — правый нижний край.
// Итог: дуга идёт от левого низа вверх через верх и вниз направо, а внизу остаётся
// разрыв 360° − 240° = 120° — там и стоит крупное число BAC.
private const val START_ANGLE = 150f
private const val SWEEP_ANGLE = 240f

/**
 * Дуговая шкала уровня алкоголя: 240°, пять цветовых зон по границам степеней,
 * маркер-стрелка, пунктирная линия юридического порога и центральное значение.
 *
 * @Composable — функция отрисовки. Внутри используется [Canvas]: это обычный элемент
 * разметки, который отдаёт нам «холст» для ручного рисования.
 *
 * `Canvas { … }` вызывает лямбду с получателем типа DrawScope — набором команд
 * рисования (drawArc, drawLine, drawPath, drawCircle). В DrawScope координаты и
 * размеры уже в пикселях, поэтому dp нужно переводить через toPx().
 *
 * @param bac текущая концентрация алкоголя в крови — её показывает маркер.
 * @param level степень опьянения: цвет маркера и подписи.
 * @param threshold юридический порог из настроек — пунктирная риска на дуге.
 * @param units единицы измерения для подписей шкалы и центрального числа.
 * @param modifier модификатор от родителя.
 */
@Composable
fun BacGauge(
    bac: Double,
    level: IntoxicationLevel,
    threshold: Double,
    units: Units,
    modifier: Modifier = Modifier,
) {
    // animateFloatAsState — «анимированное зеркало» значения: когда bac меняется,
    // маркер не прыгает, а за 400 мс плавно переезжает в новое положение.
    // by — делегирование: animatedBac читается как обычный Float, хотя это State<Float>.
    val animatedBac by animateFloatAsState(
        // Цель анимации — приведение Double к Float, потому что анимация работает с Float.
        targetValue = bac.toFloat(),
        // tween — равномерная анимация заданной длительности.
        animationSpec = tween(durationMillis = 400),
        // label — имя для инспектора анимаций; на поведение не влияет.
        label = "bacMarker",
    )

    // Цвет текущей степени берём из модели: там он хранится как Long в формате ARGB.
    val levelColor = Color(level.colorArgb)
    // Цвета из темы Material: подложка дуги и цвет подписей.
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // LocalDensity — плотность экрана (сколько физических пикселей в одном dp).
    // Нужна, чтобы перевести размер шрифта из sp в пиксели для нативного Paint.
    val density = LocalDensity.current
    // remember с ключами создаёт объект один раз и пересоздаёт его только при смене
    // цвета или плотности. Paint нужен потому, что подписи шкалы рисуются
    // на нативном android.graphics.Canvas: у Compose нет готового «нарисовать текст».
    val textPaint = remember(labelColor, density) {
        android.graphics.Paint().apply {
            // Сглаживание — иначе цифры будут «лесенкой».
            isAntiAlias = true
            // Compose-цвет переводим в ARGB-Int, понятный нативному Paint.
            color = labelColor.toArgb()
            // 11.sp переводим в пиксели через плотность из композиции.
            textSize = with(density) { 11.sp.toPx() }
        }
    }

    // Пульсация есть только у опасной степени. Если степень другая, переменная равна null,
    // и ниже пульсирующая дуга просто не рисуется.
    val dangerPulse = if (level == IntoxicationLevel.DANGEROUS) {
        // rememberInfiniteTransition — бесконечные «часы» анимации, работающие,
        // пока элемент на экране.
        val transition = rememberInfiniteTransition(label = "dangerZone")
        // Плавно ходим между двумя уровнями прозрачности 0,25 и 0,75.
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            // 900 мс в одну сторону, затем Reverse — обратно. Получается «дыхание».
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dangerAlpha",
        )
    } else {
        null
    }

    // Box — контейнер-слой: холст растягивается на весь Box, а Column с числом
    // накладывается сверху и выравнивается по центру. Высота фиксирована (240 dp),
    // потому что геометрия дуги считается от неё.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        // Холст на весь Box. Внутри лямбды `this` — это DrawScope.
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Толщина кольца шкалы в пикселях. Внутри DrawScope dp.toPx() доступен
            // напрямую, потому что DrawScope сам является Density.
            val stroke = 16.dp.toPx()
            // Центр окружности: по горизонтали — ровно середина ширины.
            val cx = size.width / 2f
            // По вертикали — 62 % высоты, то есть ниже середины. Тогда верх дуги
            // не обрезается, а нижний разрыв попадает в нижнюю часть блока.
            val cy = size.height * 0.62f
            // Радиус: берём меньшее из двух ограничений, чтобы дуга не вылезла
            // ни за ширину (минус 14 dp запаса), ни за высоту (60 % её).
            val radius = min(size.width / 2f - 14.dp.toPx(), size.height * 0.60f)

            // Фоновая дорожка
            // drawArc рисует часть эллипса. Эллипс задаётся прямоугольником:
            // topLeft — его левый верхний угол, size — ширина и высота.
            // Чтобы получить окружность радиуса radius с центром (cx, cy),
            // левый верхний угол ставим в (cx − radius, cy − radius),
            // а размер — 2·radius на 2·radius. Тогда центр прямоугольника
            // совпадёт с (cx, cy).
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                // useCenter = false — рисуем только дугу, без «клиньев» к центру.
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                // Stroke — обводка линии заданной толщины, Round — скруглённые торцы.
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Пять цветовых зон
            // Шесть границ BAC делят шкалу 0…4 на пять отрезков — по одному на степень.
            // Границы берём из модели, а не из чисел в коде: поменяется модель — шкала
            // перестроится сама.
            val boundaries = listOf(
                0.0,
                IntoxicationLevel.MILD.minBac,
                IntoxicationLevel.MODERATE.minBac,
                IntoxicationLevel.STRONG.minBac,
                IntoxicationLevel.DANGEROUS.minBac,
                IntoxicationLevel.SCALE_MAX,
            )
            // Пробегаем по парам соседних границ: i-я зона лежит между boundaries[i]
            // и boundaries[i + 1]. lastIndex = 5, поэтому i идёт 0…4 — ровно пять зон.
            for (i in 0 until boundaries.lastIndex) {
                // Перевод значения BAC в угол: доля от максимума шкалы, умноженная
                // на длину дуги, плюс её начало. Пример: BAC 2,0 → 2,0/4,0 = 0,5 →
                // 150° + 240°·0,5 = 270°, то есть верхняя точка дуги.
                val from = START_ANGLE + SWEEP_ANGLE * (boundaries[i] / IntoxicationLevel.SCALE_MAX).toFloat()
                val to = START_ANGLE + SWEEP_ANGLE * (boundaries[i + 1] / IntoxicationLevel.SCALE_MAX).toFloat()
                // Цвет зоны берём у соответствующей степени: entries[i] идёт
                // в том же порядке, что и границы (SOBER, MILD, MODERATE, STRONG, DANGEROUS).
                val zoneLevel = IntoxicationLevel.entries[i]
                drawArc(
                    color = Color(zoneLevel.colorArgb),
                    // Небольшие зазоры: сдвигаем начало на +0,8°, а длину уменьшаем
                    // на 1,6° — так между зонами появляется видимая «щель».
                    startAngle = from + 0.8f,
                    sweepAngle = (to - from) - 1.6f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    // Butt — прямые торцы: со скруглением зазоры выглядели бы «каплями».
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }

            // Пульсация опасной зоны
            // Опасная зона начинается с BAC 2,5 при максимуме 4,0 → 2,5/4,0 = 0,625
            // доли дуги. Отсюда начало 150° + 240°·0,625, а длина — оставшиеся 37,5 %:
            // 240°·0,375 = 90°, то есть последняя четверть шкалы.
            if (dangerPulse != null) {
                drawArc(
                    // Прозрачность берётся из анимации — дуга «дышит».
                    color = Color(IntoxicationLevel.DANGEROUS.colorArgb).copy(alpha = dangerPulse.value),
                    startAngle = START_ANGLE + SWEEP_ANGLE * 0.625f,
                    sweepAngle = SWEEP_ANGLE * 0.375f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    // Обводка на 6 dp шире основной — получается мягкое свечение поверх зоны.
                    style = Stroke(width = stroke + 6.dp.toPx()),
                )
            }

            // Подписи шкалы: 0 … 4 ‰, в выбранных единицах, внутри дуги
            // Пять подписей: i = 0…4, значение = 4·i/4, то есть 0, 1, 2, 3, 4.
            for (i in 0..4) {
                // Значение в единицах модели (‰); форматирование в нужные единицы — ниже.
                val value = IntoxicationLevel.SCALE_MAX * i / 4.0
                // Тот же перевод «значение → угол», что и у зон.
                val angle = START_ANGLE + SWEEP_ANGLE * (value / IntoxicationLevel.SCALE_MAX).toFloat()
                // Тригонометрия работает в радианах, а Canvas — в градусах:
                // rad = угол · π / 180.
                val rad = PI * angle / 180.0
                // Подписи ставим не на самой дуге, а внутри неё: отнимаем толщину
                // кольца и ещё 22 dp, чтобы цифры не наезжали на цветные зоны.
                val labelRadius = radius - stroke - 22.dp.toPx()
                // Точка на окружности: x = cx + R·cos α, y = cy + R·sin α.
                // При α = 150° cos отрицателен, sin положителен → левее и ниже центра.
                val lx = cx + labelRadius * cos(rad).toFloat()
                val ly = cy + labelRadius * sin(rad).toFloat()
                // Текст в текущих единицах измерения пользователя (‰ или мг/л и т. п.).
                val text = Formatters.formatValue(value, units)
                // drawText рисует по базовой линии, а не «по центру». Поэтому:
                // по X отнимаем половину ширины текста (measureText) — центрируем;
                // по Y прибавляем textSize/3 — примерно половину высоты строчных цифр.
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    lx - textPaint.measureText(text) / 2f,
                    ly + textPaint.textSize / 3f,
                    textPaint,
                )
            }

            // Линия юридического порога
            // Показываем риску только если порог вообще попадает на шкалу 0…4.
            if (threshold in 0.0..IntoxicationLevel.SCALE_MAX) {
                // Угол порога считается так же, как углы зон и подписей.
                val angle = START_ANGLE + SWEEP_ANGLE * (threshold / IntoxicationLevel.SCALE_MAX).toFloat()
                drawArc(
                    color = labelColor,
                    // Тонкая поперечная риска: начало на 1,2° раньше угла порога,
                    // длина 2,4° — итого она отцентрована ровно на угле порога.
                    startAngle = angle - 1.2f,
                    sweepAngle = 2.4f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        // Пунктир: 6 dp штрих, 5 dp промежуток — визуально отличается
                        // от сплошной цветной шкалы.
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }

            // Маркер-стрелка
            // Значение анимации приводим к Double и зажимаем в 0…4: отрицательный BAC
            // или перелёт за максимум не должны уводить маркер за пределы дуги.
            val clamped = animatedBac.toDouble().coerceIn(0.0, IntoxicationLevel.SCALE_MAX)
            // Угол маркера — та же формула «доля шкалы · длина дуги + начало».
            val markerAngle = START_ANGLE + SWEEP_ANGLE * (clamped / IntoxicationLevel.SCALE_MAX).toFloat()
            // Угол в радианах и единичный вектор направления от центра к маркеру.
            // dirX, dirY — это (cos α, sin α): куда «смотрит» радиус.
            val markerRad = PI * markerAngle / 180.0
            val dirX = cos(markerRad).toFloat()
            val dirY = sin(markerRad).toFloat()
            // Остриё стрелки — на самом радиусе дуги.
            val tip = Offset(cx + radius * dirX, cy + radius * dirY)
            // Хвостик — на 24 dp ближе к центру: получается короткий отрезок поперёк кольца.
            val tail = Offset(cx + (radius - 24.dp.toPx()) * dirX, cy + (radius - 24.dp.toPx()) * dirY)

            // Сначала сам отрезок-«ножка» маркера от хвоста к острию,
            // толщиной 4 dp, со скруглёнными концами.
            drawLine(
                color = levelColor,
                start = tail,
                end = tip,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            // Теперь треугольный наконечник. Path — «перо»: набор команд построения
            // фигуры, который затем заливается одной командой drawPath.
            // Геометрия треугольника по шагам:
            // 1) перпендикуляр к радиусу — если вектор (dirX, dirY), то перпендикуляр
            //    (−dirY, dirX); он задаёт «ширину» треугольника;
            // 2) основание — на 12 dp ближе к центру, чем остриё (иначе игла была бы слишком длинной);
            // 3) половина ширины основания — 9 dp в каждую сторону от оси;
            // 4) moveTo ставит перо в остриё, два lineTo ведут к углам основания,
            //    close() замыкает фигуру на первую точку.
            val arrow = Path().apply {
                val perpX = -dirY
                val perpY = dirX
                val base = Offset(cx + (radius - 12.dp.toPx()) * dirX, cy + (radius - 12.dp.toPx()) * dirY)
                val width = 9.dp.toPx()
                moveTo(tip.x, tip.y)
                lineTo(base.x + perpX * width, base.y + perpY * width)
                lineTo(base.x - perpX * width, base.y - perpY * width)
                close()
            }
            // Заливаем треугольник цветом степени.
            drawPath(arrow, levelColor)
            // Кружок в острие: сначала белая заливка радиусом 4 dp…
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = tip)
            // …сверху кольцо цвета степени толщиной 2 dp — получается «точка в ободке».
            drawCircle(color = levelColor, radius = 4.dp.toPx(), center = tip, style = Stroke(2.dp.toPx()))
        }

        // Текстовая часть поверх холста. Column выровнен по центру Box и чуть смещён
        // вниз (offset 6 dp), чтобы крупное число встало в разрыв дуги.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Число и единица стоят в одну строку; Alignment.Bottom выравнивает их
            // по нижней кромке, чтобы единица не «висела» у верхушки цифр.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                // Само значение BAC крупным шрифтом. Здесь показываем исходный bac,
                // а не анимированный: текст должен обновляться мгновенно.
                Text(
                    text = Formatters.formatValue(bac, units),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                // Символ единиц мельче, с небольшим отступом слева и снизу.
                Text(
                    text = units.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }
            // Crossfade — плавная смена содержимого: при изменении level старый набор
            // «иконка + подпись» растворяется, новый проявляется за 250 мс.
            // Параметр лямбды current — то состояние, которое рисуется сейчас.
            Crossfade(targetState = level, animationSpec = tween(250), label = "levelSwap") { current ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    // Иконка-персонаж степени (внутри неё своя пульсация для опасной зоны).
                    LevelIcon(level = current, size = 42.dp)
                    // Название степени, окрашенное в её цвет.
                    Text(
                        text = stringResource(current.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(current.colorArgb),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
