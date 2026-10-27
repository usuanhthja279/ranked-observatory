# Match Intelligence Implementation Progress

## Phase M1 — Canonical model and validation

**Status: complete.** Added a bounded schema-v1 export parser, provider-scoped identity resolution, response validation, canonical in-memory records, source provenance, and reconciliation with machine-readable quality statuses. Unknown source segments and duplicate-looking events are preserved and disclosed. No raw export was copied into fixtures or logs.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.MatchExportParserTest --no-daemon` — passed, 9 focused tests.
- Local-only validation of the already committed sample export — passed: 5 accepted, 0 partial, 0 rejected. The temporary probe was removed and is not part of the repository.
- See [VERIFICATION.md](VERIFICATION.md) and [REAL_DATA_VALIDATION.md](REAL_DATA_VALIDATION.md) for actual details and limitations.

### Safety and phase boundary

The Observatory process and collection controls were not changed; no database, API, collector, or UI work was started. The existing sample dataset was read-only. Phase M2 and later phases have not started.

### Phase boundary

M2 was not started during the original M1 task. The user has now authorized continuation through M8; M2 implementation and verification status follows below.

## Phase M2 — Persistence and APIs

**Complete.** Added Flyway V11 storage for shared canonical match versions and normalized team, participant, round, player-round, damage, event, and position records. Imports are account-owned, CSRF-protected, bounded to 25 MiB/20 matches, serialized one at a time per app process, and committed transactionally. Raw exports are gzip-compressed, private, never returned by read APIs, expire after 90 days by default, and can be explicitly deleted. Owner-scoped import, match, round, and deletion APIs are implemented. Duplicate inserts now recover to a transaction savepoint so PostgreSQL can safely continue idempotent imports.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --rerun-tasks --tests com.valorant.leaderboard.StandaloneApplicationTest --tests com.valorant.leaderboard.matchintelligence.MatchExportParserTest --tests com.valorant.leaderboard.matchintelligence.PostgresMatchIntelligenceIntegrationTest --no-daemon` — **BUILD SUCCESSFUL** against H2 and isolated native PostgreSQL: 28 focused tests passed. The opt-in real-export test separately passed against the same isolated PostgreSQL database and confirmed five complete imports.
- The H2-backed Spring flow verifies authentication, CSRF, user isolation, duplicate import deduplication, relational round/event/position persistence, round APIs, private raw deletion, owner deletion, and canonical cleanup after the last import link is removed.
- Native PostgreSQL 17 ran on `127.0.0.1:5435` using the ignored local cluster under `build/match-intelligence-test-postgres`; test schemas were uniquely named and dropped after each run. The local five-match export was imported and verified in a temporary schema, then deleted. The dedicated test password is held only in an ignored local file with a restricted Windows ACL. The existing database on port 5434 and the running application were not touched.

### M2 boundary and remaining acceptance

The dedicated PostgreSQL test checks V1–V11 migration, five-match sample ingestion, duplicate import/event deduplication, and owner deletion cleanup. Explicit forced SQL failure rollback and concurrent import behavior remain for M8. The application on port 8090 and its extraction state were not restarted or changed. No Tracker collection or admin API was called. See [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), and [VERIFICATION.md](VERIFICATION.md).

## Phase M3 — Bounded collector import

**Complete.** The Tracker extension now supports an explicit selected-player target of 5, 10, or 20 matches in one dedicated collector window. It observes only successful JSON match responses that Tracker itself requested after a user opens each match through the UI. Match IDs are deduplicated, progress and partial failures are preserved, and raw response JSON is offered as a local download. For an authenticated Observatory page, validated response records are sent in sequential batches of at most five through the existing authenticated, CSRF-protected import API. An API failure leaves the local JSON export available. Network response records and rendered DOM fallback captures remain distinct; DOM-only captures are not misrepresented as raw JSON or uploaded through this path.

The browser bridge accepts requests only from the local Observatory origin and a validated player profile route. HTTP 403/429, private profile or challenge signals still stop the collection. No Tracker request is fabricated, no cookies or headers are copied, and there is no public unauthenticated import endpoint. M2’s server parser and ownership rules remain authoritative. The app process and live/archive extraction states were not changed.

### Verification

- `node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/tracker-network-analysis.test.cjs` — passed 15 focused tests.
- `python scripts/leaderboard/test_tracker_collector_extension.py` — passed synthetic Chromium UI, route, window visibility, fallback, identity, duplicate, malformed response, 403 and 429 cases.
- `python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 10` — passed with 10 synthetic response bodies, two bounded API imports, and one JSON export.
- `python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 20` — passed with 20 synthetic response bodies, four bounded API imports, and one JSON export.
- These are synthetic extension tests, not live Tracker acceptance. No real Tracker request was made during this phase. The opt-in native PostgreSQL M2 export test independently verified five complete imports; see [VERIFICATION.md](VERIFICATION.md).

### Limitations

- Local checkpoints/resume after a browser or extension crash are not implemented. Completed response batches and already downloaded JSON remain durable; an interrupted batch must be recollected.
- Target values indicate maximum attempts, not guaranteed access. Partial count is shown honestly and failures are retained.
- The latest packaged extension must be reloaded from `build/tracker-collector-extension`; the running Observatory on 8090 has intentionally not been restarted to preserve its active extraction.

M4 followed in sequence; M6–M8 remain unstarted after the M5 completion recorded below.

## Phase M4 — Backend-backed Match Analysis workspace

