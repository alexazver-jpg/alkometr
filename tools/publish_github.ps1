# Publishes the project to a PRIVATE GitHub repository and verifies privacy.
# Usage (from the alkometr folder):
#   powershell -ExecutionPolicy Bypass -File tools\publish_github.ps1
#   $env:GITHUB_TOKEN = "..." ; powershell ... -File tools\publish_github.ps1 -RepoName alkometr
# The token is read only from the environment variable GITHUB_TOKEN and is never printed or stored.

param(
    [string]$RepoName = 'alkometr',
    [string]$Description = 'Alkometr - calculated blood alcohol tracker (Android, Kotlin)'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    Write-Host "Project: $root"

    $hasGh = $null -ne (Get-Command gh -ErrorAction SilentlyContinue)
    if ($hasGh) {
        Write-Host 'GitHub CLI found, checking authorization...'
        gh auth status 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $exists = gh repo view $RepoName --json name 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Repository $RepoName already exists, pushing..."
                git remote remove origin 2>$null | Out-Null
                gh repo set-default $RepoName 2>$null | Out-Null
                git push -u origin HEAD
            } else {
                gh repo create $RepoName --private --source=. --remote=origin --push
            }
            gh repo view $RepoName --json name,url,isPrivate
            exit $LASTEXITCODE
        }
        Write-Host 'gh is installed but not authorized. Run: gh auth login'
    }

    $token = $env:GITHUB_TOKEN
    if (-not $token) {
        throw 'No GitHub access: install gh and run "gh auth login", or set GITHUB_TOKEN and retry.'
    }

    $headers = @{
        Authorization          = "Bearer $token"
        Accept                 = 'application/vnd.github+json'
        'User-Agent'           = 'alkometr-publish'
        'X-GitHub-Api-Version' = '2022-11-28'
    }

    Write-Host 'Resolving account...'
    $user = Invoke-RestMethod -Uri 'https://api.github.com/user' -Headers $headers
    $owner = $user.login

    Write-Host "Creating private repository $owner/$RepoName ..."
    $body = @{ name = $RepoName; private = $true; description = $Description } | ConvertTo-Json
    try {
        Invoke-RestMethod -Method Post -Uri 'https://api.github.com/user/repos' -Headers $headers -Body $body -ContentType 'application/json' | Out-Null
        Write-Host 'Repository created.'
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -eq 422) {
            Write-Host 'Repository already exists, continuing.'
        } else {
            throw
        }
    }

    $url = "https://github.com/$owner/$RepoName.git"
    git remote remove origin 2>$null | Out-Null
    git remote add origin $url
    Write-Host "Remote origin set to $url"
    Write-Host 'Push with your usual git credentials:'
    Write-Host '  git push -u origin HEAD'

    Write-Host ''
    Write-Host 'Privacy check (anonymous request must return 404):'
    try {
        $anonymous = Invoke-WebRequest -Uri "https://api.github.com/repos/$owner/$RepoName" -UseBasicParsing -TimeoutSec 30
        Write-Host "WARNING: repository is PUBLIC (HTTP $($anonymous.StatusCode)). Make it private immediately." -ForegroundColor Red
        exit 2
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 404) {
            Write-Host 'OK: anonymous request returned 404, repository is private.' -ForegroundColor Green
        } else {
            Write-Host "Could not verify privacy (HTTP $code)." -ForegroundColor Yellow
        }
    }
} finally {
    Pop-Location
}
