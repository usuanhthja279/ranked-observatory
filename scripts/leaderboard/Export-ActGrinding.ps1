$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$settings=Get-Content -LiteralPath (Join-Path $taskRoot 'config/leaderboard.local.json') -Raw|ConvertFrom-Json
$env:LEADERBOARD_DB_URL=$settings.url
$env:LEADERBOARD_DB_USERNAME=$settings.username
$env:LEADERBOARD_DB_PASSWORD=(Get-Content -LiteralPath (Join-Path $taskRoot $settings.passwordFile) -Raw).Trim()
$driver=Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql') -Recurse -Filter '*.jar'|Select-Object -First 1 -ExpandProperty FullName
$java='C:/Users/usuan/.jdks/dragonwell-21.0.12/bin/java.exe'
try {
 & $java -cp $driver (Join-Path $PSScriptRoot 'ExportActGrinding.java') (Join-Path $PSScriptRoot 'export_act_grinding.sql') (Join-Path $taskRoot 'build/act-grinding-inputs.json')
 if($LASTEXITCODE -ne 0){throw 'Read-only act export failed.'}
 Write-Output 'Exported act-to-date wins and all retained current-act rank history.'
} finally {Remove-Item Env:LEADERBOARD_DB_PASSWORD -ErrorAction SilentlyContinue}


