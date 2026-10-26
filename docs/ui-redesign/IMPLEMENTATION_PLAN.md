# Ranked Observatory 2.0 implementation plan

Audit completed: 27 October 2026, Asia/Calcutta. Tests began on 26 October; browser inspection continued after midnight.

## Scope and starting state

Repository: https://github.com/usuanhthja279/ranked-observatory
Branch: `codex/ranked-observatory`
Verified remote base: `c3faa2b3eaa255895632f23a54ec645303f291e8`

The initial local `main` at `b2c51c9` had no remote and only `ui-sample/` untracked. Origin was verified/configured, fetched, and the requested branch checked out. Its application tree matches the initial source; sample originals remain untouched.

This pass is Phase 0 documentation/evidence only. The user chose to select GPT-6 Luna with Medium reasoning before implementation. The audit ran with GPT-6.1 Sol; no model switch is claimed. After model selection, implement ordered phases with tests, evidence, progress update and a commit at each boundary.

## 1. Current architecture and operational constraints

Java 21, Spring Boot 3.5.6, MVC, JDBC, PostgreSQL, active Flyway V1–V7, Spring Security and Caffeine. Static HTML/global JavaScript and canvas charts serve the frontend. A framework rewrite is unnecessary.

The existing loopback service on 8090 was actively collecting and published another live snapshot during inspection. Its catalog currently exposes 35 historical Acts plus current. Those are audit observations, not fixed test totals. Do not restart it, start a second writer against its database, change checkpoints/pause markers, invoke collection POSTs, touch the streaming tracker or replace the submitted tunnel.

Source owners:

- `LeaderboardController`: scope validation and current/archive dispatch.
- `GlobalLeaderboardService`: live collection, completed snapshots and current read models.
- `HistoricalActArchiveService`: resumable sources/pages and archive queries.
- `HistoricalTrendsQueryService`: eligible Act catalog, series, archived players and journey.
- `DashboardCache`: completed live snapshot key plus incomplete archive-page revision.
- `CountryActivity`, `BoundaryMovement`, `PlayerTargets`, `LeaderboardConfidence`: activity, inferred boundary changes, nearby targets, race and freshness.
- `TrackerProfiles`: public enrichment/cache; reads can trigger upstream requests.
- `ObservatoryAccounts`/`LeaderboardSecurity`: BCrypt, independent sessions, CSRF and ADMIN collection.
- `leaderboard.html`/`leaderboard.js`: large shared DOM/CSS and view state.
- `act-trends.js`, `country-model.js`, `server-access.js`, `confidence-race.js`, `movement-boundary.js`, `overtake-targets.js` and `country-scenery.js`: reusable domain/presentation modules.

`src/deferred-migrations/V8__historical_trend_foundation.sql` is outside active Flyway locations; its foundation service is disabled unless explicitly enabled. Do not activate it incidentally. Allocate future migration numbers at implementation time and reconcile the deferred filename before later activation.

## 2. Approved reference and improvements

All three supplied files are copied under `reference/` with byte counts/SHA-256 in `manifest.json`: the 95,666-byte interactive HTML and desktop/mobile PNG previews.

All 16 prototype views rendered in Edge at 1440 × 900 and 390 × 844. No document overflow/page exception was recorded in those 32 states. Ctrl+K → India search → Escape worked at both sizes. Representative images were opened alongside the supplied previews and existing/current-app screenshots. This is not full functional/accessibility certification.

Adopt midnight/navy layers; coral actions; teal observed values; gold Radiant/estimates; desktop sidebar; contextual header; mobile bottom navigation; compact cards; dedicated player/history pages; accessible evidence beside metrics.

Adapt these limitations:

- The HTML contains illustrative player/count/chart data, guest account state, local saves and demonstration authentication. Never ship these as real facts or durable account behavior.
- Preserve existing rank emblems, scenery/credits and SVG flags. Emoji flags rendered as country letters on Windows.
- Explicitly verify typography/fallbacks: actual HTML typography differs from the supplied preview PNGs.
- The prototype does not implement all required region/player tabs or region comparison; the full task governs those features.
- Mobile ranking tables are contained but hide columns. Provide compact player rows/expansion.
- Reduce hero height so useful metrics appear early; move heavy historical controls away from the overview.
- Search open/close is not proof of arrow-key navigation, focus trapping or accessible tabs. Implement/test these.
- Standard is the analytical chart default; Smooth/Stepped are optional. Preserve missing samples and nonnegative smooth geometry.

