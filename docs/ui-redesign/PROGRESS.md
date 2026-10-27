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
- [x] 6A — Dedicated trends.
- [ ] 6B — Archive explorer.
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

## Phase 3B — Region details (complete; data extraction paused for remaining redesign phases)

### Changes

- Added six keyboard-operable region tabs: Overview, Standings, Countries, Rank distribution, Movement and Historical trends.
- Reused the existing player standings, country/subregion table, movement panels and both within-Act and cross-Act trend systems; no new data endpoint or database change was needed.
- Added region overview cards from the existing regional summary, including top player/rank, observed player totals, and the boundary value only when the country summary provides it. The UI describes the boundary as observed context, not an official promotion requirement.
- Added a rank distribution view with observed tier counts and shares explicitly based on the listed tiers. Confirmed Radiant counts are labeled as confirmed.
- Archived Acts show an explicit movement-unavailable explanation instead of an empty/zero-like movement table.
- Preserved Act query state when changing tabs, made browser Back/Forward restore a selected tab, and kept subregion filtering available under Countries.
- Added a mobile hint for the horizontally scrollable tab strip; no document-level overflow was observed.
- User requested that data extraction remain stopped until all redesign phases are implemented. Added a persistent all-extraction hold that blocks the live scheduler, manual live collection, automatic live resume after archive completion, and the historical archive worker. Status responses expose `paused`; historical archive schedule reports `scheduled:false` while held.
- The startup script supports `-PauseAllExtractionUntilRedesignComplete`, which stores an ignored local marker under `build/`. The marker survives service restarts. `-ResumeLiveExtraction` removes both the all-extraction and live-only pause markers; use it only after the redesign phases are complete and the user asks to resume extraction.
- On startup, an abandoned live run is marked `FAILED` only when the PostgreSQL advisory lock confirms no collector still owns it. Its durable page checkpoint remains in `build/live-collection/`.

### Verification

- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/region-detail.js` — **PASS**.
- `node --test src/test/js/region-detail.test.cjs src/test/js/observatory-lifecycle.test.cjs` — **PASS**, 5 tests, 0 failures.
- `git diff --check` — **PASS**.
- `.\gradlew.bat bootJar --console=plain` — **PASS**, Java 21; the build completed successfully.
- `.\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --tests com.valorant.leaderboard.HistoricalActArchiveServiceTest bootJar --console=plain` — **PASS**, 8 focused tests (6 + 2), 0 failures, 0 errors, 0 skipped; packaged build successful.
- Synthetic Edge browser flow — **PASS** at 1440 × 900 and 390 × 844: all six tabs, Act query preservation, Asia-Pacific subregion selection, rank bars, movement/history panel visibility, keyboard navigation and no JavaScript errors. Document width remained 390 px on mobile. Synthetic fixture screenshots: `evidence/phase3b-region-overview-desktop.png` and `evidence/phase3b-region-mobile.png`.
- Real-backend Edge flow — **PASS**, read-only GET requests proxied to the local 8090 API while the updated source UI was served from the workspace. Asia-Pacific summary, confirmed Radiant count, observed boundary, player list, country list, movement and Act-trends panels loaded; no browser errors; desktop width was 1440 px and mobile width was 390 px. Live-data screenshots: `evidence/phase3b-region-live-desktop.png` and `evidence/phase3b-region-live-mobile.png`.
- The browser comment screenshot's bright blue outline is the comment-selection overlay. On the running app, Ctrl+K opened “Find a player,” focused the input, and the app's own focus treatment was teal (`rgb(131, 216, 208)`); no extra blue app outline was observed.
- Screenshot inspection found the mobile tab labels extend beyond the visible strip; this is now signaled by “Swipe to explore all six sections” and the final tab remains reachable. The fixed bottom navigation has the existing 88 px safe-area padding on the page.

### Local service boundary

- Snapshot 400 was the latest completed snapshot before the next live run. At the time the user requested a full pause, the active run was collecting LATAM page 49. The service was stopped, and its page-by-page checkpoint was retained at `build/live-collection/88a01c48-0e50-49cb-9e72-748987293947` (1,026 files). The interrupted run was marked `FAILED`; the last completed snapshot remains unchanged.
- Packaged and restarted the app on port 8090 with `scripts/Stop-LeaderboardService.ps1 -Port 8090` and `scripts/Start-LeaderboardService.ps1 -Port 8090 -PauseAllExtractionUntilRedesignComplete` — **PASS**. The app and `/leaderboard.html` returned HTTP 200.
- Post-restart `/api/leaderboard/status?compact=true` — **PASS**: `enabled:false`, `paused:true`, `running:false`, latest completed snapshot `400`, latest run `FAILED`.
- Historical Act status — **PASS**: `paused:true`, `running:false`, `schedule.scheduled:false`.
- Authenticated local admin `POST /api/leaderboard/collect` — **PASS**: HTTP 409 with `accepted:false`; the pause cannot be bypassed by manually requesting collection. Follow-up status remained paused and idle, with snapshot 400 still latest.
- Phase 3B is complete and the app remains available for UI work while all live and historical extraction stays paused. Keep the local pause marker for subsequent phase restarts. Do not resume extraction until all redesign phases are implemented.

## Phase 4A — Countries directory and country detail routes

### Changes

- Added canonical `/countries` and `/countries/{country}` routes while retaining the established leaderboard renderer and API contracts behind redirects. Region, country and Act route context is validated before redirecting.
- Added a Countries entry to desktop and mobile navigation. The active navigation state now stays on Countries when a country detail view is open.
- Added a searchable, region-filterable country directory with deterministic sorting and pagination. Regional links are generated only for ladders with observations, and the directory preserves Act context.
- Used the existing country and status read APIs; the UI labels observed entries and warns that regional views may include the same account. No population totals are implied.

### Verification

- `node --check src/main/resources/static/countries-directory.js` — **PASS**.
- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/observatory-shell.js` — **PASS**.
- `node --test src/test/js/countries-directory.test.cjs` — **PASS**, 4 tests, 0 failures.
- `git diff --check` — **PASS**.
- `.\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --console=plain bootJar` — **PASS**, 7 Java tests, 0 failures/errors/skips; `bootJar` completed. Gradle used the installed Java 21 JDK with a corrected process-only `JAVA_HOME`; the machine environment was not changed.
- ChatGPT in-app browser against the local app — **PASS**: opened `/countries`; searched for India; selected Asia-Pacific; verified the URL retained `region=ap&q=India` and results narrowed to India and British Indian Ocean Territory; cleared search, sorted by country name and advanced to page 2; opened India’s Asia-Pacific detail route; after restart, verified the canonical route resolves and Countries is the active desktop/mobile navigation item.
- Responsive DOM inspection — **PASS**: mobile detail document width 380 CSS px at a 390 CSS px viewport; wide tables stay within their own horizontal scrollers. Desktop document width was 1430 CSS px at a 1440 CSS px viewport; page-level overflow was not observed. Desktop trend-table content remains intentionally horizontally scrollable.
- Local service was rebuilt and restarted on port 8090 using the checked-in startup script. Post-restart status returned `enabled:false`, `paused:true`, `running:false`, and latest completed snapshot 400. The persistent all-extraction pause marker was retained; no collection request was sent.

### Visual evidence and limitations

- `evidence/phase4a-countries-desktop.png` — actual in-app browser capture, 1430 × 894.
- `evidence/phase4a-countries-mobile.png` — actual in-app browser capture, 380 × 822.
- `evidence/phase4a-country-detail-desktop.png` — actual in-app browser capture, 1010 × 894. The current ChatGPT browser panel constrained the captured image width even with a 1440 CSS viewport override; the page DOM measured 1430 px and no document-level overflow was found.
- `evidence/phase4a-country-detail-mobile.png` — actual in-app browser capture, 380 × 822.
- Screenshots were inspected. The country detail's wide summary and movement tables expose their own horizontal scrolling; no page-level horizontal overflow was detected. The browser panel's capture width is a visual-evidence limitation for the country-detail desktop image.
- Data in these captures comes from the local persisted snapshot (snapshot 400), not synthetic fixtures. New extraction remained paused throughout verification.

Phase 4A is implemented and focused verification passed. Proceed to Phase 4B; keep extraction paused.

