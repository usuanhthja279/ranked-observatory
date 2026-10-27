$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $root 'browser-extension/tracker-collector'
$destination = Join-Path $root 'build/tracker-collector-extension'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Copy-Item -Path (Join-Path $source '*') -Destination $destination -Force
Copy-Item -LiteralPath (Join-Path $root 'src/main/resources/static/tracker-match-capture.js') -Destination $destination -Force
Copy-Item -LiteralPath (Join-Path $root 'src/main/resources/static/match-analyzer.js') -Destination $destination -Force
Write-Output "Load this unpacked extension folder in Chrome, Edge or Brave: $destination"
