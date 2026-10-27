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

## Phases M3–M8

Not started. M3 requires the M2 schema/API to pass the dedicated PostgreSQL acceptance test before collector integration. Subsequent phases remain gated on completing and verifying each preceding phase.

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

M4 is the next phase; M5–M8 remain unstarted.

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

M4 has not been authenticated against a running Spring backend in a browser session. Existing H2 Spring and native PostgreSQL API tests cover authorization and persisted values, while the browser’s connected-record UI was exercised with synthetic responses. The production-like map plot, event-coordinate visualization and M5 deterministic coaching findings are not yet available.
