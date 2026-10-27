# Match Intelligence Canonical Data Contract — M1

Status: implemented parser contract, version `match-normalizer-1`. This document covers Phase M1 only. It defines normalization and validation in memory; it does not describe persistence or an API.

## Pipeline boundary

`Tracker response-export v1 → bounded JSON parse → envelope/player/match validation → source reconciliation → canonical match → machine-readable quality`

The parser accepts the existing collector export envelope (`schemaVersion: 1`, provider, selected player, match entries, response, and export-level completeness/failure metadata). The individual response is validated against the response match ID before normalization. The complete payload is limited to 25 MiB. Duplicate JSON object keys, unsupported export versions, a different requested player, and an oversized export are rejected. Duplicate match IDs are reported and rejected rather than deduplicated silently.

No database, network call, raw-file write, or logging of the payload is performed in M1.

## Identity and provenance

- Canonical match identity is provider plus provider match ID. The Tracker response source is retained separately (the sample reports `riot`).
- Participant identity is provider-scoped. Riot ID is preserved as an observed alias, not treated as a permanent account key. The selected player is resolved from the exact requested Riot ID using NFC normalization and case-insensitive comparison; zero or multiple matches fail identity validation.
- Identity confidence is explicit. M1 uses `PROVIDER_SCOPED`; it does not claim a globally immutable identity.
- Provenance carries provider, response source, capture timestamp, parser version, capture method, and SHA-256 of the serialized response subtree. The hash is a canonicalized response-object fingerprint, not a transport-byte signature.

## Canonical records

The Java records in `CanonicalMatch.java` form the M1 contract:

| Record | M1 meaning |
| --- | --- |
| `CanonicalMatch` | Match mode/queue, map and map ID, Act, start, duration, ranked/overtime/completeness, selected-player perspective, teams, participant summaries, rounds, player-round rows, damage interactions, events, positions, transform, field availability, provenance, parser version, quality and issues. Patch remains absent when the source does not provide it. |
| `Team` | Provider team ID, observed name, score, rounds lost, observed winner state. |
| `Participant` | Provider-scoped identity, team, agent, observed rank/rating, aggregate K/D/A, damage, score and any supplied combat/rank metrics. Missing fields remain null. |
| `Round` | Number, winning team, raw result label, reported side context, objective facts, source completeness, and overtime-by-round-position. |
| `PlayerRound` | Identity/team/agent/round, side, K/D/A, damage, score/combat score, loadout value, credits spent and remaining. Side is retained from each source row; no side-switch rule is inferred. |
| `DamageInteraction` | Attacker, victim, round, damage and hit-location counts from the source damage segment. |
| `MatchEvent` | Stable local event reference, source reference/type, normalized category, round, raw time values, actor/target/assistants, weapon and source evidence. |
| `PositionSnapshot` | Discrete source location associated with a player, opponent, or objective/event. Coordinates remain in `TRACKER_WORLD_RAW`; a source map transform is retained separately. |
| `FieldAvailability` | Explicit presence for player rounds, damage interactions, kill events, economy, objectives, event positions, round start/end events, and patch. |
| `MatchValidationIssue` | Severity, stable machine code, source path and concise explanation. |
| `ParsedMatch` / `ExportReport` | Per-match status/counts/issues and export-level requested/response/accepted/partial/rejected counts. |

## Null, zero, and raw measurements

An observed numeric zero is represented as zero. A missing or unparseable value is null and is accompanied by availability or validation information; it is never converted to zero. Source time fields are named `roundTimeRaw` and `matchTimeRaw`. The export values are numeric and non-negative in the sample, but their units have not been independently verified, so M1 does not label them seconds or milliseconds. Raw x/y coordinates are not turned into movement paths. A discrete event snapshot is not a trajectory.

## Event categories

M1 distinguishes `CREDITED_PLAYER_KILL`, a separately derived `PLAYER_DEATH` linked to its source kill, `SELF_OR_ENVIRONMENTAL_EVENT`, `ABILITY_RELATED_SPECIAL_EVENT`, `PLANT`, `DEFUSE`, `DETONATION`, `ROUND_START`, `ROUND_END`, and `UNKNOWN_SOURCE_EVENT`.

The raw `player-round-kills` segment is not equivalent to an enemy elimination. A self-referential identity is classified conservatively from its observed event metadata. Unknown segment types are retained with a source reference and evidence and add a warning. Duplicate-looking event rows are preserved and flagged; they are not discarded. The derived death and source kill share provenance so the same event is not counted as two credited kills.

## Quality statuses

- `VALID`: required identities and core match/round/participant data reconcile, with no unresolved warning.
- `VALID_WITH_WARNINGS`: usable records with disclosed unresolved source coverage or unknown source data.
- `PARTIAL`: useful canonical data exists, but expected per-round coverage is incomplete.
- `INVALID`: identity/schema/core consistency errors prevent accepting the match as valid.

No status silently repairs source values. Team/player total checks are used where the observed sample supports the comparison; warning-only differences remain visible as issues.

## Observed sample coverage

The real five-match validation summary, counts, and discrepancies are recorded in [REAL_DATA_VALIDATION.md](REAL_DATA_VALIDATION.md). The raw export remains the single existing source file and is not copied into this contract, fixtures, logs, or screenshots.

## M1 exclusions and follow-on requirements

M1 does not persist records, expose APIs, score tactical decisions, infer utility use or intention, create top-rank benchmarks, or train models. No continuous player paths or normalized map positions are emitted. Round start/end events and patch were not available in the inspected export. Economic values retain their source labels (`loadoutValue`, `spentCredits`, `remainingCredits`); M1 does not infer buy categories. These boundaries must remain explicit in later phases.
