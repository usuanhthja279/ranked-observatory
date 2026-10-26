# Ranked Observatory UI guide and browser walkthrough

> Responsive follow-up: see [the 2026-10-26 two-resolution retest](RANKED_OBSERVATORY_RESPONSIVE_RETEST_2026-10-26.md) for desktop (1536 × 816) and mobile (390 × 844) results, full-page capture behavior, and the mobile overflow findings. The original screenshots indexed below are from an earlier viewport pass.

## What was reviewed

I reopened the running local Ranked Observatory in the in-app browser and walked the main page, region page, country page, global leaderboard, act-trend controls, pagination, and player selection. The page served from port 8090. Screenshots are PNGs in `docs/screenshots/ranked-observatory/flows/` and were captured at a 712 × 668 browser viewport.

This is a practical set of distinct view states, not every possible filter combination. The UI walkthrough used the in-app browser. No automated test suite was run. This browser surface did not provide a 390 px device-emulation viewport, so the screenshots are narrow-layout evidence, not a full mobile-device verification.

## Screenshot index

| # | Scenario | What the screenshot records |
| -: | --- | --- |
| 1 | [Overview landing](screenshots/ranked-observatory/flows/01-overview-landing.png) | Main header, data confidence, search and region entry |
| 2 | [Player search results](screenshots/ranked-observatory/flows/02-player-search-results.png) | Search matches and player context |
| 3 | [Searched player selected](screenshots/ranked-observatory/flows/03-searched-player-selected.png) | Selected player summary and history |
| 4 | [Explore region cards](screenshots/ranked-observatory/flows/04-explore-region-cards.png) | Region cards with scenery, leading player, and RR |
| 5 | [Regional standings popup](screenshots/ranked-observatory/flows/05-regional-standings-popup.png) | Standings opened from the region-card flow |
| 6 | [Overview activity](screenshots/ranked-observatory/flows/06-overview-activity.png) | Region activity beside Top 10 countries |
| 7 | [Region country data](screenshots/ranked-observatory/flows/07-region-current-country-data.png) | Current country standings and estimate control |
| 8 | [Region subregion filter](screenshots/ranked-observatory/flows/08-region-subregion-filter.png) | Asia-Pacific subregion selection |
| 9 | [Region movement](screenshots/ranked-observatory/flows/09-region-movement-data.png) | Activity totals, rank movement, and promotion/demotion counts |
| 10 | Movement player drilldown | No screenshot: the captured range had zero eligible possible promotions/demotions, so there was no player list to open. |
| 11 | [Region rank mix](screenshots/ranked-observatory/flows/11-region-rank-mix.png) | Rank distribution in a region |
| 12 | [Country preview](screenshots/ranked-observatory/flows/12-country-preview.png) | Country top-player preview |
| 13 | [Preview player selected](screenshots/ranked-observatory/flows/13-preview-player-selected.png) | Selected preview player with RR history |
| 14 | [Act-trend country picker](screenshots/ranked-observatory/flows/14-act-trend-country-picker.png) | Multi-country selection for act comparison |
| 15 | [Smoothed trend](screenshots/ranked-observatory/flows/15-trend-smooth.png) | Curved chart mode |
| 16 | [Standard trend](screenshots/ranked-observatory/flows/16-trend-standard.png) | Straight-line chart mode |
| 17 | [Stepped trend](screenshots/ranked-observatory/flows/17-trend-stepped.png) | Horizontal/vertical chart mode |
| 18 | [Estimates and tier coverage](screenshots/ranked-observatory/flows/18-trend-estimates-tier-coverage.png) | Estimate toggle and Immortal archive-coverage detail |
| 19 | [Archived act snapshot](screenshots/ranked-observatory/flows/19-archived-act-snapshot.png) | V26 Act IV selected; this state also exposes the archived-summary mismatch described below |
| 20 | [Country page overview](screenshots/ranked-observatory/flows/20-country-page-overview.png) | India country header, snapshot, and standings |
| 21 | [Country movement](screenshots/ranked-observatory/flows/21-country-movement.png) | Country-specific activity and movement |
| 22 | [Country player table](screenshots/ranked-observatory/flows/22-country-player-table.png) | Country player rows and page controls |
| 23 | [Country player selected](screenshots/ranked-observatory/flows/23-country-player-selected.png) | Country player details and RR history |
| 24 | [Global leaderboard, page 1](screenshots/ranked-observatory/flows/24-global-leaderboard-page-1.png) | Global ranking first page |
| 25 | [Global leaderboard, page 2](screenshots/ranked-observatory/flows/25-global-leaderboard-page-2.png) | Global ranking pagination |
| 26 | [Global player selected](screenshots/ranked-observatory/flows/26-global-player-selected.png) | Player details opened from global rankings |
| 27 | [SkRossi player card](screenshots/ranked-observatory/flows/27-skrossi-player-card.png) | S8UL SkRossi’s Radiant race and RR history |

## SkRossi player-card review

The India/APAC player card loaded for **S8UL SkRossiエース #4646**. The captured view shows an observed Radiant boundary of **479 RR**, the player **1 RR above** it, **16 RR gained on the boundary**, and a 60-minute RR/boundary chart. The in-app page also exposed the player’s historical act journey, overtake targets, and Tracker profile details when expanded. The plotted observations are sparse snapshots, not match-by-match history; the boundary is an observed regional comparison and is not an official promotion requirement.

## Archived-act discrepancy found

The selected V26 Act IV page reported archive collection complete (**7/7 source ladders, 600 pages**) and the India player section contained **350 players** with individual RR/tier rows. However, the **Archived act snapshot** summary said **“No current observations”** (and the visible aggregate row showed zero Radiant and zero combined-Immortal counts). That conflicts with the populated player list. Treat the archived aggregate summary as incorrect or incomplete for this act until its aggregation/source mapping is reconciled. The act-trend chart and player rows should not be interpreted as proving that the summary totals are correct.

## Walkthrough outcome

- The overview, region, country, and global routes rendered in the in-app browser.
- Search, region standings, activity tables, subregion selection, act selection, chart styles, player selection, and leaderboard paging were reachable in the reviewed states.
- The three trend shapes are represented by separate captures.
- The act comparison surface reports 35 completed historical acts in the observed browser state. This is archive coverage, not proof that every rank subtotal is fully resolved; the Immortal source-coverage disclosure remains important.
- Possible promotion/demotion drilldown is data-dependent. It could not be captured in the chosen interval because both eligible counts were zero.
- The player-card capture specifically checks SkRossi as requested.

## Scope and limitations

These are visual/manual walkthrough screenshots, not automated assertions. The narrow browser viewport was 712 px wide; a 390 px device-emulation pass was unavailable in this browser surface. Browser selection and scrolling were exercised, but this document does not claim complete accessibility, cross-browser, performance, or mobile-device certification. No GIFs were made; the PNGs preserve actual rendered states without implying an interaction recording.

