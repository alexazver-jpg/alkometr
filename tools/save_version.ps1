# -*- coding: utf-8 -*-
<#
  Зафиксировать версию проекта «АлкоМетр» в локальном git-архиве.

  Что делает:
    1) копирует рабочую папку проекта в «Программа»;
    2) обновляет ReadMe.md и ЖУРНАЛ РАЗРАБОТКИ.md в корне архива и в «Документы»;
    3) делает коммит в этом репозитории.

  Примеры:
    Сохранить версию.cmd
    Сохранить версию.cmd "feat: шкала времени"
    powershell -File save_version.ps1 -Message "fix: иконки" -Source "D:\work\alkometr"
#>
param(
    [string]$Message = '',
    [string]$Source = ''
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$archive = $PSScriptRoot
$sourceFile = Join-Path $archive '.source'
if (-not $Source) {
    if (Test-Path $sourceFile) { $Source = (Get-Content $sourceFile -Raw).Trim() }
    else { throw 'Не указан путь к рабочей папке проекта: задайте -Source или создайте файл .source' }
}
if (-not (Test-Path $Source)) { throw ('Нет папки проекта: ' + $Source) }

$program = Join-Path $archive 'Программа'
if (-not (Test-Path $program)) { New-Item -ItemType Directory -Path $program -Force | Out-Null }

Write-Host 'Копирую проект в архив…'
$rc = robocopy $Source $program /MIR /XD .git .gradle build .idea .cxx /XF *.log /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw ('robocopy: код ' + $LASTEXITCODE) }

$docs = Join-Path $archive 'Документы'
if (-not (Test-Path $docs)) { New-Item -ItemType Directory -Path $docs -Force | Out-Null }

foreach ($name in @('ReadMe.md', 'ЖУРНАЛ РАЗРАБОТКИ.md', 'ПРОМПТ для ИИ — АлкоМетр.md')) {
    $p = Join-Path $Source $name
    if (-not (Test-Path $p)) { $p = Join-Path $Source ('docs\' + $name) }
    if (Test-Path $p) { Copy-Item $p (Join-Path $docs $name) -Force }
}
foreach ($name in @('ReadMe.md', 'ЖУРНАЛ РАЗРАБОТКИ.md')) {
    $p = Join-Path $Source $name
    if (-not (Test-Path $p)) { $p = Join-Path $Source ('docs\' + $name) }
    if (Test-Path $p) { Copy-Item $p (Join-Path $archive $name) -Force }
}

Push-Location $archive
try {
    git add -A | Out-Null
    $status = git status --porcelain
    if (-not $status) { Write-Host 'Изменений нет — коммит не нужен.'; return }

    if (-not $Message) { $Message = 'обновление от ' + (Get-Date -Format 'yyyy-MM-dd HH:mm') }
    $msgFile = Join-Path $env:TEMP ('commitmsg_' + [guid]::NewGuid().ToString('N') + '.txt')
    [IO.File]::WriteAllText($msgFile, $Message, (New-Object Text.UTF8Encoding($false)))
    git -c user.name='AutoClaw' -c user.email='autoclaw@local' commit -F $msgFile | Out-Null
    Remove-Item $msgFile -Force -ErrorAction SilentlyContinue

    $hash = (git rev-parse --short HEAD).Trim()
    $count = (git rev-list --count HEAD).Trim()
    Write-Host ('Версия зафиксирована: ' + $hash + ' (коммитов: ' + $count + ')')
} finally {
    Pop-Location
}
