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

The browser bridge accepts requests only from the local Observatory origin and a validated player profile route. HTTP 403/429, private profile or challenge signals still stop the collection. No Tracker request is fabricated, no cookies or headers are copied, and there is no public unauthenticated import endpoint. M2’s server parser and ownership rules remain authoritative. Version 0.5.0 adds a local IndexedDB checkpoint for validated captures/responses, bounded progress and failures; an explicit new click resumes the same player/target after an interrupted runner. Restored data is revalidated, checkpoints expire after 30 days, and restricted-source stops clear them. The app process and live/archive extraction states were not changed.

### Verification

- `node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/tracker-network-analysis.test.cjs` — passed 15 focused tests.
- `python scripts/leaderboard/test_tracker_collector_extension.py` — passed synthetic Chromium UI, route, window visibility, fallback, identity, duplicate, malformed response, 403 and 429 cases.
- `python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 10` — passed with 10 synthetic response bodies, two bounded API imports, and one JSON export.
- `python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 20` — passed with 20 synthetic response bodies, four bounded API imports, and one JSON export.
- `python scripts/leaderboard/test_tracker_collector_extension.py` — passed with the added interruption case: closed the runner after one response, verified the local IndexedDB checkpoint, started a new explicit collection, resumed after the first match without duplicate response requests, and completed the five-match JSON/account import flow. Existing 403/429 stop cases passed in the same run.
- These are synthetic extension tests, not live Tracker acceptance. No real Tracker request was made during this phase. The opt-in native PostgreSQL M2 export test independently verified five complete imports; see [VERIFICATION.md](VERIFICATION.md).

### Limitations

- Target values indicate maximum attempts, not guaranteed access. Partial count is shown honestly and failures are retained.
- Resume checkpoints are local to the browser profile, may include private full match response data, expire after 30 days, and should not be shared. A storage/quota failure is reported while the active run continues without resume protection.
- The latest packaged extension must be reloaded from `build/tracker-collector-extension`; the running Observatory on 8090 has intentionally not been restarted to preserve its active extraction.

M4 followed in sequence; M6–M8 were subsequently implemented and verified as recorded below.

## Phase M4 — Backend-backed Match Analysis workspace

**Workspace and verified tactical-map overlay complete.** Added an account-wide `/match-analysis` route and primary navigation entry plus a Player Profile integration. The workspace reads only the existing owner-scoped match list/detail APIs. It reports source-backed match, score, K/D/A, ACS/ADR, opening summary, map/agent usage, pistol and recorded-economy coverage, with small-sample and unavailable labels. Match detail has keyboard-operable Overview, Rounds, Timeline, Economy, Team Analysis and Coaching tabs; the Economy tab charts complete per-team sums of recorded player loadouts alongside the selected player's recorded economy table. Round filters include outcomes, regulation pistols, overtime, reported attack/defense side, uniquely timed player opening kills/deaths, recorded multi-kills, explicit objective-event types, and evidence-linked review findings. It does not infer buy categories or tactical conclusions.

The later M4 follow-up adds map plots only for catalog maps whose identity, validity dates and source transform match. Positions remain discrete observations, never continuous paths. Coaching uses the later M5 report; missing evidence remains unavailable. The page uses the shared Observatory shell and responsive table scrolling. No backend workflow or permissions changed.

### Verification

- `node --test src/test/js/match-intelligence-workspace.test.cjs` — 2 passed. The tests cover observed sample aggregation, unknown scores not being counted as losses, pistol/economy/map/agent summaries, and round filters.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon` — **BUILD SUCCESSFUL**, including the new `/match-analysis` shared-shell forward route regression.
- ChatGPT in-app browser desktop inspection at `http://127.0.0.1:8091/leaderboard.html?view=match-analysis` confirmed the real Observatory shell, navigation and clear API-unavailable state on source preview (8091 does not host backend APIs).
- A separate synthetic-only browser fixture served the actual M4 module and CSS. In the built-in browser, a saved match opened through Overview and Rounds tabs, with event, economy and missing-position states. The same workspace rendered in a 390-pixel iframe viewport; document width equaled viewport width (375 CSS px after the vertical scrollbar), with dense tables constrained to internal scrolling. The fixture was synthetic, not a backend data acceptance run.
- No application process was restarted. Live extraction remained undisturbed.

