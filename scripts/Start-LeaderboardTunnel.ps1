param([int]$Port=8090)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$build=Join-Path $root 'build'
$pidFile=Join-Path $build 'cloudflare.pid'; $urlFile=Join-Path $build 'cloudflare.url'
if (!(Test-Path -LiteralPath $pidFile) -or !(Test-Path -LiteralPath $urlFile)) { throw 'No existing fixed-hostname tunnel was found. This script will not create a quick tunnel because Riot review uses a submitted fixed URL.' }
$url=(Get-Content -LiteralPath $urlFile -Raw).Trim()
if ($url -notmatch '^https://knights-helps-especially-peers\.trycloudflare\.com$') { throw "The saved URL does not match the submitted hostname. Refusing to change or start a tunnel: $url" }
$tunnelPid=[int](Get-Content -LiteralPath $pidFile)
$proc=Get-CimInstance Win32_Process -Filter "ProcessId=$tunnelPid" -ErrorAction SilentlyContinue
if (!$proc -or $proc.Name -ne 'cloudflared.exe' -or $proc.CommandLine -notlike "*127.0.0.1:$Port*") { throw 'The submitted tunnel process is unavailable. Do not replace its hostname; restore the existing named tunnel configuration.' }
Write-Output $url