## Phase 4B — Global leaderboard and mobile player preview

### Changes

- Added canonical `/leaderboard`, with validated search, page, Act, sort and direction parameters. Existing `/leaderboard.html?view=global` remains supported.
- Added URL-backed global search, paging, sorting and browser Back/Forward restoration. Search and page totals are sourced from the full current or archived player set.
- Added safe server-side sort choices for global rank, regional rank, player name, country, tier, rating, rating change and observation time. Sort SQL expressions are selected from an allowlist; invalid columns/directions return HTTP 400. Existing callers retain the default global-rank ordering.
- Added compact mobile player cards with ranking, identity, country, rating and an expandable details area. The existing player-history view opens from each card. Desktop retains the established table.
- Hid region-only overview panels while the global leaderboard is active. Preserved Act context in the canonical route and API requests.

### Verification

- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/observatory-shell.js` — **PASS**.
- `node --test src/test/js/countries-directory.test.cjs` — **PASS**, 4 tests, 0 failures.
- `.\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --console=plain bootJar` — **PASS**, 9 Java tests, 0 failures/errors/skips; packaged build successful using Java 21. The new route test covers canonical query preservation and bad route inputs; the sort test rejects unsupported SQL sort/direction values. No Docker was used.
- `git diff --check` — **PASS**.
- ChatGPT in-app browser using the local app and PostgreSQL snapshot 400 — **PASS**: global leaderboard showed 54,429 players; searching `Jaggi` returned one match and updated the URL; page 2 displayed rows 11–20 and browser Back restored page 1; country sort began with Andorra and then United Arab Emirates; rating and rating-change sorts returned data; clicking “View player history” opened the existing history dialog and loaded source observations. No browser console errors were recorded.
- Responsive checks — **PASS**: at 390 × 844 CSS viewport, the document measured 380 px wide (the in-app browser content area) with no horizontal overflow; mobile cards expand and remain scrollable, and the player-history dialog is usable with its own vertical scrolling. Desktop DOM measured 1,430 px wide at a 1,440 px CSS viewport.
- Local application was rebuilt and restarted on port 8090. After startup completed, API health/status returned **HTTP 200** with `enabled:false`, `paused:true`, `running:false`, and latest complete snapshot 400. No collection request was sent.

### Visual evidence and limitations

- `evidence/phase4b-global-desktop.png` — actual ChatGPT in-app browser capture, 1,010 × 894. The CSS viewport was set to 1,440 × 900 and the document measured 1,430 px wide; the narrower browser-panel capture clips the far-right portion of the wide desktop table.
- `evidence/phase4b-global-mobile.png` — actual in-app browser capture, 380 × 822, showing the responsive global leaderboard.
- `evidence/phase4b-global-sort-mobile.png` — actual in-app browser capture, 380 × 822, showing the sort selector and sorted player cards.
- `evidence/phase4b-global-mobile-history.png` — actual in-app browser capture, 390 × 843, of the player-history dialog on mobile.
- Screenshots were inspected. The global screen has no page-level horizontal overflow; the fixed bottom navigation overlays the viewport edge while the page scrolls, and lower player cards remain reachable by scrolling. The desktop screenshot width is constrained by the built-in browser panel, not by the page's CSS layout.

Phase 4B is implemented and focused verification passed. Continue to Phase 5. Keep all data extraction paused until the redesign phases are complete.

## Phase 5 — Player profile and ranked journey

### Changes

- Added the canonical `/players?player=…&region=…` profile route. Global leaderboard rows and player-search results open this dedicated page while preserving Riot ID tags, region, and selected Act.
- Reused the existing player history, snapshot summary, Radiant race, overtake target, cross-Act journey, and Tracker profile APIs and renderers. The profile adds source-observation timestamps, a selectable history window, and an expandable observation table without changing API contracts or snapshot meaning.
- Archived Act profiles show the saved snapshot and account journey, explain that no within-Act race exists, and omit current Tracker data. Existing history selection outside the global page remains in place.
- Corrected profile-only panels inheriting display styles on the global leaderboard. Corrected the archive profile renderer so a missing live race response does not raise a null-data error.

### Verification

- `node --check src/main/resources/static/leaderboard.js`, `observatory-shell.js`, and `overtake-targets.js` — **PASS**.
- `node --test src/test/js/countries-directory.test.cjs` — **PASS**, 4 tests, 0 failures.
- `.\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --console=plain bootJar` — **PASS**, 7 Java tests, 0 failures/errors/skips; packaged build successful with installed Java 21. One initial attempt without a process Java setting failed immediately; the rerun used the installed JDK for that process only.
- `git diff --check` — **PASS**.
- ChatGPT in-app browser against local snapshot 400 — **PASS**: opened a player from the real global ranking; confirmed profile route, display identity, source timestamps, published snapshot, Radiant race and gap, Tracker rating, source-history chart, and 23-act journey. Changed the history window to 15 minutes and confirmed the view updated. Returned to the leaderboard and verified profile panels are hidden there. Opened an archived Act profile and confirmed the archived rating/journey and unavailable-race explanation; no JavaScript console errors remained after the archive null-render fix.
- Responsive DOM — **PASS**: at a 1,440 × 900 CSS viewport, document width was 1,430 px; at 390 × 844, document width was 380 px. Mobile profile cards fit the content width and remain scrollable. Extraction status before and after app restart: `enabled:false`, `paused:true`, `running:false`, snapshot 400.

### Visual evidence and limitations

- `evidence/phase5-player-profile-desktop.png` — actual in-app browser capture at a 1,440 × 900 CSS viewport. The embedded browser capture itself is 1,010 px wide and clips the far-right portion of the desktop layout; DOM measurements show no page-level overflow.
- `evidence/phase5-player-profile-mobile.png` — actual profile top view at a 390 × 844 CSS viewport (380 × 822 capture content).
- `evidence/phase5-player-profile-mobile-details.png` — actual mobile capture showing snapshot details, overtake-target explanation, and source observations control.
- `evidence/phase5-player-profile-archive.png` — actual archived Act profile capture showing archived snapshot mode.
- Captures use persisted local data, not synthetic fixtures. Tracker profile values are a separate cached external source and carry their own fetched timestamp; leaderboard and history remain anchored to local completed snapshots. The desktop browser panel constrained screenshot width; the mobile fixed navigation overlays the viewport bottom, while remaining page content is scrollable.

Phase 5 is implemented and focused verification passed. Continue to Phase 6A. Keep all data extraction paused until the redesign phases are complete.

## Phase 6A — Complete: dedicated historical trends

### Changes

- Added the canonical `/trends` route and a dedicated trends page while preserving the existing legacy query route.
- Added historical-act comparisons for region/country, cohort and tier, including observed/estimated counts, regional/scope shares, index comparisons, chart shape selection and CSV export.
- Excluded empty series and clarified index cases with no non-zero first-act baseline; table peaks remain empty instead of rendering negative infinity.
- Kept filter selectors usable on desktop and mobile. The metric selector now has sufficient desktop width; mobile filter groups use the full panel width and the metric selector uses a dedicated full-width row.

### Verification

- `node --check src/main/resources/static/act-trends.js` — **PASS**.
- `node --check src/main/resources/static/act-trends-export.js` — **PASS**.
- `node --test src/test/js/act-trends-export.test.cjs src/test/js/countries-directory.test.cjs` — **PASS**, 6 tests, 0 failures.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --console=plain bootJar` — **PASS**, 11 tests, 0 failures/errors/skips; packaged jar successful.
- Final CSS-only rebuild `.\gradlew.bat bootJar --console=plain` — **PASS**. The route test was not rerun after this CSS-only adjustment; it had passed immediately before it.
- `git diff --check` — **PASS** (Git reports only the existing LF-to-CRLF working-copy conversion warning for `act-trends.js`).
- ChatGPT in-app browser with local PostgreSQL snapshot 400 — **PASS**: `/trends` rendered 35 historical Acts and six region series; chart totals and summary table matched. At 1440 × 900, the metric label fits. At 390 × 844, the filter groups span the panel and the page has no horizontal overflow (380 px document width in the built-in browser content area).
- Baseline edge case — **PASS**: Asia-Pacific, Top 500, Immortal 2, index displayed the explanatory no-baseline message; browser page text contained no `Infinity`/`-∞` artifacts and the chart legend was empty.
- Status before and after the final restart — **PASS**: `enabled:false`, `paused:true`, `running:false`, latest completed snapshot 400. No extraction request was sent.

