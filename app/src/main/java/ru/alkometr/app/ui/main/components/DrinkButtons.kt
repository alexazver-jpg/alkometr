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

/** Иконка сосуда по виду напитка. */
fun DrinkKind.iconRes(): Int = when (this) {
    DrinkKind.SHOT -> R.drawable.ic_shot
    DrinkKind.WINE_GLASS -> R.drawable.ic_wine
    DrinkKind.MUG -> R.drawable.ic_mug
    DrinkKind.CUSTOM -> R.drawable.ic_custom
}

fun DrinkKind.titleRes(): Int = when (this) {
    DrinkKind.SHOT -> R.string.drink_shot
    DrinkKind.WINE_GLASS -> R.string.drink_wine
    DrinkKind.MUG -> R.string.drink_mug
    DrinkKind.CUSTOM -> R.string.drink_custom
}

/**
 * Кнопки напитков: тап — добавить порцию сейчас, долгое нажатие — правка пресета.
 * Кнопка «+ Свой» открывает диалог с объёмом, крепостью и временем.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            Column(
                modifier = Modifier
                    .width(90.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .combinedClickable(
                        onClick = { onAdd(preset) },
                        onLongClick = { onEdit(preset) },
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(preset.kind.iconRes()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = stringResource(preset.kind.titleRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
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

        Column(
            modifier = Modifier
                .width(80.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .combinedClickable(onClick = onCustom, onLongClick = onCustom)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.drink_custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
