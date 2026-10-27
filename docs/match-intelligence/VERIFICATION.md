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

Result: **BUILD SUCCESSFUL**. The focused suites reported 18 `StandaloneApplicationTest` cases, 9 parser cases, and the PostgreSQL persistence test passed against isolated PostgreSQL. The Spring API regression used its isolated H2 fixture, not the running application database.

The active PostgreSQL database on 5434 was left untouched. A separate native PostgreSQL 17 test cluster was created at `127.0.0.1:5435`, database `ranked_observatory_redesign_test`, role `mi_test`, under the ignored `build/match-intelligence-test-postgres` folder. Its generated password is kept in a local-only `pgpass.conf` with a restricted Windows ACL and is not included here.

Additional focused command, with the dedicated environment variables configured from the local private credentials:

```powershell
.\gradlew.bat test --rerun-tasks --tests com.valorant.leaderboard.matchintelligence.PostgresMatchIntelligenceIntegrationTest --no-daemon
```

Result: **BUILD SUCCESSFUL** against PostgreSQL 17. The test applied all migrations into a unique temporary schema, imported the same synthetic export twice, verified one canonical version and one normalized event/player-round set, then verified owner deletion and cleanup. A separate opt-in test imported the local five-match sample, verified five complete accepted matches and five canonical versions, and removed its temporary schema. The source export was never copied into a fixture, emitted in logs, or committed by this work.

The first native run exposed that catching a PostgreSQL duplicate-key exception leaves the transaction aborted; idempotent inserts now use a savepoint and rollback to it before continuing. The integration fixture also now registers Java time support and asserts the actual 20 player-round rows created by its two-round fixture.

## Scope limits

Only the isolated disposable test cluster on 5435 was used. The active port-8090 application and its extraction state were not restarted or changed, and no collection/admin API or Tracker request was made. Phase M2 native PostgreSQL acceptance is complete; its APIs remain covered by the existing authenticated/CSRF H2 Spring tests, while SQL persistence is covered on PostgreSQL.