**Complete.** Added an account-wide `/match-analysis` route and primary navigation entry plus a Player Profile integration. The workspace reads only the existing owner-scoped match list/detail APIs. It reports source-backed match, score, K/D/A, ACS/ADR, opening summary, map/agent usage, pistol and recorded-economy coverage, with small-sample and unavailable labels. Match detail has keyboard-operable Overview, Rounds, Timeline, Economy, Team Analysis and Coaching tabs; round selection and filters include outcomes, regulation pistols, overtime and reported attack/defense side. The detail exposes team/agent roster, provenance, validation quality, source availability, round records, event categories, raw source time units and coordinate snapshots. It does not infer buy categories or tactical conclusions.

No map plot is shown: the source data may contain coordinates, but a matching map asset and validated display transform were not established. Positions remain a table of discrete observations, never continuous paths. Coaching explicitly remains unavailable until M5. The page uses the shared Observatory shell and responsive table scrolling. No backend workflow or permissions changed.

### Verification

- `node --test src/test/js/match-intelligence-workspace.test.cjs` — 2 passed. The tests cover observed sample aggregation, unknown scores not being counted as losses, pistol/economy/map/agent summaries, and round filters.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon` — **BUILD SUCCESSFUL**, including the new `/match-analysis` shared-shell forward route regression.
- ChatGPT in-app browser desktop inspection at `http://127.0.0.1:8091/leaderboard.html?view=match-analysis` confirmed the real Observatory shell, navigation and clear API-unavailable state on source preview (8091 does not host backend APIs).
- A separate synthetic-only browser fixture served the actual M4 module and CSS. In the built-in browser, a saved match opened through Overview and Rounds tabs, with event, economy and missing-position states. The same workspace rendered in a 390-pixel iframe viewport; document width equaled viewport width (375 CSS px after the vertical scrollbar), with dense tables constrained to internal scrolling. The fixture was synthetic, not a backend data acceptance run.
- No application process was restarted. Live extraction remained undisturbed.

### Limitations

M4 has not been authenticated against a running Spring backend in a browser session. Existing H2 Spring and native PostgreSQL API tests cover authorization and persisted values, while the browser’s connected-record UI was exercised with synthetic responses. The production-like map plot and event-coordinate visualization are not available.

## Phase M5 — Deterministic tactical analysis

**Complete.** Added a versioned Java analysis service, authenticated owner-scoped per-match analysis endpoint, and on-demand Coaching tab report. Metrics cover uniquely timed opening engagements, team conversion after any selected-team opening advantage, reciprocal trade-like kill sequences, per-team recorded loadout comparisons and versioned own-team loadout bands, explicit objective events, multi-kill rounds, no-death rounds, and mean reported damage. Findings link to canonical event IDs/source references and identify the rule/evidence version, confidence, completeness, uncertainty and review question. Missing values stay unavailable. Team loadout bands are explicitly described as heuristic ranges, not source eco/full-buy classifications. Early deaths are descriptive review moments; they are not automatically errors. No aggregate player score is produced.

Clutches, exact trade opportunity, visibility, movement paths, utility value, voice communication, intention and expert judgments remain unavailable. The report explicitly marks these limits and never claims a top-Radiant benchmark; that belongs to a later phase and requires a defensible cohort.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.DeterministicMatchAnalyzerTest --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon` — **BUILD SUCCESSFUL**. Focused deterministic-rule cases cover selected-player and teammate opening conversion, tied/missing opening timestamps, reciprocal timed trade relationships, economy bands, damage averaging, report version/evidence links, and unauthenticated/owner/other-account API access.
- `node --check src/main/resources/static/match-intelligence-workspace.js` — passed.
- `node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/match-intelligence-workspace.test.cjs src/test/js/tracker-network-analysis.test.cjs` — 17 passed.
- No schema change or database migration was required. Existing PostgreSQL M2 tests were not rerun for this phase because M5 only analyzes an already owner-authorized canonical record and does not change persistence; the new route is covered by the focused Spring/H2 authorization regression.
- The running port-8090 service was not restarted to preserve live extraction. Browser-backed authenticated data for the new coaching report was not available from the static-only source preview; the report renderer is wired to the endpoint but remains pending live application acceptance.
- ChatGPT in-app browser synthetic fixture verified the M5 Coaching report on desktop and mobile. The report version, loadout caveat, source-linked finding and unavailable-analysis list rendered. At a 390px mobile frame, the document remained 375 CSS px wide (viewport 390px including vertical scrollbar), with tabs contained in their own horizontal scroller. The screenshot inspection exposed touching category/round labels; the spacing was fixed and rechecked. This is synthetic UI verification, not authenticated backend browser acceptance.

### Phase boundary

M5 is complete. M6 contextual benchmarks and cohort/sample adequacy are next; M7 ML and M8 integrated release verification remain unstarted.

### M5 correction — restore the owner-selected player perspective

A post-phase review found that canonical match versions intentionally strip selected-player identity so shared match records do not retain an importing account's perspective. The analysis route now restores the requesting owner's selected-player perspective in memory from that owner's import link before calculating player metrics. It does not alter the shared canonical record or widen owner-scoped access.

Focused verification: the Spring endpoint regression asserts that the selected player's opening-kill metric is computed as 1/1 for the owner and that another account still receives not found. `StandaloneApplicationTest` passed with the focused M5 analyzer suite. This correction was found before beginning M6.