### Limitations

The M4 module has since been authenticated in a real browser against an isolated Spring service and native PostgreSQL schema using a synthetic two-round import; see the M8 follow-up in [VERIFICATION.md](VERIFICATION.md). The M4 follow-up below adds a map overlay gated on a current map ID/date/catalog transform match. Out-of-map source coordinates remain visible in the authoritative table and are not clamped onto the image.

## Phase M5 — Deterministic tactical analysis

**Complete.** Added a versioned Java analysis service, authenticated owner-scoped per-match analysis endpoint, and on-demand Coaching tab report. Metrics cover uniquely timed opening engagements, team conversion after any selected-team opening advantage, reciprocal trade-like kill sequences, per-team recorded loadout comparisons and versioned own-team loadout bands, explicit objective events, multi-kill rounds, no-death rounds, and mean reported damage. Findings link to canonical event IDs/source references and identify the rule/evidence version, confidence, completeness, uncertainty and review question. Missing values stay unavailable. Team loadout bands are explicitly described as heuristic ranges, not source eco/full-buy classifications. Early deaths are descriptive review moments; they are not automatically errors. No aggregate player score is produced.

Clutches, exact trade opportunity, visibility, movement paths, utility value, voice communication, intention and expert judgments remain unavailable. The report explicitly marks these limits and never claims a top-Radiant benchmark; that belongs to a later phase and requires a defensible cohort.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.DeterministicMatchAnalyzerTest --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon` — **BUILD SUCCESSFUL**. Focused deterministic-rule cases cover selected-player and teammate opening conversion, tied/missing opening timestamps, reciprocal timed trade relationships, economy bands, damage averaging, report version/evidence links, and unauthenticated/owner/other-account API access.
- `node --check src/main/resources/static/match-intelligence-workspace.js` — passed.
- `node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/match-intelligence-workspace.test.cjs src/test/js/tracker-network-analysis.test.cjs` — 17 passed.
- No schema change or database migration was required. Existing PostgreSQL M2 tests were not rerun for this phase because M5 only analyzes an already owner-authorized canonical record and does not change persistence; the new route is covered by the focused Spring/H2 authorization regression.
- The running port-8090 service was not restarted to preserve live extraction. Browser-backed authenticated coaching data was later verified against the isolated native PostgreSQL service with a synthetic import; this does not constitute a live-source match-quality claim.
- ChatGPT in-app browser synthetic fixture verified the M5 Coaching report on desktop and mobile. The report version, loadout caveat, source-linked finding and unavailable-analysis list rendered. At a 390px mobile frame, the document remained 375 CSS px wide (viewport 390px including vertical scrollbar), with tabs contained in their own horizontal scroller. The screenshot inspection exposed touching category/round labels; the spacing was fixed and rechecked. This is synthetic UI verification, not authenticated backend browser acceptance.

### Phase boundary

M5 implementation is complete. M6 contextual benchmarks, M7 model evaluation, and M8 integrated verification are recorded below.

### M5 correction — restore the owner-selected player perspective

A post-phase review found that canonical match versions intentionally strip selected-player identity so shared match records do not retain an importing account's perspective. The analysis route now restores the requesting owner's selected-player perspective in memory from that owner's import link before calculating player metrics. It does not alter the shared canonical record or widen owner-scoped access.

Focused verification: the Spring endpoint regression asserts that the selected player's opening-kill metric is computed as 1/1 for the owner and that another account still receives not found. `StandaloneApplicationTest` passed with the focused M5 analyzer suite. This correction was found before beginning M6.

## Phase M6 — Contextual benchmark cohorts

**Complete.** Added Flyway V12 owner-private cohort and membership storage. Owners can record a ten-player selection with rank at selection, region, Act, snapshot date, up to twenty requested matches per member, and an explicit access-status statement. The API does not verify rank or consent. Member imports, distinct match IDs, identity availability, match-time rank labels, and source coverage are computed from that account's stored imports. Cohort rows cascade-delete with the cohort; canonical shared match data is not modified.

The comparison endpoint is owner-scoped and uses only ranked Competitive matches in the cohort Act. Optional map, exact mode-name, and agent filters narrow the scenario. It aggregates each eligible player's deterministic M5 metric across that player's matches first, then calculates the reference median and interquartile spread across players. The subject is excluded by Riot ID. Shared IDs count once in unique-match coverage. At least ten reference players and twenty unique reference matches are required before reference median/quartiles are shown; metric-level coverage is also reported. The interquartile range is labeled as between-player spread, not a confidence interval. Unsupported categories and equal opponent difficulty are not inferred.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.PostgresMatchIntelligenceIntegrationTest --tests com.valorant.leaderboard.StandaloneApplicationTest --no-daemon` — **BUILD SUCCESSFUL**. `StandaloneApplicationTest`: 19 passed. Native PostgreSQL test suite: 2 passed, 1 opt-in local-export test skipped. The PG tests applied Flyway V1–V12 in unique schemas on the isolated 5435 database, verified cohort persistence/import-derived coverage/owner isolation, and confirmed import deletion removes the cohort's derived match coverage. No live 5434 database was used.
- `node --check src/main/resources/static/match-intelligence-workspace.js` — passed.
- `node --test src/test/js/match-analyzer.test.cjs src/test/js/match-intelligence-client.test.cjs src/test/js/match-intelligence-workspace.test.cjs src/test/js/tracker-network-analysis.test.cjs` — 17 passed.
- `git diff --check` — passed.
- ChatGPT in-app browser inspected the actual M6 workspace source with synthetic API fixtures. Desktop showed the cohort selector, scenario filters, comparison and evidence limit. Mobile stacked the filter fields and withheld reference statistics for a synthetic cohort with 1 eligible player and 5 unique matches. This confirms the insufficient-evidence UI behavior only; it is not authenticated backend/browser verification. The screenshot inspection found and fixed filter-field overflow and spacing.
- The Observatory on port 8090 was not restarted, and live/archive extraction settings were not changed. The temporary 8092 preview used only synthetic data.

