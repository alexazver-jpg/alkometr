# Проверяет собранный APK без устройства: манифест, разрешения, ресурсы, подпись.
# Использование: powershell -ExecutionPolicy Bypass -File tools\check_apk.ps1 [-Apk <путь>]
param(
    [string]$Apk = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = 'E:\ProectsAutoClaw\_tools\android-sdk'
if (-not $Apk) { $Apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk' }
if (-not (Test-Path $Apk)) { throw "APK не найден: $Apk" }

$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

$script:failed = 0
function Check($name, $condition, $detail = '') {
    if ($condition) {
        Write-Host ("[OK]   " + $name + $(if ($detail) { " — $detail" } else { '' }))
    } else {
        Write-Host ("[FAIL] " + $name + $(if ($detail) { " — $detail" } else { '' })) -ForegroundColor Red
        $script:failed++
    }
}

Write-Host "APK: $Apk"
Write-Host ("Размер: {0:N1} МБ" -f ((Get-Item $Apk).Length / 1MB))

$badging = & $aapt2 dump badging $Apk 2>&1 | Out-String

Check 'Пакет ru.alkometr.app' ($badging -match "package: name='ru\.alkometr\.app'")
Check 'versionName 0.1.0' ($badging -match "versionName='0\.1\.0'")
Check 'minSdk 26' ($badging -match "minSdkVersion:'26'")
Check 'targetSdk 36' ($badging -match "targetSdkVersion:'36'")
Check 'Нет разрешения INTERNET' (-not ($badging -match 'android\.permission\.INTERNET'))
Check 'Нет лишних разрешений' (-not ($badging -match 'uses-permission: name=.android\.permission\.(CAMERA|RECORD_AUDIO|ACCESS_FINE_LOCATION|READ_CONTACTS)'))
Check 'Метка приложения' ($badging -match 'application-label:')

$resources = & $aapt2 dump resources $Apk 2>&1 | Out-String
foreach ($icon in @('ic_level_1', 'ic_level_2', 'ic_level_3', 'ic_level_4', 'ic_level_5', 'ic_shot', 'ic_wine', 'ic_mug', 'ic_launcher')) {
    Check "Ресурс $icon" ($resources -match [regex]::Escape($icon))
}

$verified = & $apksigner verify --print-certs $Apk 2>&1 | Out-String
Check 'Подпись APK валидна' ($LASTEXITCODE -eq 0)
Check 'Подпись debug-ключом' ($verified -match 'CN=Android Debug')

Write-Host ''
if ($script:failed -eq 0) {
    Write-Host 'Все проверки APK пройдены.' -ForegroundColor Green
    exit 0
} else {
    Write-Host ("Провалено проверок: " + $script:failed) -ForegroundColor Red
    exit 1
}
