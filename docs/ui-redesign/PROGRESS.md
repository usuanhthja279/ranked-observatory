# Ranked Observatory 2.0 progress

Updated: 27 October 2026, Asia/Calcutta.

## Phase 0 — Complete: audit and execution plan

Repository: https://github.com/usuanhthja279/ranked-observatory
Branch: `codex/ranked-observatory`
Audited base: `c3faa2b3eaa255895632f23a54ec645303f291e8`

### Changes made

- Read the supplied master task, README, all review documents, current UI guide, responsive retest, historical implementation progress and repository-separation notes.
- Located all three user samples, rendered all 16 reference views and inspected representative screenshots plus the supplied full desktop/mobile previews.
- Inspected current frontend state/navigation, API/security/account contracts, archive query/source selection, cache invalidation, startup ownership, schemas and test prerequisites.
- Created `IMPLEMENTATION_PLAN.md`: architecture, route/component/API map, preservation checklist, demonstrated defects, bounded phase sequence, verification and rollback.
- Retained approved reference copies/hashes under `reference/` and actual browser/test evidence under `evidence/phase0/`.

Only documentation/reference/evidence changed. Application source, active/deferred migrations, local configuration, archive records/cursors, pause markers, collector settings and tunnel remain untouched. No deployment or service restart occurred.

### Existing functionality and runtime

The existing 8090 service responded and was actively collecting. The catalog exposed 35 archived Acts plus current. Current snapshots advanced during read-only checks. The running service and independent streaming tracker were not stopped or reconfigured.

Overview, APAC, India, global and archived APAC/India rendered in logged-out desktop/mobile browser contexts. This was read-only navigation, not complete interactive acceptance or authenticated live-account testing.

### Actual checks

1. `.\gradlew.bat test --console=plain` — successful, but test was UP-TO-DATE, so not counted as fresh execution.
2. `.\gradlew.bat test --rerun --console=plain` — fresh **PASS**: **39 discovered, 34 passed, 5 skipped, 0 failures/errors, 14 suites**. External test DB variables were cleared in this process. PostgreSQL/upstream opt-in cases were skipped; this is not native PostgreSQL integration verification.
3. Existing Spring/H2 tests covered independent dashboard/status, account registration/hash/login/logout, CSRF and ADMIN collection, alongside parsing, capture, pacing, observation and cache regressions. No live account was created.
4. Bundled Playwright/installed Edge: all **16 prototype views at 1440 × 900 and 390 × 844**, **32 render states**, no recorded document overflow/page exception. Ctrl+K → India search → Escape worked in both contexts. These are synthetic prototype records.
5. Existing live app: six routes at each size, **12 states**, final screenshots after core and historical charts rendered. All desktop documents fit 1440 px. At 390 px, overview/APAC/global/archive-APAC measured **433 px**; current/archive India measured **390 px**.
6. An initial harness wait on global timed out because that route does not set coreReadyMs even though player rows were populated. Final capture used player-row plus trend readiness; no app-load failure is inferred from the marker mismatch.
7. Read-only APIs and source confirmed V26 Act IV 7/7 source ladders, 600 pages; India/APAC 350 players with source I3/I2/I1 57/83/210; region aggregate only EU 13,237. This demonstrates both archived summary defects documented in the plan.
8. Actual representative screenshots were opened and inspected for hierarchy, labels, wrapping and controls. This is not a complete accessibility or performance audit.
9. Reference source/copy SHA-256 verification and evidence inventory checks — **PASS**. Staged whitespace/documentation-only scope and committed-reference SHA-256 checks — **PASS** (`git diff --cached --check` plus binary hash comparison).

No fresh PostgreSQL integration, production build, collection POST, migration, authenticated live-account journey or production code change was performed for Phase 0.

### Problems and limitations