### Visual evidence and limitations

- `evidence/phase6a-trends-desktop.png` — actual local application screenshot at a 1440 × 900 CSS viewport.
- `evidence/phase6a-trends-mobile.png` — actual local application screenshot at a 390 × 844 CSS viewport; the in-app browser's capture content is 380 px wide.
- Screenshots were inspected. The responsive table retains an internal horizontal scroller for its full set of columns. The browser download event did not complete during the earlier UI attempt, so CSV file delivery remains unverified in the browser; the CSV generation tests pass.

Phase 6A is complete. Continue to Phase 6B (archive explorer); keep all data extraction paused until every redesign phase is complete.
## Phase 6B — Archive explorer

### Changes

- Added canonical `/archive` navigation and route handling while preserving the existing archive query view.
- Replaced the archive action buttons with an Archive Explorer that provides Summary, Countries, Players, and Source coverage sections.
- Summary reports source rows, source-ladder completion, fetched pages, regional source-reported rows, and the saved publication timestamp. It distinguishes a sum of regional source rows from unique players and explains snapshot limitations.
- Countries can be compared against global or regional source rows. Players supports region/country filtering, search, pagination, archived source ranks, and links to the existing player profile. Source coverage shows each ladder's saved state, page count, row count, and timestamps without exposing backend error details.
- Kept the explorer read-only. It uses existing archive APIs; no extraction or database mutation endpoint is invoked.
- Corrected archived player ordering SQL to interpolate only an allowlisted order expression. The prior query passed the literal `+order+` text to PostgreSQL; the focused archive query regression now exercises that path.
- Added canonical route tests for query preservation and malformed Act values, plus archive player ordering/pagination coverage.

