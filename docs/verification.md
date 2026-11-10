# Проверка сборки Alkometr (после перезагрузки)

Дата проверки: 10.11.2026. Все команды выполнялись из папки `alkometr`.
Исходники не изменялись. Версии Gradle/AGP не менялись.

## Итог

| Показатель | Значение |
|---|---|
| Debug APK собран | да (`BUILD SUCCESSFUL`) |
| Юнит-тестов выполнено | 23 |
| Из них упало | 0 |
| Ошибок lint (Error) | 0 |
| Предупреждений / подсказок lint | 21 / 4 |
| Размер `dist\alkometr-0.1.0-debug.apk` | 13 154 743 байт = 12,5 МБ |
| Разрешение INTERNET в APK | нет |

## Шаги

### 1. `local.properties`

Файла нет (`Test-Path local.properties` → `False`). Удалять не потребовалось: SDK и JDK задаются в `tools\build.ps1` (JDK —
`E:\ProectsAutoClaw\_tools\jdk-17.0.20.1+1`, SDK — `C:\Users\user\AppData\Local\Android\Sdk`).

### 2. Юнит-тесты

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Tasks ':app:testDebugUnitTest'
```

Первый запуск: `BUILD SUCCESSFUL in 16s`, но задача помечена `UP-TO-DATE` — тесты не
перезапускались. После очистки каталога результатов Gradle отдал `FROM-CACHE`.
Чтобы получить действительно выполненные тесты, та же задача запущена напрямую с
отключённым кэшем:

```
gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-build-cache --console=plain
```

Результат: `BUILD SUCCESSFUL in 25s`, `25 actionable tasks: 25 executed`.

Подсчёт по `app\build\test-results\testDebugUnitTest\*.xml` (атрибуты `tests`/`failures`):

| testsuite | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ru.alkometr.app.data.BackupCodecTest` | 4 | 0 | 0 | 0 |
| `ru.alkometr.app.engine.BacEngineTest` | 19 | 0 | 0 | 0 |
| **Итого** | **23** | **0** | **0** | **0** |

### 3. Сборка APK

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Tasks ':app:assembleDebug'
```

`BUILD SUCCESSFUL in 5s`, `36 actionable tasks: 3 executed, 33 up-to-date`.
Артефакт: `app\build\outputs\apk\debug\app-debug.apk` (13 154 743 байта).

### 4. Lint

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Tasks ':app:lintDebug'
```

`BUILD SUCCESSFUL in 31s`, `27 actionable tasks: 8 executed, 19 up-to-date`.
Падения из-за кэша lint и упоминаний `local.properties` не было — повтор не потребовался.

Из `app\build\reports\lint-results-debug.xml` / `.txt`: **0 errors, 21 warnings, 4 hints**.
Разбивка по правилам: `UnusedResources` 15, `AutoboxingStateCreation` 4,
`NewerVersionAvailable` 3, `OldTargetApi` 1, `AndroidGradlePluginVersion` 1,
`ObsoleteSdkInt` 1.

### 5. Проверка APK без устройства

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\check_apk.ps1
```

`All APK checks passed.` — 18 из 18 проверок `[OK]`: пакет `ru.alkometr.app`,
`versionName 0.1.0`, `minSdk 26`, `targetSdk 36`, отсутствие INTERNET и
camera/audio/location/contacts, наличие label, все 9 ресурсов иконок,
валидная подпись debug-ключом.

### 6. Копия в `dist`

```
Copy-Item app\build\outputs\apk\debug\app-debug.apk dist\alkometr-0.1.0-debug.apk -Force
```

- Путь: `dist\alkometr-0.1.0-debug.apk`
- Размер: 13 154 743 байта = 12,55 МиБ ≈ **12,5 МБ**
- SHA-256: `0E27303EA1544AD8A30DCA737C230EF223A443D43CDCE677478882F010A00AB6`

`aapt2 dump badging` по этой копии: `package: name='ru.alkometr.app' versionCode='1'
versionName='0.1.0'`, `minSdkVersion:'26'`, `targetSdkVersion:'36'`,
`application-label:'АлкоМетр'`. Единственное `uses-permission` —
`ru.alkometr.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (собственное,
signature-level). `android.permission.INTERNET` отсутствует.

## Замечания

- Задача `:app:testDebugUnitTest` при обычном вызове не исполняет тесты повторно
  (`UP-TO-DATE`, затем `FROM-CACHE`). Цифры выше получены прогоном с
  `--rerun-tasks --no-build-cache`; XML-отчёты перезаписаны этим прогоном.
- Предыдущая копия в `dist` (13 285 909 байт) перезаписана новой сборкой.
- `compileSdkVersion` в badging — 37, `targetSdk` — 36; расхождением не является,
  так задано в проекте.
- Предупреждения `NewerVersionAvailable` / `AndroidGradlePluginVersion` — просто
  информация о доступных обновлениях; версии намеренно не менялись.
- В рабочем дереве на момент проверки уже были незакоммиченные правки
  (`BacTimeline.kt`, `SettingsScreen.kt`, `tools/check_apk.ps1`; время изменения
  12:18–12:19, до начала этой проверки). Они не вносились в ходе проверки и
  сборку не ломают — все шаги выше прошли на текущем состоянии дерева.
