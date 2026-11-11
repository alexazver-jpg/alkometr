package ru.alkometr.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.alkometr.app.R
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.AppSettings
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.DrinkPreset
import ru.alkometr.app.core.model.FoodIntake
import ru.alkometr.app.core.model.Metabolism
import ru.alkometr.app.core.model.Sex
import ru.alkometr.app.core.model.ThresholdPreset
import ru.alkometr.app.core.model.Units
import ru.alkometr.app.core.model.UserProfile
import ru.alkometr.app.data.BackupCodec
import ru.alkometr.app.ui.main.components.DrinkDialog
import ru.alkometr.app.ui.main.components.iconRes
import ru.alkometr.app.ui.main.components.titleRes
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Настройки: профиль, напитки, модель, порог и единицы, данные, о приложении.
 * Любое изменение сразу сохраняется и пересчитывает кривую.
 *
 * Почему на экране нет кнопки «Сохранить»: этот экран ничего не хранит у себя.
 * Он получает сверху готовый снимок [settings] и на каждое действие отдаёт наверх
 * новую копию этого снимка через [onSave]. Верх (ViewModel + репозиторий) записывает
 * копию в хранилище и возвращает её обратно — экран перерисовывается уже с новыми
 * данными, а все остальные экраны получают свежие параметры для расчёта кривой.
 * Отсюда три плюса: нет «несохранённого» состояния, приложение можно закрыть в любой
 * момент без потери ввода, и все экраны видят одни и те же числа.
 *
 * @param settings текущие настройки — единственный источник правды для расчёта.
 * @param drinks список напитков пользователя; параметр объявлен для единообразия
 *   с другими экранами, но в этом файле не используется: пресеты берём из [AppSettings.presets].
 * @param backupJson лямбда, возвращающая готовую JSON-строку резервной копии.
 * @param onSave вызывается на каждое изменение; принимает новую неизменяемую копию настроек.
 * @param onApplyBackup применяет разобранную резервную копию.
 * @param onClearHistory очищает историю замеров, не трогая настройки.
 * @param onResetAll сбрасывает приложение к заводским значениям.
 * @param onBack возврат на предыдущий экран.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    drinks: List<Drink>,
    backupJson: () -> String,
    onSave: (AppSettings) -> Unit,
    onApplyBackup: (ru.alkometr.app.data.Backup) -> Unit,
    onClearHistory: () -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    // LocalContext — мост из мира Compose в мир Android: только отсюда можно взять
    // contentResolver, через который читаются и пишутся файлы по Uri.
    val context = LocalContext.current
    // rememberCoroutineScope даёт корутину, привязанную к этому экрану: показ snackbar —
    // suspend-функция, её нельзя вызвать из обычного колбэка. scope отменится вместе с экраном.
    val scope = rememberCoroutineScope()
    // SnackbarHostState — очередь всплывающих сообщений внизу экрана. remember нужен,
    // чтобы объект не пересоздавался при каждой рекомпозиции (иначе сообщения терялись бы).
    val snackbar = remember { SnackbarHostState() }
    // Дальше — чисто UI-состояние: что сейчас показано и какой диалог открыт.
    // Оно намеренно НЕ попадает в AppSettings: это не настройка пользователя, а временный
    // вид экрана. remember переживает рекомпозицию, но не перезапуск процесса — так и надо.
    var showAdvanced by remember { mutableStateOf(false) }  // раскрыт блок «продвинутых» коэффициентов
    var editingPreset by remember { mutableStateOf<DrinkPreset?>(null) }  // null = диалог редактирования закрыт
    var newPreset by remember { mutableStateOf(false) }  // открыт диалог создания пресета
    var confirmClear by remember { mutableStateOf(false) }  // открыт вопрос «очистить историю?»
    var confirmReset by remember { mutableStateOf(false) }  // открыт вопрос «сбросить всё?»
    var showDisclaimer by remember { mutableStateOf(false) }  // открыт текст дисклеймера
    var importError by remember { mutableStateOf<String?>(null) }  // текст ошибки импорта; null = ошибки нет

    // stringResource — @Composable-функция, а колбэки Activity Result и корутины таковыми
    // не являются. Поэтому все нужные тексты достаём заранее, ещё в композиции.
    val savedMessage = stringResource(R.string.settings_saved)
    val exportMessage = stringResource(R.string.settings_export_done)
    val exportErrorMessage = stringResource(R.string.settings_export_error)
    val importMessage = stringResource(R.string.settings_import_done)

    // Экспорт файла. rememberLauncherForActivityResult регистрирует контракт Activity Result API
    // и возвращает объект с методом launch(). Контракт CreateDocument("application/json") открывает
    // системный диалог «куда сохранить» и создаёт там новый документ. Приложение при этом не просит
    // доступ ко всему хранилищу: SAF (Storage Access Framework) выдаёт Uri только на выбранный файл.
    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),  // MIME создаваемого файла
    ) { uri ->
        // Колбэк приходит либо с Uri, либо с null — если пользователь передумал и закрыл диалог.
        if (uri != null) {
            // runCatching превращает возможные ошибки ввода-вывода в Result: сбой записи
            // не должен ронять приложение, о нём достаточно сообщить в snackbar.
            val saved = runCatching {
                // openOutputStream(uri) — поток записи в выбранный документ.
                // null значит «провайдер не дал поток» — считаем это неудачей.
                val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
                // use {} гарантированно закрывает поток даже при исключении,
                // иначе система может не зафиксировать файл целиком.
                stream.use { it.write(backupJson().toByteArray(Charsets.UTF_8)) }
                // Дошли досюда без исключения — запись удалась.
                true
            }.getOrDefault(false)  // исключение внутри => false, наружу ничего не летит
            scope.launch {
                // showSnackbar — suspend-функция, поэтому запускаем корутину.
                // Сообщаем результат: «экспортировано» или «ошибка экспорта».
                snackbar.showSnackbar(if (saved) exportMessage else exportErrorMessage)
            }
        }
    }

    // Импорт файла. OpenDocument() — контракт «открыть существующий документ».
    // Массив MIME перечисляет, какие файлы показывать в системном выборе. Одного
    // application/json мало: часть файловых менеджеров отдаёт .json как text/plain
    // или вообще application/octet-stream, поэтому в конце перечислена универсальная маска.
    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {  // null = пользователь отменил выбор
            runCatching {
                // openInputStream + bufferedReader + use: читаем текст целиком и сразу закрываем поток.
                // ?: error(...) — недоступный Uri или пустой ответ считаем ошибкой импорта.
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Пустой файл")
                // BackupCodec.decode разбирает JSON в модель Backup. На битом JSON он бросит
                // исключение, и его поймает runCatching ниже — падения не будет.
                BackupCodec.decode(text)
            }.onSuccess { backup ->
                // Разбор удался: отдаём бэкап наверх, там его применит репозиторий.
                onApplyBackup(backup)
                scope.launch { snackbar.showSnackbar(importMessage) }
            }.onFailure { error ->
                // Ошибку не глотаем: показываем её текст в AlertDialog (блок importError в конце файла).
                importError = error.message
            }
        }
    }

    // Версию приложения читаем у PackageManager. remember — чтобы не дёргать систему
    // на каждой рекомпозиции. runCatching + getOrNull: имя пакета теоретически может
    // отсутствовать (например, в тестовой сборке) — тогда показываем запасное "0.1.0".
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    // Scaffold — каркас экрана Material 3: сам расставляет верхнюю панель, хост snackbar
    // и контент, а нам отдаёт готовые отступы.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },  // место, где реально появятся сообщения из snackbar
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    // Стрелка «назад» просто закрывает экран: сохранять нечего,
                    // все изменения уже улетели наверх в момент ввода.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,  // панель сливается с фоном экрана
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // padding — отступы, занятые верхней панелью и snackbar. Применяем их ПЕРВЫМ
        // модификатором, иначе контент уедет под панель.
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())  // экран длиннее телефона — разрешаем прокрутку
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),  // одинаковый зазор между секциями
        ) {
            // --- Профиль ---
            // Section — локальная обёртка (см. ниже): карточка с заголовком, фоном и отступами.
            Section(title = stringResource(R.string.settings_profile)) {
                Text(stringResource(R.string.settings_weight), style = MaterialTheme.typography.bodyMedium)
                // Slider и NumberField ниже правят одно и то же поле веса двумя способами:
                // пальцем «на глаз» и цифрами точно. Оба пишут в одни настройки, поэтому
                // всегда показывают согласованные значения.
                Slider(
                    value = settings.profile.weightKg.toFloat(),  // у Slider Float-диапазон, вес хранится Double
                    onValueChange = { value ->
                        // Локальной копии нет: каждое движение ползунка сразу уходит наверх.
                        // roundToInt().toDouble() — вес храним целым числом кг, дробные доли отбрасываем.
                        onSave(settings.copy(profile = settings.profile.copy(weightKg = value.roundToInt().toDouble())))
                    },
                    colors = brandSliderColors(),
                    valueRange = 30f..200f,  // физиологически осмысленные границы
                    steps = 169,  // steps — промежуточные остановки; (200-30)-1 = 169 даёт шаг ровно 1 кг
                )
                NumberField(
                    label = stringResource(R.string.settings_weight),
                    value = settings.profile.weightKg,
                    range = 30.0..200.0,  // то же ограничение, что у слайдера: ввод вне диапазона не сохраняется
                    onValueChange = { weight ->
                        // copy() у data-класса делает изменённую копию, не мутируя исходный объект.
                        // Compose видит новую ссылку и перерисовывает экран — мутации он бы не заметил.
                        onSave(settings.copy(profile = settings.profile.copy(weightKg = weight)))
                    },
                )
                Text(
                    text = stringResource(R.string.settings_sex),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),  // чуть отодвигаем от поля веса
                )
                // FlowRow раскладывает «чипы» в строку и переносит их на следующую,
                // если не влезли по ширине. Это экспериментальный API — отсюда @OptIn над экраном.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Sex.entries — все значения enum: список вариантов не дублируем руками,
                    // новый вариант автоматически появится в интерфейсе.
                    Sex.entries.forEach { option ->
                        // FilterChip — «таблетка-переключатель» для выбора одного варианта из группы.
                        FilterChip(
                            colors = brandChipColors(),
                            selected = settings.profile.sex == option,  // состояние читаем из настроек, а не из локальной переменной
                            onClick = {
                                // Выбор сразу сохраняется: отдельного шага «применить» нет.
                                onSave(settings.copy(profile = settings.profile.copy(sex = option)))
                            },
                            label = {
                                Text(
                                    // when по enum превращается в id строкового ресурса: тексты лежат
                                    // в strings.xml, поэтому их можно перевести, не меняя код.
                                    stringResource(
                                        when (option) {
                                            Sex.MALE -> R.string.settings_sex_male
                                            Sex.FEMALE -> R.string.settings_sex_female
                                            Sex.UNSPECIFIED -> R.string.settings_sex_unspecified
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_sex_hint),
                    style = MaterialTheme.typography.bodySmall,  // пояснение — мелким шрифтом
                    color = MaterialTheme.colorScheme.onSurfaceVariant,  // и приглушённым цветом, чтобы не спорить с полями
                )
                // Рост и возраст необязательны: пустое поле = «не знаю», расчёт пойдёт по средним значениям.
                OptionalIntField(
                    label = stringResource(R.string.settings_height),
                    value = settings.profile.heightCm,
                    range = UserProfile.HEIGHT_RANGE,  // границы берём из модели, чтобы не расходиться с валидацией
                    onValueChange = { height ->
                        onSave(settings.copy(profile = settings.profile.copy(heightCm = height)))
                    },
                )
                OptionalIntField(
                    label = stringResource(R.string.settings_age),
                    value = settings.profile.ageYears,
                    range = UserProfile.AGE_RANGE,
                    onValueChange = { age ->
                        onSave(settings.copy(profile = settings.profile.copy(ageYears = age)))
                    },
                )
            }

            // --- Напитки ---
            Section(title = stringResource(R.string.settings_drinks)) {
                Text(
                    text = stringResource(R.string.settings_drinks_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Пресеты — это заготовки напитков, которые потом выбираются при добавлении порции.
                settings.presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,  // иконка, текст и кнопки — по одной оси
                    ) {
                        Icon(
                            // painterResource нужен потому, что иконки лежат в ресурсах приложения,
                            // а не в стандартном наборе Icons.Default. iconRes() — расширение,
                            // которое по типу напитка отдаёт id нужной иконки.
                            painter = androidx.compose.ui.res.painterResource(preset.kind.iconRes()),
                            contentDescription = null,  // текст рядом уже описывает напиток, дублировать озвучку незачем
                            tint = MaterialTheme.colorScheme.primary,  // красим иконку в цвет бренда
                            modifier = Modifier.padding(end = 10.dp),  // отступ до текста
                        )
                        // weight(1f) — колонка забирает всё свободное место, поэтому кнопки
                        // прижимаются к правому краю, а длинное название переносится, а не выдавливает их.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                // Своё имя важнее типа: если пользователь его ввёл — показываем его,
                                // иначе берём название типа напитка из ресурсов.
                                text = preset.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(preset.kind.titleRes()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                // Подпись «объём / крепость». formatVolume приводит числа к виду без
                                // лишних нулей, поэтому 100.0 показывается как «100».
                                text = stringResource(
                                    R.string.drink_caption,
                                    Formatters.formatVolume(preset.volumeMl),
                                    Formatters.formatVolume(preset.abvPercent),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { editingPreset = preset }) {
                            Text(stringResource(R.string.settings_edit))
                        }
                        // Удаление пресета: filterNot возвращает НОВЫЙ список без этого элемента,
                        // старый не меняется — так Compose и замечает изменение.
                        TextButton(onClick = {
                            onSave(settings.copy(presets = settings.presets.filterNot { it.id == preset.id }))
                        }) {
                            Text("×", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                TextButton(onClick = { newPreset = true }) {
                    Text(stringResource(R.string.settings_add_preset))
                }
            }

            // --- Модель ---
            Section(title = stringResource(R.string.settings_model)) {
                Text(stringResource(R.string.settings_food), style = MaterialTheme.typography.bodyMedium)
                // Сытость влияет на скорость всасывания алкоголя — это один из входов модели.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FoodIntake.entries.forEach { option ->
                        FilterChip(
                            colors = brandChipColors(),
                            selected = settings.food == option,
                            onClick = { onSave(settings.copy(food = option)) },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            FoodIntake.EMPTY_STOMACH -> R.string.settings_food_empty
                                            FoodIntake.NORMAL -> R.string.settings_food_normal
                                            FoodIntake.HEAVY -> R.string.settings_food_heavy
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_metabolism),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),  // отделяем вторую группу чипов от первой
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Metabolism.entries.forEach { option ->
                        FilterChip(
                            colors = brandChipColors(),
                            selected = settings.metabolism == option,
                            onClick = { onSave(settings.copy(metabolism = option)) },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            Metabolism.SLOW -> R.string.settings_metabolism_slow
                                            Metabolism.AVERAGE -> R.string.settings_metabolism_average
                                            Metabolism.FAST -> R.string.settings_metabolism_fast
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                // Продвинутые коэффициенты спрятаны за переключателем: обычному пользователю
                // они не нужны, а неверные значения дадут неправдоподобную кривую.
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(R.string.settings_advanced))
                }
                if (showAdvanced) {
                    Text(
                        text = stringResource(R.string.settings_advanced_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,  // красный — предупреждение, а не украшение
                    )
                    // Каждое поле показывает либо ручной override, либо значение из модели.
                    // Оператор ?: (элвис) читается как «override, а если его нет — расчётное значение»,
                    // поэтому пустые настройки сразу показывают то, что реально используется в формулах.
                    NumberField(
                        label = stringResource(R.string.settings_ka),
                        value = settings.overrides.kaPerHour ?: settings.modelParams.kaPerHour,
                        range = 0.1..6.0,  // границы подобраны так, чтобы даже крайности давали осмысленный результат
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(kaPerHour = it))) },
                    )
                    NumberField(
                        label = stringResource(R.string.settings_vmax),
                        value = settings.overrides.vmaxGPerLH ?: settings.modelParams.vmaxGPerLH,
                        range = 0.05..0.4,
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(vmaxGPerLH = it))) },
                    )
                    NumberField(
                        label = stringResource(R.string.settings_km),
                        value = settings.overrides.kmGPerL ?: settings.modelParams.kmGPerL,
                        range = 0.001..0.5,  // очень маленькая нижняя граница: параметр чувствительный
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(kmGPerL = it))) },
                    )
                    NumberField(
                        label = stringResource(R.string.settings_r),
                        value = settings.overrides.r ?: settings.modelParams.r,
                        range = 0.3..1.0,  // доля воды в теле — безразмерная величина от 0 до 1
                        onValueChange = { onSave(settings.copy(overrides = settings.overrides.copy(r = it))) },
                    )
                    // Сброс возвращает пустой ModelOverrides: все поля снова начнут брать
                    // значения из модели, а не из ручного ввода.
                    TextButton(onClick = {
                        onSave(settings.copy(overrides = ru.alkometr.app.core.model.ModelOverrides()))
                    }) {
                        Text(stringResource(R.string.settings_reset_params))
                    }
                }
            }

            // --- Порог и единицы ---
            Section(title = stringResource(R.string.settings_threshold)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Готовые пороги разных стран: пользователю проще выбрать «Россия»,
                    // чем помнить, что это 0.3 промилле.
                    ThresholdPreset.entries.forEach { preset ->
                        FilterChip(
                            colors = brandChipColors(),
                            // Сравниваем Double с допуском: точное == для чисел с плавающей точкой
                            // ненадёжно — 0.3 после пересчётов может стать 0.30000000000000004.
                            selected = abs(settings.thresholdPermille - preset.permille) < 0.001,
                            onClick = { onSave(settings.copy(thresholdPermille = preset.permille)) },
                            label = {
                                Text(
                                    stringResource(
                                        when (preset) {
                                            ThresholdPreset.RUSSIA -> R.string.settings_threshold_russia
                                            ThresholdPreset.EUROPE -> R.string.settings_threshold_europe
                                            ThresholdPreset.USA -> R.string.settings_threshold_usa
                                            ThresholdPreset.ZERO -> R.string.settings_threshold_zero
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                // Своё значение порога: чуть ниже выбранного пресета, если нужна персональная граница.
                NumberField(
                    label = stringResource(R.string.settings_threshold_value),
                    value = settings.thresholdPermille,
                    range = 0.0..0.8,
                    onValueChange = { onSave(settings.copy(thresholdPermille = it)) },
                )
                Text(
                    text = stringResource(R.string.settings_threshold_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_units),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column {
                    // Единицы разбиты на две строки по два варианта: так подписи короче
                    // и не расползаются на узких экранах. take(2) — первые два, drop(2) — остальные.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Units.entries.take(2).forEach { unit ->
                            FilterChip(
                            colors = brandChipColors(),
                                selected = settings.units == unit,
                                onClick = { onSave(settings.copy(units = unit)) },
                                // unitLabel() отдаёт id строки, stringResource превращает его в текст.
                                label = { Text(stringResource(unitLabel(unit))) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Units.entries.drop(2).forEach { unit ->
                            FilterChip(
                            colors = brandChipColors(),
                                selected = settings.units == unit,
                                onClick = { onSave(settings.copy(units = unit)) },
                                label = { Text(stringResource(unitLabel(unit))) },
                            )
                        }
                    }
                }
            }

            // --- Данные ---
            Section(title = stringResource(R.string.settings_data)) {
                // launch("alkometr-backup.json") открывает системный диалог сохранения с подсказанным
                // именем файла. Результат придёт в колбэк createBackup, объявленный выше.
                TextButton(onClick = { createBackup.launch("alkometr-backup.json") }) {
                    Text(stringResource(R.string.settings_export))
                }
                // Для импорта передаём список MIME-типов: какие файлы разрешено выбирать.
                // Это защита от случайного выбора картинки или APK.
                TextButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text(stringResource(R.string.settings_import))
                }
                // Необратимые действия сначала спрашивают подтверждение через AlertDialog:
                // флаг true — и диалог появится в конце композабла.
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.settings_clear_history))
                }
                TextButton(onClick = { confirmReset = true }) {
                    Text(stringResource(R.string.settings_reset_all))
                }
                Text(
                    text = stringResource(R.string.history_range_hint),  // поясняем, что именно удалится
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- О приложении ---
            Section(title = stringResource(R.string.settings_about)) {
                Text(
                    // settings_version — шаблон строки с одним подстановочным %1$s:
                    // имя версии подставляется уже во время выполнения.
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                // Ссылка на репозиторий необязательна: если строку в ресурсах очистить,
                // кнопка просто не появится — код останется рабочим.
                val repoUrl = stringResource(R.string.settings_repository_url)
                if (repoUrl.isNotBlank()) {
                    // LocalUriHandler — системный «открыватель ссылок»: отдаём ему URL,
                    // дальше браузер или приложение, назначенное по умолчанию, решают сами.
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    // runCatching на случай, если на устройстве нет ни одного обработчика http(s):
                    // тогда будет исключение, но приложение не упадёт.
                    TextButton(onClick = { runCatching { uriHandler.openUri(repoUrl) } }) {
                        Text(stringResource(R.string.settings_repository))
                    }
                }
                TextButton(onClick = { showDisclaimer = true }) {
                    Text(stringResource(R.string.settings_disclaimer))
                }
            }
        }
    }

    // Диалог редактирования существующего пресета. Показывается поверх экрана, если editingPreset != null.
    if (editingPreset != null) {
        // !! здесь безопасен: проверка выше гарантирует, что значение не null.
        // Локальная переменная нужна, потому что внутри лямбд умный каст не работает.
        val preset = editingPreset!!
        DrinkDialog(
            title = stringResource(R.string.settings_edit_preset),
            initialVolumeMl = preset.volumeMl,  // начальные значения — текущие, чтобы пользователь правил, а не вводил заново
            initialAbvPercent = preset.abvPercent,
            initialKind = preset.kind,
            initialAtMillis = System.currentTimeMillis(),  // время не редактируется, но параметр обязателен
            allowTime = false,  // пресет — это не выпитый напиток, время ему не нужно
            allowName = true,  // а своё название — нужно
            initialName = preset.name,
            confirmText = stringResource(R.string.save),
            onDismiss = { editingPreset = null },  // закрытие без изменений
            onConfirm = { draft ->
                editingPreset = null  // сначала закрываем диалог, чтобы он не мигал поверх сохранения
                onSave(
                    settings.copy(
                        // map проходит по всем пресетам и заменяет только совпавший по id,
                        // остальные возвращает как есть — список остаётся неизменяемым.
                        presets = settings.presets.map {
                            if (it.id == preset.id) {
                                it.copy(
                                    name = draft.name,
                                    volumeMl = draft.volumeMl,
                                    abvPercent = draft.abvPercent,
                                    kind = draft.kind,
                                )
                            } else {
                                it
                            }
                        },
                    ),
                )
            },
        )
    }

    // Диалог создания нового пресета: тот же DrinkDialog, но с разумными значениями по умолчанию.
    if (newPreset) {
        DrinkDialog(
            title = stringResource(R.string.settings_add_preset),
            initialVolumeMl = 100.0,  // 100 мл и 10 % — типичный средний напиток, дальше пользователь поправит
            initialAbvPercent = 10.0,
            initialKind = DrinkKind.CUSTOM,
            initialAtMillis = System.currentTimeMillis(),
            allowTime = false,
            allowName = true,
            confirmText = stringResource(R.string.custom_add),
            onDismiss = { newPreset = false },
            onConfirm = { draft ->
                newPreset = false
                onSave(
                    settings.copy(
                        // Новый пресет добавляем в конец списка. id — случайный UUID:
                        // он должен быть уникальным и не зависеть от порядка добавления.
                        presets = settings.presets + DrinkPreset(
                            id = UUID.randomUUID().toString(),
                            kind = draft.kind,
                            volumeMl = draft.volumeMl,
                            abvPercent = draft.abvPercent,
                            name = draft.name,
                        ),
                    ),
                )
            },
        )
    }

    // Подтверждение очистки истории. AlertDialog — модальное окно Material 3:
    // он сам затемняет фон и перехватывает нажатия.
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },  // тап мимо окна или кнопка «назад» = отказ
            title = { Text(stringResource(R.string.settings_clear_history)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },  // объясняем последствия
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false  // закрываем диалог до действия, чтобы он не остался висеть
                    onClearHistory()
                    // Подтверждаем результат snackbar-сообщением: удаление не видно глазами,
                    // пользователь должен получить обратную связь.
                    scope.launch { snackbar.showSnackbar(savedMessage) }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Подтверждение полного сброса: самое разрушительное действие на экране,
    // поэтому спрашиваем отдельно и после сброса уводим с экрана — настройки уже другие.
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_all)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetAll()
                    onBack()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Дисклеймер: юридическая страховка, что приложение — не алкотестер и не медицинский прибор.
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_text)) },
            confirmButton = {
                // Здесь только одна кнопка: читать нечего подтверждать, достаточно закрыть.
                TextButton(onClick = { showDisclaimer = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    // Ошибка импорта. let выполняется только когда сообщение не null — так диалог
    // появляется сам, без отдельного флага «показывать ошибку».
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },  // сбрасываем в null — и диалог исчезает
            title = { Text(stringResource(R.string.settings_import)) },
            text = { Text(stringResource(R.string.settings_import_error, message)) },  // шаблон + текст исключения
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

/**
 * Карточка-секция: скруглённый полупрозрачный фон, заголовок и содержимое.
 * content — слот-лямбда: вызывающий код сам решает, что положить внутрь,
 * а Section отвечает только за внешний вид.
 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // clip обрезает фон по скруглению — без него background нарисовал бы острые углы.
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))  // полупрозрачный фон вместо отдельной карточки
            .padding(14.dp),  // внутренние отступы: контент не прилипает к краям
        verticalArrangement = Arrangement.spacedBy(6.dp),  // строки внутри секции дышат
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,  // заголовок крупнее обычного текста
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Вызов слота: сюда подставляется содержимое секции.
        content()
    }
}

/**
 * Отображение единиц измерения в id строкового ресурса. Возвращает Int,
 * поэтому вызывающий код оборачивает результат в stringResource().
 */
@Composable
private fun unitLabel(unit: Units): Int = when (unit) {
    Units.PERMILLE -> R.string.settings_units_permille
    Units.PERCENT -> R.string.settings_units_percent
    Units.MG_PER_100ML -> R.string.settings_units_mg_100
    Units.MG_PER_LITER -> R.string.settings_units_mg_l
}

/**
 * Числовое поле: показывает значение из настроек, но позволяет набирать его посимвольно.
 * Некорректный ввод не сохраняется и не сбрасывает курсор.
 *
 * Такая двойственность нужна из-за формата: пользователь должен иметь возможность
 * написать «0,» или «1», не получая мгновенно «0.0» и не теряя позицию курсора.
 */
@Composable
private fun NumberField(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
) {
    // Два состояния: каноническое значение из настроек (value) и текст, который человек набирает.
    // Текст хранится отдельно, иначе промежуточный ввод вроде «1,» нельзя было бы показать.
    var text by remember { mutableStateOf(Formatters.formatVolume(value)) }
    // LaunchedEffect(value) срабатывает, когда значение изменилось СНАРУЖИ: подвинули слайдер,
    // импортировали бэкап, нажали сброс. Тогда переформатируем текст.
    // Если число в поле уже совпадает с value, текст не трогаем — иначе курсор прыгал бы в конец.
    LaunchedEffect(value) {
        // Запятую превращаем в точку: toDoubleOrNull понимает только точку.
        val parsed = text.replace(',', '.').toDoubleOrNull()
        // abs(...) > 1e-9 — сравнение с допуском: точное == для Double ненадёжно.
        // parsed == null значит «в поле не число» — тогда текст тоже надо восстановить.
        if (parsed == null || abs(parsed - value) > 1e-9) text = Formatters.formatVolume(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            // Валидация на входе: оставляем только цифры, запятую и точку.
            // Буквы и минус отбрасываются сразу, поэтому «плохое» значение физически нельзя ввести.
            text = input.filter { it.isDigit() || it == ',' || it == '.' }
            // Сохраняем только число внутри range. takeIf пропускает значение дальше,
            // let вызывает onValueChange; всё, что не прошло проверку, просто не доходит до настроек.
            text.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it in range }
                ?.let(onValueChange)
        },
        label = { Text(label) },
        // Тип клавиатуры: на телефоне сразу откроется цифровая раскладка с разделителем.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,  // поле в одну строку: перенос ломал бы ввод
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Необязательное целочисленное поле: пустое значение — null, вне диапазона не сохраняется.
 */
@Composable
private fun OptionalIntField(
    label: String,
    value: Int?,
    range: IntRange,
    onValueChange: (Int?) -> Unit,
) {
    // remember(value) с ключом: когда значение пришло снаружи, текст пересоздаётся.
    // Для короткого целого поля этого достаточно, LaunchedEffect не нужен.
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            // Только цифры и не больше трёх символов: рост и возраст трёхзначными и ограничены.
            text = input.filter { it.isDigit() }.take(3)
            if (text.isEmpty()) {
                // Пустая строка — это осознанный выбор «не указывать», а не ошибка ввода.
                onValueChange(null)
            } else {
                // Число вне range просто не сохраняем: текст остаётся на экране,
                // пользователь видит, что ввёл, и может исправить.
                text.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Цвета FilterChip под бренд: вместо стандартной заливки Material 3 используем
 * фон поверхности и полупрозрачный primary для выбранного состояния.
 */
@Composable
private fun brandChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,  // обычный чип — как фон карточки
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),  // выбранный — светлая заливка бренда
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Цвета Slider под бренд: активная часть дорожки и ползунок — основным цветом,
 * неактивная — тем же цветом, но приглушённым.
 */
@Composable
private fun brandSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
)