### Verification

- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/observatory-shell.js` — **PASS**.
- `node --test src/test/js/act-trends-export.test.cjs src/test/js/countries-directory.test.cjs` — **PASS**, 6 tests, 0 failures.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest --tests com.valorant.leaderboard.HistoricalActArchiveSummaryTest --console=plain bootJar` — **PASS**, 14 focused Java tests, 0 failures/errors/skips; packaged jar successful.
- `git diff --check` — **PASS** (Git reports LF-to-CRLF working-copy conversion warnings for modified text files).
- ChatGPT in-app browser against the local PostgreSQL snapshot — **PASS**: `/archive` rendered Summary, Countries, Players, and Source coverage. Players showed 53,775 archived rows with 25 rows on page 1 and functional page controls; the Source coverage table showed all 7 source ladders complete. Country/source labels and source-rank labels were visible. Browser console error list was empty.
- Live status endpoint checked in the ChatGPT in-app browser — **PASS for pause invariant**: `enabled:false`, `paused:true`, `running:false`; latest published snapshot remains 400. Status history also contains run 401 as **FAILED**, so no successful snapshot newer than 400 is claimed. No collection request was issued in this phase.
- Application was rebuilt and restarted on port 8090 after the archive SQL correction; the browser loaded the updated route afterward.

### Visual evidence and limitations

- The actual in-app browser Summary and Source coverage captures were visually inspected during this turn. The browser's capture is displayed in the task transcript; a PNG copy was not saved under `docs/ui-redesign/evidence/` because the in-app browser capture interface did not expose a local file-save operation in this session.
- The current ChatGPT in-app browser does not expose a viewport-resize method, so a 390 × 844 mobile capture was not produced in this pass. Mobile styles exist, but mobile visual acceptance remains unverified for this phase.
- The inspected browser data is the persisted local snapshot, not synthetic data. Source status rows are recorded source evidence, not a claim that the currently disabled collector is running.

Phase 6B implementation and focused checks are complete; mobile visual evidence remains a known verification gap. Continue to Phase 7 only after capturing the remaining mobile evidence if the in-app browser supports a mobile viewport; keep extraction paused until the redesign phases are complete.

## Phase 7A — Evidence-based comparison

