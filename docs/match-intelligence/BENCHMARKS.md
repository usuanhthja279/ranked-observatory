# Match Intelligence Benchmarks — M6

## Scope and evidence boundary

M6 adds account-private reference cohorts and contextual comparisons over match imports already owned by that Observatory account. A cohort is an owner-entered research selection of exactly ten Riot IDs, with selection rank, region, Act, snapshot date, requested match target and a data-access status. The service does not verify leaderboard placement, permission/consent, current identity ownership, or source completeness. `OWNER_REPORTED_PERMISSION` is stored as an owner statement and is explicitly marked as unverified. The region, Act and snapshot date describe cohort selection; they do not establish each player's rank during an imported match.

The interface records up to 20 requested matches per member. It does not initiate Tracker collection. Imported match counts, unique IDs and source-coverage flags are derived from the authenticated owner's imports, not from client-supplied totals. Only ranked Competitive matches from the cohort Act enter the comparison. Optional map, mode and agent filters narrow the scenario. Unsupported dimensions—including reliable role equivalence, lobby strength, source eco/full-buy classification and equal opponent difficulty—are not inferred.

## Comparison method

For each reference member, M6 combines each supported deterministic metric's numerators and denominators across that member's eligible unique matches, producing one member-level rate. The cohort reference is the median across member-level rates; the 25th–75th percentile is the observed between-player interquartile spread, not a confidence interval. The selected subject is excluded by case-insensitive Riot ID when present in the cohort. Shared match IDs are deduplicated for unique-match coverage; a match is not counted once for every cohort member.

The initial eligible metrics are deterministic M5 measures with numeric source denominators: selected-player opening kills/deaths, selected-team conversion after an opening advantage, confirmed reciprocal trade-like sequences, recorded relative team-loadout rounds, explicit objective-event count per match, multikill rounds, no-death rounds, and reported damage per reporting round. Each metric reports subject sample size, number of reference players with that metric, and unique reference-match coverage for that metric. Missing denominators remain unavailable. A positive difference is a numeric difference from the reference median, not a quality judgment.

The engine returns `INSUFFICIENT_EVIDENCE` and suppresses reference quartiles/median until there are at least ten eligible reference players and twenty unique reference matches in the selected scenario. Metric-specific coverage can remain insufficient after the cohort-level threshold is met. No aggregate player score or “correct decision” label is produced.

## Storage and API

Flyway V12 creates `mi_benchmark_cohorts` and `mi_benchmark_memberships`. Cohorts are keyed by owner account; members cascade-delete with the cohort. The existing shared canonical match store remains unchanged. Cohort and comparison reads are owner-scoped and authenticated; creation requires CSRF.

See [API.md](API.md) for the M6 routes. Cohort responses distinguish selected rank from observed match-time rank and include derived identity availability, import counts, unique-match counts and source field coverage.

## Remaining work

M6 is a descriptive first benchmark release. It does not verify consent or rank, access Tracker on behalf of the user, infer economy purchase categories, match opponent difficulty, model within-match dependence in uncertainty, or calculate bootstrap confidence intervals. Source quality and all small-sample limitations must stay visible. Cohort curation for ten high-ranked players and twenty competitive matches each remains an owner-run data collection task.
