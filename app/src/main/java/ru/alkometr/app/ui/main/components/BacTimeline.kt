package ru.alkometr.app.ui.main.components

// Импорты ниже: средства Compose (рисование, жесты, состояние), платформенный Paint и классы проекта.
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.alkometr.app.R
import ru.alkometr.app.core.engine.BacCurve
import ru.alkometr.app.core.engine.BacEngine
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.ModelParams
import ru.alkometr.app.core.model.IntoxicationLevel
import ru.alkometr.app.core.model.Units
import kotlin.math.max
import kotlin.math.min

// Время во всём файле живёт в миллисекундах (Long), поэтому «минуту» и «час» держим константами:
// 60_000L — это 60 000 миллисекунд, подчёркивания в числе нужны только для читаемости.
private const val MINUTE = 60_000L
// Час — это 60 минут; так любое «6 * HOUR» читается как «шесть часов», а не как магия.
private const val HOUR = 60 * MINUTE

/**
 * Шкала времени: кривая уровня, порции, линия порога и прогноз.
 *
 * Управление: палец по графику двигает курсор (scrub), два пальца — прокрутка и щипок,
 * кнопки «+»/«−» — масштаб, «сейчас» — вернуть окно к текущему моменту.
 *
 * Три слоя, из которых собран компонент:
 * 1. Состояние — окно времени (windowStart..windowEnd), выбранный момент, ширина холста.
 * 2. Ввод — pointerInput: жест меняет либо окно, либо позицию курсора.
 * 3. Отрисовка — Canvas: каждый кадр сетка, кривая и подписи рисуются заново,
 *    готовой «картинки» нигде не хранится.
 *
 * Вся графика сводится к двум функциям-переводчикам, объявленным внутри Canvas:
 * xOf(время) даёт пиксель по горизонтали, yOf(концентрация) — пиксель по вертикали.
 * Понять их — значит понять почти весь файл: любая линия рисуется в точке (xOf(...), yOf(...)).
 *
 * Параметры: curve — уже посчитанная кривая, drinks — порции для маркеров под осью,
 * threshold — порог в промилле, units — единицы для подписей, nowMillis — текущий момент,
 * initialStartMillis/initialEndMillis — окно, которое показываем при открытии,
 * driveText — готовая подпись «когда за руль» снизу.
 *
 * modifier объявлен для единообразия с другими компонентами и внутри пока не используется.
 */
