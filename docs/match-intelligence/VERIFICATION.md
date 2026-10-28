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

## M5 deterministic analysis verification

Focused command:

```powershell
$env:JAVA_HOME='C:\Users\usuan\.jdks\dragonwell-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.DeterministicMatchAnalyzerTest --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon
```

Result: **BUILD SUCCESSFUL**. Deterministic tests validate unique versus tied/missing opening timestamps, selected-team conversion including teammate openings, timed reciprocal trade relationships, versioned loadout bands, recorded damage averages and evidence-linked report metadata. The Spring regression verifies unauthenticated access is rejected, the importing owner receives a report, and another account receives not-found.

Frontend checks:

```powershell
node --check src/main/resources/static/match-intelligence-workspace.js
node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/match-intelligence-workspace.test.cjs src/test/js/tracker-network-analysis.test.cjs
```

Result: syntax check passed; 17 Node tests passed. PostgreSQL was not rerun because no schema or persistence code changed in M5. No live Tracker requests, collection/admin API calls, or restarts of the active 8090 application occurred. Browser backend verification of the Coaching report remains pending because 8091 is static-only; synthetic browser evidence must be separately identified if used.

ChatGPT in-app browser verification used the ignored synthetic-only preview on port 8092. The Coaching tab displayed a synthetic report on desktop and in a 390-pixel mobile iframe. The measured mobile document width was 375 CSS px with a 390px viewport and the horizontal tab overflow was confined to the tab strip. The finding category and round labels were visually inspected; their spacing was corrected. This does not verify the new endpoint against a live authenticated browser session, because the port-8091 preview is static-only and port 8090 was intentionally left running without a restart.

## M5 owner-perspective correction

Command:

```powershell
$env:JAVA_HOME='C:\Users\usuan\.jdks\dragonwell-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.DeterministicMatchAnalyzerTest --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon
```

Result: **BUILD SUCCESSFUL**. The analysis endpoint regression verifies selected-player opening kills are calculated from the authenticated import perspective after canonical shared data has had that perspective stripped. A different owner remains unable to read the match. Shared stored canonical JSON is unchanged.

## M6 benchmark cohort verification

Command:

```powershell
$env:JAVA_HOME='C:\Users\usuan\.jdks\dragonwell-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:LEADERBOARD_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5435/ranked_observatory_redesign_test'
# Credentials were loaded for the test process from the ignored, ACL-restricted local pgpass file.
.\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.PostgresMatchIntelligenceIntegrationTest --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon
```

Result: **BUILD SUCCESSFUL**. The Spring suite reported 19 passing tests. The native PostgreSQL suite reported 2 passing tests and 1 skipped optional local-export case. Its uniquely named schema migrated through V12 and verified cohort insert/read, permission-status caveat, import-derived identity/match coverage, owner isolation, and updated coverage after import deletion. The environment-variable values and password were not written to project documentation or output.

Additional focused checks:

```powershell
node --check src/main/resources/static/match-intelligence-workspace.js
node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/match-intelligence-workspace.test.cjs src/test/js/tracker-network-analysis.test.cjs
git diff --check
```

Results: JavaScript syntax passed, 17 focused Node tests passed, and diff check passed. ChatGPT in-app browser inspection used a synthetic fixture: desktop and 390px mobile benchmark panels rendered; with 1 eligible reference player and 5 unique matches, the interface displayed `Insufficient Evidence` and suppressed reference medians/quartiles. This is synthetic visual verification, not a real authenticated backend journey. The active Observatory process was not restarted.


## M7 reviewer annotation and baseline verification

The focused M7 run completed successfully against the isolated PostgreSQL 5435 test database. The M7 tests covered the pre-round baseline's deterministic match-grouped split and metrics, no future-result leakage, and insufficient-evidence response; Spring endpoint auth/CSRF and annotation revision behavior; and PostgreSQL V1–V13 annotation persistence, stale correction conflict, append-only withdrawal, and owner cleanup. The full M8 run below reran all Java tests after adding explicit transaction rollback and simultaneous duplicate-import coverage.

The Expert review panel was opened and inspected in the ChatGPT in-app browser against the ignored synthetic preview. It showed the privacy/evidence warning, round and event selection, tactical judgment, and reviewer reason controls. This was UI presentation verification only; the preview did not call a real authenticated annotation API. Predictive metrics in the unit tests used synthetic outcomes and are not a claim of useful real-match prediction.

## M8 integrated acceptance and release-readiness verification

Full command:

```powershell
$env:JAVA_HOME='C:\Users\usuan\.jdks\dragonwell-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
# PostgreSQL credentials were loaded from the ignored local pgpass file.
.\gradlew.bat test --no-daemon
```

Result: **BUILD SUCCESSFUL; 82 tests, 0 failures, 0 errors, 2 opt-in tests skipped.** Native PostgreSQL integration tests used `127.0.0.1:5435/ranked_observatory_redesign_test`, isolated schemas, and Flyway through V13. They covered V1–V2 migration paths, multipage persistence/conflicts, scanned asset authorization, preflight persistence/history, customer/designer/owner permissions, acknowledgement authorization, immutable approval, annotation ownership/revisions, late SQL failure rollback, and concurrent duplicate imports. The two skips are explicitly opt-in tests, not reported as passing.

Frontend and collector checks:

```powershell
node --test src/test/js/*.test.cjs
node --check src/main/resources/static/leaderboard.js
node --check scripts/leaderboard/browser-smoke.cjs
python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 5
python scripts/leaderboard/test_tracker_collector_extension.py
 git diff --check
```

Results: 52 Node tests passed; syntax checks and diff check passed. Both collector commands passed using synthetic/intercepted response bodies; the full harness covered explicit-request gating, background-tab behavior, bounded queue, JSON export, deduplication, partial invalid data, and safe stop for HTTP 403/429. No real Tracker collection was initiated.

M3 checkpoint correction: the synthetic extension harness was rerun after adding a local IndexedDB checkpoint. It stopped the runner after its first validated match, confirmed the checkpoint held one validated response and next index 1, closed the runner tab, then explicitly started again. The collector resumed at the next match, completed all five, and made exactly five distinct response requests across the interrupted and resumed portions (the first match was not recaptured). The same run passed duplicate/malformed-data and HTTP 403/429 stopping regressions. This is synthetic-only verification; no live Tracker requests were issued.

Browser verification:

- `node scripts/leaderboard/browser-smoke.cjs` against the isolated app at `http://127.0.0.1:8093`: **90 route/viewport checks passed**, including `/match-analysis`, search keyboard behavior and region-tab keyboard navigation. Desktop/mobile screenshots are retained in ignored `build/m8-browser-smoke/` (`overview-desktop.png`, `region-mobile.png`, `trends-mobile.png`) and were visually inspected. They are synthetic/isolated-app evidence, not authenticated account screenshots.
- ChatGPT in-app browser opened `/match-analysis` on the same isolated app and confirmed the sign-in-required state with no authenticated match data. Back and Forward navigation between Match analysis and Watchlist worked.
- The temporary app used extraction disabled and its own `mi_m8_smoke` schema on port 8093. Port 8090 was left running and untouched; live extraction stayed enabled, archive extraction stayed paused.

M8 fixes were made for empty snapshot comparisons (`null`/`undefined`) and for the Match analysis route's dependency on unrelated leaderboard APIs. The Gradle wrapper executable mode was corrected for Linux CI invocation. GitHub Actions itself was not run. There was no authenticated Match analysis flow, fresh live Tracker collection, or model-quality claim in this acceptance pass. No merge or deployment was performed.
