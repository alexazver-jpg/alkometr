# Checks the built APK without a device: manifest, permissions, resources, signature.
# Usage: powershell -ExecutionPolicy Bypass -File tools\check_apk.ps1 [-Apk <path>]
param(
    [string]$Apk = '',
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = 'C:\Users\user\AppData\Local\Android\Sdk'
$jdk = 'E:\ProectsAutoClaw\_tools\jdk-17.0.20.1+1'
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
if (-not $Apk) { $Apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk' }
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

$script:failed = 0
function Check($name, $condition, $detail = '') {
    $suffix = if ($detail) { " - $detail" } else { '' }
    if ($condition) {
        Write-Host ("[OK]   " + $name + $suffix)
    } else {
        Write-Host ("[FAIL] " + $name + $suffix) -ForegroundColor Red
        $script:failed++
    }
}

Write-Host "APK: $Apk"
Write-Host ("Size: {0:N1} MB" -f ((Get-Item $Apk).Length / 1MB))

$badging = & $aapt2 dump badging $Apk 2>&1 | Out-String

Check 'Package ru.alkometr.app' ($badging -match "package: name='ru\.alkometr\.app'")
Check 'versionName 0.1.0' ($badging -match "versionName='0\.1\.0'")
Check 'minSdk 26' ($badging -match "minSdkVersion:'26'")
Check 'targetSdk 36' ($badging -match "targetSdkVersion:'36'")
Check 'No INTERNET permission' (-not ($badging -match 'android\.permission\.INTERNET'))
Check 'No camera/audio/location/contacts' (-not ($badging -match 'uses-permission: name=.android\.permission\.(CAMERA|RECORD_AUDIO|ACCESS_FINE_LOCATION|READ_CONTACTS)'))
Check 'Application label present' ($badging -match 'application-label:')

$resources = & $aapt2 dump resources $Apk 2>&1 | Out-String
foreach ($icon in @('ic_level_1', 'ic_level_2', 'ic_level_3', 'ic_level_4', 'ic_level_5', 'ic_shot', 'ic_wine', 'ic_mug', 'ic_launcher')) {
    Check "Resource $icon" ($resources -match [regex]::Escape($icon))
}

$verified = & $apksigner verify --print-certs $Apk 2>&1 | Out-String
Check 'APK signature valid' ($LASTEXITCODE -eq 0)
if ($Release) {
    Check 'Signed with release key' ($verified -match 'CN=Alkometr')
} else {
    Check 'Signed with debug key' ($verified -match 'CN=Android Debug')
}

Write-Host ''
if ($script:failed -eq 0) {
    Write-Host 'All APK checks passed.' -ForegroundColor Green
    exit 0
} else {
    Write-Host ("Failed checks: " + $script:failed) -ForegroundColor Red
    exit 1
}