### Changes

- Added a canonical `/compare` page with Countries, Regions, and Players modes. The current Act is the default; Act selection is retained in the URL and each comparison states the source basis.
- Country comparisons use the global source and expose 1-hour, 6-hour, and 24-hour change windows for the current Act. Archived country comparisons show the selected archived snapshot and explicitly state that an archived Act has no within-Act time series.
- Region comparisons use the same selected Act and disclose whether counts come from the current global-source partition or completed archived regional ladders. The UI directs users to published Act trends when regional time-series evidence is unavailable.
- Player comparisons require exact Riot ID matches; RR/rank change summaries use bounded windows and avoid claiming identity continuity across renamed accounts.
- Country history now includes additive carried-forward and ambiguous-row counts. The comparison analysis excludes any aggregate snapshot containing either kind of uncertainty rather than presenting it as clean evidence.
- Added the comparison entry to desktop and mobile navigation. No extraction or collection endpoint is invoked.

### Verification

- `node --check src/main/resources/static/leaderboard.js` — **PASS**.
- `node --check src/main/resources/static/compare-page.js` — **PASS**.
- `node --check src/main/resources/static/compare-analysis.js` — **PASS**.
- `node --test src/test/js/compare-analysis.test.cjs` — **PASS**, 5 tests, 0 failures.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest.canonicalCompareRouteValidatesModeAndPreservesSelections` — **PASS**.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat bootJar` — **PASS**.
- `git diff --check` — **PASS** (LF-to-CRLF working-copy warnings only).
- ChatGPT in-app browser on the local service — **PASS** for rendering current-Act Countries, Regions, and Players modes; confirmed source/mode selectors and country comparison results. An archived V26 Act III country comparison rendered the saved counts and an explicit “single published source snapshot” no-history explanation.
- Restarted through `scripts/Stop-LeaderboardService.ps1 -Port 8090` and `scripts/Start-LeaderboardService.ps1 -Port 8090`. The app is running on port 8090. Status endpoint reports `enabled:false`, `paused:true`, `running:false`; latest successful snapshot remains 400. The existing extraction pause marker is present. No collection request was issued.

### Visual limits

The ChatGPT in-app browser was used for the actual local UI check. This session's in-app browser exposed the rendered accessibility tree but not a screenshot-save or viewport-resize operation, so no PNG evidence or 390 × 844 mobile capture is claimed. Responsive CSS is present but mobile visual fit remains unverified.

Phase 7A is complete. Continue to Phase 7B (persistent watchlist); keep all extraction paused.

## Phase 7B — Persistent account watchlist

### Changes

- Added Flyway V8 `observatory_watchlist` with account ownership, type/key/scope uniqueness, creation order, and cascading cleanup when an account is removed.
- Added authenticated list, idempotent save/update, and owner-scoped delete endpoints for PLAYER, COUNTRY, REGION and ACT items. Ownership is derived from the authenticated account username; callers cannot submit an owner ID. Non-account administrator identities cannot use customer watchlists.
- Kept CSRF enforcement for writes and return not-found for an item owned by another account.
- Added the `/watchlist` page to desktop and mobile navigation. It explains the four supported identifier forms, shows saved links and provides remove controls. Anonymous visitors are prompted to sign in; admin-only accounts are told that customer accounts own watchlists.
- Uses existing page routes for reopening each saved item. The view does not invoke any collection endpoint.

### Verification

