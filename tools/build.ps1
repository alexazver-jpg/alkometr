# Собирает проект: находит JDK 17 и Android SDK, пишет полный лог в docs\build-log.txt
# Использование: powershell -ExecutionPolicy Bypass -File tools\build.ps1 [задачи Gradle]
param(
    [string[]]$Tasks = @(':app:assembleDebug')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdk = 'E:\ProectsAutoClaw\_tools\jdk-17.0.20.1+1'
$sdk = 'E:\ProectsAutoClaw\_tools\android-sdk'

if (-not (Test-Path "$jdk\bin\java.exe")) { throw "JDK не найден: $jdk" }
if (-not (Test-Path "$sdk\platforms")) { throw "Android SDK не найден: $sdk" }

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$jdk\bin;$env:PATH"

$log = Join-Path $root 'docs\build-log.txt'
$taskLine = ($Tasks -join ' ')
Push-Location $root
try {
    $cmd = "gradlew.bat $taskLine --console=plain --stacktrace > `"$log`" 2>&1"
    cmd /c $cmd
    $code = $LASTEXITCODE
    Write-Output "--- build-log.txt (последние 40 строк) ---"
    Get-Content $log -Tail 40
    Write-Output "EXITCODE=$code"
    exit $code
}
finally {
    Pop-Location
}