- Historical request race/full-range bound, archive aggregation/tier semantics, cache revisions and process-local archive ownership remain implementation work.
- Before any second PostgreSQL-backed API runtime, provide a historical-worker disable gate; live-disabled configuration alone does not stop the archive initializer today.
- Some diagnostics retain the obsolete leaderboard-service path; fix each when its relevant phase uses it.
- Prototype auth, watchlist, settings and metrics are demonstrations. They do not prove real backend integration, account persistence or every required feature.
- The service exposes no source-commit identifier. Live observations and frontend source review are separately identified evidence, not proof of an exact deployed Java commit.
- Current counts/timestamps can differ because the existing collector remained active.

### Git and model handoff

Initial local main was `b2c51c9` with no origin and only user-provided `ui-sample/` untracked. The verified remote was configured and fetched; the requested target branch was checked out at `c3faa2b` with the same application tree. User sample originals remain untracked/unmodified.

The Phase 0 audit ran with GPT-6.1 Sol. The user later resumed Phase 1; no tool-based model switch is claimed.

## Phase 1A — Complete: trend request/range correctness

### Changes

- Kept the complete eligible Act catalog separate from the API's selected range. Chart/table/index calculations now use only the selected range.
- Added latest-request guards and abort controllers for series and player requests. A country trend's two dependent series reads share one immutable filter context.
- A current series failure clears stale chart/table/tier rows and reports the failure instead of repainting old results.
- Allowed the inclusive 36-Act window (35 archived Acts plus current) and retained rejection beyond that bound.
- Changed the chart-shape default to Standard; Smooth and Stepped remain available.

### Verification

- `.\gradlew.bat test --tests com.valorant.leaderboard.HistoricalTrendsQueryServiceTest --console=plain` — **PASS**; verifies 36 points accepted and 37 rejected.
- `node --check src/main/resources/static/act-trends.js` — **PASS**.
- Synthetic Edge browser check using mocked API data — **PASS**: Standard default; inclusive full-range chart/table; latest response wins after an older delayed response; failed current request clears old chart; no page errors; mobile document has no horizontal overflow at 390 × 844.
- This browser run used synthetic data and did not connect to PostgreSQL or the active collector.
- Screenshots: `evidence/phase1a-desktop.png`, `evidence/phase1a-mobile.png`.
- `git diff --check` — **PASS**.

No production service was started/restarted, and no collection, migration or live database operation was performed.

## Phase 1B — Complete: canonical archive summaries and tier semantics

### Changes

- Regional summaries and their leading player now come from the matching completed regional ladder (`source_region = player_region`). Global-source rows no longer substitute for missing regional totals.
- Archived country and region responses retain the legacy `immortal` field as the combined-only source label and add `immortal_all`, `combined_immortal`, `split_immortal`, and `tier_detail`.
- Responses now identify their `source_basis`, the RR `threshold_profile`, and the meaning/policy of estimated subtier fields. Regional summaries include their exact I2/I3 RR cutoffs.
- Archive tables label total, combined-only, and source-reported Immortal subtiers explicitly. The archived country page hides live-only movement/tier guidance and the unrelated country-estimation toggle.
- No migration or workflow/security change was needed.

### Verification

- `.\gradlew.bat test --tests com.valorant.leaderboard.HistoricalActArchiveSummaryTest --tests com.valorant.leaderboard.HistoricalTrendsQueryServiceTest --console=plain` — **PASS**. The in-memory H2 fixture distinguishes global rows from completed AP/EU regional sources; checks canonical AP player count, legacy/total/split tier values, leader, source basis, threshold profile and cutoffs; range tests cover 36 accepted/37 rejected.
- `.\gradlew.bat test --rerun --console=plain` — **PASS**: **42 discovered, 37 passed, 5 skipped, 0 failures/errors, 16 suites**. Optional external database tests were skipped because their database environment variables were cleared. This is not native PostgreSQL verification.
- `node --check src/main/resources/static/leaderboard.js` and `node --check src/main/resources/static/act-trends.js` — **PASS**.
- `git diff --check` — **PASS**.
- Synthetic Edge archive view with mocked regional/global fixtures — **PASS** for explicit columns, values, provenance and no page errors. Screenshots: `evidence/phase1b-archive-summary-desktop.png`, `evidence/phase1b-archive-countries-desktop.png`, and corresponding `*-mobile.png` files.
- At 390 × 844, archive summary/country tables remain inside their scroll containers. The whole legacy region page still overflows horizontally to about 433 px because Act Trend controls exceed the viewport; this is the existing D8 mobile-shell issue assigned to Phase 2, not introduced or concealed here.
- Browser screenshots used synthetic API responses. No live database was read or mutated.