- `node --check src/main/resources/static/watchlist-page.js` — **PASS**.
- `node --test src/test/js/compare-analysis.test.cjs` — **PASS**, 5 tests, 0 failures.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest.watchlist* --tests com.valorant.leaderboard.StandaloneApplicationTest.canonicalCompareRouteValidatesModeAndPreservesSelections bootJar` — **PASS**. Watchlist tests cover anonymous denial, CSRF, validation, all four item types, idempotent duplicate save, database-backed listing, two-account isolation, cross-account delete denial, owner delete and canonical page route.
- `git diff --check` — **PASS** (working-copy line-ending warnings only).
- Restarted the packaged service on port 8090. PostgreSQL 17.11 validated nine migrations and successfully applied **V8**. Status endpoint reports `enabled:false`, `paused:true`, `running:false`, latest successful snapshot 400.
- ChatGPT in-app browser — **PASS** for the `/watchlist` route and signed-out view; desktop navigation includes Watchlist and the page directs anonymous users to sign in. Authenticated UI form submission was not exercised in the browser; persistence and owner isolation were verified through the focused Spring integration test.

### Remaining visual limits

The in-app browser was used for the page check, but this session still exposes no screenshot export or viewport resizing. No PNG or mobile visual acceptance is claimed here; responsive CSS is included. Full desktop/mobile evidence remains part of the integrated Phase 10 review.

Phase 7B is complete. Continue to Phase 8; keep all data extraction paused.

## Phase 8 — Account experience, methodology and roadmap

### Changes

- Added account-scoped compact-table and reduced-motion preferences, saved through a CSRF-protected endpoint and applied to the shared UI. Preferences are isolated by authenticated account and persist across page loads.
- Added `/settings`; signed-out visitors receive a sign-in prompt. Signed-in customers can update the two real preferences. The account header links to Settings.
- Sign-in and signup preserve a validated same-origin return path for supported Observatory pages. External URLs and unsupported destinations fall back to the leaderboard.
- Added `/methodology` to explain source rows, archive limitations, rank/tier semantics, uncertainty and estimates in plain language.
- Added `/roadmap` with the accepted sequence and no target dates. Linked both pages from the main footer.
- Added additive Flyway V9 for account-owned UI preferences; no collector or ranking calculation was changed.

### Verification

- `node --check src/main/resources/static/account.js`, `settings-page.js`, `leaderboard.js`, and `observatory-shell.js` — **PASS**.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.StandaloneApplicationTest.displayPreferencesPersistPrivatelyAndRequireCsrf --tests com.valorant.leaderboard.StandaloneApplicationTest.settingsMethodologyAndRoadmapPagesHaveCanonicalTargets --tests com.valorant.leaderboard.StandaloneApplicationTest.createsHashedIndependentAccountsAndSignsInWithoutGrantingAdminPrivileges bootJar` — **PASS**. Coverage includes preference defaults/persistence, second-account isolation, CSRF, malformed requests, account registration/login security and page forwarding.
- `git diff --check` — **PASS** (line-ending warnings only).
- PostgreSQL 17.11 validated 10 migrations and applied **V9** on restart.
- ChatGPT in-app browser — **PASS**: Settings route and signed-out sign-in guidance rendered after correction; Methodology and Roadmap routes rendered with their explanatory content.
- Post-restart status — **PASS**: `enabled:false`, `paused:true`, `running:false`, latest successful snapshot 400. The app remains running on port 8090; no collection request was issued.

### Limits

The signed-in preference form is covered by Spring integration tests but was not exercised through an authenticated browser session. The in-app browser has no screenshot export or viewport resize in this session, so no PNG/mobile visual acceptance is claimed; those remain for Phase 10.

Phase 8 is complete. Continue to Phase 9A; keep all extraction paused.

## Phase 9A — Publication revisions and explicit rank policy

### Changes

