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

## M3 collector transport verification

The latest focused frontend tests passed with:

```powershell
node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/tracker-network-analysis.test.cjs
python scripts/leaderboard/test_tracker_collector_extension.py
python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 10
python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 20
```

Results: 15 Node tests passed; synthetic Chromium full suite passed; synthetic network mode passed for 10 and 20 requested records, creating respectively two and four API imports capped at five records each and preserving each response JSON file. The full synthetic suite also exercised local profile route validation, switching tabs while the Tracker popup remained active, DOM fallback, identity checks, deduplication, malformed responses, and HTTP 403/429 stopping. It never contacted Tracker.

The native PostgreSQL acceptance run on the dedicated port-5435 test cluster ran 2 integration cases. It passed the normal persistence test and the opt-in local export test (five accepted match versions). With 18 `StandaloneApplicationTest` and 9 parser tests, the latest combined focused Java run had 29 passing tests. No CI Actions result was checked in this phase. Neither the source app (port 8090) nor its live/archive extraction controls were restarted or changed. Real extension operation against Tracker and the latest packaged UI on port 8090 remain unverified; use the source preview for local smoke checks and reload the extension to test it in a supported Chromium browser.

## M4 workspace verification

- `node --test src/test/js/match-intelligence-workspace.test.cjs` — 2 passed.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon` — **BUILD SUCCESSFUL**; the route test verified `/match-analysis` forwards into the existing shell.
- The built-in browser inspected the source preview route and a separate synthetic-response preview. Synthetic saved match overview, detail tabs, selected round, event list and economy were exercised. Mobile viewport was constrained to a 390-pixel iframe; rendered document width was 375 CSS px due to its vertical scrollbar and equaled document scroll width. No horizontal page overflow was observed.
- The source preview is static-only, so its API request correctly showed an unavailable state. Browser UI data was synthetic and did not constitute authenticated live-backend acceptance. Existing backend auth and persistence are covered by M2 Spring/PostgreSQL tests.
- Port 8090 was not restarted; live extraction remained untouched. Temporary synthetic preview was run separately on 8092 and will be stopped after use.
