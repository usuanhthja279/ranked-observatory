# Ranked Observatory: project assessment

Review date: 26 October 2026. Scope: `leaderboard-service`, its collection and availability scripts, database migrations, browser application, and relevant existing documentation. The separate port 8080 application and video pipeline are excluded.

## Assessment

The observatory is a substantial analytical prototype with useful production foundations: atomic completed snapshots, resumable historical collection, request pacing, PostgreSQL migrations, generation-based caching, country drilldowns, player history, and explicit uncertainty features. Its strongest differentiator is connecting country and regional trends across acts with individual player progression.

The next phase should consolidate **data trust and consistency**, rather than add another dashboard. Current and archived ladders have different collection bases; estimates are not uniform across views; long-lived caches do not cover every correction; and overlapping browser requests can mix a new selection with an old result. A visually polished chart can consequently express more certainty than the underlying observation supports.

This is a source and supplied-screenshot review, not a penetration test, accessibility audit, load test, or certification. No test suite, extraction, deployment, restart, or database mutation was performed for this assessment. Existing working-tree changes were preserved. Completion counts and uptime must be read from runtime status; old documentation and screenshots are not live measurements.

## Reading order

1. [Architecture and flowcharts](ARCHITECTURE.md): deployed structure versus deferred design.
2. [Technical findings](TECHNICAL_REVIEW.md): prioritized defects, risks, and acceptance criteria.
3. [Data semantics and estimation](DATA_QUALITY_AND_MODELS.md): what the numbers can actually mean.
4. [Product and presentation](PRODUCT_AND_UX.md): clearer interaction and innovative features.
5. [Implementation roadmap](ROADMAP.md): dependency-ordered phases.
6. [Operations and validation](OPERATIONS_AND_VALIDATION.md): protect collection and measure reliability.
7. [Efficient model handoff](MODEL_EFFICIENT_HANDOFF.md): reduce repeated context and tool consumption.

## Scorecard

Scores are reviewer judgments out of 10, weighted toward correctness and reliability. They are not measured test coverage, market rankings, or security guarantees.

| Dimension | Weight | Score | Evidence and limitation |
|---|---:|---:|---|
| Architecture | 10% | 6.5 | Standalone module and clear persistence boundary; large services and shared frontend state |
| Collection and recovery | 15% | 7.0 | Checkpoints, retry pacing, resumable archives; historical multi-instance ownership needs strengthening |
| Data correctness and provenance | 15% | 5.5 | Completed snapshots and conflict handling; identity and cross-act comparability remain limited |
| Analytical validity | 10% | 4.5 | Useful descriptive metrics; estimates, losses, and backcast ranks require validation and clearer separation |
| Performance design | 10% | 6.0 | Compression, bundled country reads, caching; no measured latency or heap benchmark in this review |
| Security and privacy design | 10% | 5.5 | BCrypt, CSRF, admin collection; public upstream-triggering requests and player-consent boundaries need review |
| Presentation and usability | 10% | 6.5 | Coherent visual theme and useful drilldowns; dense controls, chart/table consistency, accessibility gaps |
| Maintainability | 10% | 4.0 | Dense large Java/JS files and duplicated business rules increase change risk |
| Operations and deployment | 5% | 4.5 | Local scripts and availability worker; durable hosting, backup recovery, and freshness objectives need proof |
| Verification evidence | 5% | 5.0 | Targeted tests exist; historical/UI regression evidence is limited and tests were not run |

**Weighted assessment: 5.7/10.** This rates readiness for a dependable public analytics product, not the effort invested or breadth of features. The biggest improvements will come from a shared data contract, versioned publication, and regression checks around historical trends.

## First decisions

- Prioritize the chart/table request race, full act-range limit, and archive cache revision before new features.
- Treat country totals as observed leaderboard representation, not the entire national VALORANT population.
- Keep actual country recovery, inferred tier reconstruction, and unknown-country allocation separately identifiable.
- Preserve the collectors while refactoring read paths. Do not enable deferred migrations merely because they exist.
- Use deterministic code for metrics and monitoring. Reserve model reasoning for interpretation and bounded code changes.

## Evidence base

Primary sources are the repository: `build.gradle`, root README, `application.properties`, Java services, static JS/HTML, V1–V7 migrations, deferred V8, and the test inventory. Relevant prior design documents include `HISTORICAL_TRENDS_IMPLEMENTATION_HANDOFF.md`, `HISTORICAL_TRENDS_IMPLEMENTATION_PROGRESS.md`, `OBSERVATORY_CACHING.md`, `COUNTRY_WEIGHTED_POLICY.md`, and `OVERTAKE_AND_BOUNDARY.md`. Prior plans are intent; executable source determines the current implementation.

Static inventory found 13 Java test classes and 37 textual `@Test` annotations. That is an inventory, not a pass count. Large reviewed files include `leaderboard.js` (~79 KB), `leaderboard.html` (~74 KB), `GlobalLeaderboardService.java` (~38 KB), and `act-trends.js` (~27 KB). Byte size and unusually dense lines are maintenance signals, not proof of poor runtime performance.

