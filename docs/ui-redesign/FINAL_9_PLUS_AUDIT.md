# Final 9+ Production-Readiness Audit

Date: 28 Oct 2026  
Branch: `codex/ranked-observatory`  
Baseline score: **7.1/10 provisional** (from the master prompt)  
Final evidence-based score: **8.4/10** — below the 9.0 release target.

## Scorecard

| Area | Weight | Score | Evidence and limit |
|---|---:|---:|---|
| Analytical correctness | 20% | 9.0 | Regression tests cover typed comparisons, explicit missing values, chart gaps, endpoint selection and round/schema reconciliation. Full production data correctness is not proven by these tests. |
| Functionality and user journeys | 20% | 8.4 | Search, navigation, player/country comparison, archive, watchlist gating, history and CSV-related flows have focused coverage. Authenticated persistence and all end-to-end data journeys were not executed against PostgreSQL. |
| UI/UX | 15% | 8.8 | Current-source desktop pages were visually reviewed; shared hierarchy and responsive cards/navigation are consistent. The live data banner reports the upstream collection failure. |
| Mobile responsiveness | 10% | 8.8 | 81 browser route/viewport checks passed at the nine requested widths. Mobile Region and Countries were visually inspected at 390×844; the full mobile matrix was geometry-checked, not screenshot-inspected page by page. |
| Performance | 10% | 7.3 | Deferring closed historical panels removes optional initial requests. Three warm trend-capability samples were 111 ms, 33 ms and 17 ms (92,997 bytes each). Cold baseline was about 15.6 s; no safe cold comparison or large-page profile was run. |
| Backend reliability | 10% | 7.2 | Java build/tests pass with six skips and CI now provisions PostgreSQL. The CI run is pending; current live collection is idle after Tracker HTTP 403. |
| Security and accessibility | 10% | 8.0 | Existing account ownership/CSRF checks were preserved; keyboard search/dialog and region tabs were manually checked. No numeric contrast, screen-reader, axe, or authenticated isolation test was completed in this environment. |
| Maintainability and tests | 5% | 8.7 | 46 JavaScript tests pass; Java suite has no failures; CI and browser smoke automation added. CI and the Playwright script have not yet executed. |
| **Weighted total** | **100%** | **8.4** | Weighted calculation: 1.80 + 1.68 + 1.32 + 0.88 + 0.73 + 0.72 + 0.80 + 0.435 = 8.365, rounded to 8.4. |

Scores reflect the evidence above and are not an assertion that all master-prompt acceptance items passed.

## Rebuild and test results

- Java 21 command: `.\gradlew.bat test bootJar --rerun-tasks` — **BUILD SUCCESSFUL**.
- JUnit reports: **59 tests, 53 passed, 0 failures, 0 errors, 6 skipped**. The skipped cases require PostgreSQL or a real Tracker request; no PostgreSQL server was available and live external requests were intentionally avoided.
- JavaScript command: `node --test src/test/js/*.test.cjs` — **PASS, 46/46**.
- Syntax validation: `node --check` across 24 static JS files and `scripts/leaderboard/browser-smoke.cjs` — **PASS**.
- `git diff --check` — **PASS**, with expected Windows line-ending notices.
- Browser smoke automation — **not run locally**; Playwright is not installed. GitHub Actions workflow is prepared but no CI result is available yet.
- Browser verification used the ChatGPT in-app browser against current source on 8091. Nine routes × nine viewports passed overflow/landmark checks. Desktop pages and selected mobile pages were visually inspected in-browser; screenshot files were not exported.

## Operational safety findings

- Read-only current collector status: `enabled=true`, `paused=false`, `running=false`. Run 426 stopped at page 479 after **Tracker HTTP 403**. Do not describe fresh collection as healthy until the upstream response is resolved and a later run completes.
- Read-only archive status: `enabled=false`, `paused=true`, `running=false`, `workerOwner=true`. No duplicate worker was observed. Database lock testing was skipped because PostgreSQL was unavailable.
- No application restart, collection retry, administrative endpoint, database write, migration, checkpoint change, or source-profile modification was performed.
- The temporary 8091 current-source preview does not proxy account endpoints. This caused a Settings error in that preview; direct backend responses were correct for a signed-out visitor (401 preferences/watchlist; `authenticated:false`).

## Verified achievements

- Corrected correctness issues in comparison, missing-value, chart-gap and stale-search behavior.
- Added bounded multi-entity search, route context preservation, and player journey controls.
- Reduced optional historical-trend work on initial page load.
- Completed the 81-case responsive geometry sweep in the built-in browser and corrected observed mobile layout/touch-target issues.
- Added reproducible CI gates with isolated PostgreSQL and collection disabled for the smoke app.

## Remaining bugs, risks and production blockers

1. **Collection freshness:** latest live run is idle after Tracker returned 403 at page 479. This is a real operational blocker for current data freshness, not a UI-only issue.
2. **Database acceptance:** six integration tests were skipped locally. PostgreSQL migration, persistence, ownership isolation and worker-lock behavior still require the isolated CI database run.
3. **Browser automation:** CI workflow and Playwright smoke are added but have not executed; no passing CI artifact exists yet.
4. **Authenticated journeys:** no customer account was used for watchlist or preference save/reload/account-isolation tests in this pass.
5. **Performance:** current cold analytics latency was not measured; the previous approximately 15.6 s cold baseline remains unresolved.
6. **Accessibility:** keyboard flows were checked, but contrast ratios and screen-reader behavior are not verified and no automated WCAG scan was run.
7. **Visual evidence retention:** screenshots were inspected in the ChatGPT in-app browser, but none of these final-pass captures are present as saved repository artifacts.

## Smallest changes to reach the 9.0 target

1. Run the new GitHub Actions workflow and fix any PostgreSQL, Chromium or smoke failures; retain its screenshot/log artifact.
2. Provide a safe isolated local or CI PostgreSQL run for the six skipped integration cases and authenticated owner-isolation tests.
3. Resolve or document the Tracker 403 operational cause and verify a full later collection run without changing pause state or bypassing upstream controls.
4. Profile cold historical analytics on an isolated database and address the query/cache bottleneck if the 3-second cold target is still required.
5. Run an automated axe scan, measure contrast for the shared theme and manually review screen-reader output; fix any critical/serious findings.
6. Export and retain representative desktop/mobile screenshots after browser automation is available, including the authenticated account views.

## Release decision

**Not approved as a 9+/10 production release.** The UI and core correctness improvements are substantial, but the current evidence is insufficient for the release gate: collection is failing upstream, database integration tests are skipped locally, CI has not run, authenticated persistence is unverified, and cold performance/accessibility evidence is incomplete. The implementation branch remains isolated from deployment and the live collector's extraction/pause settings were preserved.

