# Efficient implementation handoff

## Minimize repeated model work

This review adds documentation only. It does not change account model settings, subscriptions, or application behavior. No token-saving percentage is claimed because usage was not benchmarked.

Use the index plus one phase document as initial context. Read the specific implementation files for that phase, not the entire repository or conversation history. Search symbols first, then inspect bounded sections. Do not load large JSON captures, generated assets, logs, database dumps, or screenshots unless they answer a concrete question. Request summaries of long-running jobs rather than repeatedly polling full logs through a model.

Use deterministic scripts for extraction monitoring, numerical reconciliation, inventory, and formatting. Store compact status and evidence locally. A model is useful for explaining an unexpected discrepancy, designing a metric, or changing a bounded code path; it is unnecessary for checking the same healthy endpoint every minute.

For future AI-generated analysis, compute verified metrics in code, send only a small provenance-bearing fact bundle, cache the narrative by publication/model/prompt version, and regenerate only when inputs materially change. Never use a language model to calculate leaderboard counts or infer private mindset. Select a smaller/cheaper available model for bounded summaries only after evaluating output quality; reserve stronger reasoning for difficult correctness or architecture decisions. No specific current pricing or model availability is assumed here.

## Task-local reading map

- Trend consistency: `act-trends.js`, `HistoricalTrendsQueryService.java`, Phase 1 findings.
- Cache: `DashboardCache.java`, `TrackerProfiles.java`, read controller annotations, publication writes.
- Collection: `GlobalLeaderboardService.java`, `HistoricalActArchiveService.java`, `TrackerLeaderboardClient.java`, `RequestPacer.java`.
- Country model: `country-model.js`, `server-access.js`, `CountryModelEvidence.java`, historical estimate query.
- Movement/player: `CountryActivity.java`, `BoundaryMovement.java`, `PlayerTargets.java`, journey query, corresponding static modules.
- Security/operations: `LeaderboardSecurity.java`, `ObservatoryAccounts.java`, application properties, existing service/availability scripts.

Java paths are under `leaderboard-service/src/main/java/com/valorant/leaderboard`; browser files are under `leaderboard-service/src/main/resources/static`.

## Reusable implementation prompt

> Work only on the Ranked Observatory (`leaderboard-service`) and its directly related scripts. Read `docs/ranked-observatory-review/README.md`, then the requested phase in `ROADMAP.md` and its linked findings. Implement Phase [N] only. First verify the relevant source findings against the current working tree; treat this review as a dated assessment, not unquestionable truth. Preserve unrelated changes.
>
> Keep ongoing historical extraction, its cursors and database records, live scheduling state, and the submitted tunnel hostname unchanged. Do not restart, migrate, or deploy as an incidental step. If the requested phase explicitly requires such an action, prepare a concrete rollout and recovery plan first and follow the user's current authorization.
>
> Make the smallest coherent changes. Use one response contract for charts and tables. Preserve observed facts and distinguish inferred values. Do not silently enable deferred migrations. Keep secrets out of outputs. Use bounded file reads and focused verification; respect any current instruction not to run tests, and state that limitation.
>
> Finish with files changed, behavior changed, validation performed, remaining limitations, and the next phase's prerequisite. Update a concise phase completion note with evidence. Do not reread unrelated project modules or add speculative features.

## Completion note template

Record phase and date, publication/schema/API contract affected, changed files, acceptance criteria met, checks actually run, limitations, rollback procedure when applicable, and next dependency. Keep it short enough to reuse without the original chat.

## Low-cost operating rhythm

One plan, one bounded implementation pass, one focused validation pass, and one completion note. Reopen investigation only for a concrete failure or changed requirement. Batch independent reads, keep output limits small, and avoid multiple agents duplicating the same review. This improves both usage and reviewability without trading away correctness.