### Limitations

No high-rank cohort was collected or verified. `OWNER_REPORTED_PERMISSION` and rank selection are unverified owner statements. Match-time rank labels are source-reported observations. No bootJar/production workflow or authenticated browser-backed benchmark run is claimed in this phase. Within-match clustering is not estimated; quartiles are descriptive spread only. Economy buy labels, role equivalence and lobby strength are unavailable and excluded.

### Phase boundary

M6 implementation is complete. M7 model evaluation and M8 end-to-end release-readiness audit are recorded below.


## Phase M7 — Reviewer annotations and evaluated pre-round baseline

**Complete.** Tactical judgments are private to the importing account, tied to an observed match round and optional event, and stored as append-only revisions. Corrections require the current revision; withdrawal appends a `WITHDRAWN` revision. VOD links are validated as HTTPS and retained inertly; the application never fetches them. Selected-player perspective is resolved through the owner import link for the baseline features.

The pre-round result baseline is deterministic CPU-only logistic regression. It uses only the round index, prior score differential, and source-reported side, grouped-split by match ID. Evaluation is on demand and in memory; no model is persisted, activated at startup, or represented as a tactical expert. The UI permits a local copy of the evidence-grounded coaching brief and does not issue an outbound AI request.

Synthetic tests cover deterministic grouped evaluation, future-outcome feature invariance, and insufficient-data behavior. PostgreSQL tests cover V1–V13 and annotation create/correction/stale-revision/withdrawal/ownership/cascade behavior. The Expert review UI was visually inspected with synthetic annotation data in the ChatGPT in-app browser. No real authenticated annotation workflow or real-data predictive quality is claimed.

### Phase boundary

M7 implementation is complete. M8 end-to-end verification and release-readiness audit follows below.

## Phase M8 — Integrated verification and release-readiness audit

