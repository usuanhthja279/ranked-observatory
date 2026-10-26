# Ranked Observatory responsive retest — 2026-10-26

## Test setup

Manual browser review used the built-in browser against the local application on port 8090. The two tested viewport sizes were **1536 × 816** (desktop) and **390 × 844** (mobile). Full-page captures were used so content below the fold is included in one image wherever the browser can capture it. No automated suite was run.

The browser can capture the whole document, but it does not expand nested scroll containers. The selected-player detail panel is internally scrollable and much taller than its visible area; a full-page capture shows only the visible part of that panel. Scrolling the panel confirms the lower content exists, but getting both the top and bottom of that panel into one image requires a product change (expand the panel for capture) or stitching separate captures. Full-page Asia-Pacific current and archived captures at both requested sizes are visible in the accompanying Codex conversation; this browser capture surface does not provide a local file-save path for those new images.

## Coverage and result

| View | State reviewed | Desktop, 1536 × 816 | Mobile, 390 × 844 |
| --- | --- | --- | --- |
| Overview | Current dashboard, region cards, activity, Top 10 countries | Full document captured; no horizontal page overflow (document width 1526 px) | Full document captured; horizontal overflow (document width 426 px). Region-card carousel items and act-trend controls extend beyond the viewport. The one-card-at-a-time carousel itself displays correctly. |
| Overview | Archived V26 Act IV | Full document captured; no horizontal page overflow (document width 1526 px). Act header and archived confidence render; the act comparison panel remains visible. | Full document captured; document width 426 px. Act filters extend beyond the card, and the region carousel still shows its single active card. |
| Region | Asia-Pacific, current act, country standings, act trends and RR changes | Full document captured; no horizontal page overflow (document width 1526 px) | Full document captured; horizontal overflow (document width 426 px). The rank-tier control reaches 426 px and the multi-country trend table is wider than the viewport. Country standings are contained in a horizontal-scroll table. |
| Region | Archived V26 Act IV | Full document captured at the requested desktop size; no horizontal page overflow (document width 1526 px). Archive confidence says 7/7 source ladders and 600 pages, but the archived snapshot card says “No current observations” while country rows and act-trend data are present. | Full document captured at 390 × 844; document width is 426 px. The archived rank table scrolls inside its card, but the filter layout still causes page-level overflow. |
| Country | India, current data and selected player SkRossi | Full-page visual review; no horizontal page overflow (document width 1526 px). Selected-player panel has internal vertical scrolling. | Full-page visual review; no horizontal page overflow (document width 380 px). Selected-player panel has internal vertical scrolling; the page capture cannot include its entire contents at once. |
| Country | India, archived V26 Act IV | Archive selector reached; archived player rows and RR values rendered. The separate archived snapshot card still reported no observations despite populated player rows and tier totals. | Archived country layout reviewed in the narrow viewport; country tables keep their own horizontal scrolling. The empty snapshot card remains visible. |
| Global | Current global leaderboard and selected-player overlay | Full-page visual review; no horizontal page overflow. The selected-player overlay is a separate bounded area, so its entire scrollable content is not represented in one document capture. | Full-page visual review; horizontal overflow (document width 434 px). A wide Immortal tier-coverage table extends beyond the viewport. |
| Global | Archived V26 Act IV leaderboard | Full document captured; no horizontal page overflow (document width 1526 px). Archive confidence shows 7/7 source ladders and 600 pages; the global list shows 13,237 archived players, 10 per page. | Full document captured; document width 426 px. Player rows fit in the card with several columns requiring horizontal scrolling; act-trend controls also exceed the viewport. |

## Findings to fix

1. **Overview mobile horizontal overflow (P1).** The document is 426 px wide at a 390 px viewport. The off-screen region cards and act-trend controls contribute to the excess width. Keep the one-card carousel interaction, but clip/contain the track and make every filter row wrap or fit the viewport.
2. **Region mobile horizontal overflow (P1).** The document is 426 px wide. The act-trend rank-tier filter reaches the right edge, while wide trend tables extend far beyond the viewport. Use a mobile-specific stacked filter layout; constrain tables to their card and scroll within the table wrapper.
3. **Global mobile horizontal overflow (P1).** The document is 434 px wide; Immortal tier-coverage content is substantially wider than the viewport. Ensure the wrapper owns the horizontal scroll and does not widen the document.
4. **Player detail capture is incomplete (P2).** `#selectedPlayerSection` is internally scrollable: approximately 2910 px of content in an 823 px desktop panel and 3265 px in a 648 px mobile panel. This is usable in the page, but a full-page screenshot cannot show its entire content in one image. Consider an expanded, non-scroll-capped detail view or a dedicated player page if a single complete capture is required.
5. **Archived act snapshot card disagrees with populated archive data (P1 data correctness).** Asia-Pacific’s V26 Act IV archive reported 7/7 source ladders and 600 pages, and the country table showed India with 350 players and nonzero Immortal tier counts, while the separate archived snapshot card said “No current observations.” At least one archived player row (SkRossi) also had a high RR/Immortal tier. The empty summary and populated archive data need source/aggregation reconciliation.

## Interpretation

- Desktop page layouts fit the tested 1536 px width; long pages were captured as one full-page image.
- Mobile country page itself fits the tested width. Its internal tables and selected-player detail panel have their own scroll behavior.
- Mobile overview, region and global pages still have document-level horizontal overflow and need responsive fixes before they can be called clean at 390 px.
- Current and archived views both rendered. The V26 Act IV archive summary discrepancy is a data issue, not merely a label or layout issue.
- Profile/Tracker data and leaderboard snapshots can have different timestamps. Player RR/tier comparisons should retain their source timestamp labels rather than implying they are simultaneous.

## Screenshot references

The prior 27-flow screenshot set remains indexed in [RANKED_OBSERVATORY_UI_GUIDE.md](RANKED_OBSERVATORY_UI_GUIDE.md). Those images are viewport captures from an earlier pass, not the 1536 × 816 and 390 × 844 full-page captures from this retest. The newly captured Asia-Pacific current/archive, overview archive, and global archive images are shown inline in the Codex conversation where this retest was performed; the browser capture surface did not provide a local file-save option for those new images.