## 3. Target routes, compatibility and page mapping

Implement a shared lightweight shell and independent view modules, using these proposed paths:

| View | Proposed route | Existing source/API reuse | Phase |
| --- | --- | --- | --- |
| World overview | /overview | status, regions, confidence, activity-overview, search | 3 |
| Regions explorer | /regions | regions, leaders, scenery | 3 |
| Region detail | /regions/ap | countries, players, activity/movement, tiers, historical series | 3 |
| Countries directory | /countries | countries, region scope, flags | 4 |
| Country detail | /countries/in?region=ap | country-page, activity, players/history | 4 |
| Global leaderboard | /leaderboard | players, scopes, Act, filter/sort/paging | 4 |
| Player profile | /players?player=encodedRiotId&region=ap | summary, history, journey, race, targets, profile | 5 |
| Act trends | /trends | trends/catalog, capabilities, series, players | 6 |
| Archive explorer | /archive?act=UUID | acts, archived status/standings/confidence | 6 |
| Compare | /compare?mode=country | same-basis reads; bounded new queries where needed | 7 |
| Watchlist | /watchlist | new session-owned persistence | 7 |
| Data quality | /methodology | confidence, capabilities, policy/source disclosures | 8 |
| Settings | /settings | account/me; supported persistent preferences | 8 |
| Sign in | /signin.html | existing login, CSRF and account.js | 8 |
| Sign up | /signup.html | register API/validation | 8 |
| Feature roadmap | /features | truthful delivery states | 8 |

These are future route contracts, not currently implemented endpoints. Preserve `/leaderboard.html?view=world|region|country|global`, existing `act/region/country` queries, auth pages, diagnostics and Riot verification URLs. Add tested compatibility resolution as each replacement becomes ready, retaining the legacy view until then.

Keep backend region keys `ap, eu, na, kr, br, latam`; translate prototype `apac`. Encode Riot IDs with URLSearchParams so `#` is not a fragment. Store relevant Act range/cohort/tier/country/page state in URLs; test reload and Back/Forward. Do not create dead navigation links.

## 4. Shared components and frontend structure

Incrementally introduce browser modules:

- Core: API/error client, abort/latest-request helper, URL/router state, formatting, view cleanup and provenance adapter.
- Shell: desktop/sidebar and bottom/More navigation, breadcrumbs/title, contextual selectors, global search, account state.
- Primitives: metric/freshness/quality cards, rank/flag rendering, chips, accessible tabs/dialogs/drawers, confirmation/toast, loading/empty/error.
- Data: sort/paging controls, compact mobile player rows and exports with source context.
- Charts: sizing, legends, keyboard/text/table alternatives, hover/touch tooltip, Standard/Smooth/Stepped and explicit gap segmentation.
- Views: one module per major page; lazily load costly history/player content and dispose requests/listeners/timers on exit.

Reuse original globals behind narrow adapters during migration. Avoid loading a hidden full legacy dashboard on every new route. Scope new styles during coexistence. Do not delete working domain rules simply to simplify markup.

## 5. Existing APIs and controlled additions

Paths are under `/api/leaderboard` unless otherwise stated.

| Group | Existing endpoints | Important behavior |
| --- | --- | --- |
| Context | GET /acts, /status, /csrf | status uncached; catalog presence alone is not coverage |
| Historical | GET /trends/catalog, /trends/capabilities, /trends/series, /trends/players, /player-journey | completed regional sources; reported/reconstructed tiers differ |
| Standings | GET /regions, /countries, /country-page, /players | optional Act chooses archive; country-page bundles scoped reads |
| Current observations | GET /insights, /trends, /history, /country-history | archived Acts lack within-Act movement time series |
| Player | GET /player-summary, /profile, /overtake-targets, /boundary-signals, /radiant-race | profile and snapshot clocks differ; profile can trigger upstream work |
| Quality/activity | GET /confidence, /cache-status, /activity-overview, /country-activity, /movement-boundary-signals | inference is not exact matches/confirmed demotion |
| Administration | POST /collect | ADMIN plus CSRF; never call on the active service during UI tests |
| Accounts | GET /api/account/me; POST /api/account/register, /login, /logout | independent identity, BCrypt, CSRF |
| Compatibility | GET /, /login, /riot.txt, /leaderboard.html/riot.txt | preserve redirects/verification |

Needed additions, only in their phases:

