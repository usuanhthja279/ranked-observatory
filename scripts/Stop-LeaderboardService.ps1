param([int]$Port = 8090)
$ErrorActionPreference = 'Stop'
$listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
if (!$listeners) { Write-Output "No application is listening on port $Port."; exit 0 }
foreach ($id in $listeners) { Stop-Process -Id $id -ErrorAction Stop; Write-Output "Stopped listener PID $id on port $Port." }