@Composable
fun BacTimeline(
    curve: BacCurve,
    drinks: List<Drink>,
    params: ModelParams,
    threshold: Double,
    units: Units,
    nowMillis: Long,
    initialStartMillis: Long,
    initialEndMillis: Long,
    driveText: String,
    modifier: Modifier = Modifier,
) {
    // ── Состояние ─────────────────────────────────────────────────────────────
    // Окно времени — это отрезок [windowStart, windowEnd], который сейчас виден на графике.
    // Он живёт в состоянии, потому что меняется от жестов, кнопок и эффектов, а Compose
    // обязан перерисовать Canvas после каждого такого изменения.
    // remember сохраняет значение между рекомпозициями (иначе оно обнулялось бы на каждом кадре),
    // а mutableStateOf делает ячейку «наблюдаемой»: запись в неё сама запускает перерисовку.
    var windowStart by remember { mutableStateOf(initialStartMillis) }
    var windowEnd by remember { mutableStateOf(initialEndMillis) }
    // Выбранный момент (курсор). null — курсор не показан. Тип Long? читается как
    // «число или ничего»: именно поэтому ниже появляется `val selected = selectedMillis`
    // и проверка на null — так компилятор знает, что внутри if значение уже не пустое.
    var selectedMillis by remember { mutableStateOf<Long?>(null) }
    // Ширина холста в пикселях. Её сообщает onSizeChanged у Canvas: без ширины нельзя
    // перевести пиксели в миллисекунды (и наоборот), а значит и обработать жесты.
    // mutableFloatStateOf — то же самое, что mutableStateOf, но для Float без упаковки в объект.
    var widthPx by remember { mutableFloatStateOf(0f) }
    // Открыт ли диалог со списком всех порций.
    var expandedList by remember { mutableStateOf(false) }

    // При смене состава порций показываем окно заново — но не сбрасываем масштаб на каждом тике.
    // LaunchedEffect(ключ1, ключ2) выполняет свой блок один раз при первом показе и повторно
    // только тогда, когда меняется хотя бы один ключ. Ключи здесь — число порций и время первой
    // из них: добавили или убрали порцию — окно вернулось к initialStartMillis..initialEndMillis.
    // Если бы windowStart сбрасывался просто в теле функции, любой тик nowMillis стирал бы
    // пользовательский зум, и график «прыгал» бы сам по себе.
    LaunchedEffect(drinks.size, drinks.firstOrNull()?.atMillis) {
        windowStart = initialStartMillis
        windowEnd = initialEndMillis
    }

    // ── Кисти для подписей ────────────────────────────────────────────────────
    // Paint — обычная «кисть» из классической Android-графики: в ней цвет, размер текста
    // и сглаживание. Compose умеет рисовать текст только через холст платформы (nativeCanvas),
    // а ему нужен именно Paint. remember(density) создаёт кисть один раз и пересоздаёт её
    // при смене плотности экрана — иначе кегль 10.sp посчитался бы в старых пикселях.
    val density = LocalDensity.current
    val axisPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
        }
    }
    // Вторая кисть — для более мелких подписей: оси концентрации и маркеры порций.
    val smallPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 9.sp.toPx() }
        }
    }

    // Цвета берём из темы Material, а не «зашиваем» числами: при смене темы график перекрасится сам.
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val thresholdColor = MaterialTheme.colorScheme.error
    val nowColor = MaterialTheme.colorScheme.primary
    val iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

    // Иконки порций — заранее нарисованные drawable: рюмка, бокал, кружка и «своё».
    val shotIcon = painterResource(R.drawable.ic_shot)
    val wineIcon = painterResource(R.drawable.ic_wine)
    val mugIcon = painterResource(R.drawable.ic_mug)
    val customIcon = painterResource(R.drawable.ic_custom)

    // Paint принимает цвет числом в формате ARGB, а Compose Color — свой тип, поэтому toArgb().
    val axisTextColor = onSurfaceVariant.toArgb()
    axisPaint.color = axisTextColor
    smallPaint.color = axisTextColor

    // Все тексты — из ресурсов (strings.xml): так их можно перевести и поправить без правки кода.
    // formatBac(...) подставляет значение в шаблон строки вместе с единицами измерения.
    val nowLabel = stringResource(R.string.timeline_now)
    val description = stringResource(R.string.timeline_content_description)
    val thresholdLabel = stringResource(
        R.string.timeline_threshold,
        Formatters.formatBac(threshold, units),
    )

    // Верхняя граница оси Y — не «красивое число», а запас над максимумом: 15 % над пиком
    // кривой и минимум в 1.6 раза выше порога, чтобы линия порога не прилипала к верхнему краю.
    // Внешний max(..., 0.5) страхует пустой профиль: шкала не схлопнется в ноль.
    val yMax = max(max(curve.peak().bacGPerL * 1.15, threshold * 1.6), 0.5)

    // ── Плашка-подсказка о выбранном моменте ──────────────────────────────────
    // selected — «умное приведение» nullable-значения к обычному Long: после проверки на null
    // компилятор разрешает обращаться с ним как с числом.
    val selected = selectedMillis
    if (selected != null) {
        // valueAt — концентрация в этот момент (с интерполяцией между точками кривой),
        // forBac — уровень опьянения для текстовой подписи.
        val value = curve.valueAt(selected)
        val level = IntoxicationLevel.forBac(value)
        val levelText = levelTitle(level)
        // Для прошлого и для «сейчас/будущего» разные строки ресурсов: в прошлом это факт,
        // в будущем — прогноз. Границей служит сравнение выбранного момента с nowMillis.
        val base = if (selected < nowMillis) {
            stringResource(
                R.string.timeline_scrub_past,
                Formatters.formatClock(selected),
                Formatters.formatBac(value, units),
                levelText,
            )
        } else {
            stringResource(
                R.string.timeline_scrub_card,
                Formatters.formatClock(selected),
                Formatters.formatBac(value, units),
                levelText,
            )
        }
        // Если выбран будущий момент, в котором ещё выше порога, считаем, сколько осталось
        // до безопасного значения. Иначе ready == null и хвост подсказки пустой.
        val ready = if (selected >= nowMillis && value > threshold) {
            BacEngine.timeToThreshold(drinks, params, selected, threshold)
        } else {
            null
        }
        // ready - selected — длительность в миллисекундах, formatDuration делает из неё «2 ч 15 мин».
        // Разделитель « · » нужен, чтобы хвост читался как продолжение фразы.
        val tail = if (ready != null) {
            " · " + stringResource(
                R.string.timeline_scrub_to_threshold,
                Formatters.formatDuration(ready - selected),
            )
        } else {
            ""
        }
        // Surface — закруглённая плашка на цвете поверхности темы, чуть прозрачном,
        // чтобы график под ней не терялся.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        ) {
            Text(
                text = base + tail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    // ── Холст и жесты ─────────────────────────────────────────────────────────
    // Modifier собирается цепочкой: размер → узнать ширину → описание для экранного диктора
    // → обработка касаний. Порядок важен: onSizeChanged должен стоять до pointerInput,
    // иначе обработчик жестов может сработать раньше, чем станет известна ширина.
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            // 190.dp — общая высота: примерно 132.dp уходит на сам график,
            // остальное — на подписи времени и иконки порций под осью.
            .height(190.dp)
            // onSizeChanged вызывается после измерения, когда ширина уже известна:
            // Compose сначала раскладывает элементы, а потом рисует. it.width — Int,
            // поэтому приводим к Float, чтобы дальше делить без целочисленного округления.
            .onSizeChanged { widthPx = it.width.toFloat() }
            // semantics: для экранного диктора график — одна картинка с текстовым описанием.
            .semantics { contentDescription = description }
            // pointerInput(Unit): Unit — ключ, поэтому обработчик создаётся один раз и переживает
            // рекомпозиции. Актуальные windowStart/windowEnd он всё равно видит: `by remember` —
            // это чтение из состояния при каждом обращении, а не копия значения на момент создания.
            .pointerInput(Unit) {
                // awaitEachGesture — «один жест за раз»: дожидается первого касания, ведёт жест
                // до конца и только потом начинает следующий. Так жесты не перемешиваются.
                awaitEachGesture {
                    // Ждём первое нажатие. requireUnconsumed = false — не отбираем событие
                    // у родителей (например, у вертикального скролла всего экрана).
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Одно касание сразу двигает курсор: палец работает как «лупа» по времени.
                    // Если ширина ещё 0 (холст не измерен), переводить пиксели нечего — пропускаем.
                    if (widthPx > 0f) selectedMillis = xToTime(down.position.x, windowStart, windowEnd, widthPx)
                    // Дальше — цикл по событиям текущего жеста, пока на экране есть пальцы.
                    while (true) {
                        val event = awaitPointerEvent()
                        // Берём только те указатели, что всё ещё касаются экрана:
                        // отпущенные пальцы в расчётах участвовать не должны.
                        val pressed = event.changes.filter { it.pressed }
                        // Ни одного нажатого пальца — жест закончился, выходим из цикла.
                        if (pressed.isEmpty()) break
                        // Два и более пальца — работаем с окном времени, а не с курсором.
                        if (pressed.size >= 2) {
                            // calculateZoom(): > 1 — пальцы разъехались (увеличить),
                            // < 1 — сдвинулись (уменьшить). calculatePan() — средний сдвиг
                            // всех пальцев в пикселях за этот кадр.
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            // centroidX — центр между пальцами: «якорь», точка, которая при
                            // масштабировании должна остаться на месте.
                            val centroidX = pressed.map { it.position.x }.average().toFloat()
                            // span — сколько миллисекунд сейчас влезает в ширину экрана.
                            // coerceAtLeast(1L) не даёт окну выродиться в ноль: на него делят
                            // и умножают доли, и нулевая длина дала бы мусор вместо координат.
                            val span = (windowEnd - windowStart).coerceAtLeast(1L)
                            // Переводим якорь из пикселей во время по той же логике, что и xOf.
                            val anchor = windowStart + (centroidX / widthPx * span).toLong()
                            // Щипок меняет длину окна: zoom > 1 — делим span, окно становится
                            // короче, а картинка крупнее. coerceIn держит пределы: от 30 минут
                            // до 48 часов, иначе масштаб улетел бы в бесконечность.
                            val newSpan = (span / zoom).toLong().coerceIn(30 * MINUTE, 48 * HOUR)
                            // Новое начало окна: ставим его так, чтобы якорь остался под пальцами,
                            // и добавляем pan — прокрутку двумя пальцами.
                            var newStart = anchor -
                                (centroidX / widthPx * newSpan).toLong() -
                                (pan.x / widthPx * newSpan).toLong()
                            // Не уезжаем далеко за пределы кривой: слева запас 12 часов,
                            // справа — не дальше конца прогноза (или того же левого края,
                            // если кривая короче окна).
                            newStart = newStart.coerceIn(
                                curve.startMillis - 12 * HOUR,
                                (curve.endMillis - newSpan).coerceAtLeast(curve.startMillis - 12 * HOUR),
                            )
                            // Записываем в состояние — Compose перерисует Canvas.
                            // Начало и конец меняем вместе, иначе окно на миг станет «рваным».
                            windowStart = newStart
                            windowEnd = newStart + newSpan
                            // consume() помечает событие обработанным: родительские обработчики
                            // (например, скролл страницы) уже не будут на него реагировать.
                            pressed.forEach { it.consume() }
                        } else {
                            // Один палец — курсор: тянем выбранный момент за пальцем.
                            val change = pressed.first()
                            selectedMillis = xToTime(change.position.x, windowStart, windowEnd, widthPx)
                            // Тот же consume(): событие наше, страница под графиком не прокручивается.
                            change.consume()
                        }
                    }
                }
            },
    ) {
        // ── Геометрия области рисования ───────────────────────────────────────
        // Внутри Canvas работает DrawScope: (0,0) — левый верхний угол, x растёт вправо,
        // y — вниз (в отличие от привычной математики). size.width/size.height — размеры в пикселях.
        // Внутри DrawScope есть своя плотность экрана, поэтому dp.toPx() доступен без LocalDensity.
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val chartTop = 16.dp.toPx()
        val chartBottom = 132.dp.toPx()
        val chartHeight = chartBottom - chartTop
        // Длина видимого окна в миллисекундах — та же величина, что считали в жестах.
        val span = (windowEnd - windowStart).coerceAtLeast(1L)

        // ── Два переводчика координат ─────────────────────────────────────────
        // Главная формула по X: доля времени внутри окна (0..1) умножается на ширину графика.
        // Пример: окно 6 часов, точка через 3 часа после начала → 0.5 → середина между left и right.
        // toDouble() нужен, чтобы деление было дробным: 3/6 в целых числах дало бы 0.
        fun xOf(millis: Long): Float =
            left + (right - left) * ((millis - windowStart).toDouble() / span.toDouble()).toFloat()

        // Главная формула по Y, «перевёрнутая»: ноль концентрации — внизу (chartBottom),
        // максимум yMax — вверху (chartTop), поэтому считаем 1 - доля.
        // coerceIn(0.0, 1.0) прижимает значения к рамке: пик выше yMax не вылезет за график.
        fun yOf(bac: Double): Float =
            chartTop + chartHeight * (1.0 - (bac / yMax).coerceIn(0.0, 1.0)).toFloat()

        // ── Вертикальная сетка по времени ─────────────────────────────────────
        // Шаг подбирается по ширине окна: чем больше времени видно, тем крупнее шаг
        // (15 минут … 4 часа). Иначе подписи наехали бы друг на друга.
        // when без аргумента — цепочка условий: срабатывает первая подходящая ветка.
        val stepMinutes = when {
            span <= 2 * HOUR -> 15L
            span <= 5 * HOUR -> 30L
            span <= 10 * HOUR -> 60L
            span <= 20 * HOUR -> 120L
            else -> 240L
        }
        // Первая отметка — «круглая»: делим время на шаг нацело и умножаем обратно,
        // получаем ближайшую отметку, кратную шагу (например, 10:00 при шаге 30 минут).
        var tick = (windowStart / (stepMinutes * MINUTE)) * (stepMinutes * MINUTE)
        while (tick <= windowEnd) {
            val x = xOf(tick)
            // Рисуем только то, что попало в видимую часть: остальное всё равно обрезано.
            if (x in left..right) {
                // Вертикальная линия сетки — от верха графика до низа.
                drawLine(gridColor, Offset(x, chartTop), Offset(x, chartBottom), strokeWidth = 1f)
                val label = Formatters.formatClock(tick)
                // Подпись времени. Текст в Compose рисуется через nativeCanvas — «родной» холст
                // Android, а ему нужна кисть Paint (создана выше). Аргументы drawText: текст, x, y, кисть,
                // где x и y — левый нижний угол строки. Поэтому из x вычитаем половину ширины
                // (measureText возвращает ширину строки в пикселях) — так подпись встанет по центру.
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x - axisPaint.measureText(label) / 2f,
                    chartBottom + 12.dp.toPx(),
                    axisPaint,
                )
            }
            tick += stepMinutes * MINUTE
        }

        // ── Горизонтальная сетка по концентрации ──────────────────────────────
        // Шаг тоже зависит от yMax: 0.1 при маленькой шкале, до 1.0 при большой,
        // чтобы подписей не стало слишком много.
        val yStep = when {
            yMax <= 0.6 -> 0.1
            yMax <= 1.5 -> 0.25
            yMax <= 3.0 -> 0.5
            else -> 1.0
        }
        var line = 0.0
        // Идём от нуля вверх до yMax включительно: line += yStep — приращение цикла.
        while (line <= yMax) {
            val y = yOf(line)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val label = Formatters.formatValue(line, units)
            // Подпись значения — слева, чуть выше линии, мелкой кистью.
            drawContext.canvas.nativeCanvas.drawText(label, left + 2.dp.toPx(), y - 3.dp.toPx(), smallPaint)
            line += yStep
        }

        // ── Линия порога ─────────────────────────────────────────────────────
        val thresholdY = yOf(threshold)
        // Горизонтальный пунктир на высоте yOf(threshold): dashPathEffect принимает пары
        // «длина штриха, длина промежутка» в пикселях — здесь 6 dp и 5 dp.
        drawLine(
            color = thresholdColor.copy(alpha = 0.8f),
            start = Offset(left, thresholdY),
            end = Offset(right, thresholdY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        // Подпись порога прижимается к правому краю (right - ширина текста), а по вертикали
        // coerceAtLeast не даёт ей уехать выше самого графика.
        val thresholdWidth = smallPaint.measureText(thresholdLabel)
        drawContext.canvas.nativeCanvas.drawText(
            thresholdLabel,
            right - thresholdWidth,
            (thresholdY - 4.dp.toPx()).coerceAtLeast(chartTop + 8.dp.toPx()),
            smallPaint,
        )

        // Точки кривой (curve.all — заранее посчитанные сэмплы) и цвет по текущему уровню:
        // доменная модель хранит цвет числом, чтобы не зависеть от Compose, поэтому здесь
        // обратное превращение Int → Color.
        val points = curve.all
        val currentColor = Color(IntoxicationLevel.forBac(curve.valueAt(nowMillis)).colorArgb)

        if (points.isNotEmpty()) {
            // ── Прошлое: сплошная линия и заливка под ней ─────────────────────
            // Path собирается командами: moveTo — начать новую линию, lineTo — продлить.
            // Пути два, а проход по точкам один: pastPath — сама кривая, fillPath — контур
            // заливки, который потом замкнётся по низу графика.
            val pastPath = Path()
            val fillPath = Path()
            // startedPast — признак «первая точка уже поставлена»: самая первая команда
            // должна быть moveTo, все следующие — lineTo.
            var startedPast = false
            for (point in points) {
                // Дошли до «сейчас» — прошлое закончилось.
                if (point.atMillis > nowMillis) break
                val x = xOf(point.atMillis)
                val y = yOf(point.bacGPerL)
                if (!startedPast) {
                    pastPath.moveTo(x, y)
                    // Заливку начинаем от низа графика: вертикальный отрезок вверх до кривой.
                    fillPath.moveTo(x, chartBottom)
                    fillPath.lineTo(x, y)
                    startedPast = true
                } else {
                    pastPath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            if (startedPast) {
                // Дотягиваем линии до вертикали «сейчас»: последний сэмпл может стоять чуть левее,
                // а стык прошлого и будущего должен быть ровно на текущем моменте.
                val nowX = xOf(min(nowMillis, windowEnd))
                val nowY = yOf(curve.valueAt(nowMillis))
                pastPath.lineTo(nowX, nowY)
                fillPath.lineTo(nowX, nowY)
                // По низу графика обратно и close() — контур замкнулся, получился «стакан» под кривой.
                fillPath.lineTo(nowX, chartBottom)
                fillPath.close()

                // Заливка градиентом: сверху цвет уровня (35 % непрозрачности), снизу — прозрачный.
                // Brush.verticalGradient задаёт направление по Y в координатах холста.
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(currentColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = chartTop,
                        endY = chartBottom,
                    ),
                )
                // Сама кривая прошлого: сплошная, толщиной 2.5 dp, с круглыми концами.
                drawPath(
                    path = pastPath,
                    color = currentColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // ── Будущее: прогноз пунктиром ────────────────────────────────────
            // Второй проход по тем же точкам, но прошлое пропускаем: continue просто переходит
            // к следующей точке (в отличие от break, который завершает цикл целиком).
            val futurePath = Path()
            var startedFuture = false
            for (point in points) {
                if (point.atMillis < nowMillis) continue
                val x = xOf(point.atMillis)
                val y = yOf(point.bacGPerL)
                if (!startedFuture) {
                    futurePath.moveTo(x, y)
                    startedFuture = true
                } else {
                    futurePath.lineTo(x, y)
                }
            }
            if (startedFuture) {
                // Прогноз рисуем пунктиром и чуть прозрачнее: это ещё не факт, а расчёт.
                drawPath(
                    path = futurePath,
                    color = currentColor.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }
        }

        // ── Линия «сейчас» ────────────────────────────────────────────────────
        // Вертикаль в текущем моменте, но только если он попал в видимое окно.
        if (nowMillis in windowStart..windowEnd) {
            val x = xOf(nowMillis)
            drawLine(
                color = nowColor.copy(alpha = 0.55f),
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1.5f,
            )
            // Подпись «сейчас» — справа от линии, но не вылезая за правый край:
            // coerceAtMost ограничивает x максимумом.
            drawContext.canvas.nativeCanvas.drawText(
                nowLabel,
                (x + 4.dp.toPx()).coerceAtMost(right - axisPaint.measureText(nowLabel)),
                chartTop + 10.dp.toPx(),
                axisPaint,
            )
        }

        // ── Точка пересечения порога с прогнозом ──────────────────────────────
        // Это момент, когда можно за руль. Ищем её только если сейчас мы ещё выше порога:
        // берём первую будущую точку, где кривая опустилась до порога или ниже.
        val driveAt = if (curve.valueAt(nowMillis) > threshold) {
            points.firstOrNull { it.atMillis > nowMillis && it.bacGPerL <= threshold }
        } else {
            null
        }
        if (driveAt != null && driveAt.atMillis in windowStart..windowEnd) {
            val x = xOf(driveAt.atMillis)
            val y = yOf(driveAt.bacGPerL)
            // Два круга: светлая «дырка» внутри и красный контур сверху — так точка
            // читается даже на фоне заливки.
            drawCircle(color = surfaceColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
            drawCircle(
                color = thresholdColor,
                radius = 4.5.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(2.dp.toPx()),
            )
            // Подпись времени рядом с точкой, с теми же ограничениями по краям графика.
            val label = Formatters.formatClock(driveAt.atMillis)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                (x + 5.dp.toPx()).coerceAtMost(right - axisPaint.measureText(label)),
                (y - 6.dp.toPx()).coerceAtLeast(chartTop + 8.dp.toPx()),
                axisPaint,
            )
        }

        // ── Маркеры порций под осью ───────────────────────────────────────────
        // Идея разведения: если иконка встаёт ближе 44 dp к предыдущей, кладём её на вторую
        // строку — иначе близкие по времени порции слились бы в одну кашу.
        // lastMarkerX — x предыдущего маркера, markerRow — 0 (верхняя строка) или 1 (нижняя).
        var lastMarkerX = -1000f
        var markerRow = 0
        // Порядок важен: разведение работает только при обходе по возрастанию времени.
        drinks.sortedBy { it.atMillis }.forEach { drink ->
            if (drink.atMillis in windowStart..windowEnd) {
                val x = xOf(drink.atMillis)
                // 1 - markerRow — переключение 0 ↔ 1: соседние близкие маркеры по очереди
                // меняют строку. Если места хватает, возвращаемся в верхнюю строку (0).
                markerRow = if (kotlin.math.abs(x - lastMarkerX) < 44.dp.toPx()) 1 - markerRow else 0
                lastMarkerX = x
                // Сдвиг по вертикали для второй строки.
                val rowOffset = markerRow * 12.dp.toPx()
                // Иконка зависит от вида напитка; when по enum обязан покрыть все варианты.
                val painter = when (drink.kind) {
                    ru.alkometr.app.core.model.DrinkKind.SHOT -> shotIcon
                    ru.alkometr.app.core.model.DrinkKind.WINE_GLASS -> wineIcon
                    ru.alkometr.app.core.model.DrinkKind.MUG -> mugIcon
                    ru.alkometr.app.core.model.DrinkKind.CUSTOM -> customIcon
                }
                val icon = 16.dp.toPx()
                // Цвет иконки — от крепости: lerp смешивает два цвета, strength = 0 даёт светлый
                // оттенок, strength = 1 (40° и выше) — тёмный.
                val strength = (drink.abvPercent / 40.0).coerceIn(0.0, 1.0).toFloat()
                val drinkTint = lerp(Color(0xFFE0A030), Color(0xFF8D3B12), strength)
                // translate временно переносит начало координат в левый верхний угол иконки:
                // дальше рисуем как из (0,0), а не считаем абсолютные координаты.
                translate(left = x - icon / 2, top = chartBottom + 16.dp.toPx() + rowOffset) {
                    with(painter) {
                        // draw у Painter рисует иконку заданного размера,
                        // ColorFilter.tint перекрашивает её в вычисленный оттенок.
                        draw(size = Size(icon, icon), colorFilter = ColorFilter.tint(drinkTint))
                    }
                }
                // Возраст порции («2 ч назад») — по центру под иконкой;
                // coerceIn не даёт подписи уехать за края графика.
                val age = Formatters.formatAge(drink.atMillis, nowMillis)
                val ageWidth = smallPaint.measureText(age)
                drawContext.canvas.nativeCanvas.drawText(
                    age,
                    (x - ageWidth / 2f).coerceIn(left, right - ageWidth),
                    chartBottom + 42.dp.toPx() + rowOffset,
                    smallPaint,
                )
            }
        }

        // ── Курсор выбранного момента ─────────────────────────────────────────
        // Пунктирная вертикаль и точка на кривой: видно, к какому значению относится
        // подсказка-плашка сверху. Рисуется последней, поэтому лежит поверх всего.
        if (selected != null && selected in windowStart..windowEnd) {
            val x = xOf(selected)
            drawLine(
                color = onSurface,
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawCircle(color = onSurface, radius = 4.dp.toPx(), center = Offset(x, yOf(curve.valueAt(selected))))
        }
    }

    // ── Кнопки под графиком ───────────────────────────────────────────────────
    // Нижняя строка: текст «когда за руль», кнопка возврата окна и кнопки масштаба.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = driveText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            // weight(1f) — текст забирает всё свободное место, кнопки прижимаются вправо.
            modifier = Modifier.weight(1f),
        )
        // «Сейчас»: окно ровно ±6 часов вокруг текущего момента — привычный вид по умолчанию.
        TextButton(onClick = {
            windowStart = nowMillis - 6 * HOUR
            windowEnd = nowMillis + 6 * HOUR
        }) {
            Text(stringResource(R.string.timeline_reset), style = MaterialTheme.typography.labelSmall)
        }
        // «−»: окно вдвое шире (масштаб уменьшается), но не больше 48 часов.
        // Центр окна сохраняем, чтобы картинка не «прыгала» вбок.
        TextButton(onClick = {
            val span = (windowEnd - windowStart).coerceAtLeast(1L)
            val center = (windowStart + windowEnd) / 2
            val newSpan = (span * 2).coerceAtMost(48 * HOUR)
            windowStart = center - newSpan / 2
            windowEnd = center + newSpan / 2
        }) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        // «+»: окно вдвое уже (масштаб растёт), но не меньше 30 минут — те же пределы,
        // что и у щипка, чтобы кнопки и жесты не расходились в поведении.
        TextButton(onClick = {
            val span = (windowEnd - windowStart).coerceAtLeast(1L)
            val center = (windowStart + windowEnd) / 2
            val newSpan = (span / 2).coerceAtLeast(30 * MINUTE)
            windowStart = center - newSpan / 2
            windowEnd = center + newSpan / 2
        }) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }

    // ── Список порций ─────────────────────────────────────────────────────────
    // Кнопка-переключатель: её текст зависит от состояния, а нажатие состояние инвертирует.
    TextButton(
        onClick = { expandedList = !expandedList },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(
                if (expandedList) R.string.timeline_drinks_collapse else R.string.timeline_drinks_expand,
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
    // Диалог со списком всех порций. Показывается только при expandedList == true:
    // когда условие ложно, Compose просто не создаёт этот узел.
    if (expandedList) {
        AlertDialog(
            // Закрытие тапом мимо окна или системной кнопкой «назад».
            onDismissRequest = { expandedList = false },
            title = { Text(stringResource(R.string.timeline_drinks_header)) },
            text = {
                // Содержимое прокручивается: heightIn(max = 360.dp) — предел высоты,
                // verticalScroll + rememberScrollState — сам скролл и запоминание его позиции.
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Пустой список — отдельная строка-подсказка, а не пустое окно.
                    if (drinks.isEmpty()) {
                        Text(stringResource(R.string.timeline_no_drinks), style = MaterialTheme.typography.bodySmall)
                    } else {
                        // Каждая порция — одна строка текста, собранная из шаблона:
                        // дата и время, название вида, объём и крепость.
                        drinks.sortedBy { it.atMillis }.forEach { drink ->
                            Text(
                                text = stringResource(
                                    R.string.timeline_drink_item,
                                    Formatters.formatDateTime(drink.atMillis),
                                    stringResource(drink.kind.titleRes()),
                                    Formatters.formatVolume(drink.volumeMl),
                                    Formatters.formatVolume(drink.abvPercent),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            },
            // Единственная кнопка — «Закрыть»: диалог только показывает данные.
            confirmButton = {
                TextButton(onClick = { expandedList = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

// Обратное превращение X → время: та же формула, что в xOf, но решённая относительно времени.
// Именно этой функцией жесты отвечают на вопрос «какой момент времени под пальцем».
// coerceIn(0f, 1f) — палец может уехать за пределы графика, доля при этом останется в [0, 1].
private fun xToTime(x: Float, start: Long, end: Long, width: Float): Long {
    if (width <= 0f) return start
    val ratio = (x / width).coerceIn(0f, 1f)
    return start + ((end - start) * ratio).toLong()
}

// Уровень опьянения — enum, а пользователю нужен локализованный текст, поэтому when
// превращает вариант перечисления в строку из ресурсов.
@Composable
private fun levelTitle(level: IntoxicationLevel): String = when (level) {
    IntoxicationLevel.SOBER -> stringResource(R.string.level_sober)
    IntoxicationLevel.MILD -> stringResource(R.string.level_mild)
    IntoxicationLevel.MODERATE -> stringResource(R.string.level_moderate)
    IntoxicationLevel.STRONG -> stringResource(R.string.level_strong)
    IntoxicationLevel.DANGEROUS -> stringResource(R.string.level_dangerous)
}
