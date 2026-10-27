# Ranked Observatory 2.0 Quality Improvement Report

Date: 28 Oct 2026  
Branch: `codex/ranked-observatory`  
Baseline: `94d43e9cdfd5e5dac28075259e8062dcb8f3f60b`

## Scope

This report covers the ordered 11A–11F quality-improvement work. Phases 11A–11D were implemented in earlier commits. The final responsive sweep and CI quality gate were completed in 11E; 11F records the independent release assessment. Neither the running Observatory nor its database was restarted or modified during this final pass.

## Delivered

- **11A — Correctness:** comparison scope and metric semantics, stale player-search handling, accurate trend gaps/endpoints and country-history null handling.
- **11B — Responsive UI:** repaired narrow region mastheads; completed the later 9-route × 9-viewport layout sweep in the ChatGPT in-app browser.
- **11C — Discovery and journeys:** unified bounded player/country/region search; keyboard operation, cancellation, focus restoration and recent destinations; improved player/country navigation and Watchlist prefill.
- **11D — Reliability/performance:** optional historical trend reads are deferred until opened; regression coverage protects one-time and eager initialization paths.
- **11E — Verification:** added an isolated GitHub Actions workflow and Chromium smoke script, fixed the stale live-versus-archive region test, and polished mobile account controls/touch target sizing.
- **11F — Final audit:** final score, test evidence, release blockers and next steps are recorded below and in [FINAL_9_PLUS_AUDIT.md](FINAL_9_PLUS_AUDIT.md).

## Verification evidence

### Automated checks

| Check | Result |
|---|---|
| `node --test src/test/js/*.test.cjs` | PASS — 46 tests, 0 failures |
| `node --check` for 24 static JavaScript files and `scripts/leaderboard/browser-smoke.cjs` | PASS |
| Java 21 `.\gradlew.bat test bootJar --rerun-tasks` | BUILD SUCCESSFUL; 59 tests, 53 passed, 0 failed, 6 skipped |
| `git diff --check` | PASS; only Windows line-ending notices |
| PostgreSQL-backed integration tests | NOT RUN locally: no listener on 5432/5433 and no `LEADERBOARD_TEST_DB_URL` |
| GitHub Actions workflow | Added, not yet executed |
| Headless Playwright script | Syntax checked; not run locally because Playwright is not installed |

The six skipped Java tests require a PostgreSQL test database or a real external Tracker request. No live Tracker call was made by this verification pass. The CI workflow provisions an isolated PostgreSQL 16 service and disables collection before its browser smoke run; that workflow still needs an actual CI run.

### Built-in browser review

The ChatGPT in-app browser opened the current-source preview at `127.0.0.1:8091`. The 9 required viewport sizes were checked against Overview, Regions, Countries, Global leaderboard, Trends, Archive, Compare, Watchlist and Settings: **81/81 route/viewport checks passed without document-level horizontal overflow**. Desktop screenshots were visually inspected across all nine routes. Mobile Region and Countries were screenshot-inspected at 390×844; the masthead and Login action fit, and the section tabs remain scrollable with keyboard/touch guidance. The screenshots were displayed in the browser for inspection but could not be exported into repository files.

Additional interactions checked in-browser included Ctrl+K search opening, Escape and focus restoration, mobile navigation, region-tab keyboard movement, country selection, ten-country chart/table rendering, player comparison, archived summary rendering and Back navigation restoring country filters. These were read-only interactions and did not submit account or collector actions.

The source preview forwards leaderboard GET requests but not `/api/account/*`; therefore its Settings page reports a preview-proxy failure. Direct read-only requests to the live backend returned `401` for preferences/watchlist and `{"authenticated":false}` from `/api/account/me`, as expected for a signed-out visitor. The current source maps a real `401` from preferences to the sign-in panel. This preview limitation does not establish a product authentication failure.

## Operational state and limitations

- Live extraction: enabled, not paused, currently idle. The latest run (426) failed on source page 479 with Tracker HTTP 403. This review did not retry it or call collection/admin endpoints.
- Historical extraction: disabled and paused, not running. Its pause marker was left unchanged.
- No app restart, database mutation, migration, user-data cleanup, account mutation, or permission change was performed.
- The existing 8090 app bundle predates the current branch UI; 8091 was used only for current-source browser review.
- No authenticated watchlist/settings persistence journey, full database integration run, numeric WCAG contrast audit, screen-reader pass, axe scan, cold-cache performance profile, or 9+ production release verification is claimed.

## Commits

- Phase 11E: `a3137d7` — `test(observatory): add comprehensive browser and integration quality gates`
- Earlier phase commits include `4c48e05` (11D) and `dcb2f42` (11C), with earlier work recorded in `PROGRESS.md`.

