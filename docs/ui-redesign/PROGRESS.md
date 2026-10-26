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
- [ ] 2A/2B — Shared shell, routing, accessibility and search.
- [ ] 3A/3B — World/regions and region detail.
- [ ] 4A/4B — Countries and global leaderboard.
- [ ] 5 — Full player profile and Radiant race.
- [ ] 6A/6B — Dedicated trends and archive explorer.
- [ ] 7A/7B — Comparisons and persistent watchlist.
- [ ] 8 — Account/settings, methodology and roadmap.
- [ ] 9A/9B — Publication/cache/policy and collector/resource reliability.
- [ ] 10 — Full integrated/visual release review.

A rendered illustrative reference does not complete a production implementation phase.