- Archive summary projection with explicit basis and total Immortal, combined-only and reported subtiers; preserve old fields.
- Server-side tier filtering/sorting before paging where unsupported today. Filtering one fetched page is not global filtering.
- Bounded comparisons and evidence-backed change decomposition. Do not fabricate country-label correction history or stable player identity.
- Session-owned watchlist/preferences. Derive owner from authentication, never a request owner ID.
- Publication revisions/worker ownership after isolated PostgreSQL tests.

Add provenance without silently changing analytics calculations: source basis; observation/fetch/publication time where known; current/archive; reported/reconstructed tier; unknown allocation version; coverage. Preserve absent values with reasons, not zeros. Preserve current-versus-historical estimate-policy differences until a deliberate tested policy change.

## 6. Confirmed defects and priorities

| ID | Actual source/runtime evidence | Phase and acceptance |
| --- | --- | --- |
| D1 | act-trends loadSeries/loadPlayers lack cancellation/generation protection; error repaints retained chart; catalog reused as selected range | 1A: immutable request context/catalog, latest-only chart+table, coordinated failure/empty state |
| D2 | series rejects `to-from>=35`; live catalog contains 36 Acts | 1A: inclusive 35 archives+current accepted; invalid/reversed ranges still rejected |
| D3 | Archive regions query groups global-source rows. V26 Act IV runtime returns only EU 13,237, although APAC/India has 350 regional rows | 1B: regional summary and supporting rows use matching regional sources; global evidence remains separate |
| D4 | Archive `immortal` counts unsplit labels only, but renderCurrent labels it the source total. India I3/I2/I1 57/83/210 display as Immortal 0 | 1B: explicit combined/split/total semantics and reconciliation tests |
| D5 | DashboardCache uses first archive UUID and summed pages | 9A: full publication revision, unchanged-page correction and multi-Act invalidation |
| D6 | Archive startup resets all RUNNING rows; ownership is process-local; live-enabled=false does not gate archive startup | Before test API startup: safe historical disable gate. 9B: durable ownership/recovery, no second active writer |
| D7 | Repeated `act_order>=32` selects rank policies | 9A: named/versioned policies preserving boundaries and explicit early backcast |
| D8 | Current 390 px overview/APAC/global/archive-APAC measure 433 px; India views fit 390 px | 2 then view phases: fix intrinsic sizing/wrapping, contain wide tables; no body overflow hiding |
| D9 | Heavy historical controls precede discovery; selected player has nested capped scrolling | 3/5/6: concise overview and dedicated player/history pages |
| D10 | Some scripts still use `leaderboard-service/src/...`; documentation links have stale screenshot paths | Correct scripts as their relevant phase uses them |
| D11 | Current chart shape defaults to Smooth | 1A/6: Standard default; chart/table from one rounded data model |
| D12 | Persistent watchlist/settings and region comparison are absent from current accounts contracts | 7/8: real persistence/permissions and honest unavailable controls |

D3/D4 are demonstrated, not just review hypotheses. V26 Act IV completed 7/7 ladders and 600 pages; collection completion does not validate aggregate source mapping. Hidden legacy summary DOM in world/global views is not treated as a visible empty-state failure.

## 7. Feature-preservation checklist

Boxes are later acceptance requirements, not claims of fully automated Phase 0 journeys.

- [ ] Overview → Region → Country → Player keeps Act/scope; six region cards/standings and imagery remain reachable.
- [ ] Overview/global search and Ctrl+K reach exact player identity.
- [ ] Regional country/subregion filters and country sorting/paging remain correct.
- [ ] Country movement → player; global/regional rank semantics and conflicts remain distinct.
- [ ] Full player summary, RR/boundary, journey, targets, Tracker evidence and source clocks survive page migration.
- [ ] Current → archive and existing deep links work; no archive within-Act movement is invented.
- [ ] Trend country multi-select/cohort/estimate controls and all chart modes agree with table values and gaps.
- [ ] Unknown-country allocation, combined-tier sources and reconstructed subtiers remain distinguishable.
- [ ] Country/player/region comparisons use equal scope or disclose incompatibility.
- [ ] Save/remove/reopen all four watchlist entity types persists across account reload/login with owner isolation.
- [ ] Sign in/up/logout, username/email login, BCrypt, CSRF and ADMIN collection restrictions remain intact.
- [ ] Sign in returns to a validated local route without an open redirect.
- [ ] CSV preserves authorized selection, headings, Act/cohort and source basis.
- [ ] Metric → provenance, Back/Forward, desktop → mobile equivalent journeys work.
- [ ] Every flow has loading/success/empty/error/unavailable states.
- [ ] Visible focus, keyboard tabs/dialogs, reduced motion and usable mobile controls are tested.
- [ ] Collectors, rows/cursors, pause markers, tunnel and the independent tracker remain untouched.