**Complete for the implemented M1–M8 scope.** The empty-database Match analysis route now loads independently of leaderboard snapshot/confidence APIs, normalizes empty snapshot comparisons, and is included in the browser smoke route matrix. The Linux executable bit on `gradlew` was corrected so CI runners can invoke the wrapper. M8 adds PostgreSQL transaction rollback coverage for late import failure and concurrent duplicate-import coverage: one canonical match/version is retained while both import jobs complete.

### Verification performed

- Full Java/Gradle suite on Java 21 with the isolated native PostgreSQL test database: **BUILD SUCCESSFUL; 82 tests, 0 failures, 0 errors, 2 opt-in tests skipped**. The PostgreSQL integration suite exercised migrations through V13, late-failure transaction rollback, concurrent duplicate imports, and the M7 annotation/ownership cases. No Docker was used.
- Focused PostgreSQL integration and Spring authorization/lifecycle tests passed. The PostgreSQL database was isolated on port 5435 and used unique test schemas; production/local port 5432 and the live Observatory database were not used.
- `node --test src/test/js/*.test.cjs`: **52 passed, 0 failed**. Browser JavaScript and smoke-script syntax checks passed. `git diff --check` passed.
- `python scripts/leaderboard/test_tracker_collector_extension.py --network-only --target-count 5`: passed with intercepted synthetic responses. The full synthetic collector harness also passed its explicit-request, background-tab, bounded capture, JSON export, deduplication, partial-invalid-data, 403 and 429 cases. This does not represent a fresh live Tracker collection.
- `scripts/leaderboard/browser-smoke.cjs` against the isolated app on port 8093: **90 route/viewport checks passed**, including Match analysis. Desktop and mobile smoke screenshots were saved under ignored `build/m8-browser-smoke/` and inspected; no viewport overflow or obvious clipping was found in the captured views.
- ChatGPT in-app browser opened the isolated Match analysis route and verified its unauthenticated sign-in-required state, then verified browser Back/Forward navigation. This is real application route verification, not an authenticated customer journey.
- The isolated port-8093 app used its own `mi_m8_smoke` schema with extraction disabled. Port 8090 was not restarted or sent collection/admin requests; live extraction remained enabled and archive extraction remained paused.

### Remaining limits

GitHub Actions was not run from this local verification. The M8 follow-up later completed an authenticated Match Analysis browser journey against an isolated PostgreSQL-backed Spring service using only a synthetic fixture; it did not make a live Tracker collection or personal-match capture. Baseline evaluation and collector input fixtures are synthetic; they do not establish predictive coaching quality or support unavailable tactical fields. Full interactive review of real imported matches and source-data quality remains an owner acceptance item. No deployment or merge was performed.

### Phase boundary

M8 acceptance work is complete. Stop this implementation at M8; no later phase was started.

### Final cross-phase audit — 2026-10-28

M1–M8 implementation and verification evidence has been reviewed against the supplied master prompt. At this audit point M4's tactical-map overlay remained deferred pending current artwork and transform verification; the later M4 completion below closes that item. M6 still needs a permission-reviewed cohort with enough observed members/matches for useful reference output; M7's baseline is not evidence of predictive coaching quality. No live service or extraction state was changed.

### M4 follow-up — event-linked position inspection (2026-10-28)

Added a round-level event selector to the discrete position table. Selecting an event filters snapshots by exact `sourceRef`; a paired kill/death with the same reference appears as one selector option, preferring the credited kill row. Events with no linked positions remain selectable and show an explicit empty-evidence message. Changing match, round, or round filter clears the selected event. Coordinates and raw time units remain labeled as source values; this is not a map overlay or movement path.

Focused verification: `node --check` passed for the workspace and page loader; `node --test src/test/js/*.test.cjs` passed **54/54**; `git diff --check` passed. Added tests cover exact-reference filtering, empty results, all-round behavior, and kill/death selector deduplication. ChatGPT in-app browser inspected the synthetic-only page: the paired event appeared once, selecting it showed its two matching snapshots, and selecting a plant event with no positions showed the empty-evidence state. The inspected browser view was desktop; narrow-mobile visual inspection was not completed in this focused pass. No real account data was loaded.

