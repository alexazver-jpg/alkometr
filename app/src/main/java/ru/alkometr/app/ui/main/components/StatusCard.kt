package ru.alkometr.app.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.IntoxicationLevel
import ru.alkometr.app.ui.main.MainUiState

/**
 * Статусная карточка: главный ответ приложения — можно ли за руль и когда будет можно.
 * Считается по юридическому порогу, а не по степени опьянения.
 *
 * @Composable — пометка «эта функция описывает кусок интерфейса». Compose вызывает её
 * при первой отрисовке и затем повторно (рекомпозиция) всякий раз, когда изменяются
 * прочитанные ею данные. Поэтому тело функции отвечает на вопрос «как это выглядит
 * сейчас», а не «что делать по нажатию».
 *
 * [Modifier] — неизменяемое описание внешних свойств элемента: отступов, фона, рамки,
 * обрезки, обработчиков. Сам по себе он ничего не рисует, а как бы «оборачивает»
 * элемент; вызовы в цепочке применяются по порядку (см. комментарий к цепочке ниже).
 *
 * @param state готовое состояние экрана из ViewModel: в карточке ничего не считается,
 *   кроме одного сравнения «текущий BAC против порога».
 * @param modifier модификатор от родителя: так карточке снаружи задают отступы.
 *   По умолчанию — пустой `Modifier`, то есть «без дополнительных свойств».
 */
@Composable
fun StatusCard(state: MainUiState, modifier: Modifier = Modifier) {
    // Достаём из состояния настройки и текущую степень опьянения.
    // Разбор на локальные val делает код ниже короче, а зависимости — виднее.
    val units = state.settings.units
    val threshold = state.settings.thresholdPermille
    val level = state.level
    // Главное сравнение карточки: текущий уровень алкоголя не превышает юридический порог.
    // Именно это, а не номер степени, определяет ответ «можно за руль».
    val canDrive = state.bacNow <= threshold

    // Цвет-акцент всей карточки: зелёный у трезвого состояния, красный — у опасного.
    // Цвета берём из модели IntoxicationLevel, чтобы экран и шкала были согласованы.
    val accent = if (canDrive) Color(IntoxicationLevel.SOBER.colorArgb) else Color(IntoxicationLevel.DANGEROUS.colorArgb)
    // Заголовок карточки выбирается по приоритету: пустая сессия → можно ехать →
    // есть прогноз времени → прогноза нет.
    val title: String = when {
        // Ни одной порции ещё не добавлено — показывать «можно за руль» было бы обманом.
        state.isEmptySession -> stringResource(R.string.status_no_data)
        // Порог не превышен прямо сейчас.
        canDrive -> stringResource(R.string.status_can_drive)
        // Порог превышен, но модель знает момент, когда он снова станет допустимым:
        // сообщаем «через сколько» и во сколько это будет по часам.
        state.forecast.readyAtMillis != null -> stringResource(
            R.string.status_drive_in,
            Formatters.formatDuration(state.forecast.remainingMillis ?: 0L),
            Formatters.formatClock(state.forecast.readyAtMillis),
        )

        // Крайний случай: прогноз не смог посчитать время (например, данные вне модели).
        else -> stringResource(R.string.timeline_drive_unknown)
    }

    // Вторая строка — про пик BAC. null означает «строку не показывать вовсе».
    val peakText: String? = when {
        // Нет порций — нет и пика.
        state.isEmptySession -> null
        // В окне последних 24 ч пика уже нет — не показываем «пик 0,0 ‰» в произвольный момент.
        state.peak.bacGPerL <= 0.0 -> null
        // Пик ещё впереди: говорим значение и время, когда он наступит.
        state.peakAhead -> stringResource(
            R.string.status_peak_ahead,
            Formatters.formatBac(state.peak.bacGPerL, units),
            Formatters.formatClock(state.peak.atMillis),
        )

        // Пик уже прошёл: показываем его как справку «сколько было максимум».
        else -> stringResource(
            R.string.status_peak_past,
            Formatters.formatBac(state.peak.bacGPerL, units),
            Formatters.formatClock(state.peak.atMillis),
        )
    }

    // Цепочка модификаторов применяется сверху вниз, поэтому порядок важен:
    // 1) fillMaxWidth — карточка занимает всю доступную ширину;
    // 2) clip — обрезаем всё нарисованное по скруглённому прямоугольнику;
    // 3) background — заливка 14 % акцента (очень светлая подложка);
    // 4) border — тонкая рамка 1 dp тем же акцентом, 45 % непрозрачности;
    // 5) padding — внутренние отступы: содержимое не прилипает к рамке.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Заголовок — самый важный текст карточки, поэтому titleMedium и цвет акцента.
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
        // Строка о пике появляется только если для неё есть данные (peakText != null).
        if (peakText != null) {
            Text(
                text = peakText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Дополнительное предупреждение — только для последней, опасной степени.
        // Это не юридическая подсказка, а напоминание о риске для здоровья.
        if (level == IntoxicationLevel.DANGEROUS) {
            Text(
                text = stringResource(R.string.status_danger),
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
