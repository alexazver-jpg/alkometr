// Build-скрипт модуля app — единственного модуля приложения.
// Здесь описано, как именно собрать APK: под какое SDK, с какими зависимостями.
// Все версии берутся из gradle/libs.versions.toml через alias(...) и libs.*.

plugins { // Плагины этого модуля: они и включают Android-сборку именно здесь.
    // AGP: превращает код и ресурсы в APK/AAB, знает про Android SDK и ресурсы.
    alias(libs.plugins.android.application)
    // Компилятор Compose — обязателен: он превращает @Composable-функции в реальный UI.
    alias(libs.plugins.compose.compiler)
    // Плагин kotlinx.serialization генерирует код сериализации в JSON (резервная копия).
    alias(libs.plugins.kotlin.serialization)
}

android { // Блок настроек Android-плагина: всё, что относится к Android-сборке.
    namespace = "ru.alkometr.app" // Пакет, в котором генерируются R (ресурсы) и BuildConfig;
    // он же используется как база для относительных имён классов в манифесте (".MainActivity").
    compileSdk = 37 // Версия Android SDK, ПРОТИВ которой компилируется код: даёт доступ к новым API.
    // От неё зависит, какие методы и классы вообще видны компилятору.

    defaultConfig { // Настройки по умолчанию для всех вариантов сборки (debug и release).
        applicationId = "ru.alkometr.app" // Уникальный идентификатор приложения на устройстве и в Play.
        // Именно он (а не namespace) определяет, как приложение выглядит для системы.
        minSdk = 26 // Минимальная версия Android (26 = Android 8.0), где приложение установится.
        // Всё, что новее minSdk, обязано быть обёрнуто проверками версии или AndroidX-совместимостью.
        targetSdk = 36 // Версия Android, под правила которой приложение заявлено.
        // Чем выше targetSdk, тем строже системные ограничения, но и тем современнее поведение.
        versionCode = 1 // Целое число для магазина: каждый релиз обязан быть больше предыдущего.
        versionName = "0.1.0" // Версия «для людей»: показывается в настройках и в магазине.
    }

    buildFeatures { // Включает необязательные возможности сборки.
        compose = true // Включает Compose: без этого плагин не станет обрабатывать @Composable.
    }

    compileOptions { // Параметры компиляции Java-байткода.
        sourceCompatibility = JavaVersion.VERSION_17 // Языковой уровень исходников — Java 17.
        targetCompatibility = JavaVersion.VERSION_17 // Уровень байткода на выходе — тоже Java 17.
    }
}

dependencies { // Зависимости — внешние библиотеки, код которых попадёт в приложение.
    implementation(platform(libs.androidx.compose.bom)) // platform() = «это BOM, а не обычная
    // библиотека»: строка не добавляет код, а задаёт версии всем compose-зависимостям ниже.
    implementation(libs.androidx.activity.compose) // setContent { } — точка входа Compose в Activity.
    implementation(libs.androidx.lifecycle.viewmodel.compose) // viewModel() внутри @Composable.
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle для StateFlow.
    implementation(libs.androidx.datastore.preferences) // Хранение настроек и истории на диске.
    implementation(libs.kotlinx.serialization.json) // JSON для резервной копии данных.
    implementation(libs.androidx.compose.ui) // Базовые элементы Compose: Modifier, State, Layout.
    implementation(libs.androidx.compose.ui.graphics) // Color и рисование на Canvas (шкала, график).
    implementation(libs.androidx.compose.material3) // Компоненты и тема Material 3.
    implementation(libs.androidx.compose.material.icons.core) // Базовые иконки (например, ArrowBack).
    implementation(libs.androidx.compose.ui.tooling.preview) // Аннотация @Preview для IDE.
    debugImplementation(libs.androidx.compose.ui.tooling) // Только в debug-сборке: превью и инспектор
    // в Android Studio; в релизный APK этот код не попадает, поэтому он не увеличивает размер.

    testImplementation(libs.junit) // Только для юнит-тестов на JVM: в приложение не попадает вообще.
}