No production service was started/restarted, and no collection, migration or live database operation was performed. Phase 1 is complete; proceed to Phase 2 only as a separate phase boundary.

## Remaining phases

- [x] 0 — Audit, reference review, baseline and plan.
- [x] 1 — Trend correctness and canonical archive summaries (1A/1B).
- [x] 2A — Shared shell, navigation, breadcrumbs and responsive legacy-route compatibility.
- [x] 2B — Global search, accessible dialog behavior and latest-request lifecycle helper.
- [ ] 3A/3B — World/regions and region detail.
- [ ] 4A/4B — Countries and global leaderboard.
- [ ] 5 — Full player profile and Radiant race.
- [ ] 6A/6B — Dedicated trends and archive explorer.
- [ ] 7A/7B — Comparisons and persistent watchlist.
- [ ] 8 — Account/settings, methodology and roadmap.
- [ ] 9A/9B — Publication/cache/policy and collector/resource reliability.
- [ ] 10 — Full integrated/visual release review.

A rendered illustrative reference does not complete a production implementation phase.

## Phase 2A — Complete: shared shell, navigation and responsive fit

### Changes

- Added a lightweight responsive shell around the existing legacy dashboard: desktop sidebar, mobile bottom navigation, skip-to-content link, contextual breadcrumbs and visible keyboard focus.
- Kept the existing `/leaderboard.html?view=...` routes and selected Act state. Navigation uses the existing query contract and has no new server route dependency.
- The Act Archive navigation action scrolls to and focuses the existing Act selector; archived Act context is exposed in the breadcrumb and active control state.
- Corrected the D8 mobile trend-control sizing without hiding document overflow. Wide data tables remain inside their existing scroll containers.
- Left data/domain workflows and all services unchanged.

### Verification

- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/act-trends.js` — **PASS** (unchanged module, included because shell navigation synchronizes with Act selection).
- `node --check src/main/resources/static/observatory-shell.js` — **PASS**.
- `git diff --check` — **PASS**.
- Synthetic Edge verification with mocked `/api/leaderboard/**` responses — **PASS**: legacy APAC/archive route, active nav and contextual breadcrumbs; Act selector focus from desktop and mobile Archive buttons; Act and region retained in navigation URL; browser Back/Forward restores the legacy query; skip link receives first keyboard focus; no page errors.
- On the archived APAC route, document width matched viewport at 320, 360, 390, 430, 600, 760, 768 and 1440 px. Wide summary tables stayed within their existing scrollers. The initial D8 433 px document overflow was not reproduced after the fix.
- Actual synthetic screenshots captured and inspected: `evidence/phase2a-desktop-region.png` (1440 px viewport) and `evidence/phase2a-mobile-region.png` (390 px viewport). The screenshots use mocked data; the trend charts have no populated series because the fixture is focused on shell, route and layout behavior.

No Gradle/backend test was run because Phase 2A changes are static frontend shell code only. No database, collector, active service, or live API was used or changed. Phase 2B remains next.

## Phase 2B — Complete: global accessible search and request lifecycle

### Changes

- Made player search available from every legacy view through the header Search players button and Ctrl+K/Meta+K shortcut.
- Moved search into a labelled native modal dialog with initial input focus, Escape/close controls, keyboard result navigation, live loading/empty/error messaging and focus restoration to the opener.
- Selecting a result closes search and opens the existing player detail dialog; closing player details returns focus to the search trigger. The global leaderboard row path returns focus to its originating row.
- Added a reusable latest-request helper that aborts superseded work and exposes a current-request guard. Search now cancels stale network requests as the query changes or the dialog closes, while retaining current-result checks.
- Preserved existing APIs, query contracts, result limits, player detail renderer and account/security flows.

### Verification

- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/observatory-lifecycle.js` — **PASS**.
- `node --test src/test/js/observatory-lifecycle.test.cjs` — **PASS**, 3 tests, 0 failures.
- `git diff --check` — **PASS**.
- Synthetic Edge test with mocked APIs — **PASS**: Ctrl+K opens search and focuses input; debounced latest-only search cancels a delayed `slow` response when `fast` is requested; arrow-down reaches the result; Enter opens existing player details; Escape closes details and restores trigger focus; no-result and HTTP 503 states are announced; Escape closes search and restores focus; search dialog fits 390 px; no page errors.
- Actual synthetic screenshots captured and inspected: `evidence/phase2b-search-desktop.png`, `evidence/phase2b-search-error-desktop.png`, and `evidence/phase2b-search-mobile.png`. The search fixtures and error responses are synthetic and do not represent live account/player data.

No Gradle/backend test was run because Phase 2B changes are static frontend behavior and a pure JavaScript helper. No database, collector, active service, or live API was used or changed. Phase 2 (2A and 2B) is complete; proceed to Phase 3A.

## Phase 3A — Region explorer and canonical overview routes

### Changes

- Added canonical `/overview`, `/regions`, and `/regions/{region}` entry points while retaining the existing leaderboard renderer and query contracts behind redirects.
- Added server-side region and Act validation for the new routes; malformed region and Act values return client errors.
- Made the overview consistently show all six regions. Regions without data in the current snapshot are explicitly marked unavailable, with no fabricated zero-valued statistics.
- Moved historical trend charts below the region explorer in a collapsed disclosure, and refreshes chart sizing when opened.
- Added focused route coverage and retained the responsive shell's canonical navigation links.

### Verification

- `.\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --console=plain` — **PASS**, 6 tests, 0 failures, 0 errors, 0 skipped. The focused Spring test uses its configured H2 test database; this is not a live PostgreSQL test.
- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/observatory-shell.js` — **PASS**.
- `node --check src/main/resources/static/observatory-lifecycle.js` — **PASS**.
- `node --test src/test/js/observatory-lifecycle.test.cjs` — **PASS**, 3 tests, 0 failures.
- `git diff --check` — **PASS**.
- Synthetic Edge verification with mocked APIs — **PASS**: six region cards; three fixture regions with data and three explicit unavailable cards; canonical region navigation redirects to the existing renderer; history starts collapsed and opens; no browser errors; desktop width stays 1440 px and mobile width stays 390 px.
- Captured and inspected synthetic screenshots: `evidence/phase3a-overview-desktop.png` (1440 × 900) and `evidence/phase3a-overview-mobile.png` (390 × 844). These show fixture data, not live region coverage. On mobile the fixed bottom navigation overlaps part of a card at the viewport edge while scrolling; the card carousel remains swipeable and its final card is reachable. No horizontal page overflow was observed.

### Local service boundary

- Before restart, the existing app on port 8090 had completed snapshot 399 (543 pages). A subsequent live extraction was allowed to finish before stopping the service; no active collection was interrupted.
- Packaged with `.\gradlew.bat bootJar --console=plain` — **PASS** using the installed Java 21 JDK.
- Restarted only the verified 8090 listener with `scripts/Stop-LeaderboardService.ps1 -Port 8090` followed by `scripts/Start-LeaderboardService.ps1 -Port 8090` — **PASS**. The standard script loaded the existing private database settings; no credential values were printed or changed.
- Post-restart `GET /api/leaderboard/status` — **HTTP 200**; app enabled on port 8090 and durable snapshot 399 remained available. The scheduled live collector resumed automatically after restart.
- Post-restart route checks — **PASS**: `/overview` and `/regions/ap` redirect to their established renderer URLs; invalid region returns 404; malformed Act returns 400; the renderer serves the new region-availability and collapsed-history hooks.
- Phase 3A is complete. Phase 3B remains next.
