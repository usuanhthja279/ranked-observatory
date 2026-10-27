# Phase 11A — Correctness and functional fixes

Date: 27 October 2026 (Asia/Calcutta)

Branch: `codex/ranked-observatory`

Base: `c87b54aae583f7ed1da4cc86f30fdfdb0dec2630`

## Changes

- Player comparison resolves the selected Act in the renderer's scope. Numeric metrics validate finite values; tier and country values are categorical, global rank reports which player ranks better, and regional ranks from different regions are explicitly not comparable.
- Player A and B suggestion searches have independent debounce and request generations. Obsolete searches and comparisons are aborted or ignored. A history request failure is displayed while the current standing comparison remains available.
- Act trend series retain each selected Act position and draw separate straight, smooth, and stepped paths for each contiguous observed segment. Single-series fills also stop at data gaps.
- Act trend summaries and tables use the actual first and last selected Act values. A missing endpoint is shown as unavailable and suppresses full-range change. Legends identify the Act of their latest available point.
- Act trend palette now has ten colors, three marker shapes and four line patterns. Percent-based table and summary values include percent units.
- Country history alignment retains timestamps with missing measurements as null gaps; an explicitly observed zero remains zero. Country comparison deltas reject empty and nonnumeric values.
- Script ordering and cache versions in `leaderboard.html` ensure Act Trends loads the shared analysis helpers before its renderer and browsers receive the updated scripts.

## Changed files

- `src/main/resources/static/act-trends.js`
- `src/main/resources/static/compare-analysis.js`
- `src/main/resources/static/compare-page.js`
- `src/main/resources/static/leaderboard.html`
- `src/test/js/compare-analysis.test.cjs`
- `src/test/js/compare-page.test.cjs`
- `docs/ui-redesign/PROGRESS.md`
- `docs/ui-redesign/PHASE_11A_VERIFICATION.md`

## Verification

- `node --check src/main/resources/static/compare-page.js` — **PASS**.
- `node --check src/main/resources/static/compare-analysis.js` — **PASS**.
- `node --check src/main/resources/static/act-trends.js` — **PASS**.
- Focused suite: `node --test src/test/js/compare-analysis.test.cjs src/test/js/compare-page.test.cjs src/test/js/act-trends-export.test.cjs` — **PASS, 20 tests**. Includes rendered player comparison for two exact Riot IDs, archived Act context, numeric and categorical values, failed history fallback, late search response supersession, independent A/B searches, missing-versus-zero country history, chart segment gaps, endpoint calculations, ten-color palette availability and helper-before-renderer script order.
- Complete JavaScript suite, `$all = Get-ChildItem src/test/js -Filter '*.test.cjs' | ForEach-Object { $_.FullName }; node --test $all` — **40 passed, 1 failed of 41**. The unrelated `region-detail.test.cjs` assertion at line 78 expects the archived historical-trend disclosure to be unhidden after switching tabs; it fails when run alone as well. No region-detail code or test was changed in this phase.
- `git diff --check` — **PASS** (Git reports only its standard LF-to-CRLF conversion warnings).
- No Java code or API contract changed, so Gradle tests were not run.
- The compare page was exercised in a DOM-backed script harness. A desktop/mobile screenshot review was not performed: the live port 8090 service was actively collecting, and the modified static assets were not deployed to that running process. No restart or collection interruption was made.

## Runtime safety

A read-only status request after verification reported `enabled:true`, `paused:false`, and `running:true` on port 8090 (latest complete snapshot 416). The service was left untouched. No collection endpoint was called, no database or extraction state was changed, and untracked `match-data/` and `ui-sample/` were preserved.

## Remaining issue

The region archived-disclosure test failure should be investigated in a separate focused correction. Full browser visual verification should be done after a safe static-asset deployment window, without restarting an active collector.
