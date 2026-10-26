# Product and presentation review

## Product purpose

Help users answer three questions: how a competitive scene changes across acts, how a player progresses, and how reliable that conclusion is. Existing features already support this direction: region exploration, country tables, activity, player search, RR history, overtake targets, Radiant race, historical comparison, and confidence status.

The supplied screenshots show a coherent dark theme, readable rank icons, regional imagery, and useful drilldowns. They also show competing controls, horizontal table overflow, long explanations, and previous chart/table mismatches. Current mobile interaction and accessibility were not visually audited in this assessment.

## Recommended information hierarchy

- Overview: concise header, confidence summary, player search, region cards, region/country activity. Put less-used standings in the existing optional surface.
- Region: scoped header, current player data, country comparison/trends, country data and movement, RR changes and selected-player details.
- Country: country identity, current counts, act journey, movement, player list, RR changes. Do not require users to reselect the current region/country.
- Player: identity and source time, current standing, act journey, RR/cutoff graph, next five targets, then detailed provenance. A missing observation must not imply a demotion automatically.

## Act trends interaction contract

First row: From act, To act, Tier. Second row on regional pages: one Add country control and removable country chips. A plain estimate checkbox belongs beneath the description. Keep region fixed on region pages and country fixed on country pages; expose wider comparison in an explicit compare mode. Hide obsolete metric and duplicate country controls rather than merely disabling them.

The picker should open in a viewport-aware overlay, scroll inside its results, support Escape, keyboard navigation, focus return, and outside-click closing. A consistent chevron is an affordance; a stray text arrow is unnecessary. Mobile controls can wrap naturally instead of forcing desktop width.

Offer Standard, Smooth, and Stepped chart shapes. Standard should be the analytical default. Smooth is a visual interpolation and must not imply extra observations; prevent overshoot below zero. Stepped holds the prior observed value until the next observation. None should connect missing periods as if measured.

Comparison table: Country, From, To, Change, Change %, Peak. Put exact act names and count basis in a nearby caption or accessible header description. Use N/A for percent change from zero. Use whole-player counts and one decimal for percentages. A single selected country can also show a small summary of peak act, observed acts, missing periods, and coverage. Do not invent extra rows merely to fill the chart height; align panel containers and allow compact empty space or useful metadata.

## Accessibility and speed

Provide text labels/tooltips for icons, visible keyboard focus, a data-table equivalent to canvas, and information that does not depend on red/green alone. Modal search needs focus trapping, Escape, and focus restoration. Disable flip/motion effects when reduced motion is requested. Mobile region cards should snap to one card and retain a clear navigation link independent of hover.

Image scenery should support the content: dark translucent treatment, bounded image dimensions, lazy loading below the fold, and local attribution. Avoid loading a large image per row where a small thumbnail suffices. Measure first useful content and interaction readiness separately from total page completion.

## Innovations worth building

1. **Explain the change:** decompose a country's act-to-act change into retained players, new appearances, exits, rank transitions, and recovered country labels. This makes growth interpretable instead of merely colorful.
2. **Coverage-aware scene explorer:** identify rising leaderboard representation only across comparable acts. Show denominator, missing-country share, and sensitivity to estimates. Call it competitive representation rather than national market size.
3. **Player journey with evidence:** act-end rank, best observed rank, first/last seen, region changes, and identity gaps. Clearly separate reconstructed tiers from reported ranks.
4. **Cohort retention:** among players observed in one act, how many remain, move tier, or disappear later? Disappearance means not observed, not confirmed churn.
5. **Radiant race context:** observed cutoff history, distance, recent gap direction, and data freshness. No unsupported time-to-Radiant prediction.
6. **Compare fairly:** same top-N cohort, same tier definition, same country-label basis. A short explanation states why an apparently dramatic comparison may be unreliable.

Round-level coaching and natural-language AI reports should follow these foundations. An automatic narrative amplifies data errors if provenance and metrics are not stable first.

