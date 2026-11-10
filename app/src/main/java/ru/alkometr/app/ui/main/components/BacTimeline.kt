package ru.alkometr.app.ui.main.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

/**
 * Шкала времени: кривая уровня, порции, линия порога и прогноз.
 *
 * Управление: палец по графику двигает курсор (scrub), два пальца — прокрутка и щипок,
 * кнопки «+»/«−» — масштаб, «сейчас» — вернуть окно к текущему моменту.
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
    var windowStart by remember { mutableStateOf(initialStartMillis) }
    var windowEnd by remember { mutableStateOf(initialEndMillis) }
    var selectedMillis by remember { mutableStateOf<Long?>(null) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var expandedList by remember { mutableStateOf(false) }

    // При смене состава порций показываем окно заново — но не сбрасываем масштаб на каждом тике.
    LaunchedEffect(drinks.size, drinks.firstOrNull()?.atMillis) {
        windowStart = initialStartMillis
        windowEnd = initialEndMillis
    }

    val density = LocalDensity.current
    val axisPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
        }
    }
    val smallPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 9.sp.toPx() }
        }
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val thresholdColor = MaterialTheme.colorScheme.error
    val nowColor = MaterialTheme.colorScheme.primary
    val iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

    val shotIcon = painterResource(R.drawable.ic_shot)
    val wineIcon = painterResource(R.drawable.ic_wine)
    val mugIcon = painterResource(R.drawable.ic_mug)
    val customIcon = painterResource(R.drawable.ic_custom)

    val axisTextColor = onSurfaceVariant.toArgb()
    axisPaint.color = axisTextColor
    smallPaint.color = axisTextColor

    val nowLabel = stringResource(R.string.timeline_now)
    val description = stringResource(R.string.timeline_content_description)
    val thresholdLabel = stringResource(
        R.string.timeline_threshold,
        Formatters.formatBac(threshold, units),
    )

    val yMax = max(max(curve.peak().bacGPerL * 1.15, threshold * 1.6), 0.5)

    val selected = selectedMillis
    if (selected != null) {
        val value = curve.valueAt(selected)
        val level = IntoxicationLevel.forBac(value)
        val levelText = levelTitle(level)
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
        val ready = if (selected >= nowMillis && value > threshold) {
            BacEngine.timeToThreshold(drinks, params, selected, threshold)
        } else {
            null
        }
        val tail = if (ready != null) {
            " · " + stringResource(
                R.string.timeline_scrub_to_threshold,
                Formatters.formatDuration(ready - selected),
            )
        } else {
            ""
        }
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

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (widthPx > 0f) selectedMillis = xToTime(down.position.x, windowStart, windowEnd, widthPx)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroidX = pressed.map { it.position.x }.average().toFloat()
                            val span = (windowEnd - windowStart).coerceAtLeast(1L)
                            val anchor = windowStart + (centroidX / widthPx * span).toLong()
                            val newSpan = (span / zoom).toLong().coerceIn(30 * MINUTE, 48 * HOUR)
                            var newStart = anchor -
                                (centroidX / widthPx * newSpan).toLong() -
                                (pan.x / widthPx * newSpan).toLong()
                            newStart = newStart.coerceIn(
                                curve.startMillis - 12 * HOUR,
                                (curve.endMillis - newSpan).coerceAtLeast(curve.startMillis - 12 * HOUR),
                            )
                            windowStart = newStart
                            windowEnd = newStart + newSpan
                            pressed.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            selectedMillis = xToTime(change.position.x, windowStart, windowEnd, widthPx)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val chartTop = 16.dp.toPx()
        val chartBottom = 132.dp.toPx()
        val chartHeight = chartBottom - chartTop
        val span = (windowEnd - windowStart).coerceAtLeast(1L)

        fun xOf(millis: Long): Float =
            left + (right - left) * ((millis - windowStart).toDouble() / span.toDouble()).toFloat()

        fun yOf(bac: Double): Float =
            chartTop + chartHeight * (1.0 - (bac / yMax).coerceIn(0.0, 1.0)).toFloat()

        // Сетка по времени
        val stepMinutes = when {
            span <= 2 * HOUR -> 15L
            span <= 5 * HOUR -> 30L
            span <= 10 * HOUR -> 60L
            span <= 20 * HOUR -> 120L
            else -> 240L
        }
        var tick = (windowStart / (stepMinutes * MINUTE)) * (stepMinutes * MINUTE)
        while (tick <= windowEnd) {
            val x = xOf(tick)
            if (x in left..right) {
                drawLine(gridColor, Offset(x, chartTop), Offset(x, chartBottom), strokeWidth = 1f)
                val label = Formatters.formatClock(tick)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x - axisPaint.measureText(label) / 2f,
                    chartBottom + 12.dp.toPx(),
                    axisPaint,
                )
            }
            tick += stepMinutes * MINUTE
        }

        // Горизонтальная сетка по концентрации
        val yStep = when {
            yMax <= 0.6 -> 0.1
            yMax <= 1.5 -> 0.25
            yMax <= 3.0 -> 0.5
            else -> 1.0
        }
        var line = 0.0
        while (line <= yMax) {
            val y = yOf(line)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val label = Formatters.formatValue(line, units)
            drawContext.canvas.nativeCanvas.drawText(label, left + 2.dp.toPx(), y - 3.dp.toPx(), smallPaint)
            line += yStep
        }

        // Линия порога
        val thresholdY = yOf(threshold)
        drawLine(
            color = thresholdColor.copy(alpha = 0.8f),
            start = Offset(left, thresholdY),
            end = Offset(right, thresholdY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        val thresholdWidth = smallPaint.measureText(thresholdLabel)
        drawContext.canvas.nativeCanvas.drawText(
            thresholdLabel,
            right - thresholdWidth,
            (thresholdY - 4.dp.toPx()).coerceAtLeast(chartTop + 8.dp.toPx()),
            smallPaint,
        )

        val points = curve.all
        val currentColor = Color(IntoxicationLevel.forBac(curve.valueAt(nowMillis)).colorArgb)

        if (points.isNotEmpty()) {
            val pastPath = Path()
            val fillPath = Path()
            var startedPast = false
            for (point in points) {
                if (point.atMillis > nowMillis) break
                val x = xOf(point.atMillis)
                val y = yOf(point.bacGPerL)
                if (!startedPast) {
                    pastPath.moveTo(x, y)
                    fillPath.moveTo(x, chartBottom)
                    fillPath.lineTo(x, y)
                    startedPast = true
                } else {
                    pastPath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            if (startedPast) {
                val nowX = xOf(min(nowMillis, windowEnd))
                val nowY = yOf(curve.valueAt(nowMillis))
                pastPath.lineTo(nowX, nowY)
                fillPath.lineTo(nowX, nowY)
                fillPath.lineTo(nowX, chartBottom)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(currentColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = chartTop,
                        endY = chartBottom,
                    ),
                )
                drawPath(
                    path = pastPath,
                    color = currentColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

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

        // Линия «сейчас»
        if (nowMillis in windowStart..windowEnd) {
            val x = xOf(nowMillis)
            drawLine(
                color = nowColor.copy(alpha = 0.55f),
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1.5f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                nowLabel,
                (x + 4.dp.toPx()).coerceAtMost(right - axisPaint.measureText(nowLabel)),
                chartTop + 10.dp.toPx(),
                axisPaint,
            )
        }

        // Точка пересечения порога с прогнозом
        val driveAt = if (curve.valueAt(nowMillis) > threshold) {
            points.firstOrNull { it.atMillis > nowMillis && it.bacGPerL <= threshold }
        } else {
            null
        }
        if (driveAt != null && driveAt.atMillis in windowStart..windowEnd) {
            val x = xOf(driveAt.atMillis)
            val y = yOf(driveAt.bacGPerL)
            drawCircle(color = surfaceColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
            drawCircle(
                color = thresholdColor,
                radius = 4.5.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(2.dp.toPx()),
            )
            val label = Formatters.formatClock(driveAt.atMillis)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                (x + 5.dp.toPx()).coerceAtMost(right - axisPaint.measureText(label)),
                (y - 6.dp.toPx()).coerceAtLeast(chartTop + 8.dp.toPx()),
                axisPaint,
            )
        }

        // Маркеры порций под осью: близкие по времени разводим на две строки
        var lastMarkerX = -1000f
        var markerRow = 0
        drinks.sortedBy { it.atMillis }.forEach { drink ->
            if (drink.atMillis in windowStart..windowEnd) {
                val x = xOf(drink.atMillis)
                markerRow = if (kotlin.math.abs(x - lastMarkerX) < 44.dp.toPx()) 1 - markerRow else 0
                lastMarkerX = x
                val rowOffset = markerRow * 12.dp.toPx()
                val painter = when (drink.kind) {
                    ru.alkometr.app.core.model.DrinkKind.SHOT -> shotIcon
                    ru.alkometr.app.core.model.DrinkKind.WINE_GLASS -> wineIcon
                    ru.alkometr.app.core.model.DrinkKind.MUG -> mugIcon
                    ru.alkometr.app.core.model.DrinkKind.CUSTOM -> customIcon
                }
                val icon = 16.dp.toPx()
                val strength = (drink.abvPercent / 40.0).coerceIn(0.0, 1.0).toFloat()
                val drinkTint = lerp(Color(0xFFE0A030), Color(0xFF8D3B12), strength)
                translate(left = x - icon / 2, top = chartBottom + 16.dp.toPx() + rowOffset) {
                    with(painter) {
                        draw(size = Size(icon, icon), colorFilter = ColorFilter.tint(drinkTint))
                    }
                }
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

        // Курсор выбранного момента
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

    // Нижняя строка: когда за руль, масштаб и возврат к «сейчас»
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
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            windowStart = nowMillis - 6 * HOUR
            windowEnd = nowMillis + 6 * HOUR
        }) {
            Text(stringResource(R.string.timeline_reset), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = {
            val span = (windowEnd - windowStart).coerceAtLeast(1L)
            val center = (windowStart + windowEnd) / 2
            val newSpan = (span * 2).coerceAtMost(48 * HOUR)
            windowStart = center - newSpan / 2
            windowEnd = center + newSpan / 2
        }) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
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
    if (expandedList) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(8.dp),
        ) {
            if (drinks.isEmpty()) {
                Text(stringResource(R.string.timeline_no_drinks), style = MaterialTheme.typography.bodySmall)
            } else {
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
    }
}

private fun xToTime(x: Float, start: Long, end: Long, width: Float): Long {
    if (width <= 0f) return start
    val ratio = (x / width).coerceIn(0f, 1f)
    return start + ((end - start) * ratio).toLong()
}

@Composable
private fun levelTitle(level: IntoxicationLevel): String = when (level) {
    IntoxicationLevel.SOBER -> stringResource(R.string.level_sober)
    IntoxicationLevel.MILD -> stringResource(R.string.level_mild)
    IntoxicationLevel.MODERATE -> stringResource(R.string.level_moderate)
    IntoxicationLevel.STRONG -> stringResource(R.string.level_strong)
    IntoxicationLevel.DANGEROUS -> stringResource(R.string.level_dangerous)
}
