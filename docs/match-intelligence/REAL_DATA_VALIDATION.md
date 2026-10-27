# M1 Real-Data Validation Report

The existing committed five-match export was parsed locally as source evidence. The report intentionally excludes player names, match IDs, raw event payloads, and coordinates. No copy of the export was added to tests or documentation.

## Export result

- Source export: Tracker collector schema version 1; source marked complete.
- Requested/returned: 5 / 5.
- Accepted: 5; partial: 0; rejected: 0.
- Parser: `match-normalizer-1`.
- All 5 matches contain 10 participant summaries and complete per-round participant coverage.
- Across the five matches: 109 rounds, 50 participant summaries, 1,090 player-round rows, 1,222 opponent-specific damage rows, 832 raw kill-event rows, 825 credited player kills, 7 self/environment/special events, 89 normalized objective events, and 5,565 discrete position snapshots.
- One match is overtime. The normalized records retain side values from each player-round row.

## Per-match results

Rows are numbered only for this report; they are not stable match identifiers.

| Sample | Rounds | Player-round rows | Damage rows | Raw kill rows | Credited kills | Self/special rows | Objective events | Position snapshots | Status |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 21 | 210 | 254 | 164 | 163 | 1 | 16 | 1,079 | Valid with warnings |
| 2 | 23 | 230 | 255 | 179 | 178 | 1 | 19 | 1,183 | Valid with warnings |
| 3 | 18 | 180 | 184 | 129 | 128 | 1 | 19 | 893 | Valid |
| 4 | 28 | 280 | 327 | 225 | 222 | 3 | 21 | 1,465 | Valid with warnings |
| 5 | 19 | 190 | 202 | 135 | 134 | 1 | 14 | 945 | Valid with warnings |

The 7 non-credited rows resolve to five `Ability` / `Not Dead Yet` self-referential events, one `Bomb` event, and one `Fall` event. They are not counted as enemy eliminations. Across all matches participant death totals exceed credited player kills by 6. Four matches surface `DEATH_EVENT_COVERAGE_DIFFERENCE`; the parser retains the discrepancy rather than changing either total.

## Reconciliation and source limits

- Round winner totals match reported team scores for all five sample matches.
- Participant round K/D/A reconciles with each participant aggregate in the inspected data.
- Team aggregate kills, deaths, assists, and damage reconcile with participant totals in this sample.
- Round outcome labels observed: 87 `Elimination`, 20 `Defuse`, and 2 `Detonate` (109 rounds total). Objective event count is a separate count of normalized source facts, not a replacement for round outcomes.
- Positions are discrete snapshots: 4,733 player-location snapshots and 832 opponent-location snapshots. The source also supplies map transform metadata. M1 preserves raw coordinates and transform separately; visual orientation and normalized-map accuracy are not asserted here.
- `gameTime` and `roundTime` are present as non-negative numeric source values. Units are unverified and intentionally remain raw.
- Economic observations include `loadoutValue`, `spentCredits`, and `remainingCredits`; M1 does not classify buy types.
- Source metadata includes match start, duration, mode/queue, season, map, rounds, and mode maximum. Patch is absent. No round-start or round-end event segments were observed.
- No unknown source segment types appeared in these five real matches; synthetic tests verify unknown-segment retention.

## Result

**Pass for M1 parsing and reconciliation.** The sample export parses consistently; all seven self/environment/special rows are excluded from credited kills; the remaining six-death coverage difference is visible and documented. This is parser verification against one existing five-match export, not proof of broader Tracker schema coverage or a production ingestion flow.
