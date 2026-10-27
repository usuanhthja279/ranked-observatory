# Phase 10 — Final Verification Report

Date: 27 October 2026 (Asia/Calcutta)
Branch: `codex/ranked-observatory`
Phase 10 base: `bbcdca7` (`fix(workers): enforce archive ownership and profile single-flight`)

## Summary

The local application was rebuilt and restarted on port 8090 after the final correction. Current and archive data workers remained paused. A responsive route smoke matrix and focused interaction checks were run in ChatGPT's in-app browser. One tablet-width Countries-directory overflow was found and fixed. An H2 fixture gap surfaced in the first full Java run and was corrected before the passing rerun.

## Phase 10 changes

- `src/main/resources/static/countries.css`: added a tablet breakpoint so the Countries search field uses a full row and the Region/Sort controls share the next row. This removes the 768 px page-level overflow without hiding content.
- `src/test/java/com/valorant/leaderboard/StandaloneApplicationTest.java` and `src/test/resources/db/test-publication-revision.sql`: added the small H2 schema fixture for the publication revision table introduced in V10. Production SQL and production workflow behavior were not changed.

## Verification results

| Check | Result | Evidence |
| --- | --- | --- |
| `$files = Get-ChildItem src/test/js -Filter '*.test.cjs' | ForEach-Object { $_.FullName }; node --test $files` | PASS — 16 tests, 0 failures | Search lifecycle, trend export, comparison, country directory and region detail regressions |
| `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test bootJar --console=plain` with process-scoped `LEADERBOARD_TEST_DB_*` variables targeting the dedicated database | PASS — 59 tests, 0 failures, 0 errors, 1 skipped; bootJar successful | Full Gradle test task and native PostgreSQL integration cases |
| `git diff --check` | PASS | No whitespace errors; Git emitted only line-ending conversion warnings |
| ChatGPT in-app browser responsive matrix | PASS — 162 route/viewport checks | 18 canonical/legacy routes × 9 CSS viewports: 320×700, 360×800, 390×844, 430×932, 768×1024, 1024×768, 1440×900, 1536×816, 1920×1080. Each route had a title and H1; document/body width did not exceed viewport. |
| Countries directory at 768×1024 after correction | PASS | Controls fit within the panel; sort select right edge was 730 px inside the 768 px viewport; document and body widths both 768 px |
| Countries search and paging at 390×844 | PASS | “India” filter produced the India and British Indian Ocean Territory matches; Next showed 13–24 of 190 and Previous returned to 1–12 of 190 |
| Search keyboard control at 390×844 | PASS | Ctrl+K opened the Find a player dialog and focused its input; Escape closed it |
| Mobile navigation | PASS | Mobile primary navigation was visible and Countries was selected in the captured view |
| Browser console | PASS | No console errors in the final Countries view |
| Current collector status before and after restart | PASS | `enabled=false`, `paused=true`, `running=false`; latest completed snapshot remained 400 |
| Archive worker status before and after restart | PASS | `paused=true`, `running=false`, `workerOwner=true`, `scheduled=false` |
| Mobile archive view after the search-focus follow-up | PASS — captured and visually inspected at 390 × 844 in ChatGPT's in-app browser | V26: Act V archive loaded 53,775 global source rows with 7/7 source ladders; archive section tabs remained inside their own horizontal scroller and mobile navigation remained visible. Capture was displayed inline; no repository PNG export was available. |
| Authenticated UI flow in isolated PostgreSQL test database | PASS | Created and signed in as a synthetic user on temporary port 8091. Saved a display preference and verified it after reload; saved a synthetic watchlist item and verified it after logout/sign-in; signed-out watchlist state correctly prompted for sign-in. No live application database was used. |

The first Java run reported two H2 failures because the test-only schema list did not contain the V10 publication revision table. After adding `test-publication-revision.sql`, the final full Gradle rerun supplied a process-scoped connection to `ranked_observatory_redesign_test` and passed 59 tests with no failures or errors. PostgreSQL integration tests for country activity, country model evidence, global persistence, leaderboard migrations/comparison, and archive worker locking all passed. The only remaining skip is `TrackerLeaderboardClientTest.fetchesRealTrackerPageUsingJavaHttpClient`, which requires `LEADERBOARD_LIVE_TEST=true`; it stayed disabled so no live Tracker fetch occurs.

## Visual review