## 8. Phase dependencies and complexity

Complexity is relative scope, not a time estimate. Commit and document each verified phase/subphase before proceeding.

| Phase | Small coherent delivery | Complexity / gate |
| --- | --- | --- |
| 0 | Audit/reference/evidence/plan | M; fresh tests and branch/runtime checks, no production edits |
| 1A | Historical supersession, catalog/range, coordinated chart/table and Standard default | M; reversed-response/error JS tests, inclusive-range Java test |
| 1B | Canonical archived summaries, tier semantics, shared provenance and isolated-runtime safety | M; native PostgreSQL split/combined/missing-shard fixtures |
| 2A | Tokens, shell, navigation, URL state and compatibility | M; Back/Forward, legacy access, focus and responsive fit |
| 2B | Search, accessible primitives and lifecycle helper | M; keyboard, latest-only, empty/error and focus restore |
| 3A/3B | World/regions then region detail with all six tabs | M each; real data, early useful content, scoped discovery/subregion |
| 4A/4B | Countries then global leaderboard/mobile preview | M each; filter/sort/page on full dataset, exact counts/profile links |
| 5 | Full player profile/race/journey/competition | L; preserve all panels, gap/identity/timestamp meaning |
| 6A/6B | Dedicated trends then archive explorer | M each; chart/table agreement, source completeness and export |
| 7A | All comparison modes and supported change explanation | M/L; same cohort/source or explicit unavailable reasoning |
| 7B | Persistent watchlist | M; additive migration, two-account isolation, login persistence |
| 8 | Auth/settings/methodology/roadmap | M; actual offered persistence, existing security tests, safe return |
| 9A | Publication/cache and explicit rank policy | L; same-page corrections invalidate, boundary fixtures preserved |
| 9B | Worker ownership, bounded upstream/profile single-flight and measured performance | L; isolated two-worker/recovery/nonprogress/resource tests |
| 10 | Full integrated/visual release review | L; all requested flows, views and sizes; no deployment without authorization |

Do not advance failed gates. Keep backend and frontend changes separately reviewable; Phase 1 correctness precedes large visual work. Source collection/policy changes belong to isolated tests and their own operational review.

## 9. Verification strategy

- Resolve existing Java 21 and this Gradle wrapper (9.6.0); do not install another JDK because PATH is absent. Native PostgreSQL, no Docker for this workflow.
- Baseline: fresh 39 discovered, 34 passed, 5 skipped, zero failure/error; opt-in database/upstream cases were disabled. H2 is not PostgreSQL verification.
- Database phases need a dedicated disposable `ranked_observatory_redesign_test` database; verify target name. Never use the historical archive as the integration-test URL.
- Before starting a second API runtime, implement/verify a historical-worker disable setting; `leaderboard.enabled=false` alone is insufficient. Disable/mock upstream access in test mode.
- Use deterministic browser fixtures for slow/out-of-order/error data, labeled synthetic, plus separate actual backend acceptance on isolated data.
- Node built-in tests/VM can cover pure existing JS; add reproducible configurable Playwright/Edge runner instead of hardcoded machine paths.
- For each UI phase: 1440 × 900 and 390 × 844; relevant keyboard, lifecycle, loading/empty/error, Back/Forward; inspect actual screenshots and console.
- Final pass: all 16 views at 320 × 700, 360 × 800, 390 × 844, 430 × 932, 768 × 1024, 1024 × 768, 1440 × 900, 1536 × 816 and 1920 × 1080. Exercise all 16 task journeys, authorized exports, auth, comparisons and watchlist.
- Reuse one test runtime per coherent phase. No overlapping Gradle; rebuild/restart only for relevant changed code. Stop only task-created test processes.
- Update `PROGRESS.md` per phase and `FINAL_VERIFICATION_REPORT.md` in Phase 10 with actual commands, outcomes, screenshots, basis and remaining limitations.

## 10. Rollback and release isolation

Keep legacy access until each replacement passes. Documentation/asset commits are independent. Use additive migrations and compatible readers, with backup and a reviewed handoff before any active archive change. Never delete source history to roll back. Reinspect uncommitted work at every phase boundary; do not restore over others' changes.

No deployment, collector restart/reset, tunnel change or streaming-tracker modification is included. Phase 0 changed no application code or database state.
