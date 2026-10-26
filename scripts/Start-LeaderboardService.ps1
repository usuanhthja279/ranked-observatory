param([int]$Port = 8090,[switch]$PauseLiveUntilArchiveComplete,[switch]$PauseAllExtractionUntilRedesignComplete,[switch]$ResumeLiveExtraction,[string]$ResumeCapture)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$settingsPath = Join-Path $root 'config/leaderboard.local.json'
if (!(Test-Path -LiteralPath $settingsPath)) { throw 'Create config/leaderboard.local.json from config/leaderboard.local.example.json and provide local secrets.' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is already in use; no process was changed." }
if (($PauseLiveUntilArchiveComplete -and $ResumeLiveExtraction) -or ($PauseAllExtractionUntilRedesignComplete -and ($PauseLiveUntilArchiveComplete -or $ResumeLiveExtraction))) { throw 'Choose only one extraction option.' }
$pauseMarker = Join-Path $root 'build/live-extraction-paused.flag'
$allPauseMarker = Join-Path $root 'build/all-extraction-paused.flag'
if ($PauseLiveUntilArchiveComplete) { New-Item -ItemType Directory -Path (Split-Path $pauseMarker) -Force | Out-Null; Set-Content -LiteralPath $pauseMarker -Value 'Resume live extraction after historical acts complete.' }
if ($PauseAllExtractionUntilRedesignComplete) { New-Item -ItemType Directory -Path (Split-Path $allPauseMarker) -Force | Out-Null; Set-Content -LiteralPath $allPauseMarker -Value 'Keep all data extraction paused until all redesign phases are complete.' }
if ($ResumeLiveExtraction) { Remove-Item -LiteralPath $pauseMarker,$allPauseMarker -Force -ErrorAction SilentlyContinue }
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
foreach ($key in @('url','username','passwordFile','adminUser','adminPasswordFile')) { if (!$settings.$key) { throw "Missing '$key' in $settingsPath." } }
$dbPassPath = Join-Path $root $settings.passwordFile
$adminPassPath = Join-Path $root $settings.adminPasswordFile
if (!(Test-Path -LiteralPath $dbPassPath) -or !(Test-Path -LiteralPath $adminPassPath)) { throw 'Create the local database and admin password files referenced in leaderboard.local.json.' }
$env:LEADERBOARD_PORT = "$Port"
$env:LEADERBOARD_DB_URL = $settings.url
$env:LEADERBOARD_DB_USERNAME = $settings.username
$env:LEADERBOARD_DB_PASSWORD = (Get-Content -LiteralPath $dbPassPath -Raw).Trim()
$env:LEADERBOARD_ADMIN_USER = $settings.adminUser
$env:LEADERBOARD_ADMIN_PASSWORD = (Get-Content -LiteralPath $adminPassPath -Raw).Trim()
$env:LEADERBOARD_ENABLED = if ((Test-Path -LiteralPath $pauseMarker) -or (Test-Path -LiteralPath $allPauseMarker)) { 'false' } else { 'true' }
$env:LEADERBOARD_PAUSE_ALL_EXTRACTION = if (Test-Path -LiteralPath $allPauseMarker) { 'true' } else { 'false' }
$env:LEADERBOARD_LIVE_PAUSE_MARKER = $pauseMarker
$env:LEADERBOARD_LIVE_CACHE_DIRECTORY = Join-Path $root 'build/live-collection'
if ($ResumeCapture) {
    $captureRoot = (Resolve-Path (Join-Path $root 'build/live-collection')).Path
    $capturePath = (Resolve-Path (Join-Path $root $ResumeCapture)).Path
    if (!$capturePath.StartsWith($captureRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Resume capture must be inside build/live-collection.' }
    $env:LEADERBOARD_RESUME_CAPTURE = $capturePath
}
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to a Java 21 JDK.' }
$javaPath = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (!(Test-Path -LiteralPath $javaPath)) { throw 'JAVA_HOME must point to a Java 21 JDK.' }
$jarPath = Join-Path $root 'build/libs/valorant-leaderboard-service-1.0.0.jar'
if (!(Test-Path -LiteralPath $jarPath)) { $jarPath = Join-Path $root 'build/libs/leaderboard-service-1.0.0.jar' }
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build the service first with .\gradlew.bat bootJar.' }
$build = Join-Path $root 'build'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$process = Start-Process -FilePath $javaPath -ArgumentList @('-jar', "`"$jarPath`"") -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $build 'service.log') -RedirectStandardError (Join-Path $build 'service-error.log')
Set-Content -LiteralPath (Join-Path $build 'service.pid') -Value $process.Id
Write-Output "Started leaderboard service PID $($process.Id) at http://localhost:$Port/"