Actual browser captures were taken and inspected in ChatGPT's in-app browser at 1440×900 (Overview), 768×1024 (Countries before and after the correction), and 390×844 (Countries, Trends, and the Act V archive). The mobile view retains the bottom navigation, content remains readable, the Countries filters no longer clip, and archive tabs stay within their own horizontal scroller. Captures were displayed inline during this task. This browser interface did not provide a supported way to export those captures as PNG files into `docs/ui-redesign/evidence/`; no file-backed screenshots are claimed.

## Routes covered

`/overview`, `/regions/ap`, `/countries`, `/countries/in?region=ap`, `/leaderboard`, `/trends`, `/archive`, `/compare?mode=country`, `/watchlist`, `/methodology`, `/settings`, `/signin.html`, `/signup.html`, `/roadmap`, and legacy `leaderboard.html` world, region, country and global views.

## Remaining limitations

- The player-profile visual journey was not opened against the local live app because its profile read may call the upstream Tracker provider. Keeping collection paused was a release invariant; no external profile request was issued. Profile route validation and existing profile tests remain the available evidence.
- The browser auth journey used one synthetic account in `ranked_observatory_redesign_test`; it does not replace multi-account authorization isolation checks, MFA, password recovery, or email delivery verification. Existing Java integration tests remain the evidence for CSRF, authorization, account isolation and persistence.
- The 162 checks verify route shell rendering and horizontal fit; they do not certify every data combination or every keyboard/screen-reader interaction. Phase-specific unit and Spring regressions provide the corresponding targeted coverage.
- Standalone PNG evidence is still absent. The actual in-app-browser images were inspected inline and identified above.

## Restart and data safety

The app was stopped and restarted through `scripts/Stop-LeaderboardService.ps1` and `scripts/Start-LeaderboardService.ps1`. The post-restart health endpoint responded on port 8090; PostgreSQL-backed snapshot reads succeeded. No collection endpoint or provider-profile lookup was invoked. Latest successful snapshot ID remains 400.

## Search focus follow-up

- The Find a player dialog intentionally focuses its search input on open; `Ctrl+K` opens it and `Escape` closes it and restores focus to the trigger.
- The blue inset ring duplicated the search field's existing teal `:focus-within` treatment. Removed the inset ring while keeping a visible focus state on the field container.
- ChatGPT in-app browser verification after restart: modal opens with one teal focus treatment, Ctrl+K opens it, and Escape closes it. No player query was submitted.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat bootJar` — **PASS**. `git diff --check` — **PASS**.
- Restarted on port 8090 using the repository scripts. The all-extraction pause marker remained present; startup log confirms archive extraction remains paused. HTTP GET `/countries` returned 200 and rendered in the in-app browser.

## Isolated authenticated UI acceptance

- Started a temporary Spring instance on port 8091 against `ranked_observatory_redesign_test`, with `LEADERBOARD_ENABLED=false` and `LEADERBOARD_PAUSE_ALL_EXTRACTION=true`. The instance logged that archive extraction remained paused.
- In ChatGPT's in-app browser, created a synthetic account, signed in, saved the Compact tables preference and verified it after reload.
- Saved a synthetic watchlist player, signed out, verified the anonymous watchlist gate, signed in again and verified the saved item returned.
- Stopped the temporary port-8091 process after the checks. The main service remained on port 8090 and the all-extraction pause marker remained present.
- This was a real browser-to-Spring-to-PostgreSQL flow against the isolated test database. The synthetic account remains only in that dedicated test database. No external profile lookup, account email, or collection request was made.

## Supplemental mobile archive acceptance — 27 October 2026

- In ChatGPT's built-in browser at 390 × 844, the saved V26: ACT V archive opened with 53,775 global source rows and 7/7 completed source ladders.
- The Countries section filtered for `India` and returned India plus British Indian Ocean Territory (2 labels, 320 observed entries, 3 confirmed Radiant entries). Opening India's Asia-Pacific view retained the selected Act and showed the regional archived standings.
- The Players section loaded Asia-Pacific standings and advanced from page 1 to page 2 of 600. The Source coverage section displayed the 7/7 publication matrix.
- Visual inspection confirmed the narrow archive tab strip and wide tables use their own horizontal scrollers. At a 390 px viewport, document and body widths were 380 px; no page-level horizontal overflow was present.
- Screenshots were captured and inspected inline in the built-in browser. They were not saved as repository PNGs: the browser has no supported filesystem export for the screenshot bytes, and a data-URL export attempt was refused by browser URL policy. No alternate export path was attempted.
- All reads used the persisted archived snapshot. No player profile was opened and no provider request or collection was triggered. The extraction pause remained enabled.