- Added additive Flyway V10 with per-scope current/archive publication revision counters. PostgreSQL statement triggers increment the relevant revision when published leaderboard or archive tables change, including corrections that leave archive page totals unchanged.
- Dashboard cache keys now include both publication revisions and the latest complete current snapshot. A revision change clears stale dashboard reads; the policy capability endpoint now uses the same revision-aware key generator.
- Exposed current and archive revision numbers in the cache-status endpoint.
- Added a persisted, constrained `threshold_profile` to archived Act metadata. Runtime tier estimates now use this explicit policy field; the historical boundary is backfilled and seeded as Episode 3 backcast for Act order 31 and V26 Act II onward for order 32+. Existing rank thresholds are preserved.
- Left extraction paused; no collection run was requested or started.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.DashboardCacheRevisionTest --tests com.valorant.leaderboard.HistoricalRankPolicyTest --tests com.valorant.leaderboard.HistoricalActArchiveSummaryTest --tests com.valorant.leaderboard.HistoricalTrendsQueryServiceTest --tests com.valorant.leaderboard.HistoricalActArchiveServiceTest bootJar` — **PASS**.
- `git diff --check` — **PASS** (Git reports expected LF-to-CRLF conversion warnings on edited Java files).
- Native PostgreSQL 17.11 — **PASS**: V10 validated and applied; subsequent startup reports schema v10 up to date.
- ChatGPT in-app browser — **PASS**: archive summaries, country comparison and six-region trend charts rendered. The live `/api/leaderboard/trends/catalog` response showed order 31 with `EPISODE_3_BACKCAST`, order 32 with `V26_ACT_II_PLUS`, and the current Act with `V26_ACT_II_PLUS`.
- Cache status — **PASS**: current publication revision 2, archive revision 636; revision-aware cache enabled.
- Post-restart status — **PASS**: `enabled:false`, `paused:true`, `running:false`, latest complete snapshot 400.

### Limits

- Cache invalidation for same-page corrections is covered by the focused H2 regression using unchanged archive page totals and changed publication revisions. The running local UI was read-only; no live archive rows were modified to simulate a correction.
- No extraction was resumed. Historical policy is explicit and versioned in persisted Act metadata; introducing a new policy family will require adding it to the constrained policy set and assigning it deliberately.

Phase 9A is complete. Continue to Phase 9B; keep all extraction paused.

## Phase 9B — Worker ownership, profile request bounds and measured reads

### Changes

- Added a PostgreSQL session advisory lock for the historical archive worker. It is acquired before catalog seeding or stale `RUNNING` recovery; instances without ownership skip archive writes/scheduling. Connection loss releases ownership automatically. Shutdown stops workers and waits before releasing the lock.
- Archive status reports whether this instance owns the worker and reports scheduling as paused when it does not.
- Added same-player profile single-flight on top of the existing two-request semaphore and bounded provider timeout. Concurrent callers share one provider request; follower waits are capped at 35 seconds and then return an unavailable/busy result without affecting other project statistics.
- Added an isolated PostgreSQL test database: `ranked_observatory_redesign_test`. No live archive tables were used for worker contention/recovery checks.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.PostgresArchiveWorkerLockIntegrationTest --tests com.valorant.leaderboard.TrackerProfilesSingleFlightTest --tests com.valorant.leaderboard.HistoricalActArchiveServiceTest --tests com.valorant.leaderboard.HistoricalActArchiveSummaryTest --tests com.valorant.leaderboard.HistoricalTrendsQueryServiceTest --tests com.valorant.leaderboard.DashboardCacheRevisionTest --tests com.valorant.leaderboard.HistoricalRankPolicyTest bootJar` — **PASS**, including PostgreSQL two-connection lock contention and lock recovery after connection close, profile single-flight, archive, cache, rank policy and trends regressions.
- `git diff --check` — **PASS** (line-ending conversion warnings only).
- Packaged app restart on port 8090 — **PASS**. Flyway reports V10 current; archive status reports `workerOwner:true`, `paused:true`, `running:false`, `scheduled:false`.
- Data extraction status — **PASS**: live collector is `enabled:false`, `paused:true`, `running:false`; latest successful snapshot is still 400. No source calls were made.
- ChatGPT in-app browser — **PASS** after final restart: archive summary and Act trend chart/table loaded with saved source data.
- Local read-only API timing samples (three per endpoint; cold/warm effects included): `/api/leaderboard/acts` 22.3/37.2/25.5 ms (median 25.5 ms); `/api/leaderboard/trends/capabilities` 15,593.2/13.6/12.2 ms (median 13.6 ms after cache warm); bounded APAC top-100 trend series 73.6/18.9/19.7 ms (median 19.7 ms). The first capability calculation is a visible cold-start cost; subsequent requests are served quickly from the revision-aware cache.

### Limits

- PostgreSQL lock integration covers the shared advisory-lock primitive with two independent connections, and the actual application reports ownership while paused. It does not start a second Spring process; that would exercise more startup paths than needed for the lock test.
- The capability endpoint has a 15.6-second cold calculation over the existing archive and is 12–14 ms warm. Further query/precompute optimization can be considered separately if cold first-view latency is unacceptable.

Phase 9B is complete. Continue to Phase 10; keep all extraction paused.
