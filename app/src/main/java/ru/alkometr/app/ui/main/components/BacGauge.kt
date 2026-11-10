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

private const val START_ANGLE = 150f
private const val SWEEP_ANGLE = 240f

/**
 * Дуговая шкала уровня алкоголя: 240°, пять цветовых зон по границам степеней,
 * маркер-стрелка, пунктирная линия юридического порога и центральное значение.
 */
@Composable
fun BacGauge(
    bac: Double,
    level: IntoxicationLevel,
    threshold: Double,
    units: Units,
    modifier: Modifier = Modifier,
) {
    val animatedBac by animateFloatAsState(
        targetValue = bac.toFloat(),
        animationSpec = tween(durationMillis = 400),
        label = "bacMarker",
    )

    val levelColor = Color(level.colorArgb)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val textPaint = remember(labelColor, density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }
    val thresholdPaint = remember(labelColor, density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
        }
    }
    val thresholdLabel = stringResource(
        R.string.timeline_threshold,
        Formatters.formatBac(threshold, units),
    )

    val dangerPulse = if (level == IntoxicationLevel.DANGEROUS) {
        val transition = rememberInfiniteTransition(label = "dangerZone")
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dangerAlpha",
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height * 0.62f
            val radius = min(size.width / 2f - 14.dp.toPx(), size.height * 0.60f)

            // Фоновая дорожка
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Пять цветовых зон
            val boundaries = listOf(
                0.0,
                IntoxicationLevel.MILD.minBac,
                IntoxicationLevel.MODERATE.minBac,
                IntoxicationLevel.STRONG.minBac,
                IntoxicationLevel.DANGEROUS.minBac,
                IntoxicationLevel.SCALE_MAX,
            )
            for (i in 0 until boundaries.lastIndex) {
                val from = START_ANGLE + SWEEP_ANGLE * (boundaries[i] / IntoxicationLevel.SCALE_MAX).toFloat()
                val to = START_ANGLE + SWEEP_ANGLE * (boundaries[i + 1] / IntoxicationLevel.SCALE_MAX).toFloat()
                val zoneLevel = IntoxicationLevel.entries[i]
                drawArc(
                    color = Color(zoneLevel.colorArgb),
                    startAngle = from + 0.8f,
                    sweepAngle = (to - from) - 1.6f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }

            // Пульсация опасной зоны
            if (dangerPulse != null) {
                drawArc(
                    color = Color(IntoxicationLevel.DANGEROUS.colorArgb).copy(alpha = dangerPulse.value),
                    startAngle = START_ANGLE + SWEEP_ANGLE * 0.625f,
                    sweepAngle = SWEEP_ANGLE * 0.375f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke + 6.dp.toPx()),
                )
            }

            // Подписи шкалы: 0 … 4 ‰, в выбранных единицах, внутри дуги
            for (i in 0..4) {
                val value = IntoxicationLevel.SCALE_MAX * i / 4.0
                val angle = START_ANGLE + SWEEP_ANGLE * (value / IntoxicationLevel.SCALE_MAX).toFloat()
                val rad = PI * angle / 180.0
                val labelRadius = radius - stroke - 10.dp.toPx()
                val lx = cx + labelRadius * cos(rad).toFloat()
                val ly = cy + labelRadius * sin(rad).toFloat()
                val text = Formatters.formatValue(value, units)
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    lx - textPaint.measureText(text) / 2f,
                    ly + textPaint.textSize / 3f,
                    textPaint,
                )
            }

            // Линия юридического порога
            if (threshold in 0.0..IntoxicationLevel.SCALE_MAX) {
                val angle = START_ANGLE + SWEEP_ANGLE * (threshold / IntoxicationLevel.SCALE_MAX).toFloat()
                drawArc(
                    color = labelColor,
                    startAngle = angle - 1.2f,
                    sweepAngle = 2.4f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
                val rad = PI * angle / 180.0
                val tx = cx + (radius - 34.dp.toPx()) * cos(rad).toFloat()
                val ty = cy + (radius - 34.dp.toPx()) * sin(rad).toFloat()
                drawContext.canvas.nativeCanvas.drawText(
                    thresholdLabel,
                    tx - thresholdPaint.measureText(thresholdLabel) / 2f,
                    ty,
                    thresholdPaint,
                )
            }

            // Маркер-стрелка
            val clamped = animatedBac.toDouble().coerceIn(0.0, IntoxicationLevel.SCALE_MAX)
            val markerAngle = START_ANGLE + SWEEP_ANGLE * (clamped / IntoxicationLevel.SCALE_MAX).toFloat()
            val markerRad = PI * markerAngle / 180.0
            val dirX = cos(markerRad).toFloat()
            val dirY = sin(markerRad).toFloat()
            val tip = Offset(cx + radius * dirX, cy + radius * dirY)
            val tail = Offset(cx + (radius - 30.dp.toPx()) * dirX, cy + (radius - 30.dp.toPx()) * dirY)

            drawLine(
                color = levelColor,
                start = tail,
                end = tip,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
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
            drawPath(arrow, levelColor)
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = tip)
            drawCircle(color = levelColor, radius = 4.dp.toPx(), center = tip, style = Stroke(2.dp.toPx()))
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = Formatters.formatValue(bac, units),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = units.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }
            Crossfade(targetState = level, animationSpec = tween(250), label = "levelSwap") { current ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    LevelIcon(level = current, size = 42.dp)
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
