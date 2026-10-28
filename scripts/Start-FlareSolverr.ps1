param([int]$Port = 8191)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$installRoot = Join-Path $root 'build/tools/flaresolverr-v3.5.2'
$binary = @(Get-ChildItem -LiteralPath $installRoot -Filter 'flaresolverr.exe' -File -Recurse -ErrorAction SilentlyContinue)
if ($binary.Count -ne 1) { throw 'Install the verified Windows release under build/tools/flaresolverr-v3.5.2 first.' }
$listener = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if ($listener.Count -gt 0) {
    $owners = @($listener | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
    if ($owners.Count -eq 1 -and $owners[0].Path -eq $binary[0].FullName -and @($listener | Where-Object { $_.LocalAddress -notin @('127.0.0.1','::1') }).Count -eq 0) {
        Write-Output "FlareSolverr is already listening on loopback port $Port (PID $($owners[0].Id))."
        return
    }
    throw "Port $Port is already in use; no process was changed."
}
$env:HOST = '127.0.0.1'
$env:PORT = "$Port"
$env:LOG_HTML = 'false'
$env:LOG_LEVEL = 'info'
$output = Join-Path $root 'build/flaresolverr.log'
$errors = Join-Path $root 'build/flaresolverr-error.log'
$process = Start-Process -FilePath $binary[0].FullName -WorkingDirectory $binary[0].DirectoryName -WindowStyle Hidden -PassThru -RedirectStandardOutput $output -RedirectStandardError $errors
Set-Content -LiteralPath (Join-Path $root 'build/flaresolverr.pid') -Value $process.Id
for ($attempt=0; $attempt -lt 25; $attempt++) {
    if ($process.HasExited) { throw "FlareSolverr exited during startup; check $errors and $output." }
    $ready = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.OwningProcess -eq $process.Id -and $_.LocalAddress -eq '127.0.0.1' })
    if ($ready.Count -gt 0) { Write-Output "Started FlareSolverr on loopback port $Port (PID $($process.Id))."; return }
    Start-Sleep -Seconds 2
    $process.Refresh()
}
throw "FlareSolverr did not become ready on loopback port $Port; check $errors and $output."
