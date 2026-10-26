# Data meaning, confidence, and models

## What the observatory observes

The dataset represents captured ranked leaderboard entries, principally Immortal/Radiant, not all VALORANT accounts or all matches. Country is reported or recovered profile information, not verified residence. A player with no country is an unknown label, not a separate competitive population. A country's representation can change because of label recovery, upstream coverage, renamed accounts, ladder entry/exit, or actual performance.

Keep four clocks: game/source observation time, fetch time, completed publication time, and next scheduled trigger. Activity windows should state which clock anchors them. A recent fetch of stale upstream content is not fresh gameplay.

## Metric contract

| Metric | Defensible meaning | What it does not establish |
|---|---|---|
| Observed players | Distinct captured player keys in a defined source and scope | Total national player population |
| Active players | Unique players with observed wins/RR evidence of activity | Every player who played; unchanged counters may conceal activity |
| Wins added | Valid positive cumulative-win differences within an act | Match identities or exact timing between scans |
| Estimated losses | Loss proxy derived from RR decreases under the chosen assumption | Exact losses; multiple games and other RR effects can be hidden |
| Estimated matches | Wins added plus assumed losses, as player-results | Unique lobbies; ten player-results can refer to one match |
| Possible promotion/demotion | Low-RR entry/absence satisfying inference safeguards | A confirmed Ascendant/Immortal transition |
| Cutoff gap | RR distance to an observed qualifying cutoff | Guaranteed promotion or matchmaking eligibility |
| Act-end rank | Archived captured standing | Full within-act peak or every rank reached |

Current boundary inference has useful freshness/coverage checks, including regional-scan requirements for demotions. Display Pending when evidence is unavailable, zero only when an eligible comparison found no qualifying signals, and N/A when the metric cannot apply.

## Source comparability

Historical trends use regional archives without adding global totals to them. Current values derive from completed live snapshots and reconciliation. These are different observation processes. A line crossing into current data should mark that transition. A fixed top-N cohort can improve comparability but excludes the rest of the ladder and must retain its denominator.

An act appearing in the catalog does not prove its country distribution is correct. In particular, the reported Episode 5 Act III dip needs source-level coverage investigation: per-region counts, known-country fraction, source update time, terminal-page evidence, repeated pages, and neighboring acts. Do not describe a sudden collapse as player migration or a UI-axis problem without that evidence. Missing or invalid data should be a gap, not a fabricated zero or interpolated replacement.

## Estimates: current policy versus historical policy

Actual country recovery should precede allocation of the remaining unknowns. Store recovery provenance and time; preserve the original observation so later enrichment does not silently rewrite history.

The current country model gives high tiers a weight of approximately:

`weight(country,tier) = known tier count × country strength × server-access factor`

For Immortal 1, the reviewed policy uses a population basis adjusted by a bounded median-RR term and server access. Unknown tier totals are distributed using normalized weights and integer largest-remainder allocation. The historical trend path uses proportional allocation rather than the same live weighted policy.

These are policy-based imputations, not validated probabilities. Known-country labels are unlikely to be missing randomly. Large populations and high-tier strength can amplify the same evidence twice; zero known high-tier players can produce zero allocated players despite plausible hidden players. Server proximity does not measure individual ping, queue preference, willingness to grind, or causality.

Recommended model requirements:

1. Return observed, reconstructed, allocated, and total values separately.
2. Version every weighting policy and preserve its input publication.
3. Use measured ping only with location, ISP, date, server, and sampling metadata; otherwise label access as a proxy. Use a neutral factor for missing evidence.
4. Compare proportional, tier-proportional, and weighted policies on masked known-country samples, stratified by region/tier/country size. Random masking alone will not reproduce nonrandom missingness; add biased-missingness sensitivity scenarios.
5. Report error and allocation stability, not only whether India's desired number is obtained. Do not tune toward a preferred answer.
6. Show sensitivity ranges as scenario ranges, not statistical confidence intervals unless a calibrated model supports that interpretation.
7. Preserve tier totals exactly; integer allocation must be deterministic. Charts and tables should use the same rounded allocation, with an approximation marker.

## Historical Immortal reconstruction

Reported combined Immortal and reconstructed Immortal 1/2/3 are distinct facts. The Episode 3 threshold backcast before Episode 3 was a user-directed assumption. It should remain labeled as reconstruction, not retroactively become an observed historical rank. Later regional thresholds need explicit effective-act policy rather than catalog-order magic numbers.

Retain `reportedTier`, `derivedTier`, `thresholdProfile`, and `derivationVersion`. The public display can remain simple while a details panel explains the distinction. Countries with no reliable RR should retain combined/unknown tier rather than a guessed subdivision.

## Player journey and playstyle

A useful player journey combines archived act-end standings and current observed history with separate timestamps. Use stable verified identity where available; Riot-name matching alone can break on renames or merge unrelated names. Show gaps and identity confidence. Never claim an all-time peak from an incomplete capture window.

Round-level playstyle requires match IDs, player identity, map, agent, side, round sequence, purchases/equipment, kills/deaths and timing, team outcome, and coverage metadata. Do not derive buy/save behavior from aggregate RR. Label behavior after a loss streak as observed behavior, not the player's psychological mindset. Control comparisons for map, side, agent, economy, and sample size. Ten selected players per rank describe that sample, not all Indian players. Public integration also needs an explicit consent/access-rights design; observatory login is not Riot Sign On.

