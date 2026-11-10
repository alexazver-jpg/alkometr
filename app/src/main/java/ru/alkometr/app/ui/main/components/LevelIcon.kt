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

/** Сопоставление степени опьянения и собственной иконки-персонажа. */
fun IntoxicationLevel.iconRes(): Int = when (this) {
    IntoxicationLevel.SOBER -> R.drawable.ic_level_1
    IntoxicationLevel.MILD -> R.drawable.ic_level_2
    IntoxicationLevel.MODERATE -> R.drawable.ic_level_3
    IntoxicationLevel.STRONG -> R.drawable.ic_level_4
    IntoxicationLevel.DANGEROUS -> R.drawable.ic_level_5
}

fun IntoxicationLevel.titleRes(): Int = when (this) {
    IntoxicationLevel.SOBER -> R.string.level_sober
    IntoxicationLevel.MILD -> R.string.level_mild
    IntoxicationLevel.MODERATE -> R.string.level_moderate
    IntoxicationLevel.STRONG -> R.string.level_strong
    IntoxicationLevel.DANGEROUS -> R.string.level_dangerous
}

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
 */
@Composable
fun LevelIcon(
    level: IntoxicationLevel,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val description = stringResource(
        R.string.level_content_description,
        stringResource(level.titleRes()),
    )

    val scale = if (level == IntoxicationLevel.DANGEROUS) {
        val transition = rememberInfiniteTransition(label = "dangerPulse")
        val value by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dangerScale",
        )
        value
    } else {
        1.0f
    }

    val darkTheme = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (darkTheme) Color(0xFFF6EFE3) else Color.Transparent)
            .padding(if (darkTheme) size * 0.08f else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(level.iconRes()),
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
