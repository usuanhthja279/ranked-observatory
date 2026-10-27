# Match Intelligence M1 Verification

## Focused Java tests

Command:

```powershell
$env:JAVA_HOME='C:\Users\usuan\.jdks\dragonwell-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.MatchExportParserTest --no-daemon
```

Result: **passed** (`BUILD SUCCESSFUL`, 9 focused JUnit tests). Coverage includes schema/size/duplicate-key rejection, selected-player and match identity, ambiguity, K/D/A and score reconciliation, incomplete player-round coverage, missing versus zero statistics, side switches, overtime, event ordering and invalid timestamps, duplicate candidates, unknown-source retention, provenance, and self/ability/bomb/fall classification.

## Local real-export validation

A temporary local-only JUnit probe parsed the existing committed export and printed only aggregate counts, quality labels, and issue codes. It reported five accepted matches, zero partial, zero rejected; no raw identifiers or payload were emitted. The temporary test source was deleted after the run. The per-match output is summarized in [REAL_DATA_VALIDATION.md](REAL_DATA_VALIDATION.md).

## Scope limits

No PostgreSQL, Spring application restart, browser flow, collection/admin API, UI, or production workflow was used or changed in M1. M1 is an in-memory parser and validator only. The successful real-export parse does not verify future Tracker response variants, persistence, account isolation, or UI behavior.
