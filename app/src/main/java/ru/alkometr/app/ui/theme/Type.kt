package ru.alkometr.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографика: системный шрифт (никаких сторонних шрифтов),
 * крупные числа — тонкие, чтобы длинные значения вроде «2,48» помещались в центр шкалы.
 *
 * Typography — набор именованных текстовых стилей Material 3. Экраны не задают размеры
 * и начертания вручную, а берут их из MaterialTheme.typography.<стиль>: шрифт меняется
 * в одном месте и остаётся единообразным во всём приложении.
 */
val AlkometrTypography = Typography(
    displayLarge = TextStyle( // displayLarge — самый крупный стиль: для главной цифры промилле на экране.
        fontFamily = FontFamily.Default, // Системный шрифт устройства (Roboto на Android): не нужно
        // тянуть файл шрифта в APK, и текст автоматически поддерживает все языки системы.
        fontWeight = FontWeight.Light, // Тонкое начертание: крупная цифра не «давит» и не расплывается.
        fontSize = 52.sp, // Размер в sp (scale-independent pixels) — он учитывает системный масштаб текста,
        // поэтому при увеличении шрифта в настройках доступности надпись тоже вырастет.
        lineHeight = 56.sp, // Высота строки чуть больше кегля — цифры не обрезаются сверху и снизу.
        letterSpacing = (-2).sp, // Отрицательный трекинг поджимает крупные цифры, чтобы «2,48» влезло в центр.
    ),
    headlineMedium = TextStyle( // headlineMedium — заголовок раздела/экрана.
        fontFamily = FontFamily.Default, // Шрифт и правила размеров те же, что выше; ниже описываем отличия.
        fontWeight = FontWeight.SemiBold, // Полужирный — заголовок явно отделяется от обычного текста.
        fontSize = 22.sp, // 22 sp — заметно крупнее body, но не как главная цифра.
        lineHeight = 28.sp, // Межстрочный интервал с запасом для двухстрочных заголовков.
    ),
    titleLarge = TextStyle( // titleLarge — заголовок верхней панели (TopAppBar).
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold, // Та же логика: заголовок должен «звучать» жирнее текста.
        fontSize = 20.sp, // Чуть меньше headlineMedium: иерархия заголовков сохраняется.
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle( // titleMedium — подзаголовок, название карточки или секции в диалоге.
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium, // Medium вместо SemiBold: мягче, это не главный заголовок.
        fontSize = 16.sp, // Равен размеру основного текста — различие только в начертании.
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle( // bodyLarge — крупный основной текст: длинные абзацы, дисклеймер.
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal, // Обычное начертание: текст для чтения не должен быть жирным.
        fontSize = 16.sp, // Комфортный для чтения размер на телефоне.
        lineHeight = 22.sp, // Достаточный интервал, чтобы строки не слипались.
    ),
    bodyMedium = TextStyle( // bodyMedium — стандартный текст интерфейса и подписи кнопок.
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, // 14 sp — плотнее, зато вмещает больше данных на экране.
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle( // bodySmall — пояснения, сноски, единицы измерения (мл, %, г).
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp, // Мелкий текст; контраст обеспечивается цветом onSurfaceVariant, а не жирностью.
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle( // labelLarge — текст кнопок и меток: он короткий, поэтому важна плотность.
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium, // Medium делает подпись кнопки чётче без «жирности» заголовка.
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle( // labelSmall — самые мелкие метки: оси графика, бейджи.
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium, // Мелкий текст слегка уплотняем начертанием, иначе он «плывёт».
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
