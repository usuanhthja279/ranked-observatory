$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$baseUrl = 'http://127.0.0.1:8090/api/leaderboard'
$host.UI.RawUI.WindowTitle = 'Valorant leaderboard extraction monitor'
function Get-Field($Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if (!$property) {
        $camel = [regex]::Replace($Name, '_([a-z])', { param($m) $m.Groups[1].Value.ToUpperInvariant() })
        $property = $Object.PSObject.Properties[$camel]
    }
    if ($property) { return $property.Value }
    return $null
}

while ($true) {
    Write-Host "`n[$(Get-Date -Format 'HH:mm:ss')] VALORANT LEADERBOARD · EXTRACTION STATUS" -ForegroundColor Cyan
    try {
        $live = Invoke-RestMethod -Uri "$baseUrl/status?compact=true" -TimeoutSec 8
        $snapshot = $live.latestSnapshot | Select-Object -First 1
        $progress = $live.liveProgress
        Write-Host 'CURRENT ACT' -ForegroundColor Yellow
        Write-Host "  State:      $(if ($live.running) { 'Collecting' } elseif ($live.enabled) { 'Waiting for next run' } else { 'Paused until historical archive completes' })"
        Write-Host "  Act:        $($live.act)"
        Write-Host "  Source:     $(if ($progress.source) { $progress.source } else { '—' })"
        Write-Host "  Page:       $(if ($progress.sourcePage) { $progress.sourcePage } else { '—' })   Pages processed: $(if ($progress.pagesFetched) { $progress.pagesFetched } else { 0 })"
        Write-Host "  Published:  $(if ($snapshot.completed_at) { $snapshot.completed_at } else { 'No complete snapshot yet' })"
        if ($live.lastError) { Write-Host "  Last error: $($live.lastError)" -ForegroundColor Red }

        $acts = Invoke-RestMethod -Uri "$baseUrl/acts" -TimeoutSec 8
        $historical = @($acts | Where-Object { $_.act -ne $live.act })
        $completed = ($historical | ForEach-Object { [int](Get-Field $_ 'completed_sources') } | Measure-Object -Sum).Sum
        $total = ($historical | ForEach-Object { [int](Get-Field $_ 'total_sources') } | Measure-Object -Sum).Sum
        $pages = ($historical | ForEach-Object { [int](Get-Field $_ 'pages') } | Measure-Object -Sum).Sum
        $rows = ($historical | ForEach-Object { [int](Get-Field $_ 'source_rows') } | Measure-Object -Sum).Sum
        if ($null -eq $completed) { $completed = 0 }
        if ($null -eq $total) { $total = 0 }
        if ($null -eq $pages) { $pages = 0 }
        if ($null -eq $rows) { $rows = 0 }
        Write-Host ''
        Write-Host 'ONE-TIME ACT ARCHIVE' -ForegroundColor Yellow
        Write-Host "  Acts:       $($historical.Count) completed acts"
        Write-Host "  Sources:    $completed / $total global/regional ladders complete"
        Write-Host "  Checkpoint: $pages pages saved · $rows source rows stored"

        $nextAct = $historical | Where-Object { [int](Get-Field $_ 'completed_sources') -lt [int](Get-Field $_ 'total_sources') } | Select-Object -First 1
        if ($nextAct) {
            $actId = Get-Field $nextAct 'act'
            $archive = Invoke-RestMethod -Uri "$baseUrl/status?act=$actId" -TimeoutSec 8
            $source = $archive.runs | Where-Object { (Get-Field $_ 'status') -eq 'RUNNING' -or (Get-Field $_ 'status') -eq 'PAUSED' } | Select-Object -First 1
            Write-Host "  Current:    $(Get-Field $nextAct 'label')"
            Write-Host "  Parallel:   $(if ($archive.pageConcurrency) { $archive.pageConcurrency } else { 1 }) page requests (ordered checkpoints)"
            $eta = $archive.archiveProgress
            if ($eta.etaSeconds -ne $null) {
                Write-Host "  ETA:        $($eta.etaFormatted) · $($eta.remainingPages) estimated pages left · $($eta.pagesPerMinute) pages/min"
            } else {
                Write-Host "  ETA:        Estimating · $($eta.completedActs)/$($eta.totalActs) acts complete"
            }
            if ($source) {
                Write-Host "  Ladder:     $(Get-Field $source 'source_region') · $(Get-Field $source 'status') · page $(Get-Field $source 'pages') saved · next page $(Get-Field $source 'next_page')"
                if (Get-Field $source 'retry_after') { Write-Host "  Retry:      $(Get-Field $source 'retry_after')" -ForegroundColor DarkYellow }
                if (Get-Field $source 'error') { Write-Host "  Detail:     $(Get-Field $source 'error')" -ForegroundColor DarkYellow }
            } else {
                Write-Host '  Ladder:     Waiting for the archive worker'
            }
        } elseif ($historical.Count -eq 35) {
            Write-Host '  State:      All 35 acts and 245 ladders complete' -ForegroundColor Green
        } else {
            Write-Host "  Catalogue returned $($historical.Count) historical acts; check the service response." -ForegroundColor DarkYellow
        }

        Write-Host ''
        Write-Host 'Recent service messages' -ForegroundColor Yellow
        $log = Join-Path $projectRoot 'build/service.log'
        if (Test-Path -LiteralPath $log) {
            Get-Content -LiteralPath $log -Tail 80 | Where-Object { $_ -match ' INFO .* (Global snapshot|Historical archive)| WARN .* (Global snapshot|Historical archive|Tracker data unavailable)' } | Select-Object -Last 2
        }
    } catch {
        Write-Host "Local service is temporarily unavailable: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host 'The monitor will retry; it does not change the service.'
    }
    Write-Host ''
    Write-Host 'Refreshes every 5 seconds. Close this terminal to stop monitoring.' -ForegroundColor DarkGray
    Start-Sleep -Seconds 5
}



