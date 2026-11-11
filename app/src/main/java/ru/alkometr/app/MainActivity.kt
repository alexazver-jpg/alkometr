package ru.alkometr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.alkometr.app.ui.AppRoot
import ru.alkometr.app.ui.theme.AlkometrTheme

// Activity — единственная точка входа в UI, которую запускает система (см. AndroidManifest).
// Наследуемся от ComponentActivity, а не от старого AppCompatActivity: он умеет работать
// с Compose и не требует XML-разметки.
class MainActivity : ComponentActivity() {
    // onCreate — первый шаг жизненного цикла Activity, здесь мы готовим экран.
    // `savedInstanceState` — Bundle с сохранённым состоянием (например, после поворота экрана);
    // при самом первом запуске он равен null.
    override fun onCreate(savedInstanceState: Bundle?) {
        // Разрешает рисовать контент под системными панелями (статус-бар и панель навигации),
        // а отступы под них добавляет Compose. Вызов идёт до super.onCreate, чтобы окно
        // успело настроиться до отрисовки первого кадра.
        enableEdgeToEdge()
        // Обязательный вызов базовой реализации: без него Activity упадёт с исключением.
        super.onCreate(savedInstanceState)
        // setContent — мост из мира Activity в мир Compose: внутри блока описывается всё
        // дерево интерфейса. Код не «рисует» вручную — Compose сам перерисовывает то,
        // что изменилось, когда меняются данные.
        setContent {
            // Тема (цвета, шрифты) оборачивает весь UI: вложенные экраны берут цвета из неё
            // и автоматически поддерживают светлую/тёмную тему.
            AlkometrTheme {
                // Корневой composable: навигация и общая обвязка экранов.
                // Activity знает только о нём, детали экранов её не касаются.
                AppRoot()
            }
        }
    }
}
