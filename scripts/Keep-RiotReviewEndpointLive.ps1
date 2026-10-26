param([int]$IntervalSeconds = 120)
$ErrorActionPreference = 'Stop'
$reviewRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$reviewBuild = Join-Path $reviewRoot 'build'
$reviewBaseUrl = 'https://knights-helps-especially-peers.trycloudflare.com'
$reviewToken = '49d563d0-35db-48c1-a616-cc498e0c5d6e'
$reviewStatusFile = Join-Path $reviewBuild 'riot-review-availability.json'
$reviewLogFile = Join-Path $reviewBuild 'riot-review-availability.log'
$reviewMutex = [Threading.Mutex]::new($false, 'Local\ValorantRiotReviewAvailability')
if (!$reviewMutex.WaitOne(0)) { throw 'The Riot review availability worker is already running.' }

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class RiotReviewPower {
  [DllImport("kernel32.dll")]
  public static extern uint SetThreadExecutionState(uint flags);
}
'@

Set-Content -LiteralPath (Join-Path $reviewBuild 'riot-review-availability.pid') -Value $PID
$reviewPrevious = ''
try {
    # Hold system sleep only while this worker runs; the display can still turn off.
    [RiotReviewPower]::SetThreadExecutionState([uint32]2147483649) | Out-Null
    while ($true) {
        $reviewState = [ordered]@{
            checkedAt = (Get-Date).ToUniversalTime().ToString('o')
            submittedUrl = "$reviewBaseUrl/leaderboard.html"
            workerPid = $PID
            localService = $false
            publicDashboard = $false
            verification = $false
            detail = ''
        }
        try {
            $reviewListener = Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue
            if (!$reviewListener) {
                & (Join-Path $reviewRoot 'scripts/Start-LeaderboardService.ps1') | Out-Null
                $reviewState.detail = 'Started stopped leaderboard service; waiting for readiness.'
            } else {
                $reviewState.localService = $true
            }
            $reviewDashboard = Invoke-WebRequest -Uri "$reviewBaseUrl/leaderboard.html" -TimeoutSec 20
            $reviewState.publicDashboard = $reviewDashboard.StatusCode -eq 200 -and $reviewDashboard.Content -match 'Valorant Ranked Observatory'
            $reviewVerification = Invoke-WebRequest -Uri "$reviewBaseUrl/leaderboard.html/riot.txt" -TimeoutSec 20
            $reviewState.verification = $reviewVerification.StatusCode -eq 200 -and $reviewVerification.Content -ceq $reviewToken
        } catch {
            $reviewState.detail = $_.Exception.Message
        }
        # Never replace/restart the quick tunnel: a new hostname would invalidate Riot's submitted URL.
        $reviewState.healthy = $reviewState.publicDashboard -and $reviewState.verification
        $reviewSummary = "$($reviewState.healthy)|$($reviewState.localService)|$($reviewState.detail)"
        if ($reviewSummary -ne $reviewPrevious) {
            Add-Content -LiteralPath $reviewLogFile -Value ($reviewState | ConvertTo-Json -Compress)
            $reviewPrevious = $reviewSummary
        }
        $reviewTemp = "$reviewStatusFile.partial"
        [IO.File]::WriteAllText($reviewTemp, ($reviewState | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $reviewTemp -Destination $reviewStatusFile -Force
        Start-Sleep -Seconds ([Math]::Max(60, $IntervalSeconds))
    }
} finally {
    [RiotReviewPower]::SetThreadExecutionState([uint32]2147483648) | Out-Null
    $reviewMutex.ReleaseMutex()
    $reviewMutex.Dispose()
}



