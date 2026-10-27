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

## M2 API and persistence tests

Command:

```powershell
$env:JAVA_HOME='C:\Users\usuan\.jdks\dragonwell-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --tests com.valorant.leaderboard.matchintelligence.MatchExportParserTest --tests com.valorant.leaderboard.matchintelligence.PostgresMatchIntelligenceIntegrationTest --no-daemon
```

Result: **BUILD SUCCESSFUL**. The selected suites reported 18 `StandaloneApplicationTest` cases and 9 parser cases passed; the dedicated PostgreSQL integration test was skipped because `LEADERBOARD_TEST_DB_URL`, `LEADERBOARD_TEST_DB_USERNAME`, and `LEADERBOARD_TEST_DB_PASSWORD` were not present. The Spring API regression used only its isolated H2 fixture, not the running application database.

The active PostgreSQL port 5434 accepted TCP connections but rejected a temporary read-only `postgres`/empty-password probe with SCRAM authentication required. The temporary test source was deleted. No password was discovered, printed, or stored, and no schema was created.

The H2 test exercises an owner import twice and verifies one canonical match/version and one set of normalized round/event rows, then verifies other-account denial and owner deletion. This is not a substitute for native PostgreSQL migration or transaction verification.

## Scope limits

No PostgreSQL database was changed, the Spring production app was not restarted, and no browser, collection/admin API, or UI flow was used. M1 is an in-memory parser and validator; M2's Spring API tests used an isolated H2 schema. The native PostgreSQL M2 test remains pending. No Tracker request was made.
