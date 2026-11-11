package ru.alkometr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Две цветовые схемы — наборы «ролей» Material 3. Роль — не конкретный цвет, а смысл:
// primary (главный акцент), background (фон экрана), onX (цвет содержимого ПОВЕРХ X).
// Компоненты Material берут цвета по ролям, поэтому палитра меняется здесь,
// а не в каждом экране, и светлая/тёмная темы не требуют правок в UI-коде.

private val DarkScheme = darkColorScheme( // darkColorScheme — конструктор тёмной схемы Material 3.
    // private: снаружи файла схема не нужна, наружу отдаётся только AlkometrTheme.
    primary = Amber, // Главный акцент: кнопки, выделение, иконки в тёмной теме — яркий янтарь.
    onPrimary = Graphite, // Цвет содержимого на primary: тёмный, чтобы читалось на янтаре.
    primaryContainer = AmberDark, // «Контейнер» — приглушённый акцент для плашек, чипов, вторичных кнопок.
    onPrimaryContainer = Color.White, // Содержимое поверх контейнера — белое, на тёмном янтаре контрастно.
    secondary = LevelMild, // Второй акцент: используется реже, для менее важных элементов.
    onSecondary = Graphite, // Содержимое на вторичном акценте — тёмное (салатовый светлый).
    background = Graphite, // Фон экранов тёмной темы — графит.
    onBackground = Color(0xFFEDEDED), // Текст на фоне: почти белый, но не чистый белый — глазам мягче.
    surface = GraphiteSoft, // Поверхность карточек и панелей: светлее фона, чтобы отделяться от него.
    onSurface = Color(0xFFEDEDED), // Текст на поверхности — тот же почти белый.
    surfaceVariant = Color(0xFF33373D), // Ещё более светлая поверхность: вложенные блоки, строки списков.
    onSurfaceVariant = Color(0xFFC3C7CC), // Приглушённый текст на ней: подписи, единицы измерения.
    outline = Color(0xFF6C7278), // Рамки, разделители, неактивные контуры.
    error = LevelDangerous, // Ошибки и опасные действия — тот же красный, что у опасной степени.
    onError = Color.White, // Содержимое на красном — белое.
)

private val LightScheme = lightColorScheme( // Светлая схема: та же логика ролей, но на песке.
    primary = AmberDark, // На светлом фоне яркий янтарь блёкнет, поэтому берём затемнённый — контраст выше.
    onPrimary = Color.White, // Содержимое на тёмном янтаре — белое.
    primaryContainer = Color(0xFFFFE3B0), // Светлый янтарный контейнер: мягкая плашка под акцентный текст.
    onPrimaryContainer = Ink, // Текст на нём — основной цвет текста, читается на светло-жёлтом.
    secondary = LevelSober, // Второй акцент — зелёный (в светлой теме он контрастнее салатового).
    onSecondary = Color.White, // Содержимое на зелёном — белое.
    background = Sand, // Фон экранов — тёплый песочный.
    onBackground = Ink, // Основной текст — почти чёрный.
    surface = SandCard, // Карточки — почти белые, чуть теплее чистого белого.
    onSurface = Ink, // Текст на карточке.
    surfaceVariant = Color(0xFFEDE4D4), // Затенённая поверхность: строки списков, вложенные блоки.
    onSurfaceVariant = InkSoft, // Приглушённый текст на ней.
    outline = Color(0xFF8D8577), // Рамки и разделители — тёплый серый, а не холодный.
    error = LevelDangerous, // Ошибки — тот же красный.
    onError = Color.White, // Содержимое на красном.
)

/**
 * Тема приложения: светло-песочная и графитовая.
 * Динамические цвета Material You намеренно отключены — цветовые зоны степеней
 * опьянения должны выглядеть одинаково на всех устройствах.
 */
@Composable // @Composable: тема — часть UI-дерева, её оборачивают вокруг всего интерфейса.
fun AlkometrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // По умолчанию берём режим системы (настройка «тёмная тема»);
    // аргумент можно передать явно — например, в @Preview, чтобы увидеть обе схемы.
    content: @Composable () -> Unit, // Слот содержимого: всё, что тема оборачивает. Такой слот —
    // стандартный приём Compose: тема не знает, что именно раскрашивает.
) {
    MaterialTheme( // MaterialTheme кладёт значения в CompositionLocal-ы: любой компонент ниже
        // читает их через MaterialTheme.colorScheme / MaterialTheme.typography.
        colorScheme = if (darkTheme) DarkScheme else LightScheme, // Выбор схемы по флагу — единственная «логика» темы.
        typography = AlkometrTypography, // Шрифтовые стили из Type.kt — тоже часть темы.
        content = content, // Прокидываем содержимое внутрь, чтобы оно видело заданные цвета и шрифты.
    )
}
