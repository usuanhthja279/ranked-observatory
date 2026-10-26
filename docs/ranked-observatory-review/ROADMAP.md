# Roadmap and implementation gates

Effort sizes are relative, not delivery promises: S is a bounded change, M crosses several modules, L changes data contracts or publication. Finish one phase before widening scope.

## Phase 1 — Trust in existing results (M)

Fix latest-request-wins UI behavior, inclusive act-range validation, shared chart/table derivation, explicit missing states, and short endpoint-aware table labels. Investigate Episode 5 Act III using archive coverage evidence. Keep estimate counts integer and clearly approximate.

Gate: rapid selection changes cannot mix results; every graph endpoint agrees with its table; the full supported act range works; a suspicious source is identified as valid, partial, unavailable, or needing recollection. No blind historical rerun or fabricated replacement data.

## Phase 2 — Versioned facts and models (L)

Introduce publication revision and provenance fields; invalidate cache against the full relevant revision. Centralize rank-threshold rules and estimation policy. Decide whether historical estimates intentionally differ from live estimates; expose the distinction or unify through an explicit versioned migration.

Gate: corrected data refreshes without a restart, failed runs preserve published reads, and each displayed estimate is reproducible from immutable inputs and a named policy. Review deferred V8 before enabling any foundation code.

## Phase 3 — Reliable operation (M/L)

Add historical ownership lease, repeat-page/nonprogress checks, bounded response bodies, per-key profile single-flight, abuse budgets, structured collection status, backup/restore evidence, and durable hosting planning.

Gate: restart resumes committed work; a second worker cannot steal a live source; unavailable upstream data produces a bounded retry state; restore works in an isolated database. Any hostname/account change requires a separate authorized deployment action.

## Phase 4 — Maintainable, fast UI and queries (M)

Extract trend state/formatting/chart/table modules, preserve existing URLs, simplify scoped controls, complete keyboard support, and measure query plans. Add materialized summaries only where measurements justify them. Avoid a wholesale framework migration.

Gate: agreed desktop/mobile journeys pass focused checks; cold and warm latency are recorded; no database-pool starvation under collection; accessible text alternatives match charts.

## Phase 5 — Distinctive analytics (L)

Implement explain-the-change decomposition, cohort retention, and player journey first. Then coverage-aware emerging-scene analysis and richer Radiant race context. Define every metric and its limitations before adding UI.

Gate: identities and denominators are explicit; incomplete observations do not become churn or demotion facts; examples are traceable to source records. Model-based narratives are optional and generated from compact approved facts, not raw unbounded histories.

## Work intentionally deferred

- Predictive mindset labels, unvalidated grind scores, and precise national population claims.
- More collection concurrency before shared pacing, ownership, and resource measurements.
- Deleting historical snapshots to save space before a lossless retention design and verified backups.
- Microservices or a frontend framework rewrite solely for appearance.
- A new automatic LLM call on every page request.

