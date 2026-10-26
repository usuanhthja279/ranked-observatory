# Phase 0 evidence

Audit: 26–27 October 2026, Asia/Calcutta. Source base: `c3faa2b`. Browser: installed Microsoft Edge driven by bundled Playwright, reduced motion enabled, fresh logged-out contexts.

## Prototype

`prototype-audit.json` records all 16 prototype views at 1440 × 900 and 390 × 844 plus Ctrl+K, India search and Escape. All 32 view states rendered without recorded page exceptions/document overflow.

Representative actual browser captures are retained. Visual inspection included the supplied full previews, player/trends/region/countries/archive desktop and leaderboard/compare/settings mobile captures. These are explicitly synthetic design-reference results, not production-data tests or a certification of every control.

## Existing live application

`baseline-audit.json` records overview, APAC, India, global, archived APAC and archived India at both sizes. Final captures await core content and historical charts. Global uses player rows because it does not set coreReadyMs.

At 1440 px all six routes fit. At 390 px overview/APAC/global/archived APAC measured 433 px; both India views fit 390 px. Initially gathered overflowing-element diagnostics are not a complete final attribution of the layout cause; use measured document widths and actual screenshots.

The active collector published during inspection; counts/times differ across captures. No current total is a fixed assertion. Hidden legacy summary DOM in world/global views is not a visible empty-data defect. No login, profile-enrichment request, collector POST, database write or service restart was performed.

`archive-summary-audit.json` and four `baseline-archive-{region|country}-summary-{desktop|mobile}.png` captures show exact archived cards. V26 Act IV: 7/7 ladders, 600 pages, India 350 players with reported I3/I2/I1 57/83/210. The regional projection selects global-source rows and returns only EU. The card reads unsplit-only Immortal as the total, yielding a misleading zero.

## Java baseline and scope

`java-baseline.json` is derived from fresh Gradle XML: 39 discovered, 34 passed, 5 skipped, zero failures/errors across 14 suites. Database/upstream prerequisites were disabled; no native PostgreSQL integration pass is claimed.

Screenshots are viewport captures unless `summary` identifies the exact currentSection element. They do not expand/stitch the old nested player inspector. Retained screenshots are audit evidence, not visual-regression golden files.

Browser audit commands used temporary Node scripts with bundled Playwright, installed Edge and explicit 1440 × 900 / 390 × 844 contexts. Prototype calls were file-URL reads plus nav(view); live navigation was GET-only to the existing 8090 service. All task-created browser contexts/processes were closed.