The page and workspace cache tokens were bumped for the changed scripts/styles. This event-selector change did not close M4 at that point; the subsequent completion below adds map-version-matched artwork and source-transform checks.

### M4 completion — version-checked tactical map overlay (2026-10-28)

Added official Riot Public Content Catalog 13.08 tactical base-map assets for 13 standard maps plus a versioned manifest with map UUID, asset, image dimensions, SHA-256, valid-from date, and affine transform metadata. The catalog is published by Riot; its [VALORANT catalog notice](https://support-developer.riotgames.com/hc/en-us/articles/22698769097107-VALORANT) states that catalog assets are free to use. For the local five-response sample, map transforms were compared privately against the catalog; raw responses, player identifiers, and match identifiers were not added to the repository.

The Rounds tab now overlays only the selected round/event's discrete snapshots whose `TRACKER_WORLD_RAW` coordinates transform into the normalized map image bounds. A map is shown only when map UUID, match timestamp within the verified release window (2026-10-13 through 2026-10-26), and all four source transform coefficients match. This upper date boundary avoids applying 13.08 artwork to matches after the subsequent 13.09 patch until its map assets are verified. A mismatch, missing source field, unknown coordinate space, or missing asset produces an explicit unavailable state. Valid coordinates outside the image remain in the table as “Outside map extent” and are counted in the caption; they are never clipped or silently moved. No continuous paths are drawn. The existing raw-coordinate table remains authoritative and now reports map placement.

Focused verification: `node --check` passed for the workspace and page loader; `node --test src/test/js/*.test.cjs` passed (result recorded in [VERIFICATION.md](VERIFICATION.md)); `git diff --check` passed. New tests cover map identity/date/transform gating, boundaries and epsilon, outside points, unsupported coordinate space, and every bundled map asset's PNG signature, dimensions, and SHA-256. In the ChatGPT in-app browser, a synthetic desktop match showed one plotted snapshot and one out-of-bounds snapshot; the table and caption agreed. The same synthetic match was opened inside a 390 × 844 viewport; the first render exposed a map-width overflow, which was fixed and visually rechecked. This was a synthetic fixture; no real account match was loaded. A browser-console error from the host instrumentation (`MutationObserver` was given a non-Node) was present and could not be attributed to the app; it did not prevent the view from rendering.

The temporary fixture listener on port 8094 was used only for synthetic browser review and stopped afterward. The live application and extraction services on ports 8090/8091 were not restarted or contacted; live extraction remained enabled and archive extraction remained paused. No collection or admin endpoint was used.

This closes the M4 map-overlay implementation item. M6's comparison quality remains dependent on a permission-reviewed cohort with sufficient imported matches, and the M7 deterministic baseline still does not establish predictive coaching quality. GitHub Actions and a real authenticated imported-match map review were not run in this follow-up.

### M4 round-filter completion — 2026-10-28

The round filter now includes selected-player opening kills/deaths only when a unique earliest credited kill has a reported raw round time; recorded multi-kills; explicit plant, defuse, detonation and combined objective-event filters; and rounds with findings from the existing owner-scoped deterministic analysis endpoint. The review-finding report loads only when that filter is selected and is cached for the workspace session. Tied or untimed openings are not attributed. Eco/full-buy filters remain intentionally unavailable because the imported source does not establish buy categories; recorded loadout amounts are not silently classified. The page loader cache token was incremented so browsers fetch the changed workspace module.

Focused checks: `node --check src/main/resources/static/match-intelligence-workspace.js` passed; `node --test src/test/js/match-intelligence-workspace.test.cjs` passed **9/9**, including opening attribution, tied timestamps, multi-kill, objective and review-finding filters, complete equipment sums and missing-roster chart gaps. Full JS suite and final Git checks are recorded in [VERIFICATION.md](VERIFICATION.md). The page-loader and stylesheet cache tokens were incremented. No backend or persistence code changed, and no service or extraction state was touched.
